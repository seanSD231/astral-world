package com.astralspace;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

/**
 * Client only: draws the Mother Tree of Desire as a big smooth mesh in place of the boss mob (which is hidden).
 * Healthy form above half HP, rotten fleshy form at or below half. The boss is found by its custom name.
 */
public final class TreeRenderer {
    private static final String NAME = "Mother Tree of Desire";

    private TreeRenderer() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(TreeRenderer::onRender);
        NeoForge.EVENT_BUS.addListener(TreeRenderer::onLiving);
    }

    private static final String[] PLANETS = {"mercury", "venus", "mars", "jupiter", "saturn", "uranus", "neptune"};
    private static final float SOV_SCALE = 0.55f;

    /** Model key for a boss entity, or null: "tree", "tree_rot" is chosen later; planets by "Sovereign of X". */
    private static String keyOf(Entity e) {
        if (!e.hasCustomName()) return null;
        String n = e.getCustomName().getString();
        if (n.startsWith(NAME)) return "tree";
        if (n.startsWith("Sovereign of ")) {
            String p = n.substring(13).trim().toLowerCase(java.util.Locale.ROOT);
            for (String s : PLANETS) if (s.equals(p)) return s;
        }
        return null;
    }

    private static boolean isBoss(Entity e) { return keyOf(e) != null; }

    private static void onLiving(RenderLivingEvent.Pre<?, ?> ev) {
        if (isBoss(ev.getEntity())) ev.setCanceled(true);
    }

    /** One drawable model: a buffer per animated part. */
    private record Model(VertexBuffer[] vbs, String[] names, double[][] pivots, TreeModel meta) {}

    private static VertexBuffer upload(float[] d, int from, int to) {
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = from; i < to; i += 6)
            bb.addVertex(d[i], d[i + 1], d[i + 2]).setColor((int) d[i + 3], (int) d[i + 4], (int) d[i + 5], 255);
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(bb.buildOrThrow());
        VertexBuffer.unbind();
        return vb;
    }

    private static final java.util.Map<String, Model> MODELS = new java.util.HashMap<>();

    private static Model model(String key) {
        Model md = MODELS.get(key);
        if (md != null) return md;
        if (key.equals("tree") || key.equals("tree_rot")) {
            TreeModel m = TreeModel.build(key.equals("tree_rot"));
            java.util.List<VertexBuffer> tv = new java.util.ArrayList<>();
            java.util.List<String> tn = new java.util.ArrayList<>();
            java.util.List<double[]> tp = new java.util.ArrayList<>();
            for (int i = 0; i < m.parts.size(); i++) {
                int from = (Integer) m.parts.get(i)[1], to = i + 1 < m.parts.size() ? (Integer) m.parts.get(i + 1)[1] : m.floats;
                if (to <= from) continue;
                tv.add(upload(m.data, from, to));
                tn.add((String) m.parts.get(i)[0]);
                tp.add((double[]) m.parts.get(i)[2]);
            }
            md = new Model(tv.toArray(new VertexBuffer[0]), tn.toArray(new String[0]), tp.toArray(new double[0][]), m);
        } else {
            Mesh m = SovModels.build(key);
            java.util.List<VertexBuffer> vbs = new java.util.ArrayList<>();
            java.util.List<String> names = new java.util.ArrayList<>();
            java.util.List<double[]> pivots = new java.util.ArrayList<>();
            for (int i = 0; i < m.parts.size(); i++) {
                int from = (Integer) m.parts.get(i)[1], to = i + 1 < m.parts.size() ? (Integer) m.parts.get(i + 1)[1] : m.floats;
                if (to <= from) continue;
                vbs.add(upload(m.data, from, to));
                names.add((String) m.parts.get(i)[0]);
                pivots.add((double[]) m.parts.get(i)[2]);
            }
            md = new Model(vbs.toArray(new VertexBuffer[0]), names.toArray(new String[0]), pivots.toArray(new double[0][]), null);
        }
        MODELS.put(key, md);
        return md;
    }

    /** Where the tree was last seen and whether it was fleshy: the boss entity is only tracked within a limited range, but the tree must stay visible from much further. */
    static double lastX = 0.5, lastY = Double.NaN, lastZ = 0.5;
    static boolean lastFleshy = false;
    private static boolean gone = false;

    /** The tree's mesh data for the form it is in now (limb positions, for the animation's leaves). */
    static TreeModel metaNow() { return model(lastFleshy ? "tree_rot" : "tree").meta(); }

    private static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 cam = event.getCamera().getPosition();
        boolean treeSeen = false;
        if (Net.ClientState.treeGone) { Net.ClientState.treeGone = false; lastY = Double.NaN; gone = true; }
        for (Entity boss : mc.level.entitiesForRendering()) {
            String key = keyOf(boss);
            if (key == null) continue;
            boolean tree = key.equals("tree");
            boolean cut = tree && TreeCutscene.active();
            double cs = cut ? TreeCutscene.seconds() : 0;
            if (tree) {
                treeSeen = true; gone = false;
                boolean fleshy = boss.getCustomName().getString().endsWith("\u200B");   // set by the server when the transformation is done
                lastX = boss.getX(); lastY = boss.getY(); lastZ = boss.getZ(); lastFleshy = fleshy;
                if (cut) fleshy = cs >= TreeCutscene.SWAP;
                if (fleshy) key = "tree_rot";
            }
            draw(event, cam, key, boss.getX(), boss.getY(), boss.getZ(), boss, cut, cs);
        }
        // in the raid arena the tree stays on screen however far you go, even when the game has stopped tracking the boss
        if (!treeSeen && !gone && mc.level.dimension().location().getPath().equals("cosmos")) {
            double y = Double.isNaN(lastY) ? PlanetGenerator.BASE + 3.0 : lastY;   // the arena floor is flat and fixed: never follow the player's height (boss spawns at the surface + 2)
            draw(event, cam, lastFleshy ? "tree_rot" : "tree", lastX, y, lastZ, null, false, 0);
        }
    }

    private static void draw(RenderLevelStageEvent event, Vec3 cam, String key, double bx, double by, double bz, Entity boss, boolean cut, double cs) {
        boolean tree = key.startsWith("tree");
        Model md = model(key);
        double t = (System.nanoTime() / 1_000_000L) / 1000.0;

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(bx - cam.x, by - cam.y, bz - cam.z);
        float[] tint = {1f, 1f, 1f};
        if (cut) {
            float[] st = TreeCutscene.treeState(cs);   // scale x, scale y, tint r, g, b, shake
            ps.scale(st[0], st[1], st[0]);
            ps.mulPose(com.mojang.math.Axis.ZP.rotation((float) Math.sin(cs * 37) * st[5]));
            ps.mulPose(com.mojang.math.Axis.XP.rotation((float) Math.cos(cs * 31) * st[5]));
            tint = new float[]{st[2], st[3], st[4]};
        }
        if (!tree) {
            double dx = cam.x - bx, dz = cam.z - bz;
            ps.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.atan2(dx, dz)));
            float jolt = boss instanceof net.minecraft.world.entity.LivingEntity le2 ? 1f + 0.04f * le2.hurtTime / 10f : 1f;
            ps.translate(0, Math.sin(t * .9) * .2, 0);       // hovers
            ps.scale(SOV_SCALE * jolt, SOV_SCALE * jolt, SOV_SCALE * jolt);
        }
        Matrix4f mv = new Matrix4f(event.getModelViewMatrix()).mul(ps.last().pose()); // camera rotation lives in the event's view matrix, not the pose stack
        ps.popPose();

        float fs = RenderSystem.getShaderFogStart(), fe = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(1.0E7f);
        RenderSystem.setShaderFogEnd(2.0E7f);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        if (tree) { float[] ta = TreeAnim.tint(); tint = new float[]{tint[0] * ta[0], tint[1] * ta[1], tint[2] * ta[2]}; }
        RenderSystem.setShaderColor(tint[0], tint[1], tint[2], 1f);
        for (int i = 0; i < md.vbs().length; i++) {
            Matrix4f pm = mv;
            if (!tree) {
                pm = new Matrix4f(mv);
                SovAnim.model(key, t, pm);
                SovAnim.part(key, md.names()[i], t, md.pivots()[i], pm);
            } else if (md.meta() != null) {
                pm = new Matrix4f(mv);
                TreeAnim.apply(md.meta(), md.names()[i], md.pivots()[i], pm, t);
            }
            md.vbs()[i].bind();
            md.vbs()[i].drawWithShader(pm, event.getProjectionMatrix(), GameRenderer.getPositionColorShader());
        }
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
        RenderSystem.setShaderFogStart(fs);
        RenderSystem.setShaderFogEnd(fe);
    }
}
