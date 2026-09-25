param([ValidateRange(1,65535)][int]$Port = 8080)
$ErrorActionPreference = 'Stop'
# Loopback only: expose using an HTTPS tunnel or local reverse proxy.
$env:PORT = "$Port"
$env:BIND_ADDRESS = '127.0.0.1'
& java '-Xmx256m' (Join-Path $PSScriptRoot 'HostedRelayServer.java')
if ($LASTEXITCODE -ne 0) { throw "Relay exited with code $LASTEXITCODE. Java 21 or newer is required." }
