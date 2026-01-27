# WebStream Manager - Guide d'utilisation complet

## 🚀 Démarrage rapide

### 1. Installation

1. **Installer Node.js LTS**
   - Télécharger depuis https://nodejs.org
   - Installer la version recommandée (LTS)
   - Vérifier : ouvrir un terminal et taper `node --version`

2. **Installer le mod**
   - Placer `webstream-mod-1.0.0.jar` dans `.minecraft/mods/`
   - S'assurer que Fabric Loader 0.15.11+ est installé
   - S'assurer que Fabric API 0.92.2+ est installé

3. **Lancer Minecraft**
   - Le mod démarre automatiquement
   - Regarder les logs : `[WebStream] Server started on http://localhost:8282`

### 2. Premier accès à l'interface

**Option A : Via le menu pause**
1. Lancer Minecraft
2. Appuyer sur `ESC`
3. Cliquer sur le bouton **"📺 WebStream"**

**Option B : Via le raccourci clavier**
1. Dans le jeu, appuyer sur `Ctrl + W`
2. Le navigateur s'ouvre automatiquement

**Option C : Manuellement**
1. Ouvrir votre navigateur web
2. Aller sur `http://localhost:8282`

## 📋 Interface web détaillée

### Page d'accueil

L'interface comporte 3 sections principales :

#### 1. 📺 Écrans
Gère tous vos écrans virtuels

**Créer un écran**
1. Cliquer sur l'onglet "Écrans"
2. Remplir le formulaire :
   - **Référence** : Nom unique de l'écran (ex: `metro-station-1`)
   - **Famille** : Catégorie d'écran (optionnel)
   - **Largeur** : Résolution en pixels (défaut: 1920)
   - **Hauteur** : Résolution en pixels (défaut: 1080)
3. Cliquer sur "Créer"

**Modifier un écran**
1. Trouver l'écran dans la liste
2. Cliquer sur "✏️ Modifier"
3. Changer les paramètres
4. Cliquer sur "Sauvegarder"

**Assigner du contenu**
1. Sélectionner une image dans la bibliothèque
2. Cliquer sur "Assigner à l'écran"
3. Choisir l'écran cible

**Accéder à l'URL de l'écran**
```
http://localhost:8282/<référence>.png
```
Exemple : `http://localhost:8282/metro-station-1.png`

#### 2. 👨‍👩‍👧‍👦 Familles
Organise les écrans par catégories

**Créer une famille**
1. Cliquer sur l'onglet "Familles"
2. Entrer le nom (ex: "Métro", "Publicités", "Musée")
3. Cliquer sur "Créer"

**Utilité des familles**
- Regrouper les écrans par thème
- Faciliter la navigation
- Appliquer des modifications en masse

#### 3. 🖼️ Bibliothèque
Gère toutes vos images

**Uploader une image**
1. Cliquer sur l'onglet "Bibliothèque"
2. Cliquer sur "Choisir des fichiers" ou glisser-déposer
3. Sélectionner une ou plusieurs images (PNG, JPG, JPEG)
4. Cliquer sur "Uploader"

**Formats supportés**
- PNG (recommandé pour la transparence)
- JPG / JPEG (recommandé pour les photos)
- Taille maximale : selon configuration

**Gérer les images**
- **Prévisualiser** : Cliquer sur l'image
- **Assigner** : Cliquer sur "Assigner à l'écran"
- **Supprimer** : Cliquer sur "🗑️ Supprimer"

## 🎮 Utilisation in-game

### Intégration avec Minecraft

Les écrans WebStream génèrent des URLs fixes qui peuvent être utilisées avec :

**1. Mod de maps personnalisées**
Exemple avec ImageOnMap ou similaire :
```
/image http://localhost:8282/metro-station-1.png
```

**2. Resource Pack dynamique**
Créer des textures qui pointent vers les URLs WebStream

**3. Mod de webview**
Afficher directement le contenu web dans Minecraft

### Workflow typique

1. **Créer une famille** "Métro"
2. **Uploader les images** des stations de métro
3. **Créer les écrans** : `metro-1`, `metro-2`, `metro-3`...
4. **Assigner les images** aux écrans
5. **Utiliser in-game** via les URLs :
   - `http://localhost:8282/metro-1.png`
   - `http://localhost:8282/metro-2.png`
   - etc.

## ⚙️ Configuration avancée

### Fichier de configuration

Éditer `config/webstream.json` :

```json
{
  "enabled": true,
  "port": 8282,
  "autoOpenBrowser": false,
  "keybind": "W",
  "useCtrlModifier": true
}
```

### Changer le port

Si le port 8282 est déjà utilisé :

1. Ouvrir `config/webstream.json`
2. Modifier `"port": 8282` → `"port": 8283`
3. Redémarrer Minecraft
4. Utiliser la nouvelle URL : `http://localhost:8283`

### Désactiver le modificateur Ctrl

Pour ouvrir avec juste `W` (sans Ctrl) :

1. Ouvrir `config/webstream.json`
2. Modifier `"useCtrlModifier": true` → `"useCtrlModifier": false`
3. Redémarrer Minecraft

### Ouvrir automatiquement le navigateur

1. Ouvrir `config/webstream.json`
2. Modifier `"autoOpenBrowser": false` → `"autoOpenBrowser": true`
3. Le navigateur s'ouvrira au démarrage de Minecraft

## 🗃️ Sauvegarde et migration

### Sauvegarder vos données

Tous les données sont dans le dossier `config/webstream/` :

```
config/webstream/
├── src/db/
│   └── webstream.db          # Base de données SQLite
└── storage/library/           # Toutes vos images
    ├── metro-1.png
    ├── metro-2.png
    └── ...
```

**Pour sauvegarder** :
1. Fermer Minecraft
2. Copier tout le dossier `config/webstream/`
3. Coller ailleurs en sécurité

### Migrer vers un autre ordinateur

1. **Sur l'ancien PC** :
   - Copier `config/webstream/`
   
2. **Sur le nouveau PC** :
   - Installer Node.js
   - Installer le mod
   - Lancer Minecraft une fois (pour créer la structure)
   - Fermer Minecraft
   - Remplacer `config/webstream/` par votre sauvegarde
   - Relancer Minecraft

## 🛠️ Maintenance

### Optimiser la base de données

Si vous avez beaucoup d'écrans, optimisez périodiquement :

```sql
-- Dans un client SQLite (DB Browser par exemple)
VACUUM;
ANALYZE;
```

### Nettoyer les images non utilisées

1. Aller dans la bibliothèque
2. Identifier les images non assignées
3. Supprimer les images inutiles

### Réinitialiser complètement

1. Fermer Minecraft
2. Supprimer `config/webstream/`
3. Relancer Minecraft
4. Tout sera recréé à partir de zéro

## 📊 Bonnes pratiques

### Nommage des écrans

**✅ Bon**
- `metro-station-1`
- `pub-cinema-1`
- `museum-hall-a`

**❌ Mauvais**
- `écran 1` (espaces)
- `écran_métro` (accents)
- `ÉCRAN-1` (majuscules)

**Règles**
- Uniquement lettres minuscules, chiffres, tirets
- Pas d'espaces, pas d'accents
- Court et descriptif

### Organisation par familles

Créez des familles logiques :
- **Métro** : Tous les écrans de métro
- **Publicité** : Écrans publicitaires
- **Musée** : Écrans de musée
- **Événements** : Écrans temporaires

### Résolutions d'images

**Recommandations** :
- **HD** : 1920x1080 (par défaut)
- **4K** : 3840x2160 (haute qualité)
- **Carré** : 1024x1024 (panneaux)
- **Vertical** : 1080x1920 (portrait)

**Astuce** : Utilisez la même résolution pour tous les écrans d'une famille.

### Performance

**Pour de meilleures performances** :
- Compressez vos images avant upload
- Utilisez PNG pour les images avec transparence
- Utilisez JPG pour les photos
- Évitez les images trop lourdes (> 5 Mo)

## 🐛 Résolution de problèmes

### Le bouton WebStream n'apparaît pas

1. Vérifier que le mod est bien chargé (logs)
2. Vérifier que Fabric API est installé
3. Redémarrer Minecraft

### "Node.js NOT FOUND"

1. Installer Node.js depuis https://nodejs.org
2. Redémarrer l'ordinateur
3. Vérifier dans un terminal : `node --version`
4. Relancer Minecraft

### Le serveur ne démarre pas

1. Vérifier les logs Minecraft
2. Vérifier que le port 8282 est libre
3. Désactiver temporairement l'antivirus
4. Vérifier `config/webstream.json` : `"enabled": true`

### Images qui ne s'affichent pas

1. Vérifier que l'image est bien uploadée
2. Vérifier que l'écran a bien du contenu assigné
3. Tester l'URL directement dans un navigateur
4. Vérifier les logs pour les erreurs

### npm install qui échoue

1. Vérifier votre connexion internet
2. Supprimer `config/webstream/node_modules/`
3. Relancer Minecraft
4. Si le problème persiste, installer manuellement :
   ```bash
   cd config/webstream
   npm install --production
   ```

## 💡 Astuces et conseils

### Raccourcis clavier

- `Ctrl + W` : Ouvrir WebStream Manager
- `ESC` → "📺 WebStream" : Alternative au raccourci

### Utilisation avancée

**Changement de contenu en direct**
1. Les écrans sont mis à jour instantanément
2. Changez le contenu via l'interface web
3. L'écran in-game se met à jour automatiquement

**Gestion de plusieurs serveurs**
- Chaque instance Minecraft peut avoir son propre serveur WebStream
- Changez le port pour éviter les conflits
- Partagez le dossier `config/webstream/` en réseau local

**API REST** (pour développeurs)
Le serveur expose des endpoints REST :
- `GET /:screenRef.png` - Récupère l'image d'un écran
- `GET /api/screens` - Liste tous les écrans
- `POST /api/screens` - Crée un écran
- etc.

## 📞 Support

**En cas de problème** :
1. Consulter les logs Minecraft
2. Vérifier ce guide
3. Créer une issue sur GitHub
4. Fournir les logs et la configuration

---

**Bon WebStreaming ! 🎮📺**

