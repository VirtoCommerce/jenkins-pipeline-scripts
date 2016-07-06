#
# This script will post message to twitter account
#

Param(  
  	[parameter(Mandatory=$true)]
        $status
     )

Import-Module "$PSScriptRoot\InvokeTwitterAPIs.psm1"
$OAuth = @{'ApiKey' = '$env:twitter_apikey'; 'ApiSecret' = '$env:twitter_apisecret';'AccessToken' = '$env:twitter_oauth_token';'AccessTokenSecret' = '$env:twitter_oauth_token_secret'}
Invoke-TwitterRestMethod -ResourceURL 'https://api.twitter.com/1.1/statuses/update.json' -RestVerb 'POST' -Parameters @{'status'=$status} -OAuthSettings $OAuth
