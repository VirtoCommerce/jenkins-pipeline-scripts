Param(
    [parameter(Mandatory = $true)]
    $apiurl,
    [parameter(Mandatory = $true)]
    $swaggerFile
)

$swaggerApi = "$apiurl/docs/v1"

Invoke-WebRequest -Uri $swaggerApi -Method Get -OutFile $swaggerFile