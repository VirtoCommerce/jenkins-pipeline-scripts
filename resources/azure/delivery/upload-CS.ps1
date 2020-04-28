param(
    $PlatformDir,
    $ModulesDir,
    $StorefrontDir,
    $ThemeDir,
    $WebAppName,
    $WebAppPublicName,
    $ResourceGroupName,
    $SubscriptionID,
    $StorageAccount = "qademovc",
    $BlobContainerName = "cms",
    $ThemeBlobPath = "Themes"
)

# Upload Storefront Zip File to Azure
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

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
Start-Sleep -s 30

$msdeploy = "${env:MSDEPLOY_DIR}\msdeploy.exe"

$sourcewebapp_msdeployUrl = "https://${WebAppName}.scm.azurewebsites.net/msdeploy.axd?site=${WebAppName}"
# Upload Platform
if($PlatformDir){
    Write-Output "Upload Platform"
    & $msdeploy -verb:sync -dest:contentPath="D:\home\site\wwwroot\platform",computerName=$sourcewebapp_msdeployUrl,publishSettings=$BackendPublishProfile -source:contentPath=$PlatformDir -verbose
    if($LASTEXITCODE -ne 0)
    {
        exit 1
    }
}
# Upload Modules
if($ModulesDir){
    Write-Output "Upload Modules"
    & $msdeploy -verb:sync -dest:contentPath="D:\home\site\wwwroot\modules",computerName=$sourcewebapp_msdeployUrl,publishSettings=$BackendPublishProfile -source:contentPath=$ModulesDir
    if($LASTEXITCODE -ne 0)
    {
        exit 1
    }
}

$sourcewebapp_msdeployUrl = "https://${WebAppPublicName}.scm.azurewebsites.net/msdeploy.axd?site=${WebAppPublicName}"
# Upload Storefront
if($StorefrontDir -and (Test-Path $ThemeDir)){
    Write-Output "Upload Storefront"
    & $msdeploy -verb:sync -dest:contentPath="D:\home\site\wwwroot\",computerName=$sourcewebapp_msdeployUrl,publishSettings=$FrontendPublishProfile -source:contentPath=$StorefrontDir
    if($LASTEXITCODE -ne 0)
    {
        exit 1
    }
}

# Upload Theme
if($ThemeDir -and (Test-Path $ThemeDir)){
    Write-Output "Upload Theme"
    #& $msdeploy -verb:sync -dest:contentPath="D:\home\site\wwwroot\wwwroot\theme",computerName=$sourcewebapp_msdeployUrl,publishSettings=$FrontendPublishProfile -source:contentPath=$ThemeDir
    
    Write-Output "AzCopy $StorageAccount"
    $token = $env:AzureBlobToken
    & "${env:Utils}\AzCopy10\AzCopy" sync $ThemeDir https://$($StorageAccount).blob.core.windows.net/$BlobContainerName/$($ThemeBlobPath)$token --delete-destination=true --include="*" #/DestKey:$accountKey /S
    if($LASTEXITCODE -ne 0)
    {
        exit 1
    }
}



Write-Host "Start Backend $WebAppName"

Start-AzureRmWebApp -ResourceGroupName $ResourceGroupName -Name $WebAppName | Select Name,State

Write-Host "Start Frontend $WebAppPublicName"

Start-AzureRmWebApp -ResourceGroupName $ResourceGroupName -Name $WebAppPublicName | Select Name,State

Remove-Item $BackendPublishProfile
Remove-Item $FrontendPublishProfile