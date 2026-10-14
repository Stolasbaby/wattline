package wattline;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MinerPlugin extends JavaPlugin {

    private MinerNodes nodes;
    private MinerMarket market;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Lang.load(this);
        MinerItems.init(new org.bukkit.NamespacedKey(this, "kind"), new org.bukkit.NamespacedKey(this, "coins"));
        MinerMenu.init(new org.bukkit.NamespacedKey(this, "menu"));
        new MinerSettings(this);
        MinerAnimFilter.hook(this);
        MinerVisuals visuals = new MinerVisuals();
        nodes = new MinerNodes(this, visuals);
        visuals.attach(nodes);
        nodes.load();
        market = new MinerMarket(this, nodes, visuals);
        market.load();
        boolean dirty = false;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                if (nodes.restore(chunk)) dirty = true;
            }
        }
        if (nodes.restoreLinks()) dirty = true;
        if (nodes.severBlocked()) dirty = true;
        if (dirty) nodes.save();
        visuals.boards(market);
        getServer().getPluginManager().registerEvents(new MinerListener(nodes), this);
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) MinerItems.dress(player);
        MinerItems.dressWorlds();
        Bukkit.getScheduler().runTaskTimer(this, nodes::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, market::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            int step;

            @Override
            public void run() {
                step++;
                visuals.animate(step);
                if (step % 2 == 0) nodes.preview();
            }
        }, 2L, 2L);
        Bukkit.getScheduler().runTaskTimer(this, () -> MinerMenu.refresh(nodes), 10L, 10L);
    }

    @Override
    public void onDisable() {
        if (market != null) market.save();
        if (nodes != null) nodes.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (sender instanceof Player player) {
                if (!player.hasPermission("miner.use") && !player.hasPermission("miner.admin")) {
                    MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.no-permission");
                    return true;
                }
                MinerMenu.home(player, nodes);
            } else sender.sendMessage(Component.text(Lang.text(sender, "msg.help-give")).color(NamedTextColor.GRAY));
            return true;
        }
        if (args[0].equalsIgnoreCase("settings")) {
            if (sender instanceof Player player) {
                if (!player.hasPermission("miner.use") && !player.hasPermission("miner.language")) {
                    MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.no-permission");
                    return true;
                }
                MinerMenu.prefs(player);
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("language") || args[0].equalsIgnoreCase("lang")) {
            return language(sender, args);
        }
        if (!sender.hasPermission("miner.give") && !sender.hasPermission("miner.admin") && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage(Component.text(Lang.text(sender, "msg.no-permission")).color(NamedTextColor.RED));
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text(Lang.text(sender, "msg.usage")).color(NamedTextColor.GRAY));
            return true;
        }
        NodeType type = NodeType.byId(args[1].toLowerCase(Locale.ROOT));
        boolean wallet = args[1].equalsIgnoreCase("wallet");
        boolean brush = args[1].equalsIgnoreCase("brush");
        boolean lamp = args[1].equalsIgnoreCase("lamp");
        boolean adminBattery = args[1].equalsIgnoreCase("adminbattery");
        if (type == null && !wallet && !adminBattery && !brush && !lamp) {
            sender.sendMessage(Component.text(Lang.text(sender, "msg.bad-item")).color(NamedTextColor.RED));
            return true;
        }
        if (adminBattery && !sender.hasPermission("miner.admin") && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage(Component.text(Lang.text(sender, "msg.no-permission")).color(NamedTextColor.RED));
            return true;
        }
        Player target = sender instanceof Player player ? player : null;
        boolean other = false;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            other = true;
        }
        if (target == null) {
            sender.sendMessage(Component.text(Lang.text(sender, "msg.offline")).color(NamedTextColor.RED));
            return true;
        }
        if (other && sender instanceof Player && !sender.hasPermission("miner.give.others") && !sender.hasPermission("miner.admin")) {
            sender.sendMessage(Component.text(Lang.text(sender, "msg.no-permission")).color(NamedTextColor.RED));
            return true;
        }
        if (wallet) {
            target.getInventory().addItem(MinerItems.wallet(target));
            target.sendMessage(Component.text(Lang.text(target, "msg.received", Lang.text(target, "item.wallet.named", target.getName()))).color(NamedTextColor.GOLD));
            return true;
        }
        if (brush) {
            target.getInventory().addItem(MinerItems.brush(target));
            target.sendMessage(Component.text(Lang.text(target, "msg.received", Lang.text(target, "item.brush.name"))).color(NamedTextColor.GOLD));
            return true;
        }
        if (lamp) {
            target.getInventory().addItem(MinerItems.lamp(target));
            target.sendMessage(Component.text(Lang.text(target, "msg.received", Lang.text(target, "item.lamp.name"))).color(NamedTextColor.YELLOW));
            return true;
        }
        if (adminBattery || type == NodeType.BATTERY) {
            int tier = adminBattery ? MinerNodes.BATTERY_ADMIN : 1;
            target.getInventory().addItem(MinerItems.battery(target, tier));
            String labelName = adminBattery ? Lang.text(target, "item.battery.admin") : NodeType.BATTERY.title(target);
            target.sendMessage(Component.text(Lang.text(target, "msg.received", labelName)).color(adminBattery ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GOLD));
            return true;
        }
        target.getInventory().addItem(MinerItems.item(target, type));
        target.sendMessage(Component.text(Lang.text(target, "msg.received", type.title(target))).color(type.color));
        return true;
    }

    private boolean language(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(Lang.text(sender, "msg.help-lang")).color(NamedTextColor.GRAY));
            return true;
        }
        if (args.length >= 3) {
            if (!player.hasPermission("miner.language.others")) {
                MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.lang-denied");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                player.sendMessage(Component.text(Lang.text(player, "msg.offline")).color(NamedTextColor.RED));
                return true;
            }
            String code = args[2].toLowerCase(Locale.ROOT);
            if (!Lang.known(code)) {
                MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.lang-bad");
                return true;
            }
            MinerSettings settings = MinerSettings.get();
            if (settings != null) settings.setLanguage(target, code);
            MinerItems.dress(target);
            player.sendMessage(Component.text(Lang.text(player, "msg.lang-set", Lang.endonym(code))).color(NamedTextColor.GREEN));
            return true;
        }
        if (!player.hasPermission("miner.language")) {
            MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.lang-denied");
            return true;
        }
        if (args.length < 2) {
            MinerSettings settings = MinerSettings.get();
            String current = settings == null ? "auto" : settings.language(player);
            player.sendMessage(Component.text(Lang.text(player, "msg.lang-set", Lang.endonym(current))).color(NamedTextColor.YELLOW));
            return true;
        }
        String code = args[1].toLowerCase(Locale.ROOT);
        if (!Lang.known(code)) {
            MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.lang-bad");
            return true;
        }
        MinerSettings settings = MinerSettings.get();
        if (settings != null) settings.setLanguage(player, code);
        MinerItems.dress(player);
        player.sendMessage(Component.text(Lang.text(player, "msg.lang-set", Lang.endonym(code))).color(NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("menu");
            out.add("settings");
            out.add("language");
            if (sender.hasPermission("miner.give") || sender.hasPermission("miner.admin")) out.add("give");
            return partial(out, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("language") || args[0].equalsIgnoreCase("lang"))) {
            out.addAll(Lang.ORDER);
            return partial(out, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            out.add("asic");
            out.add("panel");
            out.add("wire");
            out.add("pole");
            out.add("rack");
            out.add("wallet");
            out.add("brush");
            out.add("lamp");
            out.add("terminal");
            out.add("battery");
            if (sender.hasPermission("miner.admin")) out.add("adminbattery");
            return partial(out, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (Player player : Bukkit.getOnlinePlayers()) out.add(player.getName());
            return partial(out, args[2]);
        }
        return out;
    }

    private static List<String> partial(List<String> options, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        List<String> match = new ArrayList<>();
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) match.add(option);
        return match;
    }
}
