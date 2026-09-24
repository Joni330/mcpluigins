@echo off
setlocal
cd /d "%~dp0"
echo Casino-Aenderungen zu GitHub hochladen
echo.
where git >nul 2>&1
if errorlevel 1 goto missing_git
git rev-parse --is-inside-work-tree >nul 2>&1
if errorlevel 1 goto failed
set "casino_branch="
for /f "delims=" %%B in ('git symbolic-ref --quiet --short HEAD') do set "casino_branch=%%B"
if not "%casino_branch%"=="main" goto wrong_branch
git rev-parse -q --verify MERGE_HEAD >nul 2>&1
if not errorlevel 1 goto unfinished
if exist ".git\rebase-merge" goto unfinished
if exist ".git\rebase-apply" goto unfinished
if /i "%~1"=="--check-only" (
    echo Pruefung erfolgreich: Branch main, kein offener Merge oder Rebase.
    exit /b 0
)
git add --all
if errorlevel 1 goto failed
git diff --cached --quiet
if errorlevel 2 goto failed
if not errorlevel 1 goto sync
git commit -m "Casino aktualisiert"
if errorlevel 1 goto failed
:sync
git pull --rebase origin main
if errorlevel 1 goto failed
git push origin main
if errorlevel 1 goto failed
echo.
echo Fertig! Dein Projekt ist mit GitHub synchronisiert.
echo Bei neuen Commits startet GitHub den Build automatisch.
echo Fortschritt: https://github.com/Joni330/mcpluigins/actions
echo Der Minecraft-Server wird dadurch noch nicht automatisch aktualisiert.
pause
exit /b 0
:missing_git
echo Git wurde nicht gefunden. Bitte Git fuer Windows installieren.
goto stop_error
:wrong_branch
echo Abgebrochen: Diese BAT darf nur im Branch main verwendet werden.
echo Erkannter Branch: [%casino_branch%]
goto stop_error
:unfinished
echo Abgebrochen: Ein Merge oder Rebase ist noch nicht abgeschlossen.
goto stop_error
:failed
echo.
echo Fehler: Der Vorgang wurde gestoppt. Es wird kein Push erzwungen.
echo Bitte die Meldung oben kopieren oder einen Screenshot an Codex senden.
echo Bereits erstellte lokale Commits bleiben erhalten.
:stop_error
pause
exit /b 1
