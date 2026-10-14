package wattline;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

final class Tune {

    static int coinSeconds = 15;
    static int tokenCap = 64;
    static int asicDraw = 1;
    static int bladeDraw = 1;
    static int[] panel = {1, 2, 4, 8};
    static int skyLight = 12;
    static int nightStart = 12000;
    static int nightEnd = 23000;
    static int[] battery = {1, 2, 4, 8};
    static int adminAmp = 64;
    static int reserveSeconds = 600;
    static int[] upgrades = {16, 48, 128};
    static int wireReach = 16;
    static int poleReach = 32;
    static int poleSpan = 48;
    static int blocksPerIngot = 4;
    static int placeReach = 6;
    static int handsReach = 8;
    static int lamps = 2;
    static int dustOpen = 12;
    static int dustShut = 5;
    static int dustFloor = 25;
    static int brushDivisor = 12;
    static int terminalDraw = 1;
    static int[] packs = {1, 8, 16, 64};
    static double marketStart = 20;
    static double marketMin = 4;
    static double marketMax = 250;
    static double buyMarkup = 1.15;
    static int jumpMin = 90;
    static int jumpExtra = 150;
    static double wildChance = 0.2;
    static double wildLow = 0.62;
    static double wildHigh = 1.52;
    static double calmLow = 0.84;
    static double calmHigh = 1.20;

    private Tune() {}

    static void load(MinerPlugin plugin) {
        plugin.saveDefaultConfig();
        File file = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
        if (!live.contains("panel.output")) {
            String language = live.getString("language", "auto");
            plugin.saveResource("config.yml", true);
            if (language != null && !language.equalsIgnoreCase("auto")) {
                try {
                    String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                    text = text.replaceFirst("(?m)^language:.*$", "language: " + language);
                    Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                }
            }
        } else {
            fill(plugin, file, live);
        }
        plugin.reloadConfig();
        var config = plugin.getConfig();
        coinSeconds = clamp(config.getInt("mining.seconds", 15), 1, 3600);
        tokenCap = clamp(config.getInt("mining.token-cap", 64), 1, 100000);
        asicDraw = clamp(config.getInt("mining.asic-current", 1), 1, 10000);
        bladeDraw = clamp(config.getInt("mining.blade-current", 1), 1, 10000);
        panel = ints(config.getIntegerList("panel.output"), new int[]{1, 2, 4, 8}, 8, false);
        skyLight = clamp(config.getInt("panel.sky-light", 12), 0, 15);
        nightStart = clamp(config.getInt("day.night-start", 12000), 0, 23999);
        nightEnd = clamp(config.getInt("day.night-end", 23000), 0, 23999);
        if (nightEnd < nightStart) nightEnd = nightStart;
        battery = ints(config.getIntegerList("battery.output"), new int[]{1, 2, 4, 8}, 4, false);
        adminAmp = clamp(config.getInt("battery.admin-current", 64), 1, 100000);
        reserveSeconds = clamp(config.getInt("battery.reserve-seconds", 600), 1, 86400);
        upgrades = ints(config.getIntegerList("upgrade.cost"), new int[]{16, 48, 128}, 16, true);
        wireReach = clamp(config.getInt("wire.reach", 16), 1, 256);
        poleReach = clamp(config.getInt("wire.pole-reach", 32), 1, 256);
        poleSpan = clamp(config.getInt("wire.pole-span", 48), 1, 256);
        blocksPerIngot = clamp(config.getInt("wire.blocks-per-ingot", 4), 1, 256);
        placeReach = clamp(config.getInt("wire.place-reach", 6), 1, 64);
        handsReach = clamp(config.getInt("wire.hands-reach", 8), 1, 64);
        lamps = clamp(config.getInt("pole.lamps", 2), 1, 2);
        dustOpen = clamp(config.getInt("dust.open-seconds", 12), 1, 3600);
        dustShut = clamp(config.getInt("dust.shut-seconds", 5), 1, 3600);
        dustFloor = clamp(config.getInt("dust.full-speed", 25), 0, 100);
        brushDivisor = clamp(config.getInt("dust.brush-divisor", 12), 1, 1000);
        terminalDraw = clamp(config.getInt("exchange.terminal-current", 1), 1, 10000);
        packs = ints(config.getIntegerList("exchange.packs"), new int[]{1, 8, 16, 64}, 8, false);
        marketStart = Math.max(0.1, config.getDouble("exchange.start-rate", 20));
        marketMin = Math.max(0.1, config.getDouble("exchange.min-rate", 4));
        marketMax = Math.max(marketMin, config.getDouble("exchange.max-rate", 250));
        buyMarkup = Math.max(1.0, config.getDouble("exchange.buy-markup", 1.15));
        jumpMin = clamp(config.getInt("exchange.jump-min-seconds", 90), 5, 86400);
        jumpExtra = clamp(config.getInt("exchange.jump-extra-seconds", 150), 0, 86400);
        wildChance = Math.max(0, Math.min(1, config.getDouble("exchange.wild-chance", 0.2)));
        wildLow = config.getDouble("exchange.wild-low", 0.62);
        wildHigh = config.getDouble("exchange.wild-high", 1.52);
        calmLow = config.getDouble("exchange.calm-low", 0.84);
        calmHigh = config.getDouble("exchange.calm-high", 1.20);
        if (wildHigh < wildLow) wildHigh = wildLow;
        if (calmHigh < calmLow) calmHigh = calmLow;
        MinerNodes.COIN_SECONDS = coinSeconds;
        MinerNodes.PANEL_TIERS = panel.length;
        MinerNodes.CHARGE_MAX = reserveSeconds;
        MinerNodes.BATTERY_TIERS = battery.length;
        MinerNodes.ADMIN_AMP = adminAmp;
        MinerNodes.REACH = wireReach;
        MinerNodes.POLE_REACH = poleReach;
        MinerNodes.POLE_SPAN = poleSpan;
    }

    static int panelOutput(int tier) {
        return at(panel, tier);
    }

    static int batteryAmp(int tier) {
        return at(battery, tier);
    }

    static int upgradeCost(int tier) {
        if (tier < 1 || upgrades.length == 0) return 0;
        return Math.max(0, upgrades[Math.min(upgrades.length, tier) - 1]);
    }

    static int reserveMinutes() {
        return Math.max(1, reserveSeconds / 60);
    }

    static double swing(boolean wild) {
        double low = wild ? wildLow : calmLow;
        double high = wild ? wildHigh : calmHigh;
        return low + Math.random() * (high - low);
    }

    private static void fill(MinerPlugin plugin, File file, YamlConfiguration live) {
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return;
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean dirty = false;
            for (String key : bundled.getKeys(true)) {
                if (bundled.isConfigurationSection(key) || live.contains(key)) continue;
                live.set(key, bundled.get(key));
                dirty = true;
            }
            if (dirty) live.save(file);
        } catch (Exception ignored) {
        }
    }

    private static int at(int[] values, int tier) {
        int index = Math.max(1, Math.min(values.length, tier)) - 1;
        return values[index];
    }

    private static int[] ints(List<Integer> list, int[] fallback, int cap, boolean zero) {
        if (list == null || list.isEmpty()) return fallback.clone();
        int size = Math.max(1, Math.min(cap, list.size()));
        int floor = zero ? 0 : 1;
        int[] out = new int[size];
        for (int i = 0; i < size; i++) {
            Integer raw = list.get(i);
            if (raw == null || raw < floor) return fallback.clone();
            out[i] = raw;
        }
        return out;
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
