# WebStream Manager

Mod Fabric (Minecraft **1.20.4**) qui complète le mod **WebStreamer** : il héberge une interface web pour
gérer les images de vos écrans. Vous changez l'image dans le navigateur, l'écran se met à jour en jeu.

- **Aucune installation externe** : le serveur web est intégré au mod (Java uniquement, pas de Node.js).
- **Bibliothèque d'images**, **écrans** avec adresses fixes (`http://…/mon-ecran.png`), **familles** pour les ranger.
- **Profils** : une configuration (familles + écrans) par monde, ou une seule pour tous, et changement à chaud.
- **Réglages dans l'interface** : multijoueur, mot de passe, ports… sans éditer de fichier.
- Fonctionne en **solo**, en **LAN** et sur **serveur dédié**.

## Installation

1. Installer Fabric Loader ≥ 0.15 et Fabric API pour 1.20.4.
2. Déposer `webstream-mod-fabric-<version>.jar` dans `mods/` (serveur et/ou client).
3. Lancer un monde. L'interface est sur **http://localhost:8282** (ouverte par la touche **H**, le bouton « WebStream » du menu pause,
   ou `/webstream admin`).

## Utilisation

1. **Bibliothèque** : déposez vos images (PNG, JPG, GIF, WebP, BMP, TIFF, SVG).
2. **Écrans** : créez un écran (`metro-station-1`), assignez-lui une image, cliquez sur son adresse pour la copier.
3. **En jeu** : collez cette adresse dans le bloc d'affichage WebStreamer. Changer l'image dans l'interface met l'écran à jour.

### Profils : une configuration par monde

Un profil regroupe des familles et des écrans ; la bibliothèque d'images est commune. Chaque monde est lié à un profil :

- par défaut (`newWorldProfile: perWorld`), un profil est créé à la première ouverture d'un monde ;
- avec `shared`, tous les mondes utilisent le même profil ;
- la page **Profils** permet de créer, dupliquer (« copier depuis… »), renommer, supprimer et **activer** un profil à tout moment.
  Comme les adresses `/ecran.png` ne changent pas, vos blocs en jeu montrent instantanément le contenu du profil activé.

La page **Données** exporte un profil en `.json` et l'importe dans un autre profil ou un autre monde.

### Commandes

| Commande | Rôle | Qui |
|---|---|---|
| `/webstream` | profil actif, monde, adresse des images | tous |
| `/webstream url <écran>` | lien cliquable vers l'image d'un écran | tous |
| `/webstream profile` | profil actif | tous |
| `/webstream profile list` / `use <id>` | lister / activer un profil (le retient pour ce monde) | opérateurs |
| `/webstream admin` | lien vers l'interface | opérateurs |
| `/webstream reload` | relit les profils depuis le disque | opérateurs |

## Jouer à plusieurs

WebStreamer télécharge les images **depuis le PC de chaque joueur**. Une adresse `localhost` ne marche donc que chez l'hôte.
Le mod sépare deux choses :

| | Contenu | Accès |
|---|---|---|
| `port` (8282) | interface d'administration | cette machine (ou mot de passe à distance) |
| `publicPort` (ex. 8283) | **images seulement** | ouvert à tous, sans mot de passe |

1. Ouvrez la page **Réglages** : activez « Ouvrir un port public » et renseignez l'**adresse publique** (`http://mon-serveur.fr:8283`).
   L'assistant propose les adresses de votre réseau local pour jouer en LAN, et le bouton **Tester** vérifie que l'adresse répond.
2. Ouvrez / redirigez ce port (box, pare-feu, ou tunnel type playit.gg) : seules les images sont exposées.
3. Copiez les adresses depuis l'interface : elles utilisent l'adresse publique.

Les mêmes options existent dans `config/webstream.json` (voir ci-dessous) ; sur un serveur dédié, `publicPort` vaut 8283 dès la première création du fichier.

