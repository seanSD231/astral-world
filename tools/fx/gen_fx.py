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

MPF = 0.4
def emitter(name, tex, color, size, life, rate=0, maxp=200, shape='sphere', radius=1.0, thick=1.0, sscale=(1, 1, 1), speed=0, lin=(0, 0, 0),
            noise=(0.05, 0.3), alpha=(0, 0, .25, 1, .6, 1, 1, 0), grow=None, add=False, burst=0, hold=False, pos=(0, 0, 0), rot=None, mode=None):
    """color: (rgb, alpha) or ((rgb, alpha), (rgb, alpha)) for a random pick. size/life/speed: number or (min, max). rate is per tick."""
    rate = rate * MPF; maxp = max(8, int(maxp * MPF))
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
    emitter('nebula blue #area', PUFF, ((0x2A5CFF, .36), (0x3A8CFF, .32)), (.16, .3), (90, 140), rate=3.2, maxp=380, radius=1.0, thick=.4, sscale=(1, .3, 1), speed=(.0002, .0006), pos=(0, .8, 0), noise=(.006, .15), add=True),
    emitter('nebula pink #area', PUFF, ((0xC040C8, .32), (0xFF60B0, .28)), (.16, .28), (90, 140), rate=2.6, maxp=320, radius=1.0, thick=.4, sscale=(1, .3, 1), speed=(.0002, .0006), pos=(0, .85, 0), noise=(.006, .15), add=True),
    emitter('nebula cyan #area', PUFF, ((0x30D0E8, .28), (0x60F0E0, .24)), (.14, .26), (90, 140), rate=2.0, maxp=260, radius=1.0, thick=.4, sscale=(1, .3, 1), speed=(.0002, .0006), pos=(0, .8, 0), noise=(.006, .15), add=True),
    emitter('ribbons #detail', LT + 'streak.png', ((0xFFFFFF, .8), (0xD8F4FF, .7)), (.004, .01), (70, 130), rate=.3, maxp=60, radius=1.0, sscale=(1, .7, 1), speed=(.003, .01), pos=(0, .4, 0), noise=(.02, .4), add=True, mode='StretchedBillboard'),
    emitter('far lights #detail', GLOW, ((0xFFFFFF, .4), (0x90C0FF, .4)), (.005, .012), (100, 180), rate=3, maxp=900, radius=1.0, thick=.25, sscale=(1, .7, 1), pos=(0, .4, 0), noise=(.002, .3), add=True),
    emitter('motes #detail', EMBER, ((0xFFFFFF, .8), (0xA8E8FF, .8)), (.002, .005), (80, 140), rate=3, maxp=900, radius=1.0, sscale=(1, .7, 1), pos=(0, .35, 0), lin=(0, .0006, 0), noise=(.003, .3), add=True),
])

# ---- Fool hall, thick around the table (unit = the hall's half length): the grey fog hanging low over the floor and between the pillars
write('fool_hall_fog', [
    emitter('floor fog #area', PUFF, ((0x30383C, .2), (0x3E474B, .17)), (.09, .17), (50, 90), rate=28, maxp=1800, radius=1.0, sscale=(.6, .1, 1), speed=(.0005, .002), lin=(.0015, .0005, 0), noise=(.012, .25), pos=(0, .03, 0)),
    emitter('table mist #area', PUFF, (0x394146, .09), (.14, .24), (60, 100), rate=6, maxp=500, radius=1.0, sscale=(.6, .3, 1), speed=(.0004, .0012), lin=(-.001, .0008, 0), noise=(.012, .2), pos=(0, .16, 0)),
    emitter('hall lights #detail', GLOW, ((0x70D8E0, .7), (0xD0B8FF, .6)), (.01, .026), (60, 120), rate=.5, maxp=120, radius=1.0, sscale=(.6, .5, 1), pos=(0, .25, 0), lin=(0, .0008, 0), noise=(.006, .3), add=True),
])

