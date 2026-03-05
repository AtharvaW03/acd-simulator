$m = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/metrics/snapshot" -Method GET
$a = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/agents" -Method GET
Write-Host "Agents:    $($a.Count)"
Write-Host "Queue:     $($m.currentQueueDepth)"
Write-Host "Available: $($m.availableAgents)"
Write-Host "Busy:      $($m.busyAgents)"
Write-Host "Received:  $($m.totalCallsReceived)"
Write-Host "Completed: $($m.totalCallsCompleted)"
Write-Host "Abandoned: $($m.totalCallsAbandoned)"
Write-Host "Dropped:   $($m.totalCallsDropped)"
Write-Host "AvgWait:   $($m.avgWaitTimeMs) ms"
