$r = Invoke-RestMethod -Uri 'http://localhost:8080/api/course/list' -Method GET
foreach ($c in $r.data) {
    Write-Host "course: id=$($c.id) name=$($c.course_name) time=$($c.course_time)"
}

# Login as S001
$r2 = Invoke-RestMethod -Uri 'http://localhost:8080/api/student/login' -Method POST -Body '{"studentNo":"S001","password":"123456"}' -ContentType 'application/json'
Write-Host "S001 id=$($r2.data.id)"

# Login as S005
$r3 = Invoke-RestMethod -Uri 'http://localhost:8080/api/student/login' -Method POST -Body '{"studentNo":"S005","password":"123456"}' -ContentType 'application/json'
Write-Host "S005 id=$($r3.data.id)"

# Login as T001
$r4 = Invoke-RestMethod -Uri 'http://localhost:8080/api/teacher/login' -Method POST -Body '{"teacherNo":"T001","password":"123456"}' -ContentType 'application/json'
Write-Host "T001 id=$($r4.data.id)"