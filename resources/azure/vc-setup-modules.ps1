Param(  
    [parameter(Mandatory = $true)]
    $apiurl,
    $hmacAppId,
    $hmacSecret,
    $needRestart
)

. $PSScriptRoot\utilities.ps1   

if ([string]::IsNullOrWhiteSpace($hmacAppId)) {
    $hmacAppId = "${env:HMAC_APP_ID}"
}

if ([string]::IsNullOrWhiteSpace($hmacSecret)) {
    $hmacSecret = "${env:HMAC_SECRET}"
}     

# Initialize paths used by the script
$modulesStateUrl = "$apiurl/api/platform/pushnotifications"
$modulesInstallUrl = "$apiurl/api/platform/modules/autoinstall"
$restartUrl = "$apiurl/api/platform/modules/restart"

# Call homepage, to make sure site is compiled
$initResult = Invoke-WebRequest $apiurl -UseBasicParsing
if ($initResult.StatusCode -ne 200) {
    # throw exception when site can't be opened
    throw "Can't open admin site homepage"
}

# Initiate modules installation
$headerValue = Create-Authorization $hmacAppId $hmacSecret
$headers = @{}
$headers.Add("Authorization", $headerValue)
$moduleImportResult = Invoke-RestMethod $modulesInstallUrl -Method Post -Headers $headers -ErrorAction Stop
Write-Output $moduleImportResult
Start-Sleep -s 1

# save notification id, so we can get status of the operation
$notificationId = $moduleImportResult.id

# create status request json, we only need to get 1 and 1st notification
$NotificationStateJson = @"
     {"Ids":["$notificationId"],"start":0, "count": 1}     
"@

$cycleCount = 0
$startIndex = 0
try {
    do {
        # Retrieve notification state
        $moduleState = Invoke-RestMethod "$modulesStateUrl" -Body $NotificationStateJson -Method Post -ContentType "application/json" -Headers $headers

        # display all statuses
        if ($moduleState.NotifyEvents -ne $null -and $moduleState.NotifyEvents.Length -ne 0) {
            $notificationState = $moduleState.NotifyEvents[0]
            if ($notificationState.progressLog.Count -gt 0 -and $notificationState.progressLog -ne $null) {
                #Write-Output $notificationState
                for ($i = $startIndex; $i -lt $notificationState.progressLog.Count; $i++) {
                    Write-Output $notificationState.progressLog[$i].Message 
                }
                $startIndex = $notificationState.progressLog.Count - 1
            }                        
        }
        $cycleCount = $cycleCount + 1 
        Start-Sleep -s 3
    }
    while ($notificationState.finished -eq $null -and $cycleCount -lt 60) # stop processing after 3 min or when notifications had stopped $moduleState.NotifyEvents.Length -ne 0 -and
    if($null -eq $notificationState.finished){
        Write-Output "error on modules installation"
        Write-Output $moduleState
        exit 1
    }
    if($null -ne $needRestart -and $needRestart -gt 0){
      Write-Output "Restarting website"
      $moduleState = Invoke-RestMethod "$restartUrl" -Method Post -ContentType "application/json" -Headers $headers
    }
}
catch {
    $cycleCount = $cycleCount + 1 
    $message = $_.Exception.Message
    Write-Output "Error: $message"
    throw $_.Exception
}