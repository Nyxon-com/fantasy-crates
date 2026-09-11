# HazeCrates

Plugin crate per **Paper 1.21**. Chiavi fisiche, virtuali e lootbox, editor in game, preview per slot, item vanilla / MMOItems / ItemsAdder / Nexo.

Cartella dati: `plugins/HazeCrates/`  
JAR: `HazeCrates-1.0.0.jar`

## Requisiti

| Dipendenza | Versione | Obbligatorio |
| :--- | :--- | :--- |
| Paper | 1.21.x | Sì |
| Java | 21+ | Sì |
| PlaceholderAPI | 2.11+ | No |
| MMOItems | 6.9+ | No |
| ItemsAdder | 3.x / 4.x | No |
| Nexo | 1.10+ | No |

## Installazione

1. Metti il JAR in `plugins/`.
2. Avvia il server: vengono creati `config.yml`, `messages.yml`, `animations.yml`, `keys.yml` e `crates/example.yml`.
3. Per MySQL/MariaDB modifica `database` in `config.yml`. SQLite parte da solo (`plugins/HazeCrates/data.db`).
4. `/crate reload` o riavvio.

## Quick start

```
/hc editor
/crate item example
/crate give <player> example 10
/crate preview example
/chiave
```

## Comandi

Permessi: `hazecrates.admin` (op), `hazecrates.key` (give/take/set), `hazecrates.item`, `hazecrates.preview` (dichiarato, la preview non lo controlla), `hazecrates.open.*` (default true).

### `/crate`

| Comando | Cosa fa | Permesso |
| :--- | :--- | :--- |
| `/crate give <player> <crate> [qty]` | Chiave fisica o saldo virtuale, in base al `key-type` | `admin` o `key` |
| `/crate take <player> <crate> [qty]` | Toglie prima le chiavi in inventario, poi il saldo virtuale | `admin` o `key` |
| `/crate set <player> <crate> <qty>` | Imposta solo il saldo virtuale | `admin` o `key` |
| `/crate lootbox <player> <crate> [qty]` | Dà il forziere in mano | `admin` o `key` |
| `/crate item <crate> [qty]` | Item per piazzare il blocco crate | `item` o `admin` |
| `/crate place <crate>` | Piazza la crate sul blocco mirato | `admin` |
| `/crate break` | Rimuove la crate mirata (hologram compresi) | `admin` |
| `/crate preview <crate>` | Apre la GUI anteprima | nessuno in codice |
| `/crate open <crate>` | Apre se hai chiave o lootbox | `hazecrates.open.<crate>` o `open.*` |
| `/crate open <player> <crate>` | Stesso controllo sul **target** | `admin` |
| `/open …` | Alias di `/crate open` | uguale |
| `/crate stats [player]` | Conteggio aperture | `admin` |
| `/crate reload` | Ricarica config e crate | `admin` |

### `/hc` e `/chiave`

| Comando | Cosa fa | Permesso |
| :--- | :--- | :--- |
| `/hc editor` | Lista crate | `admin` |
| `/hc editor <crate>` | Editor di quella crate | `admin` |
| `/chiave` (`/chiavi`, `/keys`) | Saldo inventario + virtuale | nessuno |
| `/chiave <crate>` | Saldo di una crate | nessuno |
| `/chiave give\|take\|set …` | Come `/crate give\|take\|set` | `admin` o `key` |

## Chiavi

- **PHYSICAL** — item (`key.item`). Clic destro sul blocco crate.
- **VIRTUAL** — saldo in SQLite/MySQL. Clic destro sul blocco o pulsante Apri in preview.
- **LOOTBOX** — forziere in mano, clic destro ovunque. Usa sempre l’animazione `lootbox`.

`keys.yml` è solo aspetto e suoni. L’ID sotto `keys:` deve coincidere col nome file della crate.

`/crate give` su una crate PHYSICAL/LOOTBOX dà l’item; su VIRTUAL aggiorna il database.

## Reward

L’item in preview è solo display, **tranne** se il reward non ha `commands`.

- Con `commands:` vengono eseguiti solo i command (soldi, give, ecc.). L’icona non viene data.
- Senza `commands:` il plugin dà l’item configurato in `item:` (vanilla, MMOItems, Nexo, ItemsAdder).

`give` accetta entrambi gli ordini e risolve gli ID custom:

