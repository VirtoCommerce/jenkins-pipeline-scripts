#Install modules to platform container
Param(
    $ApiUrl,
    $PlatformContainer,
    $ModulesDir
)
. $PSScriptRoot\..\Utilities.ps1

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

$checkModulesUrl = "$ApiUrl/api/platform/modules"

# LOCAL PATH TO STORE ARCHIVE WITH WEBSITE CODE
$Archive = [System.IO.Path]::GetTempFileName() + ".zip"

Compress-Archive -Path "$ModulesDir\*" -DestinationPath $Archive

Write-Output "Upload modules to the container"
docker cp $Archive ${PlatformContainer}:/vc-platform/
docker exec $PlatformContainer powershell -Command "Expand-Archive -Path C:\vc-platform\*.zip -DestinationPath C:\vc-platform\Modules"

# Delete existing file (downloaded archive)
Remove-Item $Archive -Force

Write-Output "Restarting website"
Invoke-RestMethod "$restartUrl" -Method Post -ContentType "application/json" -Headers $headers

#Check installed modules
Write-Output "Check installed modules"
$modules = Invoke-RestMethod $checkModulesUrl -Method Get -Headers $headers -ErrorAction Stop
$installedModules = 0
if ($modules.Length -le 0) {
    Write-Output "No module's info returned"
    exit 1
}
Foreach ($module in $modules) {
    if ($module.isInstalled) {
        $installedModules++
    }
    if ($module.validationErrors.Length -gt 0) {
        Write-Output $module.id
        Write-Output $module.validationErrors
        exit 1
    }
}
Write-Output "Modules installed: $installedModules"