# ---- Seer: a smaller, paler mist with a few drifting lights (unit = the scene's radius)
write('seer_fog', [
    emitter('mist #area', PUFF, ((0x666C8C, .4), (0x7C86AE, .3)), (.3, .55), (40, 70), rate=14, maxp=500, radius=1.0, sscale=(1, .12, 1), speed=(.001, .004), lin=(.003, .002, 0), noise=(.03, .25), pos=(0, .3, 0)),
    emitter('high mist #area', PUFF, (0x8A94BC, .12), (.6, 1.0), (50, 90), rate=5, maxp=180, radius=1.1, sscale=(1, .45, 1), speed=(.001, .003), lin=(-.003, .002, 0), noise=(.04, .2), pos=(0, .6, 0)),
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
    emitter('smoke #area', PUFF, (0x1A0A08, .18), (.3, .6), (40, 70), rate=1, maxp=50, radius=.6, sscale=(1, .3, 1), speed=(.002, .01), lin=(0, .02, 0)),
])

# ---- Anarchy: spark shards thrown out, dust, a flicker of light
write('anarchy_sparks', [
    light('spark light', 0xFFD060, 4.0, 3),
    emitter('sparks #area', LT + 'streak.png', ((0xFFE070, .95), (0xFFFFFF, .95)), (.02, .05), (8, 18), rate=3, maxp=240, radius=.5, speed=(.04, .12), noise=(0, 0), add=True, mode='StretchedBillboard'),
    emitter('dust #area', PUFF, (0x3A3A44, .25), (.3, .6), (50, 90), rate=2.5, maxp=150, radius=1.0, sscale=(1, .1, 1), speed=(.001, .004), lin=(.004, .002, 0)),
])

# ---- Sky stars (unit = the sky's radius): round, many-coloured dots that twinkle, on a shell over the whole sphere
TW = (0, 0, .12, 1, .35, .45, .6, 1, .85, .5, 1, 0)
write('star_dots', [
    emitter('stars white', EMBER, ((0xFFFFFF, 1.0), (0xA8D8FF, 1.0)), (.004, .011), (200, 400), rate=.9, maxp=340, radius=1.0, thick=0.0, sscale=(1, .75, 1), pos=(0, .12, 0), noise=(0, 0), alpha=TW, add=True),
    emitter('stars pink', EMBER, ((0xFF70C0, 1.0), (0xB070FF, 1.0)), (.004, .01), (200, 400), rate=.5, maxp=200, radius=1.0, thick=0.0, sscale=(1, .75, 1), pos=(0, .12, 0), noise=(0, 0), alpha=TW, add=True),
    emitter('stars gold', EMBER, ((0xFFD060, 1.0), (0x60F0D0, 1.0)), (.004, .01), (200, 400), rate=.5, maxp=200, radius=1.0, thick=0.0, sscale=(1, .75, 1), pos=(0, .12, 0), noise=(0, 0), alpha=TW, add=True),
])

# ---- The door of light (unit = about half the door's height): white mist and motes inside it, and a low white fog spilling out
write('door_light', [
    light('door light', 0xFFFFFF, 2.2, 8, vol=1.4),
    emitter('white mist #area', PUFF, ((0xFFFFFF, .2), (0xDCE8FF, .18)), (.12, .26), (40, 80), rate=4, maxp=300, radius=.5, sscale=(1, 1.3, 1), speed=(.001, .004), noise=(.01, .3), add=True),
    emitter('light motes #detail', EMBER, ((0xFFFFFF, .9), (0xCFE4FF, .9)), (.006, .016), (60, 120), rate=2, maxp=250, radius=.7, sscale=(1, 1.2, 1), lin=(0, .002, 0), noise=(.004, .3), add=True),
    emitter('spill fog #area', PUFF, ((0xE8F0FF, .16), (0xFFFFFF, .14)), (.15, .3), (50, 90), rate=3, maxp=200, radius=.8, sscale=(1, .12, 1), pos=(0, -.9, 0), speed=(.001, .003), noise=(.01, .25), add=True),
])

