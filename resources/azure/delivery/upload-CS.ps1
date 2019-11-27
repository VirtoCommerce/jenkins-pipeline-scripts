param(
    $PlatformDir,
    $ModulesDir,
    $StorefrontDir,
    $ThemeDir,
    $WebAppName,
    $WebAppPublicName,
    $ResourceGroupName,
    $SubscriptionID,
    $BlobToken
)

# Upload Storefront Zip File to Azure

$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"
Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID

# Getting Backend Publish Profile
Write-Output "Getting publishing profile for $WebAppName app"
$BackendPublishProfile = [System.IO.Path]::GetTempFileName() + ".xml"
$xml = Get-AzureRmWebAppPublishingProfile -Name $WebAppName `
           -ResourceGroupName $ResourceGroupName `
           -OutputFile $BackendPublishProfile -Format WebDeploy -ErrorAction Stop

# Getting Frontend Publish Profile
Write-Output "Getting publishing profile for $WebAppPublicName app"
$FrontendPublishProfile = [System.IO.Path]::GetTempFileName() + ".xml"
$xml = Get-AzureRmWebAppPublishingProfile -Name $WebAppPublicName `
           -ResourceGroupName $ResourceGroupName `
           -OutputFile $FrontendPublishProfile -Format WebDeploy -ErrorAction Stop

# Stop Apps
if($PlatformDir -or $ModulesDir){
    Write-Host "Stop WebApp $WebAppName"
    Stop-AzureRmWebApp -ResourceGroupName $ResourceGroupName -Name $WebAppName | Select Name,State
}
if($StorefrontDir -or $ThemeDir){
    Write-Host "Stop WebApp $WebAppPublicName"
    Stop-AzureRmWebApp -ResourceGroupName $ResourceGroupName -Name $WebAppPublicName | Select Name,State
}
Start-Sleep -s 5

$msdeploy = "${env:MSDEPLOY_DIR}\msdeploy.exe"

$sourcewebapp_msdeployUrl = "https://${WebAppName}.scm.azurewebsites.net/msdeploy.axd?site=${WebAppName}"
# Upload Platform
if($PlatformDir){
    Write-Output "Upload Platform"
    & $msdeploy -verb:sync -dest:contentPath="D:\home\site\wwwroot\platform",computerName=$sourcewebapp_msdeployUrl,publishSettings=$BackendPublishProfile -source:contentPath=$PlatformDir
}
# Upload Modules
if($ModulesDir){
    Write-Output "Upload Modules"
    & $msdeploy -verb:sync -dest:contentPath="D:\home\site\wwwroot\modules",computerName=$sourcewebapp_msdeployUrl,publishSettings=$BackendPublishProfile -source:contentPath=$ModulesDir
}

$sourcewebapp_msdeployUrl = "https://${WebAppPublicName}.scm.azurewebsites.net/msdeploy.axd?site=${WebAppPublicName}"
# Upload Storefront
if($StorefrontDir){
    Write-Output "Upload Storefront"
    & $msdeploy -verb:sync -dest:contentPath="D:\home\site\wwwroot\",computerName=$sourcewebapp_msdeployUrl,publishSettings=$FrontendPublishProfile -source:contentPath=$StorefrontDir
}

# Upload Theme
#if($ThemeDir){
#    Write-Output "Upload Theme"
#    & $msdeploy -verb:sync -dest:contentPath="D:\home\site\wwwroot\wwwroot\theme",computerName=$sourcewebapp_msdeployUrl,publishSettings=$FrontendPublishProfile -source:contentPath=$ThemeDir
#}
$ContainerName = "cms"
$dirpath = "Themes"


Write-Output "AzCopy $elecPath"
$accountname = "qademovc"
$token = $BlobToken
& "${env:Utils}\AzCopy10\AzCopy" sync $ThemeDir "https://$($accountname).blob.core.windows.net/$ContainerName/$($dirpath)$token" --delete-destination=true #/DestKey:$accountKey /S

Write-Host "Start Backend $WebAppName"

Start-AzureRmWebApp -ResourceGroupName $ResourceGroupName -Name $WebAppName | Select Name,State

Write-Host "Start Frontend $WebAppPublicName"

Start-AzureRmWebApp -ResourceGroupName $ResourceGroupName -Name $WebAppPublicName | Select Name,State

Remove-Item $BackendPublishProfile
Remove-Item $FrontendPublishProfile