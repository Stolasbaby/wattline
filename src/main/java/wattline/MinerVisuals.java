package wattline;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

final class MinerVisuals {

    static final int MODEL = 32;
    static final String TAG = "miner-rig";
    static final String SHELL = "miner-shell";
    private static final String LED = "miner-led";
    private static final String GLASS = "miner-glass";
    private static final String CABLE = "miner-cable";
    private static final String BLADE = "miner-blade";
    private static final String BOARD = "miner-board";
    private static final String TREND = "miner-trend";
    private static final String CELL = "miner-cell-";
    private static final String DUST = "miner-dust-";
    private static final String BULB = "miner-bulb-";
    private static final AxisAngle4f STILL = new AxisAngle4f(0f, 0f, 1f, 0f);
    private static final Vector3f PIVOT = new Vector3f(0.5f, 0f, 0.5f);

    private MinerNodes nodes;
    private final Quaternionf facing = new Quaternionf();
    private final List<Blade> blades = new ArrayList<>();

    MinerVisuals() {}

    void attach(MinerNodes nodes) {
        this.nodes = nodes;
    }

    void redraw(MinerNodes.Node node, java.util.List<MinerNodes.Node> touching) {
        World world = node.world();
        if (world == null) return;
        for (MinerNodes.Node other : touching) {
            if (!world.isChunkLoaded(other.x >> 4, other.z >> 4)) world.getChunkAt(other.x >> 4, other.z >> 4);
        }
        for (UUID id : node.displays) remove(world, id);
        node.displays.clear();
        if (node.lamp != null) {
            remove(world, node.lamp);
            node.lamp = null;
        }
        forgetBlades(node.id);
        orient(node.facing);
        Location corner = node.corner();
        switch (node.type) {
            case ASIC -> asic(world, corner, node);
            case PANEL -> panel(world, corner, node);
            case WIRE -> wire(world, corner, node);
            case RACK -> rack(world, corner, node);
            case TERMINAL -> terminal(world, corner, node);
            case BATTERY -> battery(world, corner, node);
            case POLE -> pole(world, corner, node);
        }
        for (MinerNodes.Node other : touching) {
            if (nodes.linked(node, other)) continue;
            if (order(node, other) > 0) continue;
            curve(node, other, node.displays, null);
        }
        float tall = node.type == NodeType.RACK || node.type == NodeType.TERMINAL || node.type == NodeType.POLE ? 2.05f : 1.05f;
        if (node.click != null && world.getEntity(node.click) instanceof Interaction click) {
            click.setInteractionHeight(tall);
        } else {
            Interaction click = world.spawn(corner.clone().add(0.5, 0, 0.5), Interaction.class, entity -> {
                entity.setInteractionWidth(1.05f);
                entity.setInteractionHeight(tall);
                entity.setResponsive(true);
                entity.setPersistent(true);
                entity.addScoreboardTag(TAG);
            });
            node.click = click.getUniqueId();
        }
        solid(node);
        nodes.index(node);
    }

    void solid(MinerNodes.Node node) {
        World world = node.world();
        if (world == null || !world.isChunkLoaded(node.x >> 4, node.z >> 4)) return;
        int height = node.type == NodeType.RACK || node.type == NodeType.TERMINAL || node.type == NodeType.POLE ? 2 : 1;
        List<Entity> found = new ArrayList<>();
        boolean complete = true;
        for (int i = 0; i < height; i++) {
            Entity shell = shellAt(world, node.x, node.y + i, node.z);
            if (shell == null) complete = false;
            else found.add(shell);
        }
        if (!complete) {
            sweepColumn(node);
            for (UUID id : node.shells) remove(world, id);
            found.clear();
            for (int i = 0; i < height; i++) {
                Location at = new Location(world, node.x + 0.5, node.y + i, node.z + 0.5);
                found.add(world.spawn(at, org.bukkit.entity.Shulker.class, this::shell));
            }
        }
        node.shells.clear();
        for (Entity shell : found) node.shells.add(shell.getUniqueId());
    }

