. C:\Projects\GitHub\jenkins-pipeline-scripts\resources\azure\utilities.ps1

Write-Output $PSScriptRoot

$apiurl = 'http://localhost/admin'
$apiurl = 'http://ci.virtocommerce.com:8090'
$apiurl = 'http://192.168.1.107:8090'
$apiurl = 'http://localhost/admin'
$notificationId = "7a4284ed-9002-4bb6-80c8-c7ad59656acb"
$appId = '27e0d789f12641049bd0e939185b4fd2'
$secret = '34f0a3c12c9dbb59b63b5fece955b7b2b9a3b20f84370cba1524dd5c53503a2e2cb733536ecf7ea1e77319a47084a3a2c9d94d36069a432ecc73b72aeba6ea78'
$modulesStateUrl = "$apiurl/api/platform/pushnotifications"
$modulesInstallUrl = "$apiurl/api/platform/modules/autoinstall"
$modulesRestartUrl = "$apiurl/api/platform/modules/restart"

$initResult = Invoke-WebRequest $apiurl
if($initResult.StatusCode -ne 200) # throw exception when site can't be opened
{
    throw "Can't open admin site homepage"
}

$headerValue = Create-Authorization
$headers = @{}
$headers.Add("Authorization", $headerValue)

$install = Invoke-RestMethod $modulesInstallUrl -Headers $headers -Method Post -ErrorAction Stop
$notificationId = $install.id

$cycleCount = 0
$startIndex = 0
try
{
      do
      {
$NotificationStateJson = @"
            {"Ids":["$notificationId"], "start":$startIndex, "count": 1}
"@            

            $headerValue = Create-Authorization
                  
            $headers = @{}
            $headers.Add("Authorization", $headerValue)
            $moduleState = Invoke-RestMethod "$modulesStateUrl" -Body $NotificationStateJson -Method Post -ContentType "application/json" -Headers $headers
            $notificationState = $moduleState.NotifyEvents[0]       

            # display all statuses
            for ($i = $startIndex; $i -lt $notificationState.progressLog.Count; $i++) {
                Write-Output $notificationState.progressLog[$i].Message 
            }

            $startIndex = $notificationState.progressLog.Count - 1
            $cycleCount = $cycleCount + 1 
            #$startIndex += $moduleState.NotifyEvents.Length
            Start-Sleep -s 3
      }
      while ($notificationState.finished -eq $null -and $cycleCount -lt 30) # stop processing after 5 min or when notifications had stopped $moduleState.NotifyEvents.Length -ne 0 -and 

      Write-Output "Restarting website"
      $headers = @{}
      $headers.Add("Authorization", $headerValue)
      $moduleState = Invoke-RestMethod "$modulesRestartUrl" -Method Post -ContentType "application/json" -Headers $headers
      Write-Output $moduleState
}
catch
{
    $cycleCount = $cycleCount + 1 
    $message = $_.Exception.Message
    Write-Output "Error: $message"
}