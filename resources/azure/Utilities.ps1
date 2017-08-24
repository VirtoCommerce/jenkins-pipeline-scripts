Function convert-fromhex
{
    process
    {
        $_ -replace '^0x', '' -split "(?<=\G\w{2})(?=\w{2})" | ForEach-Object { [Convert]::ToByte( $_, 16 ) }
    }
}

Function Create-Authorization
{
    # constants for demo sites
    $appId = '27e0d789f12641049bd0e939185b4fd2'
    $secret = '34f0a3c12c9dbb59b63b5fece955b7b2b9a3b20f84370cba1524dd5c53503a2e2cb733536ecf7ea1e77319a47084a3a2c9d94d36069a432ecc73b72aeba6ea78'

    $timestampString = [System.DateTime]::UtcNow.ToString("o", [System.Globalization.CultureInfo]::InvariantCulture)
    $hmacsha = New-Object System.Security.Cryptography.HMACSHA256
    $hmacsha.key = $secret | convert-fromhex
          
    $signature = $hmacsha.ComputeHash([Text.Encoding]::UTF8.GetBytes("$appId&$timestampString"))
    $signature = -join ($signature | ForEach-Object {"{0:X2}" -f $_})
    $headerValue = "HMACSHA256 $appId;$timestampString;$signature"
    return $headerValue
}