Param(
    [parameter(Mandatory = $true)]
    $ApiUrl,
    [parameter(Mandatory = $true)]
    $OutFile
)

$swaggerApi = "$ApiUrl/docs/PlatformUI/swagger.json"

Start-Sleep -Seconds 10

Invoke-WebRequest -Uri $swaggerApi -Method Get -OutFile $OutFile -SkipCertificateCheck