    private void shell(org.bukkit.entity.Shulker entity) {
        entity.setAI(false);
        entity.setAware(false);
        entity.setInvisible(true);
        entity.setSilent(true);
        entity.setInvulnerable(true);
        entity.setGravity(false);
        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);
        entity.setCollidable(true);
        entity.setPeek(0f);
        entity.setGlowing(false);
        entity.addScoreboardTag(TAG);
        entity.addScoreboardTag(SHELL);
    }

    private Entity shellAt(World world, int x, int y, int z) {
        Location at = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        Entity best = null;
        double nearest = 1;
        for (Entity entity : world.getNearbyEntities(at, 0.6, 1.2, 0.6)) {
            if (!entity.getScoreboardTags().contains(SHELL)) continue;
            double dy = Math.abs(entity.getLocation().getY() - y);
            if (dy < 0.45 && dy < nearest) {
                best = entity;
                nearest = dy;
            }
        }
        return best;
    }

    private void sweepColumn(MinerNodes.Node node) {
        World world = node.world();
        if (world == null) return;
        int height = tall(node) ? 2 : 1;
        Location at = new Location(world, node.x + 0.5, node.y + (height - 1) / 2.0, node.z + 0.5);
        for (Entity entity : new ArrayList<>(world.getNearbyEntities(at, 0.7, height / 2.0 + 0.7, 0.7))) {
            if (entity.getScoreboardTags().contains(SHELL)) entity.remove();
        }
    }

    private static boolean tall(MinerNodes.Node node) {
        return node.type == NodeType.RACK || node.type == NodeType.TERMINAL || node.type == NodeType.POLE;
    }

    void dropStrays(org.bukkit.Chunk chunk) {
        if (nodes == null) return;
        World world = chunk.getWorld();
        for (Entity entity : chunk.getEntities()) {
            if (!entity.getScoreboardTags().contains(SHELL)) continue;
            Location loc = entity.getLocation();
            int x = loc.getBlockX();
            int y = (int) Math.floor(loc.getY() + 0.05);
            int z = loc.getBlockZ();
            MinerNodes.Node node = nodes.at(world.getBlockAt(x, y, z));
            if (node == null) {
                MinerNodes.Node below = nodes.at(world.getBlockAt(x, y - 1, z));
                if (below != null && tall(below)) node = below;
            }
            if (node == null) entity.remove();
        }
    }

    void lamp(MinerNodes.Node node, boolean on) {
        World world = node.world();
        if (world == null) return;
        Material led = switch (node.type) {
            case ASIC -> on ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
            case PANEL -> on ? Material.YELLOW_CONCRETE : Material.GRAY_CONCRETE;
            case RACK -> node.bays == 0 ? Material.GRAY_CONCRETE : on ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
            case TERMINAL -> on ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE;
            case BATTERY -> batteryLed(node);
            default -> Material.COPPER_BLOCK;
        };
        Display.Brightness glow = on ? new Display.Brightness(15, 15) : null;
        for (UUID id : node.displays) {
            if (!(world.getEntity(id) instanceof BlockDisplay display)) continue;
            if (display.getScoreboardTags().contains(LED)) {
                display.setBlock(led.createBlockData());
                display.setBrightness(glow);
            } else if (display.getScoreboardTags().contains(GLASS)) {
                display.setBrightness(on ? new Display.Brightness(15, 10) : null);
            }
        }
    }

    void clear(MinerNodes.Node node) {
        if (node.type == NodeType.POLE) shine(node, false);
        World world = node.world();
        if (world == null) return;
        for (UUID id : node.displays) remove(world, id);
        if (node.lamp != null) remove(world, node.lamp);
        if (node.click != null) remove(world, node.click);
        sweepColumn(node);
        for (UUID id : new ArrayList<>(node.shells)) remove(world, id);
        node.shells.clear();
        node.displays.clear();
        node.lamp = null;
        node.click = null;
        forgetBlades(node.id);
    }

    boolean bladesReady(MinerNodes.Node node) {
        if (node.type != NodeType.ASIC && !(node.type == NodeType.RACK && node.bays > 0)) return true;
        for (Blade blade : blades) {
            if (blade.nodeId.equals(node.id)) return true;
        }
        return false;
    }

    void loadBlade(String nodeId, String raw) {
        String[] part = raw.split(",");
        try {
            if (part.length == 5) {
                float cx = Float.parseFloat(part[1]);
                float cy = Float.parseFloat(part[2]);
                float z = Float.parseFloat(part[3]);
                blades.add(new Blade(nodeId, UUID.fromString(part[0]),
                        cx - 0.012f, cy + 0.028f, z + 0.032f, 0.024f, 0.105f, 0.016f,
                        cx, cy, z + 0.04f, Float.parseFloat(part[4]), -1));
                return;
            }
            if (part.length < 12) return;
            blades.add(new Blade(nodeId, UUID.fromString(part[0]),
                    Float.parseFloat(part[1]), Float.parseFloat(part[2]), Float.parseFloat(part[3]),
                    Float.parseFloat(part[4]), Float.parseFloat(part[5]), Float.parseFloat(part[6]),
                    Float.parseFloat(part[7]), Float.parseFloat(part[8]), Float.parseFloat(part[9]),
                    Float.parseFloat(part[10]), Integer.parseInt(part[11])));
        } catch (RuntimeException ignored) {
        }
    }

    List<String> bladeRows(String nodeId) {
        List<String> rows = new ArrayList<>();
        for (Blade blade : blades) {
            if (!blade.nodeId.equals(nodeId)) continue;
            rows.add(blade.id + "," + blade.x + "," + blade.y + "," + blade.z + ","
                    + blade.sx + "," + blade.sy + "," + blade.sz + ","
                    + blade.px + "," + blade.py + "," + blade.pz + "," + blade.base + "," + blade.bay);
        }
        return rows;
    }

    void animate(int step) {
        MinerSettings settings = MinerSettings.get();
        if (settings != null && !settings.anyoneAnimating()) return;
        animateNow(step);
    }

    private void animateNow(int step) {
        for (Blade blade : blades) {
            MinerNodes.Node node = nodes.byId(blade.nodeId);
            if (node == null) continue;
            boolean spinning = blade.bay < 0 ? node.powered : blade.bay < node.bays && node.bayOn[blade.bay];
            if (!spinning) continue;
            World world = node.world();
            if (world == null) continue;
            if (!(world.getEntity(blade.id) instanceof BlockDisplay display)) continue;
            MinerSettings.trackMoving(display.getEntityId());
            orient(node.facing);
            float angle = blade.base + step * 0.85f;
            Pose pose = pose(blade.x, blade.y, blade.z, blade.sx, blade.sy, blade.sz,
                    new Quaternionf().rotateZ(angle), new Vector3f(blade.px, blade.py, blade.pz));
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(2);
            display.setTransformation(new Transformation(pose.translation, new AxisAngle4f(pose.rotation), pose.scale, STILL));
        }
        if (step % 4 == 0) shimmer(step);
        float along = (step % 18) / 18f;
        for (MinerNodes.Link link : nodes.links()) {
            if (!link.live || link.spark == null) continue;
            MinerNodes.Node a = nodes.byId(link.a);
            MinerNodes.Node b = nodes.byId(link.b);
            if (a == null || b == null || a.world() == null) continue;
            if (!(a.world().getEntity(link.spark) instanceof BlockDisplay spark)) continue;
            MinerSettings.trackMoving(spark.getEntityId());
            Vector3f at = cablePoint(a, b, along);
            Vector3f translation = new Vector3f(at.x - a.x, at.y - a.y, at.z - a.z).sub(0.04f, 0.04f, 0.04f);
            spark.setInterpolationDelay(0);
            spark.setInterpolationDuration(3);
            spark.setTransformation(new Transformation(translation, STILL, new Vector3f(0.08f, 0.08f, 0.08f), STILL));
            spark.setBrightness(new Display.Brightness(15, 15));
        }
    }

    void drawLink(MinerNodes.Link link, MinerNodes.Node a, MinerNodes.Node b) {
        World world = a.world();
        if (world == null) return;
        loadSpan(world, a, b);
        for (UUID id : link.displays) remove(world, id);
        link.displays.clear();
        if (link.spark != null) {
            remove(world, link.spark);
            link.spark = null;
        }
        curve(a, b, link.displays, link);
    }

    void clearLink(MinerNodes.Link link) {
        MinerNodes.Node a = nodes.byId(link.a);
        World world = a == null ? null : a.world();
        if (world == null && nodes.byId(link.b) != null) world = nodes.byId(link.b).world();
        if (world == null) return;
        MinerNodes.Node b = nodes.byId(link.b);
        if (a != null && b != null) loadSpan(world, a, b);
        for (UUID id : link.displays) remove(world, id);
        if (link.spark != null) remove(world, link.spark);
        link.displays.clear();
        link.spark = null;
    }

    private void asic(World world, Location corner, MinerNodes.Node node) {
        Material led = node.powered ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        foot(world, corner, node, 0.10f, 0.16f);
        foot(world, corner, node, 0.82f, 0.16f);
        foot(world, corner, node, 0.10f, 0.76f);
        foot(world, corner, node, 0.82f, 0.76f);
        part(world, corner, node, Material.POLISHED_DEEPSLATE, 0.08f, 0.06f, 0.16f, 0.84f, 0.66f, 0.68f, null, null, null);
        part(world, corner, node, lidDust(node.dust), 0.06f, 0.72f, 0.14f, 0.88f, 0.04f, 0.72f, null, null, DUST + "lid");
        part(world, corner, node, bandDust(node.dust), 0.08f, 0.68f, 0.15f, 0.84f, 0.028f, 0.70f, null, null, DUST + "band");
        part(world, corner, node, filmDust(node.dust, 0), 0.12f, 0.762f, 0.20f, 0.76f, 0.03f, 0.56f, null, null, DUST + "0");
        part(world, corner, node, filmDust(node.dust, 1), 0.16f, 0.30f, 0.905f, 0.68f, 0.32f, 0.02f, null, null, DUST + "1");
        part(world, corner, node, Material.BLACK_CONCRETE, 0.10f, 0.14f, 0.82f, 0.80f, 0.54f, 0.035f, null, null, null);
        fan(world, corner, node, 0.30f, 0.44f, 0.855f);
        fan(world, corner, node, 0.70f, 0.44f, 0.855f);
        for (int i = 0; i < 5; i++) {
            part(world, corner, node, Material.IRON_BLOCK, 0.18f, 0.16f + i * 0.10f, 0.03f, 0.64f, 0.028f, 0.14f, null, null, null);
        }
        part(world, corner, node, Material.BLACK_CONCRETE, 0.00f, 0.22f, 0.28f, 0.10f, 0.30f, 0.26f, null, null, null);
        part(world, corner, node, Material.GRAY_CONCRETE, 0.00f, 0.28f, 0.34f, 0.012f, 0.16f, 0.14f, null, null, null);
        part(world, corner, node, Material.GREEN_TERRACOTTA, 0.905f, 0.26f, 0.32f, 0.03f, 0.26f, 0.32f, null, null, null);
        part(world, corner, node, Material.GOLD_BLOCK, 0.93f, 0.34f, 0.40f, 0.02f, 0.10f, 0.16f, null, null, null);
        part(world, corner, node, Material.IRON_BLOCK, 0.28f, 0.755f, 0.42f, 0.04f, 0.07f, 0.04f, null, null, null);
        part(world, corner, node, Material.IRON_BLOCK, 0.68f, 0.755f, 0.42f, 0.04f, 0.07f, 0.04f, null, null, null);
        part(world, corner, node, Material.BLACK_CONCRETE, 0.28f, 0.82f, 0.41f, 0.44f, 0.035f, 0.06f, null, null, null);
        part(world, corner, node, Material.BLACK_CONCRETE, 0.38f, 0.20f, 0.02f, 0.24f, 0.16f, 0.08f, null, null, null);
        part(world, corner, node, Material.COPPER_BLOCK, 0.43f, 0.25f, 0.00f, 0.035f, 0.06f, 0.045f, null, null, null);
        part(world, corner, node, Material.COPPER_BLOCK, 0.535f, 0.25f, 0.00f, 0.035f, 0.06f, 0.045f, null, null, null);
        float[] leds = {0.36f, 0.44f, 0.52f};
        for (float x : leds) {
            part(world, corner, node, led, x, 0.175f, 0.855f, 0.04f, 0.04f, 0.025f, null, null, LED);
        }
        part(world, corner, node, Material.ORANGE_CONCRETE, 0.60f, 0.175f, 0.855f, 0.04f, 0.04f, 0.025f, null, null, null);
    }

    void dustSkin(MinerNodes.Node node) {
        if (node.type != NodeType.ASIC && node.type != NodeType.RACK) return;
        World world = node.world();
        if (world == null) return;
        for (UUID id : node.displays) {
            if (!(world.getEntity(id) instanceof BlockDisplay display)) continue;
            Material next = null;
            for (String tag : display.getScoreboardTags()) {
                if (tag.equals(BLADE)) next = bladeDust(node.dust);
                else if (tag.equals(DUST + "lid")) next = lidDust(node.dust);
                else if (tag.equals(DUST + "band")) next = bandDust(node.dust);
                else if (tag.equals(DUST + "0")) next = filmDust(node.dust, 0);
                else if (tag.equals(DUST + "1") || tag.startsWith(DUST + "bay")) next = filmDust(node.dust, 1);
                else if (tag.startsWith(DUST + "lip")) next = lipDust(node.dust);
            }
            if (next != null) display.setBlock(next.createBlockData());
        }
    }

    static int dustStage(int dust) {
        if (dust >= 80) return 4;
        if (dust >= 55) return 3;
        if (dust >= 30) return 2;
        if (dust >= 12) return 1;
        return 0;
    }

    private static Material lidDust(int dust) {
        return switch (dustStage(dust)) {
            case 1 -> Material.CLAY;
            case 2 -> Material.GRAY_CONCRETE;
            case 3 -> Material.GRAY_TERRACOTTA;
            case 4 -> Material.TUFF;
            default -> Material.LIGHT_GRAY_CONCRETE;
        };
    }

    private static Material bandDust(int dust) {
        return switch (dustStage(dust)) {
            case 2 -> Material.RED_TERRACOTTA;
            case 3 -> Material.BROWN_TERRACOTTA;
            case 4 -> Material.BROWN_CONCRETE;
            default -> Material.RED_CONCRETE;
        };
    }

    private static Material bladeDust(int dust) {
        return switch (dustStage(dust)) {
            case 1 -> Material.LIGHT_GRAY_CONCRETE;
            case 2 -> Material.CLAY;
            case 3 -> Material.GRAY_CONCRETE;
            case 4 -> Material.BROWN_TERRACOTTA;
            default -> Material.WHITE_CONCRETE;
        };
    }

    private static Material lipDust(int dust) {
        return switch (dustStage(dust)) {
            case 1 -> Material.LIGHT_GRAY_TERRACOTTA;
            case 2 -> Material.CLAY;
            case 3 -> Material.GRAY_TERRACOTTA;
            case 4 -> Material.BROWN_TERRACOTTA;
            default -> Material.CYAN_TERRACOTTA;
        };
    }

    private static Material filmDust(int dust, int layer) {
        int stage = dustStage(dust);
        if (layer == 0) {
            if (stage < 2) return Material.BARRIER;
            return stage >= 4 ? Material.TUFF : stage == 3 ? Material.GRAY_CONCRETE_POWDER : Material.LIGHT_GRAY_CONCRETE_POWDER;
        }
        if (stage < 3) return Material.BARRIER;
        return stage >= 4 ? Material.TUFF : Material.GRAY_CONCRETE_POWDER;
    }

    private void fan(World world, Location corner, MinerNodes.Node node, float cx, float cy, float z) {
        part(world, corner, node, Material.BLACK_CONCRETE, cx - 0.155f, cy - 0.155f, z - 0.01f, 0.31f, 0.31f, 0.03f, null, null, null);
        part(world, corner, node, Material.POLISHED_BLACKSTONE, cx - 0.11f, cy - 0.11f, z + 0.01f, 0.22f, 0.22f, 0.02f, null, null, null);
        int segments = 6;
        float radius = 0.135f;
        for (int i = 0; i < segments; i++) {
            double angle = i * Math.PI * 2 / segments;
            float size = 0.05f;
            float px = cx + (float) Math.cos(angle) * radius - size / 2f;
            float py = cy + (float) Math.sin(angle) * radius - size / 2f;
            part(world, corner, node, Material.BLACK_CONCRETE, px, py, z + 0.02f, size, size, 0.025f, null, null, null);
        }
        Vector3f pivot = new Vector3f(cx, cy, z + 0.04f);
        for (int i = 0; i < 4; i++) {
            float base = (float) (i * Math.PI / 2 + 0.35);
            BlockDisplay blade = part(world, corner, node, bladeDust(node.dust), cx - 0.012f, cy + 0.028f, z + 0.032f,
                    0.024f, 0.105f, 0.016f, new Quaternionf().rotateZ(base), pivot, BLADE);
            blades.add(new Blade(node.id, blade.getUniqueId(),
                    cx - 0.012f, cy + 0.028f, z + 0.032f, 0.024f, 0.105f, 0.016f,
                    cx, cy, z + 0.04f, base, -1));
        }
        part(world, corner, node, Material.IRON_BLOCK, cx - 0.028f, cy - 0.028f, z + 0.03f, 0.056f, 0.056f, 0.03f, null, null, null);
    }

    void bayLights(MinerNodes.Node node) {
        World world = node.world();
        if (world == null) return;
        for (UUID id : node.displays) {
            if (!(world.getEntity(id) instanceof BlockDisplay display)) continue;
            for (int i = 0; i < MinerNodes.Node.RACK_SLOTS; i++) {
                if (!display.getScoreboardTags().contains("miner-bay-" + i)) continue;
                boolean on = i < node.bays && node.bayOn[i];
                display.setBlock((on ? Material.LIME_CONCRETE : Material.RED_CONCRETE).createBlockData());
                display.setBrightness(on ? new Display.Brightness(15, 15) : null);
            }
        }
    }

    private void rack(World world, Location corner, MinerNodes.Node node) {
        Material head = node.bays == 0 ? Material.GRAY_CONCRETE : node.powered ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        post(world, corner, node, 0.06f, 0.10f);
        post(world, corner, node, 0.88f, 0.10f);
        post(world, corner, node, 0.06f, 0.84f);
        post(world, corner, node, 0.88f, 0.84f);
        part(world, corner, node, Material.IRON_BLOCK, 0.06f, 0f, 0.08f, 0.88f, 0.05f, 0.84f, null, null, null);
        part(world, corner, node, Material.IRON_BLOCK, 0.06f, 1.90f, 0.08f, 0.88f, 0.07f, 0.84f, null, null, null);
        part(world, corner, node, Material.BLACK_CONCRETE, 0.13f, 0.08f, 0.125f, 0.74f, 1.78f, 0.035f, null, null, null);
        part(world, corner, node, Material.POLISHED_DEEPSLATE, 0.065f, 0.07f, 0.17f, 0.05f, 1.80f, 0.66f, null, null, null);
        part(world, corner, node, Material.POLISHED_DEEPSLATE, 0.885f, 0.07f, 0.17f, 0.05f, 1.80f, 0.66f, null, null, null);
        part(world, corner, node, head, 0.40f, 1.78f, 0.82f, 0.20f, 0.08f, 0.04f, null, null, LED);
        for (int bay = 0; bay < MinerNodes.Node.RACK_SLOTS; bay++) {
            float y = 0.16f + bay * 0.42f;
            if (bay >= node.bays) {
                part(world, corner, node, Material.BLACK_CONCRETE, 0.19f, y + 0.025f, 0.26f, 0.62f, 0.25f, 0.46f, null, null, null);
                part(world, corner, node, Material.IRON_BLOCK, 0.14f, y, 0.20f, 0.03f, 0.30f, 0.64f, null, null, null);
                part(world, corner, node, Material.IRON_BLOCK, 0.83f, y, 0.20f, 0.03f, 0.30f, 0.64f, null, null, null);
                continue;
            }
            sled(world, corner, node, bay, y);
        }
        bayLights(node);
        rackDoor(world, corner, node);
    }

    private void rackDoor(World world, Location corner, MinerNodes.Node node) {
        boolean open = node.door != 1;
        Quaternionf swing = open ? new Quaternionf().rotateY(-1.7f) : null;
        Vector3f hinge = new Vector3f(0.08f, 0f, 0.94f);
        part(world, corner, node, Material.IRON_BLOCK, 0.055f, 0.28f, 0.90f, 0.03f, 0.08f, 0.04f, null, null, null);
        part(world, corner, node, Material.IRON_BLOCK, 0.055f, 1.55f, 0.90f, 0.03f, 0.08f, 0.04f, null, null, null);
        leaf(world, corner, node, Material.POLISHED_BLACKSTONE, 0.06f, 0.10f, 0.905f, 0.04f, 1.74f, 0.04f, swing, hinge);
        leaf(world, corner, node, Material.POLISHED_BLACKSTONE, 0.90f, 0.10f, 0.905f, 0.04f, 1.74f, 0.04f, swing, hinge);
        leaf(world, corner, node, Material.POLISHED_BLACKSTONE, 0.06f, 0.10f, 0.905f, 0.88f, 0.045f, 0.04f, swing, hinge);
        leaf(world, corner, node, Material.POLISHED_BLACKSTONE, 0.06f, 1.795f, 0.905f, 0.88f, 0.045f, 0.04f, swing, hinge);
        window(world, corner, node, 0.08f, 0.13f, 0.93f, 0.84f, 1.68f, swing, hinge, 0.04f);
        leaf(world, corner, node, Material.COPPER_BLOCK, 0.84f, 0.86f, 0.94f, 0.025f, 0.18f, 0.035f, swing, hinge);
        leaf(world, corner, node, Material.COPPER_BLOCK, 0.80f, 0.93f, 0.945f, 0.07f, 0.025f, 0.03f, swing, hinge);
    }

    private void leaf(World world, Location corner, MinerNodes.Node node, Material material,
                      float x, float y, float z, float sx, float sy, float sz, Quaternionf swing, Vector3f hinge) {
        part(world, corner, node, material, x, y, z, sx, sy, sz, swing, swing == null ? null : hinge, null);
    }

    private void window(World world, Location corner, MinerNodes.Node node,
                        float x, float y, float plane, float sx, float sy, Quaternionf swing, Vector3f hinge, float depth) {
        org.bukkit.block.data.MultipleFacing pane = (org.bukkit.block.data.MultipleFacing) Material.GLASS_PANE.createBlockData();
        pane.setFace(BlockFace.EAST, true);
        pane.setFace(BlockFace.WEST, true);
        Pose pose = pose(x, y, plane - depth * 0.5f, sx, sy, depth, swing, swing == null ? null : hinge);
        spawn(world, corner, node, pane, pose.translation, pose.rotation, sx, sy, depth, null);
    }

    private void post(World world, Location corner, MinerNodes.Node node, float x, float z) {
        part(world, corner, node, Material.IRON_BLOCK, x, 0.05f, z, 0.06f, 1.85f, 0.06f, null, null, null);
    }

    private void sled(World world, Location corner, MinerNodes.Node node, int bay, float y) {
        boolean on = node.bayOn[bay];
        part(world, corner, node, Material.POLISHED_DEEPSLATE, 0.16f, y, 0.18f, 0.68f, 0.28f, 0.62f, null, null, null);
        part(world, corner, node, Material.BLACK_CONCRETE, 0.18f, y + 0.03f, 0.78f, 0.64f, 0.22f, 0.035f, null, null, null);
        part(world, corner, node, lipDust(node.dust), 0.18f, y + 0.22f, 0.80f, 0.64f, 0.025f, 0.02f, null, null, DUST + "lip" + bay);
        part(world, corner, node, filmDust(node.dust, 1), 0.20f, y + 0.05f, 0.845f, 0.42f, 0.16f, 0.016f, null, null, DUST + "bay" + bay);
        part(world, corner, node, on ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                0.70f, y + 0.08f, 0.80f, 0.06f, 0.06f, 0.025f, null, null, "miner-bay-" + bay);
        part(world, corner, node, Material.IRON_BLOCK, 0.78f, y + 0.07f, 0.82f, 0.05f, 0.14f, 0.04f, null, null, null);
        miniFan(world, corner, node, 0.32f, y + 0.13f, 0.80f, bay);
        miniFan(world, corner, node, 0.50f, y + 0.13f, 0.80f, bay);
    }

    private void miniFan(World world, Location corner, MinerNodes.Node node, float cx, float cy, float z, int bay) {
        part(world, corner, node, Material.BLACK_CONCRETE, cx - 0.07f, cy - 0.07f, z, 0.14f, 0.14f, 0.018f, null, null, null);
        part(world, corner, node, Material.POLISHED_BLACKSTONE, cx - 0.045f, cy - 0.045f, z + 0.012f, 0.09f, 0.09f, 0.012f, null, null, null);
        Vector3f pivot = new Vector3f(cx, cy, z + 0.028f);
        for (int i = 0; i < 3; i++) {
            float base = (float) (i * Math.PI * 2 / 3);
            BlockDisplay blade = part(world, corner, node, bladeDust(node.dust), cx - 0.006f, cy + 0.012f, z + 0.02f,
                    0.012f, 0.05f, 0.008f, new Quaternionf().rotateZ(base), pivot, BLADE);
            blades.add(new Blade(node.id, blade.getUniqueId(),
                    cx - 0.006f, cy + 0.012f, z + 0.02f, 0.012f, 0.05f, 0.008f,
                    cx, cy, z + 0.028f, base, bay));
        }
        part(world, corner, node, Material.IRON_BLOCK, cx - 0.016f, cy - 0.016f, z + 0.018f, 0.032f, 0.032f, 0.016f, null, null, null);
    }

    private void foot(World world, Location corner, MinerNodes.Node node, float x, float z) {
        part(world, corner, node, Material.BLACK_CONCRETE, x, 0f, z, 0.08f, 0.06f, 0.08f, null, null, null);
    }

    private void panel(World world, Location corner, MinerNodes.Node node) {
        int tier = Math.max(1, Math.min(4, node.tier));
        Quaternionf tilt = new Quaternionf().rotateX(0.40f);
        Vector3f hinge = new Vector3f(0.5f, 0.30f, 0.90f);
        Material frame = switch (tier) {
            case 2 -> Material.COPPER_BLOCK;
            case 3, 4 -> Material.GOLD_BLOCK;
            default -> Material.IRON_BLOCK;
        };
        Material cell = switch (tier) {
            case 2 -> Material.CYAN_CONCRETE;
            case 3 -> Material.LIGHT_BLUE_CONCRETE;
            case 4 -> Material.SEA_LANTERN;
            default -> Material.BLUE_CONCRETE;
        };
        Material cellDark = switch (tier) {
            case 2 -> Material.CYAN_TERRACOTTA;
            case 3 -> Material.LIGHT_BLUE_TERRACOTTA;
            case 4 -> Material.PRISMARINE;
            default -> Material.BLUE_TERRACOTTA;
        };
        Material bus = tier <= 1 ? Material.WHITE_CONCRETE : tier == 2 ? Material.COPPER_BLOCK : Material.GOLD_BLOCK;
        Material glass = tier >= 3 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.GLASS;
        Material led = node.powered ? Material.YELLOW_CONCRETE : Material.GRAY_CONCRETE;
        part(world, corner, node, Material.IRON_BLOCK, 0.08f, 0f, 0.12f, 0.05f, 0.03f, 0.74f, null, null, null);
        part(world, corner, node, Material.IRON_BLOCK, 0.87f, 0f, 0.12f, 0.05f, 0.03f, 0.74f, null, null, null);
        stand(world, corner, node, 0.09f, 0.78f, 0.28f);
        stand(world, corner, node, 0.86f, 0.78f, 0.28f);
        stand(world, corner, node, 0.11f, 0.16f, 0.13f);
        stand(world, corner, node, 0.84f, 0.16f, 0.13f);
        part(world, corner, node, Material.BLACK_CONCRETE, 0.36f, 0.08f, 0.56f, 0.28f, 0.07f, 0.14f, null, null, null);
        for (int fin = 0; fin < tier; fin++) {
            part(world, corner, node, Material.COPPER_BLOCK, 0.38f, 0.09f + fin * 0.012f, 0.69f, 0.24f, 0.008f, 0.012f, null, null, null);
        }
        part(world, corner, node, led, 0.62f, 0.095f, 0.60f, 0.02f, 0.03f, 0.05f, null, null, LED);
        float y = 0.24f;
        part(world, corner, node, frame, 0.06f, y, 0.12f, 0.05f, 0.07f, 0.76f, tilt, hinge, null);
        part(world, corner, node, frame, 0.89f, y, 0.12f, 0.05f, 0.07f, 0.76f, tilt, hinge, null);
        part(world, corner, node, frame, 0.06f, y, 0.12f, 0.88f, 0.07f, 0.05f, tilt, hinge, null);
        part(world, corner, node, frame, 0.06f, y, 0.83f, 0.88f, 0.07f, 0.05f, tilt, hinge, null);
        part(world, corner, node, Material.BLACK_CONCRETE, 0.11f, y + 0.02f, 0.17f, 0.78f, 0.018f, 0.66f, tilt, hinge, null);
        float cw = 0.118f;
        float cd = 0.16f;
        for (int col = 0; col < 5; col++) {
            for (int row = 0; row < 3; row++) {
                Material face = (col + row) % 2 == 0 ? cell : cellDark;
                part(world, corner, node, face,
                        0.145f + col * (cw + 0.016f), y + 0.04f, 0.20f + row * (cd + 0.018f),
                        cw, 0.016f, cd, tilt, hinge, tier == 4 ? GLASS : null);
            }
        }
        for (int i = 0; i < 2; i++) {
            part(world, corner, node, bus, 0.32f + i * 0.28f, y + 0.058f, 0.19f, 0.012f, 0.01f, 0.62f, tilt, hinge, null);
        }
        for (int i = 0; i < 4; i++) {
            part(world, corner, node, bus, 0.14f, y + 0.066f, 0.20f + i * 0.178f, 0.72f, 0.008f, 0.012f, tilt, hinge, null);
        }
        part(world, corner, node, glass, 0.10f, y + 0.078f, 0.16f, 0.80f, 0.012f, 0.68f, tilt, hinge, GLASS);
        screw(world, corner, node, 0.07f, y + 0.03f, 0.13f, tilt, hinge);
        screw(world, corner, node, 0.90f, y + 0.03f, 0.13f, tilt, hinge);
        screw(world, corner, node, 0.07f, y + 0.03f, 0.83f, tilt, hinge);
        screw(world, corner, node, 0.90f, y + 0.03f, 0.83f, tilt, hinge);
        if (tier >= 3) {
            part(world, corner, node, Material.WHITE_CONCRETE, 0.055f, y + 0.05f, 0.16f, 0.018f, 0.03f, 0.66f, tilt, hinge, null);
            part(world, corner, node, Material.WHITE_CONCRETE, 0.927f, y + 0.05f, 0.16f, 0.018f, 0.03f, 0.66f, tilt, hinge, null);
        }
        if (tier >= 4) {
            part(world, corner, node, Material.GOLD_BLOCK, 0.18f, y + 0.09f, 0.18f, 0.64f, 0.012f, 0.03f, tilt, hinge, GLASS);
        }
    }

    private void screw(World world, Location corner, MinerNodes.Node node, float x, float y, float z,
                       Quaternionf tilt, Vector3f hinge) {
        part(world, corner, node, Material.GOLD_BLOCK, x, y, z, 0.028f, 0.028f, 0.028f, tilt, hinge, null);
    }

    private void stand(World world, Location corner, MinerNodes.Node node, float x, float z, float height) {
        part(world, corner, node, Material.IRON_BLOCK, x, 0.02f, z, 0.05f, height, 0.05f, null, null, null);
        part(world, corner, node, Material.IRON_BLOCK, x - 0.02f, 0f, z - 0.02f, 0.09f, 0.025f, 0.09f, null, null, null);
    }

    void boards(MinerMarket market) {
        for (MinerNodes.Node node : nodes.all()) {
            if (node.type == NodeType.TERMINAL) terminalBoard(node);
        }
    }

    void terminalBoard(MinerNodes.Node node) {
        World world = node.world();
        if (world == null) return;
        Component text = boardText(node);
        Material trend = trendMaterial(node);
        for (UUID id : node.displays) {
            Entity entity = world.getEntity(id);
            if (entity instanceof TextDisplay board && entity.getScoreboardTags().contains(BOARD)) board.text(text);
            if (entity instanceof BlockDisplay block && entity.getScoreboardTags().contains(TREND)) {
                block.setBlock(trend.createBlockData());
                block.setBrightness(node.powered ? new Display.Brightness(15, 15) : null);
            }
        }
    }

    private Component boardText(MinerNodes.Node node) {
        if (!node.powered) return Component.text(Lang.text(null, "board.no-power"), NamedTextColor.RED);
        MinerMarket market = MinerMarket.get();
        if (market == null) return Component.text("—", NamedTextColor.GRAY);
        return market.screen();
    }

    private Material trendMaterial(MinerNodes.Node node) {
        if (!node.powered || MinerMarket.get() == null) return Material.GRAY_CONCRETE;
        return MinerMarket.get().rising() ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
    }

    void cells(MinerNodes.Node node) {
        int filled = MinerNodes.bars(node.charge, node.tier);
        int packed = filled * 10 + node.progress + node.tier * 100;
        if (packed == node.meter) return;
        node.meter = packed;
        World world = node.world();
        if (world == null) return;
        Material fill = MinerNodes.barMaterial(node.charge, node.tier);
        Material led = batteryLed(node);
        boolean live = node.progress == 1 || node.progress == 2 || node.progress == 4 || node.charge > 0;
        for (UUID id : node.displays) {
            if (!(world.getEntity(id) instanceof BlockDisplay display)) continue;
            for (int i = 0; i < 4; i++) {
                if (!display.getScoreboardTags().contains(CELL + i)) continue;
                boolean on = i < filled;
                display.setBlock((on ? fill : Material.BLACK_CONCRETE).createBlockData());
                display.setBrightness(on ? new Display.Brightness(15, 15) : null);
            }
            if (display.getScoreboardTags().contains(LED)) {
                display.setBlock(led.createBlockData());
                display.setBrightness(live ? new Display.Brightness(15, 15) : null);
            }
        }
    }

    private static Material batteryLed(MinerNodes.Node node) {
        return switch (node.progress) {
            case 1 -> Material.YELLOW_CONCRETE;
            case 2 -> Material.LIME_CONCRETE;
            case 3 -> Material.LIGHT_BLUE_CONCRETE;
            case 4 -> Material.MAGENTA_CONCRETE;
            default -> node.charge > 0 ? Material.ORANGE_CONCRETE : Material.GRAY_CONCRETE;
        };
    }

    private void battery(World world, Location corner, MinerNodes.Node node) {
        boolean admin = MinerNodes.adminTier(node.tier);
        int tier = MinerNodes.normalizeBattery(node.tier);
        Material shell = switch (tier) {
            case 2 -> Material.WAXED_COPPER_BLOCK;
            case 3 -> Material.WAXED_OXIDIZED_COPPER;
            case 4 -> Material.NETHERITE_BLOCK;
            case 5 -> Material.CRYING_OBSIDIAN;
            default -> Material.POLISHED_DEEPSLATE;
        };
        Material band = admin || tier >= 4 ? Material.AMETHYST_BLOCK : tier == 3 ? Material.GOLD_BLOCK : Material.COPPER_BLOCK;
        Material plus = admin ? Material.AMETHYST_BLOCK : Material.GOLD_BLOCK;
        Material minus = admin ? Material.AMETHYST_BLOCK : Material.BLACK_CONCRETE;
        Material frame = admin ? Material.AMETHYST_BLOCK : Material.IRON_BLOCK;
        float[] footX = {0.12f, 0.78f};
        float[] footZ = {0.16f, 0.72f};
        for (float fx : footX) {
            for (float fz : footZ) {
                part(world, corner, node, Material.BLACK_CONCRETE, fx, 0f, fz, 0.09f, 0.035f, 0.09f, null, null, null);
            }
        }
        part(world, corner, node, Material.IRON_BLOCK, 0.08f, 0.02f, 0.12f, 0.84f, 0.04f, 0.74f, null, null, null);
        part(world, corner, node, shell, 0.14f, 0.06f, 0.18f, 0.72f, 0.46f, 0.56f, null, null, null);
        part(world, corner, node, band, 0.12f, 0.24f, 0.16f, 0.76f, 0.045f, 0.62f, null, null, null);
        part(world, corner, node, Material.IRON_BLOCK, 0.11f, 0.52f, 0.15f, 0.78f, 0.045f, 0.66f, null, null, null);
        part(world, corner, node, Material.POLISHED_BLACKSTONE, 0.18f, 0.12f, 0.68f, 0.64f, 0.38f, 0.045f, null, null, null);
        part(world, corner, node, Material.BLACK_CONCRETE, 0.22f, 0.15f, 0.72f, 0.42f, 0.32f, 0.02f, null, null, null);
        for (int i = 0; i < 4; i++) {
            float y = 0.175f + i * 0.075f;
            part(world, corner, node, frame, 0.24f, y, 0.735f, 0.38f, 0.055f, 0.025f, null, null, null);
            part(world, corner, node, Material.BLACK_CONCRETE, 0.26f, y + 0.01f, 0.755f, 0.34f, 0.035f, 0.02f, null, null, CELL + i);
        }
        part(world, corner, node, Material.BLACK_CONCRETE, 0.64f, 0.20f, 0.725f, 0.14f, 0.22f, 0.03f, null, null, null);
        part(world, corner, node, Material.GRAY_CONCRETE, 0.665f, 0.28f, 0.75f, 0.09f, 0.07f, 0.02f, null, null, LED);
        part(world, corner, node, Material.COPPER_BLOCK, 0.22f, 0.08f, 0.74f, 0.56f, 0.035f, 0.10f, null, null, null);
        part(world, corner, node, Material.COPPER_BLOCK, 0.30f, 0.565f, 0.40f, 0.08f, 0.08f, 0.08f, null, null, null);
        part(world, corner, node, Material.IRON_BLOCK, 0.62f, 0.565f, 0.40f, 0.08f, 0.08f, 0.08f, null, null, null);
        part(world, corner, node, plus, 0.29f, 0.645f, 0.39f, 0.10f, 0.022f, 0.10f, null, null, null);
        part(world, corner, node, minus, 0.61f, 0.645f, 0.39f, 0.10f, 0.022f, 0.10f, null, null, null);
        part(world, corner, node, band, 0.38f, 0.568f, 0.42f, 0.24f, 0.018f, 0.04f, null, null, null);
        for (int i = 0; i < 3; i++) {
            float gy = 0.14f + i * 0.12f;
            part(world, corner, node, band, 0.09f, gy, 0.30f, 0.06f, 0.03f, 0.28f, null, null, null);
            part(world, corner, node, band, 0.85f, gy, 0.30f, 0.06f, 0.03f, 0.28f, null, null, null);
        }
        part(world, corner, node, Material.GRAY_CONCRETE, 0.20f, 0.44f, 0.72f, 0.03f, 0.03f, 0.02f, null, null, null);
        part(world, corner, node, Material.GRAY_CONCRETE, 0.77f, 0.44f, 0.72f, 0.03f, 0.03f, 0.02f, null, null, null);
        part(world, corner, node, Material.GRAY_CONCRETE, 0.20f, 0.14f, 0.72f, 0.03f, 0.03f, 0.02f, null, null, null);
        part(world, corner, node, Material.GRAY_CONCRETE, 0.77f, 0.14f, 0.72f, 0.03f, 0.03f, 0.02f, null, null, null);
        node.meter = -1;
        cells(node);
    }

    private void pole(World world, Location corner, MinerNodes.Node node) {
        part(world, corner, node, Material.GRAY_CONCRETE, 0.41f, 0f, 0.41f, 0.18f, 0.035f, 0.18f, null, null, null);
        part(world, corner, node, Material.LIGHT_GRAY_CONCRETE, 0.465f, 0.02f, 0.465f, 0.07f, 1.86f, 0.07f, null, null, null);
        part(world, corner, node, Material.WAXED_OXIDIZED_COPPER, 0.16f, 1.62f, 0.47f, 0.68f, 0.035f, 0.06f, null, null, null);
        float[] caps = {0.20f, 0.475f, 0.75f};
        for (float x : caps) {
            part(world, corner, node, Material.PRISMARINE, x, 1.655f, 0.475f, 0.05f, 0.08f, 0.05f, null, null, null);
            part(world, corner, node, Material.CYAN_CONCRETE, x + 0.012f, 1.735f, 0.487f, 0.026f, 0.028f, 0.026f, null, null, null);
        }
        float[] lamps = {0.18f, 0.74f};
        for (int i = 0; i < lamps.length; i++) {
            float x = lamps[i];
            part(world, corner, node, Material.BARRIER, x + 0.03f, 1.50f, 0.49f, 0.02f, 0.12f, 0.02f, null, null, BULB + "hook" + i);
            part(world, corner, node, Material.BARRIER, x, 1.36f, 0.46f, 0.08f, 0.14f, 0.08f, null, null, BULB + i);
        }
        bulbSkin(node);
    }

    void bulbSkin(MinerNodes.Node node) {
        if (node.type != NodeType.POLE) return;
        World world = node.world();
        if (world == null) return;
        int hung = Math.max(0, Math.min(2, node.bulbs));
        for (UUID id : node.displays) {
            if (!(world.getEntity(id) instanceof BlockDisplay display)) continue;
            for (int i = 0; i < 2; i++) {
                boolean lit = i < hung;
                if (display.getScoreboardTags().contains(BULB + "hook" + i)) {
                    display.setBlock((lit ? Material.IRON_BLOCK : Material.BARRIER).createBlockData());
                    display.setBrightness(null);
                } else if (display.getScoreboardTags().contains(BULB + i)) {
                    display.setBlock((lit ? Material.OCHRE_FROGLIGHT : Material.BARRIER).createBlockData());
                    display.setBrightness(lit ? new Display.Brightness(15, 15) : null);
                }
            }
        }
        shine(node, hung > 0);
    }

    private void shine(MinerNodes.Node node, boolean on) {
        World world = node.world();
        if (world == null) return;
        Block block = world.getBlockAt(node.x, node.y + 1, node.z);
        if (on) {
            if (!block.getType().isAir() && block.getType() != Material.LIGHT) return;
            org.bukkit.block.data.type.Light light = (org.bukkit.block.data.type.Light) Material.LIGHT.createBlockData();
            light.setLevel(15);
            block.setBlockData(light);
        } else if (block.getType() == Material.LIGHT) {
            block.setType(Material.AIR);
        }
    }

    private void terminal(World world, Location corner, MinerNodes.Node node) {
        Material trend = MinerMarket.get() != null && MinerMarket.get().rising() ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        Quaternionf deck = new Quaternionf().rotateX(0.42f);
        Vector3f hinge = new Vector3f(0.50f, 0.40f, 0.76f);
        part(world, corner, node, Material.IRON_BLOCK, 0.06f, 0f, 0.08f, 0.88f, 0.045f, 0.84f, null, null, null);
        part(world, corner, node, Material.POLISHED_BLACKSTONE, 0.10f, 0.045f, 0.12f, 0.80f, 0.035f, 0.74f, null, null, null);
        part(world, corner, node, Material.COPPER_BLOCK, 0.12f, 0.02f, 0.84f, 0.76f, 0.025f, 0.025f, null, null, null);
        float[] footX = {0.10f, 0.82f};
        float[] footZ = {0.12f, 0.78f};
        for (float fx : footX) {
            for (float fz : footZ) {
                part(world, corner, node, Material.BLACK_CONCRETE, fx, 0f, fz, 0.08f, 0.03f, 0.08f, null, null, null);
            }
        }
        part(world, corner, node, Material.POLISHED_DEEPSLATE, 0.18f, 0.08f, 0.16f, 0.64f, 1.38f, 0.48f, null, null, null);
        part(world, corner, node, Material.IRON_BLOCK, 0.14f, 0.08f, 0.18f, 0.06f, 1.36f, 0.62f, null, null, null);
        part(world, corner, node, Material.IRON_BLOCK, 0.80f, 0.08f, 0.18f, 0.06f, 1.36f, 0.62f, null, null, null);
        part(world, corner, node, Material.IRON_BLOCK, 0.18f, 0.70f, 0.14f, 0.64f, 0.045f, 0.50f, null, null, null);
        part(world, corner, node, Material.BLACK_CONCRETE, 0.20f, 0.16f, 0.62f, 0.60f, 1.22f, 0.035f, null, null, null);
        part(world, corner, node, Material.IRON_BLOCK, 0.10f, 1.44f, 0.12f, 0.80f, 0.07f, 0.74f, null, null, null);
        part(world, corner, node, Material.POLISHED_BLACKSTONE, 0.14f, 1.40f, 0.72f, 0.72f, 0.05f, 0.12f, null, null, null);
        part(world, corner, node, Material.BLACK_CONCRETE, 0.12f, 1.00f, 0.78f, 0.07f, 0.38f, 0.16f, null, null, null);
        part(world, corner, node, Material.BLACK_CONCRETE, 0.81f, 1.00f, 0.78f, 0.07f, 0.38f, 0.16f, null, null, null);
        part(world, corner, node, Material.IRON_BLOCK, 0.47f, 1.51f, 0.46f, 0.035f, 0.14f, 0.035f, null, null, null);
        part(world, corner, node, Material.COPPER_BLOCK, 0.455f, 1.64f, 0.445f, 0.065f, 0.04f, 0.065f, null, null, null);
        glow(part(world, corner, node, trend, 0.46f, 1.68f, 0.45f, 0.055f, 0.045f, 0.055f, null, null, TREND));
        part(world, corner, node, Material.POLISHED_BLACKSTONE, 0.22f, 0.98f, 0.66f, 0.56f, 0.40f, 0.04f, null, null, null);
        part(world, corner, node, Material.CYAN_CONCRETE, 0.26f, 1.02f, 0.695f, 0.48f, 0.32f, 0.018f, null, null, GLASS);
        part(world, corner, node, Material.IRON_BLOCK, 0.255f, 1.015f, 0.70f, 0.49f, 0.012f, 0.012f, null, null, null);
        part(world, corner, node, Material.IRON_BLOCK, 0.255f, 1.328f, 0.70f, 0.49f, 0.012f, 0.012f, null, null, null);
        part(world, corner, node, Material.IRON_BLOCK, 0.255f, 1.02f, 0.70f, 0.012f, 0.32f, 0.012f, null, null, null);
        part(world, corner, node, Material.IRON_BLOCK, 0.733f, 1.02f, 0.70f, 0.012f, 0.32f, 0.012f, null, null, null);
        window(world, corner, node, 0.27f, 1.03f, 0.78f, 0.46f, 0.30f, null, null, 0.5f);
        part(world, corner, node, Material.BLACK_CONCRETE, 0.24f, 0.90f, 0.66f, 0.52f, 0.07f, 0.03f, null, null, null);
        part(world, corner, node, Material.GOLD_BLOCK, 0.28f, 0.915f, 0.688f, 0.045f, 0.04f, 0.012f, null, null, null);
        part(world, corner, node, Material.GOLD_BLOCK, 0.34f, 0.925f, 0.688f, 0.03f, 0.02f, 0.012f, null, null, null);
        part(world, corner, node, Material.GOLD_BLOCK, 0.385f, 0.915f, 0.688f, 0.045f, 0.04f, 0.012f, null, null, null);
        glow(part(world, corner, node, trend, 0.66f, 0.915f, 0.685f, 0.04f, 0.04f, 0.02f, null, null, TREND));
        part(world, corner, node, Material.GRAY_CONCRETE, 0.24f, 0.40f, 0.70f, 0.52f, 0.46f, 0.04f, deck, hinge, null);
        part(world, corner, node, Material.BLACK_CONCRETE, 0.27f, 0.43f, 0.735f, 0.46f, 0.40f, 0.012f, deck, hinge, null);
        float[] keys = {0.30f, 0.43f, 0.56f};
        float[] rows = {0.46f, 0.55f, 0.64f, 0.73f};
        Material[] bottom = {Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.RED_CONCRETE};
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < keys.length; col++) {
                Material key = row == 3 ? bottom[col] : Material.LIGHT_GRAY_CONCRETE;
                part(world, corner, node, key, keys[col], rows[row], 0.748f, 0.09f, 0.065f, 0.02f, deck, hinge, null);
            }
        }
        part(world, corner, node, Material.IRON_BLOCK, 0.22f, 0.80f, 0.655f, 0.26f, 0.09f, 0.05f, null, null, null);
        part(world, corner, node, Material.BLACK_CONCRETE, 0.24f, 0.83f, 0.70f, 0.22f, 0.018f, 0.02f, null, null, null);
        part(world, corner, node, Material.GOLD_BLOCK, 0.24f, 0.80f, 0.70f, 0.22f, 0.012f, 0.014f, null, null, null);
        Quaternionf hang = new Quaternionf().rotateX(0.55f);
        part(world, corner, node, Material.LIGHT_BLUE_CONCRETE, 0.28f, 0.80f, 0.72f, 0.14f, 0.07f, 0.012f, hang, new Vector3f(0.28f, 0.80f, 0.72f), null);
        part(world, corner, node, Material.IRON_BLOCK, 0.56f, 0.80f, 0.655f, 0.18f, 0.08f, 0.045f, null, null, null);
        part(world, corner, node, Material.BLACK_CONCRETE, 0.58f, 0.825f, 0.695f, 0.14f, 0.02f, 0.016f, null, null, null);
        Quaternionf curl = new Quaternionf().rotateX(0.85f);
        part(world, corner, node, Material.WHITE_CONCRETE, 0.60f, 0.80f, 0.73f, 0.10f, 0.09f, 0.01f, curl, new Vector3f(0.60f, 0.80f, 0.73f), null);
        part(world, corner, node, Material.COPPER_BLOCK, 0.26f, 0.12f, 0.64f, 0.48f, 0.18f, 0.10f, null, null, null);
        part(world, corner, node, Material.BLACK_CONCRETE, 0.30f, 0.16f, 0.72f, 0.40f, 0.10f, 0.05f, null, null, null);
        part(world, corner, node, Material.LIME_CONCRETE, 0.40f, 0.185f, 0.755f, 0.16f, 0.045f, 0.012f, null, null, null);
        part(world, corner, node, Material.COPPER_BLOCK, 0.30f, 0.10f, 0.74f, 0.40f, 0.025f, 0.16f, null, null, null);
        part(world, corner, node, Material.BLACK_CONCRETE, 0.34f, 0.12f, 0.78f, 0.32f, 0.02f, 0.08f, null, null, null);
        part(world, corner, node, Material.GOLD_BLOCK, 0.38f, 0.135f, 0.80f, 0.06f, 0.025f, 0.06f, null, null, null);
        part(world, corner, node, Material.GOLD_BLOCK, 0.52f, 0.135f, 0.84f, 0.05f, 0.02f, 0.05f, null, null, null);
        for (int i = 0; i < 6; i++) {
            float gy = 0.28f + i * 0.16f;
            part(world, corner, node, Material.BLACK_CONCRETE, 0.145f, gy, 0.34f, 0.012f, 0.045f, 0.28f, null, null, null);
            part(world, corner, node, Material.BLACK_CONCRETE, 0.843f, gy, 0.34f, 0.012f, 0.045f, 0.28f, null, null, null);
        }
        part(world, corner, node, Material.GRAY_CONCRETE, 0.22f, 1.34f, 0.695f, 0.025f, 0.025f, 0.02f, null, null, null);
        part(world, corner, node, Material.GRAY_CONCRETE, 0.755f, 1.34f, 0.695f, 0.025f, 0.025f, 0.02f, null, null, null);
        part(world, corner, node, Material.GRAY_CONCRETE, 0.22f, 0.16f, 0.70f, 0.025f, 0.025f, 0.02f, null, null, null);
        part(world, corner, node, Material.GRAY_CONCRETE, 0.755f, 0.16f, 0.70f, 0.025f, 0.025f, 0.02f, null, null, null);
        screen(world, corner, node);
    }

    private static void glow(BlockDisplay display) {
        display.setBrightness(new Display.Brightness(15, 15));
    }

    private void screen(World world, Location corner, MinerNodes.Node node) {
        Pose pose = pose(0.50f, 1.18f, 0.86f, 0.01f, 0.01f, 0.01f, null, null);
        Component text = boardText(node);
        TextDisplay board = world.spawn(corner, TextDisplay.class, entity -> {
            entity.text(text);
            entity.setTransformation(new Transformation(pose.translation(), new AxisAngle4f(pose.rotation()), new Vector3f(0.28f, 0.28f, 0.28f), STILL));
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setSeeThrough(false);
            entity.setShadowed(false);
            entity.setLineWidth(90);
            entity.setBackgroundColor(Color.fromARGB(210, 8, 18, 22));
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setPersistent(true);
            entity.setShadowRadius(0f);
            entity.addScoreboardTag(TAG);
            entity.addScoreboardTag(BOARD);
        });
        node.displays.add(board.getUniqueId());
    }

    private void wire(World world, Location corner, MinerNodes.Node node) {
        worldBox(world, corner, node, Material.IRON_BLOCK, 0.18f, 0f, 0.18f, 0.64f, 0.035f, 0.64f, null);
        worldBox(world, corner, node, Material.BLACK_CONCRETE, 0.32f, 0.035f, 0.32f, 0.36f, 0.14f, 0.36f, null);
        worldBox(world, corner, node, Material.COPPER_BLOCK, 0.36f, 0.175f, 0.44f, 0.28f, 0.035f, 0.12f, null);
        worldBox(world, corner, node, Material.GOLD_BLOCK, 0.38f, 0.21f, 0.47f, 0.05f, 0.03f, 0.05f, null);
        worldBox(world, corner, node, Material.GOLD_BLOCK, 0.57f, 0.21f, 0.47f, 0.05f, 0.03f, 0.05f, null);
    }

    private void curve(MinerNodes.Node a, MinerNodes.Node b, List<UUID> into, MinerNodes.Link link) {
        World world = a.world();
        if (world == null) return;
        loadSpan(world, a, b);
        if (a.type == NodeType.ASIC && b.type == NodeType.ASIC) {
            int span = Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z);
            if (span == 1) joinRack(world, a, b, into);
            else joinWhip(world, a, b, into, link);
            return;
        }
        if (a.type == NodeType.RACK && b.type == NodeType.RACK) {
            if (beside(a, b)) joinRacks(world, a, b, into);
            else groundRun(world, a, b, into, link, 0.12f);
            return;
        }
        if (a.type == NodeType.PANEL && b.type == NodeType.PANEL) {
            if (beside(a, b)) joinPanels(world, a, b, into);
            else groundRun(world, a, b, into, link, 0.06f);
            return;
        }
        Vector3f start = port(a, b);
        Vector3f end = port(b, a);
        float span = start.distance(end);
        int steps = Math.max(4, Math.min(64, Math.round(span * 5f)));
        Vector3f prev = cablePoint(a, b, 0f);
        for (int i = 1; i <= steps; i++) {
            Vector3f next = cablePoint(a, b, i / (float) steps);
            boolean tip = i <= 2 || i > steps - 2;
            UUID piece = segment(world, prev, next, tip ? Material.BLACK_CONCRETE : Material.COPPER_BLOCK, tip ? 0.055f : 0.028f);
            if (piece != null) into.add(piece);
            prev = next;
        }
        if (link == null) return;
        Vector3f at = cablePoint(a, b, 0.15f);
        BlockDisplay spark = world.spawn(a.corner(), BlockDisplay.class, entity -> {
            entity.setBlock(Material.YELLOW_CONCRETE.createBlockData());
            entity.setTransformation(new Transformation(
                    new Vector3f(at.x - a.x, at.y - a.y, at.z - a.z), STILL, new Vector3f(0.06f, 0.06f, 0.06f), STILL));
            entity.setPersistent(true);
            entity.setShadowRadius(0f);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.addScoreboardTag(TAG);
        });
        link.spark = spark.getUniqueId();
    }

    private void joinRack(World world, MinerNodes.Node a, MinerNodes.Node b, List<UUID> into) {
        boolean vertical = a.x == b.x && a.z == b.z;
        Vector3f front = frontOf(a);
        Vector3f to = new Vector3f(b.x - a.x, 0f, b.z - a.z);
        boolean side = !vertical && to.lengthSquared() > 0.01f && Math.abs(new Vector3f(to).normalize().dot(front)) < 0.55f;
        if (vertical) {
            MinerNodes.Node lower = a.y <= b.y ? a : b;
            float px = lower.x + 0.5f - front.x * 0.40f;
            float pz = lower.z + 0.5f - front.z * 0.40f;
            piece(world, into, px - 0.035f, lower.y + 0.62f, pz - 0.035f, 0.07f, 0.86f, 0.07f, Material.IRON_BLOCK);
            piece(world, into, px - 0.07f, lower.y + 0.76f, pz - 0.07f, 0.14f, 0.05f, 0.14f, Material.BLACK_CONCRETE);
            piece(world, into, px - 0.07f, lower.y + 1.18f, pz - 0.07f, 0.14f, 0.05f, 0.14f, Material.BLACK_CONCRETE);
        } else if (side) {
            float mx = (a.x + b.x) * 0.5f + 0.5f;
            float mz = (a.z + b.z) * 0.5f + 0.5f;
            float y = Math.min(a.y, b.y);
            if (a.z == b.z) {
                piece(world, into, mx - 0.08f, y + 0.22f, mz - 0.15f, 0.16f, 0.42f, 0.30f, Material.IRON_BLOCK);
                piece(world, into, mx - 0.045f, y + 0.30f, mz - 0.09f, 0.09f, 0.24f, 0.18f, Material.BLACK_CONCRETE);
            } else {
                piece(world, into, mx - 0.15f, y + 0.22f, mz - 0.08f, 0.30f, 0.42f, 0.16f, Material.IRON_BLOCK);
                piece(world, into, mx - 0.09f, y + 0.30f, mz - 0.045f, 0.18f, 0.24f, 0.09f, Material.BLACK_CONCRETE);
            }
            piece(world, into, mx - 0.05f, y + 0.675f, mz - 0.05f, 0.10f, 0.04f, 0.10f, Material.RED_CONCRETE);
            float sx = mx + front.x * 0.17f;
            float sz = mz + front.z * 0.17f;
            piece(world, into, sx - 0.025f, y + 0.32f, sz - 0.025f, 0.05f, 0.05f, 0.05f, Material.GOLD_BLOCK);
            piece(world, into, sx - 0.025f, y + 0.48f, sz - 0.025f, 0.05f, 0.05f, 0.05f, Material.GOLD_BLOCK);
        }
        Vector3f back = new Vector3f(front).mul(-0.24f);
        Vector3f from = worldLocal(a, pin(a, b), 0.28f, 0.04f);
        Vector3f toPort = worldLocal(b, pin(b, a), 0.28f, 0.04f);
        layCord(world, into, from, toPort, back, vertical ? 0.06f : 0.03f, 6);
    }

    private void joinRacks(World world, MinerNodes.Node a, MinerNodes.Node b, List<UUID> into) {
        if (a.x == b.x && a.z == b.z) {
            float x = a.x + 0.42f;
            float z = a.z + 0.08f;
            float y0 = Math.min(a.y, b.y) + 0.85f;
            piece(world, into, x, y0, z, 0.16f, 1.15f, 0.10f, Material.IRON_BLOCK);
            piece(world, into, x + 0.04f, y0 + 0.08f, z + 0.02f, 0.08f, 0.98f, 0.05f, Material.COPPER_BLOCK, true);
            piece(world, into, x - 0.02f, y0 + 0.15f, z - 0.02f, 0.20f, 0.06f, 0.14f, Material.BLACK_CONCRETE);
            piece(world, into, x - 0.02f, y0 + 0.92f, z - 0.02f, 0.20f, 0.06f, 0.14f, Material.BLACK_CONCRETE);
            return;
        }
        float y = Math.min(a.y, b.y) + 0.02f;
        layBus(world, into, a, b, y, 0.22f);
        boot(world, into, a.x + 0.5f, a.y + 0.02f, a.z + 0.5f, 0.26f);
        boot(world, into, b.x + 0.5f, b.y + 0.02f, b.z + 0.5f, 0.26f);
        if (a.y != b.y) riser(world, into, a.y > b.y ? a : b, y);
        if (a.y == b.y && a.x != b.x && a.z != b.z) return;
        if (a.y != b.y) return;
        Vector3f to = new Vector3f(b.x - a.x, 0f, b.z - a.z);
        if (to.lengthSquared() < 0.01f) return;
        to.normalize();
        if (Math.abs(to.dot(frontOf(a))) > 0.7f || Math.abs(to.dot(frontOf(b))) > 0.7f) return;
        float base = a.y;
        if (a.z == b.z) {
            float x = Math.min(a.x, b.x) + 1f;
            float z = a.z + 0.5f;
            piece(world, into, x - 0.04f, base + 0.42f, z - 0.09f, 0.08f, 1.32f, 0.18f, Material.IRON_BLOCK);
            piece(world, into, x - 0.02f, base + 0.55f, z - 0.03f, 0.04f, 1.05f, 0.06f, Material.COPPER_BLOCK, true);
            for (int i = 0; i < 3; i++) {
                piece(world, into, x - 0.035f, base + 0.64f + i * 0.36f, z - 0.12f, 0.07f, 0.07f, 0.05f, Material.GOLD_BLOCK);
            }
        } else if (a.x == b.x) {
            float z = Math.min(a.z, b.z) + 1f;
            float x = a.x + 0.5f;
            piece(world, into, x - 0.09f, base + 0.42f, z - 0.04f, 0.18f, 1.32f, 0.08f, Material.IRON_BLOCK);
            piece(world, into, x - 0.03f, base + 0.55f, z - 0.02f, 0.06f, 1.05f, 0.04f, Material.COPPER_BLOCK, true);
            for (int i = 0; i < 3; i++) {
                piece(world, into, x - 0.12f, base + 0.64f + i * 0.36f, z - 0.035f, 0.05f, 0.07f, 0.07f, Material.GOLD_BLOCK);
            }
        }
    }

    private void joinPanels(World world, MinerNodes.Node a, MinerNodes.Node b, List<UUID> into) {
        if (a.x == b.x && a.z == b.z) {
            float y0 = Math.min(a.y, b.y) + 0.04f;
            piece(world, into, a.x + 0.46f, y0, a.z + 0.46f, 0.08f, Math.abs(a.y - b.y) + 0.12f, 0.08f, Material.IRON_BLOCK);
            piece(world, into, a.x + 0.48f, y0 + 0.02f, a.z + 0.48f, 0.04f, Math.abs(a.y - b.y) + 0.06f, 0.04f, Material.COPPER_BLOCK, true);
            return;
        }
        float y = Math.min(a.y, b.y) + 0.02f;
        float ax = a.x + 0.5f;
        float az = a.z + 0.5f;
        float bx = b.x + 0.5f;
        float bz = b.z + 0.5f;
        if (a.x != b.x && a.z != b.z) {
            skirt(world, into, ax, az, bx, az, y);
            skirt(world, into, bx, az, bx, bz, y);
            piece(world, into, bx - 0.05f, y, az - 0.05f, 0.10f, 0.035f, 0.10f, Material.IRON_BLOCK);
            piece(world, into, bx - 0.025f, y + 0.02f, az - 0.025f, 0.05f, 0.015f, 0.05f, Material.COPPER_BLOCK, true);
        } else {
            skirt(world, into, ax, az, bx, bz, y);
        }
        if (a.y != b.y) {
            MinerNodes.Node high = a.y > b.y ? a : b;
            float h = high.y - y;
            piece(world, into, high.x + 0.46f, y, high.z + 0.46f, 0.08f, h + 0.06f, 0.08f, Material.IRON_BLOCK);
            piece(world, into, high.x + 0.48f, y + 0.02f, high.z + 0.48f, 0.04f, h, 0.04f, Material.COPPER_BLOCK, true);
        }
    }

    private void skirt(World world, List<UUID> into, float x1, float z1, float x2, float z2, float y) {
        boolean alongX = Math.abs(x2 - x1) >= Math.abs(z2 - z1);
        float from = alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        float to = alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        float span = to - from;
        if (span < 0.05f) return;
        float start = from + 0.06f;
        float len = Math.max(0.2f, span - 0.12f);
        float cross = alongX ? (z1 + z2) * 0.5f : (x1 + x2) * 0.5f;
        if (alongX) {
            piece(world, into, start, y, cross - 0.045f, len, 0.028f, 0.09f, Material.BLACK_CONCRETE);
            piece(world, into, start + 0.04f, y + 0.026f, cross - 0.018f, Math.max(0.1f, len - 0.08f), 0.016f, 0.036f, Material.COPPER_BLOCK, true);
        } else {
            piece(world, into, cross - 0.045f, y, start, 0.09f, 0.028f, len, Material.BLACK_CONCRETE);
            piece(world, into, cross - 0.018f, y + 0.026f, start + 0.04f, 0.036f, 0.016f, Math.max(0.1f, len - 0.08f), Material.COPPER_BLOCK, true);
        }
    }

    private void layBus(World world, List<UUID> into, MinerNodes.Node a, MinerNodes.Node b, float y, float wide) {
        float ax = a.x + 0.5f;
        float az = a.z + 0.5f;
        float bx = b.x + 0.5f;
        float bz = b.z + 0.5f;
        if (a.x != b.x && a.z != b.z) {
            duct(world, into, ax, az, bx, az, y, wide);
            duct(world, into, bx, az, bx, bz, y, wide);
            piece(world, into, bx - 0.08f, y, az - 0.08f, 0.16f, 0.05f, 0.16f, Material.IRON_BLOCK);
            piece(world, into, bx - 0.04f, y + 0.02f, az - 0.04f, 0.08f, 0.025f, 0.08f, Material.COPPER_BLOCK, true);
            return;
        }
        duct(world, into, ax, az, bx, bz, y, wide);
    }

    private void duct(World world, List<UUID> into, float x1, float z1, float x2, float z2, float y, float wide) {
        boolean alongX = Math.abs(x2 - x1) >= Math.abs(z2 - z1);
        float from = alongX ? Math.min(x1, x2) : Math.min(z1, z2);
        float to = alongX ? Math.max(x1, x2) : Math.max(z1, z2);
        float span = to - from;
        if (span < 0.05f) return;
        float inset = Math.min(0.10f, span * 0.2f);
        float start = from + inset;
        float len = Math.max(0.16f, span - inset * 2f);
        float cross = alongX ? (z1 + z2) * 0.5f : (x1 + x2) * 0.5f;
        float half = wide * 0.5f;
        if (alongX) {
            piece(world, into, start, y, cross - half, len, 0.05f, 0.02f, Material.BLACK_CONCRETE);
            piece(world, into, start, y, cross + half - 0.02f, len, 0.05f, 0.02f, Material.BLACK_CONCRETE);
            piece(world, into, start, y, cross - half + 0.02f, len, 0.02f, wide - 0.04f, Material.IRON_BLOCK);
            piece(world, into, start + 0.04f, y + 0.022f, cross - 0.02f, Math.max(0.08f, len - 0.08f), 0.02f, 0.04f, Material.COPPER_BLOCK, true);
        } else {
            piece(world, into, cross - half, y, start, 0.02f, 0.05f, len, Material.BLACK_CONCRETE);
            piece(world, into, cross + half - 0.02f, y, start, 0.02f, 0.05f, len, Material.BLACK_CONCRETE);
            piece(world, into, cross - half + 0.02f, y, start, wide - 0.04f, 0.02f, len, Material.IRON_BLOCK);
            piece(world, into, cross - 0.02f, y + 0.022f, start + 0.04f, 0.04f, 0.02f, Math.max(0.08f, len - 0.08f), Material.COPPER_BLOCK, true);
        }
    }

    private void boot(World world, List<UUID> into, float x, float y, float z, float height) {
        piece(world, into, x - 0.06f, y, z - 0.06f, 0.12f, height, 0.12f, Material.BLACK_CONCRETE);
        piece(world, into, x - 0.025f, y + 0.02f, z - 0.025f, 0.05f, Math.max(0.04f, height - 0.03f), 0.05f, Material.COPPER_BLOCK, true);
    }

    private void riser(World world, List<UUID> into, MinerNodes.Node high, float y) {
        float h = high.y + 0.12f - y;
        if (h < 0.08f) return;
        piece(world, into, high.x + 0.44f, y, high.z + 0.44f, 0.12f, h, 0.12f, Material.IRON_BLOCK);
        piece(world, into, high.x + 0.47f, y + 0.02f, high.z + 0.47f, 0.06f, Math.max(0.04f, h - 0.04f), 0.06f, Material.COPPER_BLOCK, true);
    }

    private void groundRun(World world, MinerNodes.Node a, MinerNodes.Node b, List<UUID> into, MinerNodes.Link link, float rise) {
        Vector3f from = lip(a, b, rise);
        Vector3f to = lip(b, a, rise);
        float span = from.distance(to);
        int steps = Math.max(4, Math.min(64, Math.round(span * 5f)));
        float ground = Math.min(a.y, b.y) + 0.035f;
        Vector3f prev = hang(from, to, ground, 0f);
        for (int i = 1; i <= steps; i++) {
            Vector3f next = hang(from, to, ground, i / (float) steps);
            boolean tip = i <= 2 || i > steps - 2;
            UUID piece = segment(world, prev, next, tip ? Material.BLACK_CONCRETE : Material.COPPER_BLOCK, tip ? 0.05f : 0.028f);
            if (piece != null) into.add(piece);
            prev = next;
        }
        if (link != null) link.spark = spark(world, a, hang(from, to, ground, 0.18f));
    }

    private static boolean beside(MinerNodes.Node a, MinerNodes.Node b) {
        return Math.abs(a.x - b.x) <= 1 && Math.abs(a.y - b.y) <= 1 && Math.abs(a.z - b.z) <= 1;
    }

    private static Vector3f lip(MinerNodes.Node node, MinerNodes.Node other, float rise) {
        Vector3f center = new Vector3f(node.x + 0.5f, node.y + rise, node.z + 0.5f);
        Vector3f flat = new Vector3f(other.x - node.x, 0f, other.z - node.z);
        if (flat.lengthSquared() < 0.04f) {
            center.y = node.y + (other.y > node.y ? 0.22f : 0.08f);
            return center;
        }
        return center.add(flat.normalize().mul(0.46f));
    }

    private static Vector3f hang(Vector3f from, Vector3f to, float ground, float t) {
        Vector3f p1 = new Vector3f(from).lerp(to, 0.28f);
        Vector3f p2 = new Vector3f(from).lerp(to, 0.72f);
        float horizontal = (float) Math.hypot(to.x - from.x, to.z - from.z);
        if (horizontal < 0.75f) {
            p1.y = from.y + (to.y - from.y) * 0.28f;
            p2.y = from.y + (to.y - from.y) * 0.72f;
        } else {
            p1.y = ground;
            p2.y = ground;
        }
        return cubic(from, p1, p2, to, t);
    }

    private void joinWhip(World world, MinerNodes.Node a, MinerNodes.Node b, List<UUID> into, MinerNodes.Link link) {
        Vector3f from = shellPort(a, b);
        Vector3f to = shellPort(b, a);
        float sag = Math.min(0.42f, (float) Math.hypot(to.x - from.x, to.z - from.z) * 0.05f);
        int steps = Math.max(5, Math.min(36, Math.round(from.distance(to) * 3.2f)));
        Vector3f prev = whip(from, to, sag, 0f);
        for (int i = 1; i <= steps; i++) {
            Vector3f next = whip(from, to, sag, i / (float) steps);
            boolean tip = i <= 2 || i > steps - 2;
            UUID piece = segment(world, prev, next, tip ? Material.BLACK_CONCRETE : Material.COPPER_BLOCK, tip ? 0.05f : 0.03f);
            if (piece != null) into.add(piece);
            prev = next;
        }
        if (link != null) link.spark = spark(world, a, whip(from, to, sag, 0.22f));
    }

    private void layCord(World world, List<UUID> into, Vector3f from, Vector3f to, Vector3f bow, float lift, int steps) {
        Vector3f prev = cord(from, to, bow, lift, 0f);
        for (int i = 1; i <= steps; i++) {
            Vector3f next = cord(from, to, bow, lift, i / (float) steps);
            boolean tip = i == 1 || i == steps;
            UUID piece = segment(world, prev, next, tip ? Material.COPPER_BLOCK : Material.BLACK_CONCRETE, tip ? 0.048f : 0.03f);
            if (piece != null) into.add(piece);
            prev = next;
        }
    }

    private static Vector3f cord(Vector3f from, Vector3f to, Vector3f bow, float lift, float t) {
        Vector3f p1 = new Vector3f(from).add(bow);
        Vector3f p2 = new Vector3f(to).add(bow);
        p1.y += lift;
        p2.y += lift;
        return cubic(from, p1, p2, to, t);
    }

    private static Vector3f whip(Vector3f from, Vector3f to, float sag, float t) {
        Vector3f p1 = new Vector3f(from).lerp(to, 0.3f);
        Vector3f p2 = new Vector3f(from).lerp(to, 0.7f);
        float hang = (from.y + to.y) * 0.5f - sag;
        p1.y = hang;
        p2.y = hang;
        return cubic(from, p1, p2, to, t);
    }

    private static Vector3f cubic(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
        float u = 1f - t;
        Vector3f point = new Vector3f(p0).mul(u * u * u);
        point.add(new Vector3f(p1).mul(3f * u * u * t));
        point.add(new Vector3f(p2).mul(3f * u * t * t));
        point.add(new Vector3f(p3).mul(t * t * t));
        return point;
    }

    private static Vector3f shellPort(MinerNodes.Node node, MinerNodes.Node other) {
        Vector3f flat = new Vector3f(other.x - node.x, 0f, other.z - node.z);
        if (flat.lengthSquared() < 0.04f) {
            return new Vector3f(node.x + 0.5f, node.y + (other.y > node.y ? 0.78f : 0.14f), node.z + 0.5f);
        }
        return new Vector3f(node.x + 0.5f, node.y + 0.42f, node.z + 0.5f).add(flat.normalize().mul(0.46f));
    }

    private Vector3f worldLocal(MinerNodes.Node node, float x, float y, float z) {
        orient(node.facing);
        Vector3f local = new Vector3f(x, y, z).sub(PIVOT);
        facing.transform(local);
        local.add(PIVOT.x, PIVOT.y, PIVOT.z);
        return new Vector3f(node.x + local.x, node.y + local.y, node.z + local.z);
    }

    private Vector3f polePort(MinerNodes.Node node, MinerNodes.Node other) {
        float[] spot = poleSpot(node, other);
        Vector3f at = worldLocal(node, spot[0], 1.695f, spot[1]);
        Vector3f flat = new Vector3f(other.x + 0.5f - at.x, 0f, other.z + 0.5f - at.z);
        if (flat.lengthSquared() > 0.04f) at.add(flat.normalize().mul(0.03f));
        return at;
    }

    private float[] poleSpot(MinerNodes.Node node, MinerNodes.Node other) {
        List<MinerNodes.Node> left = new ArrayList<>();
        List<MinerNodes.Node> right = new ArrayList<>();
        List<MinerNodes.Node> straight = new ArrayList<>();
        if (nodes != null) {
            for (MinerNodes.Link link : nodes.links()) {
                String mateId = link.a.equals(node.id) ? link.b : link.b.equals(node.id) ? link.a : null;
                if (mateId == null) continue;
                MinerNodes.Node mate = nodes.byId(mateId);
                if (mate == null) continue;
                float side = armSide(node, mate);
                if (side < -0.2f) left.add(mate);
                else if (side > 0.2f) right.add(mate);
                else straight.add(mate);
            }
        }
        if (!left.contains(other) && !right.contains(other) && !straight.contains(other)) straight.add(other);
        straight.sort(MinerVisuals::order);
        for (MinerNodes.Node mate : straight) {
            if (left.size() <= right.size()) left.add(mate);
            else right.add(mate);
        }
        boolean onLeft = left.contains(other);
        List<MinerNodes.Node> side = onLeft ? left : right;
        side.sort((a, b) -> {
            int heading = Float.compare(frontDot(node, a), frontDot(node, b));
            return heading != 0 ? heading : order(a, b);
        });
        int index = Math.max(0, side.indexOf(other));
        int count = Math.max(1, side.size());
        float shift = (index - (count - 1) * 0.5f) * 0.055f;
        float x = onLeft ? 0.225f - shift : 0.775f + shift;
        x = onLeft ? Math.min(0.34f, Math.max(0.14f, x)) : Math.max(0.66f, Math.min(0.86f, x));
        float z = 0.50f + (index - (count - 1) * 0.5f) * 0.045f;
        return new float[] {x, Math.min(0.58f, Math.max(0.42f, z))};
    }

    private float armSide(MinerNodes.Node node, MinerNodes.Node other) {
        Vector3f to = new Vector3f(other.x - node.x, 0f, other.z - node.z);
        if (to.lengthSquared() < 0.01f) return 0f;
        to.normalize();
        Vector3f front = frontOf(node);
        return to.dot(new Vector3f(front.z, 0f, -front.x));
    }

    private float frontDot(MinerNodes.Node node, MinerNodes.Node other) {
        Vector3f to = new Vector3f(other.x - node.x, 0f, other.z - node.z);
        if (to.lengthSquared() < 0.01f) return 0f;
        return to.normalize().dot(frontOf(node));
    }

    private float pin(MinerNodes.Node node, MinerNodes.Node other) {
        Vector3f to = new Vector3f(other.x - node.x, 0f, other.z - node.z);
        if (to.lengthSquared() < 0.01f) return 0.5f;
        to.normalize();
        Vector3f front = frontOf(node);
        float side = to.dot(new Vector3f(front.z, 0f, -front.x));
        if (side > 0.35f) return 0.66f;
        if (side < -0.35f) return 0.34f;
        return 0.5f;
    }

    private Vector3f frontOf(MinerNodes.Node node) {
        double rad = Math.toRadians(yawOf(node.facing));
        return new Vector3f((float) Math.sin(rad), 0f, (float) Math.cos(rad));
    }

    private float yawOf(String name) {
        if (name != null && name.startsWith("yaw:")) {
            try {
                return Float.parseFloat(name.substring(4));
            } catch (NumberFormatException ignored) {
            }
        }
        BlockFace face = BlockFace.SOUTH;
        try {
            if (name != null) face = BlockFace.valueOf(name);
        } catch (IllegalArgumentException ignored) {
        }
        return switch (face) {
            case EAST -> 90f;
            case NORTH -> 180f;
            case WEST -> -90f;
            default -> 0f;
        };
    }

    private void piece(World world, List<UUID> into, float x, float y, float z, float sx, float sy, float sz, Material material) {
        piece(world, into, x, y, z, sx, sy, sz, material, false);
    }

    private void piece(World world, List<UUID> into, float x, float y, float z, float sx, float sy, float sz, Material material, boolean glow) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) world.getChunkAt(bx >> 4, bz >> 4);
        BlockDisplay display = world.spawn(new Location(world, bx, by, bz), BlockDisplay.class, entity -> {
            entity.setBlock(material.createBlockData());
            entity.setTransformation(new Transformation(new Vector3f(x - bx, y - by, z - bz), STILL, new Vector3f(sx, sy, sz), STILL));
            entity.setPersistent(true);
            entity.setShadowRadius(0f);
            entity.addScoreboardTag(TAG);
            if (glow) entity.setBrightness(new Display.Brightness(8, 12));
        });
        into.add(display.getUniqueId());
    }

    private UUID spark(World world, MinerNodes.Node a, Vector3f at) {
        BlockDisplay display = world.spawn(a.corner(), BlockDisplay.class, entity -> {
            entity.setBlock(Material.YELLOW_CONCRETE.createBlockData());
            entity.setTransformation(new Transformation(
                    new Vector3f(at.x - a.x, at.y - a.y, at.z - a.z), STILL, new Vector3f(0.06f, 0.06f, 0.06f), STILL));
            entity.setPersistent(true);
            entity.setShadowRadius(0f);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.addScoreboardTag(TAG);
        });
        return display.getUniqueId();
    }

    Vector3f cablePoint(MinerNodes.Node a, MinerNodes.Node b, float t) {
        Vector3f p0 = port(a, b);
        Vector3f p3 = port(b, a);
        float ground = Math.min(a.y, b.y) + 0.035f;
        float horizontal = (float) Math.hypot(p3.x - p0.x, p3.z - p0.z);
        Vector3f p1 = new Vector3f(p0).lerp(p3, 0.28f);
        Vector3f p2 = new Vector3f(p0).lerp(p3, 0.72f);
        boolean line = a.type == NodeType.POLE || b.type == NodeType.POLE;
        if (horizontal < 0.75f) {
            p1.y = p0.y + (p3.y - p0.y) * 0.28f;
            p2.y = p0.y + (p3.y - p0.y) * 0.72f;
        } else if (line) {
            float drop = Math.min(0.42f, Math.max(0.15f, horizontal * 0.012f));
            p1.y = Math.max(ground + 0.2f, p0.y + (p3.y - p0.y) * 0.28f - drop);
            p2.y = Math.max(ground + 0.2f, p0.y + (p3.y - p0.y) * 0.72f - drop);
        } else {
            p1.y = ground;
            p2.y = ground;
        }
        float u = 1f - t;
        Vector3f point = new Vector3f(p0).mul(u * u * u);
        point.add(new Vector3f(p1).mul(3f * u * u * t));
        point.add(new Vector3f(p2).mul(3f * u * t * t));
        point.add(new Vector3f(p3).mul(t * t * t));
        return point;
    }

    boolean clips(MinerNodes.Node a, MinerNodes.Node b) {
        World world = a.world();
        if (world == null || b.world() == null) return false;
        int span = Math.max(Math.abs(a.x - b.x), Math.max(Math.abs(a.y - b.y), Math.abs(a.z - b.z)));
        int steps = Math.max(8, span * 4);
        int lastX = Integer.MIN_VALUE;
        int lastY = 0;
        int lastZ = 0;
        for (int i = 1; i < steps; i++) {
            Vector3f point = cablePoint(a, b, i / (float) steps);
            int x = (int) Math.floor(point.x);
            int y = (int) Math.floor(point.y);
            int z = (int) Math.floor(point.z);
            if (x == lastX && y == lastY && z == lastZ) continue;
            lastX = x;
            lastY = y;
            lastZ = z;
            if (footprint(a, x, y, z) || footprint(b, x, y, z)) continue;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) world.getChunkAt(x >> 4, z >> 4);
            Block block = world.getBlockAt(x, y, z);
            if (!block.isEmpty() && !block.isPassable() && block.getType().isSolid()) return true;
        }
        return false;
    }

    private static boolean footprint(MinerNodes.Node node, int x, int y, int z) {
        if (x != node.x || z != node.z) return false;
        int extra = node.type == NodeType.RACK || node.type == NodeType.TERMINAL || node.type == NodeType.POLE ? 1 : 0;
        return y >= node.y && y <= node.y + extra;
    }

    private Vector3f port(MinerNodes.Node node, MinerNodes.Node other) {
        if (node.type == NodeType.POLE) return polePort(node, other);
        float rise = other.y > node.y ? 0.82f : other.y < node.y ? 0.1f : 0.16f;
        Vector3f center = new Vector3f(node.x + 0.5f, node.y + rise, node.z + 0.5f);
        Vector3f flat = new Vector3f(other.x - node.x, 0f, other.z - node.z);
        if (flat.lengthSquared() < 0.04f) return center;
        return new Vector3f(center).add(flat.normalize().mul(0.48f));
    }

    private UUID segment(World world, Vector3f from, Vector3f to, Material material, float thick) {
        Vector3f delta = new Vector3f(to).sub(from);
        float length = delta.length();
        if (length < 1.0e-4f) return null;
        Vector3f dir = new Vector3f(delta).div(length);
        Vector3f start = new Vector3f(from).sub(new Vector3f(dir).mul(length * 0.18f));
        float draw = length * 1.36f;
        Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), dir);
        Vector3f side = new Vector3f(thick / 2f, 0f, thick / 2f);
        rot.transform(side);
        Vector3f origin = new Vector3f(start).sub(side);
        int bx = (int) Math.floor(origin.x);
        int by = (int) Math.floor(origin.y);
        int bz = (int) Math.floor(origin.z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) world.getChunkAt(bx >> 4, bz >> 4);
        Vector3f translation = new Vector3f(origin.x - bx, origin.y - by, origin.z - bz);
        boolean copper = material == Material.COPPER_BLOCK;
        BlockDisplay display = world.spawn(new Location(world, bx, by, bz), BlockDisplay.class, entity -> {
            entity.setBlock(material.createBlockData());
            entity.setTransformation(new Transformation(translation, new AxisAngle4f(rot), new Vector3f(thick, draw, thick), STILL));
            entity.setPersistent(true);
            entity.setShadowRadius(0f);
            entity.addScoreboardTag(TAG);
            entity.addScoreboardTag(CABLE);
            if (copper) entity.setBrightness(new Display.Brightness(8, 12));
        });
        return display.getUniqueId();
    }

    private void shimmer(int step) {
        MinerSettings settings = MinerSettings.get();
        if (settings != null && !settings.anyoneAnimating()) return;
        int glow = 11 + (int) Math.round(4 * (0.5 + 0.5 * Math.sin(step * 0.35)));
        Display.Brightness brightness = new Display.Brightness(glow, glow);
        for (MinerNodes.Node node : nodes.all()) {
            if (!node.powered) continue;
            World world = node.world();
            if (world == null) continue;
            for (UUID id : node.displays) {
                if (!(world.getEntity(id) instanceof BlockDisplay display)) continue;
                if (display.getScoreboardTags().contains(LED) || display.getScoreboardTags().contains(GLASS)) {
                    display.setBrightness(brightness);
                }
            }
        }
    }

    private static void loadSpan(World world, MinerNodes.Node a, MinerNodes.Node b) {
        int steps = Math.max(1, Math.max(Math.abs(a.x - b.x), Math.max(Math.abs(a.y - b.y), Math.abs(a.z - b.z))));
        for (int i = 0; i <= steps; i += 8) {
            int x = a.x + (b.x - a.x) * i / steps;
            int z = a.z + (b.z - a.z) * i / steps;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) world.getChunkAt(x >> 4, z >> 4);
        }
    }

    private BlockDisplay part(World world, Location corner, MinerNodes.Node node, Material material,
                              float x, float y, float z, float sx, float sy, float sz,
                              Quaternionf local, Vector3f pivot, String tag) {
        Pose pose = pose(x, y, z, sx, sy, sz, local, pivot);
        return spawn(world, corner, node, material, pose.translation, pose.rotation, sx, sy, sz, tag);
    }

    private Pose pose(float x, float y, float z, float sx, float sy, float sz, Quaternionf local, Vector3f pivot) {
        Vector3f offset = new Vector3f(x, y, z);
        Quaternionf rot = new Quaternionf(facing);
        if (local != null && pivot != null) {
            Vector3f shifted = new Vector3f(offset).sub(pivot);
            local.transform(shifted);
            offset = new Vector3f(pivot).add(shifted);
            rot.mul(local);
        }
        Vector3f spun = new Vector3f(offset).sub(PIVOT);
        facing.transform(spun);
        return new Pose(new Vector3f(PIVOT).add(spun), rot, new Vector3f(sx, sy, sz));
    }

    private void worldBox(World world, Location corner, MinerNodes.Node node, Material material,
                          float x, float y, float z, float sx, float sy, float sz, String tag) {
        spawn(world, corner, node, material, new Vector3f(x, y, z), new Quaternionf(), sx, sy, sz, tag);
    }

    private BlockDisplay spawn(World world, Location corner, MinerNodes.Node node, Material material,
                               Vector3f translation, Quaternionf rot, float sx, float sy, float sz, String tag) {
        return spawn(world, corner, node, material.createBlockData(), translation, rot, sx, sy, sz, tag);
    }

    private BlockDisplay spawn(World world, Location corner, MinerNodes.Node node, org.bukkit.block.data.BlockData block,
                               Vector3f translation, Quaternionf rot, float sx, float sy, float sz, String tag) {
        AxisAngle4f axis = new AxisAngle4f(rot);
        boolean glow = LED.equals(tag) && node.powered || GLASS.equals(tag) && node.powered;
        BlockDisplay display = world.spawn(corner, BlockDisplay.class, entity -> {
            entity.setBlock(block);
            entity.setTransformation(new Transformation(translation, axis, new Vector3f(sx, sy, sz), STILL));
            entity.setPersistent(true);
            entity.setShadowRadius(0f);
            entity.addScoreboardTag(TAG);
            if (tag != null) entity.addScoreboardTag(tag);
            if (glow) entity.setBrightness(new Display.Brightness(15, 15));
        });
        node.displays.add(display.getUniqueId());
        if (LED.equals(tag) && node.lamp == null) node.lamp = display.getUniqueId();
        return display;
    }

    private void forgetBlades(String nodeId) {
        Iterator<Blade> it = blades.iterator();
        while (it.hasNext()) {
            if (it.next().nodeId.equals(nodeId)) it.remove();
        }
    }

    private record Pose(Vector3f translation, Quaternionf rotation, Vector3f scale) {}

    private record Blade(String nodeId, UUID id,
                         float x, float y, float z, float sx, float sy, float sz,
                         float px, float py, float pz, float base, int bay) {}

    private void orient(String name) {
        facing.identity().rotateY((float) Math.toRadians(yawOf(name)));
    }

    private static int order(MinerNodes.Node a, MinerNodes.Node b) {
        if (a.x != b.x) return Integer.compare(a.x, b.x);
        if (a.y != b.y) return Integer.compare(a.y, b.y);
        return Integer.compare(a.z, b.z);
    }

    private static void remove(World world, UUID id) {
        Entity entity = world.getEntity(id);
        if (entity == null) return;
        MinerSettings.forgetMoving(entity.getEntityId());
        entity.remove();
    }
}
