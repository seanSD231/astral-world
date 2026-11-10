package com.astralspace;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client side of the Eyes of the Door: the Door key, and glowing eyes on the face of anyone wearing them. */
public final class DoorClient {
    private DoorClient() {}

    public static final KeyMapping KEY = new KeyMapping("key.astral_world.door_eyes", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.astral_world");
    private static final ResourceLocation FACE = ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "textures/entity/door_eyes.png");
    private static final Map<UUID, Boolean> WORN = new HashMap<>();

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(KEY));
        modBus.addListener(DoorClient::addLayers);
        NeoForge.EVENT_BUS.addListener(DoorClient::tick);
    }

    private static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        while (KEY.consumeClick()) if (mc.screen == null) PacketDistributor.sendToServer(new DoorNet.Key());
        if (mc.player.tickCount % 10 == 0) for (AbstractClientPlayer p : mc.level.players()) WORN.put(p.getUUID(), !DoorEyes.findEyes(p).isEmpty());
    }

    private static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (var skin : e.getSkins()) {
            EntityRenderer<?> r = e.getSkin(skin);
            if (r instanceof PlayerRenderer pr) pr.addLayer(new EyesLayer(pr));
        }
    }

    /** Two glowing eyes over the face, drawn bright (like a spider's) so they shine in the dark. */
    private static final class EyesLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        EyesLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buf, int light, AbstractClientPlayer p, float limb, float limbAmt, float pt, float age, float headYaw, float headPitch) {
            if (p.isInvisible() || !WORN.getOrDefault(p.getUUID(), false)) return;
            pose.pushPose();
            getParentModel().getHead().translateAndRotate(pose);
            VertexConsumer vc = buf.getBuffer(RenderType.eyes(FACE));
            PoseStack.Pose m = pose.last();
            float x0 = -4f / 16, x1 = 4f / 16, y0 = -6.2f / 16, y1 = -2.2f / 16, z = -4.03f / 16;
            v(vc, m, x0, y1, z, 0, 0); v(vc, m, x1, y1, z, 1, 0); v(vc, m, x1, y0, z, 1, 1); v(vc, m, x0, y0, z, 0, 1);
            pose.popPose();
        }

        private static void v(VertexConsumer vc, PoseStack.Pose m, float x, float y, float z, float u, float w) {
            vc.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u, w).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(m, 0, 0, -1);
        }
    }
}
