param([string]$ProjectRoot=(Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference='Stop'
$utf8=New-Object Text.UTF8Encoding($false)
$document=[IO.File]::ReadAllLines((Join-Path $ProjectRoot 'ACCEPTANCE_CRITERIA.md'))
$criteria=[ordered]@{}
foreach($line in $document){
 if($line -match '^\|\s*([A-Z]+-\d\d)\s*\|\s*([^|]+)'){$criteria[$matches[1]]=$matches[2].Trim()}
 elseif($line -match '^### (E2E-\d\d) [–-] (.+)$'){$criteria[$matches[1]]=$matches[2].Trim()}
}
$passed=@{
 'IMP-11'='CoreAnalysisTest and RealRepositoryRegressionTest: dangerous/special/colliding entries rejected; safe internal file links remain passive; copied bytes bounded. Directory links remain unsupported, see REAL-03.'
 'LANG-13'='Per-tool statuses, versions and reasons retained in all real-project results and browser coverage view. This does not accept each individual language.'
 'LANG-14'='ExternalScannersTest plus actual REAL-06/REAL-09: unavailable/failed tools explicitly reported; never inferred successful from zero findings.'
 'ANA-07'='BoundedFindings deduplicates retained fingerprints; regression verifies duplicate input does not inflate retained findings.'
 'ANA-09'='Actual real-project states PARTIAL, FAILED and BLOCKED are distinct and retained in results.json.'
 'SEC-03'='Browser SEC-03 and BrowserRequestFilter tests: hostile origins, missing mutation header and hostile Host rejected.'
 'DOC-07'='Actual backend/browser tests, ten unchanged GitHub inputs and their failures are documented with evidence. No product release claimed.'
 'AGENT-06'='All four binding documents reread; this IDE-agent task maps changes to IMP-11, ANA-06 and ANA-09 with regression tests and real inputs.'
}
$failed=@{
 'SYS-06'='Parallelism remains hard-coded to one; not every required runtime limit is configurable and fully represented in UI. New malware/result limits do not complete this criterion.'
 'LANG-06'='Cppcheck is installed, but the specified C/C++ format-check adapter is missing. REAL-06 also has Semgrep partial failure; positive separate C and C++ acceptance remains incomplete.'
 'ANA-06'='Tool versions and paths are now normalized correctly. Several explanations/remediations remain generic; dependency package/version context is still insufficient.'
 'UI-04'='Several runtime errors, coverage reasons and report labels remain English in the German workflow. Theme/locale switching tests do not prove full translation.'

}
$entries=@()
foreach($id in $criteria.Keys){
 $state='NOT TESTED';$reason='No complete individual acceptance proof in this scoped test series. Existing partial evidence does not constitute PASS.'
 if($passed.ContainsKey($id)){$state='PASS';$reason=$passed[$id]}
 if($failed.ContainsKey($id)){$state='FAIL';$reason=$failed[$id]}
 $entries+=@{testId=$id;description=$criteria[$id];status=$state;requirementRef='ACCEPTANCE_CRITERIA.md';expected='All conditions of this criterion as written in the binding acceptance document.';actual=$reason;evidence=@('docs/REAL_REPOSITORY_ACCEPTANCE.md','docs/test-evidence/real-repositories/results.json','backend/target/surefire-reports','frontend/test-results/e2e-results.json');timestamp=(Get-Date).ToUniversalTime().ToString('o');testedVersion='repoguard-source-2026.11.03.1';environment='Local Windows/Docker Compose; IDE checkout';fixStatus=if($state -eq 'PASS'){'Verified for stated criterion'}else{'Open'}}
}
$result=@{generatedAt=(Get-Date).ToUniversalTime().ToString('o');releaseReady=$false;policy='Only complete evidence earns PASS. NOT TESTED includes criteria with partial evidence but no full acceptance. Real-project probe PASS is distinct from acceptance-criterion PASS.';criteria=$entries}
[IO.File]::WriteAllText((Join-Path $ProjectRoot 'docs/test-evidence/acceptance-status.json'),($result|ConvertTo-Json -Depth 12),$utf8)
$lines=@('# Verbindlicher Abnahmestatus','',"Stand: $($result.generatedAt)",'','**Keine produktive Freigabe.** Die vier Projektdokumente gelten unverändert. Nicht vollständig belegte Kriterien bleiben NOT TESTED; vorhandene Teiltests werden nicht als vollständige Abnahme umgedeutet. FAIL bedeutet eine konkret festgestellte Abweichung. Die zehn Realprojekt-Proben sind getrennt von diesen MUSS-Kriterien zu bewerten.','', 'Die gezielt nachgewiesenen Kriterien stehen unten. Insbesondere LAN-/Offline-Netzwerkmitschnitt, Prozess-/Sandbox-Audit, vollständige Sprach-Einzelabnahme, Restart/Backup, PDF-Sichtprüfung, Lizenzprüfung und System-Schwachstellenbewertung sind dadurch nicht freigegeben.','', '[Praxistest mit zehn Originalarchiven](REAL_REPOSITORY_ACCEPTANCE.md) · [Maschinenlesbare Einzelkriterien](test-evidence/acceptance-status.json)','', '| Kriterium | Vorgabe | Status | Nachweis / offene Abweichung |','|---|---|---|---|')
foreach($entry in $entries){$lines+="| $($entry.testId) | $($entry.description) | $($entry.status) | $($entry.actual) |"}
[IO.File]::WriteAllLines((Join-Path $ProjectRoot 'docs/ACCEPTANCE_STATUS.md'),$lines,$utf8)
Write-Output "Recorded $($entries.Count) mandatory criteria; releaseReady=false."
