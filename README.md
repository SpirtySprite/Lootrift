# Lootrift

Caisses animées pour Paper et Folia 1.21 : clés virtuelles et physiques, 12 animations d'ouverture,
ouverture groupée, effets de particules autour des blocs, hologrammes par joueur, modèles
ModelEngine, paliers d'ouvertures, système de pitié, récompenses uniques, historique des gains et
éditeur complet en jeu.

## Installation

1. Placez `Lootrift.jar` dans `plugins/`.
2. Démarrez le serveur : `crates.yml` (quatre caisses d'exemple) et `messages.yml` sont créés dans
   `plugins/Lootrift/`, avec la base `lootrift.db`.
3. Posez une caisse depuis `/crate admin`.

Optionnel : Vault (achat de clés et récompenses en argent), PlaceholderAPI, ModelEngine (caisses
en modèle 3D).

## Utilisation

Clic droit sur une caisse posée pour l'ouvrir avec une clé, clic gauche pour l'aperçu des
récompenses. Accroupi, l'ouverture groupée utilise toutes les clés d'un coup avec une animation
dédiée. Les récompenses qui ne tiennent pas dans l'inventaire tombent au sol.

## Commandes

| Commande | Effet |
|---|---|
| `/crate admin` | menu d'administration : créer, éditer, poser et retirer des caisses |
| `/crate apercu <caisse>` | aperçu des récompenses |
| `/crate ouvrir <caisse>` | ouvre une caisse sans bloc |
| `/crate historique` | historique des gains |
| `/crate give <caisse>` | reçoit le bloc de la caisse à poser |
| `/crate reload` | recharge `crates.yml` |
| `/cle give <joueur> <caisse> [quantité]` | donne des clés virtuelles |
| `/cle take`, `/cle set` | retire ou fixe des clés |
| `/cle physique <joueur> <caisse> [quantité]` | donne des clés en objet |
| `/cle all <caisse> [quantité]` | donne des clés à tous les joueurs connectés |

## Permissions

| Permission | Par défaut | Effet |
|---|---|---|
| `lootrift.admin.crates` | op | `/crate` et `/cle` |
| `lootrift.crates.bypass-cooldown` | op | ignore le délai entre deux ouvertures |
| `lootrift.alerts.caisses` | op | alertes de récompenses non remises |

## Configuration

`crates.yml` contient les réglages globaux (`settings`) et une section par caisse sous `crates`.
Tout se règle aussi depuis `/crate admin`, qui réécrit le fichier.

| Clé d'une caisse | Effet |
|---|---|
| `name`, `icon`, `key` | nom, icône du menu et apparence de la clé |
| `block` ou `model` | bloc posé ou modèle ModelEngine (`blueprint`, animations, échelle) |
| `animation`, `bulk-animation` | animation d'ouverture simple et groupée |
| `rolls` | nombre de récompenses par ouverture |
| `price`, `daily-key` | prix d'une clé via Vault, clé gratuite quotidienne |
| `permission`, `cooldown-seconds` | accès et délai entre deux ouvertures |
| `pity` | récompense de rareté minimale garantie après `after` ouvertures sans chance |
| `milestones` | bonus en argent et message après un nombre d'ouvertures, répétables |
| `hologram` | lignes au-dessus de la caisse |
| `effects` | particules autour du bloc |
| `rewards` | récompenses : objet, `weight`, `rarity`, `min-amount`, `max-amount`, `money`, `commands`, `unique`, `announce` |

Animations d'ouverture : `csgo`, `roulette`, `cascade`, `roue`, `pulse`, `tombola`, `eclair`,
`horloge`, `vague`, `zoom`, `mosaique`, `instant`.

Animations groupées : `domino`, `inverse`, `avalanche`, `vague`, `spirale`, `explosion`,
`implosion`, `miroir`, `rideau`, `diagonale`, `scanner`, `rafale`.

Les commandes de récompense acceptent `<player>`. Chaque ouverture est écrite dans
`logs/caisses.log`.

## Compilation

```bash
mvn package
```

Le plugin se trouve dans `target/Lootrift.jar`. Java 21 est requis.
