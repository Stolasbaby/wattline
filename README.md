# Wattline

Solar panels, cables and mining rigs for Paper and Purpur.

![Minecraft 1.21.10](https://img.shields.io/badge/Minecraft-1.21.10-3D8C40?style=flat-square)
![Java 21](https://img.shields.io/badge/Java-21-E76F00?style=flat-square)
![Paper or Purpur](https://img.shields.io/badge/server-Paper%20%7C%20Purpur-2F6FED?style=flat-square)
![License MIT](https://img.shields.io/badge/license-MIT-lightgrey?style=flat-square)

Wattline is a power grid you place in the world. Panels make current while the sun is up, copper wire and utility poles carry it, and ASICs turn that current into tokens. Batteries keep the network alive after dark. An exchange terminal sells the tokens for Vault money.

The plugin id on the server is still **MinerRig**, so an existing world keeps the machines already saved in `plugins/MinerRig`. The command is `/miner`.

## Machines

| Piece | What it does |
| --- | --- |
| Solar panel | Makes current in daylight. Four tiers: 1, 2, 4, then 8. |
| Wire | Links two machines up to 16 blocks apart. |
| Utility pole | Extends a line. 32 blocks from a pole to a machine, 48 blocks between poles. Up to two lamps. |
| ASIC | Mines one token every 15 seconds while it has current. |
| Server rack | Holds ASIC blades. Each blade draws its own current. |
| Battery | Stores leftover daytime current and feeds the grid at night. |
| Exchange | Draws 1 current. Buys and sells tokens. The rate moves. |
| Wallet | Personal token purse. Coins stay on the item. |
| Brush | Sweeps dust off an ASIC or a rack. It wears out. |
| Lamp | Hangs on a pole. Gives real light and draws no current. |

Cables stop at solid blocks. A pole is how a line gets past a wall: the wire attaches to the side insulators, not through the block.

## Requirements

- Minecraft **1.21.10**
- **Paper** or **Purpur**
- **Java 21**

Optional:

- [Vault](https://github.com/MilkBowl/Vault) and an economy plugin, if you want the exchange to pay in server money
- [ProtocolLib 5.3.0](https://github.com/dmulloy2/ProtocolLib/releases), for smoother machine animations

The plugin still loads if those two are missing. Without Vault the exchange cannot move money. Without ProtocolLib the machines still run.

## Install

1. Download `MinerRig-1.1.0.jar` from the [latest release](https://github.com/Stolasbaby/wattline/releases/latest).
2. Put the jar in the server `plugins` folder.
3. Restart the server. Reloading a plugin that spawns displays is a bad idea; restart.
4. Join and run `/miner`.

To remove it, stop the server, delete `plugins/MinerRig-1.1.0.jar`, and delete `plugins/MinerRig` if you also want the saved grid gone. Leaving that folder in place keeps every machine for the next start.

## First grid

1. Open `/miner` and take a panel, a wire and an ASIC. Operators can also run `/miner give`.
2. Place the panel in open sky and the ASIC nearby.
3. Hold the wire, right-click the panel, then right-click the ASIC. In daylight the ASIC starts mining.
4. Tokens collect in your wallet. The count is written on the wallet and in `/miner`.
5. Place a battery on the same network if the rigs should keep running at night.
6. Place an exchange, give it current, and sell tokens there once Vault is installed.

Upgrades are paid in tokens from the machine's own menu.

| Upgrade | Cost |
| --- | --- |
| Tier 1 → 2 | 16 tokens |
| Tier 2 → 3 | 48 tokens |
| Tier 3 → 4 | 128 tokens |

Panel and battery tiers use the same steps. A tier 4 machine outputs 8 current. The admin battery is separate: it outputs 64, never drains, and does not charge other batteries. Only an operator can give that one.

## Commands

| Command | Who | What |
| --- | --- | --- |
| `/miner` | everyone | Opens the mining menu. |
| `/miner menu` | everyone | Same menu. |
| `/miner settings` | everyone | Sound, animations and language. |
| `/miner language` | everyone | Shows your language. |
| `/miner language <code>` | everyone | Sets your language, or `auto` to follow the client. |
| `/miner language <player> <code>` | operators | Sets another player's language. |
| `/miner reload` | operators | Reads `config.yml` and the language files again. |
| `/miner give <item> [player]` | operators | Puts an item in an inventory. |

Items you can give: `asic`, `panel`, `wire`, `pole`, `rack`, `terminal`, `battery`, `adminbattery`, `wallet`, `brush`, `lamp`.

```
/miner give asic
/miner give battery Steve
/miner give adminbattery
```

## Permissions

| Permission | Default | What it allows |
| --- | --- | --- |
| `miner.use` | everyone | Place machines and open your own menu. |
| `miner.break` | everyone | Pick up your own machines. |
| `miner.wire` | everyone | Pull cable from machines you can open. |
| `miner.give` | operators | Take items from the catalog and `/miner give` to yourself. |
| `miner.give.others` | operators | Give an item to another player. |
| `miner.admin` | operators | Open, wire and pick up other players' machines, and take the admin battery. |
| `miner.language` | everyone | Change your own language. |
| `miner.language.others` | operators | Change another player's language. |
| `miner.reload` | operators | Reload `config.yml` and the language files. |

Collecting tokens, inserting blades and sweeping dust stay with the owner. An operator with `miner.admin` can open, wire and pick up someone else's machine, but the tokens still go to the owner's wallet.

## Languages

The menu follows each player. `auto` uses the first two letters of the Minecraft client locale. A fixed code in `plugins/MinerRig/config.yml` is the fallback, and it is also the language of the exchange board in the world (that board is one display, shared by everyone).

```yaml
language: auto
```

Codes: `en`, `ru`, `uk`, `de`, `es`, `fr`, `pt`, `zh`, `pl`, `tr`.

In game, open `/miner settings` and click the language button, or run `/miner language ru`. The files in `plugins/MinerRig/lang/` can be edited. A missing line falls back to English. `/miner reload` picks up language edits and `config.yml`. Changing language also renames the Wattline items already in that player's inventory.

## Config

Gameplay numbers live in `plugins/MinerRig/config.yml`. The file is created on first start, with a note on every setting. Defaults match the table in this guide: a token every 15 seconds, panel and battery tiers of 1, 2, 4 and 8, wire of 16 blocks, a pole reach of 32 and 48, and an exchange that drifts between 4 and 250.

`/miner reload` applies a saved config without a restart. The live exchange rate stays in `market.yml`. A new `config.yml` from this version replaces an older file that only stored the language, and it keeps the language you had set.

## How current works

- Daytime is world time outside 12000–23000, unless `config.yml` says otherwise. Panels also need open sky.
- One ASIC, one rack blade and the exchange each draw the current set in the config. The default is 1.
- Spare daytime current flows into batteries. At night, batteries give it back. Reserve time is `battery.reserve-seconds`, 600 by default.
- A full battery stops accepting current. An empty one stops the night shift.
- Dust builds on a running ASIC or rack and slows it. A full coat leaves about a quarter of the speed, unless `dust.full-speed` is changed. Sweep it with the brush.
- Machines also warm up. A choked or hot unit produces less.

Poles carry up to two lamps. The lamps sit on the side insulators and light the block above the pole. They are decoration; they are not part of the power math.

## Saved data

Everything lives in `plugins/MinerRig/`.

| File | Contents |
| --- | --- |
| `nodes.yml` | Machines, links, charge, dust and tiers |
| `market.yml` | Exchange rate |
| `players.yml` | Personal sound, animation and language |
| `config.yml` | Language, tiers, reach, dust and the exchange |
| `lang/` | The ten language files |

Stop the server before you edit those files. Take a copy first.

## Build

```bash
mvn package
```

The jar is written to `target/MinerRig-1.1.0.jar`. Java 21 is required. Paper's API is downloaded by Maven and is not packed into the jar.

## Credits

Author: **Stolas**.

The machines are built from BlockDisplays in game, not from a separate resource pack. [Vault](https://github.com/MilkBowl/Vault) is used for the exchange when it is installed. [ProtocolLib](https://github.com/dmulloy2/ProtocolLib) is used for animation packets when it is installed.

The code was written with Cursor.

## License

[MIT](LICENSE).
