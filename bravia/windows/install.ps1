param([string]$ApkDir = (Join-Path $PSScriptRoot '..\apk'), [switch]$SetHome, [string]$Backup)
. (Join-Path $PSScriptRoot 'common.ps1')
Open-Tv
if (!$SetHome) {
    if ($Backup) {
        $baseline = Read-State $Backup
        if ((Get-Home) -ne $baseline.stock) { throw 'Original HOME has changed.' }
        $dir = $Backup
    } else { $dir = Save-State }
    foreach ($pkg in 'house.bravia.panel','house.bravia.home') {
        if (!$Backup -and ((Invoke-Tv shell pm list packages $pkg) -contains "package:$pkg")) { throw 'Preview requires a fresh installation. Existing data preserved.' }
    }
    foreach ($name in 'panel','home') {
        $apk = Join-Path $ApkDir "$name.apk"
        $expected = (Get-Content (Join-Path $ApkDir 'SHA256SUMS') | Where-Object { $_ -match "  $name\.apk$" }) -split ' '
        if ((Get-FileHash $apk -Algorithm SHA256).Hash -ne $expected[0]) {throw "Hash mismatch: $name"}
    }
    foreach ($name in 'panel','home') {
        $apk = Join-Path $ApkDir "$name.apk"
        $pkg = "house.bravia.$name"
        if ((Invoke-Tv shell pm list packages $pkg) -contains "package:$pkg") {
            if ((Get-Content -LiteralPath "$dir\packages.txt") -match "=$([regex]::Escape($pkg))$") {throw "Package predates preview: $pkg"}
            $remote = ((Invoke-Tv shell pm path $pkg) -join '').Trim()
            if ($remote -notmatch '^package:(/data/app/[A-Za-z0-9_./=-]+/base\.apk)$') { throw 'Cannot verify existing preview package.' }
            $tmp = [IO.Path]::GetTempFileName()
            try {
                Copy-InstalledApk $Matches[1] $tmp
                if ((Get-FileHash $apk).Hash -ne (Get-FileHash $tmp).Hash) { throw "Existing APK differs: $pkg" }
            } finally { Remove-Item -LiteralPath $tmp }
        } else { Invoke-Tv install $apk }
    }
    Show-Component $Preview
    if ((Get-Home) -ne (Read-State $dir).stock) { throw "Unexpected HOME. Use restore.ps1 -Backup '$dir'." }
    Write-Host "Preview only. Test D-pad, HDMI 1-4, HA live and offline, Sony settings, Back and physical Home."
    Write-Host "After testing and separate approval: .\install.ps1 -SetHome -Backup '$dir'"
    return
}
if (!$Backup) {throw 'Pass the ORIGINAL preview backup path.'}
$s = Read-State $Backup
if ((Get-Home) -ne $s.stock) {throw 'Current HOME differs from the preview baseline.'}
$helpText = (Invoke-Tv shell cmd package help) -join ' '
if ($helpText -notmatch 'set-home-activity') {throw 'This firmware lacks the required restore command.'}
if (!(Test-Path (Join-Path $PSScriptRoot 'restore.ps1'))) {throw 'Restore script missing.'}
Show-Component $s.stock
Show-Component $Preview
Write-Host "Restore command: & '$Adb' -s '$Tv' shell cmd package set-home-activity --user 0 '$($s.stock)'"
if ((Read-Host 'Only after successful HDMI, remote, HA and stock launch tests type SET HOME') -cne 'SET HOME') {return}
try {
    Invoke-Tv shell am broadcast --include-stopped-packages --user 0 -n house.bravia.home/com.gathin.tvhome.HomeEnrollment -a house.bravia.ENROLL
    Invoke-Tv shell cmd package set-home-activity --user 0 $AliasHome
    if ((Get-Home) -ne $AliasHome) {throw 'New HOME did not resolve.'}
    Invoke-Tv shell input keyevent 3
    Start-Sleep -Seconds 2
    if (!((Invoke-Tv shell dumpsys activity activities) -match 'mFocusedActivity.*house\.bravia\.home/')) {
        throw 'Physical HOME activity could not be verified.'
    }
} catch {
    & (Join-Path $PSScriptRoot 'restore.ps1') -Backup $Backup
    throw
}
