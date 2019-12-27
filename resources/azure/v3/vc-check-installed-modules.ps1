Param(
    [parameter(Mandatory = $true)]
    $ApiUrl
)

add-type @"
using System.Net;
using System.Security.Cryptography.X509Certificates;
public class TrustAllCertsPolicy : ICertificatePolicy {
    public bool CheckValidationResult(
        ServicePoint srvPoint, X509Certificate certificate,
        WebRequest request, int certificateProblem) {
        return true;
    }
}
"@
$AllProtocols = [System.Net.SecurityProtocolType]'Ssl3,Tls,Tls11,Tls12'
[System.Net.ServicePointManager]::SecurityProtocol = $AllProtocols
[System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAllCertsPolicy

$appAuthUrl = "$ApiUrl/connect/token"

function Get-AuthToken {
    param (
        $appAuthUrl,
        $username,
        $password
    )
    $grant_type = "password"
    $content_type = "application/x-www-form-urlencoded"

    $body = @{username=$username; password=$password; grant_type=$grant_type}
    $response = Invoke-WebRequest -Uri $appAuthUrl -Method Post -ContentType $content_type -Body $body
    $responseContent = $response.Content | ConvertFrom-Json
    return $responseContent.access_token
}

$authToken = Get-AuthToken $appAuthUrl admin store
$headers = @{}
$headers.Add("Authorization", "Bearer $authToken")

$checkModulesUrl = "$ApiUrl/api/platform/modules"

$modules = Invoke-RestMethod $checkModulesUrl -Method Get -Headers $headers -ErrorAction Stop
$installedModules = 0
$iserror = $false
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
        $iserror = $true
    }
}
Write-Output "Modules installed: $installedModules"
if($iserror)
{
    exit 1
}