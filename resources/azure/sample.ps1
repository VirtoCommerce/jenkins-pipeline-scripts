. C:\Projects\GitHub\jenkins-pipeline-scripts\resources\azure\utilities.ps1

Write-Output $PSScriptRoot

$apiurl = 'http://localhost/admin'
$apiurl = 'http://ci.virtocommerce.com:8090'
$notificationId = "7a4284ed-9002-4bb6-80c8-c7ad59656acb"
$appId = '27e0d789f12641049bd0e939185b4fd2'
$secret = '34f0a3c12c9dbb59b63b5fece955b7b2b9a3b20f84370cba1524dd5c53503a2e2cb733536ecf7ea1e77319a47084a3a2c9d94d36069a432ecc73b72aeba6ea78'
$modulesStateUrl = "$apiurl/api/platform/pushnotifications"
$modulesInstallUrl = "$apiurl/api/platform/modules/autoinstall"

$install = Invoke-RestMethod $modulesInstallUrl -Method Post -ErrorAction Stop
Write-Output $install
$notificationId = $install.id
$NotificationStateJson = @"
{"Ids":["$notificationId"],"start":0}     
"@

$cycleCount = 0
try
{
      do
      {
            Start-Sleep -s 5

            $timestampString = [System.DateTime]::UtcNow.ToString("o", [System.Globalization.CultureInfo]::InvariantCulture)
            $hmacsha = New-Object System.Security.Cryptography.HMACSHA256
            $hmacsha.key = $secret | convert-fromhex
                  
            $signature = $hmacsha.ComputeHash([Text.Encoding]::UTF8.GetBytes("$appId&$timestampString"))
            $signature = -join ($signature | % {“{0:X2}” -f $_})
            $headerValue = "HMACSHA256 $appId;$timestampString;$signature"
                  
            $headers = @{}
            $headers.Add("Authorization", $headerValue)
            $moduleState = Invoke-RestMethod "$modulesStateUrl" -Body $NotificationStateJson -Method Post -ContentType "application/json" -Headers $headers
            Write-Output "Module install state: $moduleState"

            $cycleCount = $cycleCount + 1 
      }
      while ($cycleCount -lt 5)
}
catch
{
      $message = $_.Exception.Message
      Write-Output "Error: $message"
}     