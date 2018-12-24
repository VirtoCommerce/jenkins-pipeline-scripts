Param(
    [parameter(Mandatory = $true)]
    $themeZip,
    [parameter(Mandatory = $true)]
    $platformContainer
)

Write-Output $platformContainer
Write-Output $themeZip
Write-Output "docker cp"
docker cp $themeZip ${platformContainer}:/vc-platform/
Write-Output "remove-item old theme"
docker exec $platformContainer powershell -Command "Remove-Item C:\vc-platform\App_Data\cms-content\Themes\Electronics\default -Force -Recurse"
Write-Output "expand archive new theme"
docker exec $platformContainer powershell -Command "Expand-Archive -Path 'C:\vc-platform\theme.zip' -DestinationPath 'C:\vc-platform\App_Data\cms-content\Themes\Electronics\'"
