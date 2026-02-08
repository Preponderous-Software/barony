@echo off
REM Backend startup script for Windows

cd /d "%~dp0backend"
call mvnw.cmd spring-boot:run
