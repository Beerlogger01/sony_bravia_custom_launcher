param([string]$ApkDir = (Join-Path $PSScriptRoot '..\apk'), [string]$Apksigner)
. (Join-Path $PSScriptRoot 'common.ps1')
. (Join-Path $PSScriptRoot 'hdmi.ps1')
Open-Tv
$original = Get-Home
if ($original -ne 'com.google.android.leanbacklauncher/.MainActivity') { throw "Unexpected current HOME: $original" }
$dir = Save-State
$previous = @{home='41079d682bd58ec973b5bd46021a1743119ad4fa02e8b7f2b8932651a863559e';panel='8e06170620521d08c8eada130817168fae067c91adc5b180d4444e41ba8757d6'}
$cert = '8c564f58900eb43a9e9a15b456381916a0e164226821c499b7f4b8944f25c82c'
$map = Read-PortMap (Get-Content (Join-Path $dir 'inputs.txt'))
$rc1 = @{home='2f7bae5681d82d8430c2e6504ef48243383e6718a4384bdeff69977417030a0a';panel='dc6fc7c396d99f5940cc0f839f5424d61539621d1f2b25885ec986e8e7427502'}
# Approved byte-for-byte release pair. Its manifests and signer are checked at packaging time.
$approved = @{home='71901882789e5cd5a870432a12f2b079384d5d94ffa9e927c17f301a7952ec42';panel='ddfe1893a3c102b8f38bc15c6ee18d85c7294e3d6ec6bec1b8e119f2341ee665'}
$pending = @()
foreach ($name in 'panel','home') {
    $pkg = "house.bravia.$name"
    $limit = if ($name -eq 'home') { 15 } else { 3 }
    $versions = @((Invoke-Tv shell dumpsys package $pkg) | Select-String 'versionCode=(\d+)')
    if ($versions.Count -ne 1 -or [long]$versions[0].Matches[0].Groups[1].Value -gt $limit) { throw "Unknown or newer installed version: $pkg" }
    $remote = ((Invoke-Tv shell pm path $pkg) -join '').Trim()
    if ($remote -notmatch '^package:(/data/app/[A-Za-z0-9_./=-]+/base\.apk)$') { throw "Expected installed single APK: $pkg" }
    $old = Join-Path $dir "$name-before.apk"
    Copy-InstalledApk $Matches[1] $old
    $apk = Join-Path $ApkDir "$name.apk"
    $digest = (Get-FileHash $apk -Algorithm SHA256).Hash
    $sum = @(Get-Content (Join-Path $ApkDir 'SHA256SUMS') | Where-Object { $_ -match "^[a-fA-F0-9]{64}  $name\.apk$" })
    if ($digest -ne $approved[$name]) { throw "APK differs from the verified release: $name" }
    if ($sum.Count -ne 1 -or $sum[0].Substring(0,64) -ne $digest) {throw "Unexpected new APK: $name"}
    $installed = (Get-FileHash $old -Algorithm SHA256).Hash
    if ($installed -eq $digest) { continue }
    if ($installed -ne $previous[$name] -and $installed -ne $rc1[$name]) {
        if (!$Apksigner) {throw "Installed $pkg differs from the first distribution. Pass -Apksigner with the Android SDK apksigner.bat path to verify the installed signature."}
        $result = (& $Apksigner verify --min-sdk-version 24 --print-certs $old) -join "`n"
        if ($LASTEXITCODE -ne 0 -or $result -notmatch "certificate SHA-256 digest: $cert") {throw "Different signer: $pkg"}
    }
    $pending += $apk
}
# Android 7 installs packages one at a time. A retry checks both hashes again.
$done = @()
$attempt = 'mapping'
try {
foreach ($apk in $pending) {
    $attempt = [IO.Path]::GetFileName($apk)
    Invoke-Tv install -r $apk
    $done += $attempt
}
$attempt = 'HDMI and HOME checks'
Send-PortMap $map
if ((Get-Home) -ne $original) { throw "HOME differs from the baseline. Inspect $dir before continuing." }
Show-Component $Preview
Write-Host "Preview updated. HOME: $original. Backup: $dir"
} catch {
    throw "Confirmed installations: [$($done -join ', ')]. Failed or unconfirmed: $attempt. Backup: $dir. Reconnect and rerun update.ps1. $($_.Exception.Message)"
}
