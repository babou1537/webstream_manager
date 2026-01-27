# Architecture Technique - WebStream Manager

## Vue d'ensemble

WebStream Manager est un mod Fabric qui intègre un serveur web Node.js/Express pour gérer des écrans virtuels dans Minecraft. Cette documentation détaille l'architecture technique du système.

## Schéma global

```
┌─────────────────────────────────────────────────────────┐
│                    Minecraft 1.20.4                      │
│  ┌────────────────────────────────────────────────────┐  │
│  │          WebStream Manager Mod (Fabric)            │  │
│  │                                                    │  │
│  │  ┌──────────────┐      ┌──────────────────────┐  │  │
│  │  │ WebStreamMod │──────│ WebStreamServer      │  │  │
│  │  │ (Main)       │      │ (Gestion Node.js)    │  │  │
│  │  └──────────────┘      └──────────────────────┘  │  │
│  │                                │                  │  │
│  │  ┌──────────────┐             │                  │  │
│  │  │ WebStream    │             │                  │  │
│  │  │ Client       │             │                  │  │
│  │  │ (UI+Keybind) │             │                  │  │
│  │  └──────────────┘             │                  │  │
│  │         │                     │                  │  │
│  │  ┌──────────────┐             │                  │  │
│  │  │ PauseScreen  │             │                  │  │
│  │  │ Mixin        │             │                  │  │
│  │  └──────────────┘             │                  │  │
│  └────────────────────────────────┼───────────────────┘  │
└────────────────────────────────────┼──────────────────────┘
                                     │
                                     │ spawn/manage
                                     ▼
                    ┌─────────────────────────────┐
                    │   Process Node.js           │
                    │                             │
                    │  ┌──────────────────────┐   │
                    │  │  Express Server      │   │
                    │  │  Port: 8282          │   │
                    │  └──────────────────────┘   │
                    │           │                 │
                    │           ├─► Routes        │
                    │           ├─► Store (DB)    │
                    │           └─► Static Files  │
                    └─────────────────────────────┘
                                     │
                    ┌────────────────┼────────────────┐
                    ▼                ▼                ▼
              ┌──────────┐    ┌──────────┐    ┌──────────┐
              │ SQLite   │    │ Storage  │    │ Views    │
              │ Database │    │ /library │    │ (EJS)    │
              └──────────┘    └──────────┘    └──────────┘
```

## Composants principaux

### 1. Mod Fabric (Java)

#### WebStreamMod.java
**Rôle** : Point d'entrée du mod
- Initialise le mod lors du chargement de Minecraft
- Charge la configuration depuis `config/webstream.json`
- Instancie et démarre `WebStreamServer`
- Enregistre un shutdown hook pour arrêt propre

**Cycle de vie** :
```
onInitialize()
  ├─► CONFIG.load()
  ├─► server = new WebStreamServer()
  ├─► server.start()
  └─► Runtime.addShutdownHook(() -> server.stop())
```

#### WebStreamServer.java
**Rôle** : Gestion du processus Node.js
- Détecte Node.js dans le PATH
- Extrait l'application Node.js depuis le JAR
- Installe les dépendances npm
- Démarre et surveille le processus Node.js
- Logs la sortie de Node.js dans les logs Minecraft

**Méthodes principales** :
```java
start()           // Démarre le serveur
stop()            // Arrête proprement le serveur
isRunning()       // Vérifie si le serveur tourne
openBrowser()     // Ouvre le navigateur web
```

**Processus de démarrage** :
```
start()
  ├─► isNodeInstalled()
  │    └─► ProcessBuilder("node", "--version")
  │
  ├─► extractNodeApp()
  │    └─► Copie webstream-node/ depuis JAR vers config/webstream/
  │
  ├─► installDependencies()
  │    └─► ProcessBuilder("npm", "install", "--production")
  │
  └─► startNodeProcess()
       └─► ProcessBuilder("node", "src/server.js")
            └─► Thread pour logger la sortie
```

#### WebStreamConfig.java
**Rôle** : Gestion de la configuration
- Charge/sauvegarde `config/webstream.json`
- Valeurs par défaut si pas de fichier
- Serialization JSON avec Gson

**Paramètres** :
```java
boolean enabled = true;          // Activer le mod
int port = 8282;                 // Port du serveur
boolean autoOpenBrowser = false; // Ouvrir navigateur au démarrage
String keybind = "W";            // Touche du raccourci
boolean useCtrlModifier = true;  // Utiliser Ctrl
```

