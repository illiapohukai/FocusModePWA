#!/usr/bin/env python3
"""Focus Mode+ v22 APK and merged-manifest security audit (stdlib + Android SDK tools).

Checks one built APK against the v22 policy and writes a machine-readable report. The APK's
compiled AndroidManifest.xml *is* the merged manifest; it is also written out as XML for review.

  apk_audit.py --apk app-release-unsigned.apk --variant release --out reports/apk-release.json
  apk_audit.py --apk app-debug.apk --variant debug --out reports/apk-debug.json
  apk_audit.py --compare-debug reports/apk-debug.json --compare-release reports/apk-release.json

Tools: aapt2 (build-tools) and apkanalyzer (cmdline-tools), found below ANDROID_HOME. No
network access. Nothing from the APK besides check results and component names is reported;
matched secret candidates are reported by rule and location only, never by value.
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import pathlib
import re
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

A = "{http://schemas.android.com/apk/res/android}"
RELEASE_ID = "app.focusmodeplus"
EXPECT = {
    "release": {"id": RELEASE_ID, "label": "Focus Mode+", "version": "22.0.0", "debuggable": False},
    "debug": {"id": RELEASE_ID + ".debug", "label": "Focus Mode+ Debug", "version": "22.0.0-debug", "debuggable": True},
}
VERSION_CODE = "2200"
MIN_SDK, TARGET_SDK = "26", "37"

ALLOWED_PERMISSIONS = {
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.ACCESS_NOTIFICATION_POLICY",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.WAKE_LOCK",  # WorkManager
    "{pkg}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",  # androidx.core, signature-level
}
FORBIDDEN_PERMISSIONS = {
    "android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.QUERY_ALL_PACKAGES", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE", "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO",
    "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.READ_CONTACTS",
    "android.permission.GET_ACCOUNTS", "android.permission.USE_BIOMETRIC", "android.permission.USE_FINGERPRINT",
    "android.permission.SCHEDULE_EXACT_ALARM", "android.permission.USE_EXACT_ALARM", "android.permission.BIND_ACCESSIBILITY_SERVICE",
    "android.permission.REQUEST_INSTALL_PACKAGES", "com.google.android.gms.permission.AD_ID",
    "com.google.android.providers.gsf.permission.READ_GSERVICES",
}
# Exported components that may exist, each with the permission that guards it (None: launcher only).
EXPORTED_ALLOWED = {
    "app.focusmodeplus.MainActivity": None,
    "androidx.work.impl.background.systemjob.SystemJobService": "android.permission.BIND_JOB_SERVICE",
    "androidx.work.impl.diagnostics.DiagnosticsReceiver": "android.permission.DUMP",
    "androidx.profileinstaller.ProfileInstallReceiver": "android.permission.DUMP",
    "com.google.android.gms.auth.api.signin.RevocationBoundService": "com.google.android.gms.auth.api.signin.permission.REVOCATION_NOTIFICATION",
}
FORBIDDEN_COMPONENTS = {
    "com.google.firebase.auth.internal.GenericIdpActivity": "web OAuth redirect flow (unused)",
    "com.google.firebase.auth.internal.RecaptchaActivity": "phone/reCAPTCHA flow (unused)",
    "com.google.firebase.provider.FirebaseInitProvider": "automatic Firebase start (explicit init only)",
}
RELEASE_ONLY_ABSENT = {
    "androidx.compose.ui.tooling.PreviewActivity": "Compose preview host (debug tooling)",
    "androidx.activity.ComponentActivity": "Compose test host (debug test manifest)",
}
EXPECTED_QUERIES = {
    ("action:android.media.action.IMAGE_CAPTURE",),
    ("action:android.speech.action.RECOGNIZE_SPEECH",),
    ("action:android.intent.action.MAIN", "category:android.intent.category.LAUNCHER"),
    ("action:android.settings.USAGE_ACCESS_SETTINGS",),
    ("action:android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS",),
}
SECRET_RULES = {
    "google-api-key": re.compile(rb"AIza[0-9A-Za-z_\-]{35}"),
    # Key material, not the PEM header constants that TLS and auth libraries contain.
    "private-key": re.compile(rb"-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----(?:\\n|\s)*[A-Za-z0-9+/=]{64}"),
    "service-account": re.compile(rb"\"type\"\s*:\s*\"service_account\"|\"private_key_id\"\s*:"),
    "github-token": re.compile(rb"gh[pousr]_[A-Za-z0-9]{36}"),
    "aws-access-key": re.compile(rb"AKIA[0-9A-Z]{16}"),
    "slack-token": re.compile(rb"xox[baprs]-[A-Za-z0-9-]{10,}"),
    "generic-sk-key": re.compile(rb"\bsk-[A-Za-z0-9]{32,}"),
    "emulator-project": re.compile(rb"demo-focus-mode"),
    "emulator-host": re.compile(rb"10\.0\.2\.2|127\.0\.0\.1:(?:8080|9099|4400|4000)|localhost:(?:8080|9099)"),
}
# String resources the Google Services plugin generates from google-services.json. An unconfigured
# build (CI, the repository default) must not contain them.
GOOGLE_SERVICES_RESOURCES = {
    "google_app_id", "google_api_key", "default_web_client_id", "gcm_defaultSenderId", "project_id",
    "google_storage_bucket", "firebase_database_url", "google_crash_reporting_api_key",
}
# A cleartext URL to one of these hosts would be a real endpoint, never a documentation string.
ENDPOINT_CLEARTEXT = re.compile(r"^http://(?:\d{1,3}(?:\.\d{1,3}){3}|[^/]*(?:workers\.dev|googleapis\.com|firebaseio\.com|firebaseapp\.com|cloudfunctions\.net|run\.app|web\.app|gstatic\.com|focusmode|illia))", re.I)
SIGNING_FILES = re.compile(r"\.(jks|keystore|p12|pfx|pem|key)$", re.I)
WORKER_HOST = b"focusmodedemoai.illia-pohukai.workers.dev"


def sdk_tool(name: str) -> str:
    home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    patterns = {"aapt2": "build-tools/*/aapt2", "apkanalyzer": "cmdline-tools/*/bin/apkanalyzer"}[name]
    found = sorted(glob.glob(os.path.join(home, patterns)))
    if not found:
        raise SystemExit(f"{name} not found below ANDROID_HOME={home!r}")
    return found[-1]


def run(cmd: list[str]) -> str:
    return subprocess.run(cmd, check=True, capture_output=True, text=True).stdout


def dex_strings(data: bytes) -> list[bytes]:
    """The string_ids table of one DEX file (MUTF-8 bytes)."""
    if data[:3] != b"dex":
        return []
    size, off = struct.unpack_from("<II", data, 0x38)
    out = []
    for i in range(size):
        (pos,) = struct.unpack_from("<I", data, off + 4 * i)
        shift = 0
        while True:  # skip the uleb128 utf16 length
            b = data[pos]
            pos += 1
            if b < 0x80:
                break
            shift += 7
        end = data.index(b"\x00", pos)
        out.append(data[pos:end])
    return out



def scan_entries(entries) -> dict:
    """Secret, endpoint and naming scan over (name, bytes) pairs; DEX files are read by string table."""
    hits: dict[str, list[str]] = {}
    cleartext: set[str] = set()
    documentation: set[str] = set()
    app_classes_named = False
    worker = False
    for name, data in entries:
        if SIGNING_FILES.search(name):
            hits.setdefault("signing-file", []).append(name)
        chunks = dex_strings(data) if name.endswith(".dex") else [data]
        for chunk in chunks:
            for rule, rx in SECRET_RULES.items():
                if rx.search(chunk):
                    hits.setdefault(rule, []).append(name)
            for url in re.findall(rb"http://[A-Za-z0-9._~:/?#@!$&'()*+,;=%-]*", chunk):
                u = url.decode("ascii", "replace")[:120]
                (cleartext if ENDPOINT_CLEARTEXT.match(u) else documentation).add(u)
            if name.endswith(".dex") and chunk == b"Lapp/focusmodeplus/core/domain/sync/SyncEngine;":
                app_classes_named = True
            if WORKER_HOST in chunk:
                worker = True
    return {"hits": hits, "cleartext": cleartext, "documentation": documentation, "app_classes_named": app_classes_named, "worker": worker}


def tiny_dex(strings: list[bytes]) -> bytes:
    """A minimal DEX image with only a string table (enough for dex_strings)."""
    header = bytearray(0x70)
    header[:8] = b"dex\n039\x00"
    ids_off = 0x70
    data_off = ids_off + 4 * len(strings)
    ids, data = bytearray(), bytearray()
    for s in strings:
        ids += struct.pack("<I", data_off + len(data))
        n = len(s)
        while True:
            b = n & 0x7F
            n >>= 7
            data.append(b | (0x80 if n else 0))
            if not n:
                break
        data += s + b"\x00"
    struct.pack_into("<II", header, 0x38, len(strings), ids_off)
    return bytes(header + ids + data)


def self_test() -> int:
    # Planted values are assembled at runtime so that no secret-shaped literal is ever committed.
    api_key = b"AI" + b"za" + b"Q" * 35
    pem = b"-----BEGIN " + b"PRIVATE KEY-----\n" + b"M" * 80
    gh = b"gh" + b"p_" + b"a1" * 18
    planted = [
        ("classes.dex", tiny_dex([api_key, b"Lapp/focusmodeplus/core/domain/sync/SyncEngine;", b"http://focusmodedemoai.example.workers.dev/x"])),
        ("assets/leak.txt", pem + b" " + gh),
        ("res/raw/emu.json", b'{"host":"10.0.2.2:8080","project":"demo-' + b'focus-mode"}'),
        ("META-INF/release.jks", b"\x00"),
    ]
    r = scan_entries(planted)
    for rule in ("google-api-key", "private-key", "github-token", "emulator-host", "emulator-project", "signing-file"):
        assert r["hits"].get(rule), f"scanner missed planted {rule}: {r['hits']}"
    assert r["cleartext"] and r["app_classes_named"], r
    clean = scan_entries([("classes.dex", tiny_dex([b"-----BEGIN PRIVATE KEY-----", b"http://www.apache.org/licenses/LICENSE-2.0", b"La/b;"]))])
    assert not clean["hits"] and not clean["cleartext"] and clean["documentation"], clean
    print("apk_audit self-test: ok")
    return 0


class Audit:
    def __init__(self, variant: str):
        self.variant = variant
        self.checks: list[dict] = []

    def check(self, cid: str, ok: bool, detail: str = "") -> None:
        self.checks.append({"id": cid, "status": "pass" if ok else "fail", "detail": detail})

    @property
    def failed(self) -> list[dict]:
        return [c for c in self.checks if c["status"] == "fail"]


def audit_apk(apk: pathlib.Path, variant: str, manifest_out: pathlib.Path | None, firebase_configured: bool = False) -> dict:
    exp = EXPECT[variant]
    pkg = exp["id"]
    au = Audit(variant)
    badging = run([sdk_tool("aapt2"), "dump", "badging", str(apk)])
    manifest_xml = run([sdk_tool("apkanalyzer"), "manifest", "print", str(apk)])
    if manifest_out:
        manifest_out.parent.mkdir(parents=True, exist_ok=True)
        manifest_out.write_text(manifest_xml)
    root = ET.fromstring(manifest_xml)
    app = root.find("application")

    # ------------------------------------------------------------------ identity
    m = re.search(r"package: name='([^']+)' versionCode='([^']*)' versionName='([^']*)'", badging)
    label = re.search(r"^application-label:'(.*)'$", badging, re.M)
    au.check("identity.application-id", bool(m) and m.group(1) == pkg, m.group(1) if m else "no package line")
    au.check("identity.label", bool(label) and label.group(1) == exp["label"], label.group(1) if label else "no label")
    au.check("identity.version-name", bool(m) and m.group(3) == exp["version"], m.group(3) if m else "")
    au.check("identity.version-code", bool(m) and m.group(2) == VERSION_CODE, m.group(2) if m else "")
    sdk = re.search(r"minSdkVersion:'(\d+)'", badging), re.search(r"targetSdkVersion:'(\d+)'", badging)
    au.check("identity.sdk-levels", bool(sdk[0] and sdk[1]) and sdk[0].group(1) == MIN_SDK and sdk[1].group(1) == TARGET_SDK,
             f"min={sdk[0] and sdk[0].group(1)} target={sdk[1] and sdk[1].group(1)}")
    au.check("identity.no-old-application-id", "io.github.illiapohukai" not in manifest_xml, "old id absent from the manifest")

    # ------------------------------------------------------------------ build type
    debuggable = (app.get(A + "debuggable") == "true")
    au.check("build.debuggable", debuggable == exp["debuggable"], f"debuggable={debuggable}")
    au.check("build.not-test-only", app.get(A + "testOnly") != "true" or variant == "debug", f"testOnly={app.get(A + 'testOnly')}")

    # ------------------------------------------------------------------ permissions
    perms = {p.get(A + "name") for p in root.findall("uses-permission")} | {p.get(A + "name") for p in root.findall("uses-permission-sdk-23")}
    allowed = {p.format(pkg=pkg) for p in ALLOWED_PERMISSIONS}
    au.check("permissions.allow-list", perms <= allowed, "unexpected: " + ", ".join(sorted(perms - allowed)) if perms - allowed else f"{len(perms)} permissions")
    au.check("permissions.forbidden", not (perms & FORBIDDEN_PERMISSIONS), ", ".join(sorted(perms & FORBIDDEN_PERMISSIONS)))
    defined = {p.get(A + "name"): p.get(A + "protectionLevel") for p in root.findall("permission")}
    au.check("permissions.defined-are-signature", all(v in ("0x2", "signature") for v in defined.values()), json.dumps(defined))

    # ------------------------------------------------------------------ components
    inventory = []
    for comp in app:
        if comp.tag not in ("activity", "activity-alias", "service", "receiver", "provider"):
            continue
        name = comp.get(A + "name")
        filters = comp.findall("intent-filter")
        exported_attr = comp.get(A + "exported")
        exported = exported_attr == "true" or (exported_attr is None and bool(filters) and comp.tag != "provider")
        entry = {
            "type": comp.tag, "name": name, "exported": exported, "permission": comp.get(A + "permission"),
            "authorities": comp.get(A + "authorities"), "grantUriPermissions": comp.get(A + "grantUriPermissions"),
            "intentFilters": [sorted(f"{x.tag}:{x.get(A + 'name') or x.get(A + 'scheme')}" for x in f) for f in filters],
        }
        inventory.append(entry)
        if exported:
            if name not in EXPORTED_ALLOWED:
                au.check(f"exported.{name}", False, "exported but not on the allow-list")
            else:
                need = EXPORTED_ALLOWED[name]
                au.check(f"exported.{name}", need is None or entry["permission"] == need, f"permission={entry['permission']}")
        for f in entry["intentFilters"]:
            if any(x == "category:android.intent.category.BROWSABLE" for x in f) or any(x.startswith("data:") for x in f):
                au.check(f"intent-filter.{name}", False, f"browsable or data filter {f}")
        if comp.tag == "provider":
            au.check(f"provider.{name}.authority", bool(entry["authorities"]) and all(a.startswith(pkg + ".") for a in entry["authorities"].split(";")), entry["authorities"] or "")
            au.check(f"provider.{name}.not-exported", not exported, "")
            if name == "androidx.core.content.FileProvider":
                au.check("provider.fileprovider.grant-only", entry["grantUriPermissions"] == "true" and entry["authorities"] == f"{pkg}.materialcapture", entry["authorities"] or "")
        if name in FORBIDDEN_COMPONENTS:
            au.check(f"forbidden.{name}", False, FORBIDDEN_COMPONENTS[name])
        if variant == "release" and name in RELEASE_ONLY_ABSENT:
            au.check(f"release-absent.{name}", False, RELEASE_ONLY_ABSENT[name])
        if comp.get(A + "permission") in ("android.permission.BIND_ACCESSIBILITY_SERVICE", "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", "android.permission.BIND_DEVICE_ADMIN"):
            au.check(f"forbidden-service.{name}", False, comp.get(A + "permission"))
    launcher = [c for c in inventory if c["name"] == "app.focusmodeplus.MainActivity"]
    au.check("launcher.only-main-launcher-filter", bool(launcher) and launcher[0]["intentFilters"] == [["action:android.intent.action.MAIN", "category:android.intent.category.LAUNCHER"]],
             json.dumps(launcher[0]["intentFilters"]) if launcher else "missing")
    au.check("exported.count", sum(c["exported"] for c in inventory) <= len(EXPORTED_ALLOWED), f"{sum(c['exported'] for c in inventory)} exported")

    # ------------------------------------------------------------------ queries, backup, network
    queries = root.find("queries")
    found_queries = set()
    if queries is not None:
        for q in queries:
            if q.tag != "intent":
                au.check(f"queries.{q.tag}", False, f"{q.tag} query {q.attrib}")
                continue
            found_queries.add(tuple(sorted(f"{x.tag}:{x.get(A + 'name')}" for x in q)))
    au.check("queries.exact", found_queries == {tuple(sorted(q)) for q in EXPECTED_QUERIES}, json.dumps(sorted(found_queries)))
    au.check("network.no-cleartext-flag", app.get(A + "usesCleartextTraffic") != "true", str(app.get(A + "usesCleartextTraffic")))
    au.check("network.security-config", app.get(A + "networkSecurityConfig") is not None, "")
    au.check("backup.rules-present", app.get(A + "dataExtractionRules") is not None and app.get(A + "fullBackupContent") is not None, "")

    # ------------------------------------------------------------------ content scan
    with zipfile.ZipFile(apk) as z:
        scan = scan_entries((info.filename, z.read(info)) for info in z.infolist() if info.file_size <= 64 * 1024 * 1024)
    hits, cleartext, documentation_urls = scan["hits"], scan["cleartext"], scan["documentation"]
    app_classes_named, worker = scan["app_classes_named"], scan["worker"]
    for rule in SECRET_RULES:
        files = sorted(set(hits.get(rule, [])))
        au.check(f"secrets.{rule}", not files, ", ".join(files[:5]))
    au.check("secrets.signing-files", not hits.get("signing-file"), ", ".join(hits.get("signing-file", [])[:5]))
    au.check("network.no-cleartext-endpoint-url", not cleartext, ", ".join(sorted(cleartext)[:10]))

    # ------------------------------------------------------------------ resources
    resources = run([sdk_tool("aapt2"), "dump", "resources", str(apk)])
    strings = dict(re.findall(r"resource 0x[0-9a-f]+ string/([A-Za-z0-9_.]+)\n\s+\(\) \"(.*)\"", resources))
    names = set(re.findall(r"resource 0x[0-9a-f]+ string/([A-Za-z0-9_.]+)", resources))
    token = strings.get("firebase_app_check_debug_token")
    if variant == "release":
        au.check("secrets.app-check-debug-token", "firebase_app_check_debug_token" not in names, "debug provider resource absent")
    else:
        au.check("secrets.app-check-debug-token", token in (None, ""), "library default is empty; no token is embedded")
    configured = sorted(GOOGLE_SERVICES_RESOURCES & names)
    au.check("firebase.unconfigured-build", firebase_configured or not configured, ", ".join(configured))
    au.check("network.worker-url-https-only", worker, "Worker host present (BuildConfig, https)")
    if variant == "release":
        au.check("release.r8-obfuscated", not app_classes_named, "app class names are not kept by R8")
    else:
        au.check("debug.not-obfuscated", app_classes_named, "debug keeps names (variant separation sanity check)")

    return {
        "tool": "focusmodeplus apk_audit.py",
        "apk": apk.name,
        "variant": variant,
        "applicationId": m.group(1) if m else None,
        "label": label.group(1) if label else None,
        "versionName": m.group(3) if m else None,
        "components": inventory,
        "permissions": sorted(perms),
        "documentationUrls": len(documentation_urls),
        "checks": au.checks,
        "failed": len(au.failed),
        "status": "fail" if au.failed else "pass",
    }


def compare(debug: dict, release: dict) -> dict:
    checks = [
        {"id": "separation.ids-differ", "status": "pass" if debug["applicationId"] != release["applicationId"] else "fail",
         "detail": f"{debug['applicationId']} / {release['applicationId']}"},
        {"id": "separation.debug-suffix", "status": "pass" if debug["applicationId"] == release["applicationId"] + ".debug" else "fail", "detail": ""},
        {"id": "separation.labels-differ", "status": "pass" if debug["label"] != release["label"] else "fail", "detail": f"{debug['label']} / {release['label']}"},
    ]
    failed = [c for c in checks if c["status"] == "fail"]
    return {"checks": checks, "failed": len(failed), "status": "fail" if failed else "pass"}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk")
    ap.add_argument("--variant", choices=sorted(EXPECT))
    ap.add_argument("--out")
    ap.add_argument("--manifest-out")
    ap.add_argument("--firebase-configured", action="store_true", help="owner build with google-services.json")
    ap.add_argument("--compare-debug")
    ap.add_argument("--compare-release")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()
    if args.self_test:
        return self_test()
    if args.compare_debug and args.compare_release:
        report = compare(json.loads(pathlib.Path(args.compare_debug).read_text()), json.loads(pathlib.Path(args.compare_release).read_text()))
    else:
        if not (args.apk and args.variant and args.out):
            ap.error("--apk, --variant and --out are required")
        report = audit_apk(pathlib.Path(args.apk), args.variant, pathlib.Path(args.manifest_out) if args.manifest_out else None, args.firebase_configured)
    if args.out:
        out = pathlib.Path(args.out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(report, indent=2) + "\n")
    for c in report["checks"]:
        if c["status"] == "fail":
            print(f"FAIL {c['id']}: {c['detail']}")
    print(f"{len(report['checks'])} checks, {report['failed']} failed -> {report['status']}")
    return 1 if report["status"] == "fail" else 0


if __name__ == "__main__":
    sys.exit(main())
