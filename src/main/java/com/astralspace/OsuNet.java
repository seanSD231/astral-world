package com.astralspace;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything the osu cabinet sends over the network, and what the server does with it. Nothing a client sends is trusted. */
public final class OsuNet {
    private OsuNet() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> mk(String id) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, id));
    }

    public record OpenCabinet(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenCabinet> TYPE = mk("osu_open");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenCabinet> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, OpenCabinet::pos, OpenCabinet::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ListReq() implements CustomPacketPayload {
        public static final Type<ListReq> TYPE = mk("osu_list_req");
        public static final StreamCodec<RegistryFriendlyByteBuf, ListReq> CODEC = StreamCodec.unit(new ListReq());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Rows of "id|name|author|circles|disc". */
    public record ListData(List<String> rows) implements CustomPacketPayload {
        public static final Type<ListData> TYPE = mk("osu_list");
        public static final StreamCodec<RegistryFriendlyByteBuf, ListData> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(OsuLibrary.MAX_LEVELS + 1)), ListData::rows, ListData::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Fetch(int id) implements CustomPacketPayload {
        public static final Type<Fetch> TYPE = mk("osu_fetch");
        public static final StreamCodec<RegistryFriendlyByteBuf, Fetch> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Fetch::id, Fetch::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record LevelCode(String code) implements CustomPacketPayload {
        public static final Type<LevelCode> TYPE = mk("osu_code");
        public static final StreamCodec<RegistryFriendlyByteBuf, LevelCode> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, LevelCode::code, LevelCode::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Publish(String code) implements CustomPacketPayload {
        public static final Type<Publish> TYPE = mk("osu_publish");
        public static final StreamCodec<RegistryFriendlyByteBuf, Publish> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Publish::code, Publish::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Delete(int id) implements CustomPacketPayload {
        public static final Type<Delete> TYPE = mk("osu_delete");
        public static final StreamCodec<RegistryFriendlyByteBuf, Delete> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Delete::id, Delete::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Start (true) or stop (false) the music disc of a level on a jukebox next to the cabinet. */
    public record Music(BlockPos pos, String disc, boolean start) implements CustomPacketPayload {
        public static final Type<Music> TYPE = mk("osu_music");
        public static final StreamCodec<RegistryFriendlyByteBuf, Music> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Music::pos, ByteBufCodecs.STRING_UTF8, Music::disc, ByteBufCodecs.BOOL, Music::start, Music::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(PayloadRegistrar r) {
        r.playToClient(OpenCabinet.TYPE, OpenCabinet.CODEC, (p, ctx) -> ctx.enqueueWork(() -> OsuHooks.openCabinet(p.pos())));
        r.playToClient(ListData.TYPE, ListData.CODEC, (p, ctx) -> ctx.enqueueWork(() -> OsuHooks.showList(p.rows())));
        r.playToClient(LevelCode.TYPE, LevelCode.CODEC, (p, ctx) -> ctx.enqueueWork(() -> OsuHooks.gotCode(p.code())));
        r.playToServer(ListReq.TYPE, ListReq.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) sendList(sp); }));
        r.playToServer(Fetch.TYPE, Fetch.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) fetch(sp, p.id()); }));
        r.playToServer(Publish.TYPE, Publish.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) publish(sp, p.code()); }));
        r.playToServer(Delete.TYPE, Delete.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) delete(sp, p.id()); }));
        r.playToServer(Music.TYPE, Music.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) music(sp, p); }));
    }

    // ------------------------------------------------------------------ server side

    private static final Map<UUID, Long> lastAction = new HashMap<>();

    private static boolean tooFast(ServerPlayer p, long ms) {
        long now = System.currentTimeMillis();
        Long last = lastAction.get(p.getUUID());
        if (last != null && now - last < ms) return true;
        lastAction.put(p.getUUID(), now);
        return false;
    }

    private static void sendList(ServerPlayer p) {
        if (tooFast(p, 300)) return;
        List<String> rows = new ArrayList<>();
        List<OsuLibrary.Entry> all = new ArrayList<>(OsuLibrary.get(p.server).levels.values());
        java.util.Collections.reverse(all);   // newest first
        for (OsuLibrary.Entry e : all) {
            String nm = e.level.name.replace('|', '/'), au = e.level.author.replace('|', '/');
            rows.add(e.id + "|" + nm + "|" + au + "|" + e.level.notes.size() + "|" + (e.level.disc.isEmpty() ? "0" : "1") + "|" + (e.author.equals(p.getUUID()) ? "1" : "0"));
        }
        PacketDistributor.sendToPlayer(p, new ListData(rows));
    }

    private static void fetch(ServerPlayer p, int id) {
        if (tooFast(p, 200)) return;
        OsuLibrary.Entry e = OsuLibrary.get(p.server).levels.get(id);
        if (e == null) { p.sendSystemMessage(Component.literal("That level is gone.")); return; }
        PacketDistributor.sendToPlayer(p, new LevelCode(e.code));
    }

    private static void publish(ServerPlayer p, String code) {
        if (tooFast(p, 3000)) { p.sendSystemMessage(Component.literal("Slow down: one level every few seconds.")); return; }
        try {
            OsuLevel l = OsuLevel.decode(code);
            l.author = p.getGameProfile().getName();
            OsuLibrary.get(p.server).add(p.getUUID(), l);
            p.sendSystemMessage(Component.literal("Published \"" + l.name + "\" on every osu cabinet (" + l.notes.size() + " circles)."));
        } catch (IllegalArgumentException ex) {
            p.sendSystemMessage(Component.literal("That level could not be published: " + ex.getMessage()));
        }
    }

    private static void delete(ServerPlayer p, int id) {
        OsuLibrary lib = OsuLibrary.get(p.server);
        OsuLibrary.Entry e = lib.levels.get(id);
        if (e == null) return;
        if (!e.author.equals(p.getUUID()) && !p.hasPermissions(2)) { p.sendSystemMessage(Component.literal("Only the author (or an op) can delete a level.")); return; }
        lib.remove(id);
        p.sendSystemMessage(Component.literal("Deleted \"" + e.level.name + "\"."));
    }

    /** The disc of a level plays on a jukebox standing next to the cabinet (Etched's discs play wherever Etched lets them). */
    private static void music(ServerPlayer p, Music m) {
        if (p.distanceToSqr(m.pos().getX() + 0.5, m.pos().getY() + 0.5, m.pos().getZ() + 0.5) > 64) return;
        var level = p.serverLevel();
        if (!(level.getBlockState(m.pos()).getBlock() == AstralFloor.CABINET.get())) return;
        BlockPos jpos = null;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (level.getBlockEntity(m.pos().relative(d)) instanceof JukeboxBlockEntity) { jpos = m.pos().relative(d); break; }
        }
        if (jpos == null) {
            if (m.start() && !m.disc().isEmpty()) p.displayClientMessage(Component.literal("Put a jukebox next to the cabinet to hear this level's disc."), true);
            return;
        }
        JukeboxBlockEntity jb = (JukeboxBlockEntity) level.getBlockEntity(jpos);
        if (!m.start()) {
            ItemStack cur = jb.getTheItem();
            if (!cur.isEmpty() && cur.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getBoolean("astral_osu")) jb.setTheItem(ItemStack.EMPTY);
            return;
        }
        if (m.disc().isEmpty() || !jb.getTheItem().isEmpty()) return;   // never touch a disc someone put there
        try {
            var tag = net.minecraft.nbt.TagParser.parseTag(m.disc());
            ItemStack disc = ItemStack.parse(level.registryAccess(), tag).orElse(ItemStack.EMPTY);
            if (disc.isEmpty()) return;
            disc.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY.update(t -> t.putBoolean("astral_osu", true)));   // marks it as the cabinet's copy, which it takes away again
            jb.setTheItem(disc);
            etchedNudge(level, jb, jpos, disc);
        } catch (Exception ex) {
            p.displayClientMessage(Component.literal("The disc of this level could not be played."), true);
        }
    }

    /**
     * Etched plays its discs by hooking JukeboxBlockEntity.setTheItem and sending its own "play block music" packet. If that hook did not fire
     * (it marks the jukebox with an etched$playing flag), send the same packet here. Everything is by reflection, so the mod is optional.
     */
    private static void etchedNudge(net.minecraft.server.level.ServerLevel level, JukeboxBlockEntity jb, BlockPos pos, ItemStack disc) {
        try {
            Class<?> pr = Class.forName("gg.moonflower.etched.api.record.PlayableRecord");
            if (!(Boolean) pr.getMethod("isPlayableRecord", ItemStack.class).invoke(null, disc)) return;
            java.lang.reflect.Field f = null;
            for (Class<?> c = jb.getClass(); c != null && f == null; c = c.getSuperclass()) {
                try { f = c.getDeclaredField("etched$playing"); } catch (NoSuchFieldException ignored) { }
            }
            if (f != null) { f.setAccessible(true); if (f.getBoolean(jb)) return; }   // Etched already started it
            Class<?> pk = Class.forName("gg.moonflower.etched.common.network.play.ClientboundPlayBlockMusicPacket");
            Object packet = pk.getConstructor(ItemStack.class, BlockPos.class, java.util.UUID.class).newInstance(disc.copy(), pos, null);
            PacketDistributor.sendToPlayersNear(level, null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 64.0, (net.minecraft.network.protocol.common.custom.CustomPacketPayload) packet);
            if (f != null) f.setBoolean(jb, true);
            com.mojang.logging.LogUtils.getLogger().info("[osu] sent the Etched disc to players by hand (Etched's own hook had not started it)");
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().warn("[osu] Etched disc playback failed: {}", t.toString());
        }
    }
}
