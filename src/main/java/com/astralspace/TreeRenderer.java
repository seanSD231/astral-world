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
    private static final java.util.Map<String, VertexBuffer> CACHE = new java.util.HashMap<>();
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

    private static VertexBuffer upload(float[] d, int floats) {
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < floats; i += 6)
            bb.addVertex(d[i], d[i + 1], d[i + 2]).setColor((int) d[i + 3], (int) d[i + 4], (int) d[i + 5], 255);
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(bb.buildOrThrow());
        VertexBuffer.unbind();
        return vb;
    }

    private static VertexBuffer model(String key) {
        VertexBuffer vb = CACHE.get(key);
        if (vb != null) return vb;
        if (key.equals("tree") || key.equals("tree_rot")) {
            TreeModel m = TreeModel.build(key.equals("tree_rot"));
            vb = upload(m.data, m.floats);
        } else {
            Mesh m = SovModels.build(key);
            vb = upload(m.data, m.floats);
        }
        CACHE.put(key, vb);
        return vb;
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
            VertexBuffer vb = model(key);

            PoseStack ps = event.getPoseStack();
            ps.pushPose();
            ps.translate(boss.getX() - cam.x, boss.getY() - cam.y, boss.getZ() - cam.z);
            if (!tree) {
                double dx = cam.x - boss.getX(), dz = cam.z - boss.getZ();
                ps.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.atan2(dx, dz)));
                ps.scale(SOV_SCALE, SOV_SCALE, SOV_SCALE);
            }
            Matrix4f mv = new Matrix4f(ps.last().pose());
            ps.popPose();

            float fs = RenderSystem.getShaderFogStart(), fe = RenderSystem.getShaderFogEnd();
            RenderSystem.setShaderFogStart(1.0E7f);
            RenderSystem.setShaderFogEnd(2.0E7f);
            RenderSystem.enableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            vb.bind();
            vb.drawWithShader(mv, event.getProjectionMatrix(), GameRenderer.getPositionColorShader());
            VertexBuffer.unbind();
            RenderSystem.enableCull();
            RenderSystem.setShaderFogStart(fs);
            RenderSystem.setShaderFogEnd(fe);
        }
    }
}
