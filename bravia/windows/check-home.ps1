param([string]$PreviousReport)
. (Join-Path $PSScriptRoot 'common.ps1')
Open-Tv
function Foreground {
    $matchesFocus = @(Invoke-Tv shell dumpsys activity activities | Select-String 'mFocusedActivity:.* u0 ([A-Za-z0-9_.]+/[A-Za-z0-9_.]+)')
    if ($matchesFocus.Count -ne 1) { throw 'Foreground component is ambiguous.' }
    $matchesFocus[0].Matches[0].Groups[1].Value
}
$boot = ((Invoke-Tv shell cat /proc/sys/kernel/random/boot_id) -join '').Trim()
$identity = Get-Identity
if ($PreviousReport) {
    $old = Get-Content -Raw -LiteralPath $PreviousReport | ConvertFrom-Json
    if ($old.identity -ne $identity -or $old.boot -eq $boot) {throw 'Expected the same TV after a separate reboot.'}
}
$null = Read-Host 'Open YouTube or another app using the remote, then press Enter on the PC'
$before = Foreground
$null = Read-Host 'Press the PHYSICAL Home once, wait for the screen and press Enter on the PC'
$after = Foreground
$resolver = Get-Home
$candidates = @(Invoke-Tv shell cmd package query-activities --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME)
$dir = Join-Path $env:LOCALAPPDATA 'BraviaHome'
$null = New-Item -ItemType Directory -Force $dir
$file = Join-Path $dir ("physical-home-"+(Get-Date -Format yyyyMMdd-HHmmss-fff)+".json")
[pscustomobject]@{boot=$boot;identity=$identity;before=$before;after=$after;resolver=$resolver;candidates=$candidates} |
    ConvertTo-Json | Out-File -LiteralPath $file -Encoding utf8
Write-Host "Foreground: $before -> $after. Resolver: $resolver. Report: $file"
if ($before -eq $after -or $after -ne $resolver) {Write-Warning 'The observed transition does not confirm the resolved HOME.'}
