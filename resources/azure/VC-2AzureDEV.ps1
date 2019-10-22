param(
    [string] $StagingName, 
    [string] $StoreName,
    $AzureBlobName,
    $AzureBlobKey
)

$ErrorActionPreference = "Stop"

Copy-Item .\pages\ .\artifacts\Pages\vccom -Recurse -Force
Copy-Item .\theme\ .\artifacts\Theme\vccom\default -Recurse -Force
Compress-Archive -Path .\artifacts\* -CompressionLevel Fastest -DestinationPath .\artifacts\artifact.zip -Force

# Get Theme Zip File

$Path2Zip = Get-Childitem -Recurse -Path "${env:WORKSPACE}\artifacts\" -File -Include *.zip

# Unzip Theme Zip File

$Path = "${env:WORKSPACE}\artifacts\" + [System.IO.Path]::GetFileNameWithoutExtension($Path2Zip)
Add-Type -AssemblyName System.IO.Compression.FileSystem
#[System.IO.Compression.ZipFile]::ExtractToDirectory($Path2Zip, $Path)

# Upload Theme Zip File to Azure

$ConnectionString = "DefaultEndpointsProtocol=https;AccountName={0};AccountKey={1};EndpointSuffix=core.windows.net"
if ($StagingName -eq "deploy"){
    $ConnectionString = $ConnectionString -f $AzureBlobName, $AzureBlobKey
}
$BlobContext = New-AzureStorageContext -ConnectionString $ConnectionString

Write-Host "$StagingName"
Write-Host "$StoreName"
Write-Host "$AzureBlobName"

$Now = Get-Date -format yyyyMMdd-HHmmss
$DestContainer = "cms-content_" + $Now
Get-AzureStorageBlob -Blob ("$AzureBlobName*") -Container $StoreName -Context $BlobContext | Start-AzureStorageBlobCopy -DestContainer $DestContainer

Write-Host "Remove from $StoreName"
Get-AzureStorageBlob -Blob ("$AzureBlobName*") -Container $StoreName -Context $BlobContext  | ForEach-Object { Remove-AzureStorageBlob -Blob $_.Name -Container "cms-content" -Context $BlobContext } -ErrorAction Continue

Write-Host "Upload to $StoreName"
Get-ChildItem -File -Recurse $Path | ForEach-Object { Set-AzureStorageBlobContent -File $_.FullName -Blob ("$AzureBlobName/" + (([System.Uri]("$Path/")).MakeRelativeUri([System.Uri]($_.FullName))).ToString()) -Container "cms-content" -Context $BlobContext }
