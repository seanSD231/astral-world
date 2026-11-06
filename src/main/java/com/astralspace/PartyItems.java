package com.astralspace;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.joml.Vector3f;

/** Cosmetic party items, /give only: the confetti popper and the bubble wand. Neither does any damage. */
public final class PartyItems {
    private PartyItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AstralMod.MOD_ID);
    public static final DeferredItem<Item> POPPER = ITEMS.register("confetti_popper", () -> new Popper(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> WAND = ITEMS.register("bubble_wand", () -> new Wand(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus modBus) { ITEMS.register(modBus); }

    private static final float[][] CONFETTI = {{1f, .31f, .43f}, {1f, .82f, .3f}, {.3f, .75f, 1f}, {.5f, .9f, .45f}, {1f, .5f, .86f}, {1f, 1f, 1f}};

    /** A random direction inside a cone around `look`, `spread` being the sideways share. */
    private static Vec3 inCone(Vec3 look, double spread, java.util.Random r) {
        Vec3 up = Math.abs(look.y) > .95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = look.cross(up).normalize(), b = look.cross(a).normalize();
        return look.add(a.scale((r.nextDouble() - .5) * 2 * spread)).add(b.scale((r.nextDouble() - .5) * 2 * spread)).normalize();
    }

    static final class Popper extends Item {
        Popper(Properties p) { super(p); }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
            if (level instanceof ServerLevel lv) {
                Vec3 look = player.getLookAngle(), from = player.getEyePosition().add(look.scale(.8));
                java.util.Random r = new java.util.Random();
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0f, 1.3f);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 0.8f, 1.0f);
                for (int i = 0; i < 60; i++) {
                    float[] c = CONFETTI[r.nextInt(CONFETTI.length)];
                    Vec3 d = inCone(look, .45, r);
                    lv.sendParticles(new DustParticleOptions(new Vector3f(c[0], c[1], c[2]), 1.1f), from.x, from.y, from.z, 0, d.x, d.y, d.z, .35 + r.nextDouble() * .5);
                }
                lv.sendParticles(ParticleTypes.FIREWORK, from.x, from.y, from.z, 12, .1, .1, .1, .12);
            }
            player.getCooldowns().addCooldown(this, 20);
            player.swing(hand);
            return InteractionResultHolder.success(stack);
        }
    }

    static final class Wand extends Item {
        Wand(Properties p) { super(p); }

        @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
        @Override public int getUseDuration(ItemStack stack, LivingEntity e) { return 72000; }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }

        @Override
        public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
            if (!(level instanceof ServerLevel lv)) return;
            int t = getUseDuration(stack, user) - remaining;
            if (t % 2 != 0) return;
            Vec3 look = user.getLookAngle(), from = user.getEyePosition().add(look.scale(.9)).add(0, -.25, 0);
            java.util.Random r = new java.util.Random();
            for (int i = 0; i < 2; i++) {
                Vec3 d = inCone(look, .18, r).add(0, .08, 0);
                lv.sendParticles(new DustParticleOptions(new Vector3f(.75f, .93f, 1f), 1.6f + r.nextFloat()), from.x, from.y, from.z, 0, d.x, d.y, d.z, .12 + r.nextDouble() * .1);
            }
            if (t % 6 == 0) {
                Vec3 at = from.add(look.scale(1.5 + r.nextDouble() * 2.5)).add((r.nextDouble() - .5) * .6, .3 + r.nextDouble() * .5, (r.nextDouble() - .5) * .6);
                lv.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.PLAYERS, .5f, .9f + r.nextFloat() * .4f);
            }
        }
    }
}
