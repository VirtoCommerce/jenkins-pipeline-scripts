Param(
    [parameter(Mandatory = $true)]
    $ApiUrl,
    [parameter(Mandatory = $true)]
    $OutFile
)

$swaggerApi = "$ApiUrl/docs/PlatformUI/swagger.json"

Write-Output "OutFile: $OutFile"
Invoke-RestMethod -Uri $swaggerApi -Method Get -OutFile $OutFile -SkipCertificateCheck -MaximumRetryCount 3 -RetryIntervalSec 5