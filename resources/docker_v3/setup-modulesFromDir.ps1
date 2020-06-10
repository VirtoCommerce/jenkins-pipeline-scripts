#Install modules to platform container
Param(
    $PlatformContainer,
    $ModulesDir
)
Write-Output "Stop Container ${PlatformContainer}"
docker stop $PlatformContainer

$containerDest = "/vc-platform/Modules"
if($IsLinux)
{
    Write-Output "Linux"
    $containerDest = "/opt/vc-platform/Modules"
}
Write-Output "Upload modules to the container"
docker cp ${ModulesDir}/. ${PlatformContainer}:${containerDest}

Write-Output "Start Container"
docker start $PlatformContainer
