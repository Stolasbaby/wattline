package wattline;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

final class MinerMenu implements InventoryHolder {

    private static NamespacedKey action;

    private final Page page;
    private final String nodeId;
    private Inventory inventory;
    private int seenProgress = -2;
    private int seenBuffer = -2;
    private boolean seenPower;
    private int seenTier = -1;
    private int seenTemp = -1;
    private int seenChoke = -1;
    private String seenRack = "";
    private String seenQuote = "";
    private int seenCharge;
    private int seenDust = -1;
    private int seenPurse = Integer.MIN_VALUE;
    private boolean seenDay;

    private MinerMenu(Page page, String nodeId) {
        this.page = page;
        this.nodeId = nodeId;
    }

    static void init(NamespacedKey key) {
        action = key;
    }

    static void home(Player player, MinerNodes nodes) {
        MinerMenu menu = new MinerMenu(Page.HOME, null);
        Inventory inv = Bukkit.createInventory(menu, 54, title(t(player, "menu.home"), NamedTextColor.GOLD));
        menu.inventory = inv;
        frame(inv, Material.BLACK_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE);
        inv.setItem(4, button(Material.NETHER_STAR, t(player, "menu.farm"), NamedTextColor.GOLD, null, true,
                c("yellow", t(player, "menu.farm.panels", nodes.count(player, NodeType.PANEL))),
                c("aqua", t(player, "menu.farm.asics", nodes.count(player, NodeType.ASIC))),
                c("white", t(player, "menu.farm.racks", nodes.count(player, NodeType.RACK))),
                c("gold", t(player, "menu.farm.wires", nodes.count(player, NodeType.WIRE))),
                c("gold", t(player, "menu.farm.batteries", nodes.count(player, NodeType.BATTERY))),
                c("gray", t(player, "menu.farm.poles", nodes.count(player, NodeType.POLE)))));
        boolean give = player.hasPermission("miner.give");
        inv.setItem(20, button(Material.CHEST, t(player, "menu.machines"), NamedTextColor.GREEN, "list", false,
                c("gray", t(player, "menu.machines.lore"))));
        inv.setItem(22, button(Material.BARREL, t(player, "menu.items"), NamedTextColor.GOLD, "items", true,
                c("gray", t(player, give ? "menu.items.give1" : "menu.items.look")),
                c("gray", t(player, give ? "menu.items.give2" : "menu.items.op"))));
        inv.setItem(24, button(Material.COMPARATOR, t(player, "menu.settings"), NamedTextColor.WHITE, "prefs", false,
                c("gray", t(player, "menu.settings.lore"))));
        paintDay(inv, player, nodes);
        inv.setItem(33, button(Material.BOOK, t(player, "menu.how"), NamedTextColor.WHITE, null, false,
                c("gray", t(player, "menu.how.1")),
                c("gray", t(player, "menu.how.2")),
                c("gray", t(player, "menu.how.3")),
                c("gray", t(player, "menu.how.4")),
                c("gold", t(player, "menu.how.5"))));
        inv.setItem(40, button(Material.LODESTONE, t(player, "menu.rack-desk"), NamedTextColor.WHITE, "racks", false,
                c("gold", t(player, "menu.rack-desk.lore1")),
                c("gray", t(player, "menu.rack-desk.lore2"))));
        inv.setItem(49, button(Material.BARRIER, t(player, "menu.close"), NamedTextColor.RED, "close", false));
        player.openInventory(inv);
        MinerSettings.play(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.3f);
    }

    static void items(Player player, MinerNodes nodes) {
        MinerMenu menu = new MinerMenu(Page.ITEMS, null);
        Inventory inv = Bukkit.createInventory(menu, 54, title(t(player, "menu.items"), NamedTextColor.GOLD));
        menu.inventory = inv;
        frame(inv, Material.BLACK_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE);
        boolean give = player.hasPermission("miner.give");
        String take = c(give ? "green" : "gray", t(player, give ? "menu.take" : "menu.items.op"));
        inv.setItem(4, button(Material.BARREL, t(player, "menu.catalog"), NamedTextColor.GOLD, null, true,
                c("gray", t(player, give ? "menu.catalog.give" : "menu.catalog.look"))));
        inv.setItem(11, button(Material.LANTERN, t(player, "item.lamp.name"), NamedTextColor.YELLOW, give ? "take:lamp" : null, true,
                c("gray", t(player, "menu.lamp.1", Tune.lamps)), c("gray", t(player, "menu.lamp.2")), take));
        inv.setItem(13, button(Material.BRUSH, t(player, "item.brush.name"), NamedTextColor.GOLD, give ? "take:brush" : null, true,
                c("gray", t(player, "menu.brush.1")), c("gold", t(player, "menu.brush.2")),
                c("red", t(player, "menu.brush.3")), take));
        inv.setItem(15, button(Material.LIGHTNING_ROD, NodeType.POLE.title(player), NamedTextColor.GRAY, give ? "take:pole" : null, true,
                c("gray", t(player, "menu.pole.1", MinerNodes.REACH)),
                c("gray", t(player, "menu.pole.2", MinerNodes.POLE_REACH, MinerNodes.POLE_SPAN)),
                c("gray", t(player, "menu.pole.3")), take));
        inv.setItem(19, button(Material.OBSERVER, NodeType.ASIC.title(player), NamedTextColor.AQUA, give ? "take:asic" : null, true,
                c("gray", t(player, "menu.asic.1", Tune.asicDraw)), take));
        inv.setItem(21, button(Material.DAYLIGHT_DETECTOR, NodeType.PANEL.title(player), NamedTextColor.YELLOW, give ? "take:panel" : null, true,
                c("gray", t(player, "menu.panel.1")), take));
        inv.setItem(23, button(Material.COPPER_INGOT, NodeType.WIRE.title(player), NamedTextColor.GOLD, give ? "take:wire" : null, true,
                c("gray", t(player, "menu.wire.1")), c("gray", t(player, "menu.wire.2", MinerNodes.REACH)), take));
        inv.setItem(25, button(Material.LODESTONE, NodeType.RACK.title(player), NamedTextColor.WHITE, give ? "take:rack" : null, true,
                c("gray", t(player, "menu.rack.1")), c("gray", t(player, "menu.rack.2")), take));
        inv.setItem(29, button(Material.VAULT, NodeType.TERMINAL.title(player), NamedTextColor.GREEN, give ? "take:terminal" : null, true,
                c("gray", t(player, "menu.terminal.1", Tune.terminalDraw)), c("gray", t(player, "menu.terminal.2")), take));
        inv.setItem(31, button(Material.COPPER_BULB, NodeType.BATTERY.title(player), NamedTextColor.GOLD, give ? "take:battery" : null, true,
                c("gray", t(player, "menu.battery.1", MinerNodes.amp(1), Tune.reserveMinutes())),
                c("gray", t(player, "menu.battery.2", MinerNodes.amp(MinerNodes.BATTERY_TIERS))), take));
        inv.setItem(33, button(Material.BUNDLE, t(player, "item.wallet.name"), NamedTextColor.GOLD, give ? "take:wallet" : null, false,
                c("aqua", t(player, "menu.wallet.1")), c("gold", t(player, "menu.wallet.2")), take));
        if (player.hasPermission("miner.admin")) {
            inv.setItem(40, button(Material.RESPAWN_ANCHOR, t(player, "item.battery.admin"), NamedTextColor.LIGHT_PURPLE, "take:adminbattery", true,
                    c("light_purple", t(player, "menu.admin.1")),
                    c("light_purple", t(player, "menu.admin.2")),
                    c("green", t(player, "menu.take"))));
        }
        inv.setItem(49, button(Material.ARROW, t(player, "menu.back"), NamedTextColor.WHITE, "home", false));
        player.openInventory(inv);
    }

