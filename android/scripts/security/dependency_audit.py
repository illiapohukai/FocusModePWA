#!/usr/bin/env python3
"""Focus Mode+ v22 dependency advisory audit (stdlib only, bounded network use).

Reads the resolved Gradle runtime classpaths (``./gradlew :app:dependencies --configuration
<name>RuntimeClasspath`` output), queries the OSV database (https://osv.dev, the open advisory
source behind GitHub's Maven/npm data) with one batch request plus one detail request per
advisory, and writes a machine-readable report.

Policy: a CRITICAL or HIGH advisory against a shipped dependency fails the audit unless it is
listed in ``advisory-allowlist.json`` with a reviewed justification and an expiry date.
MODERATE/LOW findings are reported, not fatal (documented in
android/docs/security/security-findings-v22.md). No credentials, no uploads of project data:
only public ``group:artifact`` coordinates and versions are sent.

Usage:
  dependency_audit.py --gradle-report deps-release.txt [--gradle-report deps-debug.txt]
                      --out build/reports/security/dependency-audit.json
  dependency_audit.py --self-test
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import pathlib
import re
import sys
import time
import urllib.error
import urllib.request

OSV_BATCH = "https://api.osv.dev/v1/querybatch"
OSV_VULN = "https://api.osv.dev/v1/vulns/{}"
TIMEOUT_S = 30
MAX_QUERIES = 1000  # OSV batch limit
MAX_DETAILS = 200  # bounded detail lookups
FATAL = {"CRITICAL", "HIGH"}

# "+--- group:artifact:requested -> resolved (*)" and BOM-managed "+--- group:artifact -> resolved";
# project and constraint lines are skipped.
LINE = re.compile(r"^[|\\+ \-]*(?:\+---|\\---)\s+([A-Za-z0-9_.\-]+):([A-Za-z0-9_.\-]+)(?::([^\s]+))?(?:\s+->\s+([^\s]+))?(.*)$")


def parse_gradle(text: str) -> set[tuple[str, str, str]]:
    coords: set[tuple[str, str, str]] = set()
    for raw in text.splitlines():
        m = LINE.match(raw)
        if not m:
            continue
        group, artifact, requested, resolved, tail = m.groups()
        if "(c)" in tail or "(n)" in tail or "FAILED" in tail:
            continue
        version = (resolved or requested or "").strip()
        if version.startswith("{") or version in {"", "+"}:
            continue
        coords.add((group, artifact, version))
    return coords


def http_json(url: str, body: dict | None = None, retries: int = 2) -> dict:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method="POST" if data else "GET",
                                 headers={"Content-Type": "application/json", "User-Agent": "focusmodeplus-security-audit"})
    for attempt in range(retries + 1):
        try:
            with urllib.request.urlopen(req, timeout=TIMEOUT_S) as resp:
                return json.loads(resp.read(8 * 1024 * 1024).decode())
        except (urllib.error.URLError, TimeoutError) as exc:  # bounded retry, no retry storm
            if attempt == retries:
                raise SystemExit(f"OSV request failed after {retries + 1} attempts: {exc}")
            time.sleep(2 * (attempt + 1))
    raise AssertionError("unreachable")


def severity_of(vuln: dict) -> str:
    sev = (vuln.get("database_specific") or {}).get("severity")
    if isinstance(sev, str) and sev:
        return sev.upper().replace("MODERATE", "MODERATE")
    for aff in vuln.get("affected") or []:
        s = (aff.get("database_specific") or {}).get("severity")
        if isinstance(s, str) and s:
            return s.upper()
    return "UNKNOWN"


def fixed_versions(vuln: dict, package: str) -> list[str]:
    out: list[str] = []
    for aff in vuln.get("affected") or []:
        if (aff.get("package") or {}).get("name") != package:
            continue
        for rng in aff.get("ranges") or []:
            for ev in rng.get("events") or []:
                if "fixed" in ev:
                    out.append(ev["fixed"])
    return sorted(set(out))


def load_allowlist(path: pathlib.Path) -> dict[str, dict]:
    if not path.is_file():
        return {}
    entries = json.loads(path.read_text()).get("allow", [])
    today = dt.date.today().isoformat()
    out = {}
    for e in entries:
        if not e.get("reason") or not e.get("expires"):
            raise SystemExit(f"allow-list entry {e.get('id')} needs a reason and an expiry date")
        if e["expires"] < today:
            print(f"allow-list entry {e['id']} expired on {e['expires']}: it no longer applies", file=sys.stderr)
            continue
        out[f"{e['id']}|{e['package']}"] = e
    return out


def audit(coords: set[tuple[str, str, str]], allow: dict[str, dict], fetch=http_json) -> dict:
    items = sorted(coords)
    if len(items) > MAX_QUERIES:
        raise SystemExit(f"{len(items)} coordinates exceed the bounded batch size {MAX_QUERIES}")
    queries = [{"package": {"name": f"{g}:{a}", "ecosystem": "Maven"}, "version": v} for g, a, v in items]
    batch = fetch(OSV_BATCH, {"queries": queries}) if queries else {"results": []}
    results = batch.get("results", [])
    findings = []
    detail_calls = 0
    for (g, a, v), res in zip(items, results):
        for ref in res.get("vulns") or []:
            vid = ref.get("id")
            if not vid:
                continue
            detail_calls += 1
            if detail_calls > MAX_DETAILS:
                raise SystemExit("too many advisories for the bounded audit; review manually")
            vuln = fetch(OSV_VULN.format(vid))
            pkg = f"{g}:{a}"
            sev = severity_of(vuln)
            key = f"{vid}|{pkg}"
            findings.append({
                "id": vid,
                "aliases": vuln.get("aliases", []),
                "package": pkg,
                "version": v,
                "severity": sev,
                "summary": (vuln.get("summary") or "")[:200],
                "fixed": fixed_versions(vuln, pkg),
                "allowlisted": key in allow,
                "justification": allow.get(key, {}).get("reason"),
            })
    fatal = [f for f in findings if f["severity"] in FATAL and not f["allowlisted"]]
    return {
        "tool": "focusmodeplus dependency_audit.py",
        "source": "OSV (api.osv.dev)",
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "coordinates": len(items),
        "findings": findings,
        "fatal": len(fatal),
        "status": "fail" if fatal else "pass",
    }


def self_test() -> int:
    sample = """
