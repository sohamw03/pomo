<#
.SYNOPSIS
  Builds the Pomo Android APK on the pomo-android Codespace and downloads it.

.DESCRIPTION
  The Codespace is a build server only: JDK 17 + Android SDK, no emulator,
  no usable git credentials. So this script never uses git remotely. Local
  working-tree changes are shipped up as base64 over `gh codespace ssh`,
  Gradle runs there, and the APK comes back the same way.

.EXAMPLE
  .\scripts\Build-Apk.ps1
  .\scripts\Build-Apk.ps1 -Task ":app:assembleDebug" -Label "pomo-debug"
#>
[CmdletBinding()]
param(
  [string]$Task = ":app:assembleDebug",
  [string]$Label = "pomo-debug",
  [string]$CodespaceName = "pomo-android"
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$RemoteRepo = "/workspaces/pomo"
$ApkFlavor = if ($Task -match "[Rr]elease") { "release" } else { "debug" }
$RemoteApkDir = "$RemoteRepo/app/build/outputs/apk/$ApkFlavor"
$DistDir = Join-Path $RepoRoot "dist"

function Shq([string]$s) { "'" + ($s -replace "'", "'\''") + "'" }

function Invoke-Cs([string]$cs, [string]$cmd) {
  $out = & gh codespace ssh -c $cs -- $cmd 2>&1
  return @{ Code = $LASTEXITCODE; Text = (($out | ForEach-Object { "$_" }) -join "`n") }
}

function Send-CsFile([string]$cs, [string]$remote, [string]$local) {
  $b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($local))
  $tmp = "$remote.b64tmp"
  $dir = $remote.Substring(0, $remote.LastIndexOf("/"))
  [void](Invoke-Cs $cs ("mkdir -p " + (Shq $dir) + " && : > " + (Shq $tmp)))
  $CHUNK = 48 * 1024
  for ($i = 0; $i -lt $b64.Length; $i += $CHUNK) {
    $part = $b64.Substring($i, [Math]::Min($CHUNK, $b64.Length - $i))
    [void](Invoke-Cs $cs ("printf '%s' '" + $part + "' >> " + (Shq $tmp)))
  }
  $r = Invoke-Cs $cs ("base64 -d " + (Shq $tmp) + " > " + (Shq $remote) + " && rm -f " + (Shq $tmp))
  if ($r.Code -ne 0) { throw "upload failed for $remote`n$($r.Text)" }
}

function Receive-CsFile([string]$cs, [string]$remote) {
  $r = Invoke-Cs $cs ("base64 -w0 " + (Shq $remote))
  if ($r.Code -ne 0) { throw "download failed for $remote`n$($r.Text)" }
  $payload = @($r.Text -split "`n" | ForEach-Object { $_.Trim() } |
    Where-Object { $_.Length -gt 64 -and $_ -match "^[A-Za-z0-9+/=]+$" } |
    Sort-Object Length -Descending | Select-Object -First 1)
  if (-not $payload) { throw "no payload returned for $remote" }
  return [Convert]::FromBase64String($payload[0])
}

