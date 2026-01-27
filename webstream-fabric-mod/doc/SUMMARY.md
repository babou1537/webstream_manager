# 🎮 WebStream Manager Mod - Récapitulatif complet

## ✅ Ce qui a été créé

### 📦 Structure du mod Fabric
```
webstream-fabric-mod/
├── build.gradle                    ✅ Configuration Gradle
├── gradle.properties               ✅ Propriétés du projet
├── settings.gradle                 ✅ Configuration Gradle
├── LICENSE                         ✅ Licence MIT
├── README.md                       ✅ Documentation principale
├── BUILD.md                        ✅ Guide de compilation
├── USAGE.md                        ✅ Guide d'utilisation complet
├── ARCHITECTURE.md                 ✅ Documentation technique
├── .gitignore                      ✅ Configuration Git
│
├── src/main/java/com/babou/webstream/
│   ├── WebStreamMod.java           ✅ Point d'entrée du mod
│   ├── WebStreamServer.java        ✅ Gestion du serveur Node.js
│   ├── config/
│   │   └── WebStreamConfig.java    ✅ Gestion de la configuration
│   ├── client/
│   │   └── WebStreamClient.java    ✅ Logique client (keybinds)
│   └── mixin/
│       └── PauseScreenMixin.java   ✅ Injection bouton menu pause
│
├── src/main/resources/
│   ├── fabric.mod.json             ✅ Métadonnées Fabric
│   ├── webstream.mixins.json       ✅ Configuration Mixins
│   ├── assets/webstream/lang/
│   │   ├── fr_fr.json              ✅ Traductions françaises
│   │   └── en_us.json              ✅ Traductions anglaises
│   │
│   └── webstream-node/             ✅ Application Node.js complète
│       ├── package.json            ✅ Dépendances npm
│       ├── src/
│       │   ├── server.js           ✅ Serveur Express
│       │   ├── store.js            ✅ Accès base de données
│       │   ├── db/
│       │   │   ├── migrate.js      ✅ Script de migration
│       │   │   ├── optimize.sql    ✅ Script d'optimisation
│       │   │   └── webstream.db    ✅ Base SQLite
│       │   ├── routes/
│       │   │   ├── index.js        ✅ Route principale
│       │   │   ├── families.js     ✅ Gestion familles
│       │   │   ├── library.js      ✅ Gestion bibliothèque
│       │   │   └── screens.js      ✅ Gestion écrans
│       │   ├── views/
│       │   │   ├── layout.ejs      ✅ Template principal
│       │   │   ├── 404.ejs         ✅ Page 404
│       │   │   └── partials/       ✅ Vues partielles
│       │   └── static/
│       │       ├── *.js            ✅ Scripts client
│       │       ├── *.css           ✅ Styles
│       │       └── images/         ✅ Assets
│       └── storage/library/        ✅ Images de démonstration
```

## 🎯 Fonctionnalités implémentées

### ✅ Gestion automatique du serveur Node.js
- [x] Détection de Node.js au démarrage
- [x] Message d'erreur clair si Node.js absent
- [x] Extraction de l'application Node.js depuis le JAR
- [x] Installation automatique des dépendances (npm install)
- [x] Démarrage automatique avec Minecraft
- [x] Arrêt propre à la fermeture de Minecraft
- [x] Logs intégrés dans les logs Minecraft (préfixe `[WebStream]`)
- [x] Gestion des erreurs et redémarrage

### ✅ Configuration flexible
- [x] Fichier `config/webstream.json`
- [x] Activation/désactivation du mod
- [x] Port personnalisable (défaut: 8282)
- [x] Auto-ouverture du navigateur
- [x] Raccourci clavier configurable
- [x] Modificateur Ctrl optionnel

### ✅ Interface in-game
- [x] Bouton "📺 WebStream" dans le menu pause (ESC)
- [x] Raccourci clavier Ctrl+W (configurable)
- [x] Ouverture automatique du navigateur
- [x] Feedback visuel clair

