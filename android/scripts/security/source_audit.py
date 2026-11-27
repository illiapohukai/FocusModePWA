#!/usr/bin/env python3
"""Focus Mode+ v22 source-level security audit (stdlib only; run from the repository root).

Static checks that complement the APK audit:
  * production Kotlin: no logging/printing, no custom TLS trust or hostname verification, no
    WebView/JS bridges, no emulator endpoints, no world-readable files, immutable PendingIntents;
  * identity: the old application id appears only in the v22 migration documentation;
  * repository hygiene: no google-services.json, keystores, service-account or .env files are
    tracked, and no tracked text file contains a key-shaped secret;
  * trust boundaries: Rules and client agree on every reserved server field; the synchronized
    schema carries no app inventory, usage data or active Session; CI security steps cannot be
    silenced with continue-on-error or `|| true`.

  source_audit.py --out android/build/reports/security/source-audit.json
"""
from __future__ import annotations

import argparse
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path.cwd()
OLD_ID = "io.github.illiapohukai"
OLD_ID_ALLOWED = (
    "android/docs/application-identity-v22.md",
    "android/docs/security/",
    "android/scripts/security/",
    "CHANGELOG.md",
)
FORBIDDEN_KOTLIN = {
    "logging": re.compile(r"\bandroid\.util\.Log\b|\bLog\.(?:v|d|i|w|e|wtf)\(|\bprintln\(|\.printStackTrace\(|\bTimber\b|System\.(?:out|err)\b"),
    "custom-trust-manager": re.compile(r"X509TrustManager|TrustManagerFactory|checkServerTrusted|TrustAllCerts|trustAll", re.I),
    "hostname-verifier": re.compile(r"HostnameVerifier|hostnameVerifier\(|ALLOW_ALL_HOSTNAME|setDefaultHostnameVerifier"),
    "custom-ssl-socket-factory": re.compile(r"sslSocketFactory\(|SSLContext\.getInstance"),
    "cleartext-literal": re.compile(r"\"http://"),
    "webview": re.compile(r"\bWebView\b|addJavascriptInterface|setJavaScriptEnabled"),
    "emulator-endpoint": re.compile(r"useEmulator\(|10\.0\.2\.2|demo-focus-mode|localhost:\d"),
    "world-readable": re.compile(r"MODE_WORLD_(?:READABLE|WRITEABLE)"),
    "runtime-exec": re.compile(r"Runtime\.getRuntime\(\)\.exec|ProcessBuilder\(|DexClassLoader|PathClassLoader"),
    "mutable-pending-intent": re.compile(r"FLAG_MUTABLE"),
    "clipboard": re.compile(r"ClipboardManager|LocalClipboardManager|setPrimaryClip"),
    "secret-in-url": re.compile(r"[?&](?:key|api_key|apikey|token|access_token)=", re.I),
    # One-shot server reads only: a long-lived listener could outlive a sign-out or account switch.
    "firestore-listener": re.compile(r"addSnapshotListener|snapshots\(\)"),
}
TRACKED_FORBIDDEN = re.compile(r"(^|/)(google-services\.json|GoogleService-Info\.plist|service-account[^/]*\.json|credentials[^/]*\.json|\.env(\.[^/]*)?|local\.properties|keystore\.properties)$|\.(jks|keystore|p12|pfx|pem|key)$", re.I)
SECRET_TEXT = {
    "google-api-key": re.compile(r"AIza[0-9A-Za-z_\-]{35}"),
    "private-key": re.compile(r"-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----\s*[A-Za-z0-9+/=]{64}"),
    "github-token": re.compile(r"gh[pousr]_[A-Za-z0-9]{36}"),
    "aws-access-key": re.compile(r"AKIA[0-9A-Z]{16}"),
    "slack-token": re.compile(r"xox[baprs]-[A-Za-z0-9-]{10,}"),
    "service-account": re.compile(r"\"type\"\s*:\s*\"service_account\""),
}
TEXT_SUFFIXES = {".kt", ".kts", ".java", ".xml", ".json", ".md", ".js", ".cjs", ".mjs", ".html", ".css", ".yml", ".yaml", ".pro", ".properties", ".toml", ".txt", ".py", ".rules", ".webmanifest", ".gradle"}


