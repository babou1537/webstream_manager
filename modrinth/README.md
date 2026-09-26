# Publier WebStream Manager sur Modrinth

Tous les textes sont prêts dans ce dossier. Il ne reste qu'à les copier dans Modrinth.

| Fichier | À coller où |
|---|---|
| `summary.txt` (bloc EN) | Summary |
| `description.en.md` | Description du projet (Modrinth accepte le Markdown) |
| `description.fr.md` | Version française, à garder pour le GitHub ou un lien |
| `version-2.2.1-beta.1.md` | Version changelog |
| `gallery/*.png` + `gallery/TITRES.md` | Galerie (titre et description de chaque image) |

## Avant de créer le projet

- [ ] **Pousser sur GitHub** : `git push origin feature/mod-pret-pour-le-jeu` puis `git push origin v2.2.1-beta.1`. Les liens Source et Issues doivent fonctionner. Idéalement fusionnez dans `main`.
- [ ] Le `README.md` à la racine du dépôt est quasi vide : un visiteur venant de Modrinth tombera dessus. Ajoutez-y quelques lignes pointant vers `webstream-fabric-mod/`.
- [ ] **Tester en jeu** (non vérifié à ce jour) : démarrage serveur dédié, commandes `/webstream`, touche H, bouton du menu pause, changement de langue, partie à deux joueurs avec port public, migration d'une config 1.0.x, comportement de WebStreamer au changement d'image.
- [ ] Faire relire les traductions anglaise et espagnole par quelqu'un dont c'est la langue si possible.
- [ ] Vérifier que les images de la galerie ne contiennent rien dont vous n'avez pas les droits.

## Étape 1 : créer le projet

| Champ | Valeur |
|---|---|
| Type | Mod |
| Name | `WebStream Manager` |
| URL (slug) | `webstream-manager` (si disponible) |
| Summary | voir `summary.txt` |
| Visibility | Public (ou « Unlisted » pour un essai) |

## Étape 2 : description et détails

| Champ | Valeur |
|---|---|
| Description | contenu de `description.en.md` |
| Categories | à choisir dans la liste de Modrinth : « Utility » et « Decoration » conviennent le mieux |
| Client side | Optional |
| Server side | Required |
| License | MIT (choisir dans la liste) |
| Source code | https://github.com/babou1537/webstream_manager |
| Issue tracker | https://github.com/babou1537/webstream_manager/issues |
| Icon | `webstream-fabric-mod/src/main/resources/assets/webstream/icon.png` |
| Galerie | les 6 images de `gallery/`, la première en « Featured » |

## Étape 3 : ajouter la version

| Champ | Valeur |
|---|---|
| Version number | `2.2.1-beta.1` |
| Version title | `WebStream Manager 2.2.1-beta.1` |
| Release channel | **Beta** |
| Changelog | contenu de `version-2.2.1-beta.1.md` |
| Loaders | Fabric |
| Game versions | 1.20.4 |
| Fichier principal | `webstream-fabric-mod/build/libs/webstream-mod-fabric-2.2.1-beta.1.jar` |
| Fichier `-sources.jar` | facultatif : ne l'ajoutez pas comme fichier principal |
| Dependencies | **Fabric API : Required**. WebStreamer : Optional, si vous le trouvez sur Modrinth (cherchez-le dans le sélecteur de projet) |

## Étape 4 : envoyer en modération

Cliquez sur « Submit for review ». La modération prend en général 24 à 48 h. Elle regardera surtout la licence, le contenu de la description et le fichier. Le mod ne collecte rien et n'envoie aucune donnée à l'extérieur : c'est un point à mentionner si on vous le demande.

## Après publication

- Ajoutez le lien Modrinth dans le README GitHub.
- Pour les versions suivantes : changez `mod_version` dans `gradle.properties`, reconstruisez le jar, puis « Add version » sur Modrinth.
