$ErrorActionPreference = 'Stop'

$SubscriptionID="${env:SubscriptionID}"
$ResourceGroupName="${env:ResourceGroupName}"
$WebAppName="${env:WebAppName}"
$sourceStorage="${env:sourceStorage}"
$destStorage="${env:destStorage}"
$sourceSAS="${env:sourceSAS}"
$destSAS="${env:destSAS}"

Write-Host "Source storage: $sourceStorage"
Write-Host "Dest storage: $destStorage"

$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID

Write-Host "Stop WebApp $WebAppName"
Stop-AzureRmWebApp -ResourceGroupName $ResourceGroupName -Name $WebAppName

$ExcludePattern="google-tag-manager-body.liquid;google-tag-manager-head.liquid"
& "${env:Utils}\AzCopy10\AzCopy" cp https://${sourceStorage}.blob.core.windows.net/cms-content${sourceSAS} https://${destStorage}.blob.core.windows.net/cms-content${destSAS} --recursive --exclude-pattern="$ExcludePattern" --overwrite true #--delete-destination=true

Write-Host "Start WebApp $WebAppName"
Start-AzureRmWebApp -ResourceGroupName $ResourceGroupName -Name $WebAppName
