# ============================================================
# ACD Simulator  Stress Test Script (Fixed)
# ============================================================

$baseUrl = "http://localhost:8080"
$utf8 = [System.Text.Encoding]::UTF8

function Write-Header($text) {
    Write-Host ""
    Write-Host ("=" * 60) -ForegroundColor Cyan
    Write-Host "  $text" -ForegroundColor Cyan
    Write-Host ("=" * 60) -ForegroundColor Cyan
}

function Write-Result($label, $value) {
    Write-Host ("  {0,-30} {1}" -f $label, $value)
}

function Send-Json($url, $jsonString) {
    return Invoke-WebRequest -Uri $url -Method POST -ContentType "application/json; charset=utf-8" -Body ($utf8.GetBytes($jsonString)) -UseBasicParsing -TimeoutSec 60
}

# Phase 0: Health Check
Write-Header "Phase 0: Health Check"
try {
    $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method GET -TimeoutSec 5
    Write-Host "  Server is UP" -ForegroundColor Green
}
catch {
    Write-Host "  Server not reachable!" -ForegroundColor Red
    exit 1
}

# Phase 1: Agent List
Write-Header "Phase 1: Agent List"
$agents = Invoke-RestMethod -Uri "$baseUrl/api/v1/agents" -Method GET
Write-Result "Registered agents:" $agents.Count

# Phase 2: Single-call latency
Write-Header "Phase 2: Single-Call Latency (20 sequential calls)"
$latencies = @()
for ($i = 1; $i -le 20; $i++) {
    $pri = Get-Random -Minimum 1 -Maximum 6
    $json = '{"callerId":"+1-SOLO-' + $i + '","priority":' + $pri + '}'
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $null = Send-Json "$baseUrl/api/v1/calls" $json
        $sw.Stop()
        $latencies += $sw.ElapsedMilliseconds
    }
    catch {
        $sw.Stop()
        $latencies += -1
    }
}
$okL = $latencies | Where-Object { $_ -ge 0 }
if ($okL.Count -gt 0) {
    $minL = [Math]::Round(($okL | Measure-Object -Minimum).Minimum)
    $maxL = [Math]::Round(($okL | Measure-Object -Maximum).Maximum)
    $avgL = [Math]::Round(($okL | Measure-Object -Average).Average, 1)
    $failCount = ($latencies | Where-Object { $_ -lt 0 }).Count
    Write-Result "Successful:" "$($okL.Count) / 20"
    Write-Result "Min / Avg / Max latency:" "$minL / $avgL / $maxL ms"
}
else {
    Write-Host "  All calls failed!" -ForegroundColor Red
}

# Phase 3: Bulk ingestion - increasing batch sizes
Write-Header "Phase 3: Bulk Ingestion - Scaling Up"
$bulkSizes = @(10, 50, 100, 500, 1000, 2000, 5000)
foreach ($size in $bulkSizes) {
    # Build JSON array manually for speed
    $parts = @()
    for ($j = 0; $j -lt $size; $j++) {
        $pri = Get-Random -Minimum 1 -Maximum 10
        $parts += '{"callerId":"+1-B-' + $size + '-' + $j + '","priority":' + $pri + '}'
    }
    $json = '[' + ($parts -join ',') + ']'

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $resp = Send-Json "$baseUrl/api/v1/calls/bulk" $json
        $sw.Stop()
        $tput = [Math]::Round($size / ($sw.ElapsedMilliseconds / 1000), 0)
        $msg = "{0,6} calls: {1,6} ms  ~{2,6} calls/sec  HTTP {3}" -f $size, $sw.ElapsedMilliseconds, $tput, $resp.StatusCode
        Write-Host "  $msg" -ForegroundColor Green
    }
    catch {
        $sw.Stop()
        $code = "ERR"
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        $msg = "{0,6} calls: {1,6} ms  $code" -f $size, $sw.ElapsedMilliseconds
        Write-Host "  $msg" -ForegroundColor Red
    }
    Start-Sleep -Milliseconds 300
}

# Phase 4: Rapid-fire sequential (simulates sustained load)
Write-Header "Phase 4: Sustained Load - 500 rapid sequential calls"
$okCount = 0; $errCount = 0
$sw = [System.Diagnostics.Stopwatch]::StartNew()
for ($i = 0; $i -lt 500; $i++) {
    $pri = Get-Random -Minimum 1 -Maximum 6
    $json = '{"callerId":"+1-RAPID-' + $i + '","priority":' + $pri + '}'
    try {
        $null = Send-Json "$baseUrl/api/v1/calls" $json
        $okCount++
    }
    catch {
        $errCount++
    }
}
$sw.Stop()
$tput = [Math]::Round(500 / ($sw.ElapsedMilliseconds / 1000), 0)
Write-Result "Total time:" "$($sw.ElapsedMilliseconds) ms"
Write-Result "Throughput:" "~$tput req/sec"
Write-Result "Success / Fail:" "$okCount / $errCount"

