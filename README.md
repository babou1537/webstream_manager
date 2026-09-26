# WebStream Manager

Mod Fabric (Minecraft **1.20.4**) qui complète le mod **WebStreamer** : une interface web intégrée pour gérer les images
affichées sur vos écrans, avec une bibliothèque d'images, des écrans à adresse fixe, des profils par monde et un port public pour le multijoueur.
Aucun Node.js, aucune base de données. Interface en français, English et Español.

> **Bêta.** Version actuelle : 2.2.1-beta.1.

## Où trouver quoi

- **[webstream-fabric-mod/](webstream-fabric-mod/)** : le mod (code source, tests, [README](webstream-fabric-mod/README.md) détaillé, [CHANGELOG](webstream-fabric-mod/CHANGELOG.md)). C'est ici que se trouve la version à utiliser.
- **[modrinth/](modrinth/)** : textes et captures pour la page Modrinth.
- Le reste du dépôt (`src/`, `package.json`, `launch_*.bat`, `test/`, [README_APP.md](README_APP.md)) est l'**ancienne application Node.js**, conservée pour mémoire et qui n'est plus nécessaire.

## Installation rapide

1. Installer Fabric Loader ≥ 0.15 et Fabric API pour 1.20.4.
2. Déposer `webstream-mod-fabric-<version>.jar` dans `mods/`.
3. Lancer un monde puis ouvrir **http://localhost:8282** (touche **H**, bouton « WebStream » du menu pause, ou `/webstream admin`).

## Compiler

Java 17 requis.

```
cd webstream-fabric-mod
./gradlew build      # gradlew.bat sous Windows
```

Le JAR est produit dans `webstream-fabric-mod/build/libs/`.

## Licence

MIT.
