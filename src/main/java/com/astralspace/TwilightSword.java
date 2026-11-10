package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import org.joml.Vector3f;

import java.util.List;

/** The Sword of Twilight: a real sword. Every hit bursts into dusk light, and right click looses a crescent wave of twilight. */
public final class TwilightSword extends WornUniq {
    private static final DustParticleOptions AMBER = new DustParticleOptions(new Vector3f(1f, .62f, .2f), 1.3f), VIOLET = new DustParticleOptions(new Vector3f(.6f, .3f, 1f), 1.3f);

    public TwilightSword(String pathId, String title, Item.Properties props) { super(pathId, title, 0, "right click looses a crescent of twilight", (sp, st) -> { }, props); }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tip, flag);
        tip.add(Component.literal("Right click: a crescent wave of twilight.").withStyle(ChatFormatting.GOLD));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.level() instanceof ServerLevel lv) {
            double y = target.getY() + target.getBbHeight() * .5;
            lv.sendParticles(AMBER, target.getX(), y, target.getZ(), 18, .35, .4, .35, .05);
            lv.sendParticles(VIOLET, target.getX(), y, target.getZ(), 18, .35, .4, .35, .05);
            lv.sendParticles(ParticleTypes.END_ROD, target.getX(), y, target.getZ(), 8, .3, .35, .3, .08);
            lv.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), y, target.getZ(), 1, 0, 0, 0, 0);
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
            for (int i = 1; i <= 14; i++) {
                Vec3 c = o.add(d.scale(i * .8));
                Vec3 side = new Vec3(-d.z, 0, d.x).normalize();
                for (int k = -4; k <= 4; k++) {   // the crescent: its horns sweep back
                    Vec3 q = c.add(side.scale(k * .28)).subtract(d.scale(Math.abs(k) * .12));
                    lv.sendParticles(k % 2 == 0 ? AMBER : VIOLET, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
                lv.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, .05, .05, .05, 0);
                for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(1.6), x -> x != p && x.isAlive())) hit.add(e);
            }
            for (LivingEntity e : hit) { e.hurt(lv.damageSources().playerAttack(p), 18f); e.knockback(.9, -d.x, -d.z); }
            p.getCooldowns().addCooldown(this, 30);
        }
        p.swing(hand);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }
}
