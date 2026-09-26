# Contribuer à WebStream Manager

Merci de votre intérêt ! Le projet est en bêta et géré par une seule personne : les retours, corrections et traductions sont les bienvenus.

*English summary at the bottom of this page.*

## Signaler un bug

Ouvrez une [issue](https://github.com/babou1537/webstream_manager/issues) en indiquant :

- la version du mod, de Minecraft, de Fabric Loader et de WebStreamer ;
- le contexte : solo, LAN ou serveur dédié, et si vous utilisez le port public ;
- ce que vous attendiez et ce qui s'est passé, avec les étapes pour reproduire ;
- le fichier `logs/latest.log` (retirez-en tout mot de passe ou adresse privée).

## Compiler et tester

Java 17 est requis.

```
cd webstream-fabric-mod
./gradlew build      # gradlew.bat sous Windows : compile et lance les tests
./gradlew runWeb     # lance l'interface web sans Minecraft, pour la développer
```

Le JAR est produit dans `webstream-fabric-mod/build/libs/`. Les tests (JUnit 5) doivent passer avant toute proposition.

## Proposer du code

1. Créez une branche par sujet à partir de la branche principale du dépôt.
2. Gardez les changements ciblés et décrivez-les dans la pull request.
3. Ajoutez ou adaptez les tests qui couvrent votre changement.
4. Si vous touchez au comportement visible, notez-le dans `webstream-fabric-mod/CHANGELOG.md`.

Règles du projet :

- **Pas de dépendance externe** : le mod est autonome (serveur HTTP du JDK, stockage JSON). Pas de Node.js ni de base de données.
- **Toute chaîne visible passe par une clé de traduction**, dans les trois langues. Les tests de cohérence (`I18nTest`) échouent si une clé manque.
- **Identité visuelle** : « moniteur rétro-néon », thème sombre uniquement. Évitez d'introduire un autre style.
- **Sécurité** : l'interface d'administration ne doit jamais être servie par le port public, qui ne sert que les images.

## Traductions

Les textes sont dans :

- `webstream-fabric-mod/src/main/resources/web/lang/{fr,en,es}.json` (interface web, messages, commandes) ;
- `webstream-fabric-mod/src/main/resources/assets/webstream/lang/{fr_fr,en_us,es_es}.json` (textes affichés dans Minecraft).

Les corrections de l'anglais et de l'espagnol sont particulièrement utiles. Une nouvelle langue est possible : ouvrez d'abord une issue.

## À ne jamais committer

Vos images, vos exports d'écrans, le dossier `config/webstream/`, les mots de passe et toute adresse privée.

---

## English summary

- **Bugs**: open an issue with mod / Minecraft / Fabric / WebStreamer versions, setup (singleplayer, LAN, dedicated server, public port), steps to reproduce and `logs/latest.log` (remove passwords and private addresses).
- **Build**: Java 17, then `cd webstream-fabric-mod && ./gradlew build` (runs the tests). `./gradlew runWeb` starts the web UI without Minecraft.
- **Pull requests**: one branch per topic, focused changes, tests passing, note visible changes in `CHANGELOG.md`.
- **Rules**: no external dependencies; every visible string needs a translation key in all three languages (`I18nTest` checks this); keep the retro-neon dark look; the public port must only serve images.
- **Translations**: `web/lang/*.json` and `assets/webstream/lang/*.json`. English and Spanish proofreading is very welcome.
- **Never commit** your images, screen exports, `config/webstream/`, passwords or private addresses.
