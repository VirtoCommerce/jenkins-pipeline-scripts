param([string] $StagingName, [string] $StoreName)

$StagingName
$StoreName

# Get Theme Zip File

$Path2Zip = Get-Childitem -Recurse -Path "${env:WORKSPACE}\artifacts\" -File -Include *.zip

$Path2Zip

# Unzip Theme Zip File
$Path = "${env:WORKSPACE}\artifacts\" + [System.IO.Path]::GetFileNameWithoutExtension($Path2Zip)
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::ExtractToDirectory($Path2Zip, $Path)

$Path

# Upload Theme Zip File to Azure

$ConnectionString = "DefaultEndpointsProtocol=https;AccountName={0};AccountKey={1};EndpointSuffix=core.windows.net"
if ($StagingName -eq "dev"){
    $ConnectionString = $ConnectionString -f ${env:AzureBlobNameDev}, ${env:AzureBlobKeyDev}
}
if ($StagingName -eq "qa"){
    $ConnectionString = $ConnectionString -f ${env:AzureBlobNameQA}, ${env:AzureBlobKeyQA}
}
$ConnectionString
$BlobContext = New-AzureStorageContext -ConnectionString $ConnectionString

$AzureBlobName = "Themes/" + $StoreName

Write-Host "Remove from " + $StoreName
Remove-AzureStorageBlob -Blob $AzureBlobName -Container "cms" -Context $BlobContext

Write-Host "Upload to " + $StoreName
Get-ChildItem -File -Recurse $Path | ForEach-Object { Set-AzureStorageBlobContent -File $_.FullName -Blob $AzureBlobName + "/" + (([System.Uri]($Path)).MakeRelativeUri([System.Uri]($_.FullName))).ToString() -Container "cms" -Context $BlobContext }