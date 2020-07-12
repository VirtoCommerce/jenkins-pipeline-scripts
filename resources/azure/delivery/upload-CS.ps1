param(
    $PlatformDir,
    $ModulesDir,
    $StorefrontDir,
    $ThemeDir,
    [Array] $WebAppName,
    $WebAppPublicName,
    $ResourceGroupName,
    $SubscriptionID,
    $StorageAccount = "qademovc",
    $BlobContainerName = "cms",
    $ThemeBlobPath = "Themes/"
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
$BackendPublishProfile = @()    # Init empty for easy add with +=
foreach ($App in $WebAppName)
{
    Write-Host "Getting publishing profile for $App app"
    $BackendPublishProfile += [System.IO.Path]::GetTempFileName() +".xml"
    $xml = Get-AzureRmWebAppPublishingProfile -Name $App `
               -ResourceGroupName $ResourceGroupName `
               -OutputFile $BackendPublishProfile[$WebAppName.IndexOf($App)] -Format WebDeploy -ErrorAction Stop
}

# Getting Frontend Publish Profile
Write-Host "Getting publishing profile for $WebAppPublicName app"
$FrontendPublishProfile = [System.IO.Path]::GetTempFileName() + ".xml"
$xml = Get-AzureRmWebAppPublishingProfile -Name $WebAppPublicName `
           -ResourceGroupName $ResourceGroupName `
           -OutputFile $FrontendPublishProfile -Format WebDeploy -ErrorAction Stop

# Stop Apps
foreach ($App in $WebAppName)
{
    if($PlatformDir -or $ModulesDir){
        Write-Host "Stop WebApp $App"
        Stop-AzureRmWebApp -ResourceGroupName $ResourceGroupName -Name $App | Select-Object Name,State
    }
}

if($StorefrontDir -or $ThemeDir){
    Write-Host "Stop WebApp $WebAppPublicName"
    Stop-AzureRmWebApp -ResourceGroupName $ResourceGroupName -Name $WebAppPublicName | Select-Object Name,State
}
Start-Sleep -s 30

$msdeploy = "${env:MSDEPLOY_DIR}\msdeploy.exe"

foreach ($App in $WebAppName)
{
    $sourcewebapp_msdeployUrl = "https://${App}.scm.azurewebsites.net/msdeploy.axd?site=${App}"

    # Upload Platform
    if($PlatformDir){
        Write-Host "Upload Platform"
        $BackendPublishProfileStr = $BackendPublishProfile[$WebAppName.IndexOf($App)]
        Write-Host $BackendPublishProfile[$WebAppName.IndexOf($App)]
        Write-Host "BackendPublishProfileStr: $BackendPublishProfileStr"
        & $msdeploy -verb:sync -dest:contentPath="D:\home\site\wwwroot\platform",computerName=$sourcewebapp_msdeployUrl,publishSettings=$BackendPublishProfileStr -source:contentPath=$PlatformDir -verbose
        if($LASTEXITCODE -ne 0)
        {
            exit 1
        }
    }
    # Upload Modules
    if($ModulesDir){
        Write-Host "Upload Modules"
        & $msdeploy -verb:sync -dest:contentPath="D:\home\site\wwwroot\modules",computerName=$sourcewebapp_msdeployUrl,publishSettings=$BackendPublishProfileStr -source:contentPath=$ModulesDir
        if($LASTEXITCODE -ne 0)
        {
            exit 1
        }
    }
}

$sourcewebapp_msdeployUrl = "https://${WebAppPublicName}.scm.azurewebsites.net/msdeploy.axd?site=${WebAppPublicName}"
# Upload Storefront
if($StorefrontDir -and (Test-Path $ThemeDir)){
    Write-Host "Upload Storefront"
    & $msdeploy -verb:sync -dest:contentPath="D:\home\site\wwwroot\",computerName=$sourcewebapp_msdeployUrl,publishSettings=$FrontendPublishProfile -source:contentPath=$StorefrontDir
    if($LASTEXITCODE -ne 0)
    {
        exit 1
    }
}

# Upload Theme
if($ThemeDir -and (Test-Path $ThemeDir)){
    Write-Host "Upload Theme"
    #& $msdeploy -verb:sync -dest:contentPath="D:\home\site\wwwroot\wwwroot\theme",computerName=$sourcewebapp_msdeployUrl,publishSettings=$FrontendPublishProfile -source:contentPath=$ThemeDir
    
    Write-Host "AzCopy $StorageAccount"
    $token = $env:AzureBlobToken
    & "${env:Utils}\AzCopy10\AzCopy" sync $ThemeDir https://$($StorageAccount).blob.core.windows.net/$BlobContainerName/$($ThemeBlobPath)$token --delete-destination=true #/DestKey:$accountKey /S
    if($LASTEXITCODE -ne 0)
    {
        exit 1
    }
}

Write-Host "Start Backend $WebAppName"
foreach ($App in $WebAppName)
{
    Start-AzureRmWebApp -ResourceGroupName $ResourceGroupName -Name $App | Select-Object Name,State
}

Write-Host "Start Frontend $WebAppPublicName"
Start-AzureRmWebApp -ResourceGroupName $ResourceGroupName -Name $WebAppPublicName | Select-Object Name,State

#Remove-Item $BackendPublishProfile
Remove-Item $FrontendPublishProfile
