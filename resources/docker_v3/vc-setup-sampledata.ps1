Param(
    [parameter(Mandatory = $true)]
    $ApiUrl,
    $SampleDataSrc,
    $Username = "admin",
    $Password = "store"
)

function Get-AuthToken {
    param (
        $appAuthUrl,
        $username,
        $password
    )
    Write-Output "Get-AuthToken: appAuthUrl $appAuthUrl"
    $grant_type = "password"
    $content_type = "application/x-www-form-urlencoded"

    $body = @{username=$username; password=$password; grant_type=$grant_type}
    try {
        $response = Invoke-WebRequest -Uri $appAuthUrl -Method Post -ContentType $content_type -Body $body -SkipCertificateCheck -MaximumRetryCount 5 -RetryIntervalSec 5
        $responseContent = $response.Content | ConvertFrom-Json
        return $responseContent.access_token
    }
    catch {
        Write-Output $_.Exception
        exit 1
    }
}  

$sdStateUrl = "$ApiUrl/api/platform/pushnotifications"
if([string]::IsNullOrWhiteSpace($SampleDataSrc)){
    $sdInstallUrl = "$ApiUrl/api/platform/sampledata/autoinstall"
} else {
    $sdInstallUrl = "$ApiUrl/api/platform/sampledata/import?url=$SampleDataSrc"
}
$appAuthUrl = "$ApiUrl/connect/token"

#Start-Sleep -Seconds 15
$authToken = (Get-AuthToken $appAuthUrl $Username $Password)[1]
$headers = @{}
$headers.Add("Authorization", "Bearer $authToken")
$installResult = Invoke-RestMethod -Uri $sdInstallUrl -ContentType "application/json" -Method Post -Headers $headers -SkipCertificateCheck -MaximumRetryCount 5 -RetryIntervalSec 5
Write-Output $installResult

$notificationId = $installResult.id
$NotificationStateJson = @"
     {"Ids":["$notificationId"],"start":0, "count": 1}
"@

$notify = @{}
do {
    Start-Sleep -s 3
    $state = Invoke-RestMethod "$sdStateUrl" -Body $NotificationStateJson -Method Post -ContentType "application/json" -Headers $headers -SkipCertificateCheck
    Write-Output $state
    if ($state.notifyEvents -ne $null ) {
        $notify = $state.notifyEvents
        if ($notify.errorCount -gt 0) {
            Write-Output $notify
            exit 1
        }
    }
}
while (([string]::IsNullOrEmpty($notify.finished)) -and $cycleCount -lt 180)
Write-Output "Sample data installation complete"
