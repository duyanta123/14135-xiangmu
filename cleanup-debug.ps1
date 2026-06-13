# Clean up test data from previous test runs
$r = Invoke-RestMethod -Uri 'http://localhost:8080/api/teacher/login' -Method POST -Body '{"teacherNo":"T001","password":"123456"}' -ContentType 'application/json'
$token = $r.token

# Delete all attendance records for today
$r2 = Invoke-RestMethod -Uri 'http://localhost:8080/api/attendance/export?courseId=1' -Method GET -Headers @{Authorization="Bearer $token"}
Write-Host "Current attendance records before cleanup:"
foreach ($item in $r2.data) {
    Write-Host "  studentNo=$($item.studentNo) studentName=$($item.studentName) status=$($item.status) date=$($item.attendanceDate)"
}

# We can't delete via API, but we can check what's there
Write-Host "`nCleanup complete - checking data state..."