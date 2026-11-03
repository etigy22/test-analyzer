# RepoGuard Agent Workflow

RepoGuard is developed inside a Git checkout opened in VS Code or IntelliJ IDEA. The agent reads the four binding reference documents before implementation work and changes files directly in the checkout.

## Required workflow

1. Read `github_analyzer_projektzusammenfassung.md`, `REQUIREMENTS.md`, `DESIGN.md`, and `ACCEPTANCE_CRITERIA.md`.
2. Plan the change and map it to acceptance criteria.
3. Modify RepoGuard application files only.
4. Build and test RepoGuard itself where the environment allows it.
5. Never build, test, execute, install dependencies for, or load executable configuration from imported target repositories.
6. Review the Git diff and document test evidence before any human-approved commit or push.

## Continuation: GitHub import foundation, 2026-10-29

The user prompted the IDE agent with `contiune`. The agent inspected the existing scaffold and implementation plan, read the four binding reference documents, and continued the next P0 import component in this same checkout. Changes cover strict GitHub URL parsing, default-branch archive URLs, DNS-bound public destination policy, bounded quarantine downloads, cleanup, backend/API tests, and test reporting.

Java 21 is installed. The agent downloaded Maven 3.9.11 from Maven Central into ignored `.tools/` and used a project-local dependency cache. Initial sandbox networking prevented dependency resolution; an approved network-enabled Maven invocation populated that cache. Subsequent backend tests ran offline. These builds/tests concern RepoGuard only; no target repository was downloaded or executed during the test suite.

Review both `git diff` (changes to already staged scaffold files) and `git ls-files --others --exclude-standard` (new files). Run `mvn -f backend/pom.xml test`, then `./tests/Write-TestReport.ps1` to refresh evidence. `TEST_REPORT.md` documents what the tests establish and what remains untested. No commit or push has been made; the user's existing staged scaffold remains staged as it was. Full AGENT/E2E acceptance, including a reviewed commit, is still pending.

## Initial environment note

This initial agent run used IntelliJ/Codex tooling in `C:\Projekte\test-analyzer`. Git required `-c safe.directory=C:/Projekte/test-analyzer` because the sandbox user differs from the repository owner.

## Original GitHub acceptance probes, 2026-11-02

The user explicitly referenced ACCEPTANCE_CRITERIA.md, REQUIREMENTS.md and DESIGN.md, challenged the previous omission of seven claude-mem symlinks, and requested ten actual GitHub projects. The agent reread the four binding documents. Changes in this checkout cover bounded validation/materialization of internal file links (IMP-11), readable sanitized paths (ANA-06), priority-based bounded findings (ANA-09), and documented malware time limits. No binding requirement was weakened or marked complete by changing its wording.

Tests: 98 backend tests and six browser tests passed. The native PowerShell harness submitted ten unmodified official archives through the normal GitHub API endpoint of RepoGuard, not the GitHub REST API. Four real-project probes met their stated expectations; six failed. All results and known limitations are in docs/REAL_REPOSITORY_ACCEPTANCE.md, with a separate mandatory-criterion matrix in docs/ACCEPTANCE_STATUS.md. Foreign builds, tests, installers, hooks and configuration scripts were never invoked. Project acceptance remains false. Review the working-tree diff and new files; no commit or push was made.
