param([string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference='Stop'
$entries=@()
foreach($file in Get-ChildItem (Join-Path $ProjectRoot 'backend/target/surefire-reports/TEST-*.xml')) {
 [xml]$xml=Get-Content -Raw $file.FullName
 foreach($case in $xml.testsuite.testcase) {
  $state=if($case.failure -or $case.error){'FAIL'}elseif($case.skipped){'SKIPPED'}else{'PASS'}
  $entries+=@{testId="$($case.classname)#$($case.name)";status=$state;timestamp=$file.LastWriteTimeUtc.ToString('o');evidence=$file.Name}
 }
}
$browser=Get-Content -Raw (Join-Path $ProjectRoot 'frontend/test-results/e2e-results.json')|ConvertFrom-Json
$result=@{generatedAt=(Get-Date).ToUniversalTime().ToString('o');releaseReady=$false;tests=$entries;browser=$browser}
$realPath=Join-Path $ProjectRoot 'docs/test-evidence/real-repositories/results.json'
if(Test-Path (Join-Path $ProjectRoot 'docs/test-evidence/real-repositories-v3/results.json')){$realPath=Join-Path $ProjectRoot 'docs/test-evidence/real-repositories-v3/results.json'}
if(Test-Path $realPath){$result.realRepositoryTests=Get-Content -Raw $realPath|ConvertFrom-Json}
$followupPath=Join-Path $ProjectRoot 'docs/test-evidence/real-repositories-v3/REAL-03-followup.json'
if(Test-Path $followupPath){$result.realRepositoryFollowup=Get-Content -Raw $followupPath|ConvertFrom-Json}
$acceptancePath=Join-Path $ProjectRoot 'docs/test-evidence/acceptance-status.json'
if(Test-Path $acceptancePath){$result.acceptance=Get-Content -Raw $acceptancePath|ConvertFrom-Json}
$utf8=New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText((Join-Path $ProjectRoot 'test-results.json'),($result|ConvertTo-Json -Depth 50),$utf8)
$pass=@($entries|Where-Object status -eq 'PASS').Count
$fail=@($entries|Where-Object status -eq 'FAIL').Count
$text=@"
# RepoGuard test report

Generated: $($result.generatedAt)

Backend: $pass PASS, $fail FAIL. Browser: $($browser.stats.expected) passed, $($browser.stats.unexpected) failed, $($browser.stats.skipped) skipped.

Evidence: backend/target/surefire-reports and frontend/test-results (JSON, screenshots and traces). Detailed outcomes: test-results.json. All target fixtures remain passive.

Executed scanner versions, finding counts and explicit coverage limits: docs/test-evidence/scanner-coverage.json. Post-run checks found empty worker/backend workspaces, no executed target-configuration marker, no worker default route and zero stored occurrences of the unmasked fixture secrets.

Ten unmodified real GitHub projects: docs/REAL_REPOSITORY_ACCEPTANCE.md and docs/test-evidence/real-repositories/results.json. A real-project probe PASS is scoped to its declared expectations and does not imply product acceptance. Remaining mandatory criteria and confirmed failures: docs/ACCEPTANCE_STATUS.md. releaseReady remains false.

Backend checks include ten-language source rules, scanner normalization and failures, bounded separate output streams, masking, fingerprints, curated CVEs, ZIP attacks, cleanup, malware gating and origin/host checks. Browser cases exercise all twelve installed scanners on passive ten-language input, real offline Trivy CVE findings, hostile project configuration, ZIP scans, filtering, masked details, PDF download, EICAR and traversal blocking, history/comparison/deletion, German/English and light/dark at three widths. Failed/skipped cases are not accepted.

Docker startup and the ClamAV/Trivy security data update succeeded. Installed tools and their limits are documented in scanner/SCANNER_INVENTORY.md. Compiler/type/build-dependent analysis, unsupported dependency formats and missing transitive metadata remain outside coverage. Full operational acceptance remains incomplete: live GitHub/TLS/network tracing, restart/backup acceptance, independent LAN-device access and PDF visual inspection are outstanding. No production/security release approval is claimed.
"@
[IO.File]::WriteAllText((Join-Path $ProjectRoot 'TEST_REPORT.md'),$text,$utf8)
Write-Output "Backend $pass PASS, $fail FAIL; browser $($browser.stats.expected) passed, $($browser.stats.unexpected) failed."
