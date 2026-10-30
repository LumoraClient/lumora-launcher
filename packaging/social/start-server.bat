@echo off
rem Lumora Social server (friends, groups, chat). Anleitung: SOCIAL.md
rem Optionen: --port 25600  --bind 0.0.0.0  --data lumora-social-data  --tls-keystore server.p12
cd /d "%~dp0"
set "JAVA=java"
if exist "%~dp0..\LumoraLauncher\runtime\bin\java.exe" set "JAVA=%~dp0..\LumoraLauncher\runtime\bin\java.exe"
"%JAVA%" -jar "%~dp0lumora-social-server.jar" %*
pause
