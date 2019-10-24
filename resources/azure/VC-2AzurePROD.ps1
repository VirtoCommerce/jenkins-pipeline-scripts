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

#Copy-Item .\pages\ .\artifacts\Pages\vccom -Recurse -Force
#Copy-Item .\theme\ .\artifacts\Theme\vccom\default -Recurse -Force
Copy-Item .\pages\docs .\artifacts\docs -Recurse -Force
Compress-Archive -Path .\artifacts\* -CompressionLevel Fastest -DestinationPath .\artifacts\artifact.zip -Force


$Path2Zip = Get-Childitem -Recurse -Path "${env:WORKSPACE}\artifacts\" -File -Include artifact.zip

# Unzip Theme Zip File
$Path = "${env:WORKSPACE}\artifacts\" + [System.IO.Path]::GetFileNameWithoutExtension($Path2Zip)

Write-Host "path to zip:  $Path2Zip"

Expand-Archive -Path "$Path2Zip" -DestinationPath "$Path" -Force

# Upload Zip File to Azure
$ConnectionString = "DefaultEndpointsProtocol=https;AccountName={0};AccountKey={1};EndpointSuffix=core.windows.net"
if ($StagingName -eq "deploy"){
    $ConnectionString = $ConnectionString -f $AzureBlobName, $AzureBlobKey
}
$BlobContext = New-AzureStorageContext -ConnectionString $ConnectionString

$AzureBlobName = "$StoreName"
$SlotName = "staging"

$Now = Get-Date -format yyyyMMdd-HHmmss
$DestContainer = $AzureBlobName + "-" + $Now

$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID

$DestWebAppName = $WebAppName
$DestResourceGroupName = $ResourceGroupName

Write-Host "Stop $DestWebAppName"
Stop-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName -Slot $SlotName

New-AzureStorageContainer -Name $DestContainer -Context $BlobContext -Permission Container
Get-AzureStorageBlob -Container $StoreName -Context $BlobContext | Start-AzureStorageBlobCopy -DestContainer "$DestContainer" -Force

Write-Host "Remove from $StoreName"
Get-AzureStorageBlob -Blob ("Pages/vccom/docs*") -Container "cms-content" -Context $BlobContext  | ForEach-Object { Remove-AzureStorageBlob -Blob $_.Name -Container "cms-content" -Context $BlobContext } -ErrorAction Continue

Write-Host "Upload to $StoreName"
Get-ChildItem -File -Recurse $Path | ForEach-Object { Set-AzureStorageBlobContent -File $_.FullName -Blob ("Pages/vccom/" + (([System.Uri]("$Path/")).MakeRelativeUri([System.Uri]($_.FullName))).ToString()) -Container "cms-content" -Context $BlobContext }

Write-Host "Start $DestWebAppName"
Start-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName -Slot $SlotName
