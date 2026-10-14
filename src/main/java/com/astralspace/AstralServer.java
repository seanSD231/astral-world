package com.astralspace;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Astral World rules:
 *  1. Anyone below Seq 0 in the Astral World gains rapid corruption on one pathway (LOTM kills them at max
 *     corruption). Exempt: Seq 0, Door (Apprentice) Seq 3 and stronger, creative and spectator players.
 *  2. Reaching Seq 0 teleports you to the Astral World. /astral travels between the worlds (Seq 0, or Door Seq 3+).
 *     Positions are mirrored: you arrive at the same X/Z you left from.
 *  3. The Astral Barrier's HP only rises, +1 per second per Seq 0 in the Astral World (+3 for Door).
 *     Max HP = 10 x the number of Seq 0 players. Every Seq 0 sees it on the HUD.
 */
@EventBusSubscriber(modid = AstralMod.MOD_ID)
public final class AstralServer {
    public static final ResourceLocation ASTRAL_ID = ResourceLocation.fromNamespaceAndPath("astral_space", "astral_space");
    public static final ResourceKey<Level> ASTRAL = ResourceKey.create(Registries.DIMENSION, ASTRAL_ID);

    private static final int DOOR_SURVIVES_AT_OR_BELOW = 3;
    private static final int REGEN_NORMAL = 1;
    private static final int REGEN_DOOR = 3;
    /** Seconds until corruption is maxed, by Sequence. */
    private static final Map<Integer, Double> SECONDS_TO_MAX = Map.of(
            1, 3.0, 2, 2.5, 3, 2.0, 4, 2.0, 5, 1.5, 6, 1.5, 7, 1.0, 8, 1.0, 9, 0.5);

    private static int tickCounter = 0;
    private static final Map<UUID, Object> visitPathway = new HashMap<>(); // chosen pathway per visit
    private static final Set<UUID> hudShown = new HashSet<>();

    private AstralServer() {}

    // ------------------------------------------------------------------ helpers

    private static boolean inAstral(ServerPlayer p) { return p.level().dimension().equals(ASTRAL); }

    private static boolean exempt(ServerPlayer p) { return p.isCreative() || p.isSpectator(); }

    private static String name(ServerPlayer p) { return p.getGameProfile().getName(); }

    /** Door (Apprentice) at Seq 3 or stronger, and not Seq 0 handling. */
    private static boolean doorSafe(ServerPlayer p, int seq) {
        return seq >= 0 && seq <= DOOR_SURVIVES_AT_OR_BELOW && Lotm.isDoor(p);
    }

