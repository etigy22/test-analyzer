#!/usr/bin/env python3
"""Run an auditable passive self-scan against a local, already running RepoGuard.
No target project commands, build tools, hooks or dependency resolution are invoked.
"""
import argparse
from collections import Counter
import hashlib
import gzip
import json
from pathlib import Path
import subprocess
import time
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/test-evidence/self-review-2026-11-11"
BASE = "http://localhost:8088"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--label", required=True, choices=["same-input", "after"])
    parser.add_argument("--archive", type=Path)
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    archive = args.archive
    if archive is None:
        tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")
        added = subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard", "-z", "--", "backend/src", "tests"], cwd=ROOT).decode().split("\0")
        files = sorted({name for name in tracked + added if name and (ROOT / name).is_file()})
        if any(Path(name).name == ".env" or Path(name).name.startswith(".env.") and Path(name).name != ".env.example" for name in files):
            raise RuntimeError("Credential environment files must never enter a self-scan archive.")
        archive = OUT / (args.label + ".zip")
        with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as output:
            for name in files:
                output.write(ROOT / name, "repoguard/" + name)
        manifest = {"files": len(files), "entries": {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest() for name in files},
                    "testFiles": [name for name in files if "/test" in name or name.startswith("tests/")],
                    "sha256": hashlib.sha256(archive.read_bytes()).hexdigest()}
        (OUT / (args.label + "-input.json")).write_text(json.dumps(manifest, indent=2))
    ready_deadline = time.monotonic() + 120
    while True:
        try:
            with urllib.request.urlopen(BASE + "/api/health", timeout=5) as response:
                if response.status == 200:
                    break
        except OSError:
            if time.monotonic() >= ready_deadline:
                raise
        time.sleep(2)
    boundary = "RepoGuardSelfReview20261111"
    data = ("--" + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="repoguard-' + args.label + '.zip"\r\nContent-Type: application/zip\r\n\r\n').encode() + archive.read_bytes() + ("\r\n--" + boundary + "--\r\n").encode()
    request = urllib.request.Request(BASE + "/api/scans/zip", data=data,
        headers={"Content-Type": "multipart/form-data; boundary=" + boundary, "X-RepoGuard-Request": "true"})
    job = json.load(urllib.request.urlopen(request, timeout=30))
    (OUT / (args.label + "-job.json")).write_text(json.dumps(job, indent=2))
    print(args.label, job["id"], flush=True)
    deadline = time.monotonic() + 720
    while time.monotonic() < deadline:
        job = json.load(urllib.request.urlopen(BASE + "/api/scans/" + job["id"], timeout=20))
        if job.get("completedAt"):
            break
        time.sleep(2)
    else:
        raise TimeoutError("The self-scan did not reach a terminal state.")
    (OUT / (args.label + "-result.json.gz")).write_bytes(gzip.compress(json.dumps(job, indent=2).encode(), mtime=0))
    result = job.get("result") or {}
    summary = {"id": job["id"], "status": job["status"], "archiveSha256": result.get("archiveSha256"),
        "rulesVersion": result.get("rulesVersion"), "severities": dict(Counter(f["severity"] for f in result.get("findings", []))),
        "highFindings": [{k: f[k] for k in ["tool", "ruleId", "path", "line"]} for f in result.get("findings", []) if f["severity"] in ["HIGH", "CRITICAL"]],
        "coverage": result.get("coverage"), "durationMillis": result.get("durationMillis")}
    (OUT / (args.label + "-summary.json")).write_text(json.dumps(summary, indent=2))
    print(json.dumps({k: v for k, v in summary.items() if k != "coverage"}, indent=2), flush=True)
    if job["status"] != "COMPLETED":
        raise RuntimeError("Self-scan is incomplete; inspect the retained result and coverage.")


if __name__ == "__main__":
    main()
