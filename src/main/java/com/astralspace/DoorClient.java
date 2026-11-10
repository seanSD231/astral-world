package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
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
            pose.translate(side * 0.56f, -0.52f - 0.6f * equip, -0.72f);       // vanilla sword position, same as the silver sword
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
            if (p.swinging && p.swingTime == 0) mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), DoorRapier.THRUST, SoundSource.PLAYERS, 0.5f, 1.7f + (mc.level.random.nextFloat() - .5f) * .2f, false);
            if (p.swinging && p.swingTime == 0) {   // one door-seam per attack: a black line packed with blue stars
                Vec3 up = right.cross(look).normalize();
                Vec3 mid = p.getEyePosition().add(look.scale(1.7)).add(right.scale(0.1)).add(up.scale(-0.15));
                double ang = ((p.tickCount / 4) % 2 == 0 ? 1 : -1) * 0.8;                       // alternate the slant each swing
                Vec3 dir = right.scale(Math.cos(ang)).add(up.scale(Math.sin(ang))).normalize();
                for (int i = -22; i <= 22; i++) {
                    double t = i * 0.055, taper = 1.0 - Math.abs(i) / 26.0;
                    Vec3 q = mid.add(dir.scale(t));
                    mc.level.addParticle(PartyItems.SLIT.get(), q.x, q.y, q.z, 0, 0, 0);
                    if (i % 2 == 0) {
                        Vec3 o = right.cross(dir).normalize().scale((mc.level.random.nextFloat() - .5f) * .1 * taper);
                        mc.level.addParticle(PartyItems.STAR_BLUE.get(), q.x + o.x, q.y + o.y, q.z + o.z, 0, 0.004, 0);
                        mc.level.addParticle(PartyItems.STAR_BLUE.get(), q.x + o.x * 1.5, q.y + o.y * 1.5, q.z + o.z * 1.5, look.x * .02, look.y * .02, look.z * .02);
                    }
                }
            }
        }
    }
}
