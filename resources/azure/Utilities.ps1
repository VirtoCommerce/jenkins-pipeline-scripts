Function convert-fromhex
{
    process
    {
        $_ -replace '^0x', '' -split "(?<=\G\w{2})(?=\w{2})" | ForEach-Object { [Convert]::ToByte( $_, 16 ) }
    }
}

Function Create-Authorization([string] $appId, [string] $secret)
{
    $timestampString = [System.DateTime]::UtcNow.ToString("o", [System.Globalization.CultureInfo]::InvariantCulture)
    $hmacsha = New-Object System.Security.Cryptography.HMACSHA256
    $hmacsha.key = $secret | convert-fromhex
          
    $signature = $hmacsha.ComputeHash([Text.Encoding]::UTF8.GetBytes("$appId&$timestampString"))
    $signature = -join ($signature | ForEach-Object {"{0:X2}" -f $_})
    $headerValue = "HMACSHA256 $appId;$timestampString;$signature"
    return $headerValue
}