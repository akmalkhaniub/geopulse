param(
    [string]$User = "admin",
    [string]$Password = "demo1234",
    [string]$KeycloakUrl = "http://localhost:8180"
)

$body = @{
    client_id = "fleet-cli"
    grant_type = "password"
    username = $User
    password = $Password
}

$response = Invoke-RestMethod -Uri "$KeycloakUrl/realms/fleet/protocol/openid-connect/token" -Method Post -Body $body
$response.access_token
