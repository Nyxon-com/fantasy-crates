# ✦ HazeCrates

> **The Ultimate Next-Generation Crate & Lootbox Plugin for Paper 1.21+**  
> Physical, Virtual & Lootbox Key Systems · 7 Modern Opening Animations · 100% Configurable Dedicated Preview Panels · Visual ASCII Pattern Grid Layouts · In-Game Admin Editor GUI · ItemsAdder, MMOItems & Nexo Ready · SQL Milestones & Leaderboards.

---

## 📑 Table of Contents

1. [✨ Features](#-features)
2. [📦 Requirements & Compatibility](#-requirements--compatibility)
3. [🚀 Installation](#-installation)
4. [⚡ Quick Start](#-quick-start)
5. [💻 Commands & Permissions](#-commands--permissions)
6. [🔮 PlaceholderAPI Reference](#-placeholderapi-reference)
7. [🎛️ Preview GUI System (Slot-by-Slot & Visual Patterns)](#-preview-gui-system)
8. [🔑 Key Systems (Physical, Virtual, Lootbox)](#-key-systems)
9. [🎬 Opening & Ambient Animations](#-opening--ambient-animations)
10. [⚙️ Configuration Guide](#-configuration-guide)
    - [crates/*.yml (Dedicated Crate Config)](#cratesyml-example)
    - [config.yml (Global & Default Preview)](#configyml)
    - [keys.yml (Per-Key Config)](#keysyml)
    - [External items (ItemsAdder, MMOItems, Nexo)](#-external-items-itemsadder-mmoitems-nexo)
11. [Database Setup (MySQL / MariaDB / SQLite)](#-database-setup)

---

## ✨ Features

- **🎨 Dedicated Preview GUI Panel Per Crate**: Every crate can define its own unique preview GUI with custom rows, RGB gradient titles, visual ASCII pattern grids, slot-by-slot items, interactive buttons (`OPEN_CRATE`, `CLOSE`, `NEXT_PAGE`, `PREVIOUS_PAGE`, `COMMAND:`), custom click sounds, and automatic multi-page pagination!
- **🔑 3 Key Architectures**:
  - **PHYSICAL**: Traditional item keys consumed on crate block interaction.
  - **VIRTUAL**: Key balance stored asynchronously in MySQL/SQLite database with `/chiave` command balance viewer.
  - **LOOTBOX**: Portable lootbox items — right-click anywhere (air or block) to spin and claim rewards!
- **🎬 Opening Animations**:
  - `csgo` — GUI ticker orizzontale (stile ExcellentCrates / CSGO)
  - `roulette` — GUI ruota con puntatore
  - `roll` — 3D, premi che passano sopra la crate
  - `spiral` — 3D, spirale di particelle e premio
  - `lootbox` — 3D, forziere che sale e si apre (forzata sulle lootbox)
- **🛠️ In-Game GUI Admin Editor (`/hc editor`)**: Manage crates, create new crates, edit display names, materials, animations, holograms, particles, and rewards visually without touching files.
- **🔌 Full External Items Support**: Natively compatible with vanilla materials, **CustomModelData**, **ItemsAdder**, **MMOItems**, and **Nexo**.
- **🏆 Milestones & SQL Leaderboards**: Track opening statistics, award milestone rewards after X opens, and display top players via PlaceholderAPI.

---

## 📦 Requirements & Compatibility

| Dependency | Required Version | Status |
| :--- | :--- | :--- |
| **Paper / Purpur** | **1.21.x** | **Required** |
| **Java** | **21+** | **Required** |
| **PlaceholderAPI** | 2.11+ | *Optional* |
| **ItemsAdder** | 4.x / 3.x | *Optional* |
| **MMOItems** | 6.9+ | *Optional* |
| **Nexo** | 1.10+ | *Optional* |

---

## 🚀 Installation

1. Place `HazeCrates.jar` in your server's `plugins/` directory.
2. Start the server to generate default configurations in `plugins/HazeCrates/`.
3. (Optional) Configure database credentials in `config.yml` if using MySQL/MariaDB for Virtual Keys or Leaderboards.
4. Reload or restart the server.

---

## ⚡ Quick Start

```bash
# 1. Open the interactive Crate Editor GUI
/hc editor

# 2. Get a placeable crate block item in your hand
/crate item example

# 3. Give keys to a player (crate type decides physical vs virtual)
/crate give Steve example 10

# 4. Preview crate rewards in the custom GUI
/crate preview example

# 5. Check key balance
/chiave
```

---

## 💻 Commands & Permissions

### 📦 `/crate` — Main Admin & Player Command

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/crate give <player> <crate> [amount]` | Gives keys. Physical crate = item, virtual crate = balance | `hazecrates.admin` or `hazecrates.key` |
| `/crate take <player> <crate> [amount]` | Removes keys from inventory then virtual balance | `hazecrates.admin` or `hazecrates.key` |
| `/crate set <player> <crate> <amount>` | Sets the virtual key balance | `hazecrates.admin` or `hazecrates.key` |
| `/crate lootbox <player> <crate> [amount]` | Gives a handheld lootbox item | `hazecrates.admin` or `hazecrates.key` |
| `/crate item <crate> [amount]` | Gives a placeable crate block item | `hazecrates.item` or `hazecrates.admin` |
| `/crate place <crate>` | Places a crate on the targeted block | `hazecrates.admin` |
| `/crate break` | Removes the targeted crate | `hazecrates.admin` |
| `/crate preview <crate>` | Opens the reward preview GUI | `hazecrates.preview` |
| `/crate open <crate>` | Apre la crate se hai chiave o forziere | `hazecrates.open.<crate>` o `hazecrates.open.*` |
| `/crate open <player> <crate>` | Apre la crate per un altro player (deve averla lui) | `hazecrates.admin` |
| `/open <crate>` | Stesso di `/crate open` | `hazecrates.open.<crate>` o `hazecrates.open.*` |
| `/open <player> <crate>` | Stesso di `/crate open <player> <crate>` | `hazecrates.admin` |
| `/crate stats [player]` | Shows opening counts | `hazecrates.admin` |
| `/crate reload` | Reloads configs | `hazecrates.admin` |

### 🛠️ `/hc` — In-Game GUI Editor Command

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/hc editor` | Opens the main interactive Crate Manager GUI | `hazecrates.admin` |
| `/hc editor <crate>` | Opens the editor directly for a specific crate | `hazecrates.admin` |

### 🔑 `/chiave` — Key Balance Command

| Command | Aliases | Description | Permission |
| :--- | :--- | :--- | :--- |
| `/chiave` | `/chiavi`, `/keys` | Lists inventory and virtual keys | *None* |
| `/chiave <crate>` | | Balance for one crate | *None* |
| `/chiave give <player> <crate> [amount]` | | Same as `/crate give` | `hazecrates.admin` or `hazecrates.key` |

---

## 🔮 PlaceholderAPI Reference

### 🌐 Global & Player Placeholders

| Placeholder | Description |
| :--- | :--- |
| `%hazecrates_opened_<crate>%` | Total number of times the player has opened `<crate>`. |
| `%hazecrates_keys_<crate>%` | Player's current virtual key balance for `<crate>`. |
| `%hazecrates_milestone_<crate>_<milestone>%` | Displays milestone progress/status. |
| `%hazecrates_leaderboard_<crate>_<page>_<row>%` | Formatted top player name and score for `<crate>`. |
| `%hazecrates_leaderboard_<crate>_<page>_<row>_name%` | Top player name for `<crate>` at rank. |
| `%hazecrates_leaderboard_<crate>_<page>_<row>_amount%` | Top player opening count for `<crate>` at rank. |

### 🎛️ Internal Preview GUI Placeholders
These placeholders are automatically available in all `title`, `name`, and `lore` lines of crate preview panels:

| Placeholder | Replacement Value |
| :--- | :--- |
| `%crate%` | Crate display name with MiniMessage formatting |
| `%crate_plain%` | Plain-text crate display name without colors |
| `%player%` | Viewer's username |
| `%keys%` | Number of physical keys matching this crate in player's inventory |
| `%rewards%` | Total number of rewards defined in the crate |
| `%totalweight%` | Total weight sum of all crate rewards |
| `%page%` | Current preview page number |
| `%total_pages%` | Total number of preview pages |
| `%prev_page%` | Previous page number |
| `%next_page%` | Next page number |
| `%chance%` | Coloured percentage chance of reward (in reward lore) |
| `%rarity%` | Formatted rarity badge (in reward lore) |
| `%weight%` | Weight of reward (in reward lore) |
| `%id%` | ID of reward (in reward lore) |
| `%permission%` | Permission requirement of reward (in reward lore) |

---

## 🎛️ Preview GUI System

Every crate can have its own preview panel. **Each inventory slot is configured directly** — no ASCII pattern.

Slots are `0`–`53` (row-major, 9 columns). Use a single slot (`"39"`), a range (`"10-16"`), or a comma list (`"10,13,16"`).

```yaml
preview:
  enabled: true
  title: '%crate_plain%'
  rows: 5
  filler:
    item: BLACK_STAINED_GLASS_PANE
    name: ' '
    fill-empty: true
  slots:
    "0-8":
      item: GRAY_STAINED_GLASS_PANE
      name: ' '
    "9":
      item: GRAY_STAINED_GLASS_PANE
      name: ' '
    "10-16": reward
    "17":
      item: GRAY_STAINED_GLASS_PANE
      name: ' '
    "19-25": reward
    "28-34": reward
    "38":
      item: ARROW
      name: '<gray>Pagina precedente</gray>'
      sound: UI_BUTTON_CLICK
      action: PREVIOUS_PAGE
    "39":
      item: NETHER_STAR
      name: '<green>Apri</green>'
      lore:
        - '<gray>Chiavi: <white>%keys%</white></gray>'
      sound: UI_BUTTON_CLICK
      action: OPEN_CRATE
    "41":
      item: BARRIER
      name: '<red>Chiudi</red>'
      sound: UI_BUTTON_CLICK
      action: CLOSE
    "42":
      item: ARROW
      name: '<gray>Pagina successiva</gray>'
      sound: UI_BUTTON_CLICK
      action: NEXT_PAGE
```

`reward` marks slots that show crate prizes (paginated if there are more rewards than slots). Any other slot is a button or decoration: set `item`, `name`, `lore`, `glow`, `sound`, and `action`.

Old `pattern:` + `items:` layouts still load if `slots:` is missing.

### ⚡ Supported Button Actions

| Action | Behavior |
| :--- | :--- |
| `OPEN_CRATE` / `OPEN` | Consumes a physical or virtual key and launches the opening animation! |
| `CLOSE` | Closes the preview inventory. |
| `NEXT_PAGE` | Navigates to the next page of rewards. |
| `PREVIOUS_PAGE` | Navigates to the previous page of rewards. |
| `COMMAND:<cmd>` | Runs a console command (supports `%player%`, `%crate%`). Example: `COMMAND:give %player% cookie 1` |
| `PLAYER_COMMAND:<cmd>` | Forces the player to execute a command. Example: `PLAYER_COMMAND:shop` |
| `MESSAGE:<msg>` | Sends a MiniMessage-formatted message to the player. |

---

## 🔑 Key Systems

1. **PHYSICAL**:
   - Traditional item held in player hand.
   - Defined via `key.item` in crate YAML (e.g. `TRIPWIRE_HOOK`, `itemsadder:custom_key`, `nexo:crate_key`).
   - Player right-clicks the crate block to consume 1 key and open.
2. **VIRTUAL**:
   - Stored in MySQL/SQLite database.
   - Configured in `keys.yml`.
   - Admin gives keys via `/crate give <player> <crate> [amount]`.
   - Players view balance via `/chiave` and right-click the crate block or click "Open Crate" in Preview.
3. **LOOTBOX**:
   - Handheld lootbox crate.
   - Right-click anywhere (air or block): the chest opens, rewards fly out one by one and orbit, then the winner rises above the chest.
   - The item lore automatically lists every reward with its rarity (disable with `lootbox.auto-lore: false`).

---

## 🎬 Opening & Ambient Animations

### 🎰 Opening Animations (`animation: <name>`)
- **`csgo`**: Ticker orizzontale, il premio e l item sotto i vetri.
- **`roulette`**: Ruota intorno al puntatore.
- **`roll`**: I premi scorrono sopra il blocco, poi si bloccano.
- **`spiral`**: Spirale di particelle, poi compare il premio.
- **`lootbox`**: Forziere vero (coperchio che si alza), i premi escono uno a uno e girano, poi il vincitore sale sopra la cassa. Le crate `LOOTBOX` la usano sempre.

### ✨ Ambient Idle Effects (`idle-effect: <name>`)
- `gold_orbit`
- `rune_helix`
- `amethyst_pulse`
- `soul_helix`
- `flame_ring`
- `portal_spiral`
- `cherry_rain`

---

## ⚙️ Configuration Guide

### `crates/*.yml` Example

```yaml
display-name: '<gradient:#55FFFF:#0080FF><bold>Example Crate</bold></gradient>'

key-type: PHYSICAL
key:
  item: TRIPWIRE_HOOK

# Opening animation from animations.yml
animation: csgo

display:
  block: CHEST
  custom-model-data: 0
  glowing-outline: false

  hologram:
    lines:
      - '<gradient:#55FFFF:#0080FF><bold>EXAMPLE CRATE</bold></gradient>'
      - '<gray>Right-click with a key to open</gray>'
    height-offset: 1.5
    refresh-rate: 40

  idle-effect: flame_ring

  # Vuoto = usa opening-title in config.yml
  title:
    text: ''
    subtitle: ''

broadcast: '<gold>✦ <yellow>%player%</yellow> <gold>found <white>%reward%</white><gold>!</gold>'
broadcast-threshold: 15
preview-on-left-click: true

# Dedicated Preview GUI Panel
preview:
  enabled: true
  title: '<gradient:#55FFFF:#0080FF><bold>%crate% Preview</bold></gradient>'
  rows: 6
  reward-slots: [10-16, 19-25, 28-34]
  filler:
    item: BLACK_STAINED_GLASS_PANE
    name: '<dark_gray>'
    fill-empty: true
  items:
    border_cyan:
      slots: [0-8, 45, 53]
      item: CYAN_STAINED_GLASS_PANE
      name: '<dark_gray>'
    info:
      slot: 49
      item: BOOK
      name: '<gradient:#55FFFF:#0080FF><bold>%crate% Info</bold></gradient>'
      glow: true
    open_btn:
      slot: 48
      item: NETHER_STAR
      name: '<gradient:#55FF55:#00AA00><bold>✦ OPEN CRATE ✦</bold></gradient>'
      sound: UI_BUTTON_CLICK
      action: OPEN_CRATE
    close_btn:
      slot: 50
      item: BARRIER
      name: '<red><bold>Close</bold></red>'
      sound: UI_BUTTON_CLICK
      action: CLOSE

# Rewards Pool
rewards:
  - id: diamonds
    item: DIAMOND
    amount: 3
    name: '<aqua>3x Diamonds</aqua>'
    lore:
      - '<gray>A shiny valuable prize</gray>'
    glow: false
    commands:
      - 'give %player% diamond 3'
    weight: 80

  - id: netherite
    item: NETHERITE_INGOT
    amount: 1
    name: '<gradient:#FF5555:#FFAA00><bold>Netherite Ingot</bold></gradient>'
    glow: true
    commands:
      - 'give %player% netherite_ingot 1'
    weight: 5
    broadcast: true
```

---

## 🔌 External items (ItemsAdder, MMOItems, Nexo)

Anywhere you set `item:` (rewards, keys, preview slots, display block) you can use a vanilla material **or** a custom-item prefix.

| Format | Plugin | Example |
| :--- | :--- | :--- |
| `DIAMOND` | Vanilla | `item: DIAMOND` |
| `itemsadder:namespace:id` | ItemsAdder | `item: 'itemsadder:my_pack:ruby_sword'` |
| `mmoitems:TYPE:ID` | MMOItems | `item: 'mmoitems:SWORD:TERRATRON'` |
| `nexo:item_id` | Nexo | `item: 'nexo:ruby_sword'` |

Nexo uses the **item id** from your Nexo configs (the same id as `/nexo give`), not a namespace.

```yaml
  - id: nexo_sword
    item: 'nexo:ruby_sword'
    amount: 1
    lore-override: false   # keep Nexo name + lore
    commands:
      - 'nexo give %player% ruby_sword 1'
    weight: 10
```

- Leave `name` empty and `lore-override: false` so preview follows the live Nexo / ItemsAdder / MMOItems template.
- The preview icon is **not** given to the player. Always add a `commands:` line (`nexo give`, `mmoitems give`, `iagive`, …).
- In `/hc editor`, hold the custom item and click the reward item slot (no shift-click) to bind it.
- After `/nexo reload` (or Nexo finishing its item load), crate icons refresh automatically. Same for ItemsAdder pack load and `/mi reload`.
- Display blocks: `display.block: 'nexo:my_crate'` places a Nexo custom block or furniture if that id has those mechanics.

---

## 🗄️ Database Setup

SQLite si crea da solo in `plugins/HazeCrates/data.db` (aperture, leaderboard, chiavi virtuali, milestone). Per MySQL/MariaDB:

```yaml
database:
  enabled: true
  driver: mysql # mysql | mariadb | sqlite
  host: localhost
  port: 3306
  database: minecraft
  user: root
  password: 'your_password'
  pool-size: 4
  connection-timeout-ms: 5000
  useSSL: false
```

---

## 📜 License

Distributed under the **MIT License**. Created by Haze / HazeCrates.
