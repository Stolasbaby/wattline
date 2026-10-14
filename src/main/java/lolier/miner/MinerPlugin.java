package lolier.miner;

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
            if (sender instanceof Player player) MinerMenu.home(player, nodes);
            else sender.sendMessage(Component.text("/miner give asic|panel|wire").color(NamedTextColor.GRAY));
            return true;
        }
        if (args[0].equalsIgnoreCase("settings")) {
            if (sender instanceof Player player) MinerMenu.prefs(player);
            return true;
        }
        if (!sender.hasPermission("miner.give")) {
            sender.sendMessage(Component.text("нельзя").color(NamedTextColor.RED));
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text("/miner give asic|panel|wire").color(NamedTextColor.GRAY));
            return true;
        }
        NodeType type = NodeType.byId(args[1].toLowerCase(Locale.ROOT));
        boolean wallet = args[1].equalsIgnoreCase("wallet");
        boolean brush = args[1].equalsIgnoreCase("brush");
        boolean lamp = args[1].equalsIgnoreCase("lamp");
        boolean adminBattery = args[1].equalsIgnoreCase("adminbattery");
        if (type == null && !wallet && !adminBattery && !brush && !lamp) {
            sender.sendMessage(Component.text("asic, panel, wire, rack, wallet, terminal, battery, adminbattery, pole, brush или lamp").color(NamedTextColor.RED));
            return true;
        }
        Player target = sender instanceof Player player ? player : null;
        if (args.length >= 3) target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("игрок не в сети").color(NamedTextColor.RED));
            return true;
        }
        if (wallet) {
            target.getInventory().addItem(MinerItems.wallet(target));
            target.sendMessage(Component.text("получил: Кошелёк " + target.getName()).color(NamedTextColor.GOLD));
            return true;
        }
        if (brush) {
            target.getInventory().addItem(MinerItems.brush());
            target.sendMessage(Component.text("получил: Кисть").color(NamedTextColor.GOLD));
            return true;
        }
        if (lamp) {
            target.getInventory().addItem(MinerItems.lamp());
            target.sendMessage(Component.text("получил: Лампочка").color(NamedTextColor.YELLOW));
            return true;
        }
        if (adminBattery || type == NodeType.BATTERY) {
            int tier = adminBattery ? MinerNodes.BATTERY_ADMIN : 1;
            target.getInventory().addItem(MinerItems.battery(tier));
            target.sendMessage(Component.text("получил: " + (adminBattery ? "Админский аккумулятор" : "Аккумулятор")).color(adminBattery ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GOLD));
            return true;
        }
        target.getInventory().addItem(MinerItems.item(type));
        target.sendMessage(Component.text("получил: " + type.title).color(type.color));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("menu");
            out.add("settings");
            if (sender.hasPermission("miner.give")) out.add("give");
        }
        if (args.length == 2) {
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
            out.add("adminbattery");
        }
        return out;
    }
}
