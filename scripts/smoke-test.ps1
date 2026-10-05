param([string]$BaseUrl = "http://localhost:8080", [string]$AdminTokenFile = "")
$ErrorActionPreference = "Stop"
$PSDefaultParameterValues["Invoke-RestMethod:TimeoutSec"] = 20
$PSDefaultParameterValues["Invoke-WebRequest:TimeoutSec"] = 20
$api = "$BaseUrl/api/v1"
function Assert-True($Value, $Message) {
    if (-not $Value) { throw $Message }
}
Write-Output "Checking health and metrics..."
$health = Invoke-RestMethod "$api/actuator/health"
Assert-True ($health.status -eq "UP") "Health must be UP"
$anonymousMetrics = Invoke-WebRequest "$api/actuator/prometheus" -SkipHttpErrorCheck
Assert-True ($anonymousMetrics.StatusCode -eq 401) "Anonymous metrics must be rejected"
if ($AdminTokenFile) {
    $adminToken = (Get-Content -LiteralPath $AdminTokenFile -Raw).Trim()
    $metrics = Invoke-WebRequest "$api/actuator/prometheus" -Headers @{Authorization="Bearer $adminToken"}
    Assert-True ($metrics.Content -match "ecom_orders_total") "Business metrics missing"
} else {
    Write-Output "SKIP: authenticated metrics scrape (no AdminTokenFile supplied)."
}
$catalog = Invoke-RestMethod "$api/products"
Assert-True $catalog.success "Product listing failed"

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
$phone = "0" + $suffix.Substring($suffix.Length - 9)
$password = "Local-smoke-test-$suffix!"
$registration = @{
    fullname = "Upgrade verification"
    phone_number = $phone
    email = "upgrade-$suffix@example.test"
    password = $password
    retype_password = $password
    role_id = 1
} | ConvertTo-Json
Write-Output "Checking registration and JWT..."
$registered = Invoke-RestMethod "$api/users/register" -Method Post -ContentType "application/json" -Body $registration
Assert-True $registered.success "Registration failed"

$loginBody = @{ phone_number=$phone; password=$password } | ConvertTo-Json
$loginResponse = Invoke-WebRequest "$api/users/login" -Method Post -ContentType "application/json" -Body $loginBody
$login = $loginResponse.Content | ConvertFrom-Json
Assert-True $login.success "Login failed"
$details = Invoke-RestMethod "$api/users/details" -Method Post -Headers @{Authorization="Bearer $($login.data.token)"}
Assert-True $details.success "JWT authentication failed"

$buyerMetrics = Invoke-WebRequest "$api/actuator/prometheus" -Headers @{Authorization="Bearer $($login.data.token)"} -SkipHttpErrorCheck
Assert-True ($buyerMetrics.StatusCode -eq 403) "Buyer must not read metrics"
$refreshBody = @{refreshToken=$login.data.refresh_token} | ConvertTo-Json
$rotated = Invoke-RestMethod "$api/users/refreshToken" -Method Post -ContentType "application/json" -Body $refreshBody
Assert-True ($rotated.success -and $rotated.data.refresh_token -ne $login.data.refresh_token) "Refresh rotation failed"
$replay = Invoke-WebRequest "$api/users/refreshToken" -Method Post -ContentType "application/json" -Body $refreshBody -SkipHttpErrorCheck
Assert-True ($replay.StatusCode -eq 401) "Used refresh token must be rejected"
Write-Output "Checking rate limit..."
$remaining = [int]($loginResponse.Headers["X-RateLimit-Remaining"] | Select-Object -First 1)
for ($i = 0; $i -lt $remaining; $i++) {
    $response = Invoke-WebRequest "$api/users/login" -Method Post -ContentType "application/json" -Body "{}" -SkipHttpErrorCheck
    Assert-True ($response.StatusCode -ne 429) "Quota depleted too early"
}
$limited = Invoke-WebRequest "$api/users/login" -Method Post -ContentType "application/json" -Body "{}" -SkipHttpErrorCheck
Assert-True ($limited.StatusCode -eq 429) "Expected rate limit rejection"
Assert-True ([int]($limited.Headers["Retry-After"] | Select-Object -First 1) -gt 0) "Missing retry delay"
Write-Output "PASS: health, metrics access control, public catalog, registration, login, JWT, refresh rotation/replay and Redis rate limiting."
