@echo off
rem Lumora Client - per-user installer, run by the IExpress self-extractor from its temp folder.
rem Installs to %LOCALAPPDATA%\Programs\LumoraClient (no admin rights). User data in %APPDATA%\LumoraClient
rem (settings, login, worlds) is never touched.
setlocal
set "DEST=%LOCALAPPDATA%\Programs\LumoraClient"
set "APP=%DEST%\LumoraLauncher"

tasklist /FI "IMAGENAME eq LumoraLauncher.exe" 2>nul | find /I "LumoraLauncher.exe" >nul
if not errorlevel 1 (
  echo Lumora Launcher laeuft noch. Bitte schliessen und Setup erneut starten.
  pause
  exit /b 1
)

if exist "%APP%" rmdir /s /q "%APP%"
if not exist "%DEST%" mkdir "%DEST%"
tar -xf "%~dp0LumoraClient.zip" -C "%DEST%"
if errorlevel 1 (
  echo Entpacken fehlgeschlagen. Ist genug Speicherplatz vorhanden?
  pause
  exit /b 1
)
if not exist "%APP%\LumoraLauncher.exe" (
  echo Installation unvollstaendig: LumoraLauncher.exe fehlt.
  pause
  exit /b 1
)
copy /y "%~dp0uninstall.cmd" "%DEST%\Deinstallieren.cmd" >nul

rem LUMORA_NO_SHORTCUTS / LUMORA_NO_START: for silent installs and automated tests.
if defined LUMORA_NO_SHORTCUTS goto :noshortcuts
powershell.exe -NoProfile -NonInteractive -Command ^
  "$s = New-Object -ComObject WScript.Shell;" ^
  "foreach ($p in @([Environment]::GetFolderPath('Programs'), [Environment]::GetFolderPath('Desktop'))) {" ^
  "  $l = $s.CreateShortcut((Join-Path $p 'Lumora Client.lnk'));" ^
  "  $l.TargetPath = '%APP%\LumoraLauncher.exe'; $l.WorkingDirectory = '%APP%'; $l.Save() }"

:noshortcuts
if not defined LUMORA_NO_START start "" "%APP%\LumoraLauncher.exe"
exit /b 0
