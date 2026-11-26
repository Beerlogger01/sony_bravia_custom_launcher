function Read-PortMap([string[]]$Lines) {
    $ports = @{}
    $ids = @{}
    $section = ''
    foreach ($line in $Lines) {
        if ($line -match '^\s*mHardwareList:') { $section = 'hardware'; continue }
        if ($line -match '^\s*mHardwareInputIdMap:') { $section = 'map'; continue }
        if ($line -match '^\s*m\w+:') { $section = ''; continue }
        if ($section -eq 'hardware' -and $line -match '\bid=(\d+)' ) {
            $device = $Matches[1]
            if ($line -match '\bhdmi_port=([1-4])\b') {
                $port = $Matches[1]
                $ports[$port] = @($ports[$port] | Where-Object { $_ }) + $device
            }
        }
        if ($section -eq 'map' -and $line -match '^\s*(\d+):\s+([A-Za-z0-9_./]+)\s*$') {
            $device = $Matches[1]
            $inputId = $Matches[2]
            if ($ids.ContainsKey($device) -and $ids[$device] -ne $inputId) { throw "Conflicting active input for hardware $device" }
            $ids[$device] = $inputId
        }
    }
    $out = @{}
    foreach ($port in $ports.Keys) {
        $active = @($ports[$port] | Where-Object { $ids.ContainsKey($_) } | ForEach-Object { $ids[$_] } | Select-Object -Unique)
        if ($active.Count -gt 1) { throw "Ambiguous hardware port $port among active inputs. Use the Sony input selector." }
        if ($active.Count -eq 1) { $out[$port] = $active[0] }
    }
    if (@($out.Values | Select-Object -Unique).Count -ne $out.Count) { throw 'Duplicate HDMI input ID.' }
    $out
}

function Send-PortMap($map) {
    $command = @('shell','am','broadcast','--include-stopped-packages','--user','0','-n','house.bravia.home/com.gathin.tvhome.PortMapReceiver','-a','house.bravia.PORT_MAP')
    foreach ($port in $map.Keys) { $command += @('--es',"p$port",$map[$port]) }
    $reply = (Invoke-Tv @command) -join ' '
    if ($reply -notmatch 'result=-1') { throw 'Port map import not confirmed. Check ADB and retry.' }
}
