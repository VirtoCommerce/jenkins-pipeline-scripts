param(
    $ZipFile,
    $WebAppName,
    $ResourceGroupName,
    $SubscriptionID,
    $DestContentPath = ""
    )

$ErrorActionPreference = 'Stop'

# Upload Platform Zip File to Azure

$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID


$DestResourceGroupName = $ResourceGroupName
$DestWebAppName = $WebAppName

Write-Host "Stop WebApp $DestWebAppName"

Stop-AzureRmWebApp -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName

Start-Sleep -s 50

# Getting Publish Profile
Write-Output "Getting publishing profile for $DestWebAppName app"
$tmpPublishProfile = [System.IO.Path]::GetTempFileName() + ".xml"
$xml = Get-AzureRmWebAppPublishingProfile -Name $DestWebAppName `
           -ResourceGroupName $DestResourceGroupName `
           -OutputFile $tmpPublishProfile -Format WebDeploy -ErrorAction Stop



$contentPath = $DestContentPath
$msdeploy = "${env:MSDEPLOY_DIR}\msdeploy.exe"
$sourcewebapp_msdeployUrl = "https://${DestWebAppName}.scm.azurewebsites.net/msdeploy.axd?site=${DestWebAppName}"
& $msdeploy -verb:sync -dest:contentPath="D:\home\site\wwwroot\$contentPath",computerName=$sourcewebapp_msdeployUrl,publishSettings=$tmpPublishProfile -source:package=$ZipFile -skip:Directory="App_Data\\Lucene"



Start-Sleep -s 5

Write-Host "Start WebApp $DestWebAppName"

Start-AzureRmWebApp -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName

Remove-Item $tmpPublishProfile -Force