param(
    [parameter(Mandatory = $true)]
    $Prefix
)

. $PSScriptRoot\utilities.ps1 

# Get Storefront Zip File

$Path2Zip = Get-Childitem -Recurse -Path "${env:WORKSPACE}\artifacts\" -File -Include *.zip

# Upload Storefront Zip File to Azure

$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"
$SubscriptionID = Get-EnvVar -Prefix $Prefix -Name "AzureSubscriptionID"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID

$DestResourceGroupName = Get-EnvVar -Prefix $Prefix -Name "AzureResourceGroupName"
$DestWebAppName = Get-EnvVar -Prefix $Prefix -Name "AzureWebAppName"
$DestKuduPath = "https://$DestWebAppName.scm.azurewebsites.net/api/zip/site/wwwroot/"
$DestKuduDelPath = "https://$DestWebAppName.scm.azurewebsites.net/api/vfs/site/wwwroot/?recursive=true"

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

Write-Host "Stop WebApp $DestWebAppName"

Stop-AzureRmWebApp -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName | Select-Object Name,State
Start-Sleep -s 35

Write-Host "Deleting Files in $DestKuduDelPath"

Invoke-RestMethod -Uri $DestKuduDelPath -Headers @{"Authorization"=$DestKuduApiAuthorisationToken;"If-Match"="*"} -Method DELETE

Start-Sleep -s 10

Write-Host "Uploading File: $Path2Zip"

Invoke-RestMethod -Uri $DestKuduPath `
                        -Headers @{"Authorization"=$DestKuduApiAuthorisationToken;"If-Match"="*"} `
                        -Method PUT `
                        -InFile $Path2Zip `
                        -ContentType "multipart/form-data"

Start-Sleep -s 10

Write-Host "Start WebApp $DestWebAppName"

Start-AzureRmWebApp -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName | Select-Object Name,State
