package wattline;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

enum NodeType {
    ASIC("asic", Material.OBSERVER, "Асик", NamedTextColor.AQUA, "ПКМ по блоку — поставить. Ест ток с панели по проводу."),
    PANEL("panel", Material.DAYLIGHT_DETECTOR, "Солнечная панель", NamedTextColor.YELLOW, "Днём даёт ток. В меню можно усилить до 8."),
    WIRE("wire", Material.COPPER_INGOT, "Провод", NamedTextColor.GOLD, "До 16 блоков. Через электростолб дальше: 32 до машины, 48 между столбами."),
    POLE("pole", Material.LIGHTNING_ROD, "Электростолб", NamedTextColor.GRAY, "Ставь по пути провода. До машины 32 блока, между столбами 48."),
    RACK("rack", Material.LODESTONE, "Серверная стойка", NamedTextColor.WHITE, "ПКМ асиком — вставить лезвие. Каждое ест свой ток."),
    TERMINAL("terminal", Material.VAULT, "Обменник", NamedTextColor.GREEN, "Ест 1 ток. Ночью живёт от батареи. Курс скачет."),
    BATTERY("battery", Material.COPPER_BULB, "Аккумулятор", NamedTextColor.GOLD, "Копит лишний дневной ток. В меню усиливается. Ночью кормит сеть.");

    final String id;
    final Material material;
    final String title;
    final NamedTextColor color;
    final String hint;

    NodeType(String id, Material material, String title, NamedTextColor color, String hint) {
        this.id = id;
        this.material = material;
        this.title = title;
        this.color = color;
        this.hint = hint;
    }

    static NodeType byId(String id) {
        if (id == null) return null;
        for (NodeType type : values()) {
            if (type.id.equals(id)) return type;
        }
        return null;
    }
}
