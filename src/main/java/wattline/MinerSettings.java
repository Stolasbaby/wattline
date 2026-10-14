package wattline;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MinerSettings {

    private static MinerSettings current;
    private static final Set<Integer> moving = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Set<Integer>> shown = new ConcurrentHashMap<>();

    private final MinerPlugin plugin;
    private final File file;
    private final Map<UUID, Pref> prefs = new HashMap<>();

    MinerSettings(MinerPlugin plugin) {
        current = this;
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    static MinerSettings get() {
        return current;
    }

    static void trackMoving(int entityId) {
        moving.add(entityId);
    }

    static void forgetMoving(int entityId) {
        moving.remove(entityId);
    }

    static boolean moving(int entityId) {
        return moving.contains(entityId);
    }

    static boolean firstLook(UUID player, int entityId) {
        return shown.computeIfAbsent(player, id -> ConcurrentHashMap.newKeySet()).add(entityId);
    }

    static void forgetViewer(UUID player) {
        shown.remove(player);
    }

    boolean anyoneAnimating() {
        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (anim(player)) return true;
        }
        return false;
    }

    boolean sound(Player player) {
        Pref pref = player == null ? null : prefs.get(player.getUniqueId());
        return pref == null || pref.sound;
    }

    boolean anim(Player player) {
        Pref pref = player == null ? null : prefs.get(player.getUniqueId());
        return pref == null || pref.anim;
    }

    String language(Player player) {
        Pref pref = player == null ? null : prefs.get(player.getUniqueId());
        return pref == null || pref.lang == null ? "auto" : pref.lang;
    }

    void toggleSound(Player player) {
        Pref row = row(player);
        row.sound = !row.sound;
        save();
    }

    void toggleAnim(Player player) {
        Pref row = row(player);
        row.anim = !row.anim;
        save();
    }

    void setLanguage(Player player, String code) {
        row(player).lang = code == null ? "auto" : code;
        save();
    }

    Integer tokens(UUID id) {
        Pref pref = prefs.get(id);
        return pref == null ? null : pref.tokens;
    }

    void rememberTokens(UUID id, int amount) {
        Pref row = prefs.computeIfAbsent(id, ignored -> new Pref());
        int kept = Math.max(0, amount);
        if (row.tokens != null && row.tokens == kept) return;
        row.tokens = kept;
        save();
    }

    static void play(Player player, Sound sound, float volume, float pitch) {
        if (player == null || current != null && !current.sound(player)) return;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    static void playAt(Player player, Location at, Sound sound, SoundCategory category, float volume, float pitch) {
        if (player == null || current != null && !current.sound(player)) return;
        player.playSound(at, sound, category, volume, pitch);
    }

    private Pref row(Player player) {
        return prefs.computeIfAbsent(player.getUniqueId(), id -> new Pref());
    }

    private void load() {
        File source = Store.readable(file);
        if (!source.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);
        var section = yaml.getConfigurationSection("players");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            try {
                Pref pref = new Pref();
                pref.sound = section.getBoolean(id + ".sound", true);
                pref.anim = section.getBoolean(id + ".anim", true);
                pref.lang = section.getString(id + ".lang", "auto");
                if (section.contains(id + ".tokens")) pref.tokens = Math.max(0, section.getInt(id + ".tokens"));
                prefs.put(UUID.fromString(id), pref);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var entry : prefs.entrySet()) {
            String path = "players." + entry.getKey();
            yaml.set(path + ".sound", entry.getValue().sound);
            yaml.set(path + ".anim", entry.getValue().anim);
            yaml.set(path + ".lang", entry.getValue().lang);
            if (entry.getValue().tokens != null) yaml.set(path + ".tokens", entry.getValue().tokens);
        }
        try {
            Store.yaml(file, yaml);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save player settings: " + e.getMessage());
        }
    }

    private static final class Pref {
        boolean sound = true;
        boolean anim = true;
        String lang = "auto";
        Integer tokens;
    }
}
