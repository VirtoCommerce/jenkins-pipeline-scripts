Param(  
  	[parameter(Mandatory=$true)]
        $apiurl
     )

     $sampleDataStateUrl = "$apiurl/api/platform/sampledata/state"
     $sampleDataImportUrl = "$apiurl/api/platform/sampledata/autoinstall"    
         
      # Initiate sample data installation
      $sampleDataImportResult = Invoke-RestMethod $sampleDataImportUrl -Method Post -ErrorAction Stop
      Write-Output "Sample data import result: $sampleDataImportResult"

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
            Write-Output "Error: $message"
      }
      }
      while ($sampleDataState -ne "completed" -and $cycleCount -lt 24)