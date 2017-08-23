#
# This script will post message to twitter account
#

Param(  
  	[parameter(Mandatory=$true)]
        $apiurl
     )

     $modulesStateUrl = "$apiurl/api/platform/modules/state"
     $modulesInstallUrl = "$apiurl/api/platform/modules/autoinstall"
     $sampleDataStateUrl = "$apiurl/api/platform/sampledata/state"
     $sampleDataImportUrl = "$apiurl/api/platform/sampledata/autoinstall"

     <#
     # Initiate modules installation
     $moduleImportResult = Invoke-RestMethod $modulesInstallUrl -Method Post -ErrorAction Stop
     
     Write-Output "Module install result: $moduleImportResult"

     # Wait until sample data have been imported
     Write-Output "Waiting for modules install to be completed"
     do
     {
      try
      {
            Start-Sleep -s 5
            $moduleState = Invoke-RestMethod $modulesStateUrl -ErrorAction Stop
            Write-Output "Module install state: $moduleState"
      }
      catch
      {
            $message = $_.Exception.Message
            Write-Output "Error: $message"
      }
     }
     while ($moduleState -ne "completed")
     #>
          
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
      while ($sampleDataState -ne "completed" && $cycleCount -lt 24)