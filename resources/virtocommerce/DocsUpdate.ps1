$ErrorActionPreference = 'Continue'

$DestContentPath = ""
$ZipFile = "${env:ArtifactPath}"

$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"
$SubscriptionID = "${env:AzureSubscriptionIDProd}"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID

$DestResourceGroupName = "${env:AzureResourceGroupNameProd}"
$DestWebAppName = "${env:AzureWebAppNameProd}"

Write-Host "Stop WebApp $DestWebAppName"
# Stop-AzureRmWebApp -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName

Write-Host "ZipFile: $ZipFile"
Write-Host "ApplicationID: $ApplicationID"
Write-Host "TenantID: $TenantID"
Write-Host "SubscriptionID: $SubscriptionID"
Write-Host "DestResourceGroupName: $DestResourceGroupName"
Write-Host "DestWebAppName: $DestWebAppName"

Start-Sleep -s 35

# Getting Publish Profile
Write-Output "Getting publishing profile for $DestWebAppName app"
$tmpPublishProfile = [System.IO.Path]::GetTempFileName() + ".xml"
$xml = Get-AzureRmWebAppPublishingProfile -Name $DestWebAppName `
           -ResourceGroupName $DestResourceGroupName `
           -OutputFile $tmpPublishProfile -Format WebDeploy -ErrorAction Continue

$contentPath = $DestContentPath
$msdeploy = "${env:MSDEPLOY_DIR}\msdeploy.exe"
$sourcewebapp_msdeployUrl = "https://${DestWebAppName}.scm.azurewebsites.net/msdeploy.axd?site=${DestWebAppName}"
& $msdeploy -verb:sync -dest:contentPath="D:\home\site\wwwdocs\latest\$contentPath",computerName=$sourcewebapp_msdeployUrl,publishSettings=$tmpPublishProfile -source:package=$ZipFile
if($LASTEXITCODE -ne 0)
{
    exit 1
}

Start-Sleep -s 5

Write-Host "Start WebApp $DestWebAppName"
# Start-AzureRmWebApp -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName

Remove-Item $tmpPublishProfile -Force
