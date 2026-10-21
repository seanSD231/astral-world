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
        if (NAME.equals(n)) return "tree";
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
    private record Model(VertexBuffer[] vbs, String[] names, double[][] pivots) {}

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
            md = new Model(new VertexBuffer[]{upload(m.data, 0, m.floats)}, new String[]{"body"}, new double[][]{{0, 0, 0}});
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
            md = new Model(vbs.toArray(new VertexBuffer[0]), names.toArray(new String[0]), pivots.toArray(new double[0][]));
        }
        MODELS.put(key, md);
        return md;
    }

    private static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 cam = event.getCamera().getPosition();
        for (Entity boss : mc.level.entitiesForRendering()) {
            String key = keyOf(boss);
            if (key == null) continue;
            boolean tree = key.equals("tree");
            if (tree && boss instanceof net.minecraft.world.entity.LivingEntity le && le.getHealth() <= le.getMaxHealth() * 0.5f) key = "tree_rot";
            Model md = model(key);
            double t = (System.nanoTime() / 1_000_000L) / 1000.0;

            PoseStack ps = event.getPoseStack();
            ps.pushPose();
            ps.translate(boss.getX() - cam.x, boss.getY() - cam.y, boss.getZ() - cam.z);
            if (!tree) {
                double dx = cam.x - boss.getX(), dz = cam.z - boss.getZ();
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
            for (int i = 0; i < md.vbs().length; i++) {
                Matrix4f pm = mv;
                if (!tree) {
                    pm = new Matrix4f(mv);
                    SovAnim.model(key, t, pm);
                    SovAnim.part(key, md.names()[i], t, md.pivots()[i], pm);
                }
                md.vbs()[i].bind();
                md.vbs()[i].drawWithShader(pm, event.getProjectionMatrix(), GameRenderer.getPositionColorShader());
            }
            VertexBuffer.unbind();
            RenderSystem.enableCull();
            RenderSystem.setShaderFogStart(fs);
            RenderSystem.setShaderFogEnd(fe);
        }
    }
}
