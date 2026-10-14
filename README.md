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

1. Download `MinerRig-1.0.0.jar` from the [latest release](https://github.com/Stolasbaby/wattline/releases/latest).
2. Put the jar in the server `plugins` folder.
3. Restart the server. Reloading a plugin that spawns displays is a bad idea; restart.
4. Join and run `/miner`.

To remove it, stop the server, delete `plugins/MinerRig-1.0.0.jar`, and delete `plugins/MinerRig` if you also want the saved grid gone. Leaving that folder in place keeps every machine for the next start.

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
| `/miner settings` | everyone | Personal settings. |
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
| `miner.use` | everyone | Place machines and use your own. |
| `miner.give` | operators | Open the item catalog and run `/miner give`. |
| `miner.admin` | operators | Open and break other players' machines. |

A normal player cannot pick up someone else's ASIC. An operator with `miner.admin` can.

## How current works

- Daytime is world time outside 12000–23000. Panels produce only then.
- One ASIC, one rack blade and the exchange each need current every second they run.
- Spare daytime current flows into batteries. At night, batteries give it back.
- A full battery stops accepting current. An empty one stops the night shift.
- Dust builds on a running ASIC or rack and slows it. A full coat leaves about a quarter of the speed. Sweep it with the brush.
- Machines also warm up. A choked or hot unit produces less.

Poles carry up to two lamps. The lamps sit on the side insulators and light the block above the pole. They are decoration; they are not part of the power math.

## Menus are in Russian

The items and menus use the names the server already plays with.

| In game | In this guide |
| --- | --- |
| Асик | ASIC |
| Солнечная панель | Solar panel |
| Провод | Wire |
| Электростолб | Utility pole |
| Серверная стойка | Server rack |
| Аккумулятор | Battery |
| Обменник | Exchange |
| Кошелёк | Wallet |
| Жетон | Token |
| Кисть | Brush |
| Лампочка | Lamp |

## Saved data

Everything lives in `plugins/MinerRig/`.

| File | Contents |
| --- | --- |
| `nodes.yml` | Machines, links, charge, dust and tiers |
| `market.yml` | Exchange rate |
| `players.yml` | Personal settings |

Stop the server before you edit those files. Take a copy first.

## Build

```bash
mvn package
```

The jar is written to `target/MinerRig-1.0.0.jar`. Java 21 is required. Paper's API is downloaded by Maven and is not packed into the jar.

## Credits

Author: **Stolas**.

The machines are built from BlockDisplays in game, not from a separate resource pack. [Vault](https://github.com/MilkBowl/Vault) is used for the exchange when it is installed. [ProtocolLib](https://github.com/dmulloy2/ProtocolLib) is used for animation packets when it is installed.

The code was written with Cursor.

## License

[MIT](LICENSE).
