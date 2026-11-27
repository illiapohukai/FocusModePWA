#!/usr/bin/env python3
"""Focus Mode+ v22 machine-readable security summary (stdlib only).

Collects, without any runtime user data:
  * JUnit results of the security regression grouping (classes named SecurityV22*) and of the
    complete JVM suite, from android/**/build/test-results;
  * the Firestore Rules JUnit report (node:test --test-reporter=junit);
  * apk_audit.py and dependency_audit.py JSON reports and the npm audit JSON.

  security_summary.py --android-root android --rules-junit firebase/reports/rules-junit.xml \
      --report-dir android/build/reports/security --out android/build/reports/security/security-summary.json

Exit status 1 when anything failed, so the CI job cannot pass with a red component.
"""
from __future__ import annotations

import argparse
import datetime as dt
import glob
import json
import pathlib
import sys
import xml.etree.ElementTree as ET


# Every v22 security regression class must report results; a missing class fails the summary.
EXPECTED_SECURITY_CLASSES = {
    "SecurityV22SyncTest", "SecurityV22BackupAdversarialTest", "SecurityV22DeepLinkParserTest", "SecurityV22DeepLinkAuthTest",
    "SecurityV22AttachmentTest", "SecurityV22NetworkTest", "SecurityV22ManifestAndStorageTest",
}


def junit(paths: list[str], only_security: bool) -> dict:
    total = failed = skipped = 0
    classes: dict[str, dict] = {}
    for p in paths:
        root = ET.parse(p).getroot()
        # Each <testcase> exactly once (node:test nests <testsuite> elements for describe blocks).
        for case in root.iter("testcase"):
            cls = case.get("classname") or pathlib.Path(p).stem
            if only_security and "SecurityV22" not in cls:
                continue
            total += 1
            bad = case.find("failure") is not None or case.find("error") is not None
            skip = case.find("skipped") is not None
            failed += bad
            skipped += skip
            entry = classes.setdefault(cls.split(".")[-1], {"tests": 0, "failed": 0})
            entry["tests"] += 1
            entry["failed"] += bad
    return {"tests": total, "failed": failed, "skipped": skipped, "classes": classes}


def load(path: pathlib.Path) -> dict | None:
    return json.loads(path.read_text()) if path.is_file() else None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--android-root", default="android")
    ap.add_argument("--rules-junit", action="append", default=[])
    ap.add_argument("--report-dir", required=True)
    ap.add_argument("--npm-audit")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    results = glob.glob(f"{args.android_root}/**/build/test-results/**/TEST-*.xml", recursive=True)
    rd = pathlib.Path(args.report_dir)
    summary: dict = {
        "tool": "focusmodeplus security_summary.py",
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "jvmSuite": junit(results, only_security=False),
        "securityRegression": junit(results, only_security=True),
        "firestoreRules": junit([p for p in args.rules_junit if pathlib.Path(p).is_file()], only_security=False),
        "apk": {},
        "sourceAudit": None,
        "dependencies": None,
        "npmAudit": None,
    }
    for variant in ("debug", "release"):
        r = load(rd / f"apk-{variant}.json")
        summary["apk"][variant] = None if r is None else {"status": r["status"], "checks": len(r["checks"]), "failed": r["failed"], "applicationId": r["applicationId"], "label": r["label"]}
    sep = load(rd / "apk-separation.json")
    summary["apk"]["separation"] = None if sep is None else {"status": sep["status"], "failed": sep["failed"]}
    src = load(rd / "source-audit.json")
    summary["sourceAudit"] = None if src is None else {"status": src["status"], "checks": len(src["checks"]), "failed": src["failed"]}
    dep = load(rd / "dependency-audit.json")
    if dep:
        summary["dependencies"] = {"status": dep["status"], "coordinates": dep["coordinates"], "advisories": len(dep["findings"]), "fatal": dep["fatal"],
                                   "bySeverity": {s: sum(1 for f in dep["findings"] if f["severity"] == s) for s in sorted({f["severity"] for f in dep["findings"]})}}
    if args.npm_audit and pathlib.Path(args.npm_audit).is_file():
        meta = (json.loads(pathlib.Path(args.npm_audit).read_text()).get("metadata") or {}).get("vulnerabilities", {})
        summary["npmAudit"] = {"vulnerabilities": meta, "status": "fail" if meta.get("high", 0) or meta.get("critical", 0) else "pass"}

    problems = []
    for name in ("jvmSuite", "securityRegression", "firestoreRules"):
        s = summary[name]
        # The JVM total is informational in the security job (it runs the grouping only).
        if s["failed"] or (s["tests"] == 0 and name != "jvmSuite"):
            problems.append(f"{name}: {s['tests']} tests, {s['failed']} failed")
    missing = sorted(EXPECTED_SECURITY_CLASSES - set(summary["securityRegression"]["classes"]))
    if missing:
        problems.append("securityRegression: missing " + ", ".join(missing))
    for key, value in summary["apk"].items():
        if value is None or value["status"] != "pass":
            problems.append(f"apk.{key}: {value}")
    for key in ("sourceAudit", "dependencies", "npmAudit"):
        if summary[key] is None or summary[key]["status"] != "pass":
            problems.append(f"{key}: {summary[key]}")
    summary["status"] = "fail" if problems else "pass"
    summary["problems"] = problems

    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps({k: summary[k] for k in ("status", "problems")}, indent=2))
    print(f"security regression: {summary['securityRegression']['tests']} tests; JVM: {summary['jvmSuite']['tests']}; Rules: {summary['firestoreRules']['tests']}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
