param([string] $StagingName, [string] $StoreName)

# Get Theme Zip File

$Path2Zip = Get-Childitem -Recurse -Path "${env:WORKSPACE}\artifacts\" -File -Include *.zip

# Unzip Theme Zip File
$Path = "${env:WORKSPACE}\artifacts\" + [System.IO.Path]::GetFileNameWithoutExtension($Path2Zip)
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::ExtractToDirectory($Path2Zip, $Path)

# Upload Theme Zip File to Azure

$ConnectionString = ""
if ($StagingName -eq "dev"){
    $ConnectionString = "${env:AzureBlobConnectionStringDev}"
}
if ($StagingName -eq "qa"){
    $ConnectionString = "${env:AzureBlobConnectionStringQA}"
}
$BlobContext = New-AzureStorageContext -ConnectionString $ConnectionString

$AzureBlobName = "Themes/" + $StoreName

Write-Host "Remove from " + $StoreName
Remove-AzureStorageBlob -Blob $AzureBlobName -Container "cms" -Context $BlobContext

Write-Host "Upload to " + $StoreName
Get-ChildItem -File -Recurse $Path | ForEach-Object { Set-AzureStorageBlobContent -File $_.FullName -Blob $AzureBlobName + "/" + (([System.Uri]($Path)).MakeRelativeUri([System.Uri]($_.FullName))).ToString() -Container "cms" -Context $BlobContext }