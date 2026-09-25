@echo off
setlocal
set "PORT=8080"
set "BIND_ADDRESS=127.0.0.1"
java -Xmx256m "%~dp0HostedRelayServer.java"
if errorlevel 1 pause
