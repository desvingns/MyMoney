"""Offline security-review reproductions; no real credentials, network or device calls."""

import difflib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid


MM = Path("D:/Pet/MyMoney")
MP = Path("D:/Pet/mobile-pipeline")
OUT = Path(__file__).parent
FIXTURE = Path("D:/Pet/archive/mobile-pipeline/2026-09-27/security-review") / uuid.uuid4().hex[:8]
BASH = shutil.which("bash")
assert BASH, "Git Bash is required"
RESULTS = []


def run(args, cwd=None, env=None):
    return subprocess.run(args, cwd=cwd, env=env, text=True, capture_output=True, check=True)


def record(name, evidence):
    result = "STATIC_CONFIRMED" if evidence.get("execution", "").startswith("static") else "REPRODUCED"
    RESULTS.append({"check": name, "result": result, "evidence": evidence})


def shell(code, *args, cwd=None, env=None):
    return run([BASH, "-c", code, "audit", *args], cwd=cwd, env=env)


def main():
    FIXTURE.mkdir(parents=True, exist_ok=False)
    index = Path("D:/Pet/archive/INDEX.md")
    with index.open("a", encoding="utf-8") as out:
        out.write(f"\n2026-09-27 | security-review fixtures (created, no source moved) | {FIXTURE.as_posix()} | preserve offline audit evidence; no deletion\n")

    source = (MP / "templates/common/scripts/{{PREFIX}}-propose-improvement.sh").read_text()
    prefix = source.split("git add -A", 1)[0]
    assert "git apply \"$PATCH_REL\"" in prefix
    assert "./lib/build-marketplace.sh" in prefix
    repo = FIXTURE / "proposal-repo"
    (repo / "lib").mkdir(parents=True)
    (repo / ".ai/proposals").mkdir(parents=True)
    (repo / ".ai/changes").mkdir(parents=True)
    before = "#!/usr/bin/env bash\nprintf 'ORIGINAL\\n'\n"
    after = "#!/usr/bin/env bash\nprintf 'PATCH_EXECUTED\\n' > patch-execution-marker.txt\n"
    (repo / "lib/build-marketplace.sh").write_text(before)
    (repo / ".ai/changes/agent-skill-log.md").write_text("# Audit fixture\n")
    (repo / ".gitignore").write_text((MP / ".gitignore").read_text())
    run(["git", "init", "-b", "main", str(repo)])
    run(["git", "config", "user.name", "Offline Security Audit"], cwd=repo)
    run(["git", "config", "user.email", "audit@example.invalid"], cwd=repo)
    shell('chmod +x lib/build-marketplace.sh', cwd=repo)
    run(["git", "add", "."], cwd=repo)
    run(["git", "commit", "-m", "fixture baseline"], cwd=repo)
    patch = "".join(difflib.unified_diff(before.splitlines(True), after.splitlines(True),
                                      fromfile="a/lib/build-marketplace.sh", tofile="b/lib/build-marketplace.sh"))
    (repo / ".ai/proposals/audit.patch").write_text(patch)
    (repo / ".ai/proposals/audit.changelog").write_text("\nAudit fixture\n")
    prefix_path = FIXTURE / "proposal-prefix.sh"
    prefix_path.write_text(prefix, encoding="utf-8", newline="\n")
    shell('bash "$1" "$2" audit .ai/proposals/audit.patch .ai/proposals/audit.changelog',
          prefix_path.as_posix(), repo.as_posix())
    marker = (repo / "patch-execution-marker.txt").read_text().strip()
    assert marker == "PATCH_EXECUTED"
    record("MP-01 patch outside templates executes during regeneration", {
        "patched_path": "lib/build-marketplace.sh", "marker": marker,
        "execution": "exact source prefix, stopped before staging/commit/push"})

    (repo / ".env.backup").write_text("AUDIT_FAKE_SECRET=not-a-real-credential\n")
    (repo / "unrelated-private-note.txt").write_text("AUDIT PRIVATE NOTE, NOT REAL USER DATA\n")
    run(["git", "add", "-A"], cwd=repo)
    staged = run(["git", "diff", "--cached", "--name-only"], cwd=repo).stdout.splitlines()
    assert ".env.backup" in staged and "unrelated-private-note.txt" in staged
    record("MP-02 direct proposal stages unrelated files", {
        "unexpected_staged": [".env.backup", "unrelated-private-note.txt"],
        "execution": "git add -A with actual mobile-pipeline ignore rules, offline fixture only"})

    url_code = source.split("RP=$(", 1)[1].split('\nif [ -n "${GITHUB_TOKEN:-}"', 1)[0]
    url_code = "RP=$(" + url_code
    env = os.environ.copy()
    env["GITHUB_TOKEN"] = "AUDIT_FAKE_TOKEN"
    result = shell('git() { printf "%s\\n" "https://audit.example.invalid/owner/repo.git"; };\n'
                   + url_code + '\nprintf "%s" "https://x-access-token:${GITHUB_TOKEN}@${RP}"', env=env)
    assert "AUDIT_FAKE_TOKEN@audit.example.invalid" in result.stdout
    record("MP-03 GitHub token forwarded to arbitrary remote host", {
        "constructed_url": result.stdout, "execution": "extracted URL construction, fake git remote, no git push"})

    adb_script = (MP / "templates/spec/skills/app-spec-creator/scripts/crawl/input.sh").read_text()
    assert 'ESC="${STR// /%s}"' in adb_script
    assert 'adbx shell input text "$ESC"' in adb_script
    result = shell('input() { :; }; STR="$1"; ESC="${STR// /%s}"; '
                   'remote="input text $ESC"; eval "$remote"', "safe;printf\tAUDIT_ADB_COMMAND_EXECUTED")
    assert result.stdout == "AUDIT_ADB_COMMAND_EXECUTED"
    record("MP-04 remote adb shell command injection", {
        "marker": result.stdout, "execution": "exact space replacement, local simulation of AOSP argument join; no adb/device call"})

    backup = (MM / "core/database/src/main/java/com/kshavrin/mymoney/core/database/repository/BackupRepositoryImpl.kt").read_text()
    field_source = backup.split("private fun csvField", 1)[1].split("private fun parseCsv", 1)[0]
    assert not any(x in field_source for x in ("'='", "'+'", "'@'"))
    def csv_field(value):
        return '"' + value.replace('"', '""') + '"' if any(c in value for c in ',"\r\n') else value
    formula = csv_field("=1+1")
    assert formula == "=1+1"
    record("MM-04 CSV formula prefix survives export", {
        "input": "=1+1", "exported_field": formula,
        "execution": "faithful Python translation of the Kotlin csvField function; no spreadsheet opened"})

    supporter = (MM / "supabase/migrations/20260813090000_supporter_purchases.sql").read_text()
    assert "grant select, insert on table public.supporter_purchases to authenticated" in supporter
    assert "with check ((select auth.uid()) = user_id)" in supporter
    assert "new.purchase_token" in supporter and "after insert" in supporter
    record("MM-01 client-authored purchase grants supporter", {
        "execution": "static migration contract only, not a running Postgres test",
        "checks": ["authenticated INSERT granted", "RLS checks own user only", "trigger grants supporter from client purchase_token"]})

    rtdn = (MM / "supabase/functions/google-play-rtdn/index.ts").read_text()
    handler = rtdn.split("Deno.serve", 1)[1]
    assert "voidedPurchaseNotification" not in handler
    assert 'let result: Record<string, unknown> = { action: "event_recorded" }' in handler
    record("MM-03 voided purchase has no reconciliation branch", {
        "execution": "static handler contract only, no Google/RTDN request",
        "checks": ["token parser accepts voided notifications", "handler has no voided branch", "default event recorded as processed"]})

    output = {"fixture_root": str(FIXTURE), "checks": RESULTS,
              "boundaries": "No real secrets, external writes, device calls, source fixes or deletion. Backend checks are static."}
    (OUT / "evidence.json").write_text(json.dumps(output, indent=2), encoding="utf-8")
    print(json.dumps({"checks": len(RESULTS), "evidence": str(OUT / "evidence.json"), "fixture_root": str(FIXTURE)}))


if __name__ == "__main__":
    main()
