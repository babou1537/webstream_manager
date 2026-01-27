# Exemples de configuration - WebStream Manager

## Configuration basique (défaut)

```json
{
  "enabled": true,
  "port": 8282,
  "autoOpenBrowser": false,
  "keybind": "W",
  "useCtrlModifier": true
}
```

## Configuration pour serveur dédié

Si vous utilisez le mod sur un serveur Minecraft dédié :

```json
{
  "enabled": true,
  "port": 8282,
  "autoOpenBrowser": false,
  "keybind": "W",
  "useCtrlModifier": true
}
```

Note : Le navigateur ne s'ouvrira pas automatiquement sur un serveur. Accédez manuellement à `http://ip-du-serveur:8282`

## Configuration multi-instance

Si vous lancez plusieurs instances de Minecraft en même temps :

**Instance 1** (`config/webstream.json`)
```json
{
  "enabled": true,
  "port": 8282,
  "autoOpenBrowser": false,
  "keybind": "W",
  "useCtrlModifier": true
}
```

**Instance 2** (`config/webstream.json`)
```json
{
  "enabled": true,
  "port": 8283,
  "autoOpenBrowser": false,
  "keybind": "W",
  "useCtrlModifier": true
}
```

**Instance 3** (`config/webstream.json`)
```json
{
  "enabled": true,
  "port": 8284,
  "autoOpenBrowser": false,
  "keybind": "W",
  "useCtrlModifier": true
}
```

## Configuration avec auto-ouverture navigateur

Pour que le navigateur s'ouvre automatiquement au démarrage :

```json
{
  "enabled": true,
  "port": 8282,
  "autoOpenBrowser": true,
  "keybind": "W",
  "useCtrlModifier": true
}
```

## Configuration raccourci simplifié

Pour ouvrir avec juste `W` (sans Ctrl) :

```json
{
  "enabled": true,
  "port": 8282,
  "autoOpenBrowser": false,
  "keybind": "W",
  "useCtrlModifier": false
}
```

⚠️ **Attention** : Sans modificateur, la touche peut interférer avec le jeu normal.

## Configuration personnalisée complète

Exemple avec tous les paramètres modifiés :

```json
{
  "enabled": true,
  "port": 9000,
  "autoOpenBrowser": true,
  "keybind": "M",
  "useCtrlModifier": true
}
```

Dans cet exemple :
- Port personnalisé : 9000
- Navigateur s'ouvre automatiquement
- Raccourci : Ctrl+M au lieu de Ctrl+W

## Configuration désactivée

Pour désactiver temporairement le mod sans le supprimer :

```json
{
  "enabled": false,
  "port": 8282,
  "autoOpenBrowser": false,
  "keybind": "W",
  "useCtrlModifier": true
}
```

Le mod ne démarrera pas le serveur Node.js.

## Configuration pour développement

Configuration idéale pour le développement :

```json
{
  "enabled": true,
  "port": 3000,
  "autoOpenBrowser": true,
  "keybind": "O",
  "useCtrlModifier": true
}
```

Port 3000 est standard pour le développement web.

## Configuration réseau local

Pour accéder depuis d'autres ordinateurs du réseau local :

1. **Modifier `server.js`** dans le mod (avant compilation) :
   ```javascript
   app.listen(PORT, '0.0.0.0', () => {
       console.log(`WebStream Manager running on http://0.0.0.0:${PORT}`);
   });
   ```

2. **Configuration** :
   ```json
   {
     "enabled": true,
     "port": 8282,
     "autoOpenBrowser": false,
     "keybind": "W",
     "useCtrlModifier": true
   }
   ```

3. **Accéder depuis un autre PC** :
   ```
   http://192.168.1.XXX:8282
   ```

⚠️ **Attention** : Ouvrir le port 8282 dans le pare-feu Windows.

## Variables d'environnement (avancé)

Le serveur Node.js peut aussi être configuré via variables d'environnement :

**Modifier `WebStreamServer.java`** :
```java
pb.environment().put("PORT", String.valueOf(WebStreamMod.CONFIG.port));
pb.environment().put("NODE_ENV", "production");
pb.environment().put("DEBUG", "false");  // Activer pour plus de logs
```

## Exemple de script de configuration automatique

Créer un script pour générer la configuration :

**Windows** (`create-config.bat`)
```batch
@echo off
echo {"enabled": true, "port": 8282, "autoOpenBrowser": false, "keybind": "W", "useCtrlModifier": true} > config\webstream.json
echo Configuration created!
```

**Linux/Mac** (`create-config.sh`)
```bash
#!/bin/bash
cat > config/webstream.json << EOF
{
  "enabled": true,
  "port": 8282,
  "autoOpenBrowser": false,
  "keybind": "W",
  "useCtrlModifier": true
}
EOF
echo "Configuration created!"
```

## Validation de la configuration

Pour vérifier que la configuration est valide, utilisez un validateur JSON en ligne :
- https://jsonlint.com
- https://jsonformatter.curiousconcept.com

Ou en ligne de commande :
```bash
# Python
python -m json.tool config/webstream.json

# Node.js
node -e "console.log(JSON.parse(require('fs').readFileSync('config/webstream.json')))"
```

## Réinitialiser la configuration

Pour revenir à la configuration par défaut :

1. Fermer Minecraft
2. Supprimer `config/webstream.json`
3. Relancer Minecraft
4. Le fichier sera recréé avec les valeurs par défaut

## Configuration par profil

Si vous utilisez plusieurs profils Minecraft :

**Profil "Créatif"** - Auto-ouverture activée
```json
{
  "enabled": true,
  "port": 8282,
  "autoOpenBrowser": true,
  "keybind": "W",
  "useCtrlModifier": true
}
```

**Profil "Survie"** - Désactivé
```json
{
  "enabled": false,
  "port": 8282,
  "autoOpenBrowser": false,
  "keybind": "W",
  "useCtrlModifier": true
}
```

Chaque profil peut avoir sa propre configuration dans son dossier `config/`.

## Troubleshooting configuration

### Configuration ignorée

**Problème** : Les changements ne sont pas pris en compte

**Solution** :
1. Vérifier la syntaxe JSON (pas de virgule à la fin)
2. Fermer complètement Minecraft
3. Relancer Minecraft

### Port déjà utilisé

**Problème** : `Address already in use`

**Solution** :
1. Changer le port dans la config
2. Ou fermer l'application qui utilise le port

**Windows** : Trouver le processus qui utilise le port
```cmd
netstat -ano | findstr :8282
taskkill /PID <PID> /F
```

**Linux/Mac** :
```bash
lsof -i :8282
kill -9 <PID>
```

### Configuration corrompe

**Problème** : Erreur au chargement de la config

**Solution** :
1. Vérifier la syntaxe JSON
2. Supprimer et relancer pour recréer
3. Vérifier les logs : `[WebStream] Failed to load config`

---

**Astuce** : Sauvegardez toujours votre configuration avant de faire des modifications !

