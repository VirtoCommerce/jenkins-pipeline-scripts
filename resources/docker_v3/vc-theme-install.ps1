Param(
    [parameter(Mandatory = $true)]
    $ThemePath,
    [parameter(Mandatory = $true)]
    $StorefrontContainer
)

Write-Output $StorefrontContainer
Write-Output $ThemePath
Write-Output "docker cp"
if($IsLinux)
{
    Write-Output "Linux"
    docker exec ${storefrontContainer} bash -c "mkdir -p /vc-storefront/wwwroot/cms-content/Themes"
    docker cp $ThemePath/. ${StorefrontContainer}:/vc-storefront/wwwroot/cms-content/Themes
}
else 
{
    docker exec ${storefrontContainer} cmd /c mkdir \vc-storefront\wwwroot\cms-content
    docker cp $ThemePath ${StorefrontContainer}:/vc-storefront/wwwroot/cms-content/Themes
}
