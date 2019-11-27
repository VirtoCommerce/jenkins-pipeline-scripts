param(
    [string] $StagingName, 
    [string] $StoreName,
    $AzureBlobName,
    $AzureBlobKey,
    $WebAppName,
    $ResourceGroupName,
    $SubscriptionID,
    $SourceDir,
    $TokenSas
)

$ErrorActionPreference = "Stop"

if ($StagingName -eq "deploy"){
    Copy-Item .\pages\docs .\artifacts -Recurse -Force
    $DestDirPath = "Pages/vccom/docs"
}
elseif ($StagingName -eq "dev-vc-new-design"){
    Copy-Item .\theme .\artifacts -Recurse -Force
    $DestDirPath = "Themes/vccom/default"
}

$Path2Zip = Get-Childitem -Recurse -Path "${env:WORKSPACE}\artifacts\" -File -Include artifact.zip

# Unzip Theme Zip File
$Path = "${env:WORKSPACE}\artifacts\" + [System.IO.Path]::GetFileNameWithoutExtension($Path2Zip)

Expand-Archive -Path "$Path2Zip" -DestinationPath "$Path" -Force

# Upload Zip File to Azure
$ConnectionString = "DefaultEndpointsProtocol=https;AccountName={0};AccountKey={1};EndpointSuffix=core.windows.net"
if ($StagingName -eq "deploy" -or $StagingName -eq "dev-vc-new-design"){
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
& "${env:Utils}\AzCopy10\AzCopy" sync $SourceDir https://$($AzureBlobName).blob.core.windows.net/$StoreName/$($DestDirPath)$TokenSas --delete-destination=true

Write-Host "Start $DestWebAppName"
Start-AzureRmWebApp -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName
