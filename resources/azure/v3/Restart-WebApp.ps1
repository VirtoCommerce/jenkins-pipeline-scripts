param(
    $WebAppName,
    $ResourceGroupName,
    $SubscriptionID
)

$ApplicationID ="${env:AzureAppID}"
$APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
$psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
$TenantID = "${env:AzureTenantID}"

Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal
Select-AzureRmSubscription -SubscriptionId $SubscriptionID


Write-Host "Restart WebApp $WebAppName"

Restart-AzureRmWebApp -ResourceGroupName $ResourceGroupName -Name $WebAppName