    private static void title(ServerPlayer p, String title, String subtitle, ChatFormatting color) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle).withStyle(ChatFormatting.GRAY)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title).withStyle(color, ChatFormatting.BOLD)));
    }

    private static ServerLevel levelOf(MinecraftServer server, String dim) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dim)));
    }

    private static String dimLabel(String dim) {
        if (dim.equals("minecraft:overworld")) return "Overworld";
        String n = dim.substring(dim.indexOf(':') + 1).replace("the_", "").replace('_', ' ');
        return n.isEmpty() ? dim : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    // ------------------------------------------------------------------ travel

    public static void goToAstral(MinecraftServer server, ServerPlayer p) {
        ServerLevel astral = server.getLevel(ASTRAL);
        if (astral == null) {
            p.sendSystemMessage(Component.literal("The Astral World is not loaded. Is the mod installed correctly?").withStyle(ChatFormatting.RED));
            return;
        }
        AstralData d = AstralData.get(server);
        d.returns.put(p.getUUID(), new AstralData.Return(p.level().dimension().location().toString(), p.getX(), p.getY(), p.getZ()));
        d.setDirty();
        p.teleportTo(astral, p.getX(), 1.0, p.getZ(), p.getYRot(), p.getXRot());
    }

    public static void leaveAstral(MinecraftServer server, ServerPlayer p) {
        AstralData d = AstralData.get(server);
        AstralData.Return r = d.returns.get(p.getUUID());
        String dim = r == null ? "minecraft:overworld" : r.dim();
        ServerLevel target = levelOf(server, dim);
        if (target == null) target = server.overworld();
        double y;
        if (target.dimension().equals(Level.OVERWORLD) || r == null) {
            // Ground at the mirrored X/Z, searched only inside the overworld's height band (see Settings), because
            // with OneDimension the overworld, Nether and End are stacked in one dimension. Loads the chunk.
            int found = Surface.find(target, (int) Math.floor(p.getX()), (int) Math.floor(p.getZ()), true);
            if (found < 0) {
                // nothing but air in that column: fall back to where they came from, or the world spawn
                found = r != null ? (int) r.y() : target.getSharedSpawnPos().getY();
            }
            y = found;
        } else {
            y = r.y(); // Nether/End etc. have no safe "surface": use the height they left from
        }
        p.teleportTo(target, p.getX(), y, p.getZ(), p.getYRot(), p.getXRot());
    }

    /** "To Astral World: x y z" / "To Overworld: x y z" for the HUD. */
    private static String destinationText(MinecraftServer server, ServerPlayer p) {
        int x = (int) Math.floor(p.getX()), z = (int) Math.floor(p.getZ());
        if (!inAstral(p)) return "To Astral World: " + x + ", 1, " + z;
        AstralData.Return r = AstralData.get(server).returns.get(p.getUUID());
        String dim = r == null ? "minecraft:overworld" : r.dim();
        ServerLevel target = levelOf(server, dim);
        String y = "~";
        if (target != null) {
            if (target.dimension().equals(Level.OVERWORLD) || r == null) {
                int found = Surface.find(target, x, z, false); // never loads chunks just for the display
                if (found >= 0) y = String.valueOf(found);
            } else {
                y = String.valueOf((int) r.y());
            }
        }
        return "To " + dimLabel(dim) + ": " + x + ", " + y + ", " + z;
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        tickCounter++;
        if (tickCounter % 10 == 0) corruptionPass(server);
        if (tickCounter >= 20) {
            tickCounter = 0;
            secondPass(server);
        }
    }

    /** Twice a second: corruption for non-exempt players in the Astral World. */
    private static void corruptionPass(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            if (exempt(p) || !inAstral(p)) { visitPathway.remove(id); continue; }
            int seq = Lotm.sequence(p);
            if (seq <= 0 || doorSafe(p, seq)) { visitPathway.remove(id); continue; }

            if (!visitPathway.containsKey(id)) {
                Object pathway = Lotm.randomPathway();
                if (pathway == null) continue;
                visitPathway.put(id, pathway); // one pathway for the whole visit, so it actually reaches max
                p.displayClientMessage(Component.literal("You do not belong here...").withStyle(ChatFormatting.DARK_PURPLE), true);
            }
            double toMax = SECONDS_TO_MAX.getOrDefault(seq, 0.5);
            double percent = 100.0 / Math.max(1.0, toMax / 0.5);
            Lotm.addCorruptionPercent(p, visitPathway.get(id), percent);
        }
    }

    /** Once a second: track Seq 0s, ascension teleport, barrier regeneration, HUD. */
    private static void secondPass(MinecraftServer server) {
        AstralData d = AstralData.get(server);
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        boolean dirty = false;

        for (ServerPlayer p : players) {
            int seq = Lotm.sequence(p);
            if (seq < 0) continue;
            UUID id = p.getUUID();
            Integer prev = d.lastSeq.put(id, seq);
            if (prev == null || prev != seq) dirty = true;
            if (seq == 0) {
                if (d.seq0.put(id, name(p)) == null) {
                    dirty = true;
                    if (d.stage == 0) Stage.set(server, 1); // the first Seq 0 ever: the Outer Gods take notice
                }
            } else if (d.seq0.remove(id) != null) {
                dirty = true;
            }
            if (prev != null && prev > 0 && seq == 0) {
                goToAstral(server, p);
                title(p, "Ascension", "The Astral World calls. Use /astral to travel", ChatFormatting.LIGHT_PURPLE);
            }
        }

        // regeneration: only while a Seq 0 is in the Astral World
        int gain = 0;
        for (ServerPlayer p : players) {
            if (exempt(p) || !inAstral(p) || Lotm.sequence(p) != 0) continue;
            gain += Lotm.isDoor(p) ? REGEN_DOOR : REGEN_NORMAL;
        }
        int max = AstralBarrier.getMax(server);
        int hp = d.hp;
        if (gain > 0) hp = Math.min(max, hp + gain);
        else if (hp > max) hp = max; // fewer Seq 0s than before
        if (hp != d.hp) { d.hp = hp; dirty = true; }
        if (dirty) d.setDirty();

        Stage.notifyPass(server);

        // HUD: every Seq 0 sees the barrier; anyone who stopped being Seq 0 gets it hidden once
        for (ServerPlayer p : players) {
            UUID id = p.getUUID();
            if (Lotm.sequence(p) == 0) {
                hudShown.add(id);
                PacketDistributor.sendToPlayer(p, new Net.HudPayload(true, d.hp, max, destinationText(server, p)));
            } else if (hudShown.remove(id)) {
                PacketDistributor.sendToPlayer(p, new Net.HudPayload(false, 0, 0, ""));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        hudShown.remove(id);
        visitPathway.remove(id);
    }

    // ------------------------------------------------------------------ commands

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("astralmod").executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.literal("Astral World mod is loaded."), false);
            return 1;
        }));

        event.getDispatcher().register(Commands.literal("astral")
                .then(Commands.literal("check").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    int seq = Lotm.sequence(p);
                    String msg = "Seq: " + seq + "  Pathway: " + Lotm.pathwayName(p) + "  Door safe: " + doorSafe(p, seq);
                    ctx.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.LIGHT_PURPLE), false);
                    return 1;
                }))
                .executes(ctx -> travel(ctx)));

        event.getDispatcher().register(Commands.literal("astralbarrier")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("info").executes(ctx -> {
                    MinecraftServer s = ctx.getSource().getServer();
                    String msg = "Astral Barrier: " + AstralBarrier.get(s) + " / " + AstralBarrier.getMax(s)
                            + " HP (" + AstralData.get(s).seq0.size() + " Seq 0)";
                    ctx.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.LIGHT_PURPLE), false);
                    return 1;
                }))
                .then(Commands.literal("set").then(Commands.argument("hp", IntegerArgumentType.integer(0)).executes(ctx -> {
                    MinecraftServer s = ctx.getSource().getServer();
                    int hp = AstralBarrier.set(s, IntegerArgumentType.getInteger(ctx, "hp"));
                    ctx.getSource().sendSuccess(() -> Component.literal("Astral Barrier set to " + hp + " / " + AstralBarrier.getMax(s)), true);
                    return 1;
                })))
                .then(Commands.literal("add").then(Commands.argument("hp", IntegerArgumentType.integer()).executes(ctx -> {
                    MinecraftServer s = ctx.getSource().getServer();
                    int hp = AstralBarrier.heal(s, IntegerArgumentType.getInteger(ctx, "hp"));
                    ctx.getSource().sendSuccess(() -> Component.literal("Astral Barrier: " + hp + " / " + AstralBarrier.getMax(s)), true);
                    return 1;
                }))));
    }

    private static int travel(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = src.getPlayerOrException();
        MinecraftServer server = src.getServer();
        int seq = Lotm.sequence(p);
        boolean allowed = seq == 0 || doorSafe(p, seq) || src.hasPermission(2);
        if (!allowed) {
            src.sendFailure(Component.literal("Only a Seq 0, or a Door Seq 3 or stronger, can walk the Astral World."));
            return 0;
        }
        if (inAstral(p)) {
            leaveAstral(server, p);
            title(p, "Returned", "You leave the Astral World", ChatFormatting.GRAY);
        } else {
            goToAstral(server, p);
            title(p, "Astral World", "You step between worlds", ChatFormatting.LIGHT_PURPLE);
        }
        return 1;
    }
}
