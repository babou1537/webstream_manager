# Synchronisation entre webstream_manager et webstream-fabric-mod

## Structure des deux projets

```
webstream_manager/                    # Projet Node.js standalone (développement)
├── src/                              # Code source Node.js
├── storage/                          # Images
└── package.json                      # Dépendances npm

webstream-fabric-mod/                 # Mod Fabric (distribution)
└── src/main/resources/
    └── webstream-node/               # Copie du projet Node.js (embarqué dans le JAR)
        ├── src/
        ├── storage/
        └── package.json
```

## Workflow de développement

### 1. Développement de l'application Node.js

Travailler dans `webstream_manager/` :

```bash
cd webstream_manager
npm run dev
```

Modifier les fichiers :
- `src/server.js` - Serveur Express
- `src/routes/*.js` - Routes API
- `src/views/*.ejs` - Templates
- `src/static/*` - Assets frontend
- `storage/library/` - Images de test

### 2. Synchronisation automatique

Lors de la compilation du mod, les fichiers sont **automatiquement synchronisés** grâce à la tâche Gradle `syncNodeApp` :

```bash
cd webstream-fabric-mod
gradlew build
```

**Que fait `syncNodeApp` ?**
1. Copie `../src/` → `src/main/resources/webstream-node/src/`
2. Copie `../storage/` → `src/main/resources/webstream-node/storage/`
3. Copie `../package.json` → `src/main/resources/webstream-node/package.json`
4. Exclut automatiquement `node_modules/`, `.git/`, etc.

### 3. Build du mod

```bash
gradlew build
```

Cela crée :
- `build/libs/webstream-mod-1.0.0.jar` contenant l'application Node.js complète

### 4. Distribution

Le JAR peut être distribué seul. Au premier démarrage du mod :
1. Extraction de `webstream-node/` depuis le JAR
2. Copie dans `config/webstream/`
3. `npm install` automatique
4. Démarrage du serveur Node.js

## Synchronisation manuelle (si nécessaire)

Si vous voulez synchroniser sans rebuild complet :

```bash
gradlew syncNodeApp
```

Cela copie les fichiers sans recompiler le mod Java.

## Avantages de cette approche

✅ **Développement découplé** : Vous pouvez développer l'app Node.js sans toucher au code Java  
✅ **Synchronisation automatique** : Pas besoin de copier manuellement lors du build  
✅ **Versionning séparé** : Le code Node.js peut évoluer indépendamment  
✅ **Tests rapides** : `npm run dev` dans `webstream_manager/` pour tester sans Minecraft  
✅ **Distribution simple** : Un seul JAR contient tout

## Fichiers exclus de la synchronisation

Ces fichiers/dossiers ne sont **pas copiés** :
- `node_modules/` (trop volumineux, réinstallé via `npm install`)
- `.git/` (historique Git non nécessaire)
- `launch_dev.bat`, `launch_prod.bat` (scripts de dev uniquement)
- `README_APP.md`, `WebStream Manager.lnk` (documentation/raccourcis)

## Cas d'usage typiques

### Modifier une route API

1. Éditer `webstream_manager/src/routes/screens.js`
2. Tester avec `npm run dev`
3. Builder le mod : `gradlew build`
4. La route modifiée est automatiquement synchronisée dans le JAR

### Ajouter une nouvelle image

1. Copier l'image dans `webstream_manager/storage/library/`
2. Builder le mod : `gradlew build`
3. L'image est automatiquement embarquée dans le JAR

### Modifier le frontend

1. Éditer `webstream_manager/src/static/styles.css`
2. Tester avec `npm run dev`
3. Builder le mod : `gradlew build`
4. Le CSS modifié est synchronisé

## Vérifier la synchronisation

Après `gradlew build`, vérifier que les fichiers sont bien copiés :

```bash
# Windows
dir src\main\resources\webstream-node\src

# Linux/Mac
ls -la src/main/resources/webstream-node/src
```

Vous devriez voir tous les fichiers de `webstream_manager/src/`.

## Résolution de problèmes

### Les modifications ne sont pas prises en compte

**Problème** : Les changements dans `webstream_manager/` n'apparaissent pas dans le mod

**Solution** :
1. Supprimer `build/` : `gradlew clean`
2. Rebuild : `gradlew build`
3. Vérifier que `syncNodeApp` s'est exécuté (logs Gradle)

### Fichiers manquants dans le JAR

**Problème** : Certains fichiers ne sont pas copiés

**Solution** :
1. Vérifier que les fichiers ne sont pas dans la liste `exclude` de `build.gradle`
2. Vérifier les chemins relatifs (`../src/`, `../storage/`, etc.)
3. Exécuter manuellement : `gradlew syncNodeApp`

### Conflits de version

**Problème** : Version différente entre dev et production

**Solution** :
1. Toujours développer dans `webstream_manager/`
2. Ne jamais modifier directement dans `webstream-fabric-mod/src/main/resources/webstream-node/`
3. Laisser `syncNodeApp` gérer la synchronisation

## Bonnes pratiques

✅ **DO** :
- Développer dans `webstream_manager/`
- Utiliser `npm run dev` pour tester rapidement
- Laisser Gradle synchroniser automatiquement
- Versionner `webstream_manager/` avec Git

❌ **DON'T** :
- Ne modifiez JAMAIS directement dans `webstream-fabric-mod/src/main/resources/webstream-node/`
- Ne copiez pas manuellement les fichiers
- Ne committez pas `build/` dans Git
- Ne distribuez pas `webstream_manager/` (c'est pour le dev)

## Résumé visuel

```
DÉVELOPPEMENT               SYNCHRONISATION            DISTRIBUTION
───────────────            ───────────────           ──────────────

webstream_manager/         
     │                     
     │ Éditer code         
     ▼                     
  src/server.js ──────────► syncNodeApp ─────────► JAR embarqué
  src/routes/   ──────────►            ─────────►
  storage/      ──────────►            ─────────►
     │                                                    │
     │                                                    │
  npm run dev                                        Extraction
  (test rapide)                                      au démarrage
                                                          │
                                                          ▼
                                                   config/webstream/
                                                   (runtime)
```

---

**En résumé** : Vous n'avez **qu'un seul endroit** où développer (`webstream_manager/`), et Gradle s'occupe du reste automatiquement ! 🚀

