Param(
    [parameter(Mandatory = $true)]
    $AppName,
    $ApiUrl,
    $PlatformContainer
)
. $PSScriptRoot\Utilities.ps1

if ([string]::IsNullOrWhiteSpace($hmacAppId)) {
    $hmacAppId = "${env:HMAC_APP_ID}"
}

if ([string]::IsNullOrWhiteSpace($hmacSecret)) {
    $hmacSecret = "${env:HMAC_SECRET}"
}  

$headerValue = Create-Authorization $hmacAppId $hmacSecret
$headers = @{}
$headers.Add("Authorization", $headerValue)

$restartUrl = "$ApiUrl/api/platform/modules/restart"

# LOCAL PATH TO STORE ARCHIVE WITH WEBSITE CODE
$Archive = [System.IO.Path]::GetTempFileName() + ".zip"

# Credentials and URL for SOURCE website. Will download code from this site.
# You can get credentials from the file obtained by pressing "Get publish profile" at WebApp overview page
# Add "`" at the begging of userName from .PublishSettings file
$sourcewebapp_username = $env:AZURE_CRED_USR
# userPWD in .PublishSettings file
$sourcewebapp_password = $env:AZURE_CRED_PSW
# Convert to Base64 string for authing
$sourcewebapp_base64AuthInfo = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(("{0}:{1}" -f $sourcewebapp_username, $sourcewebapp_password)))
# Url for source
$sourcewebapp_apiUrl = "https://${AppName}.scm.azurewebsites.net/api/zip/site/wwwroot/modules/"


# DOWNLOAD WEBSITE CODE AS ARCHIVE
Write-Output "DOWNLOAD WEBSITE CODE AS ARCHIVE"
$WebClient = New-Object System.Net.WebClient
$WebClient.Headers.Add('Authorization', 'Basic ' + $sourcewebapp_base64AuthInfo)
$WebClient.Headers.Add('ContentType', 'multipart/form-data')
try{
    $WebClient.DownloadFile($sourcewebapp_apiUrl, $Archive)
}
catch{
    Write-Output $_.Exception.Message
    exit 1
}
$WebClient.Dispose()
Remove-Variable -Name WebClient

Write-Output "Upload modules to the container"
docker cp $Archive ${PlatformContainer}:/vc-platform/
docker exec $PlatformContainer powershell -Command "Expand-Archive -Path C:\vc-platform\*.zip -DestinationPath C:\vc-platform\Modules"

# Delete existing file (downloaded archive)
Remove-Item $Archive -Force

Write-Output "Restarting website"
Invoke-RestMethod "$restartUrl" -Method Post -ContentType "application/json" -Headers $headers