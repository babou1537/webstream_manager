2. S'assurer que Node.js est installé
3. Lancer Minecraft avec Fabric Loader

## Environnement de développement

### Configurer l'IDE

**IntelliJ IDEA**
```cmd
gradlew idea
```

**Eclipse**
```cmd
gradlew eclipse
```

### Lancer en mode dev

```cmd
gradlew runClient
```

Cela lancera Minecraft en mode développement avec le mod chargé.

## Nettoyage

Pour nettoyer les fichiers de build :

```cmd
gradlew clean
```

## Structure après compilation

```
webstream-fabric-mod/
├── build/
│   ├── libs/
│   │   └── webstream-mod-1.0.0.jar    # JAR final
│   ├── classes/                        # Classes compilées
│   └── resources/                      # Ressources copiées
├── .gradle/                            # Cache Gradle
└── run/                                # Env de dev (si runClient)
```

## Dépannage

### Erreur "Could not find fabric-loom"

Vérifier votre connexion internet et relancer :
```cmd
gradlew build --refresh-dependencies
```

### Erreur de version Java

Vérifier que Java 17+ est installé :
```cmd
java -version
```

## Tests

Pour tester le mod avant de compiler :

```cmd
gradlew runClient
```

Le serveur Node.js démarrera automatiquement dans l'environnement de dev.

## Notes importantes

⚠️ **Le répertoire `src/main/resources/webstream-node/` contient l'application Node.js complète**

Ce répertoire est inclus dans le JAR et sera extrait lors de la première exécution du mod.

⚠️ **Node.js doit être installé sur le système cible**

Le mod ne fonctionnera pas sans Node.js installé et présent dans le PATH.
# Gradle
.gradle/
build/
out/
bin/

# IDE
.idea/
*.iml
*.ipr
*.iws
.vscode/
.settings/
.project
.classpath

# Fabric
run/
logs/

# OS
.DS_Store
Thumbs.db

# Node.js (dans les ressources)
src/main/resources/webstream-node/node_modules/
src/main/resources/webstream-node/package-lock.json

# Autres
*.log
*.tmp

