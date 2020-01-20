$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"

$SubscriptionID = "${env:AzureSubscriptionIDProd}"
$WebSiteName = "${env:AzureWebAppAdminNameProd}"
$SlotName = "${env:AzureSlotNameProd}"
$DestResourceGroupName = "${env:AzureResourceGroupNameProd}"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID

# Swap web site slots
Start-Sleep -s 11

Write-Output "Switching $WebSiteName slot"

Switch-AzureRmWebAppSlot -Name $WebSiteName -ResourceGroupName $DestResourceGroupName -SourceSlotName $SlotName -DestinationSlotName "production"
Stop-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $WebSiteName -Slot $SlotName

Write-Output "Completed"
