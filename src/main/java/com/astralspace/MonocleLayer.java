package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws the worn Monocle over the player's left eye (the one on the viewer's right when facing them). */
public class MonocleLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public MonocleLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float partial, float age, float headYaw, float headPitch) {
        ItemStack s = p.getItemBySlot(EquipmentSlot.HEAD);
        if (!(s.getItem() instanceof Monocle) || p.isInvisible()) return;
        ps.pushPose();
        getParentModel().getHead().translateAndRotate(ps);
        ps.translate(0.125f, -0.25f, -0.275f);          // over the eye, just in front of the face
        ps.scale(0.3f, -0.3f, -0.3f);
        Minecraft.getInstance().getItemRenderer().renderStatic(s, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, ps, buf, p.level(), p.getId());
        ps.popPose();
    }
}