# ---- A ball of storms (unit = its radius): a white-blue core in dark rolling thunderheads, lightning bolts and streaks flickering all through it
BT = HT + 'bolt.png'; BT2 = HT + 'bolt2.png'
write('storm_ball', [
    light('storm light', 0x9AB8FF, 1.6, 9, vol=1.2),
    emitter('core #hold', GLOW, ((0xFFFFFF, 1.0)), .8, 36000, burst=1, maxp=2, shape='dot', add=True, noise=(0, 0), rot=0, alpha=(0, 1, 1, 1), hold=True, mode='Billboard'),
    emitter('core halo #hold', GLOW, ((0x7A9CFF, .8)), 1.9, 36000, burst=1, maxp=2, shape='dot', add=True, noise=(0, 0), rot=0, alpha=(0, 1, 1, 1), hold=True, mode='Billboard'),
    emitter('thunderheads #area', PUFF, ((0x303A5C, .6), (0x46527A, .55)), (.3, .55), (30, 60), rate=10, maxp=240, radius=.8, thick=1.0, speed=(.001, .006), noise=(.05, .3)),
    emitter('pale cloud #area', PUFF, ((0x9AA8D8, .4), (0xB8C4F0, .35)), (.25, .45), (24, 44), rate=6, maxp=120, radius=.85, thick=.4, speed=(.001, .004), noise=(.05, .3)),
    emitter('bolts #area', BT, ((0xE8F2FF, 1.0), (0xB8D0FF, 1.0)), (.22, .45), (4, 9), rate=3.2, maxp=60, radius=.7, thick=.7, noise=(0, 0), add=True, rot=0, alpha=(0, 1, .4, 1, .4, 0, 1, 0), mode='Billboard'),
    emitter('bolts 2 #area', BT2, ((0xFFFFFF, 1.0), (0xA8C0FF, 1.0)), (.18, .4), (4, 8), rate=2.6, maxp=50, radius=.8, thick=.6, noise=(0, 0), add=True, rot=0, alpha=(0, 1, .4, 1, .4, 0, 1, 0), mode='Billboard'),
    emitter('streaks #area', LT + 'streak.png', ((0xFFFFFF, 1.0), (0x9AB8FF, 1.0)), (.03, .07), (6, 14), rate=6, maxp=140, radius=.35, thick=.5, speed=(.05, .14), noise=(0, 0), add=True, mode='StretchedBillboard'),
    emitter('sparks #detail', EMBER, ((0xFFFFFF, .95), (0xB0CCFF, .95)), (.01, .03), (12, 30), rate=4, maxp=200, radius=.9, thick=.5, speed=(.004, .02), noise=(.02, .4), add=True),
])

# ---- Twilight sword strike (unit = a block): a crescent flash, amber and violet streaks thrown out, a flare and drifting motes
write('twilight_hit', [
    light('dusk flash', 0xFFA050, 3.0, 8),
    emitter('flare #hold', FLARE, ((0xFFE0B0, 1.0)), 1.6, 36000, burst=1, maxp=2, shape='dot', add=True, noise=(0, 0), rot=0, alpha=(0, 1, .5, 0), hold=True, mode='Billboard'),
    emitter('amber streaks #area', LT + 'streak.png', ((0xFFB040, 1.0), (0xFF7A28, 1.0)), (.04, .09), (8, 16), rate=0, burst=22, maxp=40, radius=.1, speed=(.08, .22), noise=(0, 0), add=True, mode='StretchedBillboard'),
    emitter('violet streaks #area', LT + 'streak.png', ((0xB070FF, 1.0), (0x7A40E0, 1.0)), (.04, .09), (8, 16), rate=0, burst=22, maxp=40, radius=.1, speed=(.08, .22), noise=(0, 0), add=True, mode='StretchedBillboard'),
    emitter('motes #area', EMBER, ((0xFFE0A0, .95), (0xD0A0FF, .95)), (.03, .08), (20, 40), rate=0, burst=16, maxp=40, radius=.2, speed=(.01, .05), lin=(0, .01, 0), noise=(.03, .4), add=True),
])

# ---- The Twilight crescent wave, carried along its path (unit = a block): a big dusk crescent with a violet and amber wake
write('twilight_wave', [
    light('wave light', 0xFF9A50, 3.0, 6),
    emitter('wake #area', GLOW, ((0xFF9040, .8), (0x9A60FF, .8)), (.4, .9), (10, 22), rate=6, maxp=80, radius=1.2, thick=1.0, sscale=(1, 1, 1), speed=(.002, .01), noise=(.04, .3), add=True),
    emitter('sparks #area', EMBER, ((0xFFE0A0, .95), (0xD0A0FF, .95)), (.05, .12), (14, 30), rate=5, maxp=90, radius=1.3, thick=.6, speed=(.004, .02), noise=(.03, .4), add=True),
])

