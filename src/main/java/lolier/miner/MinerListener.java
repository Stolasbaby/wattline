package lolier.miner;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Постановка, меню, протяжка провода и слом шифтом. */
final class MinerListener implements Listener {

    private final MinerNodes nodes;

    MinerListener(MinerNodes nodes) {
        this.nodes = nodes;
    }

    @EventHandler
    public void use(PlayerInteractEvent event) {
        if (MinerItems.isWallet(event.getItem())) {
            event.setCancelled(true);
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;
        Player player = event.getPlayer();
        if (player.isSneaking() && nodes.isPulling(player)) {
            event.setCancelled(true);
            nodes.cancelPull(player);
            return;
        }
        if (nodes.isPulling(player)) {
            event.setCancelled(true);
            MinerNodes.Node dest = aimed(event);
            if (dest != null) nodes.finishPull(player, dest);
            else {
                Block cell = airCell(event);
                if (cell != null) nodes.finishPull(player, cell);
            }
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        ItemStack held = event.getItem();
        NodeType placing = MinerItems.kind(held);
        Block cell = event.getClickedBlock().getRelative(event.getBlockFace());
        MinerNodes.Node existing = nodes.at(cell);
        if (existing == null) existing = nodes.at(event.getClickedBlock());
        if (placing == NodeType.WIRE && existing != null && !player.isSneaking()) {
            event.setCancelled(true);
            nodes.startPull(player, existing);
            return;
        }
        if (placing == NodeType.ASIC && existing != null && existing.type == NodeType.RACK && !player.isSneaking()) {
            event.setCancelled(true);
            if (nodes.insert(player, existing)) held.setAmount(held.getAmount() - 1);
            return;
        }
        if (MinerItems.isLamp(held) && existing != null && existing.type == NodeType.POLE && !player.isSneaking()) {
            event.setCancelled(true);
            if (nodes.hangLamp(player, existing)) spendOne(player, held);
            return;
        }
        if (placing != null) {
            event.setCancelled(true);
            if (!player.hasPermission("miner.use")) return;
            if (existing != null) {
                act(player, existing);
                return;
            }
            if (!cell.getType().isAir()) {
                MinerNodes.tell(player, "нужна пустая клетка");
                return;
            }
            if (player.getLocation().distanceSquared(cell.getLocation()) > 36) return;
            if (nodes.place(player, cell, placing, MinerItems.tierOf(held), MinerItems.chargeOf(held))) held.setAmount(held.getAmount() - 1);
            return;
        }
        if (existing == null) return;
        event.setCancelled(true);
        act(player, existing);
    }

    @EventHandler
    public void click(PlayerInteractEntityEvent event) {
        if (MinerItems.isWallet(event.getPlayer().getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) return;
        MinerNodes.Node node = nodes.byEntity(event.getRightClicked().getUniqueId());
        if (node == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.isSneaking() && nodes.isPulling(player)) {
            nodes.cancelPull(player);
            return;
        }
        if (nodes.isPulling(player)) {
            nodes.finishPull(player, node);
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (MinerItems.kind(held) == NodeType.WIRE && !player.isSneaking()) {
            nodes.startPull(player, node);
            return;
        }
        if (MinerItems.kind(held) == NodeType.ASIC && node.type == NodeType.RACK && !player.isSneaking()) {
            if (nodes.insert(player, node)) held.setAmount(held.getAmount() - 1);
            return;
        }
        if (MinerItems.isLamp(held) && node.type == NodeType.POLE && !player.isSneaking()) {
            if (nodes.hangLamp(player, node)) spendOne(player, held);
            return;
        }
        act(player, node);
    }

    @EventHandler
    public void menu(InventoryClickEvent event) {
        MinerMenu.click(event, nodes);
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MinerMenu) event.setCancelled(true);
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        MinerItems.dress(event.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        nodes.cancelPull(event.getPlayer());
        MinerSettings.forgetViewer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void hurt(EntityDamageEvent event) {
        if (event.getEntity().getScoreboardTags().contains(MinerVisuals.SHELL)) event.setCancelled(true);
    }

    @EventHandler
    public void shift(EntityTeleportEvent event) {
        if (event.getEntity().getScoreboardTags().contains(MinerVisuals.SHELL)) event.setCancelled(true);
    }

    @EventHandler
    public void placeBlock(BlockPlaceEvent event) {
        if (MinerItems.kind(event.getItemInHand()) != null) {
            event.setCancelled(true);
            return;
        }
        if (event.isCancelled()) return;
        if (nodes.cutThrough(event.getBlockPlaced())) MinerNodes.tell(event.getPlayer(), "порвался провод");
    }

    @EventHandler
    public void chunk(ChunkLoadEvent event) {
        if (nodes.restore(event.getChunk()) || nodes.restoreLinks()) nodes.save();
    }

    private void act(Player player, MinerNodes.Node node) {
        if (player.isSneaking()) {
            if (!nodes.owner(player, node)) {
                MinerNodes.tell(player, "это не твоё");
                return;
            }
            if (nodes.remove(node, player)) MinerNodes.tell(player, "снял");
            return;
        }
        if (node.type != NodeType.TERMINAL && !nodes.owns(player, node)) {
            MinerNodes.tell(player, "это не твоё");
            return;
        }
        if (!player.hasPermission("miner.use")) return;
        nodes.open(player, node);
    }

    private MinerNodes.Node aimed(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            BlockFace face = event.getBlockFace();
            MinerNodes.Node node = nodes.at(event.getClickedBlock().getRelative(face));
            if (node == null) node = nodes.at(event.getClickedBlock());
            if (node != null) return node;
        }
        var hit = event.getPlayer().rayTraceBlocks(8);
        if (hit == null || hit.getHitBlock() == null) return null;
        MinerNodes.Node node = nodes.at(hit.getHitBlock());
        if (node == null && hit.getHitBlockFace() != null) node = nodes.at(hit.getHitBlock().getRelative(hit.getHitBlockFace()));
        return node;
    }

    private static void spendOne(Player player, ItemStack held) {
        if (held.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else held.setAmount(held.getAmount() - 1);
    }

    private static Block airCell(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && event.getBlockFace() != null) {
            Block cell = event.getClickedBlock().getRelative(event.getBlockFace());
            if (cell.getType().isAir()) return cell;
        }
        var hit = event.getPlayer().rayTraceBlocks(8);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) return null;
        Block cell = hit.getHitBlock().getRelative(hit.getHitBlockFace());
        return cell.getType().isAir() ? cell : null;
    }
}