# Phase 5: Parallel bombardment using runspaces (much faster than Start-Job)
Write-Header "Phase 5: Parallel Bombardment (runspace pool)"
$concurrencyLevels = @(10, 50, 100, 200, 500)

foreach ($conc in $concurrencyLevels) {
    $pool = [RunspaceFactory]::CreateRunspacePool(1, $conc)
    $pool.Open()
    $handles = @()

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    for ($i = 0; $i -lt $conc; $i++) {
        $ps = [PowerShell]::Create().AddScript({
                param($url, $id)
                $pri = Get-Random -Minimum 1 -Maximum 6
                $json = '{"callerId":"+1-P-' + $id + '","priority":' + $pri + '}'
                try {
                    $r = Invoke-WebRequest -Uri "$url/api/v1/calls" -Method POST -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) -UseBasicParsing -TimeoutSec 15
                    return $r.StatusCode
                }
                catch {
                    if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
                    return 0
                }
            }).AddArgument($baseUrl).AddArgument("$conc-$i")
        $ps.RunspacePool = $pool
        $handles += @{ PS = $ps; Handle = $ps.BeginInvoke() }
    }

    # Collect results
    $results = @()
    foreach ($h in $handles) {
        try {
            $results += $h.PS.EndInvoke($h.Handle)
        }
        catch {
            $results += 0
        }
        $h.PS.Dispose()
    }
    $pool.Close()
    $sw.Stop()

    $ok = @($results | Where-Object { $_ -eq 200 -or $_ -eq 202 }).Count
    $rateLim = @($results | Where-Object { $_ -eq 429 }).Count
    $srvErr = @($results | Where-Object { $_ -ge 500 }).Count
    $other = $conc - $ok - $rateLim - $srvErr
    $tput = [Math]::Round($conc / ($sw.ElapsedMilliseconds / 1000), 0)

    Write-Host ""
    Write-Result "Concurrency:" $conc
    Write-Result "Wall time:" "$($sw.ElapsedMilliseconds) ms"
    Write-Result "Throughput:" "~$tput req/sec"
    Write-Result "2xx OK:" $ok
    Write-Result "429 Rate Limited:" $rateLim
    Write-Result "5xx Server Error:" $srvErr
    Write-Result "Timeout/Other:" $other

    Start-Sleep -Seconds 1
}

# Phase 6: Queue overflow - flood to the 10,000 capacity limit
Write-Header "Phase 6: Queue Overflow Test (10,000 capacity)"
Write-Host "  Flooding 15 batches of 1,000 calls (15,000 total)..." -ForegroundColor Yellow
$totalOk = 0; $totalFail = 0
for ($wave = 1; $wave -le 15; $wave++) {
    $parts = @()
    for ($j = 0; $j -lt 1000; $j++) {
        $pri = Get-Random -Minimum 1 -Maximum 10
        $parts += '{"callerId":"+1-F-' + $wave + '-' + $j + '","priority":' + $pri + '}'
    }
    $json = '[' + ($parts -join ',') + ']'
    try {
        $resp = Send-Json "$baseUrl/api/v1/calls/bulk" $json
        $totalOk += 1000
        Write-Host "  Wave $wave/15: HTTP $($resp.StatusCode) - 1000 accepted" -ForegroundColor Green
    }
    catch {
        $code = "?"
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        $totalFail += 1000
        Write-Host "  Wave $wave/15: HTTP $code - REJECTED" -ForegroundColor Red
    }
}
Write-Host ""
Write-Result "Total sent:" ($totalOk + $totalFail)
Write-Result "Accepted:" $totalOk
Write-Result "Rejected:" $totalFail

# Phase 7: Final metrics
Write-Header "Phase 7: Final Metrics Snapshot"
Start-Sleep -Seconds 5
try {
    $m = Invoke-RestMethod -Uri "$baseUrl/api/v1/metrics/snapshot" -Method GET -TimeoutSec 10
    Write-Result "Queue Depth:" $m.currentQueueDepth
    Write-Result "Available Agents:" $m.availableAgents
    Write-Result "Busy Agents:" $m.busyAgents
    Write-Result "Total Received:" $m.totalCallsReceived
    Write-Result "Total Completed:" $m.totalCallsCompleted
    Write-Result "Total Abandoned:" $m.totalCallsAbandoned
    Write-Result "Total Dropped:" $m.totalCallsDropped
    if ($m.avgWaitTimeMs) {
        Write-Result "Avg Wait Time:" "$([Math]::Round($m.avgWaitTimeMs, 1)) ms"
    }
}
catch {
    Write-Host "  Could not fetch metrics" -ForegroundColor Red
}

Write-Header "STRESS TEST COMPLETE"
Write-Host "  Open http://localhost:8080 for the live dashboard." -ForegroundColor Yellow
Write-Host ""
