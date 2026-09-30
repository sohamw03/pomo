param([int]$Tries = 40, [int]$WaitSeconds = 30)
for ($i = 1; $i -le $Tries; $i++) {
  $s = gh codespace list --json state --jq '.[0].state' 2>$null
  Write-Output "try ${i}: $s"
  if ($s -eq "Available") { exit 0 }
  Start-Sleep -Seconds $WaitSeconds
}
exit 1
