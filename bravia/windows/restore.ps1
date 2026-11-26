param([Parameter(Mandatory=$true)][string]$Backup)
. (Join-Path $PSScriptRoot 'common.ps1')
Open-Tv
$s = Read-State $Backup
Show-Component $s.stock
$current = $null
try { $current = Get-Home } catch { Write-Host 'Restoring the saved explicit HOME choice.' }
if ($current -ne $s.stock) { Invoke-Tv shell cmd package set-home-activity --user 0 $s.stock }
# Return to stock before optionally asking the custom app to retire its alias.
if ((Invoke-Tv shell pm list packages house.bravia.home) -contains 'package:house.bravia.home') {
    try { Invoke-Tv shell am broadcast --include-stopped-packages --user 0 -n house.bravia.home/com.gathin.tvhome.HomeEnrollment -a house.bravia.RETIRE }
    catch { Write-Warning 'Custom app unavailable. Stock HOME was selected independently.' }
}
if ((Get-Home) -ne $s.stock) { throw 'Stock activity opened. Verify Home in Android settings.' }
Invoke-Tv shell input keyevent 3
Write-Host "Restored HOME: $($s.stock)"
