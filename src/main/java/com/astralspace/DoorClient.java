package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Client side of the Door rapier: a forward thrust in first person instead of the usual swing, star trails and a whoosh. */
public final class DoorClient {
    private DoorClient() {}

    public static final IClientItemExtensions EXT = new IClientItemExtensions() {
        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float pt, float equip, float swing) {
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            float t = Mth.sin(swing * (float) Math.PI);                         // 0 -> 1 -> 0 over the swing
            pose.translate(side * 0.56f, -0.52f - 0.6f * equip, -0.72f);        // resting place (same as vanilla)
            pose.translate(-side * 0.18f * t, 0.06f * t, -1.1f * t);            // punch the blade forward
            pose.mulPose(Axis.XP.rotationDegrees(-62f * t));                    // lay the blade flat toward the target
            pose.mulPose(Axis.ZP.rotationDegrees(side * 6f * t));
            return true;
        }
    };

    private static boolean held(AbstractClientPlayer p) { return p.getMainHandItem().getItem() instanceof DoorRapier; }

    /** Every client tick: a whoosh when a swing starts, and star trails off the blade while swinging (a faint shimmer otherwise). */
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (!held(p)) continue;
            Vec3 look = p.getLookAngle(), right = look.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 hand = p.getEyePosition().add(look.scale(0.9)).add(right.scale(0.35)).add(0, -0.3, 0);
            if (p.swinging && p.swingTime == 0) mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), DoorRapier.THRUST, SoundSource.PLAYERS, 0.8f, 1.0f + (mc.level.random.nextFloat() - .5f) * .2f, false);
            if (p.swinging) {
                for (int i = 0; i < 4; i++) {
                    Vec3 q = hand.add(look.scale(i * 0.35));
                    mc.level.addParticle(ParticleTypes.END_ROD, q.x, q.y, q.z, 0, 0, 0);
                }
            } else if (mc.level.random.nextFloat() < 0.06f) {
                Vec3 q = hand.add(look.scale(mc.level.random.nextFloat() * 1.2));
                mc.level.addParticle(ParticleTypes.END_ROD, q.x, q.y, q.z, 0, 0.01, 0);
            }
        }
    }
}
