Param(
    [parameter(Mandatory = $true)]
    $ApiUrl,
    [parameter(Mandatory = $true)]
    $OutFile
)

$swaggerApi = "$ApiUrl/docs/v1"

Invoke-WebRequest -Uri $swaggerApi -Method Get -OutFile $OutFile -SkipCertificateCheck