# ---- Waterspout: a tall spinning column of spray and mist (unit = height)
write('waterspout', [
    emitter('spray #area', PUFF, ((0xBFE4FF, .5), (0x7FB8E8, .45)), (.08, .16), (30, 50), rate=9, maxp=500, radius=1.0, sscale=(.1, .5, .1), speed=(.002, .008), lin=(0, .02, 0), noise=(.04, .3), pos=(0, .5, 0)),
    emitter('drops #detail', EMBER, (0xE8F6FF, .9), (.006, .014), (30, 60), rate=5, maxp=200, radius=1.0, sscale=(.12, .5, .12), speed=(.002, .01), lin=(0, .015, 0), noise=(.05, .3), pos=(0, .5, 0), add=True),
    emitter('foot foam #area', PUFF, (0xEAF6FF, .45), (.15, .28), (30, 50), rate=3, maxp=120, radius=.4, sscale=(1, .2, 1), speed=(.003, .01), pos=(0, .03, 0)),
])

# ================= every sprites(...) call of the hologram renderer as a Photon effect =================
# HoloRenderer.sprites() asks for "astral_world:sp/<p|g><index>_<texture>_<mode>_<seed>[_g]" (unit = the call's radius, origin at its y0) and only draws its
# own sprites when that effect is missing. This reads the calls straight out of the Java source, so the two never drift apart: rerun after changing a call.
import re
JAVA = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../src/main/java/com/astralspace/HoloRenderer.java')
SRC = open(JAVA).read()
def table(name):
    body = SRC[SRC.index(name + ' = {'):]; body = body[:body.index('};')]
    return [[int(x, 16) for x in re.findall(r'0x([0-9A-Fa-f]{6})', row)] for row in re.findall(r'\{([^{}]*)\}', body)]
PALS, GOOPALS = table('int[][] PAL'), table('int[][] GOOPAL')
SPEC = [int(x, 16) for x in re.findall(r'0x([0-9A-Fa-f]{6})', SRC[SRC.index('SPECTRUM = {'):SRC.index('};', SRC.index('SPECTRUM = {'))])]
FN = {'moon': 'p0', 'door': 'p1', 'justiciar': 'p2', 'demoness': 'p3', 'rare': 'p3', 'epic': 'p3', 'sun': 'p4', 'death': 'p5', 'abyss': 'p6', 'redPriest': 'p7', 'blackEmperor': 'p8',
      'error': 'p9', 'wheel': 'p10', 'hermit': 'p11', 'mother': 'p12', 'chained': 'p13', 'whiteTower': 'p14', 'sailor': 'p15', 'paragon': 'p16', 'hangedMan': 'p17', 'fool': 'p18',
      'darkness': 'p19', 'visionary': 'p20', 'twilight': 'p21', 'gooLord': 'g0', 'gooAlmighty': 'g1', 'gooDarkness2': 'g2', 'gooCalamity': 'g3', 'gooKnowledge': 'g4', 'gooKey': 'g5',
      'gooGoddess': 'g6', 'gooDevils': 'g7', 'gooAnarchy': 'g8', 'gooScene': 'g*'}
def mix(a, b, f):
    return (int(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f) << 16) | (int(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f) << 8) | int((a & 255) * (1 - f) + (b & 255) * f)
def gmap(rgb):
    l = min(1.0, (.3 * ((rgb >> 16) & 255) + .59 * ((rgb >> 8) & 255) + .11 * (rgb & 255)) / 255 * 1.3)
    return mix(0x050403, 0x9A6A10, l / .3) if l < .3 else mix(0x9A6A10, 0xFFD448, (l - .3) / .45) if l < .75 else mix(0xFFD448, 0xFFF0A0, (l - .75) / .25)
def split_args(s):
    out, depth, cur = [], 0, ''
    for ch in s:
        if ch == ',' and depth == 0: out.append(cur.strip()); cur = ''
        else:
            depth += ch in '([' ; depth -= ch in ')]'; cur += ch
    out.append(cur.strip()); return out
