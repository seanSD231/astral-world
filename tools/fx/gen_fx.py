#!/usr/bin/env python3
"""Writes this mod's own Photon effects (assets/astral_world/fx/*.fx).

Every emitter is a copy of one of LOTM's own emitters (nightmare_fog / flaring_sun / sin_bearing_glow) with only leaf values changed, so the
file keeps a structure Photon already loads. Sizes are for a unit-sized effect; the hologram passes the real scale when it plays it.
Usage: gen_fx.py <unzipped LOTM jar> <out dir>"""
import copy, os, sys, uuid
import nbtt

LOTM, OUT = sys.argv[1], sys.argv[2]
N = lambda v: (5, float(v))
I = lambda v: (3, int(v))
S = lambda v: (8, v)
def load(p): return nbtt.load(os.path.join(LOTM, 'assets/lotm/fx', p))
def objs(d): return d[1]['fxData'][1]['fxObjects'][1][1]
FOG = objs(load('spectator/nightmare_fog.fx'))[1]                        # circle emitter, plain colour, drifts upward
BURST = [o for o in objs(load('bard/flaring_sun.fx')) if o['data'][1]['name'][1] == 'rays #hold'][0]['data'][1]['config'][1]['emission'][1]['bursts']
LIGHTS = objs(load('secrets_supplicant/sin_bearing_glow.fx'))
ROOT = load('spectator/nightmare_fog.fx')

def argb(rgb, a=1.0):
    v = (int(round(a * 255)) << 24) | rgb
    return v - (1 << 32) if v >= 1 << 31 else v
def const(v): return (10, {'data': (10, {'number': N(v)}), 'type': S('constant')})
def rnd(a, b): return (10, {'data': (10, {'a': N(a), 'b': N(b)}), 'type': S('random_constant')})
def constI(v): return (10, {'data': (10, {'number': I(v)}), 'type': S('constant')})
def num(x): return rnd(*x) if isinstance(x, (tuple, list)) else const(x)
def put(c, path, node):
    """Set a leaf by path. c is a compound's dict; list elements (shape/scale/0 ...) are compounds stored as plain dicts."""
    cur = c
    keys = path.split('/')
    for i, k in enumerate(keys):
        last = i == len(keys) - 1
        if isinstance(cur, dict):
            if last: cur[k] = node; return
            cur = cur[k][1]
        else:   # a list tag (elem type, [elements])
            if last: cur[1][int(k)] = node[1]; return
            cur = cur[1][int(k)]
def uid(o): o['data'][1]['transform'][1]['id'] = S(str(uuid.uuid4()))

