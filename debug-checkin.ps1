$r = Invoke-RestMethod -Uri 'http://localhost:8080/api/student/login' -Method POST -Body '{"studentNo":"S001","password":"123456"}' -ContentType 'application/json'
$token = $r.token
Write-Host "Token: $($token.Substring(0, 20))..."

$r2 = Invoke-RestMethod -Uri 'http://localhost:8080/api/attendance/check-in' -Method POST -Body '{"studentId":2,"courseId":1}' -ContentType 'application/json' -Headers @{Authorization="Bearer $token"}
$r2 | ConvertTo-Json -Depth 5
Write-Host "---"
Write-Host "success: $($r2.success)"
Write-Host "status: $($r2.status)"
Write-Host "message: $($r2.message)"