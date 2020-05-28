$ErrorActionPreference = 'Stop'
if (-not ([Net.ServicePointManager]::SecurityProtocol).ToString().Contains([Net.SecurityProtocolType]::Tls12)) {
  [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol.toString() + ', ' + [Net.SecurityProtocolType]::Tls12
}

$releases = Invoke-RestMethod https://api.github.com/repos/VirtoCommerce/vc-storefront-core/releases -Headers @{"Accept"="application/json"} -UseBasicParsing
$latestRelease=''
foreach ($lr in $releases) {
    if ($lr.tag_name.StartsWith("v4")) {
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
$slotName = "staging"
$DestKuduDelPath = "https://$DestWebAppName-$slotName.scm.azurewebsites.net/api/vfs/site/wwwroot/?recursive=true"
$DestKuduConfPath = "https://$DestWebAppName-$slotName.scm.azurewebsites.net/api/vfs/site/wwwroot/web.config"
$DestKuduPath = "https://$DestWebAppName-$slotName.scm.azurewebsites.net/api/zip/site/wwwroot/"

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

Write-Host "Add Rewrite rule to web.config $DestWebAppName-$slotName"

$confOutFile = "web.config.tmp"

Invoke-RestMethod -Uri $DestKuduConfPath `
                        -Headers @{"Authorization"=$DestKuduApiAuthorisationToken;"If-Match"="*"} `
                        -Method GET `
                        -OutFile $confOutFile `
                        -ContentType "multipart/form-data"

# Add rule to web.config

$sourceXml = [xml](Get-Content "rewrite_rule.xml")
$targetXml = [xml](Get-Content "$confOutFile")

$sourceElement = $sourceXml.configuration.'system.webServer'.rewrite

$targetXml.configuration.'system.webServer'.AppendChild($targetXml.ImportNode($sourceElement, $true))
$targetXml.Save("web.config")
$confInFile = "web.config"
Remove-Item $confOutFile -Force

Invoke-RestMethod -Uri $DestKuduConfPath `
                        -Headers @{"Authorization"=$DestKuduApiAuthorisationToken;"If-Match"="*"} `
                        -Method PUT `
                        -InFile $confInFile `
                        -ContentType "multipart/form-data"

# Add robots.txt

$robotsFile = "robots.txt"
$DestKuduRobotsPath = "https://$DestWebAppName-$slotName.scm.azurewebsites.net/api/vfs/site/wwwroot/wwwroot/robots.txt"

Invoke-RestMethod -Uri $DestKuduRobotsPath `
                        -Headers @{"Authorization"=$DestKuduApiAuthorisationToken;"If-Match"="*"} `
                        -Method PUT `
                        -InFile $robotsFile `
                        -ContentType "multipart/form-data"						

Write-Host "Start WebApp $DestWebAppName-$slotName"

Start-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName -Slot $slotName
