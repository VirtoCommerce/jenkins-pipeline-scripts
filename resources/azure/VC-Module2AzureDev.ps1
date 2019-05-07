$ResourceGroupLocation = @(Get-AzureRmLocation | Where-Object Providers -like "*vm*" | Select-Object -ExpandProperty Location)
$TemplateFile = 'azuredeploy.json'
$TemplateParametersFile = 'parameters.json'

# Get Module Name

$Path = Get-Childitem -Recurse -Path "${env:WORKSPACE}\" -File -Include module.manifest

# Read in the file contents and return the version node's value.
[ xml ]$fileContents = Get-Content -Path $Path -Raw
$ModuleName = Select-Xml -Xml $fileContents -XPath "/module/id"

# Get Module Zip File

$Path2Zip = Get-Childitem -Recurse -Path "${env:WORKSPACE}\artifacts\" -File -Include *.zip

# Upload Module Zip File to Azure

$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"
$SubscriptionID = "${env:AzureSubscriptionIDDev}"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID

$DestResourceGroupName = "${env:AzureResourceGroupNameDev}"
$DestWebAppName = "${env:AzureWebAppAdminNameDev}"
$DestKuduPath = "https://$DestWebAppName.scm.azurewebsites.net/api/zip/site/wwwroot/modules/$ModuleName/"

if ((Get-AzureRmResourceGroup -Name $DestResourceGroupName -Location $ResourceGroupLocation -Verbose -ErrorAction SilentlyContinue) -eq $null) {
    New-AzureRmResourceGroup -Name $DestResourceGroupName -Location $ResourceGroupLocation -Verbose -Force -ErrorAction Stop
    New-AzureRmResourceGroupDeployment -Name ((Get-ChildItem $TemplateFile).BaseName + '-' + ((Get-Date).ToUniversalTime()).ToString('MMdd-HHmm')) `
    -ResourceGroupName $DestResourceGroupName `
    -TemplateFile $TemplateFile `
    -TemplateParameterFile $TemplateParametersFile `
    -Force -Verbose `
    -ErrorVariable ErrorMessages
}

function Get-AzureRmWebAppPublishingCredentials($DestResourceGroupName, $DestWebAppName, $slotName = $null){
	if ([string]::IsNullOrWhiteSpace($slotName)){
        $ResourceType = "Microsoft.Web/sites/config"
		$DestResourceName = "$DestWebAppName/publishingcredentials"
	}
	else{
        $ResourceType = "Microsoft.Web/sites/slots/config"
		$DestResourceName = "$DestWebAppName/$slotName/publishingcredentials"
	}
	$DestPublishingCredentials = Invoke-AzureRmResourceAction -ResourceGroupName $DestResourceGroupName -ResourceType $ResourceType -ResourceName $DestResourceName -Action list -ApiVersion 2015-08-01 -Force
    	return $DestPublishingCredentials
}

function Get-KuduApiAuthorisationHeaderValue($DestResourceGroupName, $DestWebAppName){
    $DestPublishingCredentials = Get-AzureRmWebAppPublishingCredentials $DestResourceGroupName $DestWebAppName
    return ("Basic {0}" -f [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(("{0}:{1}" -f $DestPublishingCredentials.Properties.PublishingUserName, $DestPublishingCredentials.Properties.PublishingPassword))))
}

$DestKuduApiAuthorisationToken = Get-KuduApiAuthorisationHeaderValue $DestResourceGroupName $DestWebAppName

Write-Host "Stop WebApp"

Stop-AzureRmWebApp -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName

Start-Sleep -s 5

Write-Host "Uploading File"

Invoke-RestMethod -Uri $DestKuduPath `
                        -Headers @{"Authorization"=$DestKuduApiAuthorisationToken;"If-Match"="*"} `
                        -Method PUT `
                        -InFile $Path2Zip `
                        -ContentType "multipart/form-data"

Start-Sleep -s 5

Write-Host "Start WebApp"

Start-AzureRmWebApp -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName
