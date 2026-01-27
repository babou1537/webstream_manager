# 🎮 WebStream Manager - Mod Fabric pour Minecraft 1.20.4

## 📋 Résumé

Ce mod Fabric intègre un serveur web Node.js/Express dans Minecraft pour gérer des écrans virtuels avec des URLs fixes.

## 🎯 Réponses aux questions importantes

### ❓ Quelle est la différence entre `webstream_manager` et `webstream-fabric-mod` ?

**`webstream_manager/`** 
- ✏️ **Projet de développement** de l'application Node.js
- 🔧 Vous développez et testez ici
- 🚀 Lancez avec `npm run dev` pour tester rapidement
- 📝 Modifiez le code ici

**`webstream-fabric-mod/`**
- 📦 **Projet du mod Fabric** pour Minecraft
- 🔨 Compilez le JAR ici avec `gradlew build`
- 🎮 Contient le code Java du mod
- 📥 Embarque automatiquement l'application Node.js

### ❓ Puis-je déplacer webstream-fabric-mod ailleurs ?

**Oui, avec nuances :**

#### Option 1 : Déplacer seulement le JAR compilé ✅
```bash
build/libs/webstream-mod-1.0.0.jar → N'importe où
```
- Totalement **indépendant**
- Contient déjà l'application Node.js complète
- Prêt à distribuer

#### Option 2 : Déplacer le dossier webstream-fabric-mod ⚠️

**Si déplacement AVANT compilation :**
```
Garder cette structure :
parent/
├── webstream_manager/          # Doit rester à côté
└── webstream-fabric-mod/       # Peut être déplacé avec parent/
```

**Si déplacement APRÈS compilation :**
- Le JAR est indépendant, vous pouvez le déplacer seul

### ❓ Comment fonctionne la synchronisation ?

```
1. Vous modifiez dans webstream_manager/src/
   ↓
2. Vous lancez gradlew build
   ↓
3. La tâche syncNodeApp copie automatiquement :
   - webstream_manager/src/ → webstream-fabric-mod/src/main/resources/webstream-node/src/
   - webstream_manager/storage/ → .../storage/
   - webstream_manager/package.json → .../package.json
   ↓
4. Le build crée le JAR avec tout embarqué
```

**Vous n'avez JAMAIS à copier manuellement !**

## 🚀 Guide rapide

### Installation des prérequis

1. **Node.js LTS**
   ```
   https://nodejs.org
   Télécharger et installer
   ```

2. **Java 17+**
   ```
   Vérifier : java -version
   ```

### Développement

```bash
# 1. Développer l'application Node.js
cd webstream_manager
npm install
npm run dev

# L'application est accessible sur http://localhost:8282
# Modifiez les fichiers dans src/, testez, etc.
```

### Compilation du mod

```bash
# 2. Compiler le mod Fabric
cd webstream-fabric-mod
gradlew build

# Le JAR sera dans build/libs/webstream-mod-1.0.0.jar
```

### Installation du mod

```bash
# 3. Copier le JAR dans Minecraft
.minecraft/mods/webstream-mod-1.0.0.jar

# 4. Lancer Minecraft avec Fabric
# Le serveur Node.js démarre automatiquement
```

## 📁 Structure des projets

```
webstream_manager/                      🔧 Développement
├── src/
│   ├── server.js                       ← Modifiez ici
│   ├── routes/                         ← Modifiez ici
│   ├── views/                          ← Modifiez ici
│   └── static/                         ← Modifiez ici
├── storage/library/                    ← Ajoutez des images ici
└── package.json

webstream-fabric-mod/                   📦 Build & Distribution
├── src/main/java/                      ← Code Java du mod
├── src/main/resources/
│   └── webstream-node/                 ← Copie automatique
└── build.gradle                        ← Configuration build
```

## 🎮 Utilisation in-game

### Ouvrir l'interface web

**Méthode 1 : Menu pause**
- Appuyer sur `ESC`
- Cliquer sur **"📺 WebStream"**

**Méthode 2 : Raccourci**
- Appuyer sur `Ctrl + W`

**Méthode 3 : Manuellement**
- Ouvrir `http://localhost:8282` dans un navigateur

### Gérer les écrans

1. **Créer une famille** (ex: "Métro")
2. **Uploader des images** dans la bibliothèque
3. **Créer des écrans** (ex: `metro-1`, `metro-2`)
4. **Assigner du contenu** aux écrans

### URLs des écrans

Chaque écran a une URL fixe :
```
http://localhost:8282/metro-1.png
http://localhost:8282/pub-cinema.png
http://localhost:8282/museum-hall-a.png
```

Utilisez ces URLs avec un mod de maps/webview pour afficher in-game.

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

## 📚 Documentation complète

- **[FIXED.md](FIXED.md)** - Correction du problème settings.gradle
- **[SYNC.md](SYNC.md)** - Synchronisation entre les deux projets
- **[doc/USAGE.md](USAGE.md)** - Guide d'utilisation complet
- **[doc/BUILD.md](BUILD.md)** - Guide de compilation
- **[doc/ARCHITECTURE.md](ARCHITECTURE.md)** - Documentation technique
- **[doc/CONFIG_EXAMPLES.md](CONFIG_EXAMPLES.md)** - Exemples de configuration

## 🐛 Dépannage

### Node.js non détecté
```
Erreur: [WebStream] Node.js NOT FOUND in PATH!

Solution:
1. Installer Node.js depuis https://nodejs.org
2. Redémarrer l'ordinateur
3. Vérifier : node --version
```

### settings.gradle error
```
Erreur: Could not compile settings file

Solution:
Le problème a été corrigé. Voir FIXED.md
```

### Port déjà utilisé
```
Erreur: Address already in use

Solution:
Modifier config/webstream.json → "port": 8283
```

## ✅ Checklist avant distribution

- [ ] Développer et tester dans `webstream_manager/`
- [ ] Compiler : `gradlew build`
- [ ] Vérifier que le JAR est créé : `build/libs/webstream-mod-1.0.0.jar`
- [ ] Tester le JAR dans `.minecraft/mods/`
- [ ] Vérifier que le serveur démarre
- [ ] Vérifier que l'interface web fonctionne
- [ ] Documenter les changements dans CHANGELOG.md

## 🎉 Résumé

**Pour développer :**
```bash
cd webstream_manager
npm run dev
# Modifiez, testez, sauvegardez
```

**Pour compiler :**
```bash
cd webstream-fabric-mod
gradlew build
# Le JAR est créé avec tout embarqué
```

**Pour distribuer :**
```bash
# Distribuez uniquement :
build/libs/webstream-mod-1.0.0.jar
```

**Pour utiliser :**
```bash
# Copier dans .minecraft/mods/
# Lancer Minecraft
# Ctrl+W pour ouvrir l'interface
```

---

**Le mod est prêt ! Bon développement et bon WebStreaming ! 🎮📺**

