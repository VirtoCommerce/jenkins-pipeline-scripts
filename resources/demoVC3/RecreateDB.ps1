param (
    $SubscriptionID,
    $ResourceGroupName,
    $Username = "admin",
    $Password = "store",
    $WebAppPublicName = "prod-demovc3-store",
    $WebAppAdminName = "prod-demovc3-admin",
    $SqlServerName = 'prod-demovc3-srv'
)
#$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

function Get-AuthToken {
    param (
        $appName,
        $username,
        $password
    )
    $url = "https://$appName.azurewebsites.net/connect/token"
    $grant_type = "password"
    $content_type = "application/x-www-form-urlencoded"

    $body = @{username=$username; password=$password; grant_type=$grant_type}
    $response = Invoke-WebRequest -Uri $url -Method Post -ContentType $content_type -Body $body
    $responseContent = $response.Content | ConvertFrom-Json
    return "$($responseContent.token_type) $($responseContent.access_token)"
}

$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"


Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID

#$publishSettingsFile = "$env:VC_RES\azure\Configs\demovc.publishsettings"
#$SubscriptionName = "demo.vc"
$SlotName = "staging"
$modulesInstallStateUrl = "https://$WebAppAdminName-$SlotName.azurewebsites.net/api/platform/modules/autoinstall/state"
$modulesInstallUrl = "https://$WebAppAdminName-$SlotName.azurewebsites.net/api/platform/modules/autoinstall"
$sampleDataStateUrl = "https://$WebAppAdminName-$SlotName.azurewebsites.net/api/platform/sampledata/state"
$sampleDataImportUrl = "https://$WebAppAdminName-$SlotName.azurewebsites.net/api/platform/sampledata/autoinstall"
$setSettingUrl = "https://$WebAppAdminName-$SlotName.azurewebsites.net/api/platform/settings"
$DestResourceGroupName = $ResourceGroupName


#Import-Module Azure
#Import-AzurePublishSettingsFile $publishSettingsFile
#Select-AzureSubscription $SubscriptionName

# Get database name from connection string

Write-Output "Getting connection string from web site $WebAppAdminName slot $SlotName"
$webSite = Get-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $WebAppAdminName -Slot $SlotName
$connectionString = ($webSite.SiteConfig.ConnectionStrings | Where Name -eq 'VirtoCommerce').ConnectionString
Write-Output "ConnectionString: $connectionString"
$sb = New-Object System.Data.Common.DbConnectionStringBuilder
$sb.set_ConnectionString($connectionstring)
$databaseName = $sb['Initial Catalog']

# Stop web site

Write-Output "Stopping web site $WebAppAdminName slot $SlotName"
Stop-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $WebAppAdminName -Slot $SlotName

# Remove database

Write-Output "Removing database $databaseName"
Remove-AzureRmSqlDatabase -ResourceGroupName $DestResourceGroupName -ServerName $SqlServerName -DatabaseName $databaseName -Force

# Remove blob containers

#Write-Output "!!!dbg"
#foreach($setting in $webSite.SiteConfig.AppSettings){
#    Write-Output "name: $($setting.Name) value: $($setting.Value)"
#}
$storageConnectionString = $webSite.SiteConfig.AppSettings | Where-Object { $_.Name -eq "Assets:AzureBlobStorage:ConnectionString" }
Write-Output "Storage connection string: $($storageConnectionString.Value)"

$storageSettings = @{}
foreach ($kvpString in $storageConnectionString.Value.Split(';', [System.StringSplitOptions]::RemoveEmptyEntries))
{
    $kvp = $kvpString.Split('=', 2)
    $storageSettings[$kvp[0]] = $kvp[1]
}

$storageContext = New-AzureStorageContext -StorageAccountName $storageSettings['AccountName'] -StorageAccountKey $storageSettings['AccountKey']
#$containers = $storageContext | Get-AzureStorageContainer | Select Name

Write-Output "Removing storage container: $containerName"
Remove-AzureStorageContainer -Name cms -Context $storageContext -Force
#foreach($container in $containers)
#{
#    $containerName = $container.Name
#    Write-Output "Removing storage container: $containerName"
#    Remove-AzureStorageContainer -Name $containerName -Context $storageContext -Force
#    Write-Output "OK"
#}

# Remove Modules folder

$DestKuduDelPath = "https://$WebAppAdminName-$SlotName.scm.azurewebsites.net/api/vfs/site/wwwroot/modules/?recursive=true"

function Get-AzureRmWebAppPublishingCredentials($DestResourceGroupName, $WebAppAdminName, $slotName = $null){
	if ([string]::IsNullOrWhiteSpace($slotName)){
        $ResourceType = "Microsoft.Web/sites/config"
		$DestResourceName = "$WebAppAdminName/publishingcredentials"
	}
	else{
        $ResourceType = "Microsoft.Web/sites/slots/config"
		$DestResourceName = "$WebAppAdminName/$slotName/publishingcredentials"
	}
	$DestPublishingCredentials = Invoke-AzureRmResourceAction -ResourceGroupName $DestResourceGroupName -ResourceType $ResourceType -ResourceName $DestResourceName -Action list -ApiVersion 2015-08-01 -Force
    	return $DestPublishingCredentials
}