def ev(expr, env):
    e = re.sub(r'\(m \? ([^:]+) : ([^)]+)\)', r'(\2)', expr)
    e = re.sub(r'(\d*\.?\d+)f\b', r'\1', e).replace('(float)', '').replace('Math.', '')
    return eval(e, {'__builtins__': {}}, env)
MODES = {'FALLM': 0, 'RISEM': 1, 'SPIRALM': 2, 'ORBITM': 3, 'RADIALM': 4, 'DRIFTM': 5}
def sprite_fx(tex, mode, h, size, rgb, alpha, speed, n):
    """One sprites() call in units of its radius: h = height of the column, size = sprite size, n = how many are alive at once."""
    L = max(30.0, min(240.0, 20.0 / max(.02, speed))); sec = L / 20.0
    smoke = tex == 'smoke'
    texture = PUFF if smoke else HT + tex + '.png'
    col = (rgb, min(1.0, alpha * (.35 if smoke else 1.0)))
    kw = dict(rate=n / L / MPF, maxp=int(n * 1.7 / MPF) + 8, add=not smoke, noise=(.02, .3))
    sz, life = (size * .6, size * 1.4), (L * .75, L * 1.3)
    if mode == 0:   o = emitter('fall #area', texture, col, sz, life, radius=1.0, sscale=(1, .02, 1), pos=(0, h, 0), lin=(0, -h / sec, 0), **kw)
    elif mode == 1: o = emitter('rise #area', texture, col, sz, life, radius=1.0, sscale=(1, .02, 1), pos=(0, 0, 0), lin=(0, h / sec, 0), **kw)
    elif mode == 2:
        o = emitter('spiral #area', texture, col, sz, life, radius=1.0, thick=.5, sscale=(1, .02, 1), pos=(0, 0, 0), lin=(0, h / sec, 0), **kw)
        v = o['data'][1]['config'][1]['velocityOverLifetime'][1]
        put(o['data'][1]['config'][1], 'velocityOverLifetime/orbital/1', const(8.0 / sec)); v['radial'] = const(-.55 / sec)
    elif mode == 4: o = emitter('burst #area', texture, col, sz, life, radius=.04, speed=(.7 / sec, 1.1 / sec), **kw)
    else:           o = emitter('drift #area', texture, col, sz, (70, 130), radius=1.0, sscale=(1, max(.05, h / 2), 1), pos=(0, h / 2, 0), rate=n / 100 / MPF, maxp=int(n * 1.7 / MPF) + 8, add=not smoke, noise=(.05, .2))
    return o
def scene_env(key, gold):
    goo = key[0] == 'g'; pal = (GOOPALS if goo else PALS)[int(key[1:])]
    P = [0xFFD448, 0x9A6A10, 0xFFF0B8] if gold else pal
    R, top, S, CY = (12.0, 19.0, 2.35, 7.4) if goo else (6.0, 8.5, 1.15, 3.2)
    env = dict(R=R, top=top, S=S, CY=CY, N=1.5, P1=P[0], P2=P[1], P3=P[2], PUR=0xA050FF, mix=mix, max=max, min=min, SPECTRUM=SPEC, H=2.3, gy=CY + 2.4 * S)
    env['iron'] = mix(0x707890, P[0], .15); env['wy'] = max(top * .5, 3.0 * S + .5)
    return env
made = 0
for mt in re.finditer(r'sprites\("(\w+)", ([^;]*)\);', SRC):
    fn = re.findall(r'private void (\w+)\(', SRC[:mt.start()])[-1]
    if fn not in FN: continue
    args = split_args(mt.group(2))
    keys = ['g%d' % i for i in range(9)] if FN[fn] == 'g*' else [FN[fn]]
    for key in keys:
        for gold in ((False, True) if key[0] == 'p' else (False,)):
            for k in (range(7) if re.search(r'\bk\b', mt.group(2)) else (None,)):
                env = scene_env(key, gold)
                if k is not None: env['k'] = k
                try:
                    n, rad, y0, y1, size, rgb, alpha, speed, seed = ev(args[0], env), ev(args[2], env), ev(args[3], env), ev(args[4], env), ev(args[5], env), int(ev(args[6], env)), ev(args[7], env), ev(args[8], env), int(ev(args[9], env))
                except Exception as ex:
                    print('skipped', fn, mt.group(0)[:60], ex); continue
                mode = MODES[args[1]]
                if gold: rgb = gmap(rgb)
                h = abs(y1 - y0) / rad if mode != 4 else 1.0
                write('sp/%s_%s_%d_%d%s' % (key, mt.group(1), mode, seed, '_g' if gold else ''), [sprite_fx(mt.group(1), mode, h, size / rad, rgb, alpha, speed, n)])
                made += 1
