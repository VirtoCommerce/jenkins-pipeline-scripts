#$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"
$SubscriptionID = "demo.vc"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID

#$publishSettingsFile = "$env:VC_RES\azure\Configs\demovc.publishsettings"
#$SubscriptionName = "demo.vc"
$WebSiteName = "demovc-admin"
$WebSiteName2 = "demovc-public"
$SlotName = "staging"
$SqlServerName = 'demovc'
$hmacAppId = "${env:HMAC_APP_ID}"
$hmacSecret = "${env:HMAC_SECRET}"
$modulesInstallStateUrl = "https://$WebSiteName-$SlotName.azurewebsites.net/api/platform/modules/autoinstall/state"
$modulesInstallUrl = "https://$WebSiteName-$SlotName.azurewebsites.net/api/platform/modules/autoinstall"
$sampleDataStateUrl = "https://$WebSiteName-$SlotName.azurewebsites.net/api/platform/sampledata/state"
$sampleDataImportUrl = "https://$WebSiteName-$SlotName.azurewebsites.net/api/platform/sampledata/autoinstall"
$setSettingUrl = "https://$WebSiteName-$SlotName.azurewebsites.net/api/platform/settings"
$DestResourceGroupName = "demo.vc-prod"

$appId = "${env:HMAC_APP_ID}"
$secret = "${env:HMAC_SECRET}"

#Import-Module Azure
#Import-AzurePublishSettingsFile $publishSettingsFile
#Select-AzureSubscription $SubscriptionName

# Get database name from connection string

Write-Output "Getting connection string from web site $WebSiteName slot $SlotName"
$webSite = Get-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $WebSiteName -Slot $SlotName
$connectionString = ($webSite.SiteConfig.ConnectionStrings | Where Name -eq 'VirtoCommerce').ConnectionString
Write-Output "ConnectionString: $connectionString"
$sb = New-Object System.Data.Common.DbConnectionStringBuilder
$sb.set_ConnectionString($connectionstring)
$databaseName = $sb['Database']

# Stop web site

Write-Output "Stopping web site $WebSiteName slot $SlotName"
Stop-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $WebSiteName -Slot $SlotName

# Remove database

Write-Output "Removing database $databaseName"
Remove-AzureRmSqlDatabase -ResourceGroupName $DestResourceGroupName -ServerName $SqlServerName -DatabaseName $databaseName -Force

# Remove blob containers

$storageConnectionString = ($webSite.SiteConfig.ConnectionStrings | Where Name -eq 'CmsContentConnectionString').ConnectionString
Write-Output "Storage connection string: $storageConnectionString"

$storageSettings = @{}
foreach ($kvpString in $storageConnectionString.Split(';', [System.StringSplitOptions]::RemoveEmptyEntries))
{
    $kvp = $kvpString.Split('=', 2)
    $storageSettings[$kvp[0]] = $kvp[1]
}

$storageContext = New-AzureStorageContext -StorageAccountName $storageSettings['AccountName'] -StorageAccountKey $storageSettings['AccountKey']
$containers = $storageContext | Get-AzureStorageContainer | Select Name

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

$DestKuduDelPath = "https://$WebSiteName-$SlotName.scm.azurewebsites.net/api/vfs/site/wwwroot/modules/?recursive=true"

function Get-AzureRmWebAppPublishingCredentials($DestResourceGroupName, $WebSiteName, $slotName = $null){
	if ([string]::IsNullOrWhiteSpace($slotName)){
        $ResourceType = "Microsoft.Web/sites/config"
		$DestResourceName = "$WebSiteName/publishingcredentials"
	}
	else{
        $ResourceType = "Microsoft.Web/sites/slots/config"
		$DestResourceName = "$WebSiteName/$slotName/publishingcredentials"
	}
	$DestPublishingCredentials = Invoke-AzureRmResourceAction -ResourceGroupName $DestResourceGroupName -ResourceType $ResourceType -ResourceName $DestResourceName -Action list -ApiVersion 2015-08-01 -Force
    	return $DestPublishingCredentials
}

function Get-KuduApiAuthorisationHeaderValue($DestResourceGroupName, $WebSiteName){
    $DestPublishingCredentials = Get-AzureRmWebAppPublishingCredentials $DestResourceGroupName $WebSiteName $slotName
    return ("Basic {0}" -f [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(("{0}:{1}" -f $DestPublishingCredentials.Properties.PublishingUserName, $DestPublishingCredentials.Properties.PublishingPassword))))
}

$DestKuduApiAuthorisationToken = Get-KuduApiAuthorisationHeaderValue $DestResourceGroupName $WebSiteName

Write-Host "Deleting Files at $DestKuduDelPath"
Invoke-RestMethod $DestKuduDelPath -Headers @{"Authorization"=$DestKuduApiAuthorisationToken;"If-Match"="*"} -Method DELETE -ErrorAction Stop

# Create database

Start-Sleep -s 30
Write-Output "Create database $databaseName"
New-AzureRmSqlDatabase -ResourceGroupName $DestResourceGroupName -ServerName $SqlServerName -DatabaseName $databaseName

# Start web site

Write-Output "Starting web site $WebSiteName slot $SlotName"
Start-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $WebSiteName -Slot $SlotName

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

Function Create-Authorization([string] $appId, [string] $secret)
{
    $timestampString = [System.DateTime]::UtcNow.ToString("o", [System.Globalization.CultureInfo]::InvariantCulture)
    $hmacsha = New-Object System.Security.Cryptography.HMACSHA256
    $hmacsha.key = $secret | convert-fromhex
          
    $signature = $hmacsha.ComputeHash([Text.Encoding]::UTF8.GetBytes("$appId&$timestampString"))
    $signature = -join ($signature | ForEach-Object {"{0:X2}" -f $_})
    $headerValue = "HMACSHA256 $appId;$timestampString;$signature"
    return $headerValue
}

# Initiate modules and sample data installation

$headerValue = Create-Authorization $hmacAppId $hmacSecret
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
Write-Output "Restarting web site $WebSiteName slot $SlotName"
Restart-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $WebSiteName -Slot $SlotName
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

$elecPath = "Electronics"
$b2bPath = "B2B-store"
$clothPath = "Clothing"

Write-Output "Prepare Theme $elecPath"
Prepare-Theme -AssetUrl $electronicsAsset -Path $elecPath
Write-Output "Prepare Theme $b2bPath"
Prepare-Theme -AssetUrl $b2bAsset -Path $b2bPath
Write-Output "Prepare Theme $clothPath"
Prepare-Theme -AssetUrl $clothingAsset -Path $clothPath

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

Write-Output "Remove Temporary Files"
Remove-Item $elecPath -Recurse -Force
Remove-Item $b2bPath -Recurse -Force
Remove-Item $clothPath -Recurse -Force


Write-Output "Switching $WebSiteName slot"

Switch-AzureRmWebAppSlot -Name $WebSiteName -ResourceGroupName $DestResourceGroupName -SourceSlotName "staging" -DestinationSlotName "production"

Write-Output "Switching $WebSiteName2 slot"
 
Switch-AzureRmWebAppSlot -Name $WebSiteName2 -ResourceGroupName $DestResourceGroupName -SourceSlotName "staging" -DestinationSlotName "production"

Write-Output "Completed"