releaseRuntimeClasspath - Runtime classpath of 'release'.
+--- project :core:model
|    \\--- org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0
+--- com.google.firebase:firebase-auth -> 25.0.0
+--- com.squareup.okhttp3:okhttp:5.5.0 -> 5.5.1
|    \\--- com.squareup.okio:okio:3.15.0 (*)
+--- androidx.core:core-ktx:1.19.1 (c)
\\--- com.google.firebase:firebase-bom:35.2.0
     \\--- com.google.firebase:firebase-firestore:{strictly 26.0.0} -> 26.0.0 (c)
"""
    coords = parse_gradle(sample)
    assert ("com.squareup.okhttp3", "okhttp", "5.5.1") in coords, coords
    assert ("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.13.0") in coords
    assert ("com.squareup.okio", "okio", "3.15.0") in coords
    assert ("com.google.firebase", "firebase-auth", "25.0.0") in coords, "BOM-managed lines are read"
    assert not any(a == "core-ktx" for _, a, _ in coords), "constraints are skipped"

    def fake(url, body=None):
        if url == OSV_BATCH:
            return {"results": [{"vulns": [{"id": "GHSA-test-high"}]} if q["package"]["name"].endswith(":okhttp") else {} for q in body["queries"]]}
        return {"id": "GHSA-test-high", "summary": "test", "database_specific": {"severity": "HIGH"},
                "affected": [{"package": {"name": "com.squareup.okhttp3:okhttp"}, "ranges": [{"events": [{"introduced": "0"}, {"fixed": "5.5.2"}]}]}]}

    report = audit(coords, {}, fetch=fake)
    assert report["status"] == "fail" and report["fatal"] == 1 and report["findings"][0]["fixed"] == ["5.5.2"], report
    allowed = audit(coords, {"GHSA-test-high|com.squareup.okhttp3:okhttp": {"reason": "unreachable", "expires": "2999-01-01"}}, fetch=fake)
    assert allowed["status"] == "pass", allowed
    print("dependency_audit self-test: ok")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--gradle-report", action="append", default=[])
    ap.add_argument("--allowlist", default=str(pathlib.Path(__file__).with_name("advisory-allowlist.json")))
    ap.add_argument("--out")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()
    if args.self_test:
        return self_test()
    if not args.gradle_report or not args.out:
        ap.error("--gradle-report and --out are required")
    coords: set[tuple[str, str, str]] = set()
    for p in args.gradle_report:
        coords |= parse_gradle(pathlib.Path(p).read_text())
    if len(coords) < 20:
        raise SystemExit(f"only {len(coords)} dependencies parsed; the Gradle report looks wrong")
    report = audit(coords, load_allowlist(pathlib.Path(args.allowlist)))
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + "\n")
    for f in report["findings"]:
        flag = "ALLOWLISTED" if f["allowlisted"] else ("FATAL" if f["severity"] in FATAL else "report")
        print(f"[{flag}] {f['severity']:9} {f['id']} {f['package']}:{f['version']} fixed={','.join(f['fixed']) or '-'}")
    print(f"{report['coordinates']} dependencies checked, {len(report['findings'])} advisories, {report['fatal']} fatal -> {report['status']}")
    return 1 if report["status"] == "fail" else 0


if __name__ == "__main__":
    sys.exit(main())
