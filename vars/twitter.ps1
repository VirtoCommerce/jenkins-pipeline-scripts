#
# This script will post message to twitter account
#

Param(  
  	[parameter(Mandatory=$true)]
        $status
     )

Import-Module "$PSScriptRoot\InvokeTwitterAPIs.psm1"
$apikey = "$env:twitter_apikey"
$apisecret = "$env:twitter_apisecret"
$access_token = "$env:twitter_oauth_token"
$access_token_secret = "$env:twitter_oauth_token_secret"

$OAuth = @{'ApiKey' = $apikey; 'ApiSecret' = $apisecret;'AccessToken' = $access_token;'AccessTokenSecret' = $access_token_secret}
Invoke-TwitterRestMethod -ResourceURL 'https://api.twitter.com/1.1/statuses/update.json' -RestVerb 'POST' -Parameters @{'status'=$status} -OAuthSettings $OAuth
