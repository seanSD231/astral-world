package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Draws a player shrunk, squashed or stretched while a fun shroom is on them, springing into the shape and
 * wobbling while it lasts. Only how they are drawn changes; the hitbox is never touched.
 * The title mod's "you unlocked a title" chatter is swallowed here too, so faction titles arrive quietly.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID, value = Dist.CLIENT)
public final class FunClient {
    private FunClient() {}

    /** Per player: {wide now, tall now, wide from, tall from, wide to, tall to, the time the change began (ms)}. */
    private static final java.util.Map<Integer, double[]> ANIM = new java.util.HashMap<>();
    private static final double SPRING_MS = 900;

    /** A spring: shoots past the target and settles, so the change lands with a bounce. */
    private static double spring(double t) {
        if (t >= 1) return 1;
        return 1 - Math.exp(-5.5 * t) * Math.cos(t * 16);
    }

    @SubscribeEvent
    public static void pre(RenderPlayerEvent.Pre e) {
        int id = e.getEntity().getId();
        float[] sh = FunItems.shapeOf(id);
        double tw = sh == null ? 1 : sh[0], th = sh == null ? 1 : sh[1];
        double[] a = ANIM.get(id);
        if (a == null) {
            if (sh == null) return;
            a = new double[]{1, 1, 1, 1, tw, th, System.currentTimeMillis()};
            ANIM.put(id, a);
        } else if (a[4] != tw || a[5] != th) {   // a new shape (or back to normal): spring from wherever we are now
            a[2] = a[0]; a[3] = a[1]; a[4] = tw; a[5] = th; a[6] = System.currentTimeMillis();
        }
        double t = (System.currentTimeMillis() - a[6]) / SPRING_MS, k = spring(t);
        a[0] = a[2] + (a[4] - a[2]) * k; a[1] = a[3] + (a[5] - a[3]) * k;
        if (t >= 1 && sh == null) { ANIM.remove(id); return; }
        // a slow wobble while reshaped, like jelly
        double w = sh == null ? 0 : Math.sin(System.currentTimeMillis() / 180.0 + id) * .04;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.scale((float) Math.max(.05, a[0] * (1 + w)), (float) Math.max(.05, a[1] * (1 - w)), (float) Math.max(.05, a[0] * (1 + w)));
        PUSHED.add(id);
    }

    private static final java.util.Set<Integer> PUSHED = new java.util.HashSet<>();

    @SubscribeEvent
    public static void post(RenderPlayerEvent.Post e) {
        if (PUSHED.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    /** A swing of the Sword of Twilight at the air reaches out to whoever you are looking at: the server is told, and works out the rest. */
    @SubscribeEvent
    public static void swing(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickEmpty e) {
        if (e.getItemStack().getItem() instanceof TwilightSword) net.neoforged.neoforge.network.PacketDistributor.sendToServer(new Net.TwSwingPayload());
    }

    /** Faction and milestone titles announce themselves in chat; this keeps them out of it. */
    @SubscribeEvent
    public static void chat(net.neoforged.neoforge.client.event.ClientChatReceivedEvent e) {
        if (!Settings.hideTitleMessages) return;
        String s = e.getMessage().getString();
        if (s.startsWith("[TITLE]") || s.contains("You unlocked the title") || s.contains("You unlocked a new title")
            || s.contains("Title unlocked") || s.contains("new title:")) e.setCanceled(true);
    }
}
