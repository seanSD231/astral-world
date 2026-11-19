package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Two things the launcher needs from inside the game.
 *
 * <p>The launcher colours itself after a pathway, and would rather not ask. It cannot read
 * one on its own: it has no account before the player signs in, and LOTM keeps pathway
 * state on the server. So the client writes its own pathway to a small file beside the
 * install whenever it changes, and the launcher picks it up next time it starts.
 *
 * <p>Singleplayer is refused. Not because it can be prevented -- anyone determined can
 * delete this jar -- but so that nobody wanders into the pack's content offline by
 * accident and spoils it for themselves. Set {@code allow_singleplayer=true} in
 * settings.properties, or pass {@code -Dastral.allowSingleplayer=true}, to play offline
 * anyway; the second is there for development, where no settings file may exist yet.
 */
public final class Launcher {
    private Launcher() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("astral");

    /** Where the launcher looks. Beside its own data, inside the instance folder. */
    private static Path file() {
        return FMLPaths.GAMEDIR.get().resolve(".lotmcraft").resolve("pathway.json");
    }

    // ---------------------------------------------------------------- server side

    @EventBusSubscriber(modid = AstralMod.MOD_ID)
    public static final class Server {
        private Server() {}

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var server = player.getServer();
            if (server == null || server.isDedicatedServer()) return;
            if (Settings.allowSingleplayer) return;
            if (Boolean.getBoolean("astral.allowSingleplayer")) return;

            LOGGER.info("[astral] refusing a singleplayer login; set allow_singleplayer=true to permit it");
            player.connection.disconnect(Component.literal("Astral World is a server pack"));
        }
    }

    // ---------------------------------------------------------------- client side

    @EventBusSubscriber(modid = AstralMod.MOD_ID, value = Dist.CLIENT)
    public static final class Client {
        private Client() {}

        /** What was written last, so the file is only rewritten when something changed. */
        private static String last = "";
        private static int ticks;

        @SubscribeEvent
        public static void onTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            // a pathway changes rarely, and this reads NBT, so twice a minute is plenty
            if (++ticks < 600) return;
            ticks = 0;
            write();
        }

        @SubscribeEvent
        public static void onLeave(ClientPlayerNetworkEvent.LoggingOut event) {
            ticks = 0;
            write();
        }

        private static void write() {
            var mc = Minecraft.getInstance();
            var player = mc.player;
            if (player == null) return;

            String pathway = readPathway(player.saveWithoutId(new CompoundTag()));
            if (pathway.isEmpty()) return;
            int sequence = readSequence(player.saveWithoutId(new CompoundTag()));

            String json = "{\"pathway\":\"" + pathway + "\",\"sequence\":" + sequence + "}";
            if (json.equals(last)) return;

            try {
                Path path = file();
                Files.createDirectories(path.getParent());
                Files.writeString(path, json);
                last = json;
            } catch (Throwable t) {
                // the launcher simply keeps whatever colour it had; not worth a stack trace
                LOGGER.debug("[astral] could not write {}: {}", file(), t.toString());
            }
        }
    }

    // ---------------------------------------------------------------- reading LOTM

    /**
     * Finds the pathway id inside LOTM's attachment.
     *
     * <p>Rather than guess which key holds it, every string in the compound is checked
     * against the known pathway ids. That survives LOTM renaming the field, which it has
     * done before.
     */
    static String readPathway(CompoundTag root) {
        CompoundTag beyonder = beyonder(root);
        if (beyonder == null) return "";
        for (String key : beyonder.getAllKeys()) {
            Tag tag = beyonder.get(key);
            if (tag == null || tag.getId() != Tag.TAG_STRING) continue;
            String value = tag.getAsString().toLowerCase().replace("_", "");
            for (String id : Lotm.PATH_IDS) {
                if (value.contains(id.replace("_", ""))) return id;
            }
        }
        return "";
    }

    static int readSequence(CompoundTag root) {
        CompoundTag beyonder = beyonder(root);
        if (beyonder == null || !beyonder.contains("currentSequence")) return -1;
        return beyonder.getInt("currentSequence");
    }

    private static CompoundTag beyonder(CompoundTag root) {
        if (!root.contains("neoforge:attachments", Tag.TAG_COMPOUND)) return null;
        CompoundTag attachments = root.getCompound("neoforge:attachments");
        if (!attachments.contains("lotm:beyonder_class", Tag.TAG_COMPOUND)) return null;
        return attachments.getCompound("lotm:beyonder_class");
    }
}
