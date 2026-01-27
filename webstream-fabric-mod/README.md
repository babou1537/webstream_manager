# 🎮 WebStream Manager - Mod Fabric pour Minecraft 1.20.4

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.4-green)](https://www.minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-0.15.11-orange)](https://fabricmc.net)
[![Node.js](https://img.shields.io/badge/Node.js-LTS-brightgreen)](https://nodejs.org)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

> **⚡ Nouveau ?** Consultez [START_HERE.md](START_HERE.md) pour démarrer rapidement !  
> **🐛 Problème résolu ?** Voir [FIXED.md](FIXED.md) pour la résolution du bug settings.gradle

---

## 📋 Description

**WebStream Manager** est un mod Fabric qui intègre un serveur web Node.js/Express dans Minecraft pour gérer des écrans virtuels. Créez, gérez et affichez des images dynamiques via des URLs fixes.

### ✨ Fonctionnalités principales

- 📺 **Gestion d'écrans virtuels** avec URLs fixes (ex: `http://localhost:8282/ecran1.png`)
- 🖼️ **Bibliothèque d'images** avec upload et gestion
- 👨‍👩‍👧‍👦 **Organisation par familles** (Métro, Publicités, Musée, etc.)
- 🔄 **Mise à jour en temps réel** du contenu des écrans
- 🎨 **Interface web moderne** et responsive
- 🎮 **Intégration in-game** (bouton menu pause + raccourci Ctrl+W)
- 🚀 **Démarrage automatique** du serveur avec Minecraft
- 💾 **Base de données SQLite** optimisée

---

## 🚀 Installation

### Prérequis

- ✅ **Minecraft 1.20.4**
- ✅ **Fabric Loader 0.15.11+**
- ✅ **Fabric API 0.92.2+**
- ✅ **Node.js LTS** ([Télécharger](https://nodejs.org))
- ✅ **Java 17+**

### Installation rapide

1. **Installer Node.js**
   ```
   https://nodejs.org → Télécharger la version LTS
   ```

2. **Installer le mod**
   ```
   Copier webstream-mod-1.0.0.jar dans .minecraft/mods/
   ```

3. **Lancer Minecraft**
   ```
   Le serveur démarre automatiquement sur http://localhost:8282
   ```

---

## 🎮 Utilisation

### Accéder à l'interface

**Méthode 1 : Menu pause**
- Appuyer sur `ESC` → Cliquer sur **"📺 WebStream"**

**Méthode 2 : Raccourci clavier**
- Appuyer sur `Ctrl + W`

**Méthode 3 : Navigateur**
- Ouvrir `http://localhost:8282`

### Workflow typique

1. **Créer une famille** (ex: "Métro")
2. **Uploader des images** dans la bibliothèque
3. **Créer des écrans** (ex: `metro-station-1`)
4. **Assigner du contenu** aux écrans
5. **Utiliser in-game** : `http://localhost:8282/metro-station-1.png`

---

## ⚙️ Configuration

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

📖 **Plus d'exemples :** [doc/CONFIG_EXAMPLES.md](doc/CONFIG_EXAMPLES.md)

---

## 🛠️ Développement

### Structure du projet

```
webstream_manager/          # Application Node.js (DÉVELOPPEZ ICI)
└── webstream-fabric-mod/   # Mod Fabric (COMPILEZ ICI)
```

### Développer l'application Node.js

```bash
cd webstream_manager
npm install
npm run dev
# L'application démarre sur http://localhost:8282
```

### Compiler le mod

```bash
cd webstream-fabric-mod
gradlew build
# Le JAR sera dans build/libs/webstream-mod-1.0.0.jar
```

**Note importante :** La synchronisation entre les deux projets est **automatique** lors du build.

📖 **Plus d'infos :** [SYNC.md](SYNC.md)

---

## 📚 Documentation complète

| Document | Description |
|----------|-------------|
| **[START_HERE.md](START_HERE.md)** | 🚀 Guide de démarrage rapide |
| **[RECAP_COMPLET.md](RECAP_COMPLET.md)** | 📋 Récapitulatif complet |
| **[FIXED.md](FIXED.md)** | 🐛 Résolution du problème settings.gradle |
| **[SYNC.md](SYNC.md)** | 🔄 Synchronisation des projets |
| **[doc/USAGE.md](doc/USAGE.md)** | 📖 Guide d'utilisation détaillé |
| **[doc/BUILD.md](doc/BUILD.md)** | 🔨 Guide de compilation |
| **[doc/ARCHITECTURE.md](doc/ARCHITECTURE.md)** | 🏗️ Documentation technique |
| **[doc/CONFIG_EXAMPLES.md](doc/CONFIG_EXAMPLES.md)** | ⚙️ Exemples de configuration |
| **[doc/SUMMARY.md](doc/SUMMARY.md)** | 📊 Résumé des fonctionnalités |

---

## 🎯 FAQ

### ❓ Quelle est la différence entre `webstream_manager` et `webstream-fabric-mod` ?

- **`webstream_manager/`** → Projet Node.js pour le développement
- **`webstream-fabric-mod/`** → Mod Fabric pour Minecraft (build)

**Workflow :** Développez dans `webstream_manager/`, compilez dans `webstream-fabric-mod/`

📖 **Plus d'infos :** [SYNC.md](SYNC.md)

### ❓ Puis-je déplacer le mod ailleurs ?

- **Le JAR compilé** (`build/libs/*.jar`) → ✅ Totalement indépendant
- **Le dossier sources** → ⚠️ Garder avec `webstream_manager/` pour la synchronisation

📖 **Plus d'infos :** [START_HERE.md](START_HERE.md)

### ❓ Node.js est-il embarqué dans le mod ?

Non, **Node.js doit être installé** sur le système. Le mod détecte Node.js au démarrage et affiche un message clair s'il est absent.

---

## 🐛 Dépannage

### Node.js non détecté

```
Erreur: [WebStream] Node.js NOT FOUND in PATH!

Solution:
1. Installer Node.js depuis https://nodejs.org
2. Redémarrer l'ordinateur
3. Vérifier : node --version
```

### Erreur settings.gradle

```
Erreur: Could not compile settings file

✅ Problème corrigé ! Voir FIXED.md
```

### Port déjà utilisé

```
Solution: Modifier config/webstream.json
"port": 8282 → "port": 8283
```

📖 **Plus de solutions :** [doc/USAGE.md](doc/USAGE.md#dépannage)

---

## 🔗 Technologies utilisées

### Côté Java
- Fabric Loader 0.15.11
- Fabric API 0.92.2
- Cloth Config 13.0.121 (optionnel)

### Côté Node.js
- Express 4.19.2
- EJS 3.1.9
- SQLite 5.1.1
- Multer 1.4.5
- Sharp 0.33.2

---

## 📄 Licence

MIT License - Voir [LICENSE](LICENSE)

---

## 🙏 Remerciements

- **Fabric Team** - Loader et API
- **Node.js** - Runtime JavaScript
- **Express.js** - Framework web
- **SQLite** - Base de données

---

## 📧 Contact

**Auteur :** babou1537  
**GitHub :** [WebStream Manager](https://github.com/babou1537/webstream_manager)

---

**⭐ Si ce mod vous plaît, n'hésitez pas à mettre une étoile sur GitHub !**

---

## 🎉 Résumé rapide

**Pour développer :**
```bash
cd webstream_manager && npm run dev
```

**Pour compiler :**
```bash
cd webstream-fabric-mod && gradlew build
```

**Pour utiliser :**
```
Copier le JAR dans .minecraft/mods/
Lancer Minecraft → Ctrl+W
```

**C'est tout ! 🚀**

