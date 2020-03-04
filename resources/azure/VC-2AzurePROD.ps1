param(
    [string] $StagingName, 
    [string] $StoreName,
    $AzureBlobName,
    $AzureBlobKey,
    $WebAppName,
    $ResourceGroupName,
    $SubscriptionID
)

$ErrorActionPreference = "Stop"

if($StagingName -eq "deploy")
{
    Copy-Item .\pages .\artifacts\Pages\vccom -Recurse -Force    
    Copy-Item .\theme .\artifacts\Themes\vccom\default -Recurse -Force

    Copy-Item .\pages .\artifacts\Pages\vccom-staging -Recurse -Force
    Copy-Item .\theme .\artifacts\Themes\vccom-staging\default -Recurse -Force
}

$SourceDir = "${env:WORKSPACE}\artifacts"

# Upload Zip File to Azure
$ConnectionString = "DefaultEndpointsProtocol=https;AccountName={0};AccountKey={1};EndpointSuffix=core.windows.net"

$ConnectionString = $ConnectionString -f $AzureBlobName, $AzureBlobKey

$BlobContext = New-AzureStorageContext -ConnectionString $ConnectionString

$SlotName = "staging"

$Now = Get-Date -format yyyyMMdd-HHmmss
$DestContainer = $StoreName + "-" + $Now

$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID

$DestWebAppName = $WebAppName
$DestResourceGroupName = $ResourceGroupName

Stop-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName -Slot $SlotName

New-AzureStorageContainer -Name $DestContainer -Context $BlobContext -Permission Container
Get-AzureStorageBlob -Container $StoreName -Context $BlobContext | Start-AzureStorageBlobCopy -DestContainer "$DestContainer" -Force
if($StagingName -eq "deploy")
{
    Get-AzureStorageBlob -Container "cms-content" -Context $BlobContext | Start-AzureStorageBlobCopy -DestContainer "cms-content-staging" -Force
}

Write-Host "Sync $StoreName"
$token = $env:AzureBlobToken
& "${env:Utils}\AzCopy10\AzCopy" sync $SourceDir https://$($AzureBlobName).blob.core.windows.net/$StoreName$token --recursive --exclude-pattern="*.htm;*.html;*.md;*.page"

Write-Output "Restarting web site $DestWebAppName slot $SlotName"
Start-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName -Slot $SlotName
Start-Sleep -s 66

if($StagingName -eq "prod")
{
    Write-Output "Switching $DestWebAppName slot"

    Switch-AzureRmWebAppSlot -Name $DestWebAppName -ResourceGroupName $DestResourceGroupName -SourceSlotName "staging" -DestinationSlotName "production"
    Stop-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName -Slot $SlotName
}
