package com.astralspace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the hunger games leaderboard ({@link HgBoard}): a stepped plinth, a slab of dark stone six blocks wide in a
 * gold frame, a pillar each side with a lit brazier on top, and a pediment with a turning gold emblem. The front is
 * cut with the last game (the champion large, with their face, then the places behind them) and, on the right, the
 * champions of the games before. Everything is built from ordinary block models scaled into bars and tiles, so it
 * takes the world's light and any resource pack; the lettering is drawn full-bright so it reads at night.
 */
public class HgBoardRenderer extends EntityRenderer<HgBoard> {
    private static final int FULL = 0xF000F0, GOLD = 0xFFFFC83C, PALE = 0xFFE8E0C8, DIM = 0xFF9A9484, RED = 0xFFFF6A50;
    private final Font font;

    public HgBoardRenderer(EntityRendererProvider.Context ctx) { super(ctx); this.font = ctx.getFont(); }

    @Override public ResourceLocation getTextureLocation(HgBoard e) { return DefaultPlayerSkin.getDefaultTexture(); }

    private void blk(PoseStack ps, MultiBufferSource buf, BlockState st, float cx, float y0, float cz, float w, float h, float d, int light) {
        ps.pushPose(); ps.translate(cx - w / 2, y0, cz - d / 2); ps.scale(w, h, d);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(st, ps, buf, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    /** Text on the slab's face: (x, y) is the top left in blocks, `h` the height of a line of capitals in blocks. */
    private void text(PoseStack ps, MultiBufferSource buf, String s, float x, float y, float h, int color, int align) {
        float sc = h / 8f, w = font.width(s) * sc;
        ps.pushPose(); ps.translate(align == 0 ? x : align == 1 ? x - w / 2 : x - w, y, 0); ps.scale(sc, -sc, sc);
        font.drawInBatch(s, 0, 0, color, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0, FULL);
        ps.popPose();
    }

    private String fit(String s, float h, float maxW) {
        float sc = h / 8f;
        if (font.width(s) * sc <= maxW) return s;
        return font.plainSubstrByWidth(s, (int) (maxW / sc) - font.width("..")) + "..";
    }

    /** A player's face (with the hat layer), `size` blocks across, top left at (x, y). */
    private void face(PoseStack ps, MultiBufferSource buf, String name, float x, float y, float size) {
        ResourceLocation skin = DefaultPlayerSkin.getDefaultTexture();
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) { var info = conn.getPlayerInfo(name); if (info != null) skin = info.getSkin().texture(); }
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(skin));
        var p = ps.last();
        for (int layer = 0; layer < 2; layer++) {
            float u0 = (layer == 0 ? 8 : 40) / 64f, u1 = u0 + 8 / 64f, v0 = 8 / 64f, v1 = 16 / 64f, g = layer * .012f, z = layer * .01f;
            vc.addVertex(p, x - g, y + g, z).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL).setNormal(p, 0, 0, 1);
            vc.addVertex(p, x - g, y - size - g, z).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL).setNormal(p, 0, 0, 1);
            vc.addVertex(p, x + size + g, y - size - g, z).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL).setNormal(p, 0, 0, 1);
            vc.addVertex(p, x + size + g, y + g, z).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL).setNormal(p, 0, 0, 1);
        }
    }

    /**
     * The ladder's own monument, and nothing like the games'. Where that one is a wide gold-framed
     * slab between two braziers, this is a single tall stele of deepslate: a narrow shaft with a
     * quartz face, a dark iron rail down each edge, a lantern at the head instead of fire, and the
     * standing read as one column rather than two. A ladder is a line of names, so it is built as
     * one.
     */
    private void ladderStone(PoseStack ps, MultiBufferSource buf, HgBoard e, int light, float time) {
        BlockState deep = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState tile = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState face = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState iron = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState chain = Blocks.CHAIN.defaultBlockState();

        float W = 3.3f, y0 = 1.0f, H = 6.2f;
        // a square base, stepped in
        blk(ps, buf, tile, 0, 0f,   0, W + 1.5f, .35f, 2.2f, light);
        blk(ps, buf, deep, 0, .35f, 0, W + 0.9f, .30f, 1.7f, light);
        blk(ps, buf, tile, 0, .65f, 0, W + 0.4f, .35f, 1.3f, light);
        // the shaft, tiled so the stone keeps its grain
        for (int ix = 0; ix < 3; ix++) for (int iy = 0; iy < 6; iy++)
            blk(ps, buf, face, -1.1f + ix * 1.1f, y0 + iy, 0, 1.1f, 1f, .36f, light);
        // a dark rail down each edge, and a lintel top and bottom
        for (int sd = -1; sd <= 1; sd += 2) blk(ps, buf, iron, sd * (W / 2f + .12f), y0, 0, .3f, H, .5f, light);
        blk(ps, buf, deep, 0, y0 - .18f, 0, W + .7f, .2f, .56f, light);
        blk(ps, buf, deep, 0, y0 + H,    0, W + .7f, .25f, .56f, light);
        // the head: a lantern slung under a short chain, no fire
        blk(ps, buf, tile, 0, y0 + H + .25f, 0, 1.9f, .3f, .5f, light);
        blk(ps, buf, chain, 0, y0 + H + .55f, 0, .22f, .55f, .22f, light);
        blk(ps, buf, lamp, 0, y0 + H + 1.10f, 0, .78f, .78f, .78f, FULL);
        // and a slowly turning mark above it
        ps.pushPose();
        ps.translate(0, y0 + H + 2.0f + (float) Math.sin(time * .07f) * .05f, 0);
        ps.mulPose(Axis.YP.rotationDegrees(time * 1.4f));
        blk(ps, buf, lamp, 0, -.17f, 0, .34f, .34f, .34f, FULL);
        ps.popPose();

        // ---- one column of names
        List<String[]> res = new ArrayList<>(), other = new ArrayList<>();
        String big = "LADDER", one = "pt", many = "pts", first = "FIRST";
        for (String ln : e.text().split("\n")) {
            String[] f = ln.split("\\|");
            if (f.length >= 7 && f[0].equals("H")) { big = f[1]; one = f[3]; many = f[4]; first = f[6]; }
            else if (f.length >= 4 && f[0].equals("R")) res.add(f);
            else if (f.length >= 4 && f[0].equals("P")) other.add(f);
        }
        ps.pushPose(); ps.translate(0, 0, .19f);
        float left = -W / 2f + .22f, right = W / 2f - .22f, top = y0 + H - .3f;
        text(ps, buf, big, 0, top, big.length() > 12 ? .24f : .30f, DIM_T, 1);
        if (res.isEmpty()) {
            text(ps, buf, "No one has fought yet", 0, top - .9f, .18f, DIM_T, 1);
        } else {
            String[] w = res.get(0);
            face(ps, buf, w[2], left, top - .55f, .78f);
            text(ps, buf, first, left + .95f, top - .60f, .13f, DIM_T, 0);
            text(ps, buf, fit(w[2], .26f, right - left - 1.05f), left + .95f, top - .80f, .26f, INK_T, 0);
            text(ps, buf, w[3] + " " + (w[3].equals("1") ? one : many), left + .95f, top - 1.14f, .15f, DIM_T, 0);
            for (int i = 1; i < Math.min(res.size(), 9); i++) {
                String[] r = res.get(i);
                float ry = top - 1.75f - (i - 1) * .30f;
                text(ps, buf, "#" + r[1], left, ry, .17f, i < 3 ? INK_T : DIM_T, 0);
                text(ps, buf, fit(r[2], .17f, right - left - 1.25f), left + .45f, ry, .17f, INK_T, 0);
                text(ps, buf, r[3], right, ry, .17f, DIM_T, 2);
            }
        }
        float oy = y0 + .55f;
        for (int i = 0; i < Math.min(other.size(), 2); i++) {
            String[] h = other.get(i);
            text(ps, buf, fit(h[3], .13f, W - .5f), left, oy - i * .34f, .13f, DIM_T, 0);
            text(ps, buf, fit(h[1], .15f, W - .5f) + "  " + h[2], left, oy - i * .34f - .17f, .15f, INK_T, 0);
        }
        ps.popPose();
    }

    /** Ink for the quartz face: dark, where the games' board is gold on black. */
    private static final int INK_T = 0xFF14161C, DIM_T = 0xFF4A5260;

    @Override
    public void render(HgBoard e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        BlockState dark = Blocks.POLISHED_BLACKSTONE.defaultBlockState(), brick = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
            gold = Blocks.GOLD_BLOCK.defaultBlockState(), gilt = Blocks.GILDED_BLACKSTONE.defaultBlockState(),
            chis = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), slabSt = Blocks.SMOOTH_BASALT.defaultBlockState(), fire = Blocks.CAMPFIRE.defaultBlockState();
        float time = e.tickCount + pt;
        boolean ladder = !e.src().isEmpty();
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180 - e.getYRot()));

        if (ladder) { ladderStone(ps, buf, e, light, time); ps.popPose(); super.render(e, yaw, pt, ps, buf, light); return; }

        // the plinth: three steps
        blk(ps, buf, brick, 0, 0f, 0, 8.2f, .3f, 2.4f, light);
        blk(ps, buf, dark, 0, .3f, 0, 7.6f, .25f, 1.8f, light);
        blk(ps, buf, gold, 0, .55f, 0, 7.2f, .08f, 1.3f, light);
        blk(ps, buf, brick, 0, .63f, 0, 7.0f, .27f, 1.1f, light);
        float y0 = .9f, H = 4f, W = 6f;
        // the slab, tiled so the stone keeps its grain
        for (int ix = 0; ix < 6; ix++) for (int iy = 0; iy < 4; iy++) blk(ps, buf, slabSt, -2.5f + ix, y0 + iy, 0, 1f, 1f, .4f, light);
        // the gold frame
        blk(ps, buf, gold, 0, y0 - .02f, 0, W + .3f, .14f, .56f, light);
        blk(ps, buf, gold, 0, y0 + H - .12f, 0, W + .3f, .14f, .56f, light);
        blk(ps, buf, gold, -.05f + .55f, y0 + .2f, .18f, .04f, H - .9f, .1f, light);   // the line between the two halves
        // the pillars, with a brazier each
        for (int sd = -1; sd <= 1; sd += 2) {
            float px = sd * 3.35f;
            blk(ps, buf, dark, px, .63f, 0, .9f, .3f, .9f, light);
            for (int k = 0; k < 4; k++) blk(ps, buf, k == 1 ? gilt : chis, px, .93f + k, 0, .62f, 1f, .62f, light);
            blk(ps, buf, gold, px, 4.93f, 0, .8f, .12f, .8f, light);
            blk(ps, buf, dark, px, 5.05f, 0, .95f, .2f, .95f, light);
            blk(ps, buf, fire, px, 5.25f, 0, .8f, .8f, .8f, FULL);
        }
        // the pediment and its emblem
        blk(ps, buf, brick, 0, y0 + H + .02f, 0, W + .6f, .3f, .7f, light);
        blk(ps, buf, dark, 0, y0 + H + .32f, 0, 4.2f, .3f, .6f, light);
        blk(ps, buf, brick, 0, y0 + H + .62f, 0, 2.2f, .3f, .5f, light);
        blk(ps, buf, gold, 0, y0 + H + .92f, 0, 1.0f, .1f, .45f, light);
        ps.pushPose(); ps.translate(0, y0 + H + 1.55f + (float) Math.sin(time * .08f) * .06f, 0); ps.mulPose(Axis.YP.rotationDegrees(time * 2.2f)); ps.mulPose(Axis.ZP.rotationDegrees(45)); ps.mulPose(Axis.XP.rotationDegrees(35.26f));
        blk(ps, buf, gold, 0, -.26f, 0, .52f, .52f, .52f, FULL);
        ps.popPose();

        // ---- the lettering, a hair in front of the slab
        List<String[]> res = new ArrayList<>(), past = new ArrayList<>();
        // Headings and unit words: a ladder sends its own, the hunger games send none and keep these.
        String big = "HUNGER GAMES", side = "PAST CHAMPIONS", one = "kill", many = "kills", group = "tributes", first = "CHAMPION";
        for (String ln : e.text().split("\n")) {
            String[] f = ln.split("\\|");
            if (f.length >= 7 && f[0].equals("H")) { big = f[1]; side = f[2]; one = f[3]; many = f[4]; group = f[5]; first = f[6]; }
            else if (f.length >= 4 && f[0].equals("R")) res.add(f);
            else if (f.length >= 4 && f[0].equals("P")) past.add(f);
        }
        ps.pushPose(); ps.translate(0, 0, .215f);
        float top = y0 + H - .22f, left = -W / 2 + .2f, mid = .55f, right = W / 2 - .2f;
        text(ps, buf, big, (left + mid) / 2, top, big.length() > 13 ? .26f : .34f, GOLD, 1);
        text(ps, buf, side, (mid + right) / 2 + .05f, top - .06f, .2f, GOLD, 1);
        if (res.isEmpty()) {
            text(ps, buf, "Nobody yet", (left + mid) / 2, top - 1.6f, .26f, DIM, 1);
            text(ps, buf, "The first name goes here", (left + mid) / 2, top - 2.0f, .13f, DIM, 1);
        } else {
            String[] w = res.get(0);
            float fy = top - .55f, fs = .95f;
            face(ps, buf, w[2], left + .05f, fy, fs);
            text(ps, buf, first, left + fs + .25f, fy - .02f, .15f, DIM, 0);
            int pulse = 0xFF000000 | (255 << 16) | ((int) (190 + 40 * Math.sin(time * .1f)) << 8) | 60;
            text(ps, buf, fit(w[2], .3f, mid - left - fs - .4f), left + fs + .25f, fy - .24f, .3f, pulse, 0);
            text(ps, buf, w[3] + (w[3].equals("1") ? " " + one : " " + many) + "   of " + res.size() + " " + group, left + fs + .25f, fy - .66f, .15f, PALE, 0);
            for (int i = 1; i < Math.min(res.size(), 9); i++) {
                String[] r = res.get(i); float ry = fy - fs - .22f - (i - 1) * .245f;
                text(ps, buf, "#" + r[1], left + .05f, ry, .16f, i < 3 ? GOLD : DIM, 0);
                text(ps, buf, fit(r[2], .16f, mid - left - 1.35f), left + .5f, ry, .16f, PALE, 0);
                text(ps, buf, r[3], mid - .15f, ry, .16f, RED, 2);
            }
        }
        if (past.isEmpty()) text(ps, buf, "none yet", (mid + right) / 2 + .05f, top - 1.0f, .16f, DIM, 1);
        for (int i = 0; i < Math.min(past.size(), 6); i++) {
            String[] h = past.get(i); float ry = top - .5f - i * .52f;
            text(ps, buf, fit(h[1], .19f, right - mid - .3f), mid + .2f, ry, .19f, i == 0 ? GOLD : PALE, 0);
            text(ps, buf, h[2] + (h[2].equals("1") ? " " + one : " " + many) + " \u00B7 " + h[3], mid + .2f, ry - .25f, .12f, DIM, 0);
        }
        ps.popPose();
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }
}
