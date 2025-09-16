# WebStream Manager (WIP)

Application web locale pour gérer des écrans WebStreaming dans Minecraft. Les URLs de rendu sont fixes et exposées en `http://localhost:8282/screens/{ref}.png`.

## Démarrage rapide (Windows PowerShell)

1. Installer Node.js (LTS recommandé).
2. Dans ce dossier, installer les dépendances:

```powershell
npm install
```

3. Initialiser la base de données:

```powershell
npm run migrate
```

4. Lancer le serveur:

```powershell
npm run dev
```

Ouvrez: `http://localhost:8282`.

## Répertoires

- `src/public`: assets statiques (CSS/JS)
- `src/views`: templates EJS
- `src/routes`: routes Express (bibliothèque, écrans)
- `storage/library`: fichiers image téléchargés
- `src/db/webstream.db`: base SQLite (créée à la migration)

## Prochaines étapes
- Écrire la couche DB pour persister écrans/familles/assignations
- Catégories par dimensions + éditeur de prévisualisation
- Conversion/resize PNG via `sharp` si nécessaire
