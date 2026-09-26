# Politique de sécurité

## Versions prises en charge

Le mod est en bêta : seule la **dernière version publiée** reçoit des correctifs de sécurité.

| Version | Corrections de sécurité |
|---|---|
| 2.x (dernière bêta) | oui |
| 1.0.x (ancienne version Node.js) | non |

## Signaler une vulnérabilité

**Ne créez pas d'issue publique** pour une faille de sécurité.

Utilisez le signalement privé de GitHub : onglet **Security** du dépôt, puis **Report a vulnerability**.

Indiquez la version du mod, la configuration concernée (interface locale, mot de passe, port public), les étapes pour reproduire et l'impact que vous constatez. Vous recevrez une réponse dès que possible ; le projet est géré par une seule personne, comptez quelques jours.

## Ce qui nous intéresse en particulier

- Accès à la page d'administration sans le mot de passe alors qu'il est exigé (contournement de l'authentification, DNS rebinding, requêtes inter-sites).
- Accès à autre chose que les images depuis le port public.
- Lecture ou écriture de fichiers en dehors du dossier de la bibliothèque (traversée de chemin).
- Envoi de fichiers qui ne sont pas des images.

## À savoir

- L'interface écoute sur `127.0.0.1` par défaut. Si vous l'exposez sur Internet, placez un proxy inverse HTTPS devant : le mot de passe circule en clair en HTTP.
- Le port public ne sert que les images, sans mot de passe. Ne mettez rien de confidentiel dans la bibliothèque.

---

## English summary

Only the latest published beta receives security fixes. Please do **not** open a public issue for a vulnerability: use GitHub's private reporting (repository **Security** tab, then **Report a vulnerability**). Include the version, the configuration involved, steps to reproduce and the impact. Expect a reply within a few days. The admin interface listens on `127.0.0.1` by default; put an HTTPS reverse proxy in front of it if you expose it, since the password travels in clear over plain HTTP. The public port serves images only and needs no password.
