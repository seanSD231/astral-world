package com.astralspace;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * One home for the card art, and the blur that the GUI needs.
 *
 * <p>The art used to exist twice: once under {@code textures/item/} for the item models,
 * and once under {@code textures/gui/tarot/} with a {@code .mcmeta} asking for
 * {@code blur: true} so it does not look harsh when scaled in a screen. The files were
 * byte-identical, 449 of them, and 18 MB of a 61 MB jar.
 *
 * <p>The mcmeta cannot simply move to the item copies: those are stitched into the item
 * atlas, where a blur setting bleeds neighbouring sprites into each other. So the item
 * copy is the only one, and the GUI turns filtering on at runtime instead, which is what
 * the mcmeta was doing anyway.
 */
public final class CardArt {
    private CardArt() {}

    /** The art for one card. Seq 0 is 480x720, every other sequence 240x360. */
    public static ResourceLocation of(String pathway, int seq, boolean shiny) {
        return ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID,
                "textures/item/card_" + pathway + "_" + seq + (shiny ? "_shiny" : "") + ".png");
    }

    /**
     * Loads the texture and asks for bilinear filtering, which is what the old
     * {@code {"texture":{"blur":true}}} meta did. Safe to call repeatedly: the texture
     * manager hands back the same object.
     */
    public static void preload(ResourceLocation art) {
        try {
            var texture = Minecraft.getInstance().getTextureManager().getTexture(art);
            if (texture != null) {
                texture.setFilter(true, false);
            }
        } catch (Throwable ignored) {
            // a missing card should not take a screen down with it
        }
    }

    /** Loads a card's art and sets its filtering in one call. */
    public static ResourceLocation ready(String pathway, int seq, boolean shiny) {
        ResourceLocation art = of(pathway, seq, shiny);
        preload(art);
        return art;
    }
}
