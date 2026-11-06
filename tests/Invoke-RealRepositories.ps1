param([string]$BaseUrl='http://localhost:8088',[string]$OutputDirectory=(Join-Path (Split-Path -Parent $PSScriptRoot) 'docs/test-evidence/real-repositories'),[string]$RepositoryFilter='')
$ErrorActionPreference='Stop'
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$utf8=New-Object Text.UTF8Encoding($false)
$projects=@(
 @{repo='spring-projects/spring-petclinic';language='Java';tools=@('Checkstyle','Semgrep','Trivy')},
 @{repo='pallets/flask';language='Python';tools=@('Bandit','Ruff','Semgrep','Trivy')},
 @{repo='BurntSushi/ripgrep';language='Rust';tools=@('rustfmt','Semgrep','Trivy')},
 @{repo='Alamofire/Alamofire';language='Swift';tools=@('SwiftLint','Trivy')},
 @{repo='thedotmack/claude-mem';language='JavaScript/TypeScript';tools=@('ESLint','Semgrep','Trivy')},
 @{repo='fmtlib/fmt';language='C/C++';tools=@('Cppcheck','Semgrep','Trivy')},
 @{repo='DapperLib/Dapper';language='C#';tools=@('Semgrep','Trivy')},
 @{repo='go-chi/chi';language='Go';tools=@('gofmt','Semgrep','Trivy')},
 @{repo='square/okio';language='Kotlin';tools=@('detekt','Semgrep','Trivy')},
 @{repo='slimphp/Slim';language='PHP';tools=@('PHP_CodeSniffer','Semgrep','Trivy')}
)
$settings=Invoke-RestMethod "$BaseUrl/api/settings"
$run=[ordered]@{startedAt=(Get-Date).ToUniversalTime().ToString('o');completedAt=$null;version=$settings.rulesVersion;environment='Windows Docker Desktop; local Compose stack, private offline worker, PostgreSQL';selection='Ten known real projects, one per required language group; no popularity ranking is claimed.';inputPolicy='Unmodified official GitHub archives through the normal application API. No target builds, tests, installers or executable configuration. No synthetic findings added.';settings=$settings;expected='Original import reaches static analysis; expected language detected; applicable managed scanners complete. Failures, skipped content and finding limits remain recorded. Real project probes do not replace seeded per-language acceptance fixtures.';results=@()}
function Save-Run { [IO.File]::WriteAllText((Join-Path $OutputDirectory 'results.json'),($run|ConvertTo-Json -Depth 30),$utf8) }
Save-Run
$number=0
foreach($project in $projects) {
 $number++
 if($RepositoryFilter -and $project.repo -notin $RepositoryFilter.Split(',')){continue}
 $entry=[ordered]@{testId=('REAL-{0:D2}' -f $number);requirementRefs=@('IMP-01','IMP-07','IMP-11','IMP-14','LANG-13','LANG-14','LANG-15','ANA-06','ANA-09','DATA-01','E2E-02');repository="https://github.com/$($project.repo)";expectedLanguage=$project.language;expectedTools=$project.tools;startedAt=(Get-Date).ToUniversalTime().ToString('o');completedAt=$null;status='NOT TESTED';scanId=$null;scanStatus=$null;failureCode=$null;failureMessage=$null}
 $entry.requirementRefs+=('LANG-{0:D2}' -f $number)
 $entry.expected=$run.expected;$entry.testedVersion=$run.version;$entry.environment=$run.environment
 $entry.evidence=($entry.testId+'.json')
 Write-Output "$($entry.testId) START $($project.repo)"
 try {
  $job=Invoke-RestMethod "$BaseUrl/api/scans/github" -Method Post -ContentType application/json -Headers @{'X-RepoGuard-Request'='true'} -Body (@{url=$entry.repository}|ConvertTo-Json -Compress)
  $entry.scanId=$job.id;$lastPhase='';$deadline=(Get-Date).AddMinutes(12)
  do {
   $job=Invoke-RestMethod "$BaseUrl/api/scans/$($entry.scanId)"
   if($job.status -ne $lastPhase){Write-Output "$($entry.testId) $($job.status)";$lastPhase=$job.status}
   if($job.completedAt){break}
   Start-Sleep -Seconds 3
  }while((Get-Date) -lt $deadline)
  $entry.scanStatus=$job.status;$entry.failureCode=$job.failureCode;$entry.failureMessage=$job.failureMessage
  $entry.completedAt=(Get-Date).ToUniversalTime().ToString('o')
  if($job.result) {
   $result=$job.result
   $entry.archiveSha256=$result.archiveSha256;$entry.archiveBytes=$result.archiveBytes;$entry.rulesVersion=$result.rulesVersion;$entry.malwareDataVersion=$result.malwareVersion
   $entry.truncated=$result.truncated;$entry.durationMillis=$result.durationMillis;$entry.findingCount=$result.findings.Count;$entry.languages=$result.languages;$entry.coverage=$result.coverage
   $entry.findingsByTool=@($result.findings|Group-Object tool|ForEach-Object{@{tool=$_.Name;count=$_.Count}})
   $entry.findingsByCategorySeverity=@($result.findings|Group-Object category,severity|ForEach-Object{@{categoryAndSeverity=$_.Name;count=$_.Count}})
   $entry.expectedLanguageDetected=@($result.languages|Where-Object name -eq $project.language).Count -gt 0
   $missing=@();foreach($tool in $project.tools){if(-not @($result.coverage|Where-Object {$_.tool -eq $tool -and $_.status -eq 'COMPLETED'}).Count){$missing+=$tool}}
   $entry.missingCompletedTools=$missing
   $failures=@($result.coverage|Where-Object status -eq 'FAILED')
   $entry.status=if($entry.expectedLanguageDetected -and $missing.Count -eq 0 -and $failures.Count -eq 0 -and -not $result.truncated){'PASS'}else{'FAIL'}
   $entry.interpretation='PASS applies only to the stated real-project probe, not full language/security/product acceptance. Configured unsupported checks remain outside coverage.'
  }else{$entry.status='FAIL'}
  if(-not $job.completedAt){$entry.status='FAIL';$entry.failureCode='HARNESS_WAIT_LIMIT';$entry.failureMessage='Job did not terminate within twelve minutes. Later probes may be blocked by the active job.'}
 }catch{$entry.status='FAIL';$entry.completedAt=(Get-Date).ToUniversalTime().ToString('o');$entry.failureMessage='Local API request failed; inspect application health and per-job status.'}
 $run.results+=@($entry);Save-Run
 [IO.File]::WriteAllText((Join-Path $OutputDirectory ($entry.testId+'.json')),($entry|ConvertTo-Json -Depth 25),$utf8)
 Write-Output "$($entry.testId) $($entry.status) scan=$($entry.scanId) result=$($entry.scanStatus) findings=$($entry.findingCount)"
}
$run.completedAt=(Get-Date).ToUniversalTime().ToString('o');Save-Run
Write-Output "Finished $($run.results.Count) actual GitHub project probes."