def emitter(name, tex, color, size, life, rate=0, maxp=200, shape='sphere', radius=1.0, thick=1.0, sscale=(1, 1, 1), speed=0, lin=(0, 0, 0),
            noise=(0.05, 0.3), alpha=(0, 0, .25, 1, .6, 1, 1, 0), grow=None, add=False, burst=0, hold=False, pos=(0, 0, 0), rot=None, mode=None):
    """color: (rgb, alpha) or ((rgb, alpha), (rgb, alpha)) for a random pick. size/life/speed: number or (min, max). rate is per tick."""
    o = copy.deepcopy(FOG); uid(o)
    d = o['data'][1]; d['name'] = S(name); d['transform'][1]['localPosition'] = (9, (5, [float(x) for x in pos]))
    c = d['config'][1]
    pay = c['renderer'][1]['materials'][1]['payload'][1][1][0]
    pay['material'][1]['data'][1]['texture'] = S(tex)
    pay['blendMode'][1]['dstColorFactor'] = S('ONE' if add else 'ONE_MINUS_SRC_ALPHA')
    if mode: c['renderer'][1]['renderMode'] = S(mode)
    if isinstance(color[0], tuple): put(c, 'startColor', (10, {'data': (10, {'a': I(argb(*color[0])), 'b': I(argb(*color[1]))}), 'type': S('random_color')}))
    else: put(c, 'startColor', (10, {'data': (10, {'number': I(argb(*color))}), 'type': S('color')}))
    for i in range(3): put(c, 'startSize/%d' % i, num(size))
    put(c, 'startLifetime', num(life)); put(c, 'startSpeed', num(speed))
    put(c, 'emission/emissionRate', const(rate)); c['maxParticles'] = I(maxp)
    if burst:
        b = copy.deepcopy(BURST); b[1]['payload'][1][1][0]['count'][1]['data'][1]['number'] = N(burst); c['emission'][1]['bursts'] = b
    c['duration'] = I(36000 if hold else 40); c['looping'] = (1, 1)
    sh = c['shape'][1]; sh['shape'][1]['type'] = S(shape)
    if shape != 'dot':
        dd = sh['shape'][1]['data'][1]; dd['radius'] = N(radius); dd['radiusThickness'] = N(thick)
    for i in range(3): put(c, 'shape/scale/%d' % i, constI(sscale[i]) if float(sscale[i]).is_integer() else const(sscale[i]))
    c['noise'][1]['_enable'] = (1, 1 if noise[0] > 0 else 0)
    for i in range(3): put(c, 'noise/position/%d' % i, const(noise[0]))
    c['noise'][1]['frequency'] = N(noise[1])
    v = c['velocityOverLifetime'][1]
    v['_enable'] = (1, 1 if any(lin) else 0)
    for i in range(3): put(c, 'velocityOverLifetime/linear/%d' % i, const(lin[i]))
    put(c, 'colorOverLifetime/color/data/gradientColor/a', (9, (5, [float(x) for x in alpha])))
    if grow is None: c['sizeOverLifetime'][1]['_enable'] = (1, 0)
    else:
        c['sizeOverLifetime'][1]['_enable'] = (1, 1)
        for i in range(3): c['sizeOverLifetime'][1]['size'][1][1][i]['data'][1]['upper'] = N(grow)
    if rot is not None:
        for i in range(2): put(c, 'startRotation/%d' % i, constI(0))
        put(c, 'startRotation/2', constI(rot))
    return o

def light(name, rgb, rng, power, pos=(0, 0, 0), vol=1.0):
    o = copy.deepcopy(LIGHTS[0]); uid(o)
    d = o['data'][1]; d['name'] = S(name); d['transform'][1]['localPosition'] = (9, (5, [float(x) for x in pos]))
    c = d['config'][1]
    c['color'][1]['data'][1]['color'] = (9, (5, [((rgb >> 16) & 255) / 255, ((rgb >> 8) & 255) / 255, (rgb & 255) / 255, 1.0]))
    c['range'][1]['data'][1]['number'] = N(rng)
    c['intensity'][1]['data'][1]['upper'] = N(power)
    c['volumetric'][1]['strength'][1]['data'][1]['number'] = N(vol)
    return o

def write(name, objects):
    r = copy.deepcopy(ROOT)
    r[1]['fxData'][1]['fxObjects'] = (9, (10, objects))
    p = os.path.join(OUT, name + '.fx'); os.makedirs(os.path.dirname(p), exist_ok=True); nbtt.save(p, r); print('wrote', p, len(objects), 'objects')

LT = 'lotm:textures/particle/fx/'
HT = 'astral_world:textures/holo/'
PUFF, WISP, EMBER, FLARE, GLOW = LT + 'puff.png', LT + 'wisp.png', LT + 'ember_hd.png', LT + 'flare.png', LT + 'glow.png'

