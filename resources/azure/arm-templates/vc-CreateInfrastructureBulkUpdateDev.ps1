Write-Host ${env:AzureAppID}
Write-Host ${env:AzureAPIKey}
Write-Host ${env:AzureTenantID}
Write-Host ${env:AzureSubscriptionIDDev}
Write-Host ${env:AzureResourceGroupNameDev}

$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"
$SubscriptionID = "${env:AzureSubscriptionIDDev}"

Write-Host "Infrastructure Check and Deploy Started"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID

#$DestResourceGroupName = "${env:AzureResourceGroupNameDev}"
$DestResourceGroupName = "DEV-BulkUpdate"
$ResourceGroupLocation = "eastus"
$TemplateFile = 'azuredeployBulkUpdate.json'
$TemplateParametersFile = 'azuredeployBulkUpdate_parameters_dev.json'

$currentResourceDeploy = Get-AzureRmResourceGroup -Name $DestResourceGroupName -Location $ResourceGroupLocation -Verbose -ErrorAction SilentlyContinue
If ($null -eq $currentResourceDeploy) {
    New-AzureRmResourceGroup -Name $DestResourceGroupName -Location $ResourceGroupLocation -Verbose -Force -ErrorAction Stop
} else {
    New-AzureRmResourceGroupDeployment -Name ((Get-ChildItem $TemplateFile).BaseName + '-' + ((Get-Date).ToUniversalTime()).ToString('MMdd-HHmm')) `
    -ResourceGroupName $DestResourceGroupName `
    -TemplateFile $TemplateFile `
    -TemplateParameterFile $TemplateParametersFile `
    -Force -Verbose `
    -ErrorVariable ErrorMessages
}

Write-Host "Infrastructure Check and Deploy Finished"