#### WebStreamClient.java
**Rôle** : Logique côté client
- Enregistre le keybinding (Ctrl+W)
- Écoute les événements clavier
- Ouvre le navigateur via `openWebStream()`

**Event handling** :
```java
ClientTickEvents.END_CLIENT_TICK.register(client -> {
    if (openWebStreamKey.wasPressed() && ctrlPressed) {
        openWebStream();
    }
});
```

#### PauseScreenMixin.java
**Rôle** : Injection UI dans le menu pause
- Utilise Mixin pour modifier `GameMenuScreen`
- Ajoute un bouton "📺 WebStream" dans le menu ESC
- Appelle `WebStreamClient.openWebStream()` au clic

**Injection** :
```java
@Inject(method = "init", at = @At("TAIL"))
private void addWebStreamButton(CallbackInfo ci) {
    this.addDrawableChild(
        ButtonWidget.builder(...)
    );
}
```

### 2. Application Node.js (JavaScript)

#### Architecture Express

```
src/
├── server.js           # Point d'entrée Express
├── store.js            # Couche d'accès aux données
├── routes/
│   ├── index.js        # Route principale
│   ├── families.js     # CRUD familles
│   ├── library.js      # Upload & gestion images
│   └── screens.js      # CRUD écrans
├── views/
│   ├── layout.ejs      # Template principal
│   └── partials/       # Vues partielles (SPA-like)
└── static/
    └── *.js, *.css     # Assets statiques
```

#### server.js
**Rôle** : Configuration Express
- Configure EJS comme moteur de templates
- Route dynamique `/:screenRef.png` pour servir les écrans
- Middleware pour JSON et FormData
- Serveur HTTP sur port 8282

**Route principale** :
```javascript
app.get('/:screenRef.png', async (req, res) => {
    const screen = await store.getScreen(screenRef);
    res.sendFile(contentPath);
});
```

#### store.js
**Rôle** : Couche d'accès aux données SQLite
- Connexion à `src/db/webstream.db`
- Méthodes CRUD pour screens et families
- Queries asynchrones avec better-sqlite3

**API principale** :
```javascript
// Screens
getScreen(ref)
getAllScreens()
createScreen(ref, family_id, width, height)
updateScreen(ref, data)
deleteScreen(ref)

// Families
getAllFamilies()
createFamily(name)
deleteFamily(id)
```

### 3. Base de données SQLite

#### Schéma

**Table `families`**
```sql
CREATE TABLE families (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE
);
```

**Table `screens`**
```sql
CREATE TABLE screens (
    ref TEXT PRIMARY KEY,
    family_id INTEGER,
    content TEXT,
    width INTEGER DEFAULT 1920,
    height INTEGER DEFAULT 1080,
    FOREIGN KEY (family_id) REFERENCES families(id) ON DELETE SET NULL
);
```

**Index (pour performances)**
```sql
CREATE INDEX idx_screens_family ON screens(family_id);
CREATE INDEX idx_screens_content ON screens(content);
```

#### Optimisations

- **WAL Mode** : Meilleures performances en lecture/écriture concurrente
- **Cache** : 64 MB pour requêtes fréquentes
- **Index** : Sur family_id et content
- **VACUUM** : Récupération espace disque

### 4. Stockage des fichiers

```
config/webstream/
├── src/
│   ├── server.js
│   ├── store.js
│   ├── db/
│   │   └── webstream.db       # Base SQLite
│   ├── routes/
│   ├── views/
│   └── static/
├── storage/
│   └── library/                # Toutes les images
│       ├── image1.png
│       ├── image2.jpg
│       └── ...
├── package.json
└── node_modules/              # Dépendances npm
```

## Flux de données

### Création d'un écran

```
1. Utilisateur → Interface Web (formulaire)
   ↓
2. POST /screens
   ↓
3. screensRouter → Validation
   ↓
4. store.createScreen(ref, family_id, width, height)
   ↓
5. SQLite INSERT INTO screens
   ↓
6. Redirection → Interface mise à jour
```

### Affichage d'un écran

```
1. Requête → GET /metro-1.png
   ↓
2. Route dynamique /:screenRef.png
   ↓
3. store.getScreen('metro-1')
   ↓
4. SQLite SELECT * FROM screens WHERE ref = 'metro-1'
   ↓
5. Récupération du content (ex: 'metro-map.png')
   ↓
6. Lecture du fichier storage/library/metro-map.png
   ↓
7. res.sendFile() → Image servie
```

### Upload d'image

