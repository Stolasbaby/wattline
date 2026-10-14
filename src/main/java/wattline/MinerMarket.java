package wattline;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Locale;

/** Общий курс жетона к деньгам Vault. Время от времени скачет. */
final class MinerMarket {

    private final JavaPlugin plugin;
    private final MinerNodes nodes;
    private final MinerVisuals visuals;
    private final File file;
    private Object economy;
    private Method deposit;
    private Method withdraw;
    private Method has;
    private Method balance;
    private Method plural;
    private double rate = 20;
    private double previous = 20;
    private int left = 150;

    private static MinerMarket live;

    MinerMarket(JavaPlugin plugin, MinerNodes nodes, MinerVisuals visuals) {
        this.plugin = plugin;
        this.nodes = nodes;
        this.visuals = visuals;
        this.file = new File(plugin.getDataFolder(), "market.yml");
        live = this;
    }

    static MinerMarket get() {
        return live;
    }

    void load() {
        if (!file.isFile()) {
            left = waitTime();
            save();
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        rate = Math.max(4, yaml.getDouble("rate", 20));
        previous = yaml.getDouble("previous", rate);
        left = Math.max(1, yaml.getInt("left", 150));
    }

    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("rate", rate);
        yaml.set("previous", previous);
        yaml.set("left", left);
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save the exchange rate: " + e.getMessage());
        }
    }

    void tick() {
        hook();
        if (left > 1) {
            left--;
            return;
        }
        jump();
    }

    void sell(Player player, int amount) {
        if (!ready(player)) return;
        int have = nodes.walletCoins(player);
        if (have < 0) return;
        int count = amount < 0 ? have : amount;
        if (count <= 0 || have < count) {
            MinerNodes.tell(player, MinerNodes.Tone.BAD, have <= 0 ? "msg.no-tokens" : "msg.wallet-low");
            return;
        }
        double pay = count * rate;
        if (!nodes.takeWallet(player, count)) return;
        if (!credit(player, pay)) {
            nodes.giveWallet(player, count);
            MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.currency-rejected");
            return;
        }
        MinerNodes.tell(player, MinerNodes.Tone.GOOD, "msg.sold", count, money(pay), currency(player));
    }

    void buy(Player player, int amount) {
        if (!ready(player)) return;
        if (nodes.walletCoins(player) < 0) return;
        double price = buyRate();
        int count = amount;
        if (amount < 0) {
            double cash = cash(player);
            count = price <= 0 ? 0 : (int) Math.floor(cash / price);
        }
        if (count <= 0) {
            MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.no-money");
            return;
        }
        double cost = count * price;
        if (!debit(player, cost)) {
            MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.no-money");
            return;
        }
        if (!nodes.giveWallet(player, count)) {
            credit(player, cost);
            return;
        }
        MinerNodes.tell(player, MinerNodes.Tone.GOOD, "msg.bought", count, money(cost), currency(player));
    }

    String quote() {
        return money(rate) + "/" + money(buyRate()) + "/" + left + "/" + (rising() ? "1" : "0");
    }

    String boardLine() {
        return money(rate);
    }

    boolean rising() {
        return rate >= previous;
    }

    String cardLoreSell(org.bukkit.entity.Player player) {
        return Lang.text(player, "menu.sell-word", money(rate), currency(player));
    }

    String cardLoreBuy(org.bukkit.entity.Player player) {
        return Lang.text(player, "menu.buy-word", money(buyRate()), currency(player));
    }

    String cardLoreWait(org.bukkit.entity.Player player) {
        if (left >= 60) return Lang.text(player, "menu.wait-min", left / 60);
        return Lang.text(player, "menu.wait-sec", left);
    }

    boolean online() {
        hook();
        return economy != null;
    }

    String currency(org.bukkit.entity.Player player) {
        hook();
        if (plural == null || economy == null) return Lang.text(player, "currency.coins");
        try {
            Object name = plural.invoke(economy);
            if (name instanceof String text && !text.isBlank()) return text;
        } catch (ReflectiveOperationException ignored) {
        }
        return Lang.text(player, "currency.coins");
    }

    String gain(org.bukkit.entity.Player player, int count) {
        return money(count * rate) + " " + currency(player);
    }

    String cost(org.bukkit.entity.Player player, int count) {
        return money(count * buyRate()) + " " + currency(player);
    }

    private double buyRate() {
        return Math.round(rate * 1.15 * 10.0) / 10.0;
    }

    private void jump() {
        previous = rate;
        double swing = Math.random() < 0.2 ? 0.62 + Math.random() * 0.9 : 0.84 + Math.random() * 0.36;
        rate = Math.round(Math.max(4, Math.min(250, rate * swing)) * 10.0) / 10.0;
        left = waitTime();
        save();
        visuals.boards(this);
    }

    private static int waitTime() {
        return 90 + (int) (Math.random() * 150);
    }

    private boolean ready(Player player) {
        hook();
        if (economy == null) {
            MinerNodes.tell(player, MinerNodes.Tone.BAD, "msg.currency-off");
            return false;
        }
        return true;
    }

    private void hook() {
        if (economy != null) return;
        try {
            Class<?> type = Class.forName("net.milkbowl.vault.economy.Economy");
            var reg = Bukkit.getServicesManager().getRegistration(type);
            if (reg == null) return;
            economy = reg.getProvider();
            deposit = economy.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class);
            withdraw = economy.getClass().getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            has = economy.getClass().getMethod("has", OfflinePlayer.class, double.class);
            balance = economy.getClass().getMethod("getBalance", OfflinePlayer.class);
            plural = economy.getClass().getMethod("currencyNamePlural");
        } catch (ReflectiveOperationException ignored) {
            economy = null;
        }
    }

    private boolean credit(Player player, double amount) {
        try {
            return success(deposit.invoke(economy, player, amount));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private boolean debit(Player player, double amount) {
        try {
            if (!(boolean) has.invoke(economy, player, amount)) return false;
            return success(withdraw.invoke(economy, player, amount));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private double cash(Player player) {
        try {
            Object value = balance.invoke(economy, player);
            if (value instanceof Number number) return number.doubleValue();
        } catch (ReflectiveOperationException ignored) {
        }
        return 0;
    }

    private static boolean success(Object response) throws ReflectiveOperationException {
        if (response == null) return false;
        return (boolean) response.getClass().getMethod("transactionSuccess").invoke(response);
    }

    static String money(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.05) return String.valueOf((long) Math.rint(rounded));
        return String.format(Locale.US, "%.1f", rounded);
    }

    Component screen() {
        NamedTextColor color = rising() ? NamedTextColor.GREEN : NamedTextColor.RED;
        return Component.text(money(rate), color)
                .append(Component.newline())
                .append(Component.text(Lang.text(null, rising() ? "board.up" : "board.down"), NamedTextColor.GOLD));
    }
}
