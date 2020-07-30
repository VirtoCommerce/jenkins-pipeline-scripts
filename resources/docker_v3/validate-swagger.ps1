Param(
    [parameter(Mandatory = $true)]
    $ApiUrl,
    [parameter(Mandatory = $true)]
    $OutFile,
    $SwaggerValidatorUrl = "https://validator.swagger.io/validator/debug"
)

$swaggerApi = "$ApiUrl/docs/PlatformUI/swagger.json"

Write-Output "OutFile: $OutFile"
Invoke-WebRequest -Uri $swaggerApi -Method Get -OutFile $OutFile -SkipCertificateCheck -MaximumRetryCount 5 -RetryIntervalSec 5

$swaggerSchemaPath = $OutFile
$headers = @{}
$headers.Add("accept", "application/json")
$schemaContent = Get-Content -Path $swaggerSchemaPath
$result = Invoke-RestMethod -Uri $SwaggerValidatorUrl -Headers $headers -Body $schemaContent -ContentType "application/json" -Method Post -Debug -Verbose
Write-Output $result
$errors = $false
foreach ($validation in $result.schemaValidationMessages)
{
    Write-Output $validation
    if($validation.level -eq "error")
    {
        $errors = $true
    }
}
if($errors)
{
    exit 1
}