function Get-KuduApiAuthorisationHeaderValue($DestResourceGroupName, $WebAppAdminName){
    $DestPublishingCredentials = Get-AzureRmWebAppPublishingCredentials $DestResourceGroupName $WebAppAdminName $slotName
    return ("Basic {0}" -f [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(("{0}:{1}" -f $DestPublishingCredentials.Properties.PublishingUserName, $DestPublishingCredentials.Properties.PublishingPassword))))
}

$DestKuduApiAuthorisationToken = Get-KuduApiAuthorisationHeaderValue $DestResourceGroupName $WebAppAdminName

Write-Host "Deleting Files at $DestKuduDelPath"
Invoke-RestMethod $DestKuduDelPath -Headers @{"Authorization"=$DestKuduApiAuthorisationToken;"If-Match"="*"} -Method DELETE -ErrorAction Stop

# Create database

Start-Sleep -s 30
Write-Output "Create database $databaseName"
New-AzureRmSqlDatabase -ResourceGroupName $DestResourceGroupName -ServerName $SqlServerName -DatabaseName $databaseName -Edition 'Standard' -RequestedServiceObjectiveName 'S0'

# Start web site

Write-Output "Starting web site $WebAppAdminName slot $SlotName"
Start-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $WebAppAdminName -Slot $SlotName

# Wait until application is started

Write-Output "Waiting until application is started"
do
{
    try
    {
        Start-Sleep -s 5
        $modulesInstallState = Invoke-RestMethod $modulesInstallStateUrl -ErrorAction Stop
        Write-Output "Modules installation state: $modulesInstallState"
    }
    catch
    {
        $message = $_.Exception.Message
        Write-Output "Error: $message"
    }
}
while ($modulesInstallState -ne "Undefined")

# Function

Function convert-fromhex
{
    process
    {
        $_ -replace '^0x', '' -split "(?<=\G\w{2})(?=\w{2})" | ForEach-Object { [Convert]::ToByte( $_, 16 ) }
    }
}

# Initiate modules and sample data installation

$headerValue = Get-AuthToken($WebAppAdminName, $Username, $Password)
$headers = @{}
$headers.Add("Authorization", $headerValue)

# Modules installation

$modulesInstallResult = Invoke-RestMethod $modulesInstallUrl -Method Post -Headers $headers -ErrorAction Stop

