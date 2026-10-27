package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client only: the roots of the Mother Tree can be hit without anything standing there to be hit. When you swing and your aim
 * crosses a root (and nothing nearer is in the way), the swing is sent to the server, which checks it and hurts the tree.
 */
final class RootClick {
    private RootClick() {}

    static void init() { NeoForge.EVENT_BUS.addListener(RootClick::onAttack); }

    private static void onAttack(InputEvent.InteractionKeyMappingTriggered ev) {
        if (!ev.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null || Double.isNaN(TreeRenderer.lastY)) return;
        if (!mc.level.dimension().location().getPath().equals("cosmos")) return;
        if (CallScene.active() || TreeCutscene.active() || IntroScene.active()) return;
        HitResult hr = mc.hitResult;
        if (hr != null && hr.getType() == HitResult.Type.ENTITY) return;   // something real is there: the normal attack handles it
        if (hr != null && hr.getType() == HitResult.Type.BLOCK && hr instanceof net.minecraft.world.phys.BlockHitResult bh) {   // heart seeds and root blocks are real blocks: swinging at them breaks them, even under the drawn roots
            var st = mc.level.getBlockState(bh.getBlockPos());
            if (st.is(net.minecraft.world.level.block.Blocks.SHROOMLIGHT) || st.is(net.minecraft.world.level.block.Blocks.MANGROVE_WOOD)) return;
        }
        TreeModel m = TreeRenderer.metaNow();
        if (m == null) return;
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getLookAngle();
        double reach = mc.player.entityInteractionRange() + 2.5;
        double t = m.rayHitRoots(eye.x - TreeRenderer.lastX, eye.y - TreeRenderer.lastY, eye.z - TreeRenderer.lastZ, look.x, look.y, look.z, reach);
        if (t < 0) return;
        if (hr != null && hr.getType() == HitResult.Type.BLOCK && eye.distanceTo(hr.getLocation()) < t - 0.3) return;   // a block is in front of the root
        ev.setCanceled(true);
        mc.player.swing(InteractionHand.MAIN_HAND);
        PacketDistributor.sendToServer(new Net.RootHitPayload(0));
    }
}
