# Quick test: see what the bulk endpoint actually returns
$body = '[{"callerId":"+1-555-0101","priority":2},{"callerId":"+1-555-0102","priority":5}]'
try {
    $resp = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/calls/bulk" -Method POST -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -UseBasicParsing
    Write-Host "STATUS: $($resp.StatusCode)"
    Write-Host "BODY: $($resp.Content)"
}
catch {
    Write-Host "ERROR STATUS: $([int]$_.Exception.Response.StatusCode)"
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Host "ERROR BODY: $($reader.ReadToEnd())"
    $reader.Close()
}
