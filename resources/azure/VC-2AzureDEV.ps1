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
    Copy-Item .\pages .\artifacts\Pages\vccom -Recurse -Force
    Copy-Item .\theme .\artifacts\Themes\vccom\default -Recurse -Force
    $DestDirPath = "$StoreName"
}
elseif ($StagingName -eq "dev-vc-new-design"){
    Copy-Item .\theme .\artifacts -Recurse -Force
    $DestDirPath = "Themes/vccom/default"
}

$SourceDir = "${env:WORKSPACE}\artifacts"

# Upload Zip File to Azure
$ConnectionString = "DefaultEndpointsProtocol=https;AccountName={0};AccountKey={1};EndpointSuffix=core.windows.net"
if ($StagingName -eq "deploy"){
    $ConnectionString = $ConnectionString -f $AzureBlobName, $AzureBlobKey
}
$BlobContext = New-AzureStorageContext -ConnectionString $ConnectionString

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

Write-Host "Stop $DestWebAppName"
Stop-AzureRmWebApp -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName

New-AzureStorageContainer -Name $DestContainer -Context $BlobContext -Permission Container
Get-AzureStorageBlob -Container $StoreName -Context $BlobContext | Start-AzureStorageBlobCopy -DestContainer "$DestContainer" -Force

Write-Host "Sync $StoreName"
$token = $env:AzureBlobToken
#& "${env:Utils}\AzCopy10\AzCopy" sync $SourceDir https://$($AzureBlobName).blob.core.windows.net/$StoreName/$($DestDirPath)$token --delete-destination=true
& "${env:Utils}\AzCopy10\AzCopy" sync $SourceDir https://$($AzureBlobName).blob.core.windows.net/$StoreName/$($DestDirPath)$token --recursive --exclude="*.page" --delete-destination=true

Write-Host "Start $DestWebAppName"
Start-AzureRmWebApp -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName
