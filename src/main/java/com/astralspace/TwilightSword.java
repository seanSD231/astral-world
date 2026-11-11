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
        tip.add(Component.literal("Right click: a crescent wave of twilight.").withStyle(ChatFormatting.GOLD));
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
            java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
            UniqAbilities.fxPath(lv, 1, o.add(d.scale(1.2)), o.add(d.scale(12)), 10);
            for (int i = 1; i <= 14; i++) {
                Vec3 c = o.add(d.scale(i * .8));
                for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(1.6), x -> x != p && x.isAlive())) hit.add(e);
            }
            for (LivingEntity e : hit) { e.hurt(lv.damageSources().playerAttack(p), 18f); e.knockback(.9, -d.x, -d.z); UniqAbilities.fxPoint(lv, 0, e.getX(), e.getY() + e.getBbHeight() * .5, e.getZ(), 1f, 14); }
            p.getCooldowns().addCooldown(this, 30);
        }
        p.swing(hand);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }
}
