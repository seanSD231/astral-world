package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** The Sword of Twilight: a real sword. Every hit bursts into dusk light, and right click looses a crescent wave of twilight. */
public final class TwilightSword extends WornUniq {
    public TwilightSword(String pathId, String title, Item.Properties props) { super(pathId, title, 0, "right click calls down a twilight verdict", (sp, st) -> { }, props); }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tip, flag);
        tip.add(Component.literal("Right click: Twilight Verdict, the dusk closes on the place you look: crescents converge, a colossal blade falls, and a field of twilight lingers, dragging in and burning.").withStyle(ChatFormatting.GOLD));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.level() instanceof ServerLevel lv) {
            UniqAbilities.fxPoint(lv, 0, target.getX(), target.getY() + target.getBbHeight() * .5, target.getZ(), 1f, 14);
            lv.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.5f);
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        if (p.isShiftKeyDown()) return super.use(level, p, hand);
        ItemStack st = p.getItemInHand(hand);
        if (p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(st);
        if (level instanceof ServerLevel lv) {
            Vec3 o = p.getEyePosition().subtract(0, .3, 0), d = p.getLookAngle();
            lv.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4f, .6f);
            lv.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2f, .7f);
            // Twilight Verdict: the dusk closes in on the spot you look at. Eight crescents converge, a colossal blade of twilight falls, and a field of dusk lingers there, dragging in and burning everything inside
            net.minecraft.world.phys.HitResult hr = p.pick(24, 0f, false);
            Vec3 c0 = hr.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? o.add(d.scale(12)) : hr.getLocation();
            final Vec3 ctr = c0;
            UniqAbilities.fxPoint(lv, 3, ctr.x, ctr.y + .2, ctr.z, 2.2f, 24);
            for (int k = 0; k < 8; k++) {
                final double a = k * Math.PI / 4;
                UniqAbilities.later(4 + k, () -> {
                    Vec3 from = ctr.add(Math.cos(a) * 7, 1.6, Math.sin(a) * 7);
                    UniqAbilities.fxPath(lv, 1, 0, from, ctr.add(0, 1.0, 0), 8);
                });
            }
            UniqAbilities.later(14, () -> {   // the colossal blade starts to fall; it lands 9 ticks later (UniqWorld.BLADE_FALL)
                UniqAbilities.fxPoint(lv, 4, ctr.x, ctr.y, ctr.z, 1f, 62);
                lv.playSound(null, net.minecraft.core.BlockPos.containing(ctr), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.2f, 1.6f);
            });
            UniqAbilities.later(23, () -> {
                UniqAbilities.fxPoint(lv, 3, ctr.x, ctr.y + .5, ctr.z, 6f, 26);
                lv.playSound(null, net.minecraft.core.BlockPos.containing(ctr), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6f, .6f);
                for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(ctr, ctr).inflate(6.0), x -> x != p && x.isAlive())) {
                    e.hurt(lv.damageSources().playerAttack(p), 24f); e.knockback(1.3, ctr.x - e.getX(), ctr.z - e.getZ());
                    UniqAbilities.fxPoint(lv, 0, e.getX(), e.getY() + e.getBbHeight() * .5, e.getZ(), 1f, 14);
                }
            });
            for (int w = 1; w <= 6; w++) {   // the lingering field: pulses of dusk every half second
                UniqAbilities.later(23 + w * 12, () -> {
                    UniqAbilities.fxPoint(lv, 3, ctr.x, ctr.y + .3, ctr.z, 3.2f, 14);
                    for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(ctr, ctr).inflate(6.0), x -> x != p && x.isAlive())) {
                        e.hurt(lv.damageSources().playerAttack(p), 4f);
                        e.push((ctr.x - e.getX()) * .08, .05, (ctr.z - e.getZ()) * .08); e.hurtMarked = true;
                        e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
                    }
                });
            }
            p.getCooldowns().addCooldown(this, 140);
        }
        p.swing(hand);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }
}
