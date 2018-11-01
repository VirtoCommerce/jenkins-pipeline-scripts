Param(  
    [parameter(Mandatory = $true)]
    $apiurl,
    [parameter(Mandatory = $true)]
    $moduleZipArchievePath,
    $hmacAppId,
    $hmacSecret
)

. $PSScriptRoot\utilities.ps1

if ([string]::IsNullOrWhiteSpace($hmacAppId)) {
    $hmacAppId = "${env:HMAC_APP_ID}"
}

if ([string]::IsNullOrWhiteSpace($hmacSecret)) {
    $hmacSecret = "${env:HMAC_SECRET}"
}

# Initialize paths used by the script
$moduleUploadUrl = "$apiurl/api/platform/modules/localstorage"
$moduleInstallUrl = "$apiurl/api/platform/modules/install"

# Initiate modules installation
$headerValue = Create-Authorization $hmacAppId $hmacSecret
$headers = @{}
$headers.Add("Authorization", $headerValue)

$moduleUploadResult = Invoke-MultipartFormDataUpload -InFile $moduleZipArchievePath -Uri $moduleUploadUrl -Authorization $headerValue
Write-Output $moduleUploadResult
$moduleInstallResult = Invoke-RestMethod -Uri $moduleInstallUrl -Method Post -Headers $headers -Body $moduleUploadResult
Write-Output $moduleInstallResult
Start-Sleep -s 3