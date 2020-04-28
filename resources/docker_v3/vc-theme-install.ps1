Param(
    [parameter(Mandatory = $true)]
    $ThemePath,
    [parameter(Mandatory = $true)]
    $StorefrontContainer
)

Write-Output $StorefrontContainer
Write-Output $ThemePath
Write-Output "docker cp"
docker cp $ThemePath ${StorefrontContainer}:/vc-storefront/wwwroot/cms-content/Themes