### ✅ Application web Node.js/Express
- [x] Interface web moderne et responsive
- [x] Gestion des écrans (CRUD complet)
- [x] Gestion des familles
- [x] Bibliothèque d'images avec upload
- [x] Assignation de contenu aux écrans
- [x] URLs fixes pour chaque écran (ex: `/ecran1.png`)

### ✅ Base de données SQLite
- [x] Schéma optimisé avec index
- [x] Relations entre tables
- [x] Support des transactions
- [x] Script d'optimisation fourni
- [x] Mode WAL pour performances

### ✅ Documentation complète
- [x] README principal avec installation
- [x] Guide de compilation (BUILD.md)
- [x] Guide d'utilisation détaillé (USAGE.md)
- [x] Documentation technique (ARCHITECTURE.md)
- [x] Commentaires dans le code
- [x] Traductions FR/EN

## 🚀 Comment utiliser

### Installation

1. **Prérequis**
   ```bash
   # Installer Node.js LTS
   https://nodejs.org
   
   # Vérifier l'installation
   node --version
   npm --version
   ```

2. **Installer le mod**
   ```
   1. Copier webstream-mod-1.0.0.jar dans .minecraft/mods/
   2. S'assurer que Fabric Loader 0.15.11+ est installé
   3. S'assurer que Fabric API 0.92.2+ est installé
   ```

3. **Lancer Minecraft**
   ```
   Le serveur Node.js démarre automatiquement
   Vérifier les logs: [WebStream] Server started on http://localhost:8282
   ```

### Compilation

```bash
cd webstream-fabric-mod

# Windows
gradlew build

# Linux/Mac
./gradlew build

# Le JAR sera dans build/libs/webstream-mod-1.0.0.jar
```

### Configuration

Fichier `config/webstream.json` :
```json
{
  "enabled": true,
  "port": 8282,
  "autoOpenBrowser": false,
  "keybind": "W",
  "useCtrlModifier": true
}
```

### Utilisation in-game

1. **Ouvrir l'interface**
   - Méthode 1: Appuyer sur `ESC` → Cliquer "📺 WebStream"
   - Méthode 2: Appuyer sur `Ctrl + W`

2. **Gérer les écrans**
   - Créer des familles (ex: "Métro", "Publicités")
   - Uploader des images
   - Créer des écrans avec références uniques
   - Assigner du contenu aux écrans

3. **Utiliser les écrans dans Minecraft**
   ```
   http://localhost:8282/metro-station-1.png
   http://localhost:8282/pub-cinema.png
   etc.
   ```

## 📊 Architecture technique

### Composants Java (Fabric)

| Classe | Rôle | Responsabilité |
|--------|------|----------------|
| `WebStreamMod` | Point d'entrée | Initialisation, lifecycle |
| `WebStreamServer` | Gestion Node.js | Démarrage/arrêt du serveur |
| `WebStreamConfig` | Configuration | Chargement/sauvegarde config |
| `WebStreamClient` | Client Fabric | Keybinds, ouverture navigateur |
| `PauseScreenMixin` | Injection UI | Ajout bouton menu pause |

### Composants Node.js (Express)

| Fichier | Rôle | Responsabilité |
|---------|------|----------------|
| `server.js` | Serveur Express | Routes, middleware, static |
| `store.js` | Accès données | Interface avec SQLite |
| `routes/*.js` | Routes API | CRUD screens/families/library |
| `views/*.ejs` | Templates | Interface web |
| `static/*.js` | Scripts client | Interactivité, upload |

### Base de données

```sql
-- Table families
CREATE TABLE families (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE
);

-- Table screens
CREATE TABLE screens (
    ref TEXT PRIMARY KEY,
    family_id INTEGER,
    content TEXT,
    width INTEGER DEFAULT 1920,
    height INTEGER DEFAULT 1080,
    FOREIGN KEY (family_id) REFERENCES families(id)
);

-- Index optimisés
CREATE INDEX idx_screens_family ON screens(family_id);
CREATE INDEX idx_screens_content ON screens(content);
```

