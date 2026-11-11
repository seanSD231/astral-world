"""Redraws the 22 pathway symbols at 3x (384px) without changing their look: the bright emblem gets a crisp, anti-aliased edge
(smooth upscale + a steep contrast curve, which is in effect a vector trace), while the soft glow and faint circle behind it stay soft.
Sources are LOTM's own 128px textures, kept in tools/art/sym_src/. Run: python3 tools/art/sharpen_syms.py"""
import os, sys, glob
import numpy as np
from PIL import Image, ImageFilter
HERE = os.path.dirname(os.path.abspath(__file__))
SRC, OUT, K = os.path.join(HERE, 'sym_src'), os.path.join(HERE, '../../src/main/resources/assets/astral_world/textures/gui/tarot/'), 3
for f in sorted(glob.glob(os.path.join(SRC, 'sym_*.png'))):
    im = Image.open(f).convert('RGBA'); w, h = im.size
    a = np.asarray(im).astype(np.float32) / 255.0
    pre = a[..., :3] * a[..., 3:4]                                        # premultiplied, so transparent pixels do not bleed colour
    def up(x, blur=0.0):
        g = Image.fromarray((np.clip(x, 0, 1) * 255).astype(np.uint8))
        g = g.resize((w * K, h * K), Image.BICUBIC)
        if blur: g = g.filter(ImageFilter.GaussianBlur(blur))
        return np.asarray(g).astype(np.float32) / 255.0
    lum = (.3 * pre[..., 0] + .59 * pre[..., 1] + .11 * pre[..., 2])
    big = up(lum, .9)                                                     # the emblem's brightness field, smooth
    hi = np.percentile(lum[lum > .05], 92) if (lum > .05).any() else 1.0  # the emblem's own brightness
    thr = hi * .62
    crisp = 1 / (1 + np.exp(-(big - thr) * (26 / max(hi, .2))))           # a steep edge where the emblem ends
    rgbu = np.stack([up(pre[..., i]) for i in range(3)], -1); au = up(a[..., 3])
    # emblem colour: the source colour pushed to full emblem brightness, so the hard edge does not fade to grey
    col = rgbu / np.maximum(au[..., None], 1e-3)
    peak = np.maximum(col.max(-1, keepdims=True), 1e-3)
    emblem = np.clip(col / peak * min(1.0, hi * 1.08 + .05), 0, 1)
    soft_rgb = np.stack([up(pre[..., i], 1.2) for i in range(3)], -1); soft_a = up(a[..., 3], 1.2)
    out_a = np.maximum(soft_a * (1 - crisp), crisp * np.maximum(au, .97))
    soft_col = soft_rgb / np.maximum(soft_a[..., None], 1e-3)
    out_rgb = soft_col * (1 - crisp[..., None]) + emblem * crisp[..., None]
    res = np.concatenate([np.clip(out_rgb, 0, 1), np.clip(out_a, 0, 1)[..., None]], -1)
    Image.fromarray((res * 255).astype(np.uint8), 'RGBA').save(os.path.join(OUT, os.path.basename(f)), optimize=True)
    print('wrote', os.path.basename(f))
