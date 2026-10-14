package wattline;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class Lang {

    static final List<String> CODES = List.of("en", "ru", "uk", "de", "es", "fr", "pt", "zh", "pl", "tr");
    static final List<String> ORDER = List.of("auto", "en", "ru", "uk", "de", "es", "fr", "pt", "zh", "pl", "tr");

    private static final Map<String, Map<String, String>> PACKS = new HashMap<>();
    private static String server = "auto";

    private Lang() {}

    static void load(MinerPlugin plugin) {
        plugin.saveDefaultConfig();
        String chosen = plugin.getConfig().getString("language", "auto");
        server = chosen == null ? "auto" : chosen.toLowerCase(Locale.ROOT);
        PACKS.clear();
        for (String code : CODES) {
            String path = "lang/" + code + ".yml";
            File file = new File(plugin.getDataFolder(), path);
            if (!file.isFile()) {
                file.getParentFile().mkdirs();
                plugin.saveResource(path, false);
            }
            Map<String, String> map = read(file);
            try (InputStream in = plugin.getResource(path)) {
                if (in != null) {
                    StringBuilder extra = new StringBuilder();
                    for (var entry : read(in).entrySet()) {
                        if (map.containsKey(entry.getKey())) continue;
                        map.put(entry.getKey(), entry.getValue());
                        extra.append(entry.getKey()).append(": \"").append(entry.getValue().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"\n");
                    }
                    if (!extra.isEmpty()) {
                        Files.writeString(file.toPath(), extra, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                    }
                }
            } catch (IOException ignored) {
            }
            PACKS.put(code, map);
        }
    }

    static String endonym(String code) {
        return switch (code == null ? "auto" : code) {
            case "en" -> "English";
            case "ru" -> "Русский";
            case "uk" -> "Українська";
            case "de" -> "Deutsch";
            case "es" -> "Español";
            case "fr" -> "Français";
            case "pt" -> "Português";
            case "zh" -> "中文";
            case "pl" -> "Polski";
            case "tr" -> "Türkçe";
            default -> "Auto";
        };
    }

    static String next(String code) {
        int index = ORDER.indexOf(code == null ? "auto" : code);
        if (index < 0) return "auto";
        return ORDER.get((index + 1) % ORDER.size());
    }

    static boolean known(String code) {
        return code != null && (code.equals("auto") || PACKS.containsKey(code) || CODES.contains(code));
    }

    static String text(CommandSender who, String key, Object... args) {
        String raw = lookup(resolve(who), key);
        if (raw == null) raw = lookup("en", key);
        if (raw == null) raw = key;
        for (int i = 0; i < args.length; i++) raw = raw.replace("{" + i + "}", String.valueOf(args[i]));
        return raw;
    }

    private static String resolve(CommandSender who) {
        if (who instanceof Player player) {
            String chosen = MinerSettings.get() == null ? "auto" : MinerSettings.get().language(player);
            if (chosen != null && !chosen.equals("auto") && PACKS.containsKey(chosen)) return chosen;
            String locale = player.getLocale().toLowerCase(Locale.ROOT).replace('-', '_');
            String prefix = locale.length() >= 2 ? locale.substring(0, 2) : "en";
            if (PACKS.containsKey(prefix)) return prefix;
        }
        if (PACKS.containsKey(server)) return server;
        return "en";
    }

    private static String lookup(String code, String key) {
        Map<String, String> pack = PACKS.get(code);
        if (pack == null) return null;
        return pack.get(key);
    }

    private static Map<String, String> read(File file) {
        try {
            return read(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    private static Map<String, String> read(InputStream in) throws IOException {
        return read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }

    private static Map<String, String> read(String text) {
        Map<String, String> map = new HashMap<>();
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        for (String raw : text.split("\n", -1)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
            } else if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
                value = value.substring(1, value.length() - 1).replace("''", "'");
            }
            if (!key.isEmpty()) map.put(key, value);
        }
        return map;
    }
}
