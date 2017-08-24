Param(  
  	[parameter(Mandatory=$true)]
        $apiurl,
        $hmacAppId,
        $hmacSecret
     )

     . $PSScriptRoot\utilities.ps1   

     # Initialize paths used by the script
     $modulesStateUrl = "$apiurl/api/platform/pushnotifications"
     $modulesInstallUrl = "$apiurl/api/platform/modules/autoinstall"
     $modulesRestartUrl = "$apiurl/api/platform/modules/restart"

     # Call homepage, to make sure site is compiled
     $initResult = Invoke-WebRequest $apiurl -UseBasicParsing
     if($initResult.StatusCode -ne 200) # throw exception when site can't be opened
     {
         throw "Can't open admin site homepage"
     }

     # Initiate modules installation
     $headerValue = Create-Authorization $hmacAppId $hmacSecret
     $headers = @{}
     $headers.Add("Authorization", $headerValue)
     $moduleImportResult = Invoke-RestMethod $modulesInstallUrl -Method Post -Headers $headers -ErrorAction Stop    

     # save notification id, so we can get status of the operation
     $notificationId = $moduleImportResult.id

     # create status request json, we only need to get 1 and 1st notification
     $NotificationStateJson = @"
     {"Ids":["$notificationId"],"start":0, "count": 1}     
"@
          
     $cycleCount = 0
     $startIndex = 0
     $abort = $false
      try
      {
            do
            {
                  # Retrieve notification state
                  $moduleState = Invoke-RestMethod "$modulesStateUrl" -Body $NotificationStateJson -Method Post -ContentType "application/json" -Headers $headers

                  # display all statuses
                  if($moduleState.NotifyEvents -ne $null -and $moduleState.NotifyEvents.Length -ne 0)
                  {
                        $notificationState = $moduleState.NotifyEvents[0]
                        if($notificationState.progressLog.Count -gt 0 -and $notificationState.progressLog -ne $null)
                        {
                              #Write-Output $notificationState
                              for ($i = $startIndex; $i -lt $notificationState.progressLog.Count; $i++) {
                                    #Write-Output "Getting index $i with length " $notificationState.progressLog.Count
                                    Write-Output $notificationState.progressLog[$i].Message 
                              }
                              $startIndex = $notificationState.progressLog.Count - 1
                        }                        
                  }
                  else { # modules are already installed, exit the loop
                        Write-Output "Automatic module installation didn't start, possibly due to them already being installed. Quitting install."
                        $abort = $true
                  }
                  $cycleCount = $cycleCount + 1 
                  Start-Sleep -s 3
            }
            while (!$abort -and $notificationState.finished -eq $null -and $cycleCount -lt 60) # stop processing after 3 min or when notifications had stopped $moduleState.NotifyEvents.Length -ne 0 -and 

            Write-Output "Restarting website"
            $moduleState = Invoke-RestMethod "$modulesRestartUrl" -Method Post -ContentType "application/json" -Headers $headers
            Write-Output $moduleState                  
      }
      catch
      {
            $cycleCount = $cycleCount + 1 
            $message = $_.Exception.Message
            Write-Output "Error: $message"
            throw $_.Exception
      }