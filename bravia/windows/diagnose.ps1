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

. (Join-Path $PSScriptRoot 'hdmi.ps1')
if ((Invoke-Tv shell pm list packages -d --user 0) -contains 'package:com.google.android.leanbacklauncher') {
    Write-Warning 'Stock Leanback is disabled for user 0. Recovery requires enabling it.'
}
foreach ($pkg in 'house.bravia.home','house.bravia.panel') {
    Write-Host $pkg
    Invoke-Tv shell dumpsys package $pkg | Select-String 'versionCode=|versionName='
}
Read-PortMap (Invoke-Tv shell dumpsys tv_input)
