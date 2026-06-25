# get-tunnel-url.ps1
# Print the CURRENT Cloudflare quick-tunnel URL without restarting anything.
# Reads it live from the running cloudflared metrics endpoint, falling back to
# tunnel-url.txt. Use this whenever you need to (re)open the app on the phone.

$ErrorActionPreference = 'SilentlyContinue'

$MetricsAddr = '127.0.0.1:20241'
$RepoRoot    = Split-Path -Parent $PSScriptRoot
$UrlFile     = Join-Path $RepoRoot 'tunnel-url.txt'

$url = $null
try {
  $body = (Invoke-WebRequest "http://$MetricsAddr/quicktunnel" -UseBasicParsing -TimeoutSec 3).Content
  $m = [regex]::Match($body, '[a-z0-9-]+\.trycloudflare\.com')
  if ($m.Success) { $url = 'https://' + $m.Value }
} catch {}

if (-not $url -and (Test-Path $UrlFile)) {
  $url = (Get-Content $UrlFile -Raw).Trim()
  Write-Host "(from cache; tunnel metrics unreachable)" -ForegroundColor Yellow
}

if (-not $url) { Write-Host "No tunnel found. Run start-tunnel.ps1" -ForegroundColor Red; exit 1 }

Write-Host $url
Write-Host "QR: https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=$url" -ForegroundColor Cyan
