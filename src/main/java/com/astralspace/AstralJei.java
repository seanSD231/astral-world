package com.astralspace;

import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Client only, and loaded only when JEI is installed: hides the LOTM potion recipes whose formula this player has not
 * found yet. LOTM's own JEI plugin already hides the potion <em>items</em> that way, but the cauldron recipes are ordinary
 * data-pack recipes that every client knows, so JEI showed all of them and the formulas were no secret.
 *
 * What is unlocked comes from the server ({@link PotionLock}); until the first list arrives nothing is hidden, so a
 * server without this mod's companion side, or without LOTM, simply behaves as JEI always did.
 */
@JeiPlugin
public class AstralJei implements IModPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(AstralMod.MOD_ID, "jei");

    private IJeiRuntime runtime;
    private RecipeType<?> potionType;
    private List<Object> all;           // every potion recipe JEI knows, hidden ones included
    private int seenVersion = -1;

    @Override
    public ResourceLocation getPluginUid() { return UID; }

    @Override
    public void onRuntimeAvailable(IJeiRuntime r) {
        runtime = r;
        all = null;
        seenVersion = -1;
        potionType = lotmPotionType();
        if (potionType == null) { LOGGER.info("[astral] LOTM's JEI potion category is not there, so no recipe is hidden"); return; }
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        all = null;
    }

    /** LOTM's own potion-crafting recipe type, by reflection: this mod does not compile against LOTM. */
    private static RecipeType<?> lotmPotionType() {
        for (String n : new String[]{"net.swimmingtuna.lotm.compat.JEI.PotionCraftingCategory"}) {
            try {
                Object t = Class.forName(n).getField("POTION_CRAFTING_TYPE").get(null);
                if (t instanceof RecipeType<?> rt) return rt;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e) {
        if (runtime == null || potionType == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !PotionLock.known()) return;
        if (seenVersion == PotionLock.version()) return;
        seenVersion = PotionLock.version();
        apply(mc);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void apply(Minecraft mc) {
        var mgr = runtime.getRecipeManager();
        try {
            if (all == null) {
                all = new ArrayList<>();
                mgr.createRecipeLookup((RecipeType) potionType).includeHidden().get().forEach(r -> all.add(r));
            }
            List<Object> hide = new ArrayList<>(), show = new ArrayList<>();
            for (Object r : all) {
                if (!(r instanceof Recipe<?> rec)) continue;
                ItemStack out;
                try { out = rec.getResultItem(mc.level.registryAccess()); } catch (Throwable t) { continue; }
                String key = Lotm.potionKey(out);
                if (key.isEmpty()) continue;             // not a beyonder potion: leave it alone
                (PotionLock.unlocked(key) ? show : hide).add(r);
            }
            if (!show.isEmpty()) mgr.unhideRecipes((RecipeType) potionType, (List) show);
            if (!hide.isEmpty()) mgr.hideRecipes((RecipeType) potionType, (List) hide);
            LOGGER.info("[astral] JEI: {} potion recipes hidden, {} shown", hide.size(), show.size());
        } catch (Throwable t) {
            LOGGER.warn("[astral] could not hide the locked potion recipes in JEI: {}", t.toString());
            potionType = null;   // do not keep trying every second
        }
    }
}
