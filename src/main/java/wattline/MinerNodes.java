package wattline;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class MinerNodes {

    static int COIN_SECONDS = 15;
    static int PANEL_TIERS = 4;
    static int CHARGE_MAX = 600;
    static int BATTERY_TIERS = 4;
    static final int BATTERY_ADMIN = 5;
    static int ADMIN_AMP = 64;

    static int output(int tier) {
        return Tune.panelOutput(tier);
    }

    static int upgradeCost(int tier) {
        return Tune.upgradeCost(tier);
    }

    static String tierName(CommandSender who, int tier) {
        String key = switch (Math.max(1, Math.min(PANEL_TIERS, tier))) {
            case 2 -> "tier.2";
            case 3 -> "tier.3";
            case 4 -> "tier.4";
            default -> "tier.1";
        };
        return Lang.text(who, key);
    }

    private final MinerPlugin plugin;
    private final MinerVisuals visuals;
    static int REACH = 16;
    static int POLE_REACH = 32;
    static int POLE_SPAN = 48;

    private final Map<String, Node> nodes = new HashMap<>();
    private final Map<UUID, Node> byEntity = new HashMap<>();
    private final Map<String, Link> links = new HashMap<>();
    private final Map<UUID, String> pulling = new HashMap<>();
    private final File file;

    MinerNodes(MinerPlugin plugin, MinerVisuals visuals) {
        this.plugin = plugin;
        this.visuals = visuals;
        this.file = new File(plugin.getDataFolder(), "nodes.yml");
    }

    void load() {
        File source = Store.readable(file);
        if (!source.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);
        var section = yaml.getConfigurationSection("nodes");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            var part = section.getConfigurationSection(id);
            if (part == null) continue;
            NodeType type = NodeType.byId(part.getString("type"));
            if (type == null) continue;
            Node node = new Node(id, UUID.fromString(part.getString("owner")), part.getString("world"),
                    part.getInt("x"), part.getInt("y"), part.getInt("z"), type);
            node.buffer = part.getInt("buffer");
            node.progress = part.getInt("progress");
            node.temp = Math.max(20, part.getInt("temp", 20));
            node.facing = part.getString("facing", "SOUTH");
            node.model = part.getInt("model");
            int storedTier = part.getInt("tier", 1);
            if (type == NodeType.BATTERY) {
                node.tier = storedTier >= BATTERY_ADMIN ? BATTERY_ADMIN : Math.max(1, Math.min(BATTERY_TIERS, storedTier));
            } else if (type == NodeType.PANEL) {
                node.tier = Math.max(1, Math.min(PANEL_TIERS, storedTier));
            } else {
                node.tier = Math.max(1, storedTier);
            }
            node.bays = Math.min(Node.RACK_SLOTS, part.getInt("bays"));
            int cap = type == NodeType.BATTERY ? capacity(node.tier) : CHARGE_MAX;
            node.charge = cap <= 0 ? 0 : Math.max(0, Math.min(cap, part.getInt("charge")));
            node.dust = Math.max(0, Math.min(100, part.getInt("dust")));
            node.bulbs = Math.max(0, Math.min(2, part.getInt("bulbs")));
            if (type == NodeType.RACK) {
                int stored = part.contains("door") ? part.getInt("door") : 2;
                node.door = stored == 1 ? 1 : 2;
            }
            List<Integer> bayBuffer = part.getIntegerList("bay-buffer");
            List<Integer> bayProgress = part.getIntegerList("bay-progress");
            List<Integer> bayTemp = part.getIntegerList("bay-temp");
            for (int i = 0; i < node.bays; i++) {
                if (i < bayBuffer.size()) node.bayBuffer[i] = bayBuffer.get(i);
                if (i < bayProgress.size()) node.bayProgress[i] = bayProgress.get(i);
                node.bayTemp[i] = i < bayTemp.size() ? Math.max(20, bayTemp.get(i)) : 20;
            }
            String lamp = part.getString("lamp");
            if (lamp != null && !lamp.isBlank()) node.lamp = UUID.fromString(lamp);
            String click = part.getString("click");
            if (click != null && !click.isBlank()) node.click = UUID.fromString(click);
            for (String raw : part.getStringList("displays")) node.displays.add(UUID.fromString(raw));
            for (String raw : part.getStringList("blades")) visuals.loadBlade(node.id, raw);
            remember(node);
        }
        var wires = yaml.getConfigurationSection("links");
        if (wires == null) return;
        for (String id : wires.getKeys(false)) {
            var part = wires.getConfigurationSection(id);
            if (part == null) continue;
            String a = part.getString("a");
            String b = part.getString("b");
            if (nodes.get(a) == null || nodes.get(b) == null) continue;
            Link link = new Link(id, a, b);
            String spark = part.getString("spark");
            if (spark != null && !spark.isBlank()) link.spark = UUID.fromString(spark);
            for (String raw : part.getStringList("displays")) link.displays.add(UUID.fromString(raw));
            links.put(id, link);
        }
    }

    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Node node : nodes.values()) {
            String path = "nodes." + node.id;
            yaml.set(path + ".owner", node.owner.toString());
            yaml.set(path + ".world", node.world);
            yaml.set(path + ".x", node.x);
            yaml.set(path + ".y", node.y);
            yaml.set(path + ".z", node.z);
            yaml.set(path + ".type", node.type.id);
            yaml.set(path + ".facing", node.facing);
            yaml.set(path + ".model", node.model);
            yaml.set(path + ".tier", node.tier);
            yaml.set(path + ".buffer", node.buffer);
            yaml.set(path + ".progress", node.progress);
            yaml.set(path + ".temp", node.temp);
            yaml.set(path + ".bays", node.bays);
            yaml.set(path + ".charge", node.charge);
            yaml.set(path + ".dust", node.dust);
            yaml.set(path + ".bulbs", node.bulbs);
            yaml.set(path + ".door", node.door);
            yaml.set(path + ".bay-buffer", java.util.Arrays.stream(node.bayBuffer).boxed().toList());
            yaml.set(path + ".bay-progress", java.util.Arrays.stream(node.bayProgress).boxed().toList());
            yaml.set(path + ".bay-temp", java.util.Arrays.stream(node.bayTemp).boxed().toList());
            yaml.set(path + ".lamp", node.lamp == null ? "" : node.lamp.toString());
            yaml.set(path + ".click", node.click == null ? "" : node.click.toString());
            List<String> ids = new ArrayList<>();
            for (UUID id : node.displays) ids.add(id.toString());
            yaml.set(path + ".displays", ids);
            yaml.set(path + ".blades", visuals.bladeRows(node.id));
        }
        for (Link link : links.values()) {
            String path = "links." + link.id;
            yaml.set(path + ".a", link.a);
            yaml.set(path + ".b", link.b);
            yaml.set(path + ".spark", link.spark == null ? "" : link.spark.toString());
            List<String> ids = new ArrayList<>();
            for (UUID id : link.displays) ids.add(id.toString());
            yaml.set(path + ".displays", ids);
        }
        try {
            Store.yaml(file, yaml);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save the grid: " + e.getMessage());
        }
    }

    boolean place(Player player, Block cell, NodeType type) {
        return place(player, cell, type, 1);
    }

    boolean place(Player player, Block cell, NodeType type, int tier) {
        return place(player, cell, type, tier, 0);
    }

    boolean place(Player player, Block cell, NodeType type, int tier, int charge) {
        if (!player.hasPermission("miner.use") && !player.hasPermission("miner.admin")) {
            tell(player, Tone.BAD, "msg.no-permission");
            return false;
        }
        if (!staff(player) && Tune.machineCap > 0 && count(player) >= Tune.machineCap) {
            tell(player, Tone.BAD, "msg.machine-cap", Tune.machineCap);
            return false;
        }
        if (at(cell) != null) {
            tell(player, Tone.BAD, "msg.occupied");
            return false;
        }
        if ((type == NodeType.RACK || type == NodeType.TERMINAL || type == NodeType.POLE)
                && (!cell.getRelative(org.bukkit.block.BlockFace.UP).getType().isAir()
                || at(cell.getRelative(org.bukkit.block.BlockFace.UP)) != null)) {
            tell(player, Tone.BAD, "msg.need-two-high");
            return false;
        }
        int stored = type == NodeType.BATTERY ? normalizeBattery(tier) : 1;
        int kept = type == NodeType.BATTERY ? Math.max(0, Math.min(capacity(stored), charge)) : 0;
        spawnNode(player, cell, type, stored, kept);
        tell(player, Tone.GOOD, "msg.placed", type == NodeType.BATTERY && adminTier(stored)
                ? Lang.text(player, "item.battery.admin") : type.title(player));
        return true;
    }

    boolean hangLamp(Player player, Node node) {
        if (node == null || node.type != NodeType.POLE) return false;
        if (!canBuild(player, node)) {
            tell(player, Tone.BAD, owner(player, node) ? "msg.no-permission" : "msg.not-yours");
            return false;
        }
        if (node.bulbs >= Tune.lamps) {
            tell(player, Tone.BAD, "msg.lamps-full", Tune.lamps);
            return false;
        }
        node.bulbs++;
        visuals.bulbSkin(node);
        save();
        MinerSettings.play(player, org.bukkit.Sound.BLOCK_LANTERN_PLACE, 0.7f, 1.2f);
        tell(player, Tone.GOOD, "msg.lamp-hung");
        return true;
    }

    boolean takeLamp(Player player, Node node) {
        if (node == null || node.type != NodeType.POLE) return false;
        if (!canBuild(player, node)) {
            tell(player, Tone.BAD, owner(player, node) ? "msg.no-permission" : "msg.not-yours");
            return false;
        }
        if (node.bulbs <= 0) {
            tell(player, Tone.BAD, "msg.no-lamps");
            return false;
        }
        node.bulbs--;
        visuals.bulbSkin(node);
        give(player, MinerItems.lamp(player));
        save();
        tell(player, Tone.GOOD, "msg.lamp-taken");
        return true;
    }

    boolean remove(Node node, Player player) {
        if (player != null && !canBreak(player, node)) {
            tell(player, Tone.BAD, owner(player, node) ? "msg.no-permission" : "msg.not-yours");
            return false;
        }
        int coins = node.buffer;
        for (int i = 0; i < node.bays; i++) coins += node.bayBuffer[i];
        if (player != null && !settleCoins(player, node, coins)) return false;
        List<Node> near = touching(node);
        List<Link> cut = new ArrayList<>();
        for (Link link : links.values()) {
            if (link.joins(node.id, node.id) || link.a.equals(node.id) || link.b.equals(node.id)) cut.add(link);
        }
        for (Link link : cut) {
            visuals.clearLink(link);
            links.remove(link.id);
        }
        visuals.clear(node);
        forget(node);
            if (player != null) {
                give(player, node.type == NodeType.BATTERY ? MinerItems.battery(player, node.tier, node.charge) : MinerItems.item(player, node.type));
                for (int i = 0; i < node.bays; i++) give(player, MinerItems.item(player, NodeType.ASIC));
                for (int i = 0; i < node.bulbs; i++) give(player, MinerItems.lamp(player));
            }
        for (Node other : near) visuals.redraw(other, touching(other));
        save();
        return true;
    }

    boolean restoreLinks() {
        boolean dirty = false;
        for (Link link : new ArrayList<>(links.values())) {
            Node a = nodes.get(link.a);
            Node b = nodes.get(link.b);
            if (a == null || b == null || a.world() == null) continue;
            if (!a.world().isChunkLoaded(a.x >> 4, a.z >> 4)) continue;
            boolean alive = !link.displays.isEmpty() && a.world().getEntity(link.displays.get(0)) != null;
            if (alive) continue;
            visuals.drawLink(link, a, b);
            dirty = true;
        }
        return dirty;
    }

    boolean restore(org.bukkit.Chunk chunk) {
        String name = chunk.getWorld().getName();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        visuals.dropStrays(chunk);
        boolean dirty = false;
        for (Node node : new ArrayList<>(nodes.values())) {
            if (!node.world.equals(name)) continue;
            if ((node.x >> 4) != cx || (node.z >> 4) != cz) continue;
            visuals.solid(node);
            index(node);
            boolean alive = node.click != null && chunk.getWorld().getEntity(node.click) != null;
            if (alive && node.model >= MinerVisuals.MODEL && visuals.bladesReady(node)) continue;
            visuals.redraw(node, touching(node));
            node.model = MinerVisuals.MODEL;
            refreshLinks(node);
            dirty = true;
        }
        return dirty;
    }

    Node at(Block block) {
        if (block == null) return null;
        for (Node node : nodes.values()) {
            if (node.x == block.getX() && node.y == block.getY() && node.z == block.getZ()
                    && node.world.equals(block.getWorld().getName())) return node;
        }
        return null;
    }

    Node byEntity(UUID id) {
        return byEntity.get(id);
    }

    boolean owner(Player player, Node node) {
        return node.owner.equals(player.getUniqueId());
    }

    boolean owns(Player player, Node node) {
        return owner(player, node) || staff(player);
    }

    boolean staff(Player player) {
        return player.isOp() || player.hasPermission("miner.admin");
    }

    boolean canBreak(Player player, Node node) {
        if (staff(player)) return true;
        return owner(player, node) && player.hasPermission("miner.break");
    }

    boolean canBuild(Player player, Node node) {
        if (staff(player)) return true;
        return owner(player, node) && player.hasPermission("miner.use");
    }

    boolean canWire(Player player, Node node) {
        if (!owns(player, node)) return false;
        return staff(player) || player.hasPermission("miner.wire");
    }

    String ownerName(CommandSender who, Node node) {
        String name = Bukkit.getOfflinePlayer(node.owner).getName();
        return name == null || name.isBlank() ? Lang.text(who, "name.unknown") : name;
    }

    int rackCoins(Node rack) {
        int sum = 0;
        for (int i = 0; i < rack.bays; i++) sum += rack.bayBuffer[i];
        return sum;
    }

    void use(Player player, Node node) {
        if (!owner(player, node)) {
            tell(player, Tone.BAD, "msg.not-yours");
            return;
        }
        if (node.type == NodeType.ASIC) {
            if (node.buffer <= 0) {
                tell(player, Tone.BAD, node.powered ? "msg.powered-no-tokens" : "msg.no-power-no-tokens");
                return;
            }
            int now = pour(player, node.buffer);
            if (now < 0) return;
            int given = node.buffer;
            node.buffer = 0;
            save();
            tell(player, Tone.GOLD, "msg.collected", given, now);
            return;
        }
        if (node.type == NodeType.PANEL) {
            tell(player, Tone.YELLOW, "msg.panel-status", producing(node) ? Lang.text(player, "msg.shining") : Lang.text(player, "msg.quiet"), tierName(player, node.tier), output(node.tier));
            return;
        }
        if (node.type == NodeType.RACK) {
            tell(player, Tone.YELLOW, "msg.blades", node.bays, Node.RACK_SLOTS);
            return;
        }
        tell(player, Tone.YELLOW, "msg.wire-status", degree(node), supply(find(node)));
    }

    boolean insert(Player player, Node rack) {
        if (rack.type != NodeType.RACK) return false;
        if (!owner(player, rack)) {
            tell(player, Tone.BAD, "msg.not-yours");
            return false;
        }
        if (rack.bays >= Node.RACK_SLOTS) {
            tell(player, Tone.BAD, "msg.rack-full");
            return false;
        }
        if (rack.door == 1) {
            tell(player, Tone.BAD, "msg.door-first");
            return false;
        }
        rack.bays++;
        rack.bayTemp[rack.bays - 1] = 20;
        rack.bayThrottle[rack.bays - 1] = false;
        if (rack.powered) rack.bayOn[rack.bays - 1] = true;
        visuals.redraw(rack, touching(rack));
        save();
        MinerSettings.play(player, org.bukkit.Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.6f, 1.4f);
        tell(player, Tone.GOOD, "msg.blade-inserted", rack.bays, Node.RACK_SLOTS);
        return true;
    }

    boolean setDoor(Player player, Node rack, int mode) {
        if (rack.type != NodeType.RACK || (mode != 1 && mode != 2)) return false;
        if (!owner(player, rack)) {
            tell(player, Tone.BAD, "msg.not-yours");
            return false;
        }
        if (rack.door == mode) return true;
        rack.door = mode;
        visuals.redraw(rack, touching(rack));
        save();
        org.bukkit.Sound sound = mode == 2 ? org.bukkit.Sound.BLOCK_IRON_TRAPDOOR_OPEN : org.bukkit.Sound.BLOCK_IRON_DOOR_CLOSE;
        MinerSettings.play(player, sound, 0.7f, mode == 2 ? 1.25f : 0.8f);
        tell(player, mode == 2 ? Tone.GOOD : Tone.GOLD, mode == 2 ? "msg.door-opened" : "msg.door-closed");
        return true;
    }

    void eject(Player player, Node rack, int index) {
        if (rack.type != NodeType.RACK || index < 0 || index >= rack.bays) return;
        if (!owner(player, rack) && !staff(player)) {
            tell(player, Tone.BAD, "msg.not-yours");
            return;
        }
        if (rack.bayBuffer[index] > 0 && !settleCoins(player, rack, rack.bayBuffer[index])) return;
        give(player, MinerItems.item(player, NodeType.ASIC));
        for (int i = index; i < rack.bays - 1; i++) {
            rack.bayBuffer[i] = rack.bayBuffer[i + 1];
            rack.bayProgress[i] = rack.bayProgress[i + 1];
            rack.bayOn[i] = rack.bayOn[i + 1];
            rack.bayTemp[i] = rack.bayTemp[i + 1];
            rack.bayThrottle[i] = rack.bayThrottle[i + 1];
        }
        int last = rack.bays - 1;
        rack.bayBuffer[last] = 0;
        rack.bayProgress[last] = 0;
        rack.bayOn[last] = false;
        rack.bayTemp[last] = 20;
        rack.bayThrottle[last] = false;
        rack.bays--;
        if (rack.bays == 0) rack.powered = false;
        visuals.redraw(rack, touching(rack));
        save();
        MinerSettings.play(player, org.bukkit.Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.6f, 1.2f);
        tell(player, Tone.GOOD, "msg.blade-out");
    }

    void collectBay(Player player, Node rack, int index) {
        if (rack.type != NodeType.RACK || index < 0 || index >= rack.bays) return;
        if (!owner(player, rack)) return;
        if (rack.bayBuffer[index] <= 0) {
            tell(player, Tone.BAD, rack.bayOn[index] ? "msg.powered-no-tokens" : "msg.no-power-no-tokens");
            return;
        }
        int now = pour(player, rack.bayBuffer[index]);
        if (now < 0) return;
        int given = rack.bayBuffer[index];
        rack.bayBuffer[index] = 0;
        save();
        tell(player, Tone.GOLD, "msg.collected", given, now);
    }

    void collectRack(Player player, Node rack) {
        if (rack.type != NodeType.RACK) return;
        if (!owner(player, rack)) {
            tell(player, Tone.BAD, "msg.not-yours");
            return;
        }
        int given = rackCoins(rack);
        if (given <= 0) {
            tell(player, Tone.BAD, "msg.no-tokens");
            return;
        }
        int now = pour(player, given);
        if (now < 0) return;
        for (int bay = 0; bay < rack.bays; bay++) rack.bayBuffer[bay] = 0;
        save();
        tell(player, Tone.GOLD, "msg.collected", given, now);
    }

    void collectRacks(Player player) {
        int given = 0;
        List<Node> racks = new ArrayList<>();
        for (Node node : owned(player)) {
            if (node.type != NodeType.RACK) continue;
            int coins = rackCoins(node);
            if (coins <= 0) continue;
            given += coins;
            racks.add(node);
        }
        if (given <= 0) {
            tell(player, Tone.BAD, "msg.no-tokens");
            return;
        }
        int now = pour(player, given);
        if (now < 0) return;
        for (Node rack : racks) {
            for (int bay = 0; bay < rack.bays; bay++) rack.bayBuffer[bay] = 0;
        }
        save();
        tell(player, Tone.GOLD, "msg.collected", given, now);
    }

    void sweepRacks(Player player) {
        List<Node> dirty = new ArrayList<>();
        for (Node node : owned(player)) {
            if (node.type == NodeType.RACK && node.dust > 0) dirty.add(node);
        }
        if (dirty.isEmpty()) {
            tell(player, Tone.GOOD, "msg.already-clean");
            return;
        }
        int cleaned = 0;
        boolean broke = false;
        for (Node rack : dirty) {
            int wear = Math.max(1, rack.dust / Tune.brushDivisor);
            int used = MinerItems.wearBrush(player, wear);
            if (used == 0) break;
            rack.dust = 0;
            rack.dustClock = 0;
            visuals.dustSkin(rack);
            cleaned++;
            if (used == 2) {
                broke = true;
                break;
            }
        }
        if (cleaned == 0) {
            tell(player, Tone.BAD, "msg.need-brush");
            return;
        }
        save();
        MinerSettings.play(player, org.bukkit.Sound.ITEM_BRUSH_BRUSHING_GENERIC, 0.7f, 1.1f);
        tell(player, broke ? Tone.GOLD : Tone.GOOD, broke ? "msg.swept-racks-broke" : "msg.swept-racks", cleaned);
    }

    String rackDesk(Player player) {
        StringBuilder sig = new StringBuilder();
        for (Node node : listed(player)) {
            if (node.type != NodeType.RACK) continue;
            sig.append(node.id).append(':').append(rackCoins(node)).append(':').append(node.dust)
                    .append(':').append(node.bays).append(':').append(node.powered).append(';');
        }
        sig.append(MinerItems.balance(player));
        return sig.toString();
    }

    boolean upgrade(Player player, Node node) {
        if (node.type != NodeType.PANEL && node.type != NodeType.BATTERY) return false;
        if (!owns(player, node)) {
            tell(player, Tone.BAD, "msg.not-yours");
            return false;
        }
        if (node.type == NodeType.BATTERY && adminTier(node.tier)) {
            tell(player, Tone.BAD, "msg.admin-no-upgrade");
            return false;
        }
        int tier = Math.max(1, node.tier);
        int max = node.type == NodeType.PANEL ? PANEL_TIERS : BATTERY_TIERS;
        if (tier >= max) {
            tell(player, Tone.GOLD, node.type == NodeType.PANEL ? "msg.panel-max" : "msg.battery-max");
            return false;
        }
        int cost = upgradeCost(tier);
        if (!takeCoins(player, cost)) return false;
        node.tier = tier + 1;
        visuals.redraw(node, touching(node));
        save();
        MinerSettings.play(player, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.4f);
        if (node.type == NodeType.BATTERY) tell(player, Tone.GOOD, "msg.upgraded", batteryName(player, node.tier), amp(node.tier));
        else tell(player, Tone.GOOD, "msg.upgraded", tierName(player, node.tier), output(node.tier));
        return true;
    }

    void open(Player player, Node node) {
        MinerMenu.machine(player, this, node);
    }

    boolean isPulling(Player player) {
        return pulling.containsKey(player.getUniqueId());
    }

    void startPull(Player player, Node node) {
        if (!canWire(player, node)) {
            tell(player, Tone.BAD, owns(player, node) ? "msg.no-permission" : "msg.not-yours");
            return;
        }
        pulling.put(player.getUniqueId(), node.id);
        MinerSettings.play(player, org.bukkit.Sound.BLOCK_COPPER_PLACE, 0.7f, 1.4f);
        int limit = node.type == NodeType.POLE ? POLE_REACH : REACH;
        tell(player, Tone.YELLOW, "msg.pull-hint", limit);
    }

    void cancelPull(Player player) {
        if (pulling.remove(player.getUniqueId()) != null) tell(player, Tone.YELLOW, "msg.pull-cancel");
    }

    boolean finishPull(Player player, Node dest) {
        Node from = anchor(player);
        if (from == null) return false;
        if (from == dest) {
            tell(player, Tone.BAD, "msg.same-end");
            return false;
        }
        if (!from.world.equals(dest.world)) {
            tell(player, Tone.BAD, "msg.other-world");
            return false;
        }
        if (!owns(player, dest)) {
            tell(player, Tone.BAD, "msg.foreign");
            return false;
        }
        if (near(from, dest)) {
            tell(player, Tone.BAD, "msg.already-close");
            pulling.remove(player.getUniqueId());
            return false;
        }
        int limit = span(from, dest);
        if (reach(from, dest) > limit) {
            tell(player, Tone.BAD, "msg.too-far", limit);
            return false;
        }
        if (visuals.clips(from, dest)) {
            tell(player, Tone.BAD, "msg.through-block");
            return false;
        }
        if (linked(from, dest)) {
            if (dest.type == NodeType.POLE) {
                pulling.put(player.getUniqueId(), dest.id);
                tell(player, Tone.YELLOW, "msg.pull-from-pole");
            } else {
                tell(player, Tone.BAD, "msg.already-linked");
            }
            return false;
        }
        int cost = wireCost(distance(from, dest));
        if (!consume(player, cost)) return false;
        connect(from, dest);
        MinerSettings.play(player, org.bukkit.Sound.BLOCK_CHAIN_PLACE, 0.8f, 1.1f);
        if (dest.type == NodeType.POLE) {
            pulling.put(player.getUniqueId(), dest.id);
            tell(player, Tone.GOOD, "msg.pulled-pole", cost);
        } else {
            pulling.remove(player.getUniqueId());
            tell(player, Tone.GOOD, "msg.pulled", cost);
        }
        return true;
    }

    boolean finishPull(Player player, Block cell) {
        Node existing = at(cell);
        if (existing != null) return finishPull(player, existing);
        Node from = anchor(player);
        if (from == null) return false;
        if (!from.world.equals(cell.getWorld().getName())) {
            tell(player, Tone.BAD, "msg.other-world");
            return false;
        }
        if (!cell.getType().isAir()) {
            tell(player, Tone.BAD, "msg.need-air");
            return false;
        }
        if (player.getLocation().distanceSquared(cell.getLocation().add(0.5, 0.5, 0.5)) > (double) Tune.handsReach * Tune.handsReach) {
            tell(player, Tone.BAD, "msg.come-closer");
            return false;
        }
        int limit = from.type == NodeType.POLE ? POLE_REACH : REACH;
        if (reach(from, cell) > limit) {
            tell(player, Tone.BAD, "msg.too-far", limit);
            return false;
        }
        Node ghost = new Node("", player.getUniqueId(), cell.getWorld().getName(), cell.getX(), cell.getY(), cell.getZ(), NodeType.WIRE);
        if (visuals.clips(from, ghost)) {
            tell(player, Tone.BAD, "msg.through-block");
            return false;
        }
        int cost = wireCost(distance(from, cell));
        if (!consume(player, cost)) return false;
        Node post = spawnNode(player, cell, NodeType.WIRE, 1);
        connect(from, post);
        pulling.remove(player.getUniqueId());
        MinerSettings.play(player, org.bukkit.Sound.BLOCK_CHAIN_PLACE, 0.8f, 1.1f);
        tell(player, Tone.GOOD, "msg.pulled", cost);
        return true;
    }

    void preview() {
        var dustFar = new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(180, 60, 50), 1.1f);
        var dust = new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(196, 125, 62), 1.15f);
        for (var entry : new ArrayList<>(pulling.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Node from = nodes.get(entry.getValue());
            if (player == null || !player.isOnline()) {
                pulling.remove(entry.getKey());
                continue;
            }
            if (from == null || from.world() == null) continue;
            if (MinerSettings.get() != null && !MinerSettings.get().anim(player)) continue;
            var hit = player.rayTraceBlocks(64);
            if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) continue;
            Block aimed = hit.getHitBlock();
            Node dest = at(aimed);
            if (dest == null) dest = at(aimed.getRelative(hit.getHitBlockFace()));
            org.bukkit.Location target;
            int far;
            int limit;
            if (dest != null) {
                target = dest.corner().add(0.5, dest.type == NodeType.POLE ? 1.70 : 0.16, 0.5);
                far = reach(from, dest);
                limit = span(from, dest);
            } else {
                Block air = aimed.getRelative(hit.getHitBlockFace());
                target = air.getLocation().add(0.5, 0.16, 0.5);
                far = reach(from, air);
                limit = from.type == NodeType.POLE ? POLE_REACH : REACH;
            }
            org.bukkit.Location start = from.corner().add(0.5, from.type == NodeType.POLE ? 1.70 : 0.16, 0.5);
            boolean wall = dest != null ? visuals.clips(from, dest)
                    : visuals.clips(from, new Node("", player.getUniqueId(), target.getWorld().getName(),
                    target.getBlockX(), target.getBlockY(), target.getBlockZ(), NodeType.WIRE));
            var color = far > limit || wall ? dustFar : dust;
            double ground = Math.min(start.getY(), target.getY()) - 0.12;
            org.bukkit.util.Vector delta = target.toVector().subtract(start.toVector());
            for (int i = 1; i <= 16; i++) {
                double t = i / 16.0;
                double u = 1 - t;
                org.bukkit.Location at = start.clone().add(delta.clone().multiply(t));
                double y = u * u * u * start.getY() + 3 * u * u * t * ground + 3 * u * t * t * ground + t * t * t * target.getY();
                at.setY(y);
                player.spawnParticle(org.bukkit.Particle.DUST, at, 1, 0, 0, 0, 0, color);
            }
        }
    }

    int coinsOf(Player player) {
        int sum = 0;
        for (Node node : nodes.values()) {
            if (!node.owner.equals(player.getUniqueId())) continue;
            if (node.type == NodeType.ASIC) sum += node.buffer;
            if (node.type == NodeType.RACK) {
                for (int i = 0; i < node.bays; i++) sum += node.bayBuffer[i];
            }
        }
        return sum;
    }

    int count(Player player, NodeType type) {
        int sum = 0;
        for (Node node : nodes.values()) {
            if (node.type == type && node.owner.equals(player.getUniqueId())) sum++;
        }
        return sum;
    }

    int count(Player player) {
        int sum = 0;
        for (Node node : nodes.values()) {
            if (node.owner.equals(player.getUniqueId())) sum++;
        }
        return sum;
    }

    List<Node> owned(Player player) {
        List<Node> list = new ArrayList<>();
        for (Node node : nodes.values()) {
            if (node.owner.equals(player.getUniqueId())) list.add(node);
        }
        return list;
    }

    List<Node> listed(Player player) {
        if (staff(player)) return new ArrayList<>(nodes.values());
        return owned(player);
    }

    int degree(Node node) {
        int count = 0;
        for (Node other : nodes.values()) {
            if (conducts(node, other)) count++;
        }
        return count;
    }

    java.util.Collection<Link> links() {
        return links.values();
    }

    java.util.Collection<Node> all() {
        return nodes.values();
    }

    boolean linked(Node a, Node b) {
        for (Link link : links.values()) {
            if (link.joins(a.id, b.id)) return true;
        }
        return false;
    }

    void tick() {
        Map<Node, Node> parent = grid();
        List<Node> all = new ArrayList<>(nodes.values());
        Map<Node, Integer> sun = new HashMap<>();
        Map<Node, List<Node>> asics = new HashMap<>();
        Map<Node, List<Node>> racks = new HashMap<>();
        Map<Node, List<Node>> terminals = new HashMap<>();
        Map<Node, List<Node>> batteries = new HashMap<>();
        Map<Node, Boolean> roots = new HashMap<>();
        for (Node node : all) {
            Node root = find(parent, node);
            roots.put(root, true);
            if (node.type == NodeType.PANEL && producing(node)) sun.merge(root, output(node.tier), Integer::sum);
            if (node.type == NodeType.ASIC) asics.computeIfAbsent(root, k -> new ArrayList<>()).add(node);
            if (node.type == NodeType.RACK) racks.computeIfAbsent(root, k -> new ArrayList<>()).add(node);
            if (node.type == NodeType.TERMINAL) terminals.computeIfAbsent(root, k -> new ArrayList<>()).add(node);
            if (node.type == NodeType.BATTERY) batteries.computeIfAbsent(root, k -> new ArrayList<>()).add(node);
        }
        powerDirty = false;
        for (Node node : all) {
            if (node.type == NodeType.RACK || node.type == NodeType.TERMINAL || node.type == NodeType.BATTERY) continue;
            if (node.type != NodeType.ASIC) {
                boolean on = node.type == NodeType.PANEL && producing(node);
                if (node.powered != on) {
                    node.powered = on;
                    visuals.lamp(node, on);
                }
            }
        }
        Map<Node, Integer> livePower = new HashMap<>();
        for (Node root : roots.keySet()) {
            boolean dark = night(root.world());
            int sunPower = sun.getOrDefault(root, 0);
            List<Node> cells = batteries.get(root);
            int offer = 0;
            int admin = 0;
            if (cells != null) {
                for (Node battery : cells) {
                    if (adminTier(battery.tier)) admin += Tune.adminAmp;
                    else if (dark && battery.charge > 0) offer += Math.min(amp(battery.tier), battery.charge);
                }
            }
            int pool = (dark ? offer : sunPower) + admin;
            livePower.put(root, pool);
            int energy = pool;
            if (dark) {
                energy = runTerminals(terminals.get(root), energy, all, parent, true);
                energy = runRacks(racks.get(root), energy, all, parent, true);
                runAsics(asics.get(root), 0);
                settle(cells, true, Math.max(0, (pool - energy) - admin));
            } else {
                energy = runAsics(asics.get(root), energy);
                energy = runRacks(racks.get(root), energy, all, parent, false);
                energy = runTerminals(terminals.get(root), energy, all, parent, false);
                int used = pool - energy;
                int spareSun = Math.max(0, sunPower - Math.min(used, sunPower));
                settle(cells, false, spareSun);
            }
        }
        for (Link link : links.values()) {
            Node a = nodes.get(link.a);
            Node b = nodes.get(link.b);
            boolean hot = a != null && b != null && livePower.getOrDefault(find(parent, a), 0) > 0;
            if (link.live != hot) link.live = hot;
        }
        fans();
        if (powerDirty) save();
    }

    private boolean powerDirty;

    private int runAsics(List<Node> list, int energy) {
        if (list == null) return energy;
        for (Node asic : list) {
            int need = Tune.asicDraw;
            boolean on = energy >= need;
            if (on) energy -= need;
            if (on && !asic.throttle && hashStep(asic, -1)) {
                asic.progress++;
                if (asic.progress >= COIN_SECONDS && asic.buffer < Tune.tokenCap) {
                    asic.progress = 0;
                    asic.buffer++;
                    powerDirty = true;
                }
            }
            gatherDust(asic, on, false);
            breathe(asic);
            asic.temp = nextTemp(asic.temp, on, asic.throttle, asic.choke, 1, false);
            boolean hot = nextThrottle(asic.temp, asic.throttle, on, asic.choke, 1);
            if (hot != asic.throttle) powerDirty = true;
            asic.throttle = hot;
            if (asic.powered != on) {
                asic.powered = on;
                visuals.lamp(asic, on);
            }
        }
        return energy;
    }

    private int runRacks(List<Node> list, int energy, List<Node> all, Map<Node, Node> parent, boolean dark) {
        if (list == null) return energy;
        list.sort((p, q) -> {
            int bySupply = Integer.compare(sourceDistance(p, all, parent, dark), sourceDistance(q, all, parent, dark));
            if (bySupply != 0) return bySupply;
            if (p.y != q.y) return Integer.compare(p.y, q.y);
            if (p.x != q.x) return Integer.compare(p.x, q.x);
            return Integer.compare(p.z, q.z);
        });
        for (Node rack : list) {
            breathe(rack);
            boolean any = false;
            boolean lights = false;
            for (int i = 0; i < rack.bays; i++) {
                int need = Tune.bladeDraw;
                boolean on = energy >= need;
                if (on) energy -= need;
                if (on && !rack.bayThrottle[i] && hashStep(rack, i)) {
                    rack.bayProgress[i]++;
                    if (rack.bayProgress[i] >= COIN_SECONDS && rack.bayBuffer[i] < Tune.tokenCap) {
                        rack.bayProgress[i] = 0;
                        rack.bayBuffer[i]++;
                        powerDirty = true;
                    }
                }
                rack.bayTemp[i] = nextTemp(rack.bayTemp[i], on, rack.bayThrottle[i], rack.choke, 2, rack.door == 1);
                boolean hot = nextThrottle(rack.bayTemp[i], rack.bayThrottle[i], on, rack.choke, 2);
                if (hot != rack.bayThrottle[i]) powerDirty = true;
                rack.bayThrottle[i] = hot;
                if (rack.bayOn[i] != on) {
                    rack.bayOn[i] = on;
                    lights = true;
                }
                any |= on;
            }
            if (rack.powered != any) {
                rack.powered = any;
                visuals.lamp(rack, any);
            }
            if (lights) visuals.bayLights(rack);
            gatherDust(rack, any, rack.door == 1);
        }
        return energy;
    }

    private int runTerminals(List<Node> list, int energy, List<Node> all, Map<Node, Node> parent, boolean dark) {
        if (list == null) return energy;
        list.sort((p, q) -> {
            int bySupply = Integer.compare(sourceDistance(p, all, parent, dark), sourceDistance(q, all, parent, dark));
            if (bySupply != 0) return bySupply;
            if (p.y != q.y) return Integer.compare(p.y, q.y);
            if (p.x != q.x) return Integer.compare(p.x, q.x);
            return Integer.compare(p.z, q.z);
        });
        for (Node terminal : list) {
            int need = Tune.terminalDraw;
            boolean on = energy >= need;
            if (on) energy -= need;
            if (terminal.powered != on) {
                terminal.powered = on;
                visuals.lamp(terminal, on);
                visuals.terminalBoard(terminal);
            }
        }
        return energy;
    }

    private void settle(List<Node> cells, boolean dark, int amount) {
        if (cells == null || amount < 0) return;
        if (dark) cells.sort((p, q) -> Integer.compare(q.charge, p.charge));
        else cells.sort((p, q) -> Integer.compare(p.charge, q.charge));
        int left = amount;
        for (Node battery : cells) {
            if (adminTier(battery.tier)) {
                battery.charge = 0;
                battery.progress = 4;
                battery.powered = true;
                visuals.cells(battery);
                continue;
            }
            int rate = amp(battery.tier);
            int cap = capacity(battery.tier);
            int before = battery.charge;
            if (dark && left > 0 && battery.charge > 0) {
                int give = Math.min(left, Math.min(rate, battery.charge));
                battery.charge -= give;
                left -= give;
                battery.progress = battery.charge > 0 ? 2 : 0;
            } else if (!dark && left > 0 && battery.charge < cap) {
                int take = Math.min(left, Math.min(rate, cap - battery.charge));
                battery.charge += take;
                left -= take;
                battery.progress = 1;
            } else {
                battery.progress = battery.charge >= cap ? 3 : 0;
            }
            battery.powered = battery.progress == 1 || battery.progress == 2;
            if (before != battery.charge && (battery.charge == 0 || battery.charge == cap || before / 15 != battery.charge / 15)) {
                powerDirty = true;
            }
            visuals.cells(battery);
        }
    }

    private void fans() {
        for (Node node : nodes.values()) {
            if ((node.type != NodeType.ASIC && node.type != NodeType.RACK) || !node.powered) continue;
            tone(node, Sound.ITEM_ELYTRA_FLYING, 0.07f, 1.25f);
        }
    }

    private void tone(Node node, Sound sound, float volume, float pitch) {
        World world = node.world();
        if (world == null) return;
        Location at = node.corner().add(0.5, 0.4, 0.5);
        if (!heard(world, at)) return;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(at) > 256) continue;
            MinerSettings.playAt(player, at, sound, SoundCategory.BLOCKS, volume, pitch);
        }
    }

    private static boolean heard(World world, Location at) {
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(at) <= 256) return true;
        }
        return false;
    }

    List<Node> touching(Node node) {
        List<Node> list = new ArrayList<>();
        for (Node other : nodes.values()) {
            if (other != node && near(node, other)) list.add(other);
        }
        return list;
    }

    void breathe(Node node) {
        node.choke = intake(node);
    }

    private int intake(Node node) {
        if (node.type != NodeType.ASIC && node.type != NodeType.RACK) return 0;
        World world = node.world();
        if (world == null) return 0;
        org.bukkit.block.BlockFace face = front(node);
        int x = node.x + face.getModX();
        int z = node.z + face.getModZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return 0;
        int vents = node.type == NodeType.RACK ? 2 : 1;
        int blocked = 0;
        for (int i = 0; i < vents; i++) {
            if (!open(world.getBlockAt(x, node.y + i, z))) blocked++;
        }
        return blocked;
    }

    private static String poleYaw(Player player) {
        float model = player.getYaw() + 180f;
        int steps = Math.floorMod(Math.round(model / 22.5f), 16);
        return "yaw:" + (steps * 22.5f);
    }

    private static org.bukkit.block.BlockFace front(Node node) {
        try {
            org.bukkit.block.BlockFace face = org.bukkit.block.BlockFace.valueOf(node.facing);
            if (face == org.bukkit.block.BlockFace.NORTH || face == org.bukkit.block.BlockFace.SOUTH
                    || face == org.bukkit.block.BlockFace.EAST || face == org.bukkit.block.BlockFace.WEST) return face;
        } catch (IllegalArgumentException ignored) {
        }
        return org.bukkit.block.BlockFace.SOUTH;
    }

    private static boolean open(org.bukkit.block.Block block) {
        if (block.isLiquid()) return false;
        if (block.getType().isAir()) return true;
        return block.isPassable();
    }

    private void gatherDust(Node node, boolean running, boolean shut) {
        if (!running || node.dust >= 100) {
            if (!running) node.dustClock = 0;
            return;
        }
        node.dustClock++;
        int every = shut ? Tune.dustShut : Tune.dustOpen;
        if (node.dustClock < every) return;
        node.dustClock = 0;
        node.dust++;
        if (node.dust % 10 == 0) powerDirty = true;
        if ((node.type == NodeType.ASIC || node.type == NodeType.RACK) && MinerVisuals.dustStage(node.dust) != MinerVisuals.dustStage(node.dust - 1)) {
            visuals.dustSkin(node);
        }
    }

    private boolean hashStep(Node node, int bay) {
        int hash = hashOf(node.dust);
        if (bay < 0) {
            node.pace += hash;
            if (node.pace < 100) return false;
            node.pace -= 100;
            return true;
        }
        node.bayPace[bay] += hash;
        if (node.bayPace[bay] < 100) return false;
        node.bayPace[bay] -= 100;
        return true;
    }

    static int hashOf(int dust) {
        int clamped = Math.max(0, Math.min(100, dust));
        return 100 - clamped * (100 - Tune.dustFloor) / 100;
    }

    static int eta(int progress, int dust) {
        int left = Math.max(0, COIN_SECONDS - progress);
        int hash = Math.max(1, hashOf(dust));
        return Math.max(left == 0 ? 0 : 1, left * 100 / hash);
    }

    static String dustText(CommandSender who, int dust) {
        if (dust <= 0) return Lang.text(who, "dust.clean");
        if (dust < 30) return Lang.text(who, "dust.light");
        if (dust < 60) return Lang.text(who, "dust.some");
        if (dust < 85) return Lang.text(who, "dust.thick");
        return Lang.text(who, "dust.clogged");
    }

    boolean sweep(Player player, Node node) {
        if (node == null || (node.type != NodeType.ASIC && node.type != NodeType.RACK)) return false;
        if (!owner(player, node)) {
            tell(player, Tone.BAD, "msg.not-yours");
            return false;
        }
        if (node.dust <= 0) {
            tell(player, Tone.GOOD, "msg.already-clean");
            return false;
        }
        int wear = Math.max(1, node.dust / Tune.brushDivisor);
        int used = MinerItems.wearBrush(player, wear);
        if (used == 0) {
            tell(player, Tone.BAD, "msg.need-brush");
            return false;
        }
        node.dust = 0;
        node.dustClock = 0;
        if (node.type == NodeType.ASIC || node.type == NodeType.RACK) visuals.dustSkin(node);
        save();
        MinerSettings.play(player, org.bukkit.Sound.ITEM_BRUSH_BRUSHING_GENERIC, 0.7f, 1.1f);
        tell(player, used == 2 ? Tone.GOLD : Tone.GOOD, used == 2 ? "msg.swept-broke" : "msg.swept");
        return true;
    }

    private static int nextTemp(int temp, boolean powered, boolean throttle, int blocked, int vents, boolean doorShut) {
        boolean sealed = blocked >= vents;
        if (sealed) {
            if (!powered) return Math.max(20, temp - 1);
            if (throttle) return temp;
            return Math.min(100, temp + 8);
        }
        if (doorShut) {
            if (!powered) return Math.max(20, temp - 2);
            if (temp < 84) return Math.min(84, temp + 4);
            if (temp > 84) return Math.max(84, temp - 2);
            return 84;
        }
        if (blocked <= 0) return Math.max(20, temp - 4);
        if (!powered) return Math.max(20, temp - 2);
        return temp > 70 ? temp - 2 : Math.min(70, temp + 4);
    }

    private static boolean nextThrottle(int temp, boolean throttle, boolean powered, int blocked, int vents) {
        if (!powered || blocked < vents) return false;
        if (throttle) return temp > 60;
        return temp >= 88;
    }

    private int sourceDistance(Node consumer, List<Node> all, Map<Node, Node> parent, boolean dark) {
        Node root = find(parent, consumer);
        int best = Integer.MAX_VALUE;
        for (Node node : all) {
            if (find(parent, node) != root) continue;
            boolean source = dark
                    ? node.type == NodeType.BATTERY && (adminTier(node.tier) || node.charge > 0)
                    : node.type == NodeType.PANEL && producing(node);
            if (!source) continue;
            int dist = Math.abs(node.x - consumer.x) + Math.abs(node.y - consumer.y) + Math.abs(node.z - consumer.z);
            if (dist < best) best = dist;
        }
        return best;
    }

    private int supply(Node root) {
        int count = 0;
        boolean dark = root.world() != null && night(root.world());
        for (Node node : nodes.values()) {
            if (find(node) != root) continue;
            if (node.type == NodeType.PANEL && producing(node)) count += output(node.tier);
            if (dark && node.type == NodeType.BATTERY && node.charge > 0 && !adminTier(node.tier)) {
                count += Math.min(amp(node.tier), node.charge);
            }
            if (node.type == NodeType.BATTERY && adminTier(node.tier)) count += ADMIN_AMP;
        }
        return count;
    }

    private Node find(Node node) {
        return find(grid(), node);
    }

    private Map<Node, Node> grid() {
        Map<Node, Node> parent = new HashMap<>();
        Map<String, Node> at = new HashMap<>();
        for (Node node : nodes.values()) {
            parent.put(node, node);
            at.put(spot(node.world, node.x, node.y, node.z), node);
        }
        for (Link link : links.values()) {
            Node a = nodes.get(link.a);
            Node b = nodes.get(link.b);
            if (a != null && b != null) union(parent, a, b);
        }
        for (Node node : nodes.values()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Node other = at.get(spot(node.world, node.x + dx, node.y + dy, node.z + dz));
                        if (other != null && !cornerBlocked(node, other)) union(parent, node, other);
                    }
                }
            }
        }
        return parent;
    }

    private static String spot(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private boolean conducts(Node a, Node b) {
        if (!a.world.equals(b.world)) return false;
        if (linked(a, b)) return true;
        return near(a, b) && !cornerBlocked(a, b);
    }

    private static boolean near(Node a, Node b) {
        return a.world.equals(b.world)
                && Math.abs(a.x - b.x) <= 1
                && Math.abs(a.y - b.y) <= 1
                && Math.abs(a.z - b.z) <= 1;
    }

    private boolean cornerBlocked(Node a, Node b) {
        int dx = Integer.signum(b.x - a.x);
        int dy = Integer.signum(b.y - a.y);
        int dz = Integer.signum(b.z - a.z);
        int axes = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
        if (axes < 2) return false;
        World world = a.world();
        if (world == null) return false;
        int blocked = 0;
        if (dx != 0 && cuts(world.getBlockAt(a.x + dx, a.y, a.z))) blocked++;
        if (dy != 0 && cuts(world.getBlockAt(a.x, a.y + dy, a.z))) blocked++;
        if (dz != 0 && cuts(world.getBlockAt(a.x, a.y, a.z + dz))) blocked++;
        return blocked == axes;
    }

    boolean severBlocked() {
        boolean dirty = false;
        for (Link link : new ArrayList<>(links.values())) {
            Node a = nodes.get(link.a);
            Node b = nodes.get(link.b);
            if (a == null || b == null || a.world() == null) continue;
            if (!visuals.clips(a, b)) continue;
            visuals.clearLink(link);
            links.remove(link.id);
            dirty = true;
        }
        if (dirty) save();
        return dirty;
    }

    boolean covers(Block block) {
        if (block == null) return false;
        if (at(block) != null) return true;
        Node below = at(block.getRelative(org.bukkit.block.BlockFace.DOWN));
        return below != null && (below.type == NodeType.RACK || below.type == NodeType.TERMINAL || below.type == NodeType.POLE);
    }

    boolean cutThrough(Block block) {
        if (block == null || !cuts(block)) return false;
        boolean any = false;
        String world = block.getWorld().getName();
        for (Link link : new ArrayList<>(links.values())) {
            Node a = nodes.get(link.a);
            Node b = nodes.get(link.b);
            if (a == null || b == null || !a.world.equals(world)) continue;
            int minX = Math.min(a.x, b.x) - 1;
            int maxX = Math.max(a.x, b.x) + 1;
            int minY = Math.min(a.y, b.y) - 1;
            int maxY = Math.max(a.y, b.y) + 2;
            int minZ = Math.min(a.z, b.z) - 1;
            int maxZ = Math.max(a.z, b.z) + 1;
            if (block.getX() < minX || block.getX() > maxX || block.getY() < minY || block.getY() > maxY
                    || block.getZ() < minZ || block.getZ() > maxZ) continue;
            if (!visuals.clips(a, b)) continue;
            visuals.clearLink(link);
            links.remove(link.id);
            any = true;
        }
        if (any) save();
        return any;
    }

    private static boolean cuts(Block block) {
        return !block.isEmpty() && !block.isPassable() && block.getType().isSolid();
    }

    private Node anchor(Player player) {
        String id = pulling.get(player.getUniqueId());
        return id == null ? null : nodes.get(id);
    }

    private Node spawnNode(Player player, Block cell, NodeType type, int tier) {
        return spawnNode(player, cell, type, tier, 0);
    }

    private Node spawnNode(Player player, Block cell, NodeType type, int tier, int charge) {
        Node node = new Node(UUID.randomUUID().toString(), player.getUniqueId(), cell.getWorld().getName(),
                cell.getX(), cell.getY(), cell.getZ(), type);
        if (type == NodeType.POLE) node.facing = poleYaw(player);
        else {
            org.bukkit.block.BlockFace look = player.getFacing().getOppositeFace();
            if (look != org.bukkit.block.BlockFace.NORTH && look != org.bukkit.block.BlockFace.SOUTH
                    && look != org.bukkit.block.BlockFace.EAST && look != org.bukkit.block.BlockFace.WEST) {
                look = org.bukkit.block.BlockFace.SOUTH;
            }
            node.facing = look.name();
        }
        node.model = MinerVisuals.MODEL;
        node.tier = type == NodeType.BATTERY ? normalizeBattery(tier) : 1;
        if (type == NodeType.BATTERY && !adminTier(node.tier)) node.charge = Math.max(0, Math.min(capacity(node.tier), charge));
        if (type == NodeType.RACK) node.door = 2;
        remember(node);
        visuals.redraw(node, touching(node));
        for (Node other : touching(node)) visuals.redraw(other, touching(other));
        save();
        return node;
    }

    private void refreshLinks(Node node) {
        for (Link link : new ArrayList<>(links.values())) {
            if (!link.a.equals(node.id) && !link.b.equals(node.id)) continue;
            Node a = nodes.get(link.a);
            Node b = nodes.get(link.b);
            if (a == null || b == null || a.world() == null) continue;
            visuals.drawLink(link, a, b);
        }
    }

    private void connect(Node from, Node to) {
        Link link = new Link(UUID.randomUUID().toString(), from.id, to.id);
        links.put(link.id, link);
        visuals.drawLink(link, from, to);
        save();
    }

    int walletCoins(Player player) {
        int have = MinerItems.balance(player);
        if (have < 0) tell(player, Tone.BAD, "msg.need-wallet");
        return have;
    }

    boolean takeWallet(Player player, int amount) {
        org.bukkit.inventory.ItemStack wallet = MinerItems.walletOf(player);
        if (wallet == null) {
            tell(player, Tone.BAD, "msg.need-wallet");
            return false;
        }
        int have = MinerItems.coins(wallet);
        if (have < amount) {
            tell(player, Tone.BAD, "msg.wallet-low");
            return false;
        }
        MinerItems.setCoins(wallet, have - amount);
        return true;
    }

    boolean giveWallet(Player player, int amount) {
        return pour(player, amount) >= 0;
    }

    private boolean takeCoins(Player player, int cost) {
        ItemStack wallet = MinerItems.walletOf(player);
        if (wallet == null) {
            tell(player, Tone.BAD, "msg.need-wallet");
            return false;
        }
        int have = MinerItems.coins(wallet);
        if (have < cost) {
            tell(player, Tone.BAD, "msg.wallet-low-count", have);
            return false;
        }
        MinerItems.setCoins(wallet, have - cost);
        return true;
    }

    private boolean settleCoins(Player actor, Node node, int coins) {
        if (coins <= 0) return true;
        return pour(actor, coins) >= 0;
    }

    boolean transfer(Player from, Player to, int amount) {
        if (to == null || !to.isOnline()) {
            tell(from, Tone.BAD, "msg.offline");
            return false;
        }
        if (from.getUniqueId().equals(to.getUniqueId())) {
            tell(from, Tone.BAD, "msg.pay-self");
            return false;
        }
        if (amount < 1) {
            tell(from, Tone.BAD, "msg.pay-bad");
            return false;
        }
        if (MinerItems.walletOf(to) == null) {
            tell(from, Tone.BAD, "msg.pay-their-wallet");
            return false;
        }
        if (!takeCoins(from, amount)) return false;
        if (!pourQuiet(to, amount)) {
            pourQuiet(from, amount);
            tell(from, Tone.BAD, "msg.pay-full");
            return false;
        }
        tell(from, Tone.GOLD, "msg.pay-sent", amount, to.getName(), MinerItems.balance(from));
        tell(to, Tone.GOLD, "msg.pay-got", from.getName(), amount, MinerItems.balance(to));
        return true;
    }

    boolean credit(Player player, int amount) {
        return pour(player, amount) >= 0;
    }

    private boolean pourQuiet(Player player, int amount) {
        ItemStack wallet = MinerItems.walletOf(player);
        if (wallet == null || amount <= 0) return false;
        int have = MinerItems.coins(wallet);
        if (have > Integer.MAX_VALUE - amount) return false;
        MinerItems.setCoins(wallet, have + amount);
        return true;
    }

    private int pour(Player player, int amount) {
        if (amount <= 0) return MinerItems.balance(player);
        ItemStack wallet = MinerItems.walletOf(player);
        if (wallet == null) {
            tell(player, Tone.BAD, "msg.need-wallet");
            return -1;
        }
        int have = MinerItems.coins(wallet);
        if (have > Integer.MAX_VALUE - amount) {
            tell(player, Tone.BAD, "msg.wallet-full");
            return -1;
        }
        int now = have + amount;
        MinerItems.setCoins(wallet, now);
        return now;
    }

    private boolean consume(Player player, int cost) {
        int have = 0;
        for (var stack : player.getInventory().getContents()) {
            if (MinerItems.kind(stack) == NodeType.WIRE) have += stack.getAmount();
        }
        if (have < cost) {
            tell(player, Tone.BAD, "msg.need-wire", cost);
            return false;
        }
        int left = cost;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            var stack = inventory.getItem(i);
            if (MinerItems.kind(stack) != NodeType.WIRE) continue;
            int take = Math.min(left, stack.getAmount());
            if (stack.getAmount() - take <= 0) inventory.setItem(i, null);
            else stack.setAmount(stack.getAmount() - take);
            left -= take;
            if (left == 0) return true;
        }
        return true;
    }

    private static int span(Node from, Node dest) {
        boolean pole = from.type == NodeType.POLE;
        boolean other = dest.type == NodeType.POLE;
        if (pole && other) return POLE_SPAN;
        if (pole || other) return POLE_REACH;
        return REACH;
    }

    private static int reach(Node a, Node b) {
        return Math.max(Math.abs(a.x - b.x), Math.max(Math.abs(a.y - b.y), Math.abs(a.z - b.z)));
    }

    private static int reach(Node a, Block cell) {
        return Math.max(Math.abs(a.x - cell.getX()), Math.max(Math.abs(a.y - cell.getY()), Math.abs(a.z - cell.getZ())));
    }

    private static double distance(Node a, Node b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int wireCost(double blocks) {
        return Math.max(1, (int) Math.ceil(blocks / Tune.blocksPerIngot));
    }

    private static double distance(Node a, Block cell) {
        double dx = a.x - cell.getX();
        double dy = a.y - cell.getY();
        double dz = a.z - cell.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static boolean night(World world) {
        if (world == null) return false;
        long time = world.getTime() % 24000;
        return time >= Tune.nightStart && time <= Tune.nightEnd;
    }

    static boolean adminTier(int tier) {
        return tier >= BATTERY_ADMIN;
    }

    static int normalizeBattery(int tier) {
        if (tier >= BATTERY_ADMIN) return BATTERY_ADMIN;
        return Math.max(1, Math.min(BATTERY_TIERS, tier));
    }

    static int amp(int tier) {
        if (adminTier(tier)) return ADMIN_AMP;
        return Tune.batteryAmp(tier);
    }

    static int capacity(int tier) {
        if (adminTier(tier)) return 0;
        return CHARGE_MAX * amp(tier);
    }

    static String batteryName(CommandSender who, int tier) {
        if (adminTier(tier)) return Lang.text(who, "tier.admin");
        String key = switch (normalizeBattery(tier)) {
            case 2 -> "tier.2";
            case 3 -> "tier.3";
            case 4 -> "tier.4";
            default -> "tier.1";
        };
        return Lang.text(who, key);
    }

    static int bars(int charge, int tier) {
        if (adminTier(tier)) return 4;
        int cap = capacity(tier);
        if (charge <= 0 || cap <= 0) return 0;
        return Math.min(4, 1 + (charge - 1) * 4 / cap);
    }

    static org.bukkit.Material barMaterial(int charge, int tier) {
        if (adminTier(tier)) return org.bukkit.Material.MAGENTA_CONCRETE;
        int filled = bars(charge, tier);
        if (filled <= 1) return org.bukkit.Material.RED_CONCRETE;
        if (filled == 2) return org.bukkit.Material.YELLOW_CONCRETE;
        return org.bukkit.Material.LIME_CONCRETE;
    }

    static String chargeText(CommandSender who, int charge, int tier) {
        if (adminTier(tier)) return Lang.text(who, "battery.charge-unlimited");
        return Lang.text(who, "battery.charge", charge, capacity(tier));
    }

    static String runtimeText(CommandSender who, int charge, int tier) {
        if (adminTier(tier)) return Lang.text(who, "battery.runtime-unlimited", ADMIN_AMP);
        int rate = Math.max(1, amp(tier));
        int seconds = charge / rate;
        if (seconds >= 60) return Lang.text(who, "battery.runtime-min", seconds / 60, rate);
        return Lang.text(who, "battery.runtime-sec", seconds, rate);
    }

    static String flowText(CommandSender who, int flow, int charge) {
        return switch (flow) {
            case 1 -> Lang.text(who, "flow.charging");
            case 2 -> Lang.text(who, "flow.draining");
            case 3 -> Lang.text(who, "flow.full");
            case 4 -> Lang.text(who, "flow.unlimited");
            default -> Lang.text(who, charge > 0 ? "flow.holding" : "flow.empty");
        };
    }

    static NamedTextColor flowColor(int flow, int charge) {
        return switch (flow) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.GREEN;
            case 3 -> NamedTextColor.AQUA;
            case 4 -> NamedTextColor.LIGHT_PURPLE;
            default -> charge > 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY;
        };
    }

    private static boolean producing(Node node) {
        World world = node.world();
        if (world == null) return false;
        if (night(world)) return false;
        Block block = world.getBlockAt(node.x, node.y, node.z);
        return block.getLightFromSky() >= Tune.skyLight;
    }

    private static Node find(Map<Node, Node> parent, Node node) {
        Node root = node;
        while (parent.get(root) != root) root = parent.get(root);
        return root;
    }

    private static void union(Map<Node, Node> parent, Node a, Node b) {
        Node ra = find(parent, a);
        Node rb = find(parent, b);
        if (ra != rb) parent.put(ra, rb);
    }

    private void remember(Node node) {
        nodes.put(node.id, node);
        index(node);
    }

    void index(Node node) {
        byEntity.values().removeIf(found -> found == node);
        if (node.click != null) byEntity.put(node.click, node);
        for (UUID id : node.displays) byEntity.put(id, node);
        if (node.lamp != null) byEntity.put(node.lamp, node);
        for (UUID id : node.shells) byEntity.put(id, node);
    }

    private void forget(Node node) {
        nodes.remove(node.id);
        byEntity.values().removeIf(found -> found == node);
    }

    private static void give(Player player, org.bukkit.inventory.ItemStack stack) {
        var left = player.getInventory().addItem(stack);
        for (var extra : left.values()) player.getWorld().dropItemNaturally(player.getLocation(), extra);
    }

    enum Tone { BAD, GOOD, GOLD, YELLOW, GRAY }

    static void tell(Player player, Tone tone, String key, Object... args) {
        if (player == null) return;
        NamedTextColor color = switch (tone) {
            case BAD -> NamedTextColor.RED;
            case GOOD -> NamedTextColor.GREEN;
            case GOLD -> NamedTextColor.GOLD;
            case YELLOW -> NamedTextColor.YELLOW;
            case GRAY -> NamedTextColor.GRAY;
        };
        player.sendActionBar(net.kyori.adventure.text.Component.text(Lang.text(player, key, args)).color(color));
    }

    Node byId(String id) {
        return nodes.get(id);
    }

    static final class Link {
        final String id;
        final String a;
        final String b;
        final List<UUID> displays = new ArrayList<>();
        UUID spark;
        boolean live;

        Link(String id, String a, String b) {
            this.id = id;
            this.a = a;
            this.b = b;
        }

        boolean joins(String left, String right) {
            return (a.equals(left) && b.equals(right)) || (a.equals(right) && b.equals(left));
        }
    }

    static final class Node {
        final String id;
        final UUID owner;
        final String world;
        final int x;
        final int y;
        final int z;
        final NodeType type;
        final List<UUID> displays = new ArrayList<>();
        final List<UUID> shells = new ArrayList<>();
        UUID click;
        UUID lamp;
        String facing = "SOUTH";
        int model;
        int tier = 1;
        static final int RACK_SLOTS = 4;
        int buffer;
        int progress;
        int temp = 20;
        boolean throttle;
        boolean powered;
        int choke;
        int door = 2;
        int charge;
        int bulbs;
        int dust;
        int dustClock;
        int pace;
        int meter = -1;
        int bays;
        final int[] bayBuffer = new int[RACK_SLOTS];
        final int[] bayProgress = new int[RACK_SLOTS];
        final int[] bayTemp = new int[] {20, 20, 20, 20};
        final boolean[] bayOn = new boolean[RACK_SLOTS];
        final boolean[] bayThrottle = new boolean[RACK_SLOTS];
        final int[] bayPace = new int[RACK_SLOTS];

        String rackSig() {
            return bays + java.util.Arrays.toString(bayBuffer) + java.util.Arrays.toString(bayProgress)
                    + java.util.Arrays.toString(bayOn) + java.util.Arrays.toString(bayTemp) + java.util.Arrays.toString(bayThrottle) + choke + door + dust;
        }

        Node(String id, UUID owner, String world, int x, int y, int z, NodeType type) {
            this.id = id;
            this.owner = owner;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
        }

        World world() {
            return Bukkit.getWorld(world);
        }

        org.bukkit.Location corner() {
            World found = world();
            return found == null ? null : new org.bukkit.Location(found, x, y, z);
        }
    }
}