```
1. Utilisateur → Sélection fichier
   ↓
2. POST /library/upload (multipart/form-data)
   ↓
3. Multer middleware → Parsing fichier
   ↓
4. Sharp → Traitement/optimisation image
   ↓
5. Sauvegarde dans storage/library/
   ↓
6. Métadonnées stockées (optionnel)
   ↓
7. Confirmation → Interface mise à jour
```

## Communication inter-composants

### Java ↔ Node.js

**Démarrage** :
```
Java WebStreamServer
  ├─► ProcessBuilder("node", "src/server.js")
  ├─► ENV: PORT=8282
  └─► Thread: lecture stdout/stderr
```

**Logs** :
```
Node.js console.log("...")
  ↓
Process.getInputStream()
  ↓
BufferedReader
  ↓
LOGGER.info("[Node.js] ...")
```

**Arrêt** :
```
Runtime.addShutdownHook()
  ↓
server.stop()
  ↓
nodeProcess.destroy()
  ↓
waitFor(5 seconds)
  ↓
destroyForcibly() si nécessaire
```

### Client ↔ Serveur Web

**Keybind** :
```
Ctrl+W pressé
  ↓
WebStreamClient.openWebStream()
  ↓
WebStreamServer.openBrowser()
  ↓
Runtime.exec("rundll32 url.dll,FileProtocolHandler http://localhost:8282")
```

**Bouton menu** :
```
Clic bouton "📺 WebStream"
  ↓
PauseScreenMixin → lambda
  ↓
WebStreamClient.openWebStream()
  ↓
[même chemin qu'au-dessus]
```

## Sécurité

### Portée locale uniquement
- Serveur écoute sur `localhost` (127.0.0.1)
- Pas d'accès réseau externe
- Pas d'authentification nécessaire

### Validation des entrées
- SQLite avec paramètres préparés (protection injection SQL)
- Multer avec validation de type de fichier
- Slugification des références d'écrans

### Permissions fichiers
- Lecture/écriture dans `config/webstream/` uniquement
- Pas d'accès au système de fichiers global

## Performance

### Optimisations Java
- Thread séparé pour logs Node.js (non-bloquant)
- Extraction des ressources seulement au premier démarrage
- npm install seulement si node_modules absent

### Optimisations Node.js
- Cache EJS compilé
- Static files servis efficacement par Express
- SQLite avec WAL mode et cache

### Optimisations base de données
- Index sur colonnes fréquemment requêtées
- ANALYZE pour statistiques à jour
- VACUUM périodique

## Extensibilité

### Ajouter une route
1. Créer `src/routes/nouvelle-route.js`
2. Importer dans `server.js`
3. `app.use('/chemin', nouvelleRoute)`

### Ajouter une table
1. Modifier `src/db/migrate.js`
2. Ajouter méthodes dans `store.js`
3. Créer routes et vues

### Ajouter une configuration
1. Ajouter propriété dans `WebStreamConfig.java`
2. Utiliser via `WebStreamMod.CONFIG.propriété`
3. Documenter dans README

## Dépendances

### Java (Gradle)
```groovy
minecraft "com.mojang:minecraft:1.20.4"
fabric-loader "0.15.11"
fabric-api "0.92.2+1.20.4"
cloth-config "13.0.121" (optionnel)
```

### JavaScript (npm)
```json
{
  "express": "^4.19.2",
  "ejs": "^3.1.9",
  "sqlite": "^5.1.1",
  "sqlite3": "^5.1.7",
  "multer": "^1.4.5-lts.1",
  "sharp": "^0.33.2",
  "slugify": "^1.6.6",
  "morgan": "^1.10.0"
}
```

### Système
- Node.js LTS (v18+ recommandé)
- Java 17+
- SQLite 3 (intégré via sqlite3 npm)

## Logs et Debug

### Logs Minecraft
Préfixe `[WebStream]` :
```
[WebStream] Initializing...
[WebStream] Node.js detected: v20.11.0
[WebStream] Server started on http://localhost:8282
```

### Logs Node.js
Préfixe `[Node.js]` :
```
[Node.js] WebStream Manager running on http://localhost:8282
[Node.js] [SCREEN REQUEST] metro-1
```

### Debug mode
Modifier `server.js` :
```javascript
const DEBUG = true;
if (DEBUG) console.log('[DEBUG]', ...);
```

---

**Cette architecture permet une intégration transparente entre Minecraft et le serveur web, tout en maintenant une séparation claire des responsabilités.**

