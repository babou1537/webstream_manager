# WebStream Manager

**Changez les images de vos écrans WebStreamer depuis une page web. Aucun fichier de config, aucun outil externe, rien à installer d'autre que le mod.**

WebStream Manager fait tourner une petite interface web dans Minecraft. Vous envoyez vos images, vous créez des écrans qui ont chacun une adresse fixe, vous collez cette adresse **une seule fois** dans un bloc d'affichage **WebStreamer**, et vous gérez ensuite ce qu'affichent vos écrans depuis votre navigateur.

C'est un compagnon du mod **WebStreamer** (sans affiliation avec lui).

## Fonctionnalités

- **Bibliothèque d'images** en glisser-déposer, avec miniatures légères en cache et l'indication des écrans qui utilisent chaque image.
- **Écrans à adresse fixe** (`http://votre-serveur:8283/station-metro-1.png`). Chaque écran garde le ratio de sa résolution : vous voyez l'image étirée comme en jeu.
- **Familles** pour ranger les écrans (Métro, Parc, Musée…).
- **Profils** : une configuration (familles et écrans) par monde, créée automatiquement à la première ouverture d'un monde. Changement de profil à tout moment depuis la barre latérale.
- **Assistant multijoueur** dans la page Réglages : ouvrez un port public qui ne sert **que** les images, renseignez votre adresse publique, testez-la. Sans éditer de fichier.
- **Import / export** d'un profil au format JSON.
- **Commandes en jeu** avec liens cliquables : `/webstream url <écran>`, `/webstream profile use <id>`…
- **Trois langues** : Français, English, Español (interface, messages d'erreur et commandes).
- **Autonome** : le serveur web est intégré au mod. Ni Node.js, ni base de données, ni téléchargement supplémentaire.

## Comment ça marche

1. Ouvrez l'interface : touche **H** en jeu, bouton **WebStream** du menu pause, commande `/webstream admin`, ou `http://localhost:8282` dans un navigateur.
2. **Bibliothèque** : envoyez vos images. **Écrans** : créez un écran, donnez-lui une résolution, assignez-lui une image et copiez son adresse.
3. Collez l'adresse dans un bloc d'affichage **WebStreamer**. Pour changer l'image ensuite, vous ne touchez qu'à la page web.

## Profils

Un profil regroupe les familles et les écrans d'une configuration. La bibliothèque d'images est commune à tous les profils.

Par défaut, un profil est créé pour chaque monde ouvert et ce monde s'en souvient. Vous pouvez dupliquer, renommer, exporter un profil ou en activer un autre à tout moment. Comme les adresses des écrans ne changent jamais, changer de profil change ce que servent ces mêmes adresses.

## Jouer à plusieurs

WebStreamer télécharge les images **depuis l'ordinateur de chaque joueur** : tous doivent pouvoir joindre l'adresse collée dans le bloc. `localhost` ne fonctionne que pour l'hôte.

WebStream Manager résout cela avec deux ports séparés :

| Port | Sert | Qui peut le joindre |
|---|---|---|
| `8282` (interface) | la page d'administration | cette machine seulement, ou avec un mot de passe |
| `8283` (public, optionnel) | **les images seulement** | tout le monde, sans mot de passe |

Dans **Réglages**, activez « Ouvrir un port public », saisissez votre adresse publique (par exemple `http://mon-serveur.fr:8283`) et cliquez sur **Tester**. Ouvrez ensuite ce port sur votre box, votre pare-feu ou votre tunnel. La page d'administration n'a jamais besoin d'être exposée.

## Commandes

| Commande | Rôle | Qui |
|---|---|---|
| `/webstream` | profil actif, monde et adresse des images | tous |
| `/webstream url <écran>` | lien cliquable vers l'image d'un écran | tous |
| `/webstream profile` | profil actif | tous |
| `/webstream profile list` / `use <id>` | lister / activer un profil (retenu pour le monde courant) | opérateurs |
| `/webstream admin` | lien vers l'interface | opérateurs |
| `/webstream reload` | relit les profils depuis le disque | opérateurs |

## Prérequis

| | |
|---|---|
| Minecraft | 1.20.4 |
| Chargeur | Fabric (0.15 ou plus récent) |
| Requis | Fabric API |
| Recommandé | le mod **WebStreamer** (c'est lui qui affiche réellement les images) |
| Java | 17 ou plus récent |
| Côté | **Serveur : requis.** Client : facultatif (ajoute la touche H et le bouton du menu pause). En solo, installez-le dans votre instance comme d'habitude. |

## Bon à savoir

- **WebStreamer garde en cache une copie de chaque image par adresse exacte**, et la recharge au bout d'un moment ou quand l'adresse change. Une nouvelle image peut donc mettre un peu de temps à apparaître sur un écran déjà chargé. WebStream Manager ajoute `?refresh=1` aux adresses qu'il vous donne ; vous pouvez modifier ou retirer ce paramètre dans Réglages.
- Un écran **sans image** répond « introuvable » par défaut : WebStreamer réessaie alors toutes les 30 secondes et affiche l'image dès que vous en assignez une. Vous pouvez choisir une image « NO SIGNAL » dans Réglages, mais WebStreamer la gardera en cache tant que l'adresse ne change pas.
- Les exports contiennent les familles et les écrans, **pas les images** elles-mêmes.
- C'est une **bêta**. Signalez tout comportement étrange.

## Sécurité

- La page d'administration écoute sur `127.0.0.1` par défaut. Une requête n'est « locale » que si elle vient de la machine **et** utilise un nom d'hôte local : les tunnels et les attaques par « DNS rebinding » doivent donc fournir le mot de passe.
- Les requêtes inter-sites sont refusées, les envois sont limités en taille et vérifiés (le contenu doit être une vraie image) et les noms de fichiers sont assainis.
- Le mot de passe circule en clair en HTTP simple. Si vous exposez l'interface sur Internet, placez un proxy inverse HTTPS devant.

## Langues

English, Français et Español. À choisir dans **Réglages > Langue**. Par défaut : la langue de votre système (anglais si ce n'est pas l'une des trois).

## Liens

- Code source et suivi des problèmes : <https://github.com/babou1537/webstream_manager>
- Licence : MIT
- L'interface utilise les polices pixel *Pixelify Sans*, *Silkscreen* et *VT323* (SIL Open Font License 1.1), embarquées dans le mod. Le logo et les visuels ont été dessinés pour ce mod.