print('sprite effects:', made)

# ---- the low fog ring of a scene (HoloRenderer.fogRing): "sp/<p|g><index>_fog[_g]", unit = the ring's radius
for mt in re.finditer(r'private void (\w+)\(', SRC):
    fn = mt.group(1)
    if fn not in FN or FN[fn] == 'g*': continue
    body = SRC[mt.end():]; nxt = re.search(r'\n    private ', body); body = body[:nxt.start()] if nxt else body
    call = re.search(r'fogRing\(([^;]*)\);', body)
    if not call: continue
    args = split_args(call.group(1))
    for gold in ((False, True) if FN[fn][0] == 'p' else (False,)):
        env = scene_env(FN[fn], gold)
        try: rgb, a = int(ev(args[2], env)), ev(args[3], env)
        except Exception as ex: print('skipped fog', fn, ex); continue
        if gold: rgb = gmap(rgb)
        write('sp/%s_fog%s' % (FN[fn], '_g' if gold else ''), [
            emitter('fog ring #area', PUFF, ((rgb, min(.6, a * .75)), (mix(rgb, 0xFFFFFF, .12), min(.5, a * .6))), (.45, .85), (60, 100), rate=1.6 / MPF, maxp=int(220 / MPF), radius=1.0, thick=.35, sscale=(1, .06, 1), speed=(.004, .015), lin=(.01, .004, 0), noise=(.04, .2), pos=(0, .22, 0)),
            emitter('fog wisps #area', PUFF, (mix(rgb, 0xFFFFFF, .25), min(.3, a * .4)), (.25, .5), (50, 90), rate=.6 / MPF, maxp=int(80 / MPF), radius=1.0, thick=.6, sscale=(1, .12, 1), speed=(.004, .012), lin=(-.008, .01, 0), noise=(.05, .25), pos=(0, .3, 0)),
        ])

# ---- fog over a scene's whole floor (HoloRenderer.floorFog): "sp/<p|g><index>_floor[_g]", unit = the floor's radius
for mt in re.finditer(r'private void (\w+)\(', SRC):
    fn = mt.group(1)
    if fn not in FN or FN[fn] == 'g*': continue
    body = SRC[mt.end():]; nxt = re.search(r'\n    private ', body); body = body[:nxt.start()] if nxt else body
    at = body.find('floorFog(')
    if at < 0: continue
    depth, j = 1, at + 9
    while depth: depth += body[j] == '('; depth -= body[j] == ')'; j += 1
    args = split_args(body[at + 9:j - 1])
    for gold in ((False, True) if FN[fn][0] == 'p' else (False,)):
        env = scene_env(FN[fn], gold)
        rgb, a = int(ev(args[3], env)), ev(args[4], env)
        if gold: rgb = gmap(rgb)
        write('sp/%s_floor%s' % (FN[fn], '_g' if gold else ''), [
            emitter('floor fog #area', PUFF, ((rgb, min(.6, a * 2.2)), (mix(rgb, 0xFFFFFF, .1), min(.5, a * 1.8))), (.1, .2), (70, 110), rate=7 / MPF, maxp=int(900 / MPF), radius=1.0, thick=1.0, sscale=(1, .015, 1), speed=(.001, .004), lin=(.006, .0015, 0), noise=(.015, .2), pos=(0, .02, 0)),
            emitter('low wisps #area', PUFF, (mix(rgb, 0xFFFFFF, .2), min(.3, a * 1.1)), (.07, .14), (60, 100), rate=2.5 / MPF, maxp=int(320 / MPF), radius=1.0, thick=1.0, sscale=(1, .04, 1), speed=(.001, .004), lin=(-.005, .003, 0), noise=(.02, .25), pos=(0, .05, 0)),
        ])
