$ErrorActionPreference = 'Continue'

#Get platform src
git clone https://github.com/VirtoCommerce/vc-platform.git --branch dev --single-branch

# Get all modules from master branch
$modulesv3=Invoke-RestMethod https://raw.githubusercontent.com/VirtoCommerce/vc-modules/master/modules_v3.json
foreach ($module in $modulesv3) {
	$moduleName=$module.Versions.PackageUrl[0]
	$substingStartCut="module-"
	$substingEndCut="/releases"
	$substingStartCut=$moduleName.IndexOf($substingStartCut)+$substingStartCut.Length
	$substingEndCut=$moduleName.IndexOf($substingEndCut)-$substingStartCut
	$moduleName=$moduleName.substring($substingStartCut, $substingEndCut)
	$moduleFullName="vc-module-$moduleName"
	if(Test-Path -Path "$moduleFullName"){
        Write-Output "$moduleFullName already exists."
	}
	else{
		git clone https://github.com/VirtoCommerce/vc-module-$moduleName.git --branch master --single-branch
	}
	if(Test-Path -Path "$moduleFullName\docs"){
		Set-Location vc-module-$moduleName
        Copy-Item -Path "docs" -Destination "..\vc-platform\docs\modules\$moduleName" -Recurse -Force
		Set-Location  ..
	}
}
