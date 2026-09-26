@echo off
REM Variables (ajuste ou supprime selon besoin prod)
set WSM_HTTP_LOG=1

REM Lance le serveur (script "start" = node src/server.js)
REM start "WebStream Manager | Prod" cmd /c "npm run start"
start "WebStream Manager | Prod" cmd /k "npm run start"

REM Attente que le serveur réponde sur le port 8282
echo Démarrage du serveur, patience...
powershell -NoLogo -NoProfile -Command "for ($i=0;$i -lt 40;$i++){ try { (Invoke-WebRequest -UseBasicParsing http://localhost:8282 -TimeoutSec 1) > $null; exit 0 } catch {}; Start-Sleep -Milliseconds 500 }; exit 0"

REM Ouvre le navigateur par défaut
start "" "http://localhost:8282"
start "" "http://localhost:8100"
start "" "http://localhost:8888"