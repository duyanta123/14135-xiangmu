$r = Invoke-RestMethod -Uri 'http://localhost:8080/api/student/login' -Method POST -Body '{"studentNo":"S001","password":"123456"}' -ContentType 'application/json'
$token = $r.token

$r2 = Invoke-RestMethod -Uri 'http://localhost:8080/api/student/list' -Method GET -Headers @{Authorization="Bearer $token"}
foreach ($s in $r2.data) {
    Write-Host "id=$($s.id) no=$($s.studentNo) name=$($s.name)"
}

# Also check courses
$r3 = Invoke-RestMethod -Uri 'http://localhost:8080/api/course/list' -Method GET
foreach ($c in $r3.data) {
    Write-Host "course: id=$($c.id) name=$($c.course_name) time=$($c.course_time)"
}