$ErrorActionPreference = 'Stop'
if (-not ([Net.ServicePointManager]::SecurityProtocol).ToString().Contains([Net.SecurityProtocolType]::Tls12)) {
  [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol.toString() + ', ' + [Net.SecurityProtocolType]::Tls12
}
$latestRelease = Invoke-WebRequest https://api.github.com/repos/VirtoCommerce/vc-storefront-core/releases/latest -Headers @{"Accept"="application/json"} -UseBasicParsing
$json = $latestRelease.Content | ConvertFrom-Json
$latestZipUrl = $json.assets.browser_download_url
$latestZipName = $json.assets.name
Write-Host "Download url is $latestZipUrl"
$Path2Zip = "$((Get-Location).Path)\$latestZipName"
Write-Host "Path to zip is $Path2Zip"
Invoke-RestMethod -Method Get -Uri $latestZipUrl -OutFile $Path2Zip

# Upload Storefront Zip File to Azure
$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"
$SubscriptionID = "${env:AzureSubscriptionIDProd}"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID

$DestResourceGroupName = "${env:AzureResourceGroupNameProd}"
$DestWebAppName = "${env:AzureWebAppNameProd}"
$slotName = " "
Write-Host "$slotName is..."
$DestKuduDelPath = "https://$DestWebAppName.scm.azurewebsites.net/api/vfs/site/wwwroot/?recursive=true"
$DestKuduPath = "https://$DestWebAppName.scm.azurewebsites.net/api/zip/site/wwwroot/"

function Get-AzureRmWebAppPublishingCredentials($DestResourceGroupName, $DestWebAppName, $slotName = $null){

  $ResourceType = "Microsoft.Web/sites/config"
  $DestResourceName = "$DestWebAppName/publishingcredentials"
	
	$DestPublishingCredentials = Invoke-AzureRmResourceAction -ResourceGroupName $DestResourceGroupName -ResourceType $ResourceType -ResourceName $DestResourceName -Action list -ApiVersion 2015-08-01 -Force
    	return $DestPublishingCredentials
}

function Get-KuduApiAuthorisationHeaderValue($DestResourceGroupName, $DestWebAppName){
    $DestPublishingCredentials = Get-AzureRmWebAppPublishingCredentials $DestResourceGroupName $DestWebAppName $slotName
    return ("Basic {0}" -f [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(("{0}:{1}" -f $DestPublishingCredentials.Properties.PublishingUserName, $DestPublishingCredentials.Properties.PublishingPassword))))
}

$DestKuduApiAuthorisationToken = Get-KuduApiAuthorisationHeaderValue $DestResourceGroupName $DestWebAppName

Write-Host "Stop WebApp $DestWebAppName"

Stop-AzureRmWebApp -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName

Start-Sleep -s 15

Write-Host "Deleting Files at $DestKuduDelPath"

Invoke-RestMethod -Uri $DestKuduDelPath -Headers @{"Authorization"=$DestKuduApiAuthorisationToken;"If-Match"="*"} -Method DELETE

Write-Host "Uploading File $Path2Zip to $DestKuduPath"

Invoke-RestMethod -Uri $DestKuduPath `
                        -Headers @{"Authorization"=$DestKuduApiAuthorisationToken;"If-Match"="*"} `
                        -Method PUT `
                        -InFile $Path2Zip `
                        -ContentType "multipart/form-data"

Start-Sleep -s 5

Write-Host "Start WebApp $DestWebAppName"

Start-AzureRmWebApp -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName
