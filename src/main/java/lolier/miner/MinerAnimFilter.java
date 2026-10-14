package lolier.miner;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;

/** Игроку с выключенной анимацией не шлём кадры вращения и искры. Остальные видят как раньше. */
final class MinerAnimFilter {

    private MinerAnimFilter() {}

    static void hook(MinerPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) return;
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGH, PacketType.Play.Server.ENTITY_METADATA) {
            @Override
            public void onPacketSending(PacketEvent event) {
                MinerSettings settings = MinerSettings.get();
                if (settings == null || settings.anim(event.getPlayer())) return;
                Integer entityId = entityId(event);
                if (entityId == null || !MinerSettings.moving(entityId)) return;
                if (MinerSettings.firstLook(event.getPlayer().getUniqueId(), entityId)) return;
                event.setCancelled(true);
            }

            private static Integer entityId(PacketEvent event) {
                try {
                    if (event.getPacket().getIntegers().size() < 1) return null;
                    return event.getPacket().getIntegers().read(0);
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
        });
    }
}
