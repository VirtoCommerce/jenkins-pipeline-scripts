#$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"
$SubscriptionID = "${env:AzureSubscriptionIDProd}"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID

$WebSiteName = "vc-admin-pro"
$WebSiteName2 = "vc-public-pro"
$SlotName = "staging"
$DestResourceGroupName = "PROD-VC"

# Swap web site slots
Start-Sleep -s 11

Write-Output "Switching $WebSiteName slot"

Switch-AzureRmWebAppSlot -Name $WebSiteName -ResourceGroupName $DestResourceGroupName -SourceSlotName $SlotName -DestinationSlotName "production"
Stop-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $WebSiteName -Slot $SlotName

Write-Output "Switching $WebSiteName2 slot"
 
Switch-AzureRmWebAppSlot -Name $WebSiteName2 -ResourceGroupName $DestResourceGroupName -SourceSlotName $SlotName -DestinationSlotName "production"
Stop-AzureRmWebAppSlot -ResourceGroupName $DestResourceGroupName -Name $WebSiteName2 -Slot $SlotName

Write-Output "Completed"