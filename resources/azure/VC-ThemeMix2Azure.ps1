param(
    [string] $StagingName,
    [string] $StoreName
    )


$AzureBlobName = $env:AzureBlobName
$AzureBlobKey = $env:AzureBlobKey

$ErrorActionPreference = "Continue"

# Get Theme Zip File

$Path2Zip = Get-Childitem -Path "${env:WORKSPACE}\artifacts\" -File -Include *.zip -Recurse

# Unzip Theme Zip File

$Path = "${env:WORKSPACE}\artifacts\" + [System.IO.Path]::GetFileNameWithoutExtension($Path2Zip)
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::ExtractToDirectory($Path2Zip, $Path)
#Expand-Archive -Path $Path2Zip -DestinationPath $Path

# Upload Theme Zip File to Azure

$ConnectionString = "DefaultEndpointsProtocol=https;AccountName={0};AccountKey={1};EndpointSuffix=core.windows.net"
$ConnectionString = $ConnectionString -f ${AzureBlobName}, ${AzureBlobKey}
$BlobContext = New-AzureStorageContext -ConnectionString $ConnectionString

$AzureBlobName = "Themes/$StoreName/default"

if($StoreName -eq "odt")
{
    $accountname = $env:AzureBlobName
    $token = $env:AzureBlobKey
    $dirpath = "Themes/$StoreName"
    & "${env:Utils}\AzCopy10\AzCopy" sync $Path https://$($accountname).blob.core.windows.net/cms/$($dirpath)$token --delete-destination=true
} 
else
{
    Write-Host "Remove from $StoreName"
    Get-AzureStorageBlob -Blob ("$AzureBlobName*") -Container "cms" -Context $BlobContext  | ForEach-Object { Remove-AzureStorageBlob -Blob $_.Name -Container "cms" -Context $BlobContext } -ErrorAction Continue

    Write-Host "Upload to $StoreName"
    Get-ChildItem -Path $Path -File -Recurse | ForEach-Object { Set-AzureStorageBlobContent -File $_.FullName -Blob ("$AzureBlobName/" + (([System.Uri]("$Path/")).MakeRelativeUri([System.Uri]($_.FullName))).ToString()) -Container "cms" -Context $BlobContext }
}