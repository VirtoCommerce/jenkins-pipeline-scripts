Param(  
  	[parameter(Mandatory=$true)]
        $apiurl,
        $hmacAppId,
        $hmacSecret
     )

     . $PSScriptRoot\utilities.ps1

     if ([string]::IsNullOrWhiteSpace($hmacAppId))
     {
           $hmacAppId = "${env:HMAC_APP_ID}"
     }

     if ([string]::IsNullOrWhiteSpace($hmacSecret))
     {
           $hmacSecret = "${env:HMAC_SECRET}"
     }      

     # Initialize paths used by the script
     $sampleDataStateUrl = "$apiurl/api/platform/sampledata/state"
     $sampleDataImportUrl = "$apiurl/api/platform/sampledata/autoinstall"    
         
      # Initiate sample data installation
      $headerValue = Create-Authorization $hmacAppId $hmacSecret
      $headers = @{}
      $headers.Add("Authorization", $headerValue)      
      $sampleDataImportResult = Invoke-RestMethod $sampleDataImportUrl -Method Post -Headers $headers -ErrorAction Stop

      # Wait until sample data have been imported
      Write-Output "Waiting for sample data import to be completed"
      $cycleCount = 0
      do
      {
            try
            {
                  Start-Sleep -s 5
                  $sampleDataState = Invoke-RestMethod $sampleDataStateUrl -ErrorAction Stop
                  Write-Output "Sample data state: $sampleDataState"
                  $cycleCount = $cycleCount + 1 
            }
            catch
            {
                  $message = $_.Exception.Message
                  $cycleCount = $cycleCount + 1 
                  Write-Output "Error: $message"
                  exit 1
            }
      }
      while ($sampleDataState -ne "completed" -and $cycleCount -lt 24)      