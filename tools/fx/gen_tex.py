#!/usr/bin/env python3
"""Textures the generated effects use. Usage: gen_tex.py <textures/holo dir>"""
import sys, os
from PIL import Image, ImageDraw, ImageFilter
out = sys.argv[1]; S = 256
def bar(d, box, v): d.rectangle(box, fill=v)
# Bearing of Sins cross: white so the effect tints it; a soft red-ready glow around a bright narrow core
base = Image.new('L', (S, S), 0); d = ImageDraw.Draw(base)
bar(d, (S*.43, S*.04, S*.57, S*.96), 255); bar(d, (S*.2, S*.34, S*.8, S*.46), 255)
glow = base.filter(ImageFilter.GaussianBlur(12)).point(lambda v: min(255, int(v*1.5)))
core = Image.new('L', (S, S), 0); d = ImageDraw.Draw(core)
bar(d, (S*.475, S*.08, S*.525, S*.92), 255); bar(d, (S*.24, S*.375, S*.76, S*.425), 255)
core = core.filter(ImageFilter.GaussianBlur(2))
a = Image.eval(glow, lambda v: int(v*.55))
a = Image.composite(Image.new('L', (S, S), 255), a, core)
rgb = Image.merge('RGB', [Image.composite(Image.new('L', (S, S), 255), Image.new('L', (S, S), 255), core)]*3)
im = Image.merge('RGBA', (*rgb.split(), a)); im.save(os.path.join(out, 'sin_cross.png')); print('sin_cross.png')
