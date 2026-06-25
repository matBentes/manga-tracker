# start-tunnel.ps1
# Launch a Cloudflare quick tunnel to the local app (nginx :4200) and capture the
# randomly-assigned *.trycloudflare.com URL so it is never lost.
#
# Quick-tunnel URLs change on every cloudflared restart, which breaks the phone PWA
# (the service worker is bound to whatever origin it registered under). This script:
#   1. starts cloudflared with a PINNED metrics port (so the URL is always queryable)
#   2. waits for the tunnel to come up
#   3. writes the live URL to tunnel-url.txt
#   4. prints the URL + a phone-scannable QR link
#
# Re-run get-tunnel-url.ps1 anytime to read the current URL without restarting.

$ErrorActionPreference = 'Stop'

$LocalTarget = 'http://localhost:4200'
$MetricsAddr = '127.0.0.1:20241'
$RepoRoot    = Split-Path -Parent $PSScriptRoot
$UrlFile     = Join-Path $RepoRoot 'tunnel-url.txt'
$LogFile     = Join-Path $env:TEMP 'manga-tracker-cloudflared.log'

$cf = (Get-Command cloudflared -ErrorAction SilentlyContinue).Source
if (-not $cf) { $cf = 'C:\Program Files (x86)\cloudflared\cloudflared.exe' }
if (-not (Test-Path $cf)) { throw "cloudflared not found. Install it first." }

# Kill any existing tunnel so we have a single known instance.
Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

if (Test-Path $LogFile) { Remove-Item $LogFile -Force }

Write-Host "Starting cloudflared -> $LocalTarget (metrics $MetricsAddr)..."
Start-Process -FilePath $cf `
  -ArgumentList @('tunnel','--url',$LocalTarget,'--no-autoupdate','--metrics',$MetricsAddr) `
  -RedirectStandardError $LogFile -RedirectStandardOutput "$LogFile.out" `
  -WindowStyle Hidden

# Poll the metrics endpoint for the assigned hostname (more reliable than scraping logs).
$url = $null
for ($i = 0; $i -lt 30; $i++) {
  Start-Sleep -Seconds 1
  try {
    $body = (Invoke-WebRequest "http://$MetricsAddr/quicktunnel" -UseBasicParsing -TimeoutSec 3).Content
    $m = [regex]::Match($body, '[a-z0-9-]+\.trycloudflare\.com')
    if ($m.Success) { $url = 'https://' + $m.Value; break }
  } catch {}
}

if (-not $url) { throw "Tunnel did not come up in time. Check $LogFile" }

Set-Content -Path $UrlFile -Value $url -Encoding utf8 -NoNewline
Write-Host ""
Write-Host "TUNNEL LIVE:" -ForegroundColor Green
Write-Host "  $url"
Write-Host "  (saved to $UrlFile)"
Write-Host ""
Write-Host "Scan on phone:" -ForegroundColor Cyan
Write-Host "  https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=$url"
