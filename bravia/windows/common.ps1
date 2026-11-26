Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$Tv = $env:BRAVIA_TV
if (!$Tv) { $Tv = Read-Host 'Sony ADB address (IP:5555)' }
if ($Tv -notmatch '^[A-Za-z0-9.-]+:[0-9]{1,5}$') { throw 'Invalid TV address' }
$Adb = $env:BRAVIA_ADB
if (!$Adb) { $Adb = Join-Path ([Environment]::GetFolderPath('Desktop')) 'platform-tools\adb.exe' }
$AliasHome = 'house.bravia.home/com.gathin.tvhome.HomeChoice'
$Preview = 'house.bravia.home/com.gathin.tvhome.BraviaHome'
function Invoke-Tv {
    $lines = & $Adb -s $Tv @args
    if ($LASTEXITCODE -ne 0 -or (($lines -join "`n") -match '(?im)^(Error|Failure|Exception|Unknown command|Security exception)')) {
        throw "ADB operation failed: $($args[0..1] -join ' ')"
    }
    $lines
}
function Open-Tv {
    if (!(Test-Path $Adb)) { throw "ADB missing: $Adb" }
    $connected = $false
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        $null = & $Adb connect $Tv
        $device = (& $Adb -s $Tv get-state 2>$null) -join ''
        if ($LASTEXITCODE -eq 0 -and $device.Trim() -eq 'device') { $connected = $true; break }
        if ($attempt -lt 2) { Start-Sleep -Seconds 2 }
    }
    if (!$connected) { throw 'Wi-Fi ADB unavailable. Check address and RSA prompt, then rerun.' }
    if (((Invoke-Tv shell getprop ro.build.version.sdk) -join '').Trim() -ne '24') { throw 'Expected API 24. Review the updated firmware.' }
    if (((Invoke-Tv shell getprop ro.product.manufacturer) -join '').Trim() -notmatch 'Sony') { throw 'Unexpected manufacturer.' }
    if (((Invoke-Tv shell am get-current-user) -join '').Trim() -ne '0') { throw 'Expected Android user 0.' }
}
function Get-Home {
    $r = @(Invoke-Tv shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME)
    $value = ([string]$r[-1]).Trim()
    if ($value -notmatch '^[A-Za-z0-9_.]+/[A-Za-z0-9_.]+$' -or $value -match 'ResolverActivity|FallbackHome') { throw 'HOME is ambiguous. Select the stock HOME and retry diagnostics.' }
    $value
}
function Get-Identity {
    $id = ((Invoke-Tv shell settings get secure android_id) -join '').Trim()
    if ($id -notmatch '^[a-fA-F0-9]{8,16}$') { throw 'Cannot identify this TV.' }
    $id
}
function Save-State([switch]$Updating) {
    $homeComponent = Get-Home
    if (!$Updating -and $homeComponent -match '^house\.bravia\.') { throw 'Expected original HOME. Keep the original backup.' }
    $dir = Join-Path $env:LOCALAPPDATA ('BraviaHome\'+(Get-Date -Format yyyyMMdd-HHmmss-fff))
    $null = New-Item -ItemType Directory -Path $dir
    Invoke-Tv shell getprop | Out-File "$dir\getprop.txt" -Encoding utf8
    foreach ($ns in 'secure','system','global') { Invoke-Tv shell settings list $ns | Out-File "$dir\settings-$ns.txt" -Encoding utf8 }
    Invoke-Tv shell pm list packages -d | Out-File "$dir\disabled.txt" -Encoding utf8
    Invoke-Tv shell pm list packages -f | Out-File "$dir\packages.txt" -Encoding utf8
    Invoke-Tv shell dumpsys tv_input | Out-File "$dir\inputs.txt" -Encoding utf8
    Invoke-Tv shell dumpsys package preferred-activities | Out-File "$dir\preferred.txt" -Encoding utf8
    $stock = $homeComponent
    if ($homeComponent -eq $AliasHome) { $stock = 'com.google.android.leanbacklauncher/.MainActivity' }
    if ($Updating) {
        foreach ($pkg in 'com.google.android.leanbacklauncher','house.bravia.home','house.bravia.panel','com.sony.dtv.tvx','com.sony.dtv.webapi.core','com.android.systemui') {
            Invoke-Tv shell dumpsys package $pkg | Out-File "$dir\$pkg.txt" -Encoding utf8
        }
    }
    [pscustomobject]@{ stock=$stock; original=$homeComponent; identity=(Get-Identity); firmware=((Invoke-Tv shell getprop ro.build.fingerprint) -join '') } |
        ConvertTo-Json | Out-File "$dir\home.json" -Encoding utf8
    Write-Host "Backup: $dir"
    Write-Host "Restore: .\restore.ps1 -Backup '$dir'"
    if ($homeComponent -eq $AliasHome) { Write-Host 'If stock Leanback is disabled, restore.ps1 will ask before enabling it.' }
    Write-Host "Emergency stock screen: & '$Adb' -s '$Tv' shell am start -n '$stock'"
    $dir
}
function Read-State([string]$dir) {
    $s = Get-Content -Raw -LiteralPath (Join-Path $dir home.json) | ConvertFrom-Json
    if ($s.identity -ne (Get-Identity) -or $s.firmware -ne ((Invoke-Tv shell getprop ro.build.fingerprint) -join '') -or
        $s.stock -notmatch '^[A-Za-z0-9_.]+/[A-Za-z0-9_.]+$' -or $s.stock -match '^house\.bravia\.|FallbackHome|ResolverActivity') { throw 'Backup does not match this firmware, TV or stock HOME.' }
    $s
}
function Show-Component([string]$component) {
    $result = (Invoke-Tv shell am start -W -n $component) -join "`n"
    if ($result -match '(?m)^Status:[ \t]*ok[ \t]*\r?$') { return }
    if ($result -notmatch '(?m)^Status:[ \t]*timeout[ \t]*\r?$') { throw "Launch failed: $component" }

    # ActivityManager on API 24 can time out waiting for the first draw.
    # Verify the focused user-0 ActivityRecord. Task history can contain stopped instances.
    $pkg, $class = $component -split '/', 2
    if ($class.StartsWith('.')) { $class = $pkg + $class }
    for ($attempt = 0; $attempt -lt 5; $attempt++) {
        if ($attempt -gt 0) { Start-Sleep -Milliseconds 500 }
        $focused = @(Invoke-Tv shell dumpsys activity activities |
            Where-Object { $_ -cmatch '^\s*mFocusedActivity\s*[:=]' })
        if ($focused.Count -gt 0) {
            if ($focused.Count -ne 1 -or
                $focused[0] -cnotmatch '^\s*mFocusedActivity\s*[:=]\s*ActivityRecord\{\S+\s+u0\s+([^/\s]+)/([^\s}]+)(?=[\s}])') { continue }
        } else {
            # Some vendor dumps omit the ActivityManager focus field.
            $windows = @(Invoke-Tv shell dumpsys window windows | Where-Object { $_ -cmatch '^\s*mCurrentFocus\s*=' })
            if ($windows.Count -ne 1 -or
                $windows[0] -cnotmatch '^\s*mCurrentFocus\s*=\s*Window\{\S+\s+u0\s+([^/\s]+)/([^\s}]+)(?=[\s}])') { continue }
        }
        $actualPkg, $actualClass = $Matches[1], $Matches[2]
        if ($actualClass.StartsWith('.')) { $actualClass = $actualPkg + $actualClass }
        # HomeChoice is the manifest alias of the exact preview Activity, including when
        # am start delivers its intent to a task originally launched through HOME.
        if ($component -ceq $Preview -and "$actualPkg/$actualClass" -ceq $AliasHome) {
            $actualClass = $class
        }
        if ($actualPkg -ceq $pkg -and $actualClass -ceq $class) {
            Write-Host "Activity confirmed in foreground after timeout: $component"
            return
        }
    }
    throw "Launch timed out; user 0 foreground did not confirm $component."
}

function Get-LocalApkDigest([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    try {
        $hash = [Security.Cryptography.SHA256]::Create()
        try { return [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-','') }
        finally { $hash.Dispose() }
    } finally { $stream.Dispose() }
}
# Windows PowerShell 5.1 decodes native pipelines as text. Hash the raw stdout stream.
function Get-InstalledApkDigest([string]$Source) {
    if ($Source -notmatch '^/data/app/[A-Za-z0-9_./=-]+/base\.apk$') { throw 'Unexpected APK hash path' }
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $Adb
    $info.Arguments = "-s $Tv exec-out cat $Source"
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $true
    $process = [Diagnostics.Process]::Start($info)
    $hash = [Security.Cryptography.SHA256]::Create()
    try {
        $digest = $hash.ComputeHash($process.StandardOutput.BaseStream)
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw 'Cannot stream installed APK for verification. Reconnect and retry.' }
        return [BitConverter]::ToString($digest).Replace('-','')
    } finally { $hash.Dispose(); $process.Dispose() }
}
function Copy-InstalledApk([string]$Source, [string]$Destination) {
    if ($Source -notmatch '^/data/app/[A-Za-z0-9_./=-]+/base\.apk$') { throw 'Unexpected installed APK path' }
    $temp = '/sdcard/Download/bravia-backup-' + [guid]::NewGuid().ToString('N') + '.apk'
    $staged = $false
    try {
        try { $null = Invoke-Tv pull $Source $Destination }
        catch {
            Write-Host 'Direct APK pull failed. Copying through Downloads.'
            Open-Tv
            $staged = $true
            $null = Invoke-Tv shell cp $Source $temp
            $null = Invoke-Tv pull $temp $Destination
        }
        $bytes = ((Invoke-Tv shell stat -c %s $Source) -join '').Trim()
        $checksum = Get-InstalledApkDigest $Source
        if ($bytes -notmatch '^\d+$' -or [long]$bytes -le 0 -or [long]$bytes -ne (Get-Item $Destination).Length -or
            $checksum -ne (Get-LocalApkDigest $Destination)) { throw "Backup integrity check failed for $Source" }
    } finally {
        if ($staged) {
            try { $null = Invoke-Tv shell rm -f $temp }
            catch { Open-Tv; $null = Invoke-Tv shell rm -f $temp }
        }
    }
}
