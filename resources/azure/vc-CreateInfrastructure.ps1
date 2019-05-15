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

$DestResourceGroupName = "${env:AzureResourceGroupNameDev}"
$ResourceGroupLocation = @(Get-AzureRmLocation | Where-Object Providers -like "*vm*" | Select-Object -ExpandProperty Location)
$TemplateFile = 'azuredeploy.json'
$TemplateParametersFile = 'parameters.json'

if ($null -eq (Get-AzureRmResourceGroup -Name $DestResourceGroupName -Location $ResourceGroupLocation[0] -Verbose -ErrorAction SilentlyContinue)) {
    New-AzureRmResourceGroup -Name $DestResourceGroupName -Location $ResourceGroupLocation[0] -Verbose -Force -ErrorAction Stop
    New-AzureRmResourceGroupDeployment -Name ((Get-ChildItem $TemplateFile).BaseName + '-' + ((Get-Date).ToUniversalTime()).ToString('MMdd-HHmm')) `
    -ResourceGroupName $DestResourceGroupName `
    -TemplateFile $TemplateFile `
    -TemplateParameterFile $TemplateParametersFile `
    -Force -Verbose `
    -ErrorVariable ErrorMessages
}

Write-Host "Infrastructure Check and Deploy Finished"