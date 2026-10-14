package lolier.miner;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Предметы постановки. Настоящий блок не ставится — на клетке появляется BlockDisplay. */
final class MinerItems {

    static NamespacedKey key;
    static NamespacedKey coinsKey;
    static NamespacedKey ownerKey;
    static final String COIN = "coin";
    static final String WALLET = "wallet";
    static final String BRUSH = "brush";
    static final String LAMP = "lamp";

    private MinerItems() {}

    static void init(NamespacedKey id, NamespacedKey coins) {
        key = id;
        coinsKey = coins;
        ownerKey = new NamespacedKey(id.getNamespace(), "wallet_owner");
    }

    static ItemStack item(NodeType type) {
        if (type == NodeType.BATTERY) return battery(1);
        ItemStack stack = new ItemStack(type.material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name(type.title, type.color));
        meta.lore(List.of(line(type.hint)));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, type.id);
        stack.setItemMeta(meta);
        return stack;
    }

    static ItemStack lamp() {
        ItemStack stack = new ItemStack(Material.LANTERN);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name("Лампочка", NamedTextColor.YELLOW));
        meta.lore(List.of(line("вешается на электростолб"), line("для красоты, ток не ест")));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, LAMP);
        stack.setItemMeta(meta);
        return stack;
    }

    static boolean isLamp(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || key == null) return false;
        return LAMP.equals(stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    static ItemStack brush() {
        ItemStack stack = new ItemStack(Material.BRUSH);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name("Кисть", NamedTextColor.GOLD));
        meta.lore(List.of(line("смахивает пыль с асика и стойки"), line("стирается")));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, BRUSH);
        stack.setItemMeta(meta);
        return stack;
    }

    static boolean isBrush(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || key == null) return false;
        return BRUSH.equals(stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    static int wearBrush(Player player, int wear) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!isBrush(stack)) continue;
            ItemMeta meta = stack.getItemMeta();
            if (!(meta instanceof org.bukkit.inventory.meta.Damageable damage)) return 1;
            int next = damage.getDamage() + Math.max(1, wear);
            if (next >= stack.getType().getMaxDurability()) {
                inv.setItem(i, null);
                return 2;
            }
            damage.setDamage(next);
            stack.setItemMeta(meta);
            return 1;
        }
        return 0;
    }

    static ItemStack coin() {
        ItemStack stack = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name("Жетон", NamedTextColor.GOLD));
        meta.lore(List.of(line("намайнил асик")));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, COIN);
        stack.setItemMeta(meta);
        return stack;
    }

    static boolean isCoin(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || key == null) return false;
        return COIN.equals(stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    static ItemStack wallet(Player player) {
        return wallet(player.getUniqueId(), 0);
    }

    static ItemStack wallet(UUID owner, int coins) {
        ItemStack stack = new ItemStack(Material.BUNDLE);
        ItemMeta meta = stack.getItemMeta();
        meta.setMaxStackSize(1);
        write(meta, Math.max(0, coins), owner);
        stack.setItemMeta(meta);
        return stack;
    }

    static void dress(Player player) {
        swap(player.getInventory(), player.getUniqueId());
        swap(player.getEnderChest(), player.getUniqueId());
        ItemStack cursor = player.getItemOnCursor();
        ItemStack next = migrate(cursor, player.getUniqueId());
        if (next != null) player.setItemOnCursor(next);
    }

    static void dressWorlds() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
                    if (state instanceof org.bukkit.inventory.InventoryHolder holder) swap(holder.getInventory(), null);
                }
            }
            for (org.bukkit.entity.Item item : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                ItemStack next = migrate(item.getItemStack(), null);
                if (next != null) item.setItemStack(next);
            }
        }
    }

    private static void swap(Inventory inventory, UUID holder) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack next = migrate(inventory.getItem(i), holder);
            if (next != null) inventory.setItem(i, next);
        }
    }

    private static ItemStack migrate(ItemStack stack, UUID holder) {
        if (!isWallet(stack)) return null;
        UUID owner = ownerOf(stack);
        boolean ready = stack.getType() == Material.BUNDLE && owner != null;
        if (ready) return null;
        if (owner == null) owner = holder;
        if (owner == null && stack.getType() == Material.BUNDLE) return null;
        return wallet(owner, coins(stack));
    }

    static boolean isWallet(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || key == null) return false;
        return WALLET.equals(stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    static ItemStack walletOf(org.bukkit.entity.Player player) {
        dress(player);
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (isWallet(stack)) return stack;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        return isWallet(off) ? off : null;
    }

    static int balance(org.bukkit.entity.Player player) {
        ItemStack wallet = walletOf(player);
        return wallet == null ? -1 : coins(wallet);
    }

    static int coins(ItemStack stack) {
        if (!isWallet(stack) || coinsKey == null) return 0;
        Integer stored = stack.getItemMeta().getPersistentDataContainer().get(coinsKey, PersistentDataType.INTEGER);
        return stored == null ? 0 : Math.max(0, stored);
    }

    static void setCoins(ItemStack stack, int coins) {
        if (!isWallet(stack)) return;
        ItemMeta meta = stack.getItemMeta();
        write(meta, Math.max(0, coins), ownerOf(stack));
        stack.setItemMeta(meta);
    }

    private static UUID ownerOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || ownerKey == null) return null;
        String raw = stack.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void write(ItemMeta meta, int coins, UUID owner) {
        String who = owner == null ? null : Bukkit.getOfflinePlayer(owner).getName();
        boolean named = who != null && !who.isBlank();
        meta.displayName(name(named ? "Кошелёк " + who : "Кошелёк", NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        if (named) lore.add(Component.text("владелец " + who).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("жетонов " + coins).color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, WALLET);
        if (coinsKey != null) meta.getPersistentDataContainer().set(coinsKey, PersistentDataType.INTEGER, coins);
        if (owner != null && ownerKey != null) meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
    }

    static ItemStack battery(int tier) {
        return battery(tier, 0);
    }

    static ItemStack battery(int tier, int charge) {
        int stored = MinerNodes.normalizeBattery(tier);
        boolean admin = MinerNodes.adminTier(stored);
        int kept = admin ? 0 : Math.max(0, Math.min(MinerNodes.capacity(stored), charge));
        ItemStack stack = new ItemStack(admin ? Material.RESPAWN_ANCHOR : Material.COPPER_BULB);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name(admin ? "Админский аккумулятор" : "Аккумулятор", admin ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(line(MinerNodes.batteryName(stored)));
        lore.add(line("ток " + MinerNodes.amp(stored)));
        lore.add(line(admin ? "не садится, работает и днём" : kept > 0 ? MinerNodes.chargeText(kept, stored) : "запас на 10 минут своего тока"));
        if (!admin && kept > 0) lore.add(line("заряд переносится вместе с блоком"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, NodeType.BATTERY.id);
        meta.getPersistentDataContainer().set(tierKey(), PersistentDataType.INTEGER, stored);
        if (!admin) meta.getPersistentDataContainer().set(chargeKey(), PersistentDataType.INTEGER, kept);
        stack.setItemMeta(meta);
        return stack;
    }

    static int chargeOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || key == null) return 0;
        Integer charge = stack.getItemMeta().getPersistentDataContainer().get(chargeKey(), PersistentDataType.INTEGER);
        return charge == null ? 0 : charge;
    }

    static int tierOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || key == null) return 1;
        Integer tier = stack.getItemMeta().getPersistentDataContainer().get(tierKey(), PersistentDataType.INTEGER);
        return tier == null ? 1 : tier;
    }

    private static NamespacedKey tierKey() {
        return new NamespacedKey(key.getNamespace(), "tier");
    }

    private static NamespacedKey chargeKey() {
        return new NamespacedKey(key.getNamespace(), "charge");
    }

    static NodeType kind(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || key == null) return null;
        String id = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return NodeType.byId(id);
    }

    private static Component name(String text, NamedTextColor color) {
        return Component.text(text).color(color).decoration(TextDecoration.ITALIC, false);
    }

    private static Component line(String text) {
        return Component.text(text).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
