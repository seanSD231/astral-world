package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/** What calls it up. Used on the ground, under an open sky. */
public final class CrimsonOffering extends Item {
    public CrimsonOffering() { super(new Item.Properties().stacksTo(4).rarity(Rarity.EPIC)); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        var level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        var at = ctx.getClickedPos().above();
        var player = ctx.getPlayer();
        if (!level.canSeeSky(at)) {
            if (player != null) player.displayClientMessage(
                    Component.literal("It needs the sky.").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }
        // one at a time: two of these at once is a mess nobody asked for
        if (!level.getEntitiesOfClass(MoonCorruption.class,
                new net.minecraft.world.phys.AABB(at).inflate(96)).isEmpty()) {
            if (player != null) player.displayClientMessage(
                    Component.literal("One is already out.").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }
        ServerLevel lv = (ServerLevel) level;
        var boss = MoonCorruption.TYPE.get().create(lv);
        if (boss == null) return InteractionResult.FAIL;
        boss.moveTo(at.getX() + .5, at.getY(), at.getZ() + .5, player == null ? 0 : player.getYRot(), 0);
        lv.addFreshEntity(boss);
        lv.playSound(null, at, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 2.4f, .6f);
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                at.getX() + .5, at.getY() + 1, at.getZ() + .5, 160, 2, 2, 2, .3);
        ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack st, Item.TooltipContext c, List<Component> tip, net.minecraft.world.item.TooltipFlag f) {
        tip.add(Component.literal("Use it on the ground under an open sky.").withStyle(ChatFormatting.AQUA));
        tip.add(Component.literal("Something comes up that the moon has already finished with.")
                .withStyle(ChatFormatting.DARK_AQUA));
        tip.add(Component.literal("Do not stand in its light longer than you must.")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
