package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Client only: what the osu screens share. */
final class OsuClient {
    private OsuClient() {}

    static BlockPos cabinet = null;
    /** What to do with the next level code the server sends: "play", "edit" or "copy". */
    static String pending = "";

    static void codeArrived(String code) {
        Minecraft mc = Minecraft.getInstance();
        try {
            OsuLevel l = OsuLevel.decode(code);
            switch (pending) {
                case "edit" -> mc.setScreen(new OsuEditor(l, mc.screen));
                case "copy" -> { mc.keyboardHandler.setClipboard(code); say("Code copied. Paste it to anyone."); }
                default -> mc.setScreen(new OsuPlay(l, mc.screen));
            }
        } catch (IllegalArgumentException e) {
            say("That level is broken: " + e.getMessage());
        }
        pending = "";
    }

    static void say(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(s), true);
    }

    static void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload p) { PacketDistributor.sendToServer(p); }

    // ------------------------------------------------------------------ local levels (config folder astral_osu)

    static Path dir() {
        Path d = Minecraft.getInstance().gameDirectory.toPath().resolve("astral_osu");
        try { Files.createDirectories(d); } catch (IOException ignored) { }
        return d;
    }

    static List<String> localNames() {
        List<String> out = new ArrayList<>();
        try (var s = Files.list(dir())) {
            s.filter(p -> p.toString().endsWith(".osu")).forEach(p -> { String n = p.getFileName().toString(); out.add(n.substring(0, n.length() - 4)); });
        } catch (IOException ignored) { }
        java.util.Collections.sort(out);
        return out;
    }

    static String fileName(String name) {
        String n = name.replaceAll("[^A-Za-z0-9 _.-]", "_").trim();
        return n.isEmpty() ? "level" : n;
    }

    static void save(OsuLevel l) throws IOException {
        Files.writeString(dir().resolve(fileName(l.name) + ".osu"), l.encode());
    }

    static OsuLevel load(String file) throws IOException {
        return OsuLevel.decode(Files.readString(dir().resolve(file + ".osu")));
    }

    static void delete(String file) {
        try { Files.deleteIfExists(dir().resolve(file + ".osu")); } catch (IOException ignored) { }
    }
}
