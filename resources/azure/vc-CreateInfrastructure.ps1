
$DestResourceGroupName = "${env:AzureResourceGroupNameDev}"
$ResourceGroupLocation = @(Get-AzureRmLocation | Where-Object Providers -like "*vm*" | Select-Object -ExpandProperty Location)
$TemplateFile = 'azuredeploy.json'
$TemplateParametersFile = 'parameters.json'

if ((Get-AzureRmResourceGroup -Name $DestResourceGroupName -Location $ResourceGroupLocation -Verbose -ErrorAction SilentlyContinue) -eq $null) {
    New-AzureRmResourceGroup -Name $DestResourceGroupName -Location $ResourceGroupLocation -Verbose -Force -ErrorAction Stop
    New-AzureRmResourceGroupDeployment -Name ((Get-ChildItem $TemplateFile).BaseName + '-' + ((Get-Date).ToUniversalTime()).ToString('MMdd-HHmm')) `
    -ResourceGroupName $DestResourceGroupName `
    -TemplateFile $TemplateFile `
    -TemplateParameterFile $TemplateParametersFile `
    -Force -Verbose `
    -ErrorVariable ErrorMessages
}