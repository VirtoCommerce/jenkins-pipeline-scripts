param(
    [string] $StagingName, 
    [string] $StoreName,
    $AzureBlobName,
    $AzureBlobKey,
    $WebAppName,
    $ResourceGroupName,
    $SubscriptionID,
    $ExcludePattern
)

$ErrorActionPreference = "Stop"

Copy-Item .\pages .\artifacts\Pages\vccom -Recurse -Force
Copy-Item .\theme .\artifacts\Themes\vccom\default -Recurse -Force
Compress-Archive -Path .\artifacts\Themes\vccom -DestinationPath .\artifacts\themewithpath.zip -Force

# $SourceDir = "${env:WORKSPACE}\artifacts"

# # Upload Zip File to Azure
# $ConnectionString = "DefaultEndpointsProtocol=https;AccountName={0};AccountKey={1};EndpointSuffix=core.windows.net"

# $ConnectionString = $ConnectionString -f $AzureBlobName, $AzureBlobKey

# $BlobContext = New-AzureStorageContext -ConnectionString $ConnectionString

# $Now = Get-Date -format yyyyMMdd-HHmmss
# $DestContainer = $StoreName + "-" + $Now

# $ApplicationID ="${env:AzureAppID}"
# $APIKey = ConvertTo-SecureString "${env:AzureAPIKey}" -AsPlainText -Force
# $psCred = New-Object System.Management.Automation.PSCredential($ApplicationID, $APIKey)
# $TenantID = "${env:AzureTenantID}"

# Add-AzureRmAccount -Credential $psCred -TenantId $TenantID -ServicePrincipal -Subscription $SubscriptionID

# $DestWebAppName = $WebAppName
# $DestResourceGroupName = $ResourceGroupName

# Write-Host "Stop $DestWebAppName"
# Stop-AzureRmWebApp -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName

# New-AzureStorageContainer -Name $DestContainer -Context $BlobContext -Permission Container
# Get-AzureStorageBlob -Container $StoreName -Context $BlobContext | Start-AzureStorageBlobCopy -DestContainer "$DestContainer" -Force


# if($null -eq $ExcludePattern)
# {
#     $ExcludePattern = "*.htm;*.html;*.md;*.page;google-tag-manager-body.liquid;google-tag-manager-head.liquid"
# }

# Write-Host "Sync $StoreName"
# $token = $env:AzureBlobToken
# & "${env:Utils}\AzCopy10\AzCopy" cp $SourceDir/* https://$($AzureBlobName).blob.core.windows.net/$StoreName$token --recursive --exclude-pattern="$ExcludePattern" --overwrite true #--delete-destination=true
# if($LASTEXITCODE -ne 0)
# {
#     exit 1
# }

# Write-Host "Start $DestWebAppName"
# Start-AzureRmWebApp -ResourceGroupName $DestResourceGroupName -Name $DestWebAppName
