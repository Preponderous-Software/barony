@echo off
REM Frontend startup script for Windows
REM Note: Backend must be running first!

cd /d "%~dp0frontend"
call mvnw.cmd compile exec:java
