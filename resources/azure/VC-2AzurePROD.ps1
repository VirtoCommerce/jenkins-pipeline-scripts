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

if ($StagingName -eq "deploy"){
    Copy-Item .\pages\docs .\artifacts -Recurse -Force
    $DestDirPath = "Pages/vccom/docs"
}
elseif ($StagingName -eq "dev-vc-scriban"){
    Copy-Item .\theme .\artifacts -Recurse -Force
    $DestDirPath = "Themes/vccom/default"
}

$SourceDir = "${env:WORKSPACE}\artifacts"

# Upload Zip File to Azure
$ConnectionString = "DefaultEndpointsProtocol=https;AccountName={0};AccountKey={1};EndpointSuffix=core.windows.net"
if ($StagingName -eq "deploy" -or $StagingName -eq "dev-vc-scriban"){
    $ConnectionString = $ConnectionString -f $AzureBlobName, $AzureBlobKey
}
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

Write-Host "Sync $StoreName"
$token = $env:AzureBlobToken
& "${env:Utils}\AzCopy10\AzCopy" sync $SourceDir https://$($AzureBlobName).blob.core.windows.net/$StoreName/$($DestDirPath)$token --delete-destination=true

Write-Output "Restarting web site $DestWebAppName slot $SlotName"
Start-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName -Slot $SlotName
Start-Sleep -s 66

Write-Output "Switching $DestWebAppName slot"
Switch-AzureRmWebAppSlot -Name $DestWebAppName -ResourceGroupName $DestResourceGroupName -SourceSlotName "staging" -DestinationSlotName "production"

Stop-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName -Slot $SlotName
