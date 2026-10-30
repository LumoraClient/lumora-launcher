@echo off
rem Removes the Lumora launcher program files and shortcuts.
rem Game data, settings and the stored login in %APPDATA%\LumoraClient are kept; delete that folder manually
rem if you want to remove everything.
setlocal
set "DEST=%LOCALAPPDATA%\Programs\LumoraClient"
choice /M "Lumora Client deinstallieren (Spielstaende in %APPDATA%\LumoraClient bleiben erhalten)"
if errorlevel 2 exit /b 0
powershell.exe -NoProfile -NonInteractive -Command ^
  "foreach ($p in @([Environment]::GetFolderPath('Programs'), [Environment]::GetFolderPath('Desktop'))) { Remove-Item -LiteralPath (Join-Path $p 'Lumora Client.lnk') -ErrorAction SilentlyContinue }"
echo Lumora Client wird entfernt.
rem This script lives inside DEST: leave the folder, end the batch context, then delete everything.
cd /d "%TEMP%"
(goto) 2>nul & rmdir /s /q "%DEST%"
