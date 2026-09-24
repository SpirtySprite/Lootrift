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
| `/crate preview <crate>` | reward preview |
| `/crate open <crate>` | opens a crate without a block |
| `/crate history` | win history |
| `/crate give <crate>` | gives you the crate block to place |
| `/crate reload` | reloads `crates.yml` |
| `/cle give <player> <crate> [amount]` | gives virtual keys |
| `/cle take`, `/cle set` | removes or sets keys |
| `/cle physical <player> <crate> [amount]` | gives keys as items |
| `/cle all <crate> [amount]` | gives keys to every online player |

`/cle` is also available as `/key` and `/keys`, `/crate` as `/crates`.

## Permissions

| Permission | Default | Effect |
|---|---|---|
| `lootrift.admin.crates` | op | `/crate` and `/cle` |
| `lootrift.crates.bypass-cooldown` | op | bypasses the delay between two openings |
| `lootrift.alerts.crates` | op | alerts for undelivered rewards |

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

### Reward types

A reward can combine an item, money, experience and commands:

```yaml
rewards:
  ruby:
    custom-item: "itemsadder:ruby"
    weight: 20
    rarity: rare
  pouch:
    material: GOLD_NUGGET
    give-item: false
    money: "100-500"
    xp: 30
  rank:
    material: NAME_TAG
    give-item: false
    commands:
      - "lp user <player> parent addtemp vip 7d"
```

- `custom-item` takes an item from `itemsadder:<id>`, `nexo:<id>`, `oraxen:<id>` or
  `mmoitems:<type>:<id>`. The plugin only needs to be installed; if the item is missing, the console
  says so and the reward falls back to its `material`.
- `money` is a fixed amount or a range. A range is drawn each time, and the chat shows the amount won.
- `xp` gives experience points.
- `commands` run from the console with `<player>` replaced, so any plugin can be a reward:
  LuckPerms ranks and permissions, other crate keys, titles.

Rarities: `commun`, `peu-commun`, `rare`, `epique`, `legendaire`, `mythique`.

Opening animations: `csgo`, `roulette`, `cascade`, `roue`, `pulse`, `tombola`, `eclair`,
`horloge`, `vague`, `zoom`, `mosaique`, `instant`.

Bulk animations: `domino`, `inverse`, `avalanche`, `vague`, `spirale`, `explosion`, `implosion`,
`miroir`, `rideau`, `diagonale`, `scanner`, `rafale`.

Reward commands accept `<player>`. Every opening is written to `logs/caisses.log`.

`/crate reload` rereads `config.yml`, the language files and `crates.yml`. Changing `language`
takes full effect after a restart.

## Earning keys

Besides `/cle give`, keys can come on their own. In `config.yml`:

```yaml
key-sources:
  playtime:
    enabled: true
    every-minutes: 60
    crate: common
    amount: 1
  drops:
    - trigger: kill
      target: ZOMBIE
      chance: 0.5%
      crate: common
    - trigger: mine
      target: DIAMOND_ORE
      chance: 2%
      crate: rare
```

- `playtime` gives keys to every online player after each `every-minutes` of play.
- `drops` roll a chance on each kill (`target` is an entity type) or each block mined (`target` is a
  block), and `*` matches anything. Blocks broken in creative mode never drop keys.
- Votes: point your vote plugin's reward command at `cle give %player% vote 1`.

`/crate reload` applies changes to these sources right away.

## Importing from other crate plugins

Keep the other plugin's folder in `plugins/`, then run `/crate import <source>`:

| Source | Reads |
|---|---|
| `crazycrates` | `plugins/CrazyCrates/crates/*.yml` (current and legacy prize formats) and virtual keys from `data.yml` |
| `excellentcrates` | `plugins/ExcellentCrates/crates/*.yml` and virtual keys from its SQLite database |

Each crate becomes a Lootrift crate with the same id, its name, icon, key item and rewards. Weights
keep their proportions and a rarity is picked from each reward's share unless the source names one.
Reward commands are kept, `%player%` becomes `<player>`. Key balances are added to the players'
Lootrift keys.

A crate whose id already exists in Lootrift is skipped, so running the import twice does not
duplicate anything. Anything that could not be carried over (items from other item plugins, prizes
with several items, MySQL key storage) is listed in the console after the import.

## Placeholders

With PlaceholderAPI:

| Placeholder | Value |
|---|---|
| `%lootrift_crates%` | number of crates |
| `%lootrift_keys_total%` | virtual keys of the player, all crates |
| `%lootrift_keys_<crate>%` | virtual keys of the player for a crate |
| `%lootrift_opened_<crate>%` | openings of a crate by the player |
| `%lootrift_cooldown_<crate>%` | seconds before the player can open the crate again |

## Developer API

Add Lootrift as a `depend` or `softdepend`, then get the service:

```java
LootriftApi.get().ifPresent(lootrift -> {
    lootrift.giveKeys(player.getUniqueId(), "rare", 3);
    int keys = lootrift.keys(player.getUniqueId(), "rare");
    lootrift.open(player, "rare");
});
```

`LootriftApi` covers crate ids, virtual keys (read, give, take, set), physical keys, opening
counts, opening a crate with the usual animation and opening the preview. Unknown crates and
non-positive amounts throw `IllegalArgumentException`.

Events:

| Event | When |
|---|---|
| `CrateOpenEvent` | before a key is used, cancellable, `openings()` is above 1 for bulk openings |
| `CrateRewardEvent` | after each reward is delivered, with crate, reward id, rarity, amount and money |

## Updates and metrics

On start Lootrift checks the latest GitHub release and tells the console and players with
`lootrift.admin.crates` when a newer version exists. Set `update-checker: false` in `config.yml` to
turn it off. Anonymous usage statistics go through bStats and follow the global bStats opt-out in
`plugins/bStats/config.yml`.

## Building

```bash
mvn package
```

The plugin is built to `target/Lootrift.jar`. Java 21 is required.
