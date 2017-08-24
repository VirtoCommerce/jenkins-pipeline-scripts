Param(  
  	[parameter(Mandatory=$true)]
        $apiurl
     )

     . $PSScriptRoot\utilities.ps1

     # Initialize paths used by the script
     $modulesStateUrl = "$apiurl/api/platform/pushnotifications"
     $modulesInstallUrl = "$apiurl/api/platform/modules/autoinstall"
     $modulesRestartUrl = "$apiurl/api/platform/modules/restart"

     # Call homepage, to make sure site is compiled
     $initResult = Invoke-WebRequest $apiurl
     if($initResult.StatusCode -ne 200) # throw exception when site can't be opened
     {
         throw "Can't open admin site homepage"
     }

     # Initiate modules installation
     $headerValue = Create-Authorization
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
      try
      {
            do
            {
                  # Retrieve notification state
                  $headers = @{}
                  $headerValue = Create-Authorization
                  $headers.Add("Authorization", $headerValue)
                  $moduleState = Invoke-RestMethod "$modulesStateUrl" -Body $NotificationStateJson -Method Post -ContentType "application/json" -Headers $headers

                  # display all statuses
                  $notificationState = $moduleState.NotifyEvents[0]       
                  for ($i = $startIndex; $i -lt $notificationState.progressLog.Count; $i++) {
                        Write-Output $notificationState.progressLog[$i].Message 
                  }

                  $startIndex = $notificationState.progressLog.Count - 1
                  $cycleCount = $cycleCount + 1 
                  Start-Sleep -s 3
            }
            while ($notificationState.finished -eq $null -and $cycleCount -lt 60) # stop processing after 3 min or when notifications had stopped $moduleState.NotifyEvents.Length -ne 0 -and 

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
            throw $_.Exception
      }