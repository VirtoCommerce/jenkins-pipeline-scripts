Param(
    [parameter(Mandatory = $true)]
    $apiurl,
    $sampleDataSrc,
    $hmacAppId,
    $hmacSecret
)

. $PSScriptRoot\utilities.ps1

if ([string]::IsNullOrWhiteSpace($hmacAppId)) {
    $hmacAppId = "${env:HMAC_APP_ID}"
}

if ([string]::IsNullOrWhiteSpace($hmacSecret)) {
    $hmacSecret = "${env:HMAC_SECRET}"
}
if ([string]::IsNullOrWhiteSpace($sampleDataSrc)) {
    $sampleDataSrc = "${env:SAMPLE_DATA}"
}

$sdStateUrl = "$apiurl/api/platform/pushnotifications"
if([string]::IsNullOrWhiteSpace($sampleDataSrc)){
    $sdInstallUrl = "$apiurl/api/platform/sampledata/autoinstall"
} else {
    $sdInstallUrl = "$apiurl/api/platform/sampledata/import?url=$sampleDataSrc"
}


$headerValue = Create-Authorization $hmacAppId $hmacSecret
$headers = @{}
$headers.Add("Authorization", $headerValue)
$installResult = Invoke-RestMethod -Uri $sdInstallUrl -ContentType "application/json" -Method Post -Headers $headers
Write-Output $installResult

$notificationId = $installResult.id
$NotificationStateJson = @"
     {"Ids":["$notificationId"],"start":0, "count": 1}
"@

$notify = @{}
do {
    Start-Sleep -s 3
    $state = Invoke-RestMethod "$sdStateUrl" -Body $NotificationStateJson -Method Post -ContentType "application/json" -Headers $headers
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
