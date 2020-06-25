Param(  
    [parameter(Mandatory = $true)]
    $ApiUrl,
    [switch]$NeedRestart,
    $ContainerId = $null,
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
Write-Output "Pause"
Start-Sleep -Seconds 90
# Initialize paths used by the script
Write-Output "Initialize paths used by the script"
$modulesStateUrl = "$ApiUrl/api/platform/pushnotifications"
$modulesInstallUrl = "$ApiUrl/api/platform/modules/autoinstall"
$restartUrl = "$ApiUrl/api/platform/modules/restart"
$appAuthUrl = "$ApiUrl/connect/token"

# Call homepage, to make sure site is compiled
Write-Output "Call homepage, to make sure site is compiled"
docker logs $ContainerId
try {
    $initResult = Invoke-WebRequest $ApiUrl -UseBasicParsing -SkipCertificateCheck -RetryIntervalSec 5 -MaximumRetryCount 5
    if ($initResult.StatusCode -ne 200) {
        # throw exception when site can't be opened
        Write-Output "Can't open admin site homepage"
        throw "Can't open admin site homepage"
    }
}
catch{
    Write-Output $_.Exception
    exit 1
}

# Initiate modules installation
Write-Output "Authorization"
$authToken = (Get-AuthToken $appAuthUrl $Username $Password)[1]
$headers = @{}
$headers.Add("Authorization", "Bearer $authToken")
Write-Output "Initiate modules installation"
$moduleImportResult = Invoke-RestMethod $modulesInstallUrl -Method Post -Headers $headers -ErrorAction Stop -SkipCertificateCheck -MaximumRetryCount 5 -RetryIntervalSec 5
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
Write-Output "Retrieve notification state"
try {
    do {
        # Retrieve notification state
        $moduleState = Invoke-RestMethod "$modulesStateUrl" -Body $NotificationStateJson -Method Post -ContentType "application/json" -Headers $headers -SkipCertificateCheck
        Write-Output $moduleState
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
    if($NeedRestart){
      Write-Output "Restarting website"
      #$moduleState = Invoke-RestMethod "$restartUrl" -Method Post -ContentType "application/json" -Headers $headers -SkipCertificateCheck
      docker restart $ContainerId
    }
}
catch {
    $cycleCount = $cycleCount + 1 
    $message = $_.Exception.Message
    Write-Output "Error: $message"
    throw $_.Exception
}