package wattline;

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

    static ItemStack item(Player player, NodeType type) {
        if (type == NodeType.BATTERY) return battery(player, 1);
        ItemStack stack = new ItemStack(type.material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name(type.title(player), type.color));
        meta.lore(List.of(line(type.hint(player))));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, type.id);
        stack.setItemMeta(meta);
        return stack;
    }

    static ItemStack lamp(Player player) {
        ItemStack stack = new ItemStack(Material.LANTERN);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name(Lang.text(player, "item.lamp.name"), NamedTextColor.YELLOW));
        meta.lore(List.of(line(Lang.text(player, "item.lamp.lore1")), line(Lang.text(player, "item.lamp.lore2"))));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, LAMP);
        stack.setItemMeta(meta);
        return stack;
    }

    static boolean isLamp(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || key == null) return false;
        return LAMP.equals(stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    static ItemStack brush(Player player) {
        ItemStack stack = new ItemStack(Material.BRUSH);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name(Lang.text(player, "item.brush.name"), NamedTextColor.GOLD));
        meta.lore(List.of(line(Lang.text(player, "item.brush.lore1")), line(Lang.text(player, "item.brush.lore2"))));
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

    static ItemStack coin(Player player) {
        ItemStack stack = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name(Lang.text(player, "item.coin.name"), NamedTextColor.GOLD));
        meta.lore(List.of(line(Lang.text(player, "item.coin.lore"))));
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
        write(meta, Math.max(0, coins), owner, null);
        stack.setItemMeta(meta);
        return stack;
    }

    static void dress(Player player) {
        foldCoins(player);
        swap(player.getInventory(), player.getUniqueId(), player);
        swap(player.getEnderChest(), player.getUniqueId(), player);
        ItemStack cursor = player.getItemOnCursor();
        ItemStack next = migrate(cursor, player.getUniqueId(), player);
        if (next != null) player.setItemOnCursor(next);
        syncAccount(player);
    }

    static int shown(org.bukkit.entity.Player player) {
        MinerSettings settings = MinerSettings.get();
        if (settings != null) {
            Integer saved = settings.tokens(player.getUniqueId());
            if (saved != null) return saved;
        }
        ItemStack wallet = walletIn(player);
        return wallet == null ? 0 : coins(wallet);
    }

    static int reclaim(org.bukkit.entity.Player player) {
        if (ownsCarried(player)) return -2;
        Inventory ender = player.getEnderChest();
        for (int i = 0; i < ender.getSize(); i++) {
            ItemStack stack = ender.getItem(i);
            if (!isWallet(stack) || !player.getUniqueId().equals(ownerOf(stack))) continue;
            ender.setItem(i, null);
            player.getInventory().addItem(stack);
            syncAccount(player);
            return shown(player);
        }
        int amount = shown(player);
        ItemStack wallet = wallet(player.getUniqueId(), amount);
        MinerSettings settings = MinerSettings.get();
        if (settings != null) settings.rememberTokens(player.getUniqueId(), amount);
        player.getInventory().addItem(wallet);
        return amount;
    }

    private static void syncAccount(org.bukkit.entity.Player player) {
        MinerSettings settings = MinerSettings.get();
        if (settings == null) return;
        ItemStack wallet = walletIn(player);
        if (wallet == null || !player.getUniqueId().equals(ownerOf(wallet))) return;
        Integer saved = settings.tokens(player.getUniqueId());
        if (saved == null) {
            settings.rememberTokens(player.getUniqueId(), coins(wallet));
            return;
        }
        paintPurse(player.getInventory(), player.getUniqueId(), saved);
        paintPurse(player.getEnderChest(), player.getUniqueId(), saved);
        ItemStack cursor = player.getItemOnCursor();
        if (isWallet(cursor) && player.getUniqueId().equals(ownerOf(cursor)) && coins(cursor) != saved) setCoins(cursor, saved);
    }

    private static void paintPurse(Inventory inventory, UUID owner, int saved) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!isWallet(stack) || !owner.equals(ownerOf(stack)) || coins(stack) == saved) continue;
            setCoins(stack, saved);
        }
    }

    static ItemStack walletIn(org.bukkit.entity.Player player) {
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (isWallet(stack) && player.getUniqueId().equals(ownerOf(stack))) return stack;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isWallet(off) && player.getUniqueId().equals(ownerOf(off))) return off;
        return null;
    }

    private static boolean ownsCarried(org.bukkit.entity.Player player) {
        if (isWallet(player.getItemOnCursor()) && player.getUniqueId().equals(ownerOf(player.getItemOnCursor()))) return true;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (isWallet(stack) && player.getUniqueId().equals(ownerOf(stack))) return true;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        return isWallet(off) && player.getUniqueId().equals(ownerOf(off));
    }

    private static void foldCoins(org.bukkit.entity.Player player) {
        ItemStack wallet = walletIn(player);
        if (wallet == null) return;
        int loose = countCoins(player.getInventory()) + countCoins(player.getEnderChest());
        ItemStack cursor = player.getItemOnCursor();
        if (isCoin(cursor)) loose += cursor.getAmount();
        if (loose <= 0) return;
        int have = coins(wallet);
        if (have > Integer.MAX_VALUE - loose) return;
        harvestCoins(player.getInventory());
        harvestCoins(player.getEnderChest());
        if (isCoin(player.getItemOnCursor())) player.setItemOnCursor(null);
        setCoins(wallet, have + loose);
    }

    private static int countCoins(Inventory inventory) {
        int found = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (isCoin(stack)) found += stack.getAmount();
        }
        return found;
    }

    private static int harvestCoins(Inventory inventory) {
        int found = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!isCoin(stack)) continue;
            found += stack.getAmount();
            inventory.setItem(i, null);
        }
        return found;
    }

    static void dressWorlds() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
                    if (state instanceof org.bukkit.inventory.InventoryHolder holder) swap(holder.getInventory(), null, null);
                }
            }
            for (org.bukkit.entity.Item item : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                ItemStack next = migrate(item.getItemStack(), null, null);
                if (next != null) item.setItemStack(next);
            }
        }
    }

    private static void swap(Inventory inventory, UUID holder, Player voice) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack next = migrate(inventory.getItem(i), holder, voice);
            if (next != null) inventory.setItem(i, next);
        }
    }

    private static ItemStack migrate(ItemStack stack, UUID holder, Player voice) {
        if (stack == null || stack.getType().isAir()) return null;
        if (isWallet(stack)) {
            UUID owner = ownerOf(stack);
            boolean ready = stack.getType() == Material.BUNDLE && owner != null;
            if (ready && voice == null) return null;
            if (owner == null) owner = holder;
            if (owner == null && stack.getType() == Material.BUNDLE && voice == null) return null;
            ItemStack wallet = wallet(owner, coins(stack));
            ItemMeta meta = wallet.getItemMeta();
            write(meta, coins(stack), owner, voice);
            wallet.setItemMeta(meta);
            return wallet;
        }
        if (voice == null) return null;
        int amount = stack.getAmount();
        ItemStack next = null;
        if (isLamp(stack)) next = lamp(voice);
        else if (isBrush(stack)) {
            next = brush(voice);
            if (stack.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damage && next.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable copy) {
                copy.setDamage(damage.getDamage());
                next.setItemMeta(copy);
            }
        } else if (isCoin(stack)) next = coin(voice);
        else if (kind(stack) == NodeType.BATTERY) next = battery(voice, tierOf(stack), chargeOf(stack));
        else if (kind(stack) != null) next = item(voice, kind(stack));
        if (next == null) return null;
        next.setAmount(amount);
        return next;
    }

    static boolean isWallet(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || key == null) return false;
        return WALLET.equals(stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    static ItemStack walletOf(org.bukkit.entity.Player player) {
        dress(player);
        return walletIn(player);
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
        write(meta, Math.max(0, coins), ownerOf(stack), null);
        stack.setItemMeta(meta);
        UUID owner = ownerOf(stack);
        if (owner == null) return;
        MinerSettings settings = MinerSettings.get();
        if (settings != null) settings.rememberTokens(owner, Math.max(0, coins));
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

    private static void write(ItemMeta meta, int coins, UUID owner, Player voice) {
        String who = owner == null ? null : Bukkit.getOfflinePlayer(owner).getName();
        boolean named = who != null && !who.isBlank();
        Player speaker = voice != null ? voice : (owner == null ? null : Bukkit.getPlayer(owner));
        meta.displayName(name(named ? Lang.text(speaker, "item.wallet.named", who) : Lang.text(speaker, "item.wallet.name"), NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        if (named) lore.add(Component.text(Lang.text(speaker, "item.wallet.owner", who)).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(Lang.text(speaker, "item.wallet.coins", coins)).color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, WALLET);
        if (coinsKey != null) meta.getPersistentDataContainer().set(coinsKey, PersistentDataType.INTEGER, coins);
        if (owner != null && ownerKey != null) meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
    }

    static ItemStack battery(Player player, int tier) {
        return battery(player, tier, 0);
    }

    static ItemStack battery(Player player, int tier, int charge) {
        int stored = MinerNodes.normalizeBattery(tier);
        boolean admin = MinerNodes.adminTier(stored);
        int kept = admin ? 0 : Math.max(0, Math.min(MinerNodes.capacity(stored), charge));
        ItemStack stack = new ItemStack(admin ? Material.RESPAWN_ANCHOR : Material.COPPER_BULB);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name(admin ? Lang.text(player, "item.battery.admin") : Lang.text(player, "type.battery.name"), admin ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(line(MinerNodes.batteryName(player, stored)));
        lore.add(line(Lang.text(player, "item.battery.amp", MinerNodes.amp(stored))));
        lore.add(line(admin ? Lang.text(player, "item.battery.always") : kept > 0 ? MinerNodes.chargeText(player, kept, stored) : Lang.text(player, "item.battery.reserve", Tune.reserveMinutes())));
        if (!admin && kept > 0) lore.add(line(Lang.text(player, "item.battery.carries")));
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
