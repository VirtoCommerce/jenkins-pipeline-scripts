Function convert-fromhex
{
    process
    {
        $_ -replace '^0x', '' -split "(?<=\G\w{2})(?=\w{2})" | %{ [Convert]::ToByte( $_, 16 ) }
    }
}