## 🔧 Développement

### Environnement de développement

```bash
# Configurer l'IDE
gradlew idea      # IntelliJ IDEA
gradlew eclipse   # Eclipse

# Lancer en mode dev
gradlew runClient

# Nettoyer
gradlew clean
```

### Modifier l'application Node.js

Les fichiers sont dans `src/main/resources/webstream-node/`

Après modification :
1. Supprimer `config/webstream/` (pour forcer réextraction)
2. Recompiler le mod
3. Relancer Minecraft

### Ajouter une fonctionnalité

1. **Côté Java** : Modifier les classes dans `src/main/java/`
2. **Côté Node.js** : Modifier les fichiers dans `src/main/resources/webstream-node/`
3. Recompiler et tester

## 🐛 Dépannage

### Node.js non détecté
```
Erreur: [WebStream] Node.js NOT FOUND in PATH!

Solution:
1. Installer Node.js depuis https://nodejs.org
2. Redémarrer l'ordinateur
3. Vérifier: node --version
```

### Port déjà utilisé
```
Erreur: Address already in use: bind

Solution:
1. Modifier config/webstream.json
2. Changer "port": 8282 → "port": 8283
3. Redémarrer Minecraft
```

### npm install échoue
```
Erreur: npm install failed

Solution:
1. Vérifier la connexion internet
2. Supprimer config/webstream/node_modules/
3. Relancer Minecraft
```

## 📝 Notes importantes

### ⚠️ Dépendance Node.js
Le mod **nécessite Node.js installé** sur le système. Il ne fonctionne pas sans.

### 📁 Sauvegarde des données
Toutes les données sont dans `config/webstream/` :
- `src/db/webstream.db` : Base de données
- `storage/library/` : Images

Pour sauvegarder, copier tout le dossier `config/webstream/`.

### 🌐 Accès réseau
Le serveur écoute sur `localhost` uniquement. Pour accès réseau :
1. Modifier `server.js` : `app.listen(PORT, '0.0.0.0')`
2. Ouvrir le port dans le pare-feu
3. Utiliser l'IP locale du PC

### 🔒 Sécurité
- Pas d'authentification (accès local uniquement)
- Validation des entrées côté serveur
- Protection injection SQL (paramètres préparés)
- Validation des fichiers uploadés

## 🎓 Ressources

### Documentation
- [README.md](README.md) - Documentation principale
- [BUILD.md](BUILD.md) - Guide de compilation
- [USAGE.md](USAGE.md) - Guide d'utilisation complet
- [ARCHITECTURE.md](ARCHITECTURE.md) - Documentation technique

### Liens utiles
- Node.js : https://nodejs.org
- Fabric : https://fabricmc.net
- Express.js : https://expressjs.com
- SQLite : https://www.sqlite.org

## 🎉 Résultat final

Vous disposez maintenant d'un **mod Fabric complet** qui :

1. ✅ S'intègre parfaitement à Minecraft 1.20.4
2. ✅ Gère automatiquement un serveur web Node.js
3. ✅ Fournit une interface web moderne pour gérer les écrans
4. ✅ Stocke tout dans SQLite de manière optimisée
5. ✅ Offre une expérience utilisateur fluide (bouton + raccourci)
6. ✅ Est entièrement documenté et maintenable

**Le mod est prêt à être compilé et utilisé !** 🚀

## 📞 Support

Pour obtenir de l'aide :
1. Consulter USAGE.md pour les questions d'utilisation
2. Consulter ARCHITECTURE.md pour les questions techniques
3. Vérifier les logs Minecraft (préfixe [WebStream])
4. Créer une issue sur GitHub

---

**Bon développement et bon WebStreaming ! 🎮📺**

