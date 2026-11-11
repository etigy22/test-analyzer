# Local test evidence

Generated scan results, logs, browser artifacts, input manifests and evidence archives in this directory stay local and are ignored by Git. They are not required to build or run RepoGuard.

Commit the source, test fixtures and scripts, plus the concise reports `TEST_REPORT.md`, `test-results.json`, `docs/ACCEPTANCE_STATUS.md` and dated review documents. Detailed evidence paths in those summaries refer to local files and may be absent in a fresh checkout. Missing evidence is not a new PASS; rerun the applicable tests or obtain the recorded evidence separately when independent verification is required.

## Generate evidence

- Backend tests: `mvn -f backend/pom.xml test` (JUnit output: `backend/target/surefire-reports`).
- Frontend/browser tests: `cd frontend` and run `npm run test:e2e` against the configured local stack (output: `frontend/test-results`).
- Passive self-scan of the current checkout: `python3 tests/run-self-review.py --label after` with RepoGuard running locally. This writes local manifests, summaries and lossless compressed raw results under `self-review-2026-11-11/`.

Keep evidence locally or share a selected, sanitized evidence bundle separately when needed. Do not commit credentials, full imported repositories or unmasked secrets. Existing local evidence is retained when Git tracking is removed. Previously committed evidence remains in Git history; no history rewrite is performed.
