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
    $response = Invoke-WebRequest -Uri $appAuthUrl -Method Post -ContentType $content_type -Body $body -SkipCertificateCheck
    $responseContent = $response.Content | ConvertFrom-Json
    #return "$($responseContent.token_type) $($responseContent.access_token)"
    return $responseContent.access_token
}   

if ([string]::IsNullOrWhiteSpace($hmacAppId)) {
    $hmacAppId = "${env:HMAC_APP_ID}"
}

if ([string]::IsNullOrWhiteSpace($hmacSecret)) {
    $hmacSecret = "${env:HMAC_SECRET}"
}
if ([string]::IsNullOrWhiteSpace($SampleDataSrc)) {
    $SampleDataSrc = "${env:SAMPLE_DATA}"
}

$sdStateUrl = "$ApiUrl/api/platform/pushnotifications"
if([string]::IsNullOrWhiteSpace($SampleDataSrc)){
    $sdInstallUrl = "$ApiUrl/api/platform/sampledata/autoinstall"
} else {
    $sdInstallUrl = "$ApiUrl/api/platform/sampledata/import?url=$SampleDataSrc"
}

Start-Sleep -Seconds 15
$authToken = (Get-AuthToken $appAuthUrl $Username $Password)[1]
$headers = @{}
$headers.Add("Authorization", "Bearer $authToken")
$installResult = Invoke-RestMethod -Uri $sdInstallUrl -ContentType "application/json" -Method Post -Headers $headers -SkipCertificateCheck
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
