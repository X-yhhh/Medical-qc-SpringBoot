$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8080"
$stamp = Get-Date -Format "yyyyMMddHHmmss"
$doctorUser = "doctor_smoke_$stamp"
$adminUser = "admin_smoke_$stamp"
$password = "P@ssw0rd123"
$doctorCookie = Join-Path $env:TEMP "doctor_$stamp.cookies"
$adminCookie = Join-Path $env:TEMP "admin_$stamp.cookies"
$imagePath = "F:\Medical QC SYS\medical-qc-backend\python_model\data\head_ct\000.png"

function Invoke-CurlJson {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ArgsLine
    )

    $raw = & cmd.exe /c ("curl.exe -s -S " + $ArgsLine)
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return $null
    }

    return $raw | ConvertFrom-Json
}

function Escape-JsonForCurl {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Json
    )

    return $Json.Replace('"', '\"')
}

try {
    $doctorRegisterBody = @{
        username   = $doctorUser
        email      = "$doctorUser@example.com"
        password   = $password
        full_name  = "Smoke Doctor"
        hospital   = "Codex Hospital"
        department = "Radiology"
        role       = "doctor"
    } | ConvertTo-Json -Compress

    $adminRegisterBody = @{
        username   = $adminUser
        email      = "$adminUser@example.com"
        password   = $password
        full_name  = "Smoke Admin"
        hospital   = "Codex Hospital"
        department = "Admin"
        role       = "admin"
    } | ConvertTo-Json -Compress

    $null = Invoke-CurlJson ('-H "Content-Type: application/json" -X POST -d "' +
        (Escape-JsonForCurl $doctorRegisterBody) + '" ' + $baseUrl + '/api/v1/auth/register')
    $null = Invoke-CurlJson ('-H "Content-Type: application/json" -X POST -d "' +
        (Escape-JsonForCurl $adminRegisterBody) + '" ' + $baseUrl + '/api/v1/auth/register')

    $doctorLoginBody = @{
        username = $doctorUser
        password = $password
        role     = "doctor"
    } | ConvertTo-Json -Compress

    $adminLoginBody = @{
        username = $adminUser
        password = $password
        role     = "admin"
    } | ConvertTo-Json -Compress

    $doctorLogin = Invoke-CurlJson ('-c "' + $doctorCookie + '" -b "' + $doctorCookie +
        '" -H "Content-Type: application/json" -X POST -d "' +
        (Escape-JsonForCurl $doctorLoginBody) + '" ' + $baseUrl + '/api/v1/auth/login')
    $adminLogin = Invoke-CurlJson ('-c "' + $adminCookie + '" -b "' + $adminCookie +
        '" -H "Content-Type: application/json" -X POST -d "' +
        (Escape-JsonForCurl $adminLoginBody) + '" ' + $baseUrl + '/api/v1/auth/login')

    $currentUser = Invoke-CurlJson ('-b "' + $doctorCookie + '" ' + $baseUrl + '/api/v1/auth/current')
    $overview = Invoke-CurlJson ('-b "' + $doctorCookie + '" ' + $baseUrl + '/api/v1/dashboard/overview')
    $summaryStats = Invoke-CurlJson ('-b "' + $doctorCookie + '" ' + $baseUrl + '/api/v1/summary/stats')
    $adminUsers = Invoke-CurlJson ('-b "' + $adminCookie + '" "' + $baseUrl + '/api/v1/admin/users?page=1&limit=5"')

    $mockDetect = Invoke-CurlJson ('-b "' + $doctorCookie + '" -F "file=@' + $imagePath +
        '" -F "patient_name=Smoke Patient" -F "exam_id=MOCK-' + $stamp +
        '" -F "source_mode=local" ' + $baseUrl + '/api/v1/quality/head/detect')

    $taskId = $mockDetect.taskId
    $mockResult = $null

    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Seconds 1
        $candidate = Invoke-CurlJson ('-b "' + $doctorCookie + '" "' + $baseUrl + '/api/v1/quality/tasks/' + $taskId + '"')
        if ($candidate.status -eq "SUCCESS") {
            $mockResult = $candidate
            break
        }
    }

    $hemorrhage = Invoke-CurlJson ('-b "' + $doctorCookie + '" -F "file=@' + $imagePath +
        '" -F "patient_name=Hemorrhage Smoke" -F "patient_code=HSMOKE-' + $stamp +
        '" -F "exam_id=HEM-' + $stamp +
        '" -F "gender=男" -F "age=35" -F "study_date=2026-03-10" -F "source_mode=local" ' +
        $baseUrl + '/api/v1/quality/hemorrhage')

    $history = Invoke-CurlJson ('-b "' + $doctorCookie + '" "' + $baseUrl + '/api/v1/quality/hemorrhage/history?limit=5"')
    $summaryRecent = Invoke-CurlJson ('-b "' + $doctorCookie + '" "' + $baseUrl + '/api/v1/summary/recent?page=1&limit=5"')

    [PSCustomObject]@{
        doctorLoginUser      = $doctorLogin.username
        adminLoginUser       = $adminLogin.username
        currentUser          = $currentUser.username
        overviewKeys         = ($overview.PSObject.Properties.Name -join ",")
        summaryStatsKeys     = ($summaryStats.PSObject.Properties.Name -join ",")
        adminUsersKeys       = ($adminUsers.PSObject.Properties.Name -join ",")
        mockTaskStatus       = if ($mockResult) { $mockResult.status } else { "TIMEOUT" }
        mockTaskId           = $taskId
        hemorrhagePrediction = $hemorrhage.prediction
        hemorrhageRecordId   = $hemorrhage.record_id
        historyCount         = @($history.data).Count
        summaryRecentKeys    = ($summaryRecent.PSObject.Properties.Name -join ",")
    } | ConvertTo-Json -Depth 5
}
finally {
    Remove-Item $doctorCookie, $adminCookie -Force -ErrorAction SilentlyContinue
}
