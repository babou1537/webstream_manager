# Changelog

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