# ---- Bearing of Sins: a burning red cross and the Hanged Man's flower facing whoever looks, red light, falling petals and embers (unit = its height)
write('bearing_of_sins', [
    light('heart light', 0xFF5A4A, .7, 6, vol=1.6), light('cross light', 0xE0142A, 1.5, 5), light('wide light', 0x8A1020, 3.0, 3, pos=(0, -.6, 0), vol=.5),
    emitter('flower #hold', 'lotm:textures/effect/hanged_man_symbol.png', ((0xFFFFFF, .55)), 1.0, 36000, burst=1, maxp=2, shape='dot', add=True, noise=(0, 0), rot=0, alpha=(0, 1, 1, 1), hold=True, mode='Billboard'),
    emitter('cross #hold', HT + 'sin_cross.png', (0xFF2030, 1.0), 1.0, 36000, burst=1, maxp=2, shape='dot', add=True, noise=(0, 0), rot=0, alpha=(0, 1, 1, 1), hold=True, mode='Billboard'),
    emitter('cross core #hold', HT + 'sin_cross.png', (0xFFC0C8, 1.0), .42, 36000, burst=1, maxp=2, shape='dot', add=True, noise=(0, 0), rot=0, alpha=(0, 1, 1, 1), hold=True, mode='Billboard'),
    emitter('halo #hold', GLOW, (0xFF2030, .55), 2.2, 36000, burst=1, maxp=2, shape='dot', add=True, noise=(0, 0), rot=0, alpha=(0, 1, 1, 1), hold=True, mode='Billboard'),
    emitter('embers #detail', EMBER, ((0xFF5060, .9), (0xFFB0A0, .9)), (.025, .05), (30, 60), rate=.8, maxp=120, radius=.5, speed=(.002, .01), lin=(0, -.01, 0), add=True),
    emitter('petals #detail', HT + 'petal.png', ((0xFF7090, .9), (0xE03050, .9)), (.05, .09), (50, 90), rate=.15, maxp=40, radius=.55, sscale=(1, .2, 1), pos=(0, .55, 0), lin=(0, -.03, 0), noise=(.04, .4)),
    emitter('red mist #area', PUFF, (0xC01020, .25), (.4, .7), (36, 54), rate=.5, maxp=60, radius=.5, speed=(.002, .006), lin=(0, .004, 0)),
])

# ---- Fool hall, whole sphere (unit = the sphere's radius, so the effect is played at about 3.6 R): a grey, dark, thin fog at every height with
# lights and motes spread all the way out, so it reads the same wherever you stand
write('fool_fog', [
    emitter('low fog #area', PUFF, ((0x2C3236, .16), (0x394044, .13)), (.045, .09), (60, 100), rate=14, maxp=1300, radius=1.0, thick=1.0, sscale=(1, .12, 1), speed=(.0004, .0015), lin=(.002, .0004, 0), noise=(.01, .2), pos=(0, .02, 0)),
    emitter('mid fog #area', PUFF, ((0x252B2F, .12), (0x30373B, .1)), (.07, .13), (70, 110), rate=8, maxp=900, radius=1.0, sscale=(1, .45, 1), speed=(.0004, .0012), lin=(-.0015, .0006, 0), noise=(.01, .2), pos=(0, .3, 0)),
    emitter('high fog #area', PUFF, ((0x1C2124, .14), (0x262C30, .12)), (.09, .16), (80, 120), rate=6, maxp=700, radius=1.0, sscale=(1, .5, 1), speed=(.0004, .001), lin=(.001, 0, 0), noise=(.01, .2), pos=(0, .65, 0)),
    emitter('lights #detail', GLOW, ((0x9AD8DC, .65), (0xD8E0FF, .55)), (.006, .016), (80, 160), rate=2.5, maxp=700, radius=1.0, sscale=(1, .6, 1), pos=(0, .35, 0), lin=(0, .0006, 0), noise=(.004, .3), add=True),
    emitter('red lights #detail', GLOW, ((0xFF2030, .9), (0xFF4458, .8)), (.004, .01), (80, 170), rate=1.4, maxp=450, radius=1.0, sscale=(1, .7, 1), pos=(0, .35, 0), lin=(0, .0005, 0), noise=(.004, .3), add=True),
    emitter('nebula blue #area', PUFF, ((0x2A5CFF, .14), (0x3A8CFF, .12)), (.12, .24), (90, 140), rate=2.2, maxp=260, radius=1.0, thick=.4, sscale=(1, .3, 1), speed=(.0002, .0006), pos=(0, .8, 0), noise=(.006, .15), add=True),
    emitter('nebula pink #area', PUFF, ((0xC040C8, .12), (0xFF60B0, .1)), (.12, .22), (90, 140), rate=1.8, maxp=220, radius=1.0, thick=.4, sscale=(1, .3, 1), speed=(.0002, .0006), pos=(0, .85, 0), noise=(.006, .15), add=True),
    emitter('nebula cyan #area', PUFF, ((0x30D0E8, .1), (0x60F0E0, .08)), (.1, .2), (90, 140), rate=1.4, maxp=180, radius=1.0, thick=.4, sscale=(1, .3, 1), speed=(.0002, .0006), pos=(0, .8, 0), noise=(.006, .15), add=True),
    emitter('ribbons #detail', LT + 'streak.png', ((0xFFFFFF, .8), (0xD8F4FF, .7)), (.004, .01), (70, 130), rate=.3, maxp=60, radius=1.0, sscale=(1, .7, 1), speed=(.003, .01), pos=(0, .4, 0), noise=(.02, .4), add=True, mode='StretchedBillboard'),
    emitter('far lights #detail', GLOW, ((0xFFFFFF, .4), (0x90C0FF, .4)), (.005, .012), (100, 180), rate=3, maxp=900, radius=1.0, thick=.25, sscale=(1, .7, 1), pos=(0, .4, 0), noise=(.002, .3), add=True),
    emitter('motes #detail', EMBER, ((0xFFFFFF, .8), (0xA8E8FF, .8)), (.002, .005), (80, 140), rate=3, maxp=900, radius=1.0, sscale=(1, .7, 1), pos=(0, .35, 0), lin=(0, .0006, 0), noise=(.003, .3), add=True),
])

