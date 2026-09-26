# Changelog

## 2.1.0-beta.1

- **Nouvelle interface** (thème sombre) : barre latérale, changement de profil depuis n'importe quelle page, tableau de bord, notifications,
  édition d'un écran sans « mode crayon » (choix de l'image en cliquant sur une vignette), profils en cartes avec aperçus.
  Le mécanisme des cartes est conservé : chaque écran garde le ratio de sa résolution (image étirée comme en jeu).
- **Page Réglages** avec assistant multijoueur : port public, adresse publique (avec suggestion des adresses locales), test de connexion,
  mot de passe, options. Plus besoin d'éditer `webstream.json` ; les ports se changent à chaud (retour arrière si le nouveau port est occupé).
- **Vignettes** : la bibliothèque et les cartes chargent des miniatures JPEG en cache au lieu des images en pleine taille (15 Mo -> quelques centaines de Ko).
- Paramètre d'URL configurable (`refresh=1` par défaut) ajouté aux adresses copiées.
- Écran sans image : 404 par défaut (WebStreamer réessaie toutes les 30 s et affiche l'image dès qu'elle est assignée) ; l'image « Aucun contenu »
  est une option (WebStreamer la garde en cache tant que l'adresse ne change pas).
- Route publique `/ping`, utilisée par le test de connexion.
- 45 tests automatiques.

## 2.0.0-beta.2

- Correctif : avec un chemin de configuration relatif (cas d'un serveur), les images de la bibliothèque étaient listées mais refusées (404) ; les écrans affichaient « Aucun contenu ».

## 2.0.0-beta.1

Réécriture : le mod est désormais **autonome** (plus de Node.js, npm ni SQLite).

- Serveur web intégré (JDK uniquement), même interface qu'avant.
- **Profils** par monde, avec liaison monde → profil, changement à chaud, duplication, import/export.
- Port public séparé (`publicPort`) qui ne sert que les images : l'administration reste locale.
- Commandes `/webstream` (url, profile, admin, reload) ; adresse de l'interface envoyée aux joueurs.
- Accès : local de confiance (hôte `localhost`), mot de passe à distance, protection CSRF, envois vérifiés.
- Placeholder « Aucun contenu » servi pour les écrans sans image (au lieu d'un 404), `ETag` pour un rafraîchissement fiable.
- Noms d'écrans normalisés (sans accents ni espaces) pour des adresses valides.
- Identité neutre par défaut, personnalisable via `config/webstream/branding/`.
- Correction d'une faille XSS (noms de familles) dans l'interface.
- Tests automatiques (JUnit) et intégration continue.

Migration depuis 1.0.x : voir le README.

## 1.0.4-beta

Version Node.js embarquée : JAR sans données personnelles, import/export, permissions par accès réseau.
