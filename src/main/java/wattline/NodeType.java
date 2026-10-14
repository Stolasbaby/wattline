package wattline;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

enum NodeType {
    ASIC("asic", Material.OBSERVER, NamedTextColor.AQUA),
    PANEL("panel", Material.DAYLIGHT_DETECTOR, NamedTextColor.YELLOW),
    WIRE("wire", Material.COPPER_INGOT, NamedTextColor.GOLD),
    POLE("pole", Material.LIGHTNING_ROD, NamedTextColor.GRAY),
    RACK("rack", Material.LODESTONE, NamedTextColor.WHITE),
    TERMINAL("terminal", Material.VAULT, NamedTextColor.GREEN),
    BATTERY("battery", Material.COPPER_BULB, NamedTextColor.GOLD);

    final String id;
    final Material material;
    final NamedTextColor color;

    NodeType(String id, Material material, NamedTextColor color) {
        this.id = id;
        this.material = material;
        this.color = color;
    }

    String title(CommandSender who) {
        return Lang.text(who, "type." + id + ".name");
    }

    String hint(CommandSender who) {
        return Lang.text(who, "type." + id + ".hint");
    }

    static NodeType byId(String id) {
        if (id == null) return null;
        for (NodeType type : values()) {
            if (type.id.equals(id)) return type;
        }
        return null;
    }
}