# 0. find the codespace
$list = & gh codespace list --json name,state,displayName 2>&1 | Out-String | ConvertFrom-Json
$cs = @($list | Where-Object { $_.displayName -eq $CodespaceName } | Select-Object -First 1)
if (-not $cs) { $cs = @($list | Select-Object -First 1) }
if (-not $cs) { throw "No codespace found. Create one named `"$CodespaceName`" first." }
$csName = $cs[0].name
Write-Output "codespace: $csName"
Write-Output "task: $Task"

# 1. ship local changes (new dirs expanded via --untracked-files=all)
$raw = & git -C $RepoRoot status --porcelain -z --untracked-files=all
$fields = @("$raw" -split "`0" | Where-Object { $_ -ne "" })
$shipped = @()
$removed = @()
for ($i = 0; $i -lt $fields.Count; $i++) {
  $entry = $fields[$i]
  $code = $entry.Substring(0, 2)
  $rel = $entry.Substring(3)
  if ($code[0] -eq "R" -or $code[0] -eq "C") {
    $i++  # rename/copy stores the original path in the next field
    if ((Invoke-Cs $csName ("rm -f " + (Shq "$RemoteRepo/$rel"))).Code -eq 0) { $removed += $rel }
    continue
  }
  if ($code.Trim() -eq "D") {
    if ((Invoke-Cs $csName ("rm -f " + (Shq "$RemoteRepo/$rel"))).Code -eq 0) { $removed += $rel }
    continue
  }
  $abs = Join-Path $RepoRoot $rel
  if (-not (Test-Path $abs -PathType Leaf)) { continue }
  Send-CsFile $csName "$RemoteRepo/$rel" $abs
  $shipped += $rel
}
Write-Output ("shipped {0} file(s){1}" -f $shipped.Count, $(if ($shipped.Count) { ": " + ($shipped -join ", ") } else { "" }))
if ($removed.Count) { Write-Output ("removed: " + ($removed -join ", ")) }

# 2. build
$buildCmd = "cd " + (Shq $RemoteRepo) + " && ./gradlew $Task --console=plain 2>&1; echo `"__EXIT__`$?`""
$started = Get-Date
$b = Invoke-Cs $csName $buildCmd
$secs = ((Get-Date) - $started).TotalSeconds.ToString("0.0")
$lines = @($b.Text -split "`n")
$exit = $b.Code
$cut = $lines.Count
for ($i = $lines.Count - 1; $i -ge 0; $i--) {
  if ($lines[$i].Trim() -match "^__EXIT__(\d+)$") { $exit = [int]$Matches[1]; $cut = $i; break }
}
$text = @($lines[0..([Math]::Max(0, $cut - 1))] |
  Where-Object { $_ -notmatch "^\s*:\w+:\w+.* -> " } | ForEach-Object { "$_".TrimEnd() }) -join "`n"
$text = $text.Trim()
Write-Output "build exit=$exit in ${secs}s"
$errs = @($text -split "`n" | ForEach-Object { $_.Trim() } |
  Where-Object { $_ -match "^(e|w): " -or $_ -match "^\* What went wrong:|^> (?!Task )[A-Z]|error:|Exception|Caused by:" })
if ($errs.Count) { Write-Output ("--- key errors ---`n" + ($errs[0..([Math]::Min(24, $errs.Count - 1))] -join "`n") + "`n`n--- full tail ---") }
$tail = @($text -split "`n" | Select-Object -Last 50) -join "`n"
if ($tail) { Write-Output "--- output (tail) ---`n$tail" } else { Write-Output "(no output)" }
if ($exit -ne 0) { throw "BUILD FAILED - no APK downloaded." }

# 3. sign release builds (Gradle leaves them unsigned) and pull the APK back
# NOTE: globs must stay OUTSIDE the single quotes, or the remote
# shell passes them literally to ls and matches nothing.
if ($ApkFlavor -eq "release") {
  $signCmd = 'cd ' + (Shq $RemoteRepo) +
    ' && BT=' + (Shq '/home/vscode/android-sdk/build-tools/35.0.0') +
    ' && UNSIGNED=$(ls -1 app/build/outputs/apk/release/*-unsigned.apk 2>/dev/null | head -1)' +
    ' && [ -n "$UNSIGNED" ]' +
    ' && "$BT/zipalign" -p -f 4 "$UNSIGNED" app/build/outputs/apk/release/app-aligned.apk' +
    ' && "$BT/apksigner" sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android --out app/build/outputs/apk/release/app-release.apk app/build/outputs/apk/release/app-aligned.apk' +
    ' && rm -f app/build/outputs/apk/release/app-aligned.apk && echo SIGNED'
  $sign = Invoke-Cs $csName $signCmd
  if ($sign.Text -notmatch "SIGNED") { throw "Release signing failed.`n$($sign.Text)" }
  $lsPattern = Shq "$RemoteApkDir/app-release.apk"
} else {
  $lsPattern = (Shq $RemoteApkDir) + "/*.apk"
}
$ls = Invoke-Cs $csName ("ls -1 " + $lsPattern + " 2>/dev/null | head -1")
$remoteApk = @($ls.Text -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" } | Select-Object -Last 1)
if (-not $remoteApk) { throw "BUILD OK but no APK found in $RemoteApkDir" }
$bytes = Receive-CsFile $csName $remoteApk[0]
New-Item -ItemType Directory -Force -Path $DistDir | Out-Null
$localApk = Join-Path $DistDir "$Label.apk"
[IO.File]::WriteAllBytes($localApk, $bytes)
Write-Output "--- result ---"
Write-Output "APK: $localApk"
Write-Output ("size: {0:N2} MB" -f ($bytes.Length / 1MB))
