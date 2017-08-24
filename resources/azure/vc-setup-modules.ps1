Param(  
  	[parameter(Mandatory=$true)]
        $apiurl
     )

     Write-Output $PSScriptRoot
     . $PSScriptRoot\utilities.ps1
     $modulesStateUrl = "$apiurl/api/platform/pushnotifications"
     $modulesInstallUrl = "$apiurl/api/platform/modules/autoinstall"

     # Initiate modules installation
     $moduleImportResult = Invoke-RestMethod $modulesInstallUrl -Method Post -ErrorAction Stop
     
     Write-Output "Module install result: $moduleImportResult"
     $notificationId = $moduleImportResult.id

     $NotificationStateJson = @"
     {"Ids":["$notificationId"],"start":0}     
"@

     # Wait until sample data have been imported
     Write-Output "Waiting for modules install to be completed"

     $appId = '27e0d789f12641049bd0e939185b4fd2'
     $secret = '34f0a3c12c9dbb59b63b5fece955b7b2b9a3b20f84370cba1524dd5c53503a2e2cb733536ecf7ea1e77319a47084a3a2c9d94d36069a432ecc73b72aeba6ea78'
          
     $cycleCount = 0
      try
      {
            do
            {
                  Start-Sleep -s 5

                  $timestampString = [System.DateTime]::UtcNow.ToString("o", [System.Globalization.CultureInfo]::InvariantCulture)
                  $hmacsha = New-Object System.Security.Cryptography.HMACSHA256
                  $hmacsha.key = $secret | convert-fromhex
                        
                  $signature = $hmacsha.ComputeHash([Text.Encoding]::UTF8.GetBytes("$appId&$timestampString"))
                  $signature = -join ($signature | % {“{0:X2}” -f $_})
                  $headerValue = "HMACSHA256 $appId;$timestampString;$signature"
                        
                  $headers.Add("Authorization", $headerValue)
                  $moduleState = Invoke-RestMethod "$modulesStateUrl" -Body $NotificationStateJson -Method Post -ContentType "application/json" -Headers $headers
                  Write-Output "Module install state: $moduleState"

                  $cycleCount = $cycleCount + 1 
            }
            while ($cycleCount -lt 5)
      }
      catch
      {
            $message = $_.Exception.Message
            Write-Output "Error: $message"
      }     