```
give %player% diamond 3
give diamond %player% 3
mi give SWORD EXCALIBUR %player% 1
nexo give %player% ruby_sword 1
money give %player% 5000
```

Placeholder nei command: `%player%`.

## Animazioni di apertura

Cinque tipi (`animation:` nella crate). Le lootbox ignorano il campo e usano `lootbox`.

| Nome | Dove |
| :--- | :--- |
| `csgo` | GUI, ticker orizzontale |
| `roulette` | GUI, ruota col puntatore |
| `roll` | Mondo, premi sopra il blocco |
| `spiral` | Mondo, spirale di particelle |
| `lootbox` | Mondo, forziere che si apre |

Idle (`idle-effect:`): `gold_orbit`, `rune_helix`, `amethyst_pulse`, `soul_helix`, `flame_ring`, `portal_spiral`, `cherry_rain`, oppure `none`.

## Preview

Ogni slot si configura in `preview.slots`. Slot `0`–`53`. Range `"10-16"`, lista `"10,13,16"`. `reward` = slot premi (paginati).

Azioni pulsante: `OPEN_CRATE`, `CLOSE`, `NEXT_PAGE`, `PREVIOUS_PAGE`, `COMMAND:<cmd>`, `PLAYER_COMMAND:<cmd>`, `MESSAGE:<msg>`.

Se manca `slots:`, viene ancora letto il layout vecchio (`pattern` / `items` / `reward-slots`).

`%keys%` in preview è il conteggio delle **chiavi fisiche** in inventario, non il saldo virtuale.

## PlaceholderAPI (`%hazecrates_…%`)

| Placeholder | Valore |
| :--- | :--- |
| `opened_<crate>` | Aperture del player |
| `keys_<crate>` | Saldo **virtuale** |
| `milestone_<crate>_<id>` | `n/richieste` o `Completato` |
| `leaderboard_<crate>_<page>_<row>` | `nome - aperture` |
| `leaderboard_<crate>_<page>_<row>_name` | Nome |
| `leaderboard_<crate>_<page>_<row>_amount` | Aperture |

Nella GUI preview (titolo/lore): `%crate%`, `%crate_plain%`, `%player%`, `%keys%`, `%rewards%`, `%totalweight%`, `%page%`, `%total_pages%`, `%prev_page%`, `%next_page%`. Nei lore premio: `%chance%`, `%rarity%`, `%weight%`, `%id%`, `%permission%`.

## Item esterni

| Formato | Esempio |
| :--- | :--- |
| Vanilla | `item: DIAMOND` |
| ItemsAdder | `item: 'itemsadder:pack:ruby_sword'` |
| MMOItems | `item: 'mmoitems:SWORD:EXCALIBUR'` |
| Nexo | `item: 'nexo:ruby_sword'` |

`lore-override: false` (e senza `name`) lascia nome/lore del plugin item. Dopo `/mi reload`, load Nexo o pack ItemsAdder le icone crate si aggiornano.

Blocco display: `display.block: 'nexo:my_crate'` piazza blocco/furniture Nexo se quell’id ce l’ha.

In `/hc editor` tieni l’item e clicca lo slot materiale del reward (senza shift).

## Esempio crate

```yaml
display-name: '<gold>Crate esempio</gold>'
key-type: PHYSICAL
key:
  item: TRIPWIRE_HOOK
animation: csgo
idle-effect: gold_orbit
preview-on-left-click: true

preview:
  enabled: true
  title: '%crate_plain%'
  rows: 5
  slots:
    "10-16": reward
    "19-25": reward
    "39":
      item: NETHER_STAR
      name: '<green>Apri</green>'
      action: OPEN_CRATE

rewards:
  - id: diamanti
    item: DIAMOND
    amount: 3
    commands:
      - 'give %player% diamond 3'
    weight: 80

  - id: soldi
    item: GOLD_INGOT
    amount: 1
    commands:
      - 'money give %player% 5000'
    weight: 20
```

Il secondo reward mostra un lingotto in preview e dà solo i soldi.

## Database

```yaml
database:
  enabled: true
  driver: sqlite   # sqlite | mysql | mariadb
```

SQLite: `plugins/HazeCrates/data.db` (aperture, leaderboard, chiavi virtuali, milestone). MySQL: `driver: mysql`, `host`, `port`, `database`, `user`, `password`.

## License

MIT. Haze / HazeCrates.
