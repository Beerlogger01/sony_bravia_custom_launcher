. (Join-Path $PSScriptRoot 'common.ps1')
Open-Tv
Write-Host "Firmware: $(Invoke-Tv shell getprop ro.build.display.id)"
Write-Host "Model: $(Invoke-Tv shell getprop ro.product.model)"
Write-Host "HOME: $(Get-Home)"
Invoke-Tv shell dumpsys webviewupdate
Invoke-Tv shell dumpsys tv_input
Invoke-Tv shell pm list packages | Select-String 'sony|mediatek|systemui|chromecast|mediashell'
Write-Host 'HOME candidates and priorities:'
Invoke-Tv shell cmd package query-activities --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME
Write-Host 'Native input selector:'
Invoke-Tv shell cmd package resolve-activity --brief --user 0 -a com.android.tv.action.VIEW_INPUTS
Write-Host 'Current foreground:'
Invoke-Tv shell dumpsys activity activities | Select-String 'mFocusedActivity'
