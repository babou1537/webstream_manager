@echo off
REM Variables (ajuste ou supprime selon besoin prod)
set WSM_HTTP_LOG=1

REM Par défaut le serveur n'écoute que sur cette machine (127.0.0.1).
REM Pour l'ouvrir au réseau, décommenter les 2 lignes suivantes (mot de passe obligatoire pour l'accès distant) :
REM set HOST=0.0.0.0
REM set ADMIN_PASSWORD=choisis-un-mot-de-passe

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