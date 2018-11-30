Param(  
    [parameter(Mandatory = $true)]
    $path
)

& "${env:NUGET}\nuget.exe" push "${path}" -Source nuget.org -ApiKey ${env:NUGET_KEY}