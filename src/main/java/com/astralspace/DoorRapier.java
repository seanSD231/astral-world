package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Door Uniqueness as a rapier: quick, long reach, light hits. Three hits in a row inside 1.5s build a combo
 * (x1.0, x1.25, x1.7). Right click (not sneaking) is a door-dash lunge with a cooldown. Sneak + hold right click still
 * takes the Uniqueness in. Test build: numbers are guesses and need playing with.
 */
public final class DoorRapier extends Uniqueness {
    private static final ResourceLocation REACH_ID = ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "door_rapier_reach");
    public static final SoundEvent THRUST = snd("door.thrust"), LUNGE = snd("door.lunge"), HIT = snd("door.hit"), COMBO = snd("door.combo");
    private static final int COMBO_WINDOW = 30, LUNGE_COOLDOWN = 50;
    private static final Map<UUID, long[]> COMBOS = new ConcurrentHashMap<>();   // uuid -> {last hit tick, hits so far}
    private static boolean inLunge;

    private static SoundEvent snd(String id) { return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, id)); }

    private static Item.Properties props() {
        return new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).attributes(ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 2.5, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -0.8, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_ID, 0.9, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .build());
    }

    public DoorRapier() { super("apprentice", "Door", false, props()); }

    @Override
    public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(DoorClient.EXT);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tip, flag);
        tip.add(Component.literal("Fast, long reach, light hits. Three quick hits build a combo.").withStyle(ChatFormatting.AQUA));
        tip.add(Component.literal("Right click: door dash lunge (short cooldown).").withStyle(ChatFormatting.AQUA));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (p.isShiftKeyDown()) return super.use(level, p, hand);
        if (p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(st);
        p.swing(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp) { lunge(sp); sp.getCooldowns().addCooldown(this, LUNGE_COOLDOWN); }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    private static void lunge(ServerPlayer sp) {
        ServerLevel lv = sp.serverLevel();
        Vec3 look = sp.getLookAngle(), start = sp.position().add(0, sp.getBbHeight() * .5, 0), end = start.add(look.scale(5.0));
        lv.playSound(null, sp.getX(), sp.getY(), sp.getZ(), LUNGE, SoundSource.PLAYERS, 1f, 1f);
        // damage everything along the path
        AABB box = new AABB(start, end).inflate(1.2);
        inLunge = true;
        try {
            for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, box, x -> x != sp && x.isAlive())) {
                Vec3 c = e.position().add(0, e.getBbHeight() * .5, 0); Vec3 ab = end.subtract(start);
                double t = Math.max(0, Math.min(1, c.subtract(start).dot(ab) / ab.lengthSqr()));
                if (c.distanceTo(start.add(ab.scale(t))) > 1.3) continue;
                if (e.hurt(lv.damageSources().playerAttack(sp), 5.0f)) {
                    e.knockback(0.5, -look.x, -look.z);
                    lv.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 10, .3, .3, .3, .2);
                    lv.playSound(null, c.x, c.y, c.z, HIT, SoundSource.PLAYERS, 1f, 1.1f);
                }
            }
        } finally { inLunge = false; }
        // the dash itself, with a trail of stars along the path
        sp.setDeltaMovement(look.x * 1.7, look.y * 0.9 + 0.1, look.z * 1.7);
        sp.hurtMarked = true; sp.resetFallDistance();
        for (int i = 0; i < 22; i++) { Vec3 q = start.add(look.scale(i * .23)); lv.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, .08, .08, .08, .0); }
    }

    /** Combo: three hits inside the window each hit harder; the third finishes with a chime and a burst. */
    public static void onHurt(LivingIncomingDamageEvent e) {
        if (inLunge || !(e.getSource().getEntity() instanceof ServerPlayer sp) || e.getSource().getDirectEntity() != sp) return;
        if (!(sp.getMainHandItem().getItem() instanceof DoorRapier)) return;
        ServerLevel lv = sp.serverLevel(); long now = lv.getGameTime();
        long[] st = COMBOS.computeIfAbsent(sp.getUUID(), k -> new long[]{-999, 0});
        st[1] = (now - st[0] <= COMBO_WINDOW) ? st[1] + 1 : 1; st[0] = now;
        int hit = (int) Math.min(3, st[1]);
        float mult = hit == 1 ? 1.0f : hit == 2 ? 1.25f : 1.7f;
        e.setAmount(e.getAmount() * mult);
        LivingEntity t = e.getEntity(); Vec3 c = t.position().add(0, t.getBbHeight() * .6, 0);
        lv.playSound(null, c.x, c.y, c.z, hit == 3 ? COMBO : HIT, SoundSource.PLAYERS, 1f, hit == 3 ? 1f : 0.95f + 0.1f * hit);
        lv.sendParticles(hit == 3 ? ParticleTypes.END_ROD : ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, hit == 3 ? 24 : 6, .3, .3, .3, hit == 3 ? .25 : .1);
        sp.displayClientMessage(Component.literal("✦".repeat(hit)).withStyle(hit == 3 ? ChatFormatting.AQUA : ChatFormatting.BLUE), true);
        if (hit == 3) st[1] = 0;
    }
}
