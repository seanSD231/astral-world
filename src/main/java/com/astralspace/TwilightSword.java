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
    public TwilightSword(String pathId, String title, Item.Properties props) { super(pathId, title, 0, "right click looses a crescent of twilight", (sp, st) -> { }, props); }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tip, flag);
        tip.add(Component.literal("Right click: Dusk Rend, three crescents of twilight that detonate where they end.").withStyle(ChatFormatting.GOLD));
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
            // Dusk Rend: three crescents fan out (an upright one between a rising and a falling stroke), then the dusk detonates where the middle one ends
            Vec3 flat = new Vec3(d.x, 0, d.z).normalize();
            java.util.Map<LivingEntity, Float> hit = new java.util.HashMap<>();
            double[] yaws = {0, -.34, .34}; float[] dmg = {18f, 11f, 11f};
            Vec3 end = o.add(d.scale(12));
            for (int w = 0; w < 3; w++) {
                double c = Math.cos(yaws[w]), sn = Math.sin(yaws[w]);
                Vec3 dw = new Vec3(d.x * c - d.z * sn, d.y, d.x * sn + d.z * c);
                UniqAbilities.fxPath(lv, 1, w, o.add(dw.scale(1.2)), o.add(dw.scale(12)), 10);
                if (w == 0) end = o.add(dw.scale(12));
                for (int i = 1; i <= 14; i++) {
                    Vec3 cc = o.add(dw.scale(i * .8));
                    for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(cc, cc).inflate(1.5), x -> x != p && x.isAlive())) hit.merge(e, dmg[w], Math::max);
                }
            }
            for (var en : hit.entrySet()) { LivingEntity e = en.getKey(); e.hurt(lv.damageSources().playerAttack(p), en.getValue()); e.knockback(.9, -d.x, -d.z); UniqAbilities.fxPoint(lv, 0, e.getX(), e.getY() + e.getBbHeight() * .5, e.getZ(), 1f, 14); }
            final Vec3 bang = end;
            UniqAbilities.later(11, () -> {
                UniqAbilities.fxPoint(lv, 3, bang.x, bang.y, bang.z, 3f, 18);
                lv.playSound(null, net.minecraft.core.BlockPos.containing(bang), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.6f, .5f);
                for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(bang, bang).inflate(4.0), x -> x != p && x.isAlive())) {
                    e.hurt(lv.damageSources().playerAttack(p), 14f); e.knockback(1.1, bang.x - e.getX(), bang.z - e.getZ());
                    UniqAbilities.fxPoint(lv, 0, e.getX(), e.getY() + e.getBbHeight() * .5, e.getZ(), 1f, 14);
                }
            });
            p.getCooldowns().addCooldown(this, 30);
        }
        p.swing(hand);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }
}
