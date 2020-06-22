$ErrorActionPreference = 'Stop'

#Get platform src
git clone https://github.com/VirtoCommerce/vc-platform.git --branch master --single-branch

# Get all modules from master branch
$modulesv3=Invoke-RestMethod https://raw.githubusercontent.com/VirtoCommerce/vc-modules/master/modules_v3.json
$substingLength="VirtoCommerce.".length
foreach ($module in $modulesv3) {
    $moduleName=$module.id
	$moduleName=$moduleName.substring($substingLength)
	$moduleFullName="vc-module-$moduleName"
	if(Test-Path -Path "$moduleFullName"){
        Write-Output "$moduleFullName already exists."
	}
	else{
		git clone https://github.com/VirtoCommerce/vc-module-$moduleName.git --branch master --single-branch
	}
	if(Test-Path -Path "$moduleFullName\docs"){
		cd vc-module-$moduleName
        Copy-Item -Path "docs" -Destination "..\vc-platform\docs\modules\$moduleName" -Recurse -Force
		cd ..
    }    
}
