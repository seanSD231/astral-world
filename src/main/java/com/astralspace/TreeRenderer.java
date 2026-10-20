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
    private static VertexBuffer healthy, rotten;

    private TreeRenderer() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(TreeRenderer::onRender);
        NeoForge.EVENT_BUS.addListener(TreeRenderer::onLiving);
    }

    private static boolean isBoss(Entity e) {
        return e.hasCustomName() && NAME.equals(e.getCustomName().getString());
    }

    private static void onLiving(RenderLivingEvent.Pre<?, ?> ev) {
        if (isBoss(ev.getEntity())) ev.setCanceled(true);
    }

    private static VertexBuffer upload(TreeModel m) {
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] d = m.data;
        for (int i = 0; i < m.floats; i += 6)
            bb.addVertex(d[i], d[i + 1], d[i + 2]).setColor((int) d[i + 3], (int) d[i + 4], (int) d[i + 5], 255);
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(bb.buildOrThrow());
        VertexBuffer.unbind();
        return vb;
    }

    private static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Entity boss = null;
        for (Entity e : mc.level.entitiesForRendering()) if (isBoss(e)) { boss = e; break; }
        if (boss == null) return;
        boolean rot = false;
        if (boss instanceof net.minecraft.world.entity.LivingEntity le) rot = le.getHealth() <= le.getMaxHealth() * 0.5f;
        VertexBuffer vb;
        if (rot) { if (rotten == null) rotten = upload(TreeModel.build(true)); vb = rotten; }
        else { if (healthy == null) healthy = upload(TreeModel.build(false)); vb = healthy; }

        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(boss.getX() - cam.x, boss.getY() - cam.y, boss.getZ() - cam.z);
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
