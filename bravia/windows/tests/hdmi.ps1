. (Join-Path $PSScriptRoot '../hdmi.ps1')
$data = @'
mHardwareList:
 TvInputHardwareInfo {id=83, type=9, hdmi_port=2}
 TvInputHardwareInfo {id=17, type=9, hdmi_port=1}
 TvInputHardwareInfo {id=1, type=2}
mHdmiDeviceList:
mHardwareInputIdMap: deviceId -> inputId
  83: example/.ExternalTvInputService/HW83
  17: example/.ExternalTvInputService/HW17
mHdmiInputIdMap: id -> inputId
  17: example/.ExternalTvInputService/CEC2
'@ -split "\r?\n"
$result = Read-PortMap $data
if ($result.Count -ne 2 -or $result['1'] -ne 'example/.ExternalTvInputService/HW17' -or $result['2'] -ne 'example/.ExternalTvInputService/HW83') {throw 'Incorrect hardware join'}
if ((Read-PortMap @('unknown firmware format')).Count -ne 0) {throw 'Unverified mapping accepted'}
try {
    $null = Read-PortMap ($data -replace 'hdmi_port=2','hdmi_port=1')
    throw 'Duplicate connector accepted'
} catch {if ($_.Exception.Message -notlike 'Ambiguous hardware port*') {throw}}
'HDMI parser: permutation, CEC exclusion, missing evidence, duplicate rejection passed.'

$inactiveTwin = $data -replace 'mHardwareList:', "mHardwareList:`n TvInputHardwareInfo {id=111, type=9, hdmi_port=1}"
if ((Read-PortMap $inactiveTwin)['1'] -ne $result['1']) { throw 'Inactive duplicate altered the selected port' }