# Wait until modules have been installed
Write-Output "Waiting for modules installation to be completed"
$cycleCount = 0
do
{
    try
    {
        Start-Sleep -s 5
        $modulesInstallState = Invoke-RestMethod $modulesInstallStateUrl -ErrorAction Stop
        Write-Output "Modules installation state: $modulesInstallState"
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
while ($modulesInstallState -ne "Completed" -and $cycleCount -lt 111)

# Restart web site
Write-Output "Restarting web site $WebAppAdminName slot $SlotName"
Restart-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $WebAppAdminName -Slot $SlotName
Start-Sleep -s 66

# Wait until application is started

Write-Output "Waiting until application is started"
do
{
    try
    {
        Start-Sleep -s 5
        $sampleDataState = Invoke-RestMethod $sampleDataStateUrl -ErrorAction Stop
        Write-Output "Sample data state: $sampleDataState"
    }
    catch
    {
        $message = $_.Exception.Message
        Write-Output "Error: $message"
    }
}
while ($sampleDataState -ne "Undefined")

# Sample data installation
    
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
while ($sampleDataState -ne "Completed" -and $cycleCount -lt 111)

#Set platform setting to prevent run setup wizard on first user log-in
$setupStepSetting = "[{`"name`":`"VirtoCommerce.SetupStep`",`"value`":`"workspace`"}]";
$headers.Add("Content-Type", "application/json;charset=UTF-8");
Invoke-RestMethod $setSettingUrl -Method Post -Headers $headers -Body $setupStepSetting  -ErrorAction Stop

# Swap web site slots
Start-Sleep -s 11

# Wait until modules have been installed
Write-Output "Check for modules installation to be completed"
$cycleCount = 0
do
{
    try
    {
        Start-Sleep -s 5
        $modulesInstallState = Invoke-RestMethod $modulesInstallStateUrl -ErrorAction Stop
        Write-Output "Modules installation state: $modulesInstallState"
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
while ($modulesInstallState -ne "Completed" -and $cycleCount -lt 111)

#Deploy themes
Function Get-GithubLatestReleaseAssetUrl{
    param(
    $OrgName = "VirtoCommerce",
    $RepoName
    )
    $url = "https://api.github.com/repos/$OrgName/$RepoName/releases/latest"
    $release = Invoke-RestMethod -Method Get -Uri $url
    return $release.assets[0].url
}
Function Prepare-Theme{
    param(
    $AssetUrl,
    $Path
    )
    $githubHeaders = @{"Accept"= "application/octet-stream"}
    $zipPath = "$($Path).zip"
    Invoke-WebRequest -Uri $AssetUrl -Headers $githubHeaders -OutFile $zipPath
    Expand-Archive -Path $zipPath -DestinationPath $Path -Force
    Remove-Item $zipPath -Force
}
Function Remove-Blobs{
    param(
    $AccountName,
    $AccountKey,
    $DirPath,
    $ContainerName
    )
    $ctx = New-AzureStorageContext -StorageAccountName $AccountName -StorageAccountKey $AccountKey
    $MaxReturn = 1000
    $Total = 0
    $Token = $Null
    do
    {
        $Blobs = Get-AzureStorageBlob -Container $ContainerName -prefix $DirPath -MaxCount $MaxReturn  -ContinuationToken $Token  -Context $ctx
        $Total += $Blobs.Count

        $Blobs | Remove-AzureStorageBlob

        if($Blobs.Length -le 0) { Break;}
        $Token = $Blobs[$Blobs.Count -1].ContinuationToken;
    }
    While ($Token -ne $Null)
}

$accountname = $storageSettings['AccountName']
$accountKey = $storageSettings['AccountKey']

#Get the latest releases
Write-Output "Get latest release vc-theme-default"
$electronicsAsset = Get-GithubLatestReleaseAssetUrl -RepoName "vc-theme-default"
Write-Output "Get latest release vc-theme-b2b"
$b2bAsset = Get-GithubLatestReleaseAssetUrl -RepoName "vc-theme-b2b"
Write-Output "Get latest release vc-theme-material"
$clothingAsset = Get-GithubLatestReleaseAssetUrl -RepoName "vc-theme-material"
Write-Output "Get latest release vc-procurement-portal-theme"
$dentalAsset = Get-GithubLatestReleaseAssetUrl -RepoName "vc-procurement-portal-theme"

$elecPath = "Electronics"
$b2bPath = "B2B-store"
$clothPath = "Clothing"
$dentalPath = "dental"

Write-Output "Prepare Theme $elecPath"
Prepare-Theme -AssetUrl $electronicsAsset -Path $elecPath
Write-Output "Prepare Theme $b2bPath"
Prepare-Theme -AssetUrl $b2bAsset -Path $b2bPath
Write-Output "Prepare Theme $clothPath"
Prepare-Theme -AssetUrl $clothingAsset -Path $clothPath
Write-Output "Prepare Theme $dentalPath"
Prepare-Theme -AssetUrl $dentalAsset -Path $dentalPath

#Remove blobs

$ContainerName = "cms"
$dirpath = "Themes/"

Write-Output "Remove Blob $ContainerName"
Remove-Blobs -AccountName $accountname -AccountKey $accountKey -DirPath $dirpath -ContainerName $ContainerName

Write-Output "AzCopy $elecPath"
& "${env:Utils}\AzCopy\AzCopy" $elecPath https://$($accountname).blob.core.windows.net/$ContainerName/$dirpath$elecPath /DestKey:$accountKey /S
Write-Output "AzCopy $b2bPath"
& "${env:Utils}\AzCopy\AzCopy" $b2bPath https://$($accountname).blob.core.windows.net/$ContainerName/$dirpath$b2bPath /DestKey:$accountKey /S
Write-Output "AzCopy $clothPath"
& "${env:Utils}\AzCopy\AzCopy" $clothPath https://$($accountname).blob.core.windows.net/$ContainerName/$dirpath$clothPath /DestKey:$accountKey /S
Write-Output "AzCopy $dentalPath"
& "${env:Utils}\AzCopy\AzCopy" $dentalPath https://$($accountname).blob.core.windows.net/$ContainerName/$dirpath$dentalPath/default /DestKey:$accountKey /S

Write-Output "Remove Temporary Files"
Remove-Item $elecPath -Recurse -Force
Remove-Item $b2bPath -Recurse -Force
Remove-Item $clothPath -Recurse -Force
Remove-Item $dentalPath -Recurse -Force

Write-Output "Switching $WebAppAdminName slot"

Switch-AzureRmWebAppSlot -Name $WebAppAdminName -ResourceGroupName $DestResourceGroupName -SourceSlotName "staging" -DestinationSlotName "production"

Write-Output "Switching $WebAppPublicName slot"
 
Switch-AzureRmWebAppSlot -Name $WebAppPublicName -ResourceGroupName $DestResourceGroupName -SourceSlotName "staging" -DestinationSlotName "production"

Write-Output "Completed"