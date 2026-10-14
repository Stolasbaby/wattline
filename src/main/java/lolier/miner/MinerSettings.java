package lolier.miner;

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

/** Личные флаги звука и анимации. Чужие игроки их не видят. */
final class MinerSettings {

    private static MinerSettings current;
    private static final Set<Integer> moving = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Set<Integer>> shown = new ConcurrentHashMap<>();

    private final MinerPlugin plugin;
    private final File file;
    private final Map<UUID, boolean[]> prefs = new HashMap<>();

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
        return flag(player, 0);
    }

    boolean anim(Player player) {
        return flag(player, 1);
    }

    void toggleSound(Player player) {
        boolean[] row = row(player);
        row[0] = !row[0];
        save();
    }

    void toggleAnim(Player player) {
        boolean[] row = row(player);
        row[1] = !row[1];
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

    private boolean flag(Player player, int index) {
        if (player == null) return true;
        boolean[] row = prefs.get(player.getUniqueId());
        return row == null || row[index];
    }

    private boolean[] row(Player player) {
        return prefs.computeIfAbsent(player.getUniqueId(), id -> new boolean[] {true, true});
    }

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("players");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            try {
                prefs.put(UUID.fromString(id), new boolean[] {
                        section.getBoolean(id + ".sound", true),
                        section.getBoolean(id + ".anim", true)
                });
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var entry : prefs.entrySet()) {
            String path = "players." + entry.getKey();
            yaml.set(path + ".sound", entry.getValue()[0]);
            yaml.set(path + ".anim", entry.getValue()[1]);
        }
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не сохранил настройки: " + e.getMessage());
        }
    }
}
