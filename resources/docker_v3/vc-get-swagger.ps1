Param(
    [parameter(Mandatory = $true)]
    $ApiUrl,
    [parameter(Mandatory = $true)]
    $OutFile
)

$swaggerApi = "$ApiUrl/docs/PlatformUI/swagger.json"

Write-Output "OutFile: $OutFile"
Invoke-WebRequest -Uri $swaggerApi -Method Get -OutFile $OutFile -SkipCertificateCheck -MaximumRetryCount 5 -RetryIntervalSec 5