class Report:
    def __init__(self) -> None:
        self.checks: list[dict] = []

    def check(self, cid: str, ok: bool, detail: str = "") -> None:
        self.checks.append({"id": cid, "status": "pass" if ok else "fail", "detail": detail[:600]})


def tracked() -> list[str]:
    return subprocess.run(["git", "ls-files", "--cached", "--others", "--exclude-standard"], check=True, capture_output=True, text=True).stdout.splitlines()


def strip_comments(kotlin: str) -> str:
    kotlin = re.sub(r"/\*.*?\*/", lambda m: "\n" * m.group(0).count("\n"), kotlin, flags=re.S)
    return re.sub(r"//[^\n]*", "", kotlin)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    rep = Report()
    files = tracked()

    # ------------------------------------------------------------------ production Kotlin
    main_kt = [f for f in files if f.startswith("android/") and "/src/main/" in f and f.endswith(".kt")]
    release_kt = [f for f in files if f.startswith("android/") and "/src/release/" in f and f.endswith(".kt")]
    for rule, rx in FORBIDDEN_KOTLIN.items():
        hits = []
        for f in main_kt + release_kt:
            for n, line in enumerate(strip_comments((ROOT / f).read_text()).splitlines(), 1):
                if rx.search(line):
                    hits.append(f"{f}:{n}")
        rep.check(f"kotlin.{rule}", not hits, ", ".join(hits[:8]))
    # Every PendingIntent factory call is immutable.
    loose = []
    for f in main_kt:
        text = strip_comments((ROOT / f).read_text())
        for m in re.finditer(r"PendingIntent\.get(?:Activity|Broadcast|Service|ForegroundService)\(", text):
            if "FLAG_IMMUTABLE" not in text[m.start(): m.start() + 400]:
                loose.append(f"{f}:{text[:m.start()].count(chr(10)) + 1}")
    rep.check("kotlin.pending-intents-immutable", not loose, ", ".join(loose))
    rep.check("kotlin.sources-found", len(main_kt) > 150, f"{len(main_kt)} production Kotlin files")

    # ------------------------------------------------------------------ manifests and XML policy
    manifests = [f for f in files if f.startswith("android/") and f.endswith("AndroidManifest.xml")]
    bad_manifest = [f for f in manifests if re.search(r"android:debuggable|usesCleartextTraffic=\"true\"|android:allowTaskReparenting=\"true\"", (ROOT / f).read_text())]
    rep.check("manifest.no-debug-or-cleartext-flags", not bad_manifest, ", ".join(bad_manifest))
    nsc = (ROOT / "android/app/src/main/res/xml/network_security_config.xml").read_text()
    rep.check("network.config-cleartext-off", 'cleartextTrafficPermitted="false"' in nsc and 'cleartextTrafficPermitted="true"' not in nsc and 'src="user"' not in nsc and "debug-overrides" not in nsc)
    provider_paths = [f for f in files if f.endswith("_paths.xml") or f.endswith("file_paths.xml")]
    wide = [f for f in provider_paths if re.search(r"<root-path|<files-path[^>]*path=\"(?:\.|/)?\"|<external[^>]*path=\"(?:\.|/)?\"|<cache-path[^>]*path=\"(?:\.|/)?\"", (ROOT / f).read_text())]
    rep.check("fileprovider.narrow-paths", bool(provider_paths) and not wide, ", ".join(provider_paths + wide))

    # ------------------------------------------------------------------ identity
    leftovers = []
    for f in files:
        if pathlib.Path(f).suffix not in TEXT_SUFFIXES or f.startswith(OLD_ID_ALLOWED):
            continue
        try:
            if OLD_ID in (ROOT / f).read_text(errors="ignore"):
                leftovers.append(f)
        except OSError:
            continue
    rep.check("identity.old-id-only-in-migration-docs", not leftovers, ", ".join(leftovers[:10]))
    gradle = (ROOT / "android/app/build.gradle.kts").read_text()
    rep.check("identity.application-id", 'applicationId = "app.focusmodeplus"' in gradle and 'applicationIdSuffix = ".debug"' in gradle)
    ext = (ROOT / "android/build-logic/convention/src/main/kotlin/ProjectExtensions.kt").read_text()
    rep.check("identity.namespace", 'BASE_NAMESPACE = "app.focusmodeplus"' in ext)
    # Gradle convention plugins (build-logic) use the default package by design.
    misplaced = [f for f in files if re.search(r"android/.+/src/[^/]+/kotlin/", f) and f.endswith(".kt") and "/kotlin/app/focusmodeplus/" not in f and not f.startswith("android/build-logic/")]
    rep.check("identity.kotlin-packages-moved", not misplaced, ", ".join(misplaced[:10]))

    # ------------------------------------------------------------------ hygiene
    forbidden_files = [f for f in files if TRACKED_FORBIDDEN.search(f)]
    rep.check("hygiene.no-credential-files-tracked", not forbidden_files, ", ".join(forbidden_files))
    for rule, rx in SECRET_TEXT.items():
        hits = []
        for f in files:
            if pathlib.Path(f).suffix not in TEXT_SUFFIXES:
                continue
            try:
                if rx.search((ROOT / f).read_text(errors="ignore")):
                    hits.append(f)
            except OSError:
                continue
        rep.check(f"hygiene.secret.{rule}", not hits, ", ".join(hits[:10]))
    generated = [f for f in files if re.search(r"(^|/)build/|\.apk$|\.aab$|firebase-debug\.log$|firestore-debug\.log$|(^|/)emulator-data/", f)]
    rep.check("hygiene.no-build-or-emulator-output", not generated, ", ".join(generated[:10]))

    # ------------------------------------------------------------------ trust boundaries
    rules = (ROOT / "firebase/firestore.rules").read_text()
    schema = (ROOT / "android/core/domain/src/main/kotlin/app/focusmodeplus/core/domain/sync/RemoteSchema.kt").read_text()
    rules_reserved = set(re.findall(r"'([A-Za-z]+)'", re.search(r"function reservedFields\(\) \{(.*?)\}", rules, re.S).group(1)))
    client_reserved = set(re.findall(r"\"([A-Za-z]+)\"", re.search(r"RESERVED_SERVER_FIELDS: List<String> = listOf\((.*?)\)", schema, re.S).group(1)))
    rep.check("trust.reserved-fields-match", rules_reserved == client_reserved and len(rules_reserved) >= 14, f"rules-only={sorted(rules_reserved - client_reserved)} client-only={sorted(client_reserved - rules_reserved)}")
    fields_block = re.search(r"object Fields \{(.*?)\n    \}", schema, re.S).group(1)
    synced = set(re.findall(r"\"([A-Za-z]+)\"", fields_block))
    leaked = sorted(f for f in synced if re.search(r"(?i)package|usage|installed|selectedApp|appInventory|distraction|foreground|ownerEmail|email|token", f))
    rep.check("trust.no-app-inventory-or-usage-synced", not leaked, ", ".join(leaked))
    league = sorted(f for f in synced if f in client_reserved)
    rep.check("trust.no-reserved-field-in-client-schema", not league, ", ".join(league))
    segments = re.findall(r"\(\"([A-Za-z]+)\"\)", re.search(r"enum class RemoteCollection[^{]*\{(.*?)\}", (ROOT / "android/core/domain/src/main/kotlin/app/focusmodeplus/core/domain/sync/RemoteSyncStore.kt").read_text(), re.S).group(1))
    rep.check("trust.no-active-session-collection", bool(segments) and not any("active" in s.lower() for s in segments), ", ".join(segments))
    rep.check("trust.rules-deny-by-default", "allow read, write: if true" not in rules and "allow read: if true" not in rules and "{document=**}" not in rules)

    # ------------------------------------------------------------------ CI
    wf = (ROOT / ".github/workflows/android.yml").read_text()
    rep.check("ci.no-continue-on-error", "continue-on-error" not in wf)
    rep.check("ci.no-silenced-failures", not re.search(r"\|\|\s*true|\|\|\s*exit 0|set \+e", wf))
    rep.check("ci.no-production-credentials", not re.search(r"secrets\.(?!GITHUB_TOKEN)|GOOGLE_APPLICATION_CREDENTIALS|FIREBASE_TOKEN|google-services\.json\s*<<", wf))

    failed = [c for c in rep.checks if c["status"] == "fail"]
    report = {"tool": "focusmodeplus source_audit.py", "checks": rep.checks, "failed": len(failed), "status": "fail" if failed else "pass"}
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + "\n")
    for c in failed:
        print(f"FAIL {c['id']}: {c['detail']}")
    print(f"{len(rep.checks)} checks, {len(failed)} failed -> {report['status']}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