# ---- Fool hall, thick around the table (unit = the hall's half length): the grey fog hanging low over the floor and between the pillars
write('fool_hall_fog', [
    emitter('floor fog #area', PUFF, ((0x30383C, .2), (0x3E474B, .17)), (.09, .17), (50, 90), rate=12, maxp=800, radius=1.0, sscale=(.6, .1, 1), speed=(.0005, .002), lin=(.0015, .0005, 0), noise=(.012, .25), pos=(0, .03, 0)),
    emitter('table mist #area', PUFF, (0x394146, .09), (.14, .24), (60, 100), rate=3, maxp=250, radius=1.0, sscale=(.6, .3, 1), speed=(.0004, .0012), lin=(-.001, .0008, 0), noise=(.012, .2), pos=(0, .16, 0)),
    emitter('hall lights #detail', GLOW, ((0x70D8E0, .7), (0xD0B8FF, .6)), (.01, .026), (60, 120), rate=.5, maxp=120, radius=1.0, sscale=(.6, .5, 1), pos=(0, .25, 0), lin=(0, .0008, 0), noise=(.006, .3), add=True),
])

# ---- Seer: a smaller, paler mist with a few drifting lights (unit = the scene's radius)
write('seer_fog', [
    emitter('mist #area', PUFF, ((0x4A4F68, .4), (0x606890, .3)), (.3, .55), (40, 70), rate=14, maxp=500, radius=1.0, sscale=(1, .12, 1), speed=(.001, .004), lin=(.003, .002, 0), noise=(.03, .25), pos=(0, .06, 0)),
    emitter('high mist #area', PUFF, (0x707AA0, .12), (.6, 1.0), (50, 90), rate=5, maxp=180, radius=1.1, sscale=(1, .45, 1), speed=(.001, .003), lin=(-.003, .002, 0), noise=(.04, .2), pos=(0, .3, 0)),
    emitter('lights #detail', GLOW, ((0xE8D0FF, .6), (0xB0C8FF, .5)), (.012, .03), (60, 110), rate=.4, maxp=70, radius=1.0, sscale=(1, .5, 1), pos=(0, .3, 0), lin=(0, .002, 0), noise=(.02, .3), add=True),
    emitter('motes #detail', EMBER, (0xFFFFFF, .8), (.004, .01), (60, 100), rate=.7, maxp=110, radius=1.0, sscale=(1, .5, 1), pos=(0, .3, 0), lin=(0, .002, 0), noise=(.02, .3), add=True),
])

