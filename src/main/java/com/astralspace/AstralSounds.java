package com.astralspace;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/** The mod's own sound events. */
public final class AstralSounds {
    private AstralSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, AstralMod.MOD_ID);

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, name)));
    }

    /** The tracks an admin card's hologram plays, one after another, over and over. */
    public static final List<DeferredHolder<SoundEvent, SoundEvent>> ADMIN = List.of(
        reg("admin.track_1"), reg("admin.track_2"));
    /** How long each of them runs, in ticks, so the next one starts when the last has finished. */
    public static final int[] ADMIN_LEN = {3544, 1636};

    /** Seven seconds cut from the first track, at its strongest passage: the admin pack's own reveal. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ADMIN_REVEAL = reg("admin.reveal");

    public static ResourceLocation adminId(int i) {
        return ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "admin.track_" + (i + 1));
    }

    public static void register(IEventBus bus) { SOUNDS.register(bus); }
}
