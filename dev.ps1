<#
.SYNOPSIS
  Edit → run loop for Piyo from the command line: incremental build, install on the phone or emulator,
  launch, and stream the app's logcat. For true hot reload (no reinstall) use Android Studio's Live Edit
  (docs/implementation/SETUP_AND_RUN.md §2c); this script is the fallback and what Claude uses.

.EXAMPLE
  .\dev.ps1                 # build + install + launch + logcat (first device found)
  .\dev.ps1 -Serial <serial>
  .\dev.ps1 -NoLog          # build + install + launch, return immediately
  .\dev.ps1 -Test           # run the JVM unit tests first
  .\dev.ps1 -Clean          # cold rebuild (after changing gradle files)
  .\dev.ps1 -Release        # R8-optimised build signed with the debug key: use this to judge performance
#>
param(
    [string]$Serial = "",
    [switch]$NoLog,
    [switch]$Test,
    [switch]$Clean,
    [switch]$Release
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if ($Serial -eq "") {
    $devices = (& adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" } | ForEach-Object { ($_ -split "\t")[0] }
    if ($devices.Count -eq 0) { Write-Host "No device. Plug the phone (USB debugging) or start an emulator: emulator -avd Pixel_9" -ForegroundColor Red; exit 1 }
    $Serial = $devices[0]
    if ($devices.Count -gt 1) { Write-Host "Several devices, using $Serial (pass -Serial to pick another)" -ForegroundColor Yellow }
}

$sw = [Diagnostics.Stopwatch]::StartNew()
$tasks = @()
if ($Clean) { $tasks += "clean" }
if ($Test) { $tasks += ":app:testDebugUnitTest" }
$tasks += $(if ($Release) { ":app:installRelease" } else { ":app:installDebug" })
$env:ANDROID_SERIAL = $Serial
& .\gradlew.bat -q @tasks
if ($LASTEXITCODE -ne 0) { Write-Host "Build failed" -ForegroundColor Red; exit $LASTEXITCODE }
Write-Host ("Built + installed in {0:n0} s on {1}" -f $sw.Elapsed.TotalSeconds, $Serial) -ForegroundColor Green

& adb -s $Serial shell am force-stop bo.piyo.app
& adb -s $Serial shell am start -W -n bo.piyo.app/.MainActivity | Out-Null
if ($NoLog) { exit 0 }

Write-Host "logcat (Ctrl+C to stop):" -ForegroundColor Cyan
& adb -s $Serial logcat -c
$pid = ""
for ($i = 0; $i -lt 20 -and $pid -eq ""; $i++) { Start-Sleep -Milliseconds 250; $pid = (& adb -s $Serial shell pidof bo.piyo.app).Trim() }
if ($pid -ne "") { & adb -s $Serial logcat --pid=$pid -v color } else { & adb -s $Serial logcat -v color *:E }
