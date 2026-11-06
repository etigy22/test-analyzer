param([string]$BaseUrl='http://localhost:8088')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$directory=Join-Path $root 'docs/test-evidence/completed-static-scans'
[void][IO.Directory]::CreateDirectory($directory)
$utf8=New-Object Text.UTF8Encoding($false)
$results=@()
foreach($repo in @('spring-projects/spring-petclinic','pallets/flask','DapperLib/Dapper','go-chi/chi','slimphp/Slim','BurntSushi/ripgrep','Alamofire/Alamofire')) {
 Write-Output "START $repo"
 $job=Invoke-RestMethod "$BaseUrl/api/scans/github" -Method Post -ContentType application/json -Headers @{'X-RepoGuard-Request'='true'} -Body (@{url="https://github.com/$repo"}|ConvertTo-Json -Compress)
 $deadline=(Get-Date).AddMinutes(12)
 do{$job=Invoke-RestMethod "$BaseUrl/api/scans/$($job.id)";if($job.completedAt){break};Start-Sleep 3}while((Get-Date)-lt $deadline)
 $gaps=@($job.result.coverage|Where-Object {$_.status -ne 'COMPLETED' -and -not ($_.applicable -eq $false -and $_.status -eq 'UNSUPPORTED')})
 $pass=$job.status -eq 'COMPLETED' -and $null -ne $job.result -and -not $job.result.truncated -and $gaps.Count -eq 0
 $entry=[ordered]@{repository="https://github.com/$repo";scanId=$job.id;startedAt=$job.createdAt;completedAt=$job.completedAt;scanStatus=$job.status;status=if($pass){'PASS'}else{'FAIL'};version=$job.result.rulesVersion;archiveSha256=$job.result.archiveSha256;archiveBytes=$job.result.archiveBytes;truncated=$job.result.truncated;findings=$job.result.findings.Count;languages=$job.result.languages;coverage=$job.result.coverage;applicableGaps=$gaps;failureCode=$job.failureCode;failureMessage=$job.failureMessage;expected='Unmodified original archive; mandatory malware pre-scan; COMPLETED; no truncated results; every applicable check COMPLETED. Explicitly non-applicable analyses remain visible and are not claimed executed.'}
 $results+=@($entry)
 [IO.File]::WriteAllText((Join-Path $directory 'results.json'),(@{verifiedAt=(Get-Date).ToUniversalTime().ToString('o');results=$results;passed=@($results|Where-Object status -eq 'PASS').Count;releaseReady=$false}|ConvertTo-Json -Depth 25),$utf8)
 Write-Output "$repo $($entry.status) app=$($job.status) findings=$($entry.findings) gaps=$($gaps.Count)"
 if(-not $job.completedAt){throw 'Job did not terminate; do not submit more jobs into an occupied worker.'}
 if(@($results|Where-Object status -eq 'PASS').Count -ge 5){break}
}
if(@($results|Where-Object status -eq 'PASS').Count -lt 5){exit 1}