L'interface d'administration reste locale. Pour l'utiliser à distance : `"bindAddress": "0.0.0.0"` et un `"adminPassword"`
(nom d'utilisateur libre), éventuellement `"adminUrl"` pour que la touche des joueurs l'ouvre.

## Configuration (`config/webstream.json`)

La plupart des options se modifient depuis la page **Réglages** ; le fichier est mis à jour automatiquement.

| Option | Défaut | Rôle |
|---|---|---|
| `enabled` | `true` | désactive le mod |
| `port` | `8282` | port de l'interface |
| `bindAddress` | `127.0.0.1` | `0.0.0.0` pour ouvrir l'interface au réseau |
| `adminPassword` | `""` | mot de passe des accès non locaux ; vide = accès distant refusé |
| `trustLocalhost` | `true` | `false` : mot de passe aussi en local (si vous tunnelisez le port principal) |
| `publicPort` | `0` (`8283` sur serveur dédié) | port qui ne sert que les images |
| `publicBindAddress` | `0.0.0.0` | adresse d'écoute du port public |
| `publicUrl` | `""` | adresse publique donnée aux joueurs, utilisée pour copier les URL |
| `adminUrl` | `""` | adresse de l'interface envoyée aux joueurs (touche / bouton) |
| `remoteUrl` | `""` | côté joueur : adresse à ouvrir (prioritaire sur `adminUrl`) |
| `newWorldProfile` | `perWorld` | `perWorld` ou `shared` |
| `maxUploadMb` | `64` | taille maximale d'une image |
| `urlQuery` | `refresh=1` | paramètre ajouté aux adresses copiées (sans le `?`) ; vide = aucun |
| `placeholderImage` | `false` | `false` : un écran sans image répond 404 (WebStreamer réessaie toutes les 30 s) ; `true` : image « Aucun contenu » |

Fichiers du mod, dans `config/webstream/` : `library/` (images), `profiles/*.json`, `workspace.json` (profil actif, liens monde → profil).

### Personnaliser l'apparence

Déposez `banner.png|webp|jpg|svg` et/ou `icon.png` dans `config/webstream/branding/` : ils remplacent l'en-tête neutre, sans redémarrage.

### Sécurité

- L'interface n'écoute que sur `127.0.0.1` par défaut. Un accès n'est « local » que si la connexion vient de la machine **et** que le nom d'hôte
  est `localhost`/`127.0.0.1` : un tunnel ou une attaque « DNS rebinding » doivent donc fournir le mot de passe.
- Les requêtes d'écriture venant d'un autre site (CSRF) sont refusées. Les envois sont limités en taille et vérifiés (le contenu doit
  correspondre à une vraie image). Le mot de passe passe en clair en HTTP : sur Internet, placez un proxy HTTPS devant.

## Mise à jour depuis la version Node.js (1.0.x)

Le mod n'utilise plus Node.js. Au premier lancement :

- votre bibliothèque d'images (`config/webstream/storage/library`) est **copiée** dans `config/webstream/library` ;
- vos écrans : sur l'ancienne version, page **Données** → *Télécharger l'export*, puis dans la nouvelle → *Importer* le `.json`
  (les exports de la 1.0.x sont acceptés tels quels) ;
- les anciens dossiers (`node_modules`, `src`, `package.json`, `data`, `storage`) peuvent être supprimés.

## Développement

```bash
cd webstream-fabric-mod
./gradlew build        # compile, teste, produit build/libs/*.jar
./gradlew test         # tests unitaires et d'intégration HTTP
./gradlew runWeb --args="dossier 8281 export.json dossier-images"   # interface hors Minecraft
```

Le cœur (`core/`) et le serveur web (`web/`) n'utilisent aucune classe Minecraft et sont testés seuls ; `command/`, `net/`, `client/`
et `WebStreamMod` font le lien avec le jeu. Java 17.

## Licence

MIT — voir [LICENSE](LICENSE).
