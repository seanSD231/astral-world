package com.astralspace;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Tarot cards and the 22 pathway packs (/give only for now).
 * One card item, `astral_world:tarot_card`, carries its pathway and sequence in custom_data: {pathway:"seer",seq:9}. With no data it is the temp test card.
 * A pack gives 3 cards: rarity is rolled first (50/25/20/3/1.5/0.5 %), then the pathway (the pack's own counts 5x), then the shiny roll (2.5/5/5/7.5/10/20 % by rarity); rainbow packs roll Epic/Legendary/Mythic at 75/20/5 %.
 */
public final class Tarot {
    private Tarot() {}

    public static final String[] PATHWAYS = {"apothecary","apprentice","arbiter","assassin","bard","corpse_collector","criminal","hunter","lawyer","marauder","monster","mystery_pryer","planter","prisoner","reader","sailor","savant","secrets_supplicant","seer","sleepless","spectator","warrior"};
    /** Weight of each sequence, index = sequence 0..9. Seq 9 is the most common, Seq 0 the rarest. */
    /** Chance of each rarity per card (common, uncommon, rare, epic, legendary, mythic), percent. */
    private static final double[] RARITY_CHANCE = {50, 25, 20, 3, 1.5, 0.5};
    /** Rainbow packs: rarity chances instead of the normal ones (Epic, Legendary, Mythic only), percent. */
    private static final double[] RAINBOW_CHANCE = {0, 0, 0, 75, 20, 5};
    /** Chance that a card of that rarity is shiny, rolled after its rarity, percent. */
    private static final double[] SHINY_CHANCE = {2.5, 5, 5, 7.5, 10, 20};
    /** The sequences each rarity covers. */
    private static final int[][] RARITY_SEQS = {{9, 8}, {7, 6}, {5, 4}, {3, 2}, {1}, {0}};
    private static final String[] RARITY = {"Mythic", "Legendary", "Epic", "Epic", "Rare", "Rare", "Uncommon", "Uncommon", "Common", "Common"};
    private static final ChatFormatting[] RARITY_COLOR = {ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD, ChatFormatting.DARK_PURPLE, ChatFormatting.DARK_PURPLE, ChatFormatting.AQUA, ChatFormatting.AQUA, ChatFormatting.GREEN, ChatFormatting.GREEN, ChatFormatting.GRAY, ChatFormatting.GRAY};
    private static final int PACK_CARDS = 3;
    private static final double OWN_BONUS = 5.0;

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AstralMod.MOD_ID);
    public static final DeferredItem<Item> CARD = ITEMS.register("tarot_card", () -> new CardItem(new Item.Properties()));
    static {
        for (String id : PATHWAYS) {
            ITEMS.register(id + "_pack", () -> new PackItem(new Item.Properties().stacksTo(16), id, 0));
            ITEMS.register(id + "_rainbow_pack", () -> new PackItem(new Item.Properties().stacksTo(16), id, 1));
            ITEMS.register(id + "_shiny_pack", () -> new PackItem(new Item.Properties().stacksTo(16), id, 2));
        }
    }

    public static void register(IEventBus modBus) { ITEMS.register(modBus); }

    /** 0 common (Seq 9-8), 1 uncommon (7-6), 2 rare (5-4), 3 epic (3-2), 4 legendary (1), 5 mythic (0). */
    public static int rarityIndex(int seq) { return seq >= 8 ? 0 : seq >= 6 ? 1 : seq >= 4 ? 2 : seq >= 2 ? 3 : seq == 1 ? 4 : 5; }

    public static int rarityOf(ItemStack s) {
        CompoundTag t = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return t.contains("pathway") || t.contains("seq") ? rarityIndex(t.getInt("seq")) : 0;
    }

    /** Which designed icon a card has: 0 for none (the plain rarity swatch), else pathway*10+seq+1. Has to match the overrides in tarot_card.json. */
    /** Nothing is animated any more: every card, Seq 9 to 0, is a still. Seq 0 cards are 480x720, the rest 240x360. */
    public static final java.util.Set<String> ANIMATED = java.util.Set.of(PATHWAYS);
    public static boolean hasAnim(String p, int q) { return false; }

    public static int cardIndex(ItemStack s) {
        CompoundTag t = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!t.contains("pathway")) return 0;
        String p = t.getString("pathway"); int q = t.getInt("seq");
        if (!(q >= 0 && q <= 9 || hasAnim(p, q))) return 0;
        for (int i = 0; i < PATHWAYS.length; i++) if (PATHWAYS[i].equals(p)) return (t.getBoolean("shiny") ? 1000 : 0) + i * 10 + q + 1;   // shiny cards (Seq 9 to 3) have their own black and gold art
        return 0;
    }

    static String pretty(String id) {
        StringBuilder b = new StringBuilder();
        for (String w : id.split("_")) b.append(b.length() > 0 ? " " : "").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }

    public static ItemStack makeCard(String pathway, int seq) { return makeCard(pathway, seq, false); }

    /** A shiny card carries shiny:1b in its custom data. */
    public static ItemStack makeCard(String pathway, int seq, boolean shiny) {
        ItemStack s = new ItemStack(CARD.get());
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> { t.putString("pathway", pathway); t.putInt("seq", seq); if (shiny) t.putBoolean("shiny", true); });
        return s;
    }

    /**
     * One card: the rarity is rolled first, then a sequence of that rarity (evenly), then a pathway (the pack's own pathway counts 5x
     * as much as any single other pathway), then the shiny roll for that rarity.
     */
    static ItemStack draw(String own, Random r, int mode) {
        // rainbow packs (mode 1) only roll Epic and above (Seq 3, 2, 1, 0); shiny packs (mode 2) roll normally but every card is shiny
        int first = mode == 1 ? 3 : 0;
        double[] chance = mode == 1 ? RAINBOW_CHANCE : RARITY_CHANCE;
        double sum = 0; for (int i = first; i < chance.length; i++) sum += chance[i];
        double pick = r.nextDouble() * sum, acc = 0;
        int rar = RARITY_CHANCE.length - 1;
        for (int i = first; i < RARITY_CHANCE.length; i++) { acc += chance[i]; if (pick < acc) { rar = i; break; } }
        int[] seqs = RARITY_SEQS[rar];
        int seq = seqs[r.nextInt(seqs.length)];
        double total = PATHWAYS.length - 1 + OWN_BONUS, pp = r.nextDouble() * total;
        String path = own;
        for (String id : PATHWAYS) { pp -= id.equals(own) ? OWN_BONUS : 1.0; if (pp < 0) { path = id; break; } }
        return makeCard(path, seq, mode == 2 || r.nextDouble() * 100 < SHINY_CHANCE[rar]);
    }

    static final class CardItem extends Item {
        CardItem(Properties p) { super(p); }

        private static CompoundTag tag(ItemStack s) { return s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }

        @Override
        public Component getName(ItemStack stack) {
            CompoundTag t = tag(stack);
            if (!t.contains("pathway")) return Component.literal("Tarot Card (temp)").withStyle(ChatFormatting.LIGHT_PURPLE);
            int seq = Math.max(0, Math.min(9, t.getInt("seq")));
            return Component.literal((t.getBoolean("shiny") ? "Shiny " : "") + pretty(t.getString("pathway")) + " - Seq " + seq).withStyle(RARITY_COLOR[seq]);
        }

        @Override public boolean isFoil(ItemStack stack) { return tag(stack).getBoolean("shiny") || super.isFoil(stack); }

        /** Right click on the top of a block: put the card down. Rare and better cards (Seq 5 and up) show a hologram above it. */
        @Override
        public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext c) {
            if (c.getClickedFace() != net.minecraft.core.Direction.UP) return net.minecraft.world.InteractionResult.PASS;
            ItemStack st = c.getItemInHand(); CompoundTag t = tag(st);
            if (!t.contains("pathway")) return net.minecraft.world.InteractionResult.PASS;
            Level lv = c.getLevel();
            if (!lv.isClientSide && c.getPlayer() != null) {
                net.minecraft.core.BlockPos bp = c.getClickedPos();
                double top = bp.getY() + lv.getBlockState(bp).getShape(lv, bp).max(net.minecraft.core.Direction.Axis.Y);
                if (top < bp.getY() + .1) top = bp.getY() + 1;
                net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(c.getClickLocation().x, top + .01, c.getClickLocation().z);
                if (!lv.getEntitiesOfClass(HoloCardEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(.45, .3, .45)).isEmpty()) return net.minecraft.world.InteractionResult.FAIL;
                HoloCardEntity e = new HoloCardEntity(HoloCardEntity.TYPE.get(), lv);
                int seq = Math.max(0, Math.min(9, t.getInt("seq")));
                e.setCard(t.getString("pathway"), seq, t.getBoolean("shiny")); e.setOwner(c.getPlayer());
                e.moveTo(at.x, at.y, at.z, c.getPlayer().getYRot(), 0);
                lv.addFreshEntity(e);
                if (!c.getPlayer().getAbilities().instabuild) st.shrink(1);
                float pitch = seq >= 5 ? 1.2f : 1.6f;
                lv.playSound(null, at.x, at.y, at.z, seq == 0 ? net.minecraft.sounds.SoundEvents.END_PORTAL_SPAWN : seq <= 1 ? net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE : seq <= 3 ? net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME : net.minecraft.sounds.SoundEvents.UI_TOAST_IN, net.minecraft.sounds.SoundSource.PLAYERS, seq == 0 ? .5f : .8f, pitch);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(lv.isClientSide);
        }

        /** Right click: look at the card (its art, and the shiny sparkles if it is shiny). */
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack st = player.getItemInHand(hand);
            CompoundTag t = tag(st);
            if (!level.isClientSide && player instanceof ServerPlayer sp && t.contains("pathway"))
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new Net.PackPayload("view", t.getString("pathway") + ":" + t.getInt("seq") + ":" + (t.getBoolean("shiny") ? 1 : 0)));
            return InteractionResultHolder.success(st);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
            CompoundTag t = tag(stack);
            if (!t.contains("pathway")) { lines.add(Component.literal("A placeholder card for testing.").withStyle(ChatFormatting.GRAY)); return; }
            int seq = Math.max(0, Math.min(9, t.getInt("seq")));
            lines.add(Component.literal(RARITY[seq]).withStyle(RARITY_COLOR[seq]));
            if (t.getBoolean("shiny")) lines.add(Component.literal("Shiny").withStyle(ChatFormatting.YELLOW));
        }
    }

    static final class PackItem extends Item {
        private final String pathway;
        private final int mode;   // 0 normal, 1 rainbow (every card Seq 3 or higher), 2 shiny (every card shiny)
        PackItem(Properties p, String pathway, int mode) { super(p); this.pathway = pathway; this.mode = mode; }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
            lines.add(Component.literal(PACK_CARDS + " cards. " + pretty(pathway) + " cards come up 5x as often. "
                + (mode == 1 ? "Every card is Seq 3 or higher. Any card can be shiny." : mode == 2 ? "Every card is shiny." : "Any card can be shiny.")).withStyle(ChatFormatting.GRAY));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack pack = player.getItemInHand(hand);
            if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(pack);
            Random r = new Random();
            StringBuilder got = new StringBuilder();
            for (int i = 0; i < PACK_CARDS; i++) {
                ItemStack c = draw(pathway, r, mode);
                var tg = c.get(DataComponents.CUSTOM_DATA).copyTag();
                got.append(i > 0 ? "," : "").append(tg.getString("pathway")).append(':').append(tg.getInt("seq")).append(':').append(tg.getBoolean("shiny") ? 1 : 0);
                if (!sp.getInventory().add(c)) sp.drop(c, false);
            }
            if (!sp.getAbilities().instabuild) pack.shrink(1);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new Net.PackPayload(pathway + (mode == 1 ? "_rainbow" : mode == 2 ? "_shiny" : ""), got.toString()));
            return InteractionResultHolder.success(pack);
        }
    }
}