    static void prefs(Player player) {
        MinerSettings settings = MinerSettings.get();
        boolean sound = settings == null || settings.sound(player);
        boolean anim = settings == null || settings.anim(player);
        String code = settings == null ? "auto" : settings.language(player);
        MinerMenu menu = new MinerMenu(Page.PREFS, null);
        Inventory inv = Bukkit.createInventory(menu, 54, title(t(player, "menu.prefs"), NamedTextColor.WHITE));
        menu.inventory = inv;
        frame(inv, Material.BLACK_STAINED_GLASS_PANE, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        inv.setItem(20, button(sound ? Material.NOTE_BLOCK : Material.BARRIER, t(player, sound ? "menu.sound.on" : "menu.sound.off"),
                sound ? NamedTextColor.GREEN : NamedTextColor.RED, "pref:sound", sound,
                c("gray", t(player, "menu.sound.1")), c("gray", t(player, "menu.sound.2"))));
        inv.setItem(22, button(Material.NAME_TAG, t(player, "menu.lang", Lang.endonym(code)), NamedTextColor.AQUA, "pref:lang", false,
                c("gray", t(player, "menu.lang.lore"))));
        inv.setItem(24, button(anim ? Material.CLOCK : Material.BARRIER, t(player, anim ? "menu.anim.on" : "menu.anim.off"),
                anim ? NamedTextColor.GREEN : NamedTextColor.RED, "pref:anim", anim,
                c("gray", t(player, "menu.anim.1")), c("gray", t(player, "menu.anim.2"))));
        inv.setItem(49, button(Material.ARROW, t(player, "menu.back"), NamedTextColor.WHITE, "home", false));
        player.openInventory(inv);
    }

    static void list(Player player, MinerNodes nodes) {
        list(player, nodes, null);
    }

    static void list(Player player, MinerNodes nodes, NodeType filter) {
        MinerMenu menu = new MinerMenu(Page.LIST, null);
        Inventory inv = Bukkit.createInventory(menu, 54, title(t(player, "menu.list"), NamedTextColor.GREEN));
        menu.inventory = inv;
        frame(inv, Material.BLACK_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE);
        int slot = 10;
        boolean any = false;
        for (MinerNodes.Node node : nodes.owned(player)) {
            if (filter != null && node.type != filter) continue;
            any = true;
            if (slot == 17 || slot == 26 || slot == 35) slot++;
            if (slot >= 44) break;
            inv.setItem(slot++, machineIcon(player, nodes, node, "node:" + node.id));
        }
        if (!any) {
            inv.setItem(22, button(Material.BARRIER, t(player, filter == null ? "menu.empty" : "menu.none"), NamedTextColor.GRAY, null, false,
                    c("gray", t(player, filter == null ? "menu.empty.lore" : "menu.none.lore"))));
        }
        inv.setItem(45, button(Material.NETHER_STAR, t(player, "menu.filter.all"), NamedTextColor.WHITE, "filter:all", filter == null));
        inv.setItem(46, button(Material.OBSERVER, t(player, "menu.filter.asics"), NamedTextColor.AQUA, "filter:asic", filter == NodeType.ASIC));
        inv.setItem(47, button(Material.DAYLIGHT_DETECTOR, t(player, "menu.filter.panels"), NamedTextColor.YELLOW, "filter:panel", filter == NodeType.PANEL));
        inv.setItem(48, button(Material.LODESTONE, t(player, "menu.filter.racks"), NamedTextColor.WHITE, "filter:rack", filter == NodeType.RACK));
        inv.setItem(50, button(Material.COPPER_INGOT, t(player, "menu.filter.wires"), NamedTextColor.GOLD, "filter:wire", filter == NodeType.WIRE));
        inv.setItem(51, button(Material.VAULT, t(player, "menu.filter.exchange"), NamedTextColor.GREEN, "filter:terminal", filter == NodeType.TERMINAL));
        inv.setItem(52, button(Material.COPPER_BULB, t(player, "menu.filter.batteries"), NamedTextColor.GOLD, "filter:battery", filter == NodeType.BATTERY));
        inv.setItem(49, button(Material.ARROW, t(player, "menu.back"), NamedTextColor.WHITE, "home", false));
        player.openInventory(inv);
    }

    static void racks(Player player, MinerNodes nodes) {
        MinerMenu menu = new MinerMenu(Page.RACKS, null);
        Inventory inv = Bukkit.createInventory(menu, 54, title(t(player, "menu.rack-desk"), NamedTextColor.WHITE));
        menu.inventory = inv;
        paintRacks(inv, player, nodes);
        menu.seenRack = nodes.rackDesk(player);
        player.openInventory(inv);
        MinerSettings.play(player, Sound.BLOCK_BARREL_OPEN, 0.5f, 1.2f);
    }

    private static void paintRacks(Inventory inv, Player player, MinerNodes nodes) {
        frame(inv, Material.BLACK_STAINED_GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE);
        int count = 0;
        int coins = 0;
        int dusty = 0;
        int slot = 10;
        for (MinerNodes.Node node : nodes.owned(player)) {
            if (node.type != NodeType.RACK) continue;
            count++;
            coins += nodes.rackCoins(node);
            if (node.dust > 0) dusty++;
            if (slot == 17 || slot == 26 || slot == 35) slot++;
            if (slot >= 44) continue;
            inv.setItem(slot++, machineIcon(player, nodes, node, "node:" + node.id));
        }
        if (count == 0) {
            inv.setItem(22, button(Material.BARRIER, t(player, "menu.racks.none"), NamedTextColor.GRAY, null, false,
                    c("gray", t(player, "menu.racks.none.lore"))));
        }
        inv.setItem(4, button(Material.LODESTONE, t(player, "menu.racks.count", count), NamedTextColor.WHITE, null, count > 0,
                c("gold", t(player, "menu.racks.coins", coins)),
                dusty == 0 ? c("green", t(player, "menu.racks.clean")) : c("gold", t(player, "menu.racks.dusty", dusty)),
                c("gray", t(player, "menu.racks.open"))));
        inv.setItem(47, button(Material.GOLD_NUGGET, t(player, "menu.racks.take"), NamedTextColor.GOLD, "racks:take", coins > 0,
                c("gold", t(player, "menu.racks.coins", coins)),
                c("gold", t(player, "menu.racks.take.wallet"))));
        inv.setItem(51, button(Material.BRUSH, t(player, "menu.racks.sweep"), dusty > 0 ? NamedTextColor.GOLD : NamedTextColor.GREEN, "racks:clean", dusty > 0,
                dusty == 0 ? c("green", t(player, "msg.already-clean")) : c("gold", t(player, "menu.racks.dusty", dusty)),
                c("gray", t(player, "menu.racks.need-brush"))));
        inv.setItem(49, button(Material.ARROW, t(player, "menu.back"), NamedTextColor.WHITE, "home", false));
    }

    static void machine(Player player, MinerNodes nodes, MinerNodes.Node node) {
        MinerMenu menu = new MinerMenu(Page.MACHINE, node.id);
        Inventory inv = Bukkit.createInventory(menu, 54, title(node.type.title(player), node.type.color));
        menu.inventory = inv;
        Material accent = switch (node.type) {
            case ASIC -> Material.CYAN_STAINED_GLASS_PANE;
            case PANEL -> Material.YELLOW_STAINED_GLASS_PANE;
            case WIRE -> Material.ORANGE_STAINED_GLASS_PANE;
            case RACK -> Material.WHITE_STAINED_GLASS_PANE;
            case TERMINAL -> Material.LIME_STAINED_GLASS_PANE;
            case BATTERY -> Material.ORANGE_STAINED_GLASS_PANE;
            case POLE -> Material.BROWN_STAINED_GLASS_PANE;
        };
        frame(inv, Material.GRAY_STAINED_GLASS_PANE, accent);
        paint(inv, player, nodes, node);
        inv.setItem(45, button(Material.ARROW, t(player, "menu.back"), NamedTextColor.WHITE, "home", false));
        inv.setItem(49, button(Material.BARRIER, t(player, "menu.close"), NamedTextColor.RED, "close", false));
        player.openInventory(inv);
        MinerSettings.play(player, Sound.BLOCK_BARREL_OPEN, 0.5f, 1.4f);
    }

    static void confirm(Player player, MinerNodes nodes, MinerNodes.Node node) {
        MinerMenu menu = new MinerMenu(Page.CONFIRM, node.id);
        Inventory inv = Bukkit.createInventory(menu, 27, title(t(player, "menu.confirm"), NamedTextColor.RED));
        menu.inventory = inv;
        frame(inv, Material.BLACK_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE);
        String warn = node.type != NodeType.BATTERY ? t(player, "menu.confirm.tokens")
                : MinerNodes.adminTier(node.tier) ? t(player, "menu.confirm.item") : t(player, "menu.confirm.charge");
        inv.setItem(11, button(Material.LIME_CONCRETE, t(player, "menu.confirm.yes"), NamedTextColor.GREEN, "yes", false, c("gray", warn)));
        inv.setItem(13, machineIcon(player, nodes, node, null));
        inv.setItem(15, button(Material.RED_CONCRETE, t(player, "menu.back"), NamedTextColor.RED, "back", false));
        player.openInventory(inv);
    }

    static void refresh(MinerNodes nodes) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof MinerMenu menu)) continue;
            if (menu.page != Page.MACHINE && menu.page != Page.HOME && menu.page != Page.RACKS) continue;
            if (menu.page == Page.RACKS) {
                String desk = nodes.rackDesk(player);
                if (desk.equals(menu.seenRack)) continue;
                paintRacks(menu.inventory, player, nodes);
                menu.seenRack = desk;
                continue;
            }
            if (menu.page == Page.HOME) {
                boolean day = day(player);
                int purse = MinerItems.balance(player);
                if (day == menu.seenDay && menu.seenBuffer == nodes.coinsOf(player) && menu.seenPurse == purse) continue;
                menu.seenDay = day;
                menu.seenBuffer = nodes.coinsOf(player);
                menu.seenPurse = purse;
                paintDay(menu.inventory, player, nodes);
                continue;
            }
            MinerNodes.Node node = nodes.byId(menu.nodeId);
            if (node == null) {
                player.closeInventory();
                continue;
            }
            if (node.type == NodeType.ASIC || node.type == NodeType.RACK) nodes.breathe(node);
            String rackSig = node.type == NodeType.RACK ? node.rackSig() : "";
            String quote = node.type == NodeType.TERMINAL && MinerMarket.get() != null ? MinerMarket.get().quote() : "";
            if (node.progress == menu.seenProgress && node.buffer == menu.seenBuffer && node.powered == menu.seenPower
                    && node.tier == menu.seenTier && node.temp == menu.seenTemp && node.choke == menu.seenChoke
                    && rackSig.equals(menu.seenRack) && quote.equals(menu.seenQuote) && node.charge == menu.seenCharge
                    && node.dust == menu.seenDust) continue;
            paint(menu.inventory, player, nodes, node);
            menu.seenProgress = node.progress;
            menu.seenBuffer = node.buffer;
            menu.seenPower = node.powered;
            menu.seenTier = node.tier;
            menu.seenTemp = node.temp;
            menu.seenChoke = node.choke;
            menu.seenRack = rackSig;
            menu.seenQuote = quote;
            menu.seenCharge = node.charge;
            menu.seenDust = node.dust;
        }
    }

    static void click(InventoryClickEvent event, MinerNodes nodes) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MinerMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack stack = event.getCurrentItem();
        if (stack == null || action == null || !stack.hasItemMeta()) return;
        String act = stack.getItemMeta().getPersistentDataContainer().get(action, PersistentDataType.STRING);
        if (act == null) return;
        MinerSettings.play(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.6f);
        switch (act) {
            case "close" -> player.closeInventory();
            case "home" -> home(player, nodes);
            case "items" -> items(player, nodes);
            case "list" -> list(player, nodes);
            case "racks" -> racks(player, nodes);
            case "unlamp" -> unlamp(player, nodes, menu.nodeId);
            case "racks:take" -> {
                nodes.collectRacks(player);
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof MinerMenu open && open.page == Page.RACKS) {
                    paintRacks(open.inventory, player, nodes);
                    open.seenRack = nodes.rackDesk(player);
                }
            }
            case "racks:clean" -> {
                nodes.sweepRacks(player);
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof MinerMenu open && open.page == Page.RACKS) {
                    paintRacks(open.inventory, player, nodes);
                    open.seenRack = nodes.rackDesk(player);
                }
            }
            case "filter:all" -> list(player, nodes, null);
            case "filter:asic" -> list(player, nodes, NodeType.ASIC);
            case "filter:panel" -> list(player, nodes, NodeType.PANEL);
            case "filter:rack" -> list(player, nodes, NodeType.RACK);
            case "filter:wire" -> list(player, nodes, NodeType.WIRE);
            case "filter:terminal" -> list(player, nodes, NodeType.TERMINAL);
            case "filter:battery" -> list(player, nodes, NodeType.BATTERY);
            case "prefs" -> prefs(player);
            case "pref:sound" -> {
                MinerSettings.get().toggleSound(player);
                prefs(player);
            }
            case "pref:anim" -> {
                MinerSettings.get().toggleAnim(player);
                prefs(player);
            }
            case "pref:lang" -> {
                if (!player.hasPermission("miner.language")) {
                    MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.lang-denied");
                    return;
                }
                MinerSettings settings = MinerSettings.get();
                if (settings != null) {
                    settings.setLanguage(player, Lang.next(settings.language(player)));
                    MinerItems.dress(player);
                }
                prefs(player);
            }
            case "door:shut" -> door(player, nodes, menu.nodeId, 1);
            case "door:open" -> door(player, nodes, menu.nodeId, 2);
            case "take:asic" -> give(player, NodeType.ASIC);
            case "take:panel" -> give(player, NodeType.PANEL);
            case "take:wire" -> give(player, NodeType.WIRE);
            case "take:pole" -> give(player, NodeType.POLE);
            case "take:rack" -> give(player, NodeType.RACK);
            case "take:terminal" -> give(player, NodeType.TERMINAL);
            case "take:battery" -> giveBattery(player, 1);
            case "take:adminbattery" -> giveBattery(player, MinerNodes.BATTERY_ADMIN);
            case "dust" -> dust(player, nodes, menu.nodeId);
            case "take:brush" -> giveBrush(player);
            case "take:lamp" -> giveLamp(player);
            case "take:wallet" -> {
                if (!player.hasPermission("miner.give")) {
                    MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.no-permission");
                    return;
                }
                player.getInventory().addItem(MinerItems.wallet(player));
                MinerSettings.play(player, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
            }
            case "pull" -> pull(player, nodes, menu.nodeId);
            case "collect" -> collect(player, nodes, menu.nodeId);
            case "upgrade" -> upgrade(player, nodes, menu.nodeId);
            case "confirm" -> {
                MinerNodes.Node node = nodes.byId(menu.nodeId);
                if (node != null && nodes.canBreak(player, node)) confirm(player, nodes, node);
                else MinerNodes.tell(player, MinerNodes.Tone.BAD, node != null && nodes.owner(player, node) ? "msg.no-permission" : "msg.not-yours");
            }
            case "yes" -> destroy(player, nodes, menu.nodeId);
            case "back" -> {
                MinerNodes.Node back = nodes.byId(menu.nodeId);
                if (back != null) machine(player, nodes, back);
                else home(player, nodes);
            }
            default -> {
                if (act.startsWith("sell:") || act.startsWith("buy:")) {
                    MinerMarket market = MinerMarket.get();
                    MinerNodes.Node desk = nodes.byId(menu.nodeId);
                    if (market != null && desk != null && desk.type == NodeType.TERMINAL) {
                        if (!desk.powered) {
                            MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.no-power");
                            paint(player.getOpenInventory().getTopInventory(), player, nodes, desk);
                            return;
                        }
                        boolean sell = act.startsWith("sell:");
                        String raw = act.substring(sell ? 5 : 4);
                        int count;
                        try {
                            count = raw.equals("all") ? -1 : Integer.parseInt(raw);
                        } catch (NumberFormatException e) {
                            return;
                        }
                        if (sell) market.sell(player, count);
                        else market.buy(player, count);
                        paint(player.getOpenInventory().getTopInventory(), player, nodes, desk);
                    }
                    return;
                }
                if (act.startsWith("bay:")) {
                    MinerNodes.Node rack = nodes.byId(menu.nodeId);
                    if (rack != null) {
                        nodes.collectBay(player, rack, Integer.parseInt(act.substring(4)));
                        paint(player.getOpenInventory().getTopInventory(), player, nodes, rack);
                        MinerSettings.play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
                    }
                    return;
                }
                if (act.startsWith("out:")) {
                    MinerNodes.Node rack = nodes.byId(menu.nodeId);
                    if (rack != null) {
                        nodes.eject(player, rack, Integer.parseInt(act.substring(4)));
                        paint(player.getOpenInventory().getTopInventory(), player, nodes, rack);
                    }
                    return;
                }
                if (act.startsWith("node:")) {
                    MinerNodes.Node node = nodes.byId(act.substring(5));
                    if (node != null && (node.type == NodeType.TERMINAL || nodes.canBuild(player, node))) machine(player, nodes, node);
                }
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static void paint(Inventory inv, Player player, MinerNodes nodes, MinerNodes.Node node) {
        inv.setItem(4, machineIcon(player, nodes, node, null));
        if (node.type == NodeType.ASIC) {
            inv.setItem(20, button(node.powered ? Material.LIME_DYE : Material.RED_DYE,
                    t(player, node.powered ? "menu.power-on" : "menu.power-off"),
                    node.powered ? NamedTextColor.GREEN : NamedTextColor.RED, null, node.powered,
                    c("aqua", t(player, "menu.owner", nodes.ownerName(player, node))),
                    airLine(player, node),
                    c("gold", t(player, "menu.dust-pct", node.dust)),
                    c("gold", t(player, "menu.hash-pct", MinerNodes.hashOf(node.dust))),
                    c("gold", t(player, "menu.tokens-n", node.buffer)),
                    c("dark_green", t(player, "menu.eta", MinerNodes.eta(node.progress, node.dust)))));
            heatColumn(inv, player, node.temp, node.throttle);
            dustButton(inv, player, node);
            inv.setItem(22, button(Material.GOLD_NUGGET, t(player, "menu.collect"), NamedTextColor.GOLD, "collect", node.buffer > 0,
                    c("gold", t(player, "menu.collect.now", node.buffer))));
            int cells = 5;
            int filled = node.powered ? Math.min(cells, 1 + node.progress * cells / MinerNodes.COIN_SECONDS) : 0;
            for (int i = 0; i < cells; i++) {
                inv.setItem(29 + i, pane(i < filled ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE));
            }
        } else if (node.type == NodeType.RACK) {
            inv.setItem(20, button(node.bays == 0 ? Material.GRAY_DYE : node.powered ? Material.LIME_DYE : Material.RED_DYE,
                    t(player, "menu.blades", node.bays, MinerNodes.Node.RACK_SLOTS),
                    NamedTextColor.WHITE, null, node.powered,
                    c("aqua", t(player, "menu.owner", nodes.ownerName(player, node))),
                    airLine(player, node),
                    c("gold", t(player, "menu.dust-pct", node.dust)),
                    c("gold", t(player, "menu.hash-pct", MinerNodes.hashOf(node.dust))),
                    c("gold", t(player, "menu.tokens-n", nodes.rackCoins(node))),
                    c("yellow", t(player, "menu.blade-draw", Tune.bladeDraw)),
                    c("gold", t(player, "menu.blade-queue"))));
            doorButton(inv, player, node);
            dustButton(inv, player, node);
            int rackCoins = nodes.rackCoins(node);
            inv.setItem(22, button(Material.GOLD_NUGGET, t(player, "menu.collect-all"), NamedTextColor.GOLD, "collect", rackCoins > 0,
                    c("gold", t(player, "menu.tokens-n", rackCoins)),
                    c("dark_aqua", t(player, "menu.collect-own"))));
            int[] slots = {37, 28, 19, 10};
            for (int bay = 0; bay < MinerNodes.Node.RACK_SLOTS; bay++) {
                if (bay >= node.bays) {
                    inv.setItem(slots[bay], button(Material.GRAY_STAINED_GLASS_PANE, t(player, "menu.bay-empty", bay + 1), NamedTextColor.DARK_GRAY, null, false,
                            c("gray", t(player, "menu.bay-insert"))));
                    inv.setItem(heatSlots[bay], button(Material.GRAY_STAINED_GLASS_PANE, t(player, "menu.bay-none"), NamedTextColor.DARK_GRAY, null, false));
                    continue;
                }
                boolean on = node.bayOn[bay];
                inv.setItem(slots[bay], button(Material.OBSERVER, t(player, "menu.bay", bay + 1), NamedTextColor.AQUA, "bay:" + bay, on,
                        c(on ? "green" : "red", t(player, on ? "menu.power-on" : "menu.power-off")),
                        c("gold", t(player, "menu.tokens-n", node.bayBuffer[bay])),
                        c("dark_green", t(player, "menu.eta", MinerNodes.eta(node.bayProgress[bay], node.dust))),
                        c("green", t(player, "menu.bay-take"))));
                inv.setItem(slots[bay] + 2, button(Material.HOPPER, t(player, "menu.eject"), NamedTextColor.RED, "out:" + bay, false,
                        c("gray", t(player, "menu.eject.lore"))));
                inv.setItem(heatSlots[bay], heatLamp(player, node.bayTemp[bay], node.bayThrottle[bay], c("gray", t(player, "menu.bay", bay + 1))));
            }
        } else if (node.type == NodeType.PANEL) {
            boolean on = node.powered;
            int tier = Math.max(1, Math.min(MinerNodes.PANEL_TIERS, node.tier));
            inv.setItem(20, button(on ? Material.SUNFLOWER : Material.CLOCK, t(player, on ? "menu.shining" : "menu.idle"),
                    on ? NamedTextColor.YELLOW : NamedTextColor.GRAY, null, on,
                    c("aqua", t(player, "menu.owner", nodes.ownerName(player, node))),
                    c("yellow", MinerNodes.tierName(player, tier)),
                    c("yellow", t(player, "menu.current", MinerNodes.output(tier)))));
            inv.setItem(22, button(Material.REPEATER, t(player, "menu.level", tier), NamedTextColor.WHITE, null, tier > 1,
                    c("yellow", MinerNodes.tierName(player, tier)),
                    c("yellow", t(player, "menu.output", MinerNodes.output(tier)))));
            if (tier < MinerNodes.PANEL_TIERS) {
                int next = tier + 1;
                inv.setItem(31, button(Material.ANVIL, t(player, "menu.upgrade"), NamedTextColor.GREEN, "upgrade", true,
                        c("yellow", t(player, "menu.becomes", MinerNodes.tierName(player, next))),
                        c("yellow", t(player, "menu.current", MinerNodes.output(next))),
                        c("gold", t(player, "menu.price", MinerNodes.upgradeCost(tier)))));
            } else {
                inv.setItem(31, button(Material.NETHER_STAR, t(player, "menu.max"), NamedTextColor.GOLD, null, true,
                        c("yellow", t(player, "menu.current", MinerNodes.output(MinerNodes.PANEL_TIERS)))));
            }
        } else if (node.type == NodeType.BATTERY) {
            int filled = MinerNodes.bars(node.charge, node.tier);
            Material fill = MinerNodes.barMaterial(node.charge, node.tier);
            boolean admin = MinerNodes.adminTier(node.tier);
            int rate = MinerNodes.amp(node.tier);
            inv.setItem(13, button(admin ? Material.RESPAWN_ANCHOR : Material.COPPER_BULB, MinerNodes.flowText(player, node.progress, node.charge),
                    MinerNodes.flowColor(node.progress, node.charge), null, node.powered,
                    c("yellow", MinerNodes.batteryName(player, node.tier)),
                    c("gold", MinerNodes.chargeText(player, node.charge, node.tier)),
                    c("aqua", MinerNodes.runtimeText(player, node.charge, node.tier)),
                    c("gray", t(player, admin ? "menu.both" : "menu.day-take", rate)),
                    c(admin ? "light_purple" : "green", t(player, admin ? "menu.never-drains" : "menu.night-give", rate)),
                    c("gold", t(player, "menu.order")),
                    c("gray", t(player, admin ? "menu.operator-only" : "menu.pickup-charge"))));
            for (int i = 0; i < 4; i++) {
                boolean lit = i < filled;
                inv.setItem(19 + i, button(lit ? fill : Material.GRAY_STAINED_GLASS_PANE,
                        t(player, lit ? "menu.cell" : "menu.cell-empty", i + 1),
                        lit ? MinerNodes.flowColor(node.progress, node.charge) : NamedTextColor.DARK_GRAY, null, lit));
            }
            if (admin) {
                inv.setItem(31, button(Material.NETHER_STAR, t(player, "menu.admin-tier"), NamedTextColor.LIGHT_PURPLE, null, true,
                        c("yellow", t(player, "menu.current", rate)),
                        c("light_purple", t(player, "menu.no-upgrade"))));
            } else if (node.tier < MinerNodes.BATTERY_TIERS) {
                int next = node.tier + 1;
                inv.setItem(31, button(Material.ANVIL, t(player, "menu.upgrade"), NamedTextColor.GREEN, "upgrade", true,
                        c("yellow", t(player, "menu.becomes", MinerNodes.batteryName(player, next))),
                        c("yellow", t(player, "menu.current", MinerNodes.amp(next))),
                        c("aqua", t(player, "menu.reserve-10", Tune.reserveMinutes())),
                        c("gold", t(player, "menu.price", MinerNodes.upgradeCost(node.tier)))));
            } else {
                inv.setItem(31, button(Material.NETHER_STAR, t(player, "menu.max"), NamedTextColor.GOLD, null, true,
                        c("yellow", t(player, "menu.current", rate))));
            }
        } else if (node.type == NodeType.TERMINAL) {
            MinerMarket market = MinerMarket.get();
            boolean up = market != null && market.rising();
            boolean live = market != null && market.online();
            inv.setItem(13, button(Material.RECOVERY_COMPASS, market == null ? t(player, "menu.rate") : t(player, "menu.rate-n", market.boardLine()),
                    node.powered ? (up ? NamedTextColor.GREEN : NamedTextColor.RED) : NamedTextColor.GRAY, null, node.powered,
                    c(node.powered ? "green" : "red", t(player, node.powered ? "menu.power-on" : "menu.power-off")),
                    c(live ? "green" : "red", live ? market.cardLoreSell(player) : t(player, "menu.currency-off")),
                    c(live ? "gold" : "red", live ? market.cardLoreBuy(player) : t(player, "menu.need-vault")),
                    c("aqua", market == null ? t(player, "menu.rate-later") : market.cardLoreWait(player)),
                    c("gray", t(player, "menu.terminal-draw", Tune.terminalDraw))));
            int[] packs = Tune.packs;
            for (int i = 0; i < packs.length; i++) {
                int count = packs[i];
                inv.setItem(19 + i, button(Material.GOLD_NUGGET, t(player, "menu.sell-n", count), NamedTextColor.GREEN, "sell:" + count, false,
                        c("green", market == null ? t(player, "menu.no-rate") : t(player, "menu.gain", market.gain(player, count)))));
                inv.setItem(28 + i, button(Material.EMERALD, t(player, "menu.buy-n", count), NamedTextColor.GOLD, "buy:" + count, false,
                        c("gold", market == null ? t(player, "menu.no-rate") : t(player, "menu.pay", market.cost(player, count)))));
            }
            inv.setItem(23, button(Material.CHEST, t(player, "menu.sell-all"), NamedTextColor.GREEN, "sell:all", true,
                    c("gray", t(player, "menu.sell-all.lore"))));
            inv.setItem(32, button(Material.CHEST, t(player, "menu.buy-all"), NamedTextColor.GOLD, "buy:all", true,
                    c("gray", t(player, "menu.buy-all.lore"))));
        } else if (node.type == NodeType.POLE) {
            inv.setItem(13, button(Material.LANTERN, t(player, "menu.lamps", node.bulbs, Tune.lamps), NamedTextColor.YELLOW, null, node.bulbs > 0,
                    c("aqua", t(player, "menu.owner", nodes.ownerName(player, node))),
                    c("gray", t(player, "menu.lamps.pretty")),
                    c("gray", t(player, "menu.lamps.click")),
                    c("gray", t(player, "menu.lamps.reach", MinerNodes.POLE_REACH)),
                    c("gray", t(player, "menu.lamps.span", MinerNodes.POLE_SPAN))));
            if (node.bulbs > 0) {
                inv.setItem(31, button(Material.LANTERN, t(player, "menu.unlamp"), NamedTextColor.GOLD, "unlamp", false,
                        c("gray", t(player, "menu.unlamp.lore"))));
            }
        } else {
            inv.setItem(20, button(Material.COPPER_BLOCK, t(player, "menu.node"), NamedTextColor.GOLD, null, false,
                    c("yellow", t(player, "menu.links", nodes.degree(node)))));
            inv.setItem(22, button(Material.STRING, t(player, "menu.pulls"), NamedTextColor.WHITE, null, false,
                    c("gray", t(player, "menu.pulls.lore"))));
        }
        String reach = t(player, node.type == NodeType.POLE ? "menu.pull.pole" : "menu.pull.reach",
                node.type == NodeType.POLE ? MinerNodes.POLE_REACH : MinerNodes.REACH,
                MinerNodes.POLE_SPAN);
        inv.setItem(24, button(Material.COPPER_INGOT, t(player, "menu.pull"), NamedTextColor.GOLD, "pull", true,
                c("gray", t(player, "menu.pull.then")), c("yellow", reach)));
        inv.setItem(40, button(Material.RED_CONCRETE, t(player, "menu.pick"), NamedTextColor.RED, "confirm", false,
                c("gray", t(player, "menu.pick.lore"))));
    }

    private static void pull(Player player, MinerNodes nodes, String id) {
        MinerNodes.Node node = nodes.byId(id);
        if (node == null) return;
        player.closeInventory();
        nodes.startPull(player, node);
    }

    private static void upgrade(Player player, MinerNodes nodes, String id) {
        MinerNodes.Node node = nodes.byId(id);
        if (node == null || !nodes.upgrade(player, node)) return;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MinerMenu) {
            paint(player.getOpenInventory().getTopInventory(), player, nodes, node);
        }
    }

    private static void collect(Player player, MinerNodes nodes, String id) {
        MinerNodes.Node node = nodes.byId(id);
        if (node == null) return;
        if (node.type == NodeType.RACK) nodes.collectRack(player, node);
        else if (node.type == NodeType.ASIC) nodes.use(player, node);
        else return;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MinerMenu) {
            paint(player.getOpenInventory().getTopInventory(), player, nodes, node);
        }
        MinerSettings.play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
    }

    private static void destroy(Player player, MinerNodes nodes, String id) {
        MinerNodes.Node node = nodes.byId(id);
        player.closeInventory();
        if (node == null || !nodes.canBreak(player, node)) {
            MinerNodes.tell(player, MinerNodes.Tone.BAD, node != null && nodes.owner(player, node) ? "msg.no-permission" : "msg.not-yours");
            return;
        }
        if (!nodes.remove(node, player)) return;
        MinerSettings.play(player, Sound.BLOCK_COPPER_BREAK, 0.8f, 0.9f);
        MinerNodes.tell(player, MinerNodes.Tone.GOOD, "msg.removed");
    }

    private static void giveBattery(Player player, int tier) {
        if (MinerNodes.adminTier(tier)) {
            if (!player.hasPermission("miner.admin")) {
                MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.no-permission");
                return;
            }
        } else if (!player.hasPermission("miner.give")) {
            MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.no-permission");
            return;
        }
        player.getInventory().addItem(MinerItems.battery(player, tier));
        MinerSettings.play(player, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
    }

    private static void give(Player player, NodeType type) {
        if (!player.hasPermission("miner.give")) {
            MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.no-permission");
            return;
        }
        player.getInventory().addItem(MinerItems.item(player, type));
        MinerSettings.play(player, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
    }

    private static ItemStack machineIcon(Player player, MinerNodes nodes, MinerNodes.Node node, String act) {
        String power = node.type == NodeType.WIRE ? c("yellow", t(player, "menu.links", nodes.degree(node)))
                : node.type == NodeType.TERMINAL ? c(node.powered ? "green" : "red", t(player, node.powered ? "menu.power-on" : "menu.power-off"))
                : node.type == NodeType.POLE ? c("gray", t(player, "menu.support", nodes.degree(node)))
                : node.type == NodeType.BATTERY ? c("gold", MinerNodes.flowText(player, node.progress, node.charge))
                : c(node.powered ? "green" : "gray", t(player, node.powered ? "menu.works" : "menu.idle"));
        List<String> lore = new ArrayList<>();
        lore.add(node.x + " " + node.y + " " + node.z);
        lore.add(power);
        if (node.type == NodeType.ASIC || node.type == NodeType.RACK) nodes.breathe(node);
        if (node.type == NodeType.ASIC) {
            lore.add(c("aqua", t(player, "menu.owner", nodes.ownerName(player, node))));
            lore.add(airLine(player, node));
            lore.add(tempLine(player, node.temp, node.throttle));
            lore.add(c("gold", t(player, "menu.dust-hash", MinerNodes.dustText(player, node.dust), MinerNodes.hashOf(node.dust))));
            lore.add(c("gold", t(player, "menu.tokens-n", node.buffer)));
        }
        if (node.type == NodeType.RACK) {
            lore.add(c("aqua", t(player, "menu.owner", nodes.ownerName(player, node))));
            lore.add(airLine(player, node));
            lore.add(c("white", t(player, "menu.blades", node.bays, MinerNodes.Node.RACK_SLOTS)));
            lore.add(c("gold", t(player, "menu.dust-hash", MinerNodes.dustText(player, node.dust), MinerNodes.hashOf(node.dust))));
            lore.add(c("gold", t(player, "menu.tokens-n", nodes.rackCoins(node))));
        }
        if (node.type == NodeType.PANEL) {
            lore.add(c("aqua", t(player, "menu.owner", nodes.ownerName(player, node))));
            lore.add(c("yellow", MinerNodes.tierName(player, node.tier) + ", " + t(player, "menu.current", MinerNodes.output(node.tier))));
        }
        if (node.type == NodeType.TERMINAL && MinerMarket.get() != null) {
            lore.add(c("aqua", t(player, "menu.owner", nodes.ownerName(player, node))));
            lore.add(c("green", MinerMarket.get().cardLoreSell(player)));
            lore.add(c("gold", MinerMarket.get().cardLoreBuy(player)));
        }
        if (node.type == NodeType.BATTERY) {
            lore.add(c("aqua", t(player, "menu.owner", nodes.ownerName(player, node))));
            lore.add(c("yellow", MinerNodes.batteryName(player, node.tier)));
            lore.add(c("gold", MinerNodes.chargeText(player, node.charge, node.tier)));
            lore.add(c("aqua", MinerNodes.runtimeText(player, node.charge, node.tier)));
        }
        if (node.type == NodeType.POLE) {
            lore.add(c("aqua", t(player, "menu.owner", nodes.ownerName(player, node))));
        }
        return button(node.type.material, node.type.title(player), node.type.color, act, node.powered, lore.toArray(String[]::new));
    }

    private static void paintDay(Inventory inv, Player player, MinerNodes nodes) {
        boolean day = day(player);
        inv.setItem(29, button(day ? Material.SUNFLOWER : Material.CLOCK, t(player, day ? "menu.day" : "menu.night"),
                day ? NamedTextColor.YELLOW : NamedTextColor.BLUE, null, false,
                c(day ? "yellow" : "blue", t(player, day ? "menu.day.lore" : "menu.night.lore"))));
        inv.setItem(31, button(Material.GOLD_NUGGET, t(player, "menu.tokens"), NamedTextColor.GOLD, null, false,
                c("gold", t(player, "menu.tokens.yours", nodes.coinsOf(player))),
                purseLine(player)));
    }

    private static String purseLine(Player player) {
        int purse = MinerItems.balance(player);
        return purse < 0 ? c("red", t(player, "menu.no-purse")) : c("gold", t(player, "menu.purse", purse));
    }

    private static boolean day(Player player) {
        return !MinerNodes.night(player.getWorld());
    }

    private static void frame(Inventory inv, Material edge, Material corner) {
        ItemStack side = pane(edge);
        ItemStack mark = pane(corner);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, side);
        int last = inv.getSize() - 1;
        inv.setItem(0, mark);
        inv.setItem(8, mark);
        if (last >= 8) {
            inv.setItem(last - 8, mark);
            inv.setItem(last, mark);
        }
    }

    private static ItemStack pane(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.empty());
        meta.setHideTooltip(true);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack button(Material material, String name, NamedTextColor color, String act, boolean glint, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(shown(name)).color(color).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Component.text(shown(line)).color(colorOf(line)).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lines);
        }
        if (glint) meta.setEnchantmentGlintOverride(true);
        if (act != null && action != null) meta.getPersistentDataContainer().set(action, PersistentDataType.STRING, act);
        stack.setItemMeta(meta);
        return stack;
    }

    private static String t(Player player, String key, Object... args) {
        return Lang.text(player, key, args);
    }

    private static String c(String color, String text) {
        return color + "|" + text;
    }

    private static String shown(String line) {
        int bar = line.indexOf('|');
        if (bar > 0 && NamedTextColor.NAMES.value(line.substring(0, bar)) != null) return line.substring(bar + 1);
        return line;
    }

    private static NamedTextColor colorOf(String line) {
        int bar = line.indexOf('|');
        if (bar > 0) {
            NamedTextColor named = NamedTextColor.NAMES.value(line.substring(0, bar));
            if (named != null) return named;
        }
        if (coords(line)) return NamedTextColor.DARK_GRAY;
        return NamedTextColor.GRAY;
    }

    private static final int[] heatSlots = {43, 34, 25, 16};

    private static void heatColumn(Inventory inv, Player player, int temp, boolean throttle) {
        inv.setItem(16, heatLamp(player, temp, throttle));
        inv.setItem(25, heatMark(temp, throttle, 80));
        inv.setItem(34, heatMark(temp, throttle, 60));
        inv.setItem(43, heatMark(temp, throttle, 40));
    }

    private static ItemStack heatLamp(Player player, int temp, boolean throttle, String... lore) {
        return button(heatItem(temp, throttle), tempLine(player, temp, throttle), heatColor(temp, throttle), null, throttle, lore);
    }

    private static ItemStack heatMark(int temp, boolean throttle, int mark) {
        if (temp < mark) return button(Material.GRAY_STAINED_GLASS_PANE, String.valueOf(mark), NamedTextColor.DARK_GRAY, null, false);
        return button(heatPane(temp, throttle), String.valueOf(mark), heatColor(temp, throttle), null, false);
    }

    private static Material heatItem(int temp, boolean throttle) {
        if (throttle || temp >= 80) return Material.BLAZE_POWDER;
        if (temp >= 60) return Material.MAGMA_CREAM;
        if (temp >= 40) return Material.GLOWSTONE_DUST;
        return Material.SNOWBALL;
    }

    private static Material heatPane(int temp, boolean throttle) {
        if (throttle || temp >= 80) return Material.RED_STAINED_GLASS_PANE;
        if (temp >= 60) return Material.ORANGE_STAINED_GLASS_PANE;
        if (temp >= 40) return Material.LIME_STAINED_GLASS_PANE;
        return Material.LIGHT_BLUE_STAINED_GLASS_PANE;
    }

    private static NamedTextColor heatColor(int temp, boolean throttle) {
        if (throttle || temp >= 80) return NamedTextColor.RED;
        if (temp >= 60) return NamedTextColor.GOLD;
        if (temp >= 40) return NamedTextColor.GREEN;
        return NamedTextColor.AQUA;
    }

    private static void dustButton(Inventory inv, Player player, MinerNodes.Node node) {
        int hash = MinerNodes.hashOf(node.dust);
        NamedTextColor color = node.dust >= 80 ? NamedTextColor.RED : node.dust >= 40 ? NamedTextColor.GOLD : NamedTextColor.GREEN;
        inv.setItem(13, button(Material.BRUSH, MinerNodes.dustText(player, node.dust), color, "dust", node.dust > 0,
                c("gold", t(player, "menu.dust-pct", node.dust)),
                c("gold", t(player, "menu.hash-pct", hash)),
                c(node.type == NodeType.RACK && node.door == 1 ? "red" : "gray",
                        t(player, node.type == NodeType.RACK && node.door == 1 ? "menu.dust.fast" : "menu.dust.brush")),
                c(node.dust > 0 ? "green" : "gray", t(player, node.dust > 0 ? "menu.dust.sweep" : "msg.already-clean"))));
    }

    private static void dust(Player player, MinerNodes nodes, String id) {
        MinerNodes.Node node = nodes.byId(id);
        if (node == null || !nodes.sweep(player, node)) return;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MinerMenu) {
            paint(player.getOpenInventory().getTopInventory(), player, nodes, node);
        }
    }

    private static void giveBrush(Player player) {
        if (!player.hasPermission("miner.give")) {
            MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.no-permission");
            return;
        }
        player.getInventory().addItem(MinerItems.brush(player));
        MinerSettings.play(player, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
    }

    private static void giveLamp(Player player) {
        if (!player.hasPermission("miner.give")) {
            MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.no-permission");
            return;
        }
        player.getInventory().addItem(MinerItems.lamp(player));
        MinerSettings.play(player, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
    }

    private static void unlamp(Player player, MinerNodes nodes, String id) {
        MinerNodes.Node node = nodes.byId(id);
        if (node == null || !nodes.takeLamp(player, node)) return;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MinerMenu) {
            paint(player.getOpenInventory().getTopInventory(), player, nodes, node);
        }
    }

    private static void doorButton(Inventory inv, Player player, MinerNodes.Node node) {
        boolean shut = node.door == 1;
        inv.setItem(31, button(Material.GLASS_PANE, t(player, shut ? "menu.door.shut" : "menu.door.open"),
                shut ? NamedTextColor.RED : NamedTextColor.GREEN, shut ? "door:open" : "door:shut", shut,
                c(shut ? "red" : "green", t(player, shut ? "menu.door.heat" : "menu.door.air")),
                c(shut ? "red" : "green", t(player, shut ? "menu.door.dust-fast" : "menu.door.dust-slow")),
                c("gray", t(player, shut ? "menu.door.no-blades" : "menu.door.can-blades")),
                c("yellow", t(player, shut ? "menu.door.click-open" : "menu.door.click-shut"))));
    }

    private static void door(Player player, MinerNodes nodes, String id, int mode) {
        MinerNodes.Node node = nodes.byId(id);
        if (node == null || !nodes.setDoor(player, node, mode)) return;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MinerMenu) {
            paint(player.getOpenInventory().getTopInventory(), player, nodes, node);
        }
    }

    private static String airLine(Player player, MinerNodes.Node node) {
        int vents = node.type == NodeType.RACK ? 2 : 1;
        if (node.type == NodeType.RACK && node.door == 1 && node.choke < vents) return c("red", t(player, "menu.air-door-shut"));
        if (node.choke <= 0) return c("green", t(player, node.type == NodeType.RACK && node.door != 1 ? "menu.air-door-open" : "menu.air-open"));
        if (node.choke < vents) return c("gold", t(player, "menu.air-half"));
        return c("red", t(player, "menu.air-blocked"));
    }

    private static String tempLine(Player player, int temp, boolean throttle) {
        String text = t(player, throttle ? "menu.overheat" : "menu.temp", temp);
        if (throttle || temp >= 80) return c("red", text);
        if (temp >= 60) return c("gold", text);
        return c("green", text);
    }

    private static boolean coords(String line) {
        String[] part = line.split(" ");
        if (part.length != 3) return false;
        for (String bit : part) {
            try {
                Integer.parseInt(bit);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private static Component title(String text, NamedTextColor color) {
        return Component.text(text).color(color).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false);
    }

    private enum Page { HOME, ITEMS, LIST, RACKS, MACHINE, CONFIRM, PREFS }
}
