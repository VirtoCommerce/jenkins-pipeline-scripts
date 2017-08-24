. .\Tools.ps1

$headers = @{}
$apiurl = 'http://localhost/admin'
$notificationId = "7a4284ed-9002-4bb6-80c8-c7ad59656acb"
$apikey = 'a348fa7508d342f6a32f8bf6c6681a2a'

$install = Invoke-RestMethod "$apiurl/api/platform/modules/autoinstall" -Method Post -ErrorAction Stop
Write-Output $install
$notificationId = $install.id

$JSON = @"
{"Ids":["$notificationId"],"start":0,"count":1}     
"@

Write-Output $JSON
$bytes = [Text.Encoding]::UTF8.GetBytes($JSON)


$appId = '27e0d789f12641049bd0e939185b4fd2'
$secret = '34f0a3c12c9dbb59b63b5fece955b7b2b9a3b20f84370cba1524dd5c53503a2e2cb733536ecf7ea1e77319a47084a3a2c9d94d36069a432ecc73b72aeba6ea78'

$timestampString = [System.DateTime]::UtcNow.ToString("o", [System.Globalization.CultureInfo]::InvariantCulture)
$hmacsha = New-Object System.Security.Cryptography.HMACSHA256
$hmacsha.key = $secret | convert-fromhex

$signature = $hmacsha.ComputeHash([Text.Encoding]::UTF8.GetBytes("$appId&$timestampString"))
$signature = -join ($signature | % {“{0:X2}” -f $_})
$headerValue = "HMACSHA256 $appId;$timestampString;$signature"

$headers.Add("Authorization", $headerValue)
Invoke-RestMethod "$apiurl/api/platform/pushnotifications" -Body $JSON -Method Post -ContentType "application/json" -Headers $headers