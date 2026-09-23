# Lootrift

Animated crates for Paper and Folia 1.21: virtual and physical keys, 12 opening animations, bulk
opening, particle effects around crate blocks, per player holograms, ModelEngine models, opening
milestones, a pity system, unique rewards, win history and a full in-game editor.

## Installation

1. Drop `Lootrift.jar` into `plugins/`.
2. Start the server: `config.yml`, `crates.yml` (four example crates) and the `lang/` folder are
   created in `plugins/Lootrift/`, along with the `lootrift.db` database.
3. Place a crate from `/crate admin`.

Optional: Vault (buying keys and money rewards), PlaceholderAPI, ModelEngine (crates as 3D models).

## Languages

`config.yml` holds `language: en`. English and French ship with the plugin (`en`, `fr`).

- `lang/messages_<language>.yml` holds the chat messages.
- `lang/<language>.yml` holds the menu and log texts, and can be edited to reword any of them.
- On first start, `crates.yml` is written in the configured language.

## Usage

Right click a placed crate to open it with a key, left click to preview its rewards. While
sneaking, bulk opening uses every key at once with a dedicated animation. Rewards that do not fit
in the inventory drop on the ground.

## Commands

| Command | Effect |
|---|---|
| `/crate admin` | admin menu: create, edit, place and remove crates |
| `/crate apercu <crate>` | reward preview |
| `/crate ouvrir <crate>` | opens a crate without a block |
| `/crate historique` | win history |
| `/crate give <crate>` | gives you the crate block to place |
| `/crate reload` | reloads `crates.yml` |
| `/cle give <player> <crate> [amount]` | gives virtual keys |
| `/cle take`, `/cle set` | removes or sets keys |
| `/cle physique <player> <crate> [amount]` | gives keys as items |
| `/cle all <crate> [amount]` | gives keys to every online player |

`/cle` is also available as `/key` and `/keys`, `/crate` as `/crates`.

## Permissions

| Permission | Default | Effect |
|---|---|---|
| `lootrift.admin.crates` | op | `/crate` and `/cle` |
| `lootrift.crates.bypass-cooldown` | op | bypasses the delay between two openings |
| `lootrift.alerts.caisses` | op | alerts for undelivered rewards |

## Configuration

`crates.yml` holds the global settings (`settings`) and one section per crate under `crates`.
Everything can also be set from `/crate admin`, which rewrites the file.

| Crate key | Effect |
|---|---|
| `name`, `icon`, `key` | name, menu icon and key appearance |
| `block` or `model` | placed block or ModelEngine model (`blueprint`, animations, scale) |
| `animation`, `bulk-animation` | single and bulk opening animation |
| `rolls` | number of rewards per opening |
| `price`, `daily-key` | key price through Vault, free daily key |
| `permission`, `cooldown-seconds` | access and delay between two openings |
| `pity` | reward of a minimum rarity guaranteed after `after` unlucky openings |
| `milestones` | money bonus and message after a number of openings, repeatable |
| `hologram` | lines above the crate |
| `effects` | particles around the block |
| `rewards` | rewards: item, `weight`, `rarity`, `min-amount`, `max-amount`, `money`, `commands`, `unique`, `announce` |

Rarities: `commun`, `peu-commun`, `rare`, `epique`, `legendaire`, `mythique`.

Opening animations: `csgo`, `roulette`, `cascade`, `roue`, `pulse`, `tombola`, `eclair`,
`horloge`, `vague`, `zoom`, `mosaique`, `instant`.

Bulk animations: `domino`, `inverse`, `avalanche`, `vague`, `spirale`, `explosion`, `implosion`,
`miroir`, `rideau`, `diagonale`, `scanner`, `rafale`.

Reward commands accept `<player>`. Every opening is written to `logs/caisses.log`.

## Building

```bash
mvn package
```

The plugin is built to `target/Lootrift.jar`. Java 21 is required.
