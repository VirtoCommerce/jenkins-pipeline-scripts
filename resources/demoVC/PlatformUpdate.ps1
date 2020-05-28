$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$releases = Invoke-RestMethod https://api.github.com/repos/VirtoCommerce/vc-platform/releases -Headers @{"Accept"="application/json"} -UseBasicParsing
$latestRelease=''
foreach ($lr in $releases) {
    if ($lr.tag_name.StartsWith("v2")) {
        $latestRelease = $lr
        break
    }    
}

$latestZipUrl = $latestRelease.assets.browser_download_url
$latestZipName = $latestRelease.assets.name
Write-Host "Download url is $latestZipUrl"
$Path2Zip = "$((Get-Location).Path)\$latestZipName"
Write-Host "Path to zip is $Path2Zip"
Invoke-RestMethod -Method Get -Uri $latestZipUrl -OutFile $Path2Zip

# Upload Platform Zip File to Azure

$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"
$SubscriptionID = "${env:AzureSubscriptionIDProd}"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID

$DestResourceGroupName = "${env:AzureResourceGroupNameProd}"
$DestWebAppName = "${env:AzureWebAppAdminNameProd}"
$slotName = "staging"
$DestKuduDelPath = "https://$DestWebAppName-$slotName.scm.azurewebsites.net/api/vfs/site/wwwroot/platform/?recursive=true"
$DestKuduPath = "https://$DestWebAppName-$slotName.scm.azurewebsites.net/api/zip/site/wwwroot/platform/"
$DestKuduPutPath = "https://$DestWebAppName-$slotName.scm.azurewebsites.net/api/vfs/site/wwwroot/platform/App_Data/VirtoCommerce.lic"
$LicFile = "${env:DEMO_LIC_FILE}"

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
    $DestPublishingCredentials = Get-AzureRmWebAppPublishingCredentials $DestResourceGroupName $DestWebAppName $slotName
    return ("Basic {0}" -f [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(("{0}:{1}" -f $DestPublishingCredentials.Properties.PublishingUserName, $DestPublishingCredentials.Properties.PublishingPassword))))
}

$DestKuduApiAuthorisationToken = Get-KuduApiAuthorisationHeaderValue $DestResourceGroupName $DestWebAppName

Write-Host "Stop WebApp $DestWebAppName-$slotName"

Stop-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName -Slot $slotName

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

Write-Host "Uploading License File $LicFile to $DestKuduPutPath"

Invoke-RestMethod -Uri $DestKuduPutPath `
                        -Headers @{"Authorization"=$DestKuduApiAuthorisationToken;"If-Match"="*"} `
                        -Method PUT `
                        -InFile $LicFile `
                        -ContentType "multipart/form-data"

Write-Host "Start WebApp $DestWebAppName-$slotName"

Start-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName -Slot $slotName
