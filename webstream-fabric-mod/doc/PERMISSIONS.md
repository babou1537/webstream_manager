# 🔐 Système de Permissions WebStream

## Comment ça fonctionne ?

WebStream utilise **directement le système d'OPs de Minecraft** pour gérer les permissions.

### ✅ Qui peut modifier le contenu ?

**Seuls les opérateurs Minecraft de niveau 3 ou supérieur** peuvent :
- Créer/supprimer des familles
- Uploader/supprimer des fichiers
- Créer/modifier/supprimer des écrans
- Assigner du contenu aux écrans

### 👀 Qui peut voir le contenu ?

**Tout le monde** peut consulter :
- La liste des écrans
- La bibliothèque de fichiers
- Les familles

---

## 🎮 Comment donner les permissions ?

### Sur le serveur Minecraft :

```
/op <nom_joueur> 3
```

ou

```
/op <nom_joueur> 4
```

**Niveaux d'OP Minecraft :**
- Niveau 1 : Bypass spawn protection
- Niveau 2 : Utiliser /clear, /difficulty, /effect, /gamemode, /gamerule, /give, /tp
- **Niveau 3** : Utiliser /ban, /deop, /kick, /op ← **Minimum pour WebStream**
- Niveau 4 : Utiliser /stop

### Retirer les permissions :

```
/deop <nom_joueur>
```

---

## 📁 Fichier de configuration

Les OPs sont automatiquement lus depuis :
```
ops.json
```

**Exemple de `ops.json` :**
```json
[
  {
    "uuid": "305aa2d8-3985-4e5b-95d4-bb5fea16cd70",
    "name": "babou1537",
    "level": 4,
    "bypassesPlayerLimit": false
  },
  {
    "uuid": "12345678-1234-1234-1234-123456789012",
    "name": "joueur2",
    "level": 3,
    "bypassesPlayerLimit": false
  }
]
```

**Dans cet exemple :**
- ✅ `babou1537` (niveau 4) peut modifier WebStream
- ✅ `joueur2` (niveau 3) peut modifier WebStream
- ❌ Les joueurs non-OP ne peuvent que consulter

---

## 🔄 Rechargement automatique

Les permissions sont rechargées automatiquement **toutes les 30 secondes**.

**Donc :**
- Si vous ajoutez un OP : il aura accès dans les 30 secondes
- Si vous retirez un OP : il perdra l'accès dans les 30 secondes

Pas besoin de redémarrer le serveur Node.js !

---

## 🛠️ Mode développement

**Si `ops.json` n'existe pas ou est vide :**
- ⚠️ Tout le monde est considéré comme admin
- Utile pour le développement en solo

**Sur un serveur de production :**
- Assurez-vous d'avoir au moins un OP configuré
- Sinon, n'importe qui pourra modifier le contenu !

---

## 🔍 Vérifier les permissions

Dans les logs du serveur Node.js, vous verrez :
```
[Permissions] Loaded 2 Minecraft OPs from ops.json
[Permissions] OPs: ['babou1537', 'joueur2']
```

Si vous voyez :
```
[Permissions] ops.json not found at: /path/to/ops.json
[Permissions] Using fallback: all users are admins
```

**→ Configurez au moins un OP sur votre serveur Minecraft !**

---

## ✅ Résumé

| Niveau OP | Peut consulter ? | Peut modifier ? |
|-----------|------------------|-----------------|
| Aucun (joueur normal) | ✅ Oui | ❌ Non |
| Niveau 1-2 | ✅ Oui | ❌ Non |
| **Niveau 3+** | ✅ Oui | ✅ **Oui** |
| Pas d'`ops.json` | ✅ Oui | ⚠️ **Oui** (tous) |