# ---- Sleepless: a wide, thin dark mist spread to the edge of the dark dome, with faint stars (unit = the scene's radius)
write('sleepless_fog', [
    emitter('dark mist #area', PUFF, ((0x151A2A, .45), (0x202840, .4)), (.35, .6), (60, 100), rate=22, maxp=900, radius=1.0, sscale=(1, .1, 1), speed=(.001, .003), lin=(.002, .001, 0), noise=(.03, .2), pos=(0, .05, 0)),
    emitter('far mist #area', PUFF, (0x2A3350, .14), (.6, 1.1), (70, 110), rate=8, maxp=300, radius=1.8, thick=.4, sscale=(1, .22, 1), speed=(.001, .002), noise=(.03, .2), pos=(0, .1, 0)),
    emitter('stars #detail', EMBER, ((0xFFFFFF, .9), (0xB0B8FF, .9)), (.006, .014), (60, 120), rate=.8, maxp=160, radius=1.9, thick=.5, sscale=(1, .8, 1), pos=(0, .7, 0), noise=(.005, .2), add=True),
])

# ---- Eternal Darkness: black smoke rolling low with cold lights carried in it
write('dark_mist', [
    emitter('smoke #area', PUFF, ((0x0A0A12, .6), (0x1C1C26, .5)), (.3, .55), (50, 80), rate=22, maxp=800, radius=1.0, sscale=(1, .1, 1), speed=(.001, .004), lin=(.004, .001, 0), noise=(.03, .25), pos=(0, .05, 0)),
    emitter('pale lights #detail', GLOW, ((0xD0D8FF, .55), (0xFFE8B0, .45)), (.012, .03), (60, 110), rate=.3, maxp=50, radius=1.0, sscale=(1, .4, 1), pos=(0, .2, 0), lin=(.004, .001, 0), noise=(.02, .3), add=True),
    emitter('stars #detail', EMBER, (0xFFFFFF, .9), (.005, .012), (60, 120), rate=.7, maxp=140, radius=1.5, thick=.5, sscale=(1, 1, 1), pos=(0, .6, 0), noise=(.005, .2), add=True),
])

# ---- Key of Light: gold and spectrum motes rising, bright glints
write('light_motes', [
    light('warm light', 0xFFE8A0, 4.0, 4),
    emitter('motes #area', EMBER, ((0xFFE8A0, .95), (0x90E0FF, .95)), (.02, .045), (40, 80), rate=2.5, maxp=300, radius=1.0, sscale=(1, .2, 1), speed=(.002, .01), lin=(0, .02, 0), add=True),
    emitter('glints #detail', FLARE, ((0xFFFFFF, .9), (0xFFD890, .9)), (.05, .12), (14, 30), rate=.6, maxp=60, radius=1.0, sscale=(1, .8, 1), noise=(0, 0), add=True, rot=0),
    emitter('soft glow #area', GLOW, (0xFFF0C0, .12), (.4, .8), (50, 80), rate=1.0, maxp=60, radius=.8, sscale=(1, .6, 1), lin=(0, .003, 0), add=True),
])

# ---- Calamity of Destruction: embers thrown up, black smoke, red firelight
write('calamity_embers', [
    light('fire light', 0xFF6A20, 5.0, 5, vol=.8),
    emitter('embers #area', EMBER, ((0xFF4A10, .95), (0xFFC060, .95)), (.02, .05), (20, 50), rate=4, maxp=400, radius=.7, sscale=(1, .3, 1), speed=(.01, .04), lin=(0, .03, 0), add=True),
    emitter('smoke #area', PUFF, (0x1A0A08, .4), (.4, .8), (50, 90), rate=3, maxp=200, radius=.6, sscale=(1, .3, 1), speed=(.002, .01), lin=(0, .02, 0)),
])

# ---- Anarchy: spark shards thrown out, dust, a flicker of light
write('anarchy_sparks', [
    light('spark light', 0xFFD060, 4.0, 3),
    emitter('sparks #area', LT + 'streak.png', ((0xFFE070, .95), (0xFFFFFF, .95)), (.02, .05), (8, 18), rate=3, maxp=240, radius=.5, speed=(.04, .12), noise=(0, 0), add=True, mode='StretchedBillboard'),
    emitter('dust #area', PUFF, (0x3A3A44, .25), (.3, .6), (50, 90), rate=2.5, maxp=150, radius=1.0, sscale=(1, .1, 1), speed=(.001, .004), lin=(.004, .002, 0)),
])
