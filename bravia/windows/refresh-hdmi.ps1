. (Join-Path $PSScriptRoot 'common.ps1')
. (Join-Path $PSScriptRoot 'hdmi.ps1')
Open-Tv
$map = Read-PortMap @(Invoke-Tv shell dumpsys tv_input)
Send-PortMap $map
Write-Host "Validated $($map.Count) HDMI mappings for this boot."
