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

#$DestResourceGroupName = "${env:AzureResourceGroupNameQA}"
$DestResourceGroupName = "DEMO-BulkUpdate"
#$DestWebAppName = "${env:AzureWebAppAdminNameQA}"
$DestWebAppName = "bulkupdate-admin-demo"
$DestKuduDelPath = "https://$DestWebAppName.scm.azurewebsites.net/api/vfs/site/modules/$ModuleName/?recursive=true"
$DestKuduPath = "https://$DestWebAppName.scm.azurewebsites.net/api/zip/site/wwwroot/modules/$ModuleName/"

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

Write-Host "Stop $DestWebAppName"

Stop-AzureRmWebApp -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName

Start-Sleep -s 5

Write-Host "Deleting Files at $DestKuduDelPath"

Invoke-RestMethod -Uri $DestKuduDelPath -Headers @{"Authorization"=$DestKuduApiAuthorisationToken;"If-Match"="*"} -Method DELETE

Write-Host "Uploading File $Path2Zip to $DestKuduPath"

Invoke-RestMethod -Uri $DestKuduPath `
                        -Headers @{"Authorization"=$DestKuduApiAuthorisationToken;"If-Match"="*"} `
                        -Method PUT `
                        -InFile $Path2Zip `
                        -ContentType "multipart/form-data"

Start-Sleep -s 5

Write-Host "Start $DestWebAppName"

Start-AzureRmWebApp -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName

#Destination2

$Dest2SubscriptionID = "${env:AzureSubscriptionIDProd}"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $Dest2SubscriptionID

$Dest2WebAppName = "${env:AzureWebAppAdminNameProd}"
$slotName = "staging"
$Dest2ResourceGroupName = "${env:AzureResourceGroupNameProd}"
$Dest2KuduDelPath = "https://$Dest2WebAppName-$slotName.scm.azurewebsites.net/api/vfs/site/modules/$ModuleName/?recursive=true"
$Dest2KuduPath = "https://$Dest2WebAppName-$slotName.scm.azurewebsites.net/api/zip/site/modules/$ModuleName/"

function Get-AzureRmWebAppPublishingCredentials($Dest2ResourceGroupName, $Dest2WebAppName, $slotName = $null){
	if ([string]::IsNullOrWhiteSpace($slotName)){
        $ResourceType = "Microsoft.Web/sites/config"
		$Dest2ResourceName = "$Dest2WebAppName/publishingcredentials"
	}
	else{
        $ResourceType = "Microsoft.Web/sites/slots/config"
		$Dest2ResourceName = "$Dest2WebAppName/$slotName/publishingcredentials"
	}
	$Dest2PublishingCredentials = Invoke-AzureRmResourceAction -ResourceGroupName $Dest2ResourceGroupName -ResourceType $ResourceType -ResourceName $Dest2ResourceName -Action list -ApiVersion 2015-08-01 -Force
    	return $Dest2PublishingCredentials
}

function Get-KuduApiAuthorisationHeaderValue($Dest2ResourceGroupName, $Dest2WebAppName, $slotName = $null){
    $Dest2PublishingCredentials = Get-AzureRmWebAppPublishingCredentials $Dest2ResourceGroupName $Dest2WebAppName $slotName
    return ("Basic {0}" -f [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(("{0}:{1}" -f $Dest2PublishingCredentials.Properties.PublishingUserName, $Dest2PublishingCredentials.Properties.PublishingPassword))))
}

$Dest2KuduApiAuthorisationToken = Get-KuduApiAuthorisationHeaderValue $Dest2ResourceGroupName $Dest2WebAppName $slotName

Write-Host "Stop WebApp $Dest2WebAppName-$slotName"

Stop-AzureRmWebAppSlot -ResourceGroupName $Dest2ResourceGroupName -Name $Dest2WebAppName -Slot $slotName

Start-Sleep -s 5

Write-Host "Deleting Files at $Dest2KuduDelPath"

Invoke-RestMethod -Uri $Dest2KuduDelPath -Headers @{"Authorization"=$Dest2KuduApiAuthorisationToken;"If-Match"="*"} -Method DELETE

Write-Host "Uploading File $Path2Zip to $Dest2KuduPath"

Invoke-RestMethod -Uri $Dest2KuduPath `
                        -Headers @{"Authorization"=$Dest2KuduApiAuthorisationToken;"If-Match"="*"} `
                        -Method PUT `
                        -InFile $Path2Zip `
                        -ContentType "multipart/form-data"

Start-Sleep -s 5

Write-Host "Start $Dest2WebAppName-$slotName"

Start-AzureRmWebAppSlot -ResourceGroupName $Dest2ResourceGroupName -Name $Dest2WebAppName -Slot $slotName
