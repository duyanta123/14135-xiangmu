$r = Invoke-RestMethod -Uri 'http://localhost:8080/api/student/login' -Method POST -Body '{"studentNo":"S002","password":"123456"}' -ContentType 'application/json'
Write-Host "S002 login success: $($r.success)"
Write-Host "S002 name: $($r.data.name)"
Write-Host "S002 id: $($r.data.id)"

$r2 = Invoke-RestMethod -Uri 'http://localhost:8080/api/student/login' -Method POST -Body '{"studentNo":"S003","password":"123456"}' -ContentType 'application/json'
Write-Host "S003 login success: $($r2.success)"
Write-Host "S003 name: $($r2.data.name)"
Write-Host "S003 id: $($r2.data.id)"

$r3 = Invoke-RestMethod -Uri 'http://localhost:8080/api/student/login' -Method POST -Body '{"studentNo":"S004","password":"123456"}' -ContentType 'application/json'
Write-Host "S004 login success: $($r3.success)"
Write-Host "S004 name: $($r3.data.name)"
Write-Host "S004 id: $($r3.data.id)"