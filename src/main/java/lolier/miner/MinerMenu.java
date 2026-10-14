package lolier.miner;

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

/** Меню фермы, карточка машины и подтверждение слома. */
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
        Inventory inv = Bukkit.createInventory(menu, 54, title("Майнинг", NamedTextColor.GOLD));
        menu.inventory = inv;
        frame(inv, Material.BLACK_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE);
        inv.setItem(4, button(Material.NETHER_STAR, "Ферма", NamedTextColor.GOLD, null, true,
                "панелей " + nodes.count(player, NodeType.PANEL),
                "асиков " + nodes.count(player, NodeType.ASIC),
                "стоек " + nodes.count(player, NodeType.RACK),
                "проводов " + nodes.count(player, NodeType.WIRE),
                "батарей " + nodes.count(player, NodeType.BATTERY),
                "столбов " + nodes.count(player, NodeType.POLE)));
        boolean give = player.hasPermission("miner.give");
        boolean day = day(player);
        inv.setItem(20, button(Material.CHEST, "Мои машины", NamedTextColor.GREEN, "list", false, "список твоих устройств"));
        inv.setItem(22, button(Material.BARREL, "Предметы", NamedTextColor.GOLD, "items", true,
                give ? "выдать асик, панель, стойку" : "что можно поставить",
                give ? "аккумулятор и обменник" : "выдаёт оператор"));
        inv.setItem(24, button(Material.COMPARATOR, "Настройки", NamedTextColor.WHITE, "prefs", false,
                "звук и анимации только у тебя"));
        inv.setItem(29, button(day ? Material.SUNFLOWER : Material.CLOCK, day ? "День" : "Ночь",
                day ? NamedTextColor.YELLOW : NamedTextColor.BLUE, null, false, day ? "панели работают" : "панели молчат, батареи отдают"));
        inv.setItem(31, button(Material.GOLD_NUGGET, "Жетоны", NamedTextColor.GOLD, null, false,
                "только твои жетоны: " + nodes.coinsOf(player),
                purseLine(player)));
        inv.setItem(33, button(Material.BOOK, "Как тянуть", NamedTextColor.WHITE, null, false,
                "1. поставь панель и асик",
                "2. ПКМ проводом по первому",
                "3. ПКМ по второму",
                "диагональ, вверх и вбок — можно",
                "чем длиннее, тем больше слитков"));
        inv.setItem(40, button(Material.LODESTONE, "Стойки", NamedTextColor.WHITE, "racks", false,
                "забрать жетоны со всех",
                "очистить пыль кистью"));
        inv.setItem(49, button(Material.BARRIER, "Закрыть", NamedTextColor.RED, "close", false));
        player.openInventory(inv);
        MinerSettings.play(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.3f);
    }

    static void items(Player player, MinerNodes nodes) {
        MinerMenu menu = new MinerMenu(Page.ITEMS, null);
        Inventory inv = Bukkit.createInventory(menu, 54, title("Предметы", NamedTextColor.GOLD));
        menu.inventory = inv;
        frame(inv, Material.BLACK_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE);
        boolean give = player.hasPermission("miner.give");
        String take = give ? "нажми, чтобы взять" : "выдаёт оператор";
        inv.setItem(4, button(Material.BARREL, "Каталог", NamedTextColor.GOLD, null, true,
                give ? "нажми на предмет, он попадёт в инвентарь" : "ставить может каждый, выдаёт оператор"));
        inv.setItem(11, button(Material.LANTERN, "Лампочка", NamedTextColor.YELLOW, give ? "take:lamp" : null, true,
                "вешается на электростолб, до двух",
                "для красоты, ток не ест", take));
        inv.setItem(13, button(Material.BRUSH, "Кисть", NamedTextColor.GOLD, give ? "take:brush" : null, true,
                "смахивает пыль с асика и стойки",
                "без неё хеш падает",
                "закрытая дверь пылится быстрее", take));
        inv.setItem(15, button(Material.LIGHTNING_ROD, "Электростолб", NamedTextColor.GRAY, give ? "take:pole" : null, true,
                "провод через него дальше 16 блоков",
                "до машины 32, между столбами 48",
                "клик по столбу не обрывает тягу", take));
        inv.setItem(19, button(Material.OBSERVER, "Асик", NamedTextColor.AQUA, give ? "take:asic" : null, true,
                "ест 1 ток и копит жетоны", take));
        inv.setItem(21, button(Material.DAYLIGHT_DETECTOR, "Солнечная панель", NamedTextColor.YELLOW, give ? "take:panel" : null, true,
                "днём даёт ток, в меню усиливается", take));
        inv.setItem(23, button(Material.COPPER_INGOT, "Провод", NamedTextColor.GOLD, give ? "take:wire" : null, true,
                "ПКМ по устройству и тяни",
                "до 16 блоков, через столб дальше", take));
        inv.setItem(25, button(Material.LODESTONE, "Серверная стойка", NamedTextColor.WHITE, give ? "take:rack" : null, true,
                "на два блока, четыре лезвия",
                "ПКМ асиком — вставить", take));
        inv.setItem(29, button(Material.VAULT, "Обменник", NamedTextColor.GREEN, give ? "take:terminal" : null, true,
                "банкомат на два блока, ест 1 ток",
                "ночью живёт от батареи", take));
        inv.setItem(31, button(Material.COPPER_BULB, "Аккумулятор", NamedTextColor.GOLD, give ? "take:battery" : null, true,
                "базовый, ток 1, запас на 10 минут",
                "в меню усиливается до тока 8", take));
        inv.setItem(33, button(Material.BUNDLE, "Кошелёк", NamedTextColor.GOLD, give ? "take:wallet" : null, false,
                "именной, на тебя",
                "сюда складываются жетоны", take));
        if (give) {
            inv.setItem(40, button(Material.RESPAWN_ANCHOR, "Админский аккумулятор", NamedTextColor.LIGHT_PURPLE, "take:adminbattery", true,
                    "ток 64 и днём, и ночью",
                    "не садится и не усиливается",
                    "нажми, чтобы взять"));
        }
        inv.setItem(49, button(Material.ARROW, "Назад", NamedTextColor.WHITE, "home", false));
        player.openInventory(inv);
    }

    static void prefs(Player player) {
        MinerSettings settings = MinerSettings.get();
        boolean sound = settings == null || settings.sound(player);
        boolean anim = settings == null || settings.anim(player);
        MinerMenu menu = new MinerMenu(Page.PREFS, null);
        Inventory inv = Bukkit.createInventory(menu, 54, title("Настройки", NamedTextColor.WHITE));
        menu.inventory = inv;
        frame(inv, Material.BLACK_STAINED_GLASS_PANE, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        inv.setItem(20, button(sound ? Material.NOTE_BLOCK : Material.BARRIER, sound ? "Звук включён" : "Звук выключен",
                sound ? NamedTextColor.GREEN : NamedTextColor.RED, "pref:sound", sound,
                "только у тебя", "вентиляторы, клики, провода"));
        inv.setItem(24, button(anim ? Material.CLOCK : Material.BARRIER, anim ? "Анимации включены" : "Анимации выключены",
                anim ? NamedTextColor.GREEN : NamedTextColor.RED, "pref:anim", anim,
                "только у тебя", "вращение, искра на проводе, блик стекла"));
        inv.setItem(49, button(Material.ARROW, "Назад", NamedTextColor.WHITE, "home", false));
        player.openInventory(inv);
    }

    static void list(Player player, MinerNodes nodes) {
        list(player, nodes, null);
    }

    static void list(Player player, MinerNodes nodes, NodeType filter) {
        MinerMenu menu = new MinerMenu(Page.LIST, null);
        Inventory inv = Bukkit.createInventory(menu, 54, title("Мои машины", NamedTextColor.GREEN));
        menu.inventory = inv;
        frame(inv, Material.BLACK_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE);
        int slot = 10;
        boolean any = false;
        for (MinerNodes.Node node : nodes.owned(player)) {
            if (filter != null && node.type != filter) continue;
            any = true;
            if (slot == 17 || slot == 26 || slot == 35) slot++;
            if (slot >= 44) break;
            inv.setItem(slot++, machineIcon(nodes, node, "node:" + node.id));
        }
        if (!any) {
            inv.setItem(22, button(Material.BARRIER, filter == null ? "Пока пусто" : "Нет таких", NamedTextColor.GRAY, null, false,
                    filter == null ? "поставь панель или асик" : "в этом фильтре пусто"));
        }
        inv.setItem(45, button(Material.NETHER_STAR, "Все", NamedTextColor.WHITE, "filter:all", filter == null));
        inv.setItem(46, button(Material.OBSERVER, "Асики", NamedTextColor.AQUA, "filter:asic", filter == NodeType.ASIC));
        inv.setItem(47, button(Material.DAYLIGHT_DETECTOR, "Панели", NamedTextColor.YELLOW, "filter:panel", filter == NodeType.PANEL));
        inv.setItem(48, button(Material.LODESTONE, "Стойки", NamedTextColor.WHITE, "filter:rack", filter == NodeType.RACK));
        inv.setItem(50, button(Material.COPPER_INGOT, "Провода", NamedTextColor.GOLD, "filter:wire", filter == NodeType.WIRE));
        inv.setItem(51, button(Material.VAULT, "Обмен", NamedTextColor.GREEN, "filter:terminal", filter == NodeType.TERMINAL));
        inv.setItem(52, button(Material.COPPER_BULB, "Батареи", NamedTextColor.GOLD, "filter:battery", filter == NodeType.BATTERY));
        inv.setItem(49, button(Material.ARROW, "Назад", NamedTextColor.WHITE, "home", false));
        player.openInventory(inv);
    }

    static void racks(Player player, MinerNodes nodes) {
        MinerMenu menu = new MinerMenu(Page.RACKS, null);
        Inventory inv = Bukkit.createInventory(menu, 54, title("Стойки", NamedTextColor.WHITE));
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
            inv.setItem(slot++, machineIcon(nodes, node, "node:" + node.id));
        }
        if (count == 0) {
            inv.setItem(22, button(Material.BARRIER, "Стоек нет", NamedTextColor.GRAY, null, false, "поставь серверную стойку"));
        }
        inv.setItem(4, button(Material.LODESTONE, "Стойки " + count, NamedTextColor.WHITE, null, count > 0,
                "жетонов " + coins,
                dusty == 0 ? "пыли нет" : "пыльных " + dusty,
                "нажми стойку, чтобы открыть"));
        inv.setItem(47, button(Material.GOLD_NUGGET, "Забрать со всех", NamedTextColor.GOLD, "racks:take", coins > 0,
                "жетонов " + coins,
                "в кошелёк"));
        inv.setItem(51, button(Material.BRUSH, "Очистить все", dusty > 0 ? NamedTextColor.GOLD : NamedTextColor.GREEN, "racks:clean", dusty > 0,
                dusty == 0 ? "чисто" : "пыльных " + dusty,
                "нужна кисть в инвентаре"));
        inv.setItem(49, button(Material.ARROW, "Назад", NamedTextColor.WHITE, "home", false));
    }

    static void machine(Player player, MinerNodes nodes, MinerNodes.Node node) {
        MinerMenu menu = new MinerMenu(Page.MACHINE, node.id);
        Inventory inv = Bukkit.createInventory(menu, 54, title(node.type.title, node.type.color));
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
        paint(inv, nodes, node);
        inv.setItem(45, button(Material.ARROW, "Назад", NamedTextColor.WHITE, "home", false));
        inv.setItem(49, button(Material.BARRIER, "Закрыть", NamedTextColor.RED, "close", false));
        player.openInventory(inv);
        MinerSettings.play(player, Sound.BLOCK_BARREL_OPEN, 0.5f, 1.4f);
    }

    static void confirm(Player player, MinerNodes nodes, MinerNodes.Node node) {
        MinerMenu menu = new MinerMenu(Page.CONFIRM, node.id);
        Inventory inv = Bukkit.createInventory(menu, 27, title("Снять?", NamedTextColor.RED));
        menu.inventory = inv;
        frame(inv, Material.BLACK_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE);
        String warn = node.type != NodeType.BATTERY ? "предмет назад, жетоны в кошелёк"
                : MinerNodes.adminTier(node.tier) ? "предмет назад" : "предмет назад, заряд останется";
        inv.setItem(11, button(Material.LIME_CONCRETE, "Снять", NamedTextColor.GREEN, "yes", false, warn));
        inv.setItem(13, machineIcon(nodes, node, null));
        inv.setItem(15, button(Material.RED_CONCRETE, "Назад", NamedTextColor.RED, "back", false));
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
                menu.inventory.setItem(29, button(day ? Material.SUNFLOWER : Material.CLOCK, day ? "День" : "Ночь",
                        day ? NamedTextColor.YELLOW : NamedTextColor.BLUE, null, false, day ? "панели работают" : "панели молчат, батареи отдают"));
                menu.inventory.setItem(31, button(Material.GOLD_NUGGET, "Жетоны", NamedTextColor.GOLD, null, false,
                        "только твои жетоны: " + menu.seenBuffer, purseLine(purse)));
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
            paint(menu.inventory, nodes, node);
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
                if (!player.hasPermission("miner.give")) return;
                player.getInventory().addItem(MinerItems.wallet(player));
                MinerSettings.play(player, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
            }
            case "pull" -> pull(player, nodes, menu.nodeId);
            case "collect" -> collect(player, nodes, menu.nodeId);
            case "upgrade" -> upgrade(player, nodes, menu.nodeId);
            case "confirm" -> {
                MinerNodes.Node node = nodes.byId(menu.nodeId);
                if (node != null && nodes.owner(player, node)) confirm(player, nodes, node);
                else MinerNodes.tell(player, "это не твоё");
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
                            MinerNodes.tell(player, "нет тока");
                            paint(player.getOpenInventory().getTopInventory(), nodes, desk);
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
                        paint(player.getOpenInventory().getTopInventory(), nodes, desk);
                    }
                    return;
                }
                if (act.startsWith("bay:")) {
                    MinerNodes.Node rack = nodes.byId(menu.nodeId);
                    if (rack != null) {
                        nodes.collectBay(player, rack, Integer.parseInt(act.substring(4)));
                        paint(player.getOpenInventory().getTopInventory(), nodes, rack);
                        MinerSettings.play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
                    }
                    return;
                }
                if (act.startsWith("out:")) {
                    MinerNodes.Node rack = nodes.byId(menu.nodeId);
                    if (rack != null) {
                        nodes.eject(player, rack, Integer.parseInt(act.substring(4)));
                        paint(player.getOpenInventory().getTopInventory(), nodes, rack);
                    }
                    return;
                }
                if (act.startsWith("node:")) {
                    MinerNodes.Node node = nodes.byId(act.substring(5));
                    if (node != null && nodes.owns(player, node)) machine(player, nodes, node);
                }
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static void paint(Inventory inv, MinerNodes nodes, MinerNodes.Node node) {
        inv.setItem(4, machineIcon(nodes, node, null));
        if (node.type == NodeType.ASIC) {
            inv.setItem(20, button(node.powered ? Material.LIME_DYE : Material.RED_DYE,
                    node.powered ? "Питание есть" : "Нет тока",
                    node.powered ? NamedTextColor.GREEN : NamedTextColor.RED, null, node.powered,
                    "владелец " + nodes.ownerName(node),
                    airLine(node),
                    "пыль " + node.dust + "%",
                    "хеш " + MinerNodes.hashOf(node.dust) + "%",
                    "жетонов " + node.buffer, "до следующего " + MinerNodes.eta(node.progress, node.dust) + " с"));
            heatColumn(inv, node.temp, node.throttle);
            dustButton(inv, node);
            inv.setItem(22, button(Material.GOLD_NUGGET, "Забрать жетоны", NamedTextColor.GOLD, "collect", node.buffer > 0,
                    "сейчас " + node.buffer));
            int cells = 5;
            int filled = node.powered ? Math.min(cells, 1 + node.progress * cells / MinerNodes.COIN_SECONDS) : 0;
            for (int i = 0; i < cells; i++) {
                inv.setItem(29 + i, pane(i < filled ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE));
            }
        } else if (node.type == NodeType.RACK) {
            inv.setItem(20, button(node.bays == 0 ? Material.GRAY_DYE : node.powered ? Material.LIME_DYE : Material.RED_DYE,
                    "Лезвий " + node.bays + " из " + MinerNodes.Node.RACK_SLOTS,
                    NamedTextColor.WHITE, null, node.powered,
                    "владелец " + nodes.ownerName(node),
                    airLine(node),
                    "пыль " + node.dust + "%",
                    "хеш " + MinerNodes.hashOf(node.dust) + "%",
                    "жетонов " + nodes.rackCoins(node),
                    "каждое лезвие ест 1 ток",
                    "очередь от ближайшей панели"));
            doorButton(inv, node);
            dustButton(inv, node);
            int rackCoins = nodes.rackCoins(node);
            inv.setItem(22, button(Material.GOLD_NUGGET, "Забрать все", NamedTextColor.GOLD, "collect", rackCoins > 0,
                    "жетонов " + rackCoins, "только твои лезвия в этой стойке"));
            int[] slots = {37, 28, 19, 10};
            for (int bay = 0; bay < MinerNodes.Node.RACK_SLOTS; bay++) {
                if (bay >= node.bays) {
                    inv.setItem(slots[bay], button(Material.GRAY_STAINED_GLASS_PANE, "Пусто " + (bay + 1), NamedTextColor.DARK_GRAY, null, false,
                            "ПКМ асиком по стойке"));
                    inv.setItem(heatSlots[bay], button(Material.GRAY_STAINED_GLASS_PANE, "Нет лезвия", NamedTextColor.DARK_GRAY, null, false));
                    continue;
                }
                boolean on = node.bayOn[bay];
                inv.setItem(slots[bay], button(Material.OBSERVER, "Лезвие " + (bay + 1), NamedTextColor.AQUA, "bay:" + bay, on,
                        on ? "питание есть" : "нет тока",
                        "жетонов " + node.bayBuffer[bay],
                        "до следующего " + MinerNodes.eta(node.bayProgress[bay], node.dust) + " с",
                        "нажми, чтобы забрать"));
                inv.setItem(slots[bay] + 2, button(Material.HOPPER, "Вынуть", NamedTextColor.RED, "out:" + bay, false, "вернёт асик и жетоны"));
                inv.setItem(heatSlots[bay], heatLamp(node.bayTemp[bay], node.bayThrottle[bay], "лезвие " + (bay + 1)));
            }
        } else if (node.type == NodeType.PANEL) {
            boolean on = node.powered;
            int tier = Math.max(1, Math.min(MinerNodes.PANEL_TIERS, node.tier));
            inv.setItem(20, button(on ? Material.SUNFLOWER : Material.CLOCK, on ? "Светит" : "Молчит",
                    on ? NamedTextColor.YELLOW : NamedTextColor.GRAY, null, on,
                    "владелец " + nodes.ownerName(node),
                    MinerNodes.tierName(tier), "ток " + MinerNodes.output(tier)));
            inv.setItem(22, button(Material.REPEATER, "Уровень " + tier, NamedTextColor.WHITE, null, tier > 1,
                    MinerNodes.tierName(tier), "мощность " + MinerNodes.output(tier)));
            if (tier < MinerNodes.PANEL_TIERS) {
                int next = tier + 1;
                inv.setItem(31, button(Material.ANVIL, "Усилить", NamedTextColor.GREEN, "upgrade", true,
                        "станет " + MinerNodes.tierName(next),
                        "ток " + MinerNodes.output(next),
                        "цена " + MinerNodes.upgradeCost(tier) + " жетонов"));
            } else {
                inv.setItem(31, button(Material.NETHER_STAR, "Максимум", NamedTextColor.GOLD, null, true, "ток 8"));
            }
        } else if (node.type == NodeType.BATTERY) {
            int filled = MinerNodes.bars(node.charge, node.tier);
            Material fill = MinerNodes.barMaterial(node.charge, node.tier);
            boolean admin = MinerNodes.adminTier(node.tier);
            int rate = MinerNodes.amp(node.tier);
            inv.setItem(13, button(admin ? Material.RESPAWN_ANCHOR : Material.COPPER_BULB, MinerNodes.flowText(node.progress, node.charge),
                    MinerNodes.flowColor(node.progress, node.charge), null, node.powered,
                    MinerNodes.batteryName(node.tier),
                    MinerNodes.chargeText(node.charge, node.tier),
                    MinerNodes.runtimeText(node.charge, node.tier),
                    admin ? "даёт ток и днём, и ночью" : "днём берёт " + rate + " лишний ток",
                    admin ? "не садится" : "ночью отдаёт " + rate + " ток",
                    "сначала обменник, потом стойки",
                    admin ? "только для оператора" : "снял — заряд останется на предмете"));
            for (int i = 0; i < 4; i++) {
                boolean lit = i < filled;
                inv.setItem(19 + i, button(lit ? fill : Material.GRAY_STAINED_GLASS_PANE,
                        lit ? "ячейка " + (i + 1) : "пусто " + (i + 1),
                        lit ? MinerNodes.flowColor(node.progress, node.charge) : NamedTextColor.DARK_GRAY, null, lit));
            }
            if (admin) {
                inv.setItem(31, button(Material.NETHER_STAR, "Админский", NamedTextColor.LIGHT_PURPLE, null, true, "ток " + rate, "не усиливается"));
            } else if (node.tier < MinerNodes.BATTERY_TIERS) {
                int next = node.tier + 1;
                inv.setItem(31, button(Material.ANVIL, "Усилить", NamedTextColor.GREEN, "upgrade", true,
                        "станет " + MinerNodes.batteryName(next),
                        "ток " + MinerNodes.amp(next),
                        "запас на 10 минут",
                        "цена " + MinerNodes.upgradeCost(node.tier) + " жетонов"));
            } else {
                inv.setItem(31, button(Material.NETHER_STAR, "Максимум", NamedTextColor.GOLD, null, true, "ток " + rate));
            }
        } else if (node.type == NodeType.TERMINAL) {
            MinerMarket market = MinerMarket.get();
            boolean up = market != null && market.rising();
            boolean live = market != null && market.online();
            inv.setItem(13, button(Material.RECOVERY_COMPASS, market == null ? "Курс" : "Курс " + market.boardLine(),
                    node.powered ? (up ? NamedTextColor.GREEN : NamedTextColor.RED) : NamedTextColor.GRAY, null, node.powered,
                    node.powered ? "питание есть" : "нет тока",
                    live ? market.cardLoreSell() : "валюта не подключена",
                    live ? market.cardLoreBuy() : "нужен Vault",
                    market == null ? "курс появится с сервером" : market.cardLoreWait(),
                    "ест 1 ток, ночью от батареи"));
            int[] packs = {1, 8, 16, 64};
            for (int i = 0; i < packs.length; i++) {
                int count = packs[i];
                inv.setItem(19 + i, button(Material.GOLD_NUGGET, "Продать " + count, NamedTextColor.GREEN, "sell:" + count, false,
                        market == null ? "нет курса" : "получишь " + market.gain(count)));
                inv.setItem(28 + i, button(Material.EMERALD, "Купить " + count, NamedTextColor.GOLD, "buy:" + count, false,
                        market == null ? "нет курса" : "отдашь " + market.cost(count)));
            }
            inv.setItem(23, button(Material.CHEST, "Продать всё", NamedTextColor.GREEN, "sell:all", true, "весь кошелёк"));
            inv.setItem(32, button(Material.CHEST, "Купить всё", NamedTextColor.GOLD, "buy:all", true, "сколько хватит денег"));
        } else if (node.type == NodeType.POLE) {
            inv.setItem(13, button(Material.LANTERN, "Лампочки " + node.bulbs + " из 2", NamedTextColor.YELLOW, null, node.bulbs > 0,
                    "владелец " + nodes.ownerName(node),
                    "для красоты, ток не едят",
                    "ПКМ лампочкой по столбу",
                    "до машины 32 блока",
                    "между столбами 48"));
            if (node.bulbs > 0) {
                inv.setItem(31, button(Material.LANTERN, "Снять лампочку", NamedTextColor.GOLD, "unlamp", false, "вернёт одну"));
            }
        } else {
            inv.setItem(20, button(Material.COPPER_BLOCK, "Узел", NamedTextColor.GOLD, null, false,
                    "связей " + nodes.degree(node)));
            inv.setItem(22, button(Material.STRING, "Тянется", NamedTextColor.WHITE, null, false,
                    "диагональ, вверх и несколько блоков"));
        }
        String reach = node.type == NodeType.POLE ? "от столба 32, до другого столба 48" : "до 16 блоков";
        inv.setItem(24, button(Material.COPPER_INGOT, "Тянуть провод", NamedTextColor.GOLD, "pull", true,
                "потом ткни второй конец", reach));
        inv.setItem(40, button(Material.RED_CONCRETE, "Снять", NamedTextColor.RED, "confirm", false, "вернёт предмет"));
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
            paint(player.getOpenInventory().getTopInventory(), nodes, node);
        }
    }

    private static void collect(Player player, MinerNodes nodes, String id) {
        MinerNodes.Node node = nodes.byId(id);
        if (node == null) return;
        if (node.type == NodeType.RACK) nodes.collectRack(player, node);
        else if (node.type == NodeType.ASIC) nodes.use(player, node);
        else return;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MinerMenu) {
            paint(player.getOpenInventory().getTopInventory(), nodes, node);
        }
        MinerSettings.play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
    }

    private static void destroy(Player player, MinerNodes nodes, String id) {
        MinerNodes.Node node = nodes.byId(id);
        player.closeInventory();
        if (node == null || !nodes.owner(player, node)) {
            MinerNodes.tell(player, "это не твоё");
            return;
        }
        if (!nodes.remove(node, player)) return;
        MinerSettings.play(player, Sound.BLOCK_COPPER_BREAK, 0.8f, 0.9f);
        MinerNodes.tell(player, "снял");
    }

    private static void giveBattery(Player player, int tier) {
        if (!player.hasPermission("miner.give")) return;
        player.getInventory().addItem(MinerItems.battery(tier));
        MinerSettings.play(player, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
    }

    private static void give(Player player, NodeType type) {
        if (!player.hasPermission("miner.give")) return;
        player.getInventory().addItem(MinerItems.item(type));
        MinerSettings.play(player, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
    }

    private static ItemStack machineIcon(MinerNodes nodes, MinerNodes.Node node, String act) {
        String power = node.type == NodeType.WIRE ? "связей " + nodes.degree(node)
                : node.type == NodeType.TERMINAL ? (node.powered ? "питание есть" : "нет тока")
                : node.type == NodeType.POLE ? "опора, связи " + nodes.degree(node)
                : node.type == NodeType.BATTERY ? MinerNodes.flowText(node.progress, node.charge)
                : (node.powered ? "работает" : "молчит");
        List<String> lore = new ArrayList<>();
        lore.add(node.x + " " + node.y + " " + node.z);
        lore.add(power);
        if (node.type == NodeType.ASIC || node.type == NodeType.RACK) nodes.breathe(node);
        if (node.type == NodeType.ASIC) {
            lore.add("владелец " + nodes.ownerName(node));
            lore.add(airLine(node));
            lore.add(tempLine(node.temp, node.throttle));
            lore.add(MinerNodes.dustText(node.dust) + ", хеш " + MinerNodes.hashOf(node.dust) + "%");
            lore.add("жетонов " + node.buffer);
        }
        if (node.type == NodeType.RACK) {
            lore.add("владелец " + nodes.ownerName(node));
            lore.add(airLine(node));
            lore.add("лезвий " + node.bays);
            lore.add(MinerNodes.dustText(node.dust) + ", хеш " + MinerNodes.hashOf(node.dust) + "%");
            lore.add("жетонов " + nodes.rackCoins(node));
        }
        if (node.type == NodeType.PANEL) {
            lore.add("владелец " + nodes.ownerName(node));
            lore.add(MinerNodes.tierName(node.tier) + ", ток " + MinerNodes.output(node.tier));
        }
        if (node.type == NodeType.TERMINAL && MinerMarket.get() != null) {
            lore.add("владелец " + nodes.ownerName(node));
            lore.add(MinerMarket.get().cardLoreSell());
            lore.add(MinerMarket.get().cardLoreBuy());
        }
        if (node.type == NodeType.BATTERY) {
            lore.add("владелец " + nodes.ownerName(node));
            lore.add(MinerNodes.batteryName(node.tier));
            lore.add(MinerNodes.chargeText(node.charge, node.tier));
            lore.add(MinerNodes.runtimeText(node.charge, node.tier));
        }
        if (node.type == NodeType.POLE) {
            lore.add("владелец " + nodes.ownerName(node));
        }
        return button(node.type.material, node.type.title, node.type.color, act, node.powered, lore.toArray(String[]::new));
    }

    private static String purseLine(Player player) {
        return purseLine(MinerItems.balance(player));
    }

    private static String purseLine(int purse) {
        return purse < 0 ? "кошелька нет" : "в кошельке " + purse;
    }

    private static boolean day(Player player) {
        long time = player.getWorld().getTime() % 24000;
        return time < 12000 || time > 23000;
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
        meta.displayName(Component.text(name).color(color).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Component.text(line).color(colorOf(line)).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lines);
        }
        if (glint) meta.setEnchantmentGlintOverride(true);
        if (act != null && action != null) meta.getPersistentDataContainer().set(action, PersistentDataType.STRING, act);
        stack.setItemMeta(meta);
        return stack;
    }

    private static NamedTextColor colorOf(String line) {
        String text = line.toLowerCase(java.util.Locale.ROOT);
        if (coords(line)) return NamedTextColor.DARK_GRAY;
        if (text.startsWith("владелец")) return NamedTextColor.AQUA;
        if (text.startsWith("воздух")) return NamedTextColor.GREEN;
        if (text.startsWith("перед наполовину")) return NamedTextColor.GOLD;
        if (text.startsWith("перед закрыт")) return NamedTextColor.RED;
        if (text.startsWith("дверь закрыта") || text.startsWith("жар")) return NamedTextColor.RED;
        if (text.startsWith("дверь открыта")) return NamedTextColor.GREEN;
        if (text.startsWith("перегрев")) return NamedTextColor.RED;
        if (text.startsWith("температура")) return tempColor(line);
        if (text.startsWith("в кошельке") || text.startsWith("жетон") || text.startsWith("сейчас") || text.startsWith("цена")) return NamedTextColor.GOLD;
        if (text.startsWith("кошелька")) return NamedTextColor.RED;
        if (text.startsWith("лезви")) return NamedTextColor.WHITE;
        if (text.startsWith("ток") || text.startsWith("мощность") || text.startsWith("один ток")) return NamedTextColor.YELLOW;
        if (text.startsWith("питание есть") || text.equals("работает") || text.startsWith("светит")) return NamedTextColor.GREEN;
        if (text.startsWith("нет тока") || text.equals("молчит")) return NamedTextColor.RED;
        if (text.startsWith("пыль") || text.startsWith("хеш") || text.startsWith("кисть")) return NamedTextColor.GOLD;
        if (text.startsWith("до следующего")) return NamedTextColor.DARK_GREEN;
        if (text.startsWith("очередь")) return NamedTextColor.GOLD;
        if (text.startsWith("только")) return NamedTextColor.DARK_AQUA;
        if (text.startsWith("продажа") || text.startsWith("получишь")) return NamedTextColor.GREEN;
        if (text.startsWith("покупка") || text.startsWith("отдашь") || text.startsWith("курс") || text.startsWith("заряд") || text.startsWith("держит") || text.startsWith("копит")) return NamedTextColor.GOLD;
        if (text.startsWith("скачок") || text.startsWith("хватит") || text.startsWith("полная") || text.startsWith("безлимит") || text.startsWith("админ")) return NamedTextColor.AQUA;
        if (text.startsWith("отдаёт") || text.startsWith("ячейка")) return NamedTextColor.GREEN;
        if (text.startsWith("усилен") || text.startsWith("промыш") || text.startsWith("орбит") || text.startsWith("базов")) return NamedTextColor.YELLOW;
        return NamedTextColor.GRAY;
    }

    private static final int[] heatSlots = {43, 34, 25, 16};

    private static void heatColumn(Inventory inv, int temp, boolean throttle) {
        inv.setItem(16, heatLamp(temp, throttle));
        inv.setItem(25, heatMark(temp, throttle, 80));
        inv.setItem(34, heatMark(temp, throttle, 60));
        inv.setItem(43, heatMark(temp, throttle, 40));
    }

    private static ItemStack heatLamp(int temp, boolean throttle, String... lore) {
        return button(heatItem(temp, throttle), tempLine(temp, throttle), heatColor(temp, throttle), null, throttle, lore);
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

    private static void dustButton(Inventory inv, MinerNodes.Node node) {
        int hash = MinerNodes.hashOf(node.dust);
        NamedTextColor color = node.dust >= 80 ? NamedTextColor.RED : node.dust >= 40 ? NamedTextColor.GOLD : NamedTextColor.GREEN;
        inv.setItem(13, button(Material.BRUSH, MinerNodes.dustText(node.dust), color, "dust", node.dust > 0,
                "пыль " + node.dust + "%",
                "хеш " + hash + "%",
                node.type == NodeType.RACK && node.door == 1 ? "дверь закрыта, пылится быстрее" : "кисть в инвентаре",
                node.dust > 0 ? "нажми, чтобы смахнуть" : "чисто"));
    }

    private static void dust(Player player, MinerNodes nodes, String id) {
        MinerNodes.Node node = nodes.byId(id);
        if (node == null || !nodes.sweep(player, node)) return;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MinerMenu) {
            paint(player.getOpenInventory().getTopInventory(), nodes, node);
        }
    }

    private static void giveBrush(Player player) {
        if (!player.hasPermission("miner.give")) return;
        player.getInventory().addItem(MinerItems.brush());
        MinerSettings.play(player, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
    }

    private static void giveLamp(Player player) {
        if (!player.hasPermission("miner.give")) return;
        player.getInventory().addItem(MinerItems.lamp());
        MinerSettings.play(player, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
    }

    private static void unlamp(Player player, MinerNodes nodes, String id) {
        MinerNodes.Node node = nodes.byId(id);
        if (node == null || !nodes.takeLamp(player, node)) return;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MinerMenu) {
            paint(player.getOpenInventory().getTopInventory(), nodes, node);
        }
    }

    private static void doorButton(Inventory inv, MinerNodes.Node node) {
        boolean shut = node.door == 1;
        inv.setItem(31, button(Material.GLASS_PANE, shut ? "Дверь закрыта" : "Дверь открыта",
                shut ? NamedTextColor.RED : NamedTextColor.GREEN, shut ? "door:open" : "door:shut", shut,
                shut ? "жар у критической" : "воздух идёт",
                shut ? "пылится быстрее" : "пылится медленно",
                shut ? "лезвия не вставить" : "можно ставить лезвия",
                shut ? "нажми, чтобы открыть" : "нажми, чтобы закрыть"));
    }

    private static void door(Player player, MinerNodes nodes, String id, int mode) {
        MinerNodes.Node node = nodes.byId(id);
        if (node == null || !nodes.setDoor(player, node, mode)) return;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MinerMenu) {
            paint(player.getOpenInventory().getTopInventory(), nodes, node);
        }
    }

    private static String airLine(MinerNodes.Node node) {
        int vents = node.type == NodeType.RACK ? 2 : 1;
        if (node.type == NodeType.RACK && node.door == 1 && node.choke < vents) return "дверь закрыта";
        if (node.choke <= 0) return node.type == NodeType.RACK && node.door != 1 ? "дверь открыта" : "воздух есть";
        if (node.choke < vents) return "перед наполовину закрыт";
        return "перед закрыт";
    }

    private static String tempLine(int temp, boolean throttle) {
        return throttle ? "перегрев " + temp : "температура " + temp;
    }

    private static NamedTextColor tempColor(String line) {
        int temp = 0;
        int space = line.lastIndexOf(' ');
        if (space >= 0) {
            try {
                temp = Integer.parseInt(line.substring(space + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        if (temp >= 80) return NamedTextColor.RED;
        if (temp >= 60) return NamedTextColor.GOLD;
        return NamedTextColor.GREEN;
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
