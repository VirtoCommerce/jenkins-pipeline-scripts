$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"
$SubscriptionID = "973d0b8c-44bf-438d-a4b7-1c4162d3ccba"

Write-Host "Infrastructure Check and Deploy Started"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID

$DestResourceGroupName = "DEV-VC"
$ResourceGroupLocation = "eastus"
$TemplateFile = "azuredeployDEV-VC.json"
$TemplateParametersFile = "azuredeployDEV-VC_parameters.json"

$currentResourceDeploy = Get-AzureRmResourceGroup -Name $DestResourceGroupName -Location $ResourceGroupLocation -Verbose -ErrorAction SilentlyContinue
If ($null -eq $currentResourceDeploy) {
    New-AzureRmResourceGroupDeployment -Name ((Get-ChildItem $TemplateFile).BaseName + '-' + ((Get-Date).ToUniversalTime()).ToString('MMdd-HHmm')) `
    -ResourceGroupName $DestResourceGroupName `
    -TemplateFile $TemplateFile `
    -TemplateParameterFile $TemplateParametersFile `
    -Force -Verbose `
    -ErrorVariable ErrorMessages
}

Write-Host "Infrastructure Check and Deploy Finished"
