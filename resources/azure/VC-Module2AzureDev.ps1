param(
    $SubscriptionID,
    $WebAppName,
    $ResourceGroupName,
    $Path2Zip = $null
)
# Get Module Name

$Path = Get-Childitem -Recurse -Path "${env:WORKSPACE}\" -File -Include module.manifest

# Read in the file contents and return the version node's value.
[ xml ]$fileContents = Get-Content -Path $Path -Raw
$ModuleName = Select-Xml -Xml $fileContents -XPath "/module/id"

# Get Module Zip File

if($null -eq $Path2Zip)
{
    $Path2Zip = Get-Childitem -Recurse -Path "${env:WORKSPACE}\artifacts\" -File -Include *.zip
}

# Upload Module Zip File to Azure

$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID

$DestKuduPath = "https://$WebAppName.scm.azurewebsites.net/api/zip/site/wwwroot/modules/$ModuleName/"

function Get-AzureRmWebAppPublishingCredentials($ResourceGroupName, $WebAppName, $slotName = $null){
	if ([string]::IsNullOrWhiteSpace($slotName)){
        $ResourceType = "Microsoft.Web/sites/config"
		$DestResourceName = "$WebAppName/publishingcredentials"
	}
	else{
        $ResourceType = "Microsoft.Web/sites/slots/config"
		$DestResourceName = "$WebAppName/$slotName/publishingcredentials"
	}
	$DestPublishingCredentials = Invoke-AzureRmResourceAction -ResourceGroupName $ResourceGroupName -ResourceType $ResourceType -ResourceName $DestResourceName -Action list -ApiVersion 2015-08-01 -Force
    	return $DestPublishingCredentials
}

function Get-KuduApiAuthorisationHeaderValue($ResourceGroupName, $WebAppName){
    $DestPublishingCredentials = Get-AzureRmWebAppPublishingCredentials $ResourceGroupName $WebAppName
    return ("Basic {0}" -f [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(("{0}:{1}" -f $DestPublishingCredentials.Properties.PublishingUserName, $DestPublishingCredentials.Properties.PublishingPassword))))
}

$DestKuduApiAuthorisationToken = Get-KuduApiAuthorisationHeaderValue $ResourceGroupName $WebAppName

Write-Host "Stop WebApp"

Stop-AzureRmWebApp -ResourceGroupName $ResourceGroupName -Name $WebAppName

Start-Sleep -s 5

Write-Host "Uploading File"

Invoke-RestMethod -Uri $DestKuduPath `
                        -Headers @{"Authorization"=$DestKuduApiAuthorisationToken;"If-Match"="*"} `
                        -Method PUT `
                        -InFile $Path2Zip `
                        -ContentType "multipart/form-data"

Start-Sleep -s 5

Write-Host "Start WebApp"

Start-AzureRmWebApp -ResourceGroupName $ResourceGroupName -Name $WebAppName
