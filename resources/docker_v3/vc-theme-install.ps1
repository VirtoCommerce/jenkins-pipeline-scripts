Param(
    [parameter(Mandatory = $true)]
    $ThemePath,
    [parameter(Mandatory = $true)]
    $PlatformContainer
)

Write-Output $PlatformContainer
Write-Output $ThemePath
Write-Output "docker cp"
docker cp $ThemePath ${PlatformContainer}:/vc-platform/App_Data/cms-content/Themes
