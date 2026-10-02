"""Phase 1 machine, generator, energy cube and cable blocks for tools/gen_content.py.

generate(g, gc) is called from Gen.run(): g is the Gen instance (item/lang helpers), gc the gen_content module
(paths, save_png, avg_color, ...). Everything is original procedural art (Pillow), nothing is read from the vanilla assets.

Machines and cubes are plain Cube blocks with one texture per face (VariantRotation NESW), so the face that carries the
"North" texture is the front. Each machine has a lit front under State "Active". Cables are Model blocks: 64 shared
.blockymodel files (one per 6-bit connection mask) selected through the states "C1".."C63".
"""
import json
import math
import random

from PIL import Image, ImageDraw

N = 32
CASING = "Techtale_Steel_Casing"
WORKBENCH = {"Type": "Crafting", "Id": "Workbench", "Categories": ["Workbench_Crafting"]}

STEEL = (76, 81, 91)
EDGE = (26, 28, 33)
HI = (118, 124, 136)
SH = (40, 43, 50)
RECESS = (30, 33, 39)
METAL_LIGHT = (170, 176, 188)
METAL_MID = (110, 114, 126)

# (shadow, base, highlight)
TIER_COL = {
    "Basic": ((70, 74, 82), (150, 156, 168), (214, 220, 230)),
    "Advanced": ((92, 24, 20), (205, 60, 48), (255, 150, 130)),
    "Elite": ((14, 52, 92), (46, 150, 220), (160, 230, 255)),
    "Ultimate": ((52, 18, 84), (140, 70, 205), (226, 170, 255)),
}
TIERS = ["Basic", "Advanced", "Elite", "Ultimate"]
QUALITY = {"Basic": "Common", "Advanced": "Uncommon", "Elite": "Rare", "Ultimate": "Epic"}
CUBE_STATS = {"Basic": ("4 MJ", "4,000"), "Advanced": ("16 MJ", "16,000"),
              "Elite": ("64 MJ", "64,000"), "Ultimate": ("256 MJ", "256,000")}
CABLE_RATE = {"Basic": "8,000", "Advanced": "128,000", "Elite": "1,024,000", "Ultimate": "8,192,000"}
CUBE_UPGRADE = {  # tier -> (alloy, circuit, extra item)
    "Advanced": ("Techtale_Alloy_Infused", "Techtale_Circuit_Advanced", "Ingredient_Bar_Gold"),
    "Elite": ("Techtale_Alloy_Reinforced", "Techtale_Circuit_Elite", "Rock_Gem_Diamond"),
    "Ultimate": ("Techtale_Alloy_Atomic", "Techtale_Circuit_Ultimate", "Techtale_Ingot_Refined_Obsidian"),
}
CABLE_UPGRADE = {"Advanced": ("Basic", "Techtale_Alloy_Infused"), "Elite": ("Advanced", "Techtale_Alloy_Reinforced"),
                 "Ultimate": ("Elite", "Techtale_Alloy_Atomic")}


# ----------------------------------------------------------------------------------------------
# Small pixel helpers
# ----------------------------------------------------------------------------------------------

def sc(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c[:3])


def mixc(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def put(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), tuple(c[:3]) + (255,))


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(im, x, y, c)


def frame(im, x0, y0, x1, y1, c):
    for x in range(x0, x1 + 1):
        put(im, x, y0, c)
        put(im, x, y1, c)
    for y in range(y0, y1 + 1):
        put(im, x0, y, c)
        put(im, x1, y, c)


def noisy(im, x0, y0, x1, y1, base, rnd, j=3):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            d = rnd.randint(-j, j)
            put(im, x, y, tuple(max(0, min(255, v + d)) for v in base))


def base_plate(rnd, base=STEEL, rivets=True):
    im = Image.new("RGBA", (N, N))
    noisy(im, 0, 0, N - 1, N - 1, base, rnd, 4)
    frame(im, 0, 0, N - 1, N - 1, EDGE)
    for i in range(1, N - 1):
        put(im, i, 1, HI)
        put(im, 1, i, HI)
        put(im, i, N - 2, SH)
        put(im, N - 2, i, SH)
    if rivets:
        for cx, cy in ((3, 3), (27, 3), (3, 27), (27, 27)):
            put(im, cx, cy, (150, 156, 168))
            put(im, cx + 1, cy, (96, 102, 114))
            put(im, cx, cy + 1, (96, 102, 114))
            put(im, cx + 1, cy + 1, (24, 26, 31))
    return im


def recess(im, x0, y0, x1, y1, rnd, fill=RECESS):
    noisy(im, x0, y0, x1, y1, fill, rnd, 2)
    for x in range(x0, x1 + 1):
        put(im, x, y0, (16, 18, 22))
        put(im, x, y1, (92, 98, 110))
    for y in range(y0, y1 + 1):
        put(im, x0, y, (16, 18, 22))
        put(im, x1, y, (92, 98, 110))


def disc(cx, cy, r):
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r:
                yield x, y, d


# ----------------------------------------------------------------------------------------------
# Machine textures
# ----------------------------------------------------------------------------------------------

def side_tex(accent, seed):
    rnd = random.Random(seed)
    im = base_plate(rnd)
    for y in range(4, 28):
        put(im, 15, y, (28, 30, 36))
        put(im, 16, y, (96, 102, 114))
    rect(im, 6, 6, 8, 8, sc(accent, 0.45))
    rect(im, 6, 6, 7, 7, accent)
    put(im, 6, 6, mixc(accent, (255, 255, 255), 0.5))
    for y in (22, 24):
        for x in range(19, 26):
            put(im, x, y, (24, 26, 31))
            put(im, x, y + 1, (84, 90, 102))
    return im


def top_tex(accent, seed):
    rnd = random.Random(seed)
    im = base_plate(rnd)
    frame(im, 6, 6, 25, 25, sc(accent, 0.55))
    rect(im, 7, 7, 24, 24, RECESS)
    for y in range(8, 24, 3):
        for x in range(8, 24):
            put(im, x, y, (22, 24, 29))
            put(im, x, y + 1, (84, 90, 102))
    return im


def bottom_tex(seed):
    rnd = random.Random(seed)
    im = base_plate(rnd, sc(STEEL, 0.7), rivets=False)
    return im


def front_base(seed):
    rnd = random.Random(seed)
    im = base_plate(rnd)
    recess(im, 6, 6, 25, 25, rnd)
    return im


def front_enrichment(active, accent, seed):
    im = front_base(seed)
    frame(im, 9, 9, 22, 16, HI)
    for y in range(10, 16):
        for x in range(10, 22):
            if active:
                put(im, x, y, mixc((236, 130, 64), (140, 36, 28), (y - 10) / 5))
            else:
                put(im, x, y, (26, 18, 20))
    frame(im, 11, 19, 20, 21, (84, 90, 102))
    rect(im, 12, 20, 19, 20, (14, 14, 18))
    for y in (9, 12, 15):
        c = accent if active else sc(accent, 0.3)
        put(im, 23, y, c)
        put(im, 23, y + 1, c)
    return im


def front_crusher(active, accent, seed):
    im = front_base(seed)
    for y in range(10, 22):
        for x in range(8, 24):
            if active:
                put(im, x, y, mixc((120, 60, 20), (210, 120, 30), (y - 10) / 11))
            else:
                put(im, x, y, (14, 14, 16))
    frame(im, 8, 9, 23, 22, (84, 90, 102))
    for tx in range(9, 23, 4):
        for r in range(4):
            for x in range(tx + r // 2, tx + 4 - (r + 1) // 2):
                put(im, x, 10 + r, METAL_LIGHT if x < tx + 2 else METAL_MID)
        for r in range(4):
            for x in range(tx + 2 + r // 2, tx + 6 - (r + 1) // 2):
                if x < 23:
                    put(im, x, 21 - r, METAL_LIGHT if x < tx + 4 else METAL_MID)
    return im


def front_smelter(active, accent, seed):
    im = front_base(seed)
    frame(im, 8, 8, 23, 23, HI)
    rect(im, 9, 9, 22, 22, (90, 36, 18) if active else (34, 22, 20))
    coil = (255, 190, 70) if active else (110, 56, 44)
    glow = (200, 90, 30) if active else None
    ys = [11, 14, 17, 20]
    for i, y in enumerate(ys):
        for x in range(11, 21):
            put(im, x, y, coil)
            if glow:
                put(im, x, y - 1, glow)
                put(im, x, y + 1, glow)
        if i < len(ys) - 1:
            x = 20 if i % 2 == 0 else 11
            for yy in range(y, ys[i + 1] + 1):
                put(im, x, yy, coil)
    return im


def front_compressor(active, accent, seed):
    im = front_base(seed)
    for x, y, d in disc(15.5, 15.5, 9.5):
        if d >= 6.5:
            ang = math.atan2(y + 0.5 - 15.5, x + 0.5 - 15.5)
            c = mixc(METAL_MID, METAL_LIGHT, 0.5 + 0.5 * math.cos(ang + 2.4))
            if d > 8.8:
                c = (40, 43, 50)
            put(im, x, y, c)
        elif d >= 4.2:
            put(im, x, y, (90, 170, 240) if active else (18, 22, 30))
        else:
            c = (210, 238, 255) if active else mixc(sc(accent, 0.55), accent, 1 - d / 4.2)
            put(im, x, y, c)
    for bx, by in ((9, 9), (22, 9), (9, 22), (22, 22)):
        put(im, bx, by, (200, 206, 216))
    return im


def front_combiner(active, accent, seed):
    im = front_base(seed)
    glow = (200, 140, 255) if active else (70, 60, 90)
    for (y0, y1) in ((9, 13), (18, 22)):
        frame(im, 8, y0, 12, y1, HI)
        rect(im, 9, y0 + 1, 11, y1 - 1, (26, 24, 34))
        for x in range(13, 17):
            put(im, x, (y0 + y1) // 2, glow)
    for y in range(11, 21):
        put(im, 16, y, glow)
    for x in range(16, 19):
        put(im, x, 15, glow)
        put(im, x, 16, glow)
    frame(im, 19, 12, 24, 19, sc(accent, 1.0))
    rect(im, 20, 13, 23, 18, (160, 100, 230) if active else (26, 22, 36))
    return im


def front_infuser(active, accent, seed):
    im = front_base(seed)
    frame(im, 9, 8, 22, 23, HI)
    rect(im, 10, 9, 21, 22, (20, 24, 34))
    top = 12 if active else 16
    for y in range(top, 23):
        t = (y - top) / max(1, 22 - top)
        c = mixc((236, 90, 100), (150, 24, 40), t) if active else mixc((200, 60, 70), (130, 26, 40), t)
        for x in range(10, 22):
            put(im, x, y, c)
    for x in range(10, 22):
        put(im, x, top, (255, 170, 170) if active else (230, 130, 130))
    if active:
        for bx, by in ((12, 19), (15, 16), (18, 20), (13, 14), (19, 15)):
            put(im, bx, by, (255, 210, 210))
    for y in (10, 14):
        put(im, 11, y, (70, 80, 100))
    rect(im, 9, 6, 22, 7, (84, 90, 102))
    return im


def front_heat(active, accent, seed):
    im = front_base(seed)
    for y in (7, 9):
        rect(im, 8, y, 23, y, (22, 24, 29))
        rect(im, 8, y + 1, 23, y + 1, (84, 90, 102))
    frame(im, 8, 12, 23, 24, HI)
    rect(im, 9, 13, 22, 23, (14, 8, 8))
    rnd = random.Random(seed + 5)
    if active:
        for x in range(9, 23):
            h = rnd.randint(4, 9)
            for k in range(h):
                y = 23 - k
                t = k / h
                c = mixc((255, 230, 90), (230, 70, 20), t)
                put(im, x, y, c)
    else:
        for x in range(9, 23):
            if rnd.random() < 0.45:
                put(im, x, 23, (110, 30, 20))
    for x in (11, 15, 19):
        for y in range(13, 24):
            put(im, x, y, (36, 38, 44))
        for y in range(13, 24):
            if y % 3:
                put(im, x + 1, y, (96, 102, 114))
    return im


def front_solar(active, accent, seed):
    im = front_base(seed)
    sun = (255, 230, 100) if active else (200, 170, 70)
    for x, y, d in disc(15.5, 15.5, 4.2):
        put(im, x, y, sun)
    for ang in range(0, 360, 45):
        for r in (6.2, 7.2, 8.2):
            x = int(15.5 + r * math.cos(math.radians(ang)))
            y = int(15.5 + r * math.sin(math.radians(ang)))
            put(im, x, y, sun)
    return im


def solar_top(active, seed):
    rnd = random.Random(seed)
    im = base_plate(rnd, rivets=False)
    rect(im, 3, 3, 28, 28, (14, 20, 40))
    lo, hi = ((40, 90, 190), (110, 190, 255)) if active else ((26, 60, 140), (80, 150, 230))
    for cy in range(4):
        for cx in range(4):
            x0, y0 = 4 + cx * 6, 4 + cy * 6
            for y in range(5):
                for x in range(5):
                    t = (x + y) / 8
                    put(im, x0 + x, y0 + y, mixc(hi, lo, t))
            put(im, x0, y0, mixc(hi, (255, 255, 255), 0.5))
    return im


MACHINES = [
    dict(key="Enrichment_Chamber", name="Enrichment Chamber", accent=(210, 70, 60), light="#c53", front=front_enrichment,
         desc="Enriches dusts and turns raw ore into twice the dust. Needs power.",
         recipe=[(CASING, 1), ("Techtale_Circuit_Basic", 2), ("Ingredient_Bar_Copper", 4), ("Techtale_Ingot_Osmium", 1)]),
    dict(key="Crusher", name="Crusher", accent=(220, 150, 50), light="#b72", front=front_crusher,
         desc="Crushes ingots, bars and gems into dust. Needs power.",
         recipe=[(CASING, 1), ("Techtale_Circuit_Basic", 2), ("Ingredient_Bar_Iron", 4), ("Ingredient_Charcoal", 4)]),
    dict(key="Energized_Smelter", name="Energized Smelter", accent=(240, 120, 40), light="#f83", front=front_smelter,
         desc="Smelts items with electricity instead of fuel.",
         recipe=[(CASING, 1), ("Techtale_Circuit_Basic", 2), ("Bench_Furnace", 1), ("Ingredient_Bar_Copper", 2)]),
    dict(key="Osmium_Compressor", name="Osmium Compressor", accent=(110, 170, 230), light="#58e", front=front_compressor,
         desc="Compresses materials with osmium into denser forms. Needs power.",
         recipe=[(CASING, 1), ("Techtale_Circuit_Basic", 2), ("Techtale_Ingot_Osmium", 4), ("Ingredient_Bar_Gold", 2)]),
    dict(key="Combiner", name="Combiner", accent=(170, 100, 220), light="#a5e", front=front_combiner,
         desc="Combines dust with stone to make ore. Needs power.",
         recipe=[(CASING, 1), ("Techtale_Circuit_Basic", 2), ("Ingredient_Bar_Silver", 2), ("Techtale_Ingot_Tin", 2)]),
    dict(key="Metallurgic_Infuser", name="Metallurgic Infuser", accent=(214, 60, 90), light="#d46", front=front_infuser,
         desc="Infuses metals with carbon, copper, diamond and obsidian to make alloys, steel and circuits.",
         recipe=[("Bench_Furnace", 1), ("Ingredient_Bar_Iron", 4), ("Ingredient_Bar_Copper", 2), ("Techtale_Ingot_Osmium", 2)]),
    dict(key="Heat_Generator", name="Heat Generator", accent=(240, 130, 40), light="#f92", front=front_heat,
         desc="Burns fuel to generate power.",
         recipe=[("Bench_Furnace", 1), ("Ingredient_Bar_Iron", 3), ("Ingredient_Bar_Copper", 3), ("Techtale_Ingot_Osmium", 1)]),
    dict(key="Solar_Generator", name="Solar Generator", accent=(80, 150, 230), light=None, front=front_solar, solar=True,
         desc="Generates power from sunlight.",
         recipe=[("Techtale_Enriched_Copper", 2), ("Techtale_Ingot_Osmium", 2), ("Ingredient_Bar_Iron", 2),
                 {"ResourceTypeId": "Crystal_Shards", "Quantity": 8}]),
]


# ----------------------------------------------------------------------------------------------
# Energy cube + cable textures
# ----------------------------------------------------------------------------------------------

def bolt(im, ox, oy, c):
    pts = [(3, 0), (2, 1), (1, 2), (2, 2), (3, 2), (2, 3), (1, 4), (0, 5), (2, 4), (3, 3)]
    for x, y in [(3, 0), (2, 1), (1, 2), (2, 2), (3, 2), (2, 3), (3, 3), (1, 4), (2, 4), (0, 5), (1, 5), (0, 6)]:
        put(im, ox + x, oy + y, c)


def cube_face(tier, front, seed):
    sh, base, hi = TIER_COL[tier]
    rnd = random.Random(seed)
    im = Image.new("RGBA", (N, N))
    noisy(im, 0, 0, N - 1, N - 1, base, rnd, 4)
    frame(im, 0, 0, N - 1, N - 1, sc(sh, 0.55))
    for i in range(1, N - 1):
        for k in (1, 2):
            put(im, i, k, hi) if k == 1 else put(im, i, k, mixc(base, hi, 0.35))
            put(im, k, i, hi) if k == 1 else put(im, k, i, mixc(base, hi, 0.35))
            put(im, i, N - 1 - k, sh if k == 1 else mixc(base, sh, 0.5))
            put(im, N - 1 - k, i, sh if k == 1 else mixc(base, sh, 0.5))
    for cx, cy in ((4, 4), (26, 4), (4, 26), (26, 26)):
        rect(im, cx, cy, cx + 1, cy + 1, sc(sh, 0.5))
        put(im, cx, cy, hi)
    recess(im, 7, 7, 24, 24, rnd, fill=(20, 24, 30))
    core = hi
    if not front:
        frame(im, 9, 9, 22, 22, sc(base, 0.8))
        rect(im, 12, 12, 19, 19, core)
        rect(im, 13, 13, 18, 18, mixc(core, (255, 255, 255), 0.45))
        for x in range(10, 22, 3):
            put(im, x, 10, sc(base, 0.6))
            put(im, x, 21, sc(base, 0.6))
    else:
        for x, y, d in disc(15.5, 15.5, 7.5):
            if d > 6.2:
                put(im, x, y, HI)
            else:
                put(im, x, y, (10, 12, 16))
        for ang in range(0, 360, 90):
            x = int(15.5 + 5.5 * math.cos(math.radians(ang)))
            y = int(15.5 + 5.5 * math.sin(math.radians(ang)))
            put(im, x, y, base)
        bolt(im, 14, 12, mixc(core, (255, 255, 255), 0.35))
    return im


def cable_texture(tier, seed):
    sh, base, hi = TIER_COL[tier]
    rnd = random.Random(seed)
    im = Image.new("RGBA", (N, N))
    noisy(im, 0, 0, N - 1, N - 1, sc(base, 0.8), rnd, 3)
    # core faces (6x6 at 0,0)
    for y in range(6):
        for x in range(6):
            edge = x in (0, 5) or y in (0, 5)
            c = sc(sh, 0.7) if edge else mixc(base, hi, 0.25 + 0.15 * ((x + y) % 2))
            put(im, x, y, c)
    for x, y in ((1, 1), (4, 1), (1, 4), (4, 4)):
        put(im, x, y, hi)
    # arm faces (13x13 at 8,0): diagonal braid
    for ry in range(13):
        for rx in range(13):
            k = (rx + ry) % 4
            c = (sc(base, 0.72), base, mixc(base, hi, 0.45), base)[k]
            put(im, 8 + rx, ry, c)
    return im


# ----------------------------------------------------------------------------------------------
# Cable models: 64 .blockymodel files, one per connection mask
# bit: DOWN 0, UP 1, NORTH(-Z) 2, SOUTH(+Z) 3, WEST(-X) 4, EAST(+X) 5. Units are 1/32 block, origin at the bottom centre.
# ----------------------------------------------------------------------------------------------

CORE = 6
ARM = 4
ARM_LEN = 16 - CORE // 2  # from the core's face out to the block edge

# bit -> (centre x, y, z, size x, y, z)
ARM_BOXES = {
    0: (0, ARM_LEN / 2, 0, ARM, ARM_LEN, ARM),
    1: (0, 32 - ARM_LEN / 2, 0, ARM, ARM_LEN, ARM),
    2: (0, 16, -(CORE / 2 + ARM_LEN / 2), ARM, ARM, ARM_LEN),
    3: (0, 16, CORE / 2 + ARM_LEN / 2, ARM, ARM, ARM_LEN),
    4: (-(CORE / 2 + ARM_LEN / 2), 16, 0, ARM_LEN, ARM, ARM),
    5: (CORE / 2 + ARM_LEN / 2, 16, 0, ARM_LEN, ARM, ARM),
}


def _face_layout(off):
    return {f: {"offset": {"x": off[0], "y": off[1]}, "mirror": {"x": False, "y": False}, "angle": 0}
            for f in ("front", "back", "left", "right", "top", "bottom")}


def _box_node(nid, name, centre, size, tex_off):
    return {
        "id": str(nid),
        "name": name,
        "position": {"x": centre[0], "y": centre[1], "z": centre[2]},
        "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
        "shape": {
            "type": "box",
            "offset": {"x": 0, "y": 0, "z": 0},
            "stretch": {"x": 1, "y": 1, "z": 1},
            "settings": {"isPiece": False, "size": {"x": size[0], "y": size[1], "z": size[2]}},
            "textureLayout": _face_layout(tex_off),
            "unwrapMode": "custom",
            "visible": True,
            "doubleSided": False,
            "shadingMode": "standard",
        },
        "children": [],
    }


def cable_model(mask):
    children = [_box_node(2, "Core", (0, 16, 0), (CORE, CORE, CORE), (0, 0))]
    names = ["Down", "Up", "North", "South", "West", "East"]
    for bit in range(6):
        if mask & (1 << bit):
            b = ARM_BOXES[bit]
            children.append(_box_node(3 + bit, "Arm" + names[bit], b[:3], b[3:], (8, 0)))
    return {
        "lod": "auto",
        "nodes": [{
            "id": "1",
            "name": "Cable",
            "position": {"x": 0, "y": 0, "z": 0},
            "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
            "shape": {
                "type": "none",
                "offset": {"x": 0, "y": 0, "z": 0},
                "stretch": {"x": 1, "y": 1, "z": 1},
                "settings": {"isPiece": False},
                "textureLayout": {},
                "unwrapMode": "custom",
                "visible": True,
                "doubleSided": False,
                "shadingMode": "standard",
            },
            "children": children,
        }],
    }


# ----------------------------------------------------------------------------------------------
# Icons
# ----------------------------------------------------------------------------------------------

def cube_icon3(top, left, right):
    """64x64 isometric cube; the front texture goes on the left face."""
    icon = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    out = icon.load()
    faces = [
        (top, (8, 20), (24, -12), (24, 12), 1.0),
        (left, (8, 20), (24, 12), (0, 26), 0.78),
        (right, (32, 32), (24, -12), (0, 26), 0.56),
    ]
    for y in range(64):
        for x in range(64):
            for (tex, o, u, v, shade) in faces:
                tp = tex.load()
                tw, th = tex.size
                px_, py_ = x + 0.5 - o[0], y + 0.5 - o[1]
                det = u[0] * v[1] - u[1] * v[0]
                a = (px_ * v[1] - py_ * v[0]) / det
                b = (u[0] * py_ - u[1] * px_) / det
                if 0 <= a < 1 and 0 <= b < 1:
                    r, g, bb, al = tp[int(a * tw), int(b * th)]
                    out[x, y] = (int(r * shade), int(g * shade), int(bb * shade), 255)
                    break
    return icon


def cable_icon(tier):
    """A small cable piece (straight along one axis with a branch up), rendered isometrically at 4x then reduced."""
    sh, base, hi = TIER_COL[tier]
    S = 4
    img = Image.new("RGBA", (64 * S, 64 * S), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    org = (8 * S, 46 * S)
    U = (24 * S, 12 * S)
    V = (24 * S, -12 * S)
    H = (0, -26 * S)

    def P(u, v, h):
        return (org[0] + u * U[0] + v * V[0] + h * H[0], org[1] + u * U[1] + v * V[1] + h * H[1])

    c0, c1 = 0.5 - 0.15, 0.5 + 0.15  # core
    a0, a1 = 0.5 - 0.1, 0.5 + 0.1  # arm
    boxes = [
        ((c0, c1), (c0, c1), (c0, c1)),     # core
        ((c1, c1 + 0.35), (a0, a1), (a0, a1)),    # +u arm, near
        ((a0, a1), (c1, c1 + 0.35), (a0, a1)),    # +v arm, far
        ((c0 - 0.35, c0), (a0, a1), (a0, a1)),    # -u arm, far
        ((a0, a1), (c0 - 0.35, c0), (a0, a1)),    # -v arm, near
        ((a0, a1), (a0, a1), (c1, c1 + 0.3)),     # up
    ]
    boxes.sort(key=lambda b: ((b[0][0] + b[0][1]) - (b[1][0] + b[1][1])) / 2)
    for i, ((u0, u1), (v0, v1), (h0, h1)) in enumerate(boxes):
        faces = [
            ([P(u0, v0, h1), P(u1, v0, h1), P(u1, v1, h1), P(u0, v1, h1)], hi),
            ([P(u0, v0, h1), P(u1, v0, h1), P(u1, v0, h0), P(u0, v0, h0)], base),
            ([P(u1, v0, h1), P(u1, v1, h1), P(u1, v1, h0), P(u1, v0, h0)], sh),
        ]
        for poly, col in faces:
            dr.polygon(poly, fill=col + (255,), outline=sc(sh, 0.4) + (255,))
    return img.resize((64, 64), Image.LANCZOS)


# ----------------------------------------------------------------------------------------------
# Item/block assembly
# ----------------------------------------------------------------------------------------------

def _wb(inputs, qty=1, secs=5):
    items = []
    for i in inputs:
        items.append(i if isinstance(i, dict) else {"ItemId": i[0], "Quantity": i[1]})
    return {"Input": items, "BenchRequirement": [dict(WORKBENCH)], "OutputQuantity": qty, "TimeSeconds": secs}


def _faces(top, bottom, north, south, east, west):
    return [{"Weight": 1, "Up": top, "Down": bottom, "North": north, "South": south, "East": east, "West": west}]


def _block_common(color):
    return {
        "Material": "Solid",
        "Group": "Metal",
        "Flags": {},
        "Gathering": {"Breaking": {"GatherType": "Benches"}},
        "BlockParticleSetId": "Metal",
        "ParticleColor": color,
        "BlockSoundSetId": "Metal",
        "PhysicalMaterialId": "Metal",
    }


ICON_PROPS = {"Scale": 0.58823, "Rotation": [22.5, 45, 22.5], "Translation": [0, -13.5]}
OPEN_USE = {"Use": {"Interactions": [{"Type": "Techtale_OpenMachine"}]}}


def _tex_path(name):
    return f"BlockTextures/Techtale/{name}.png"


def _save_tex(g, gc, name, im):
    gc.save_png(im, gc.RES / "Common" / _tex_path(name))
    return _tex_path(name)


def _cube_item(g, gc, item_id, name, desc, quality, recipe, textures, active, icon_tex, light, color, folder):
    block = _block_common(color)
    block.update({
        "DrawType": "Cube",
        "HitboxType": "Full",
        "VariantRotation": "NESW",
        "RotationYawPlacementOffset": "OneEighty",
        "Textures": textures,
        "CubeShadingMode": "Standard",
        "BlockEntity": {"Components": {"Techtale_Machine": {"MachineType": item_id}}},
        "Interactions": OPEN_USE,
    })
    if active is not None:
        state = {"Textures": active}
        if light:
            state["Light"] = {"Color": light}
        block["State"] = {"Definitions": {"Active": state}}
    block["TextureComputedColor"] = gc.tohex(gc.avg_color(icon_tex["avg"])).upper()
    icon = cube_icon3(icon_tex["top"], icon_tex["left"], icon_tex["right"])
    gc.save_png(icon, gc.ICON_DIR / f"{item_id}.png")
    g.ids.append(item_id)
    d = {
        "TranslationProperties": g.name_key(item_id, name, desc),
        "ItemLevel": 10,
        "Quality": quality,
        "MaxStack": 16,
        "Categories": [f"{gc.PREFIX}.Machines"],
        "PlayerAnimationsId": "Block",
        "Recipe": recipe,
        "BlockType": block,
        "Tags": {"Type": ["Metal"]},
        "IconProperties": ICON_PROPS,
        "Icon": f"Icons/ItemsGenerated/{item_id}.png",
        "ItemSoundSetId": "ISS_Items_Metal",
    }
    g.save_item(folder, item_id, d)


def gen_machines(g, gc):
    P = gc.PREFIX
    for i, m in enumerate(MACHINES):
        item_id = f"{P}_{m['key']}"
        seed = 100 + i * 10
        side = side_tex(m["accent"], seed + 1)
        bottom = bottom_tex(seed + 3)
        solar = m.get("solar")
        top = solar_top(False, seed + 2) if solar else top_tex(m["accent"], seed + 2)
        front = m["front"](False, m["accent"], seed + 4)
        front_on = m["front"](True, m["accent"], seed + 4)
        t_side = _save_tex(g, gc, f"{item_id}_Side", side)
        t_bottom = _save_tex(g, gc, f"{item_id}_Bottom", bottom)
        t_top = _save_tex(g, gc, f"{item_id}_Top", top)
        t_front = _save_tex(g, gc, f"{item_id}_Front", front)
        t_front_on = _save_tex(g, gc, f"{item_id}_Front_Active", front_on)
        textures = _faces(t_top, t_bottom, t_front, t_side, t_side, t_side)
        t_top_on = t_top
        if solar:
            t_top_on = _save_tex(g, gc, f"{item_id}_Top_Active", solar_top(True, seed + 2))
        active = _faces(t_top_on, t_bottom, t_front_on, t_side, t_side, t_side)
        color = gc.tohex(gc.avg_color(side))
        _cube_item(g, gc, item_id, m["name"], m["desc"], "Uncommon", _wb(m["recipe"], 1, 8), textures, active,
                   dict(top=top, left=front, right=side, avg=side), m["light"], color, "Machines")


def gen_energy_cubes(g, gc):
    P = gc.PREFIX
    for i, tier in enumerate(TIERS):
        item_id = f"{P}_Energy_Cube_{tier}"
        face = cube_face(tier, False, 300 + i)
        front = cube_face(tier, True, 300 + i)
        t_face = _save_tex(g, gc, f"{item_id}_Face", face)
        t_front = _save_tex(g, gc, f"{item_id}_Front", front)
        textures = _faces(t_face, t_face, t_front, t_face, t_face, t_face)
        cap, rate = CUBE_STATS[tier]
        desc = f"Stores {cap} of energy and outputs up to {rate} J/t through its front face."
        if tier == "Basic":
            recipe = _wb([(CASING, 1), ("Techtale_Alloy_Infused", 4), ("Techtale_Ingot_Osmium", 2),
                          ("Techtale_Circuit_Basic", 1)], 1, 8)
        else:
            lower = TIERS[i - 1]
            alloy, circuit, extra = CUBE_UPGRADE[tier]
            recipe = _wb([(f"{P}_Energy_Cube_{lower}", 1), (alloy, 4), (circuit, 1), (extra, 2)], 1, 8)
        color = gc.tohex(TIER_COL[tier][1])
        _cube_item(g, gc, item_id, f"{tier} Energy Cube", desc, QUALITY[tier], recipe, textures, None,
                   dict(top=face, left=front, right=face, avg=face), None, color, "Machines")


def gen_cables(g, gc):
    P = gc.PREFIX
    model_dir = gc.RES / "Common/Blocks/Techtale/Cable"
    model_dir.mkdir(parents=True, exist_ok=True)
    for mask in range(64):
        (model_dir / f"Cable_{mask}.blockymodel").write_text(json.dumps(cable_model(mask), indent=2) + "\n", encoding="utf-8")

    def model_path(mask):
        return f"Blocks/Techtale/Cable/Cable_{mask}.blockymodel"

    g.write_json("Server/Item/Block/Hitboxes/Techtale/Techtale_Cable.json", {
        "Boxes": [{"Min": {"X": 0.25, "Y": 0.25, "Z": 0.25}, "Max": {"X": 0.75, "Y": 0.75, "Z": 0.75}}]
    })
    for i, tier in enumerate(TIERS):
        item_id = f"{P}_Cable_{tier}"
        tex = cable_texture(tier, 500 + i)
        tex_rel = f"Blocks/Techtale/Cable/Cable_{tier}_Texture.png"
        gc.save_png(tex, gc.RES / "Common" / tex_rel)
        icon = cable_icon(tier)
        gc.save_png(icon, gc.ICON_DIR / f"{item_id}.png")
        if tier == "Basic":
            recipe = _wb([(f"{P}_Ingot_Steel", 1), (f"{P}_Dust_Copper", 2)], 8, 3)
        else:
            lower, alloy = CABLE_UPGRADE[tier]
            recipe = _wb([(f"{P}_Cable_{lower}", 8), (alloy, 1)], 8, 3)
        block = _block_common(gc.tohex(TIER_COL[tier][1]))
        block.update({
            "DrawType": "Model",
            "Opacity": "Transparent",
            "CustomModel": model_path(0),
            "CustomModelTexture": [{"Texture": tex_rel, "Weight": 1}],
            "HitboxType": f"{P}_Cable",
            "BlockEntity": {"Components": {f"{P}_Cable": {"Tier": tier}}},
            "State": {"Definitions": {f"C{m}": {"CustomModel": model_path(m)} for m in range(1, 64)}},
            "TextureComputedColor": gc.tohex(gc.avg_color(tex)).upper(),
        })
        g.ids.append(item_id)
        d = {
            "TranslationProperties": g.name_key(item_id, f"{tier} Universal Cable",
                                                f"Carries up to {CABLE_RATE[tier]} J/t between machines, generators and energy cubes."),
            "ItemLevel": 10,
            "Quality": QUALITY[tier],
            "MaxStack": 100,
            "Categories": [f"{P}.Transmitters"],
            "PlayerAnimationsId": "Block",
            "Recipe": recipe,
            "BlockType": block,
            "Tags": {"Type": ["Metal"]},
            "IconProperties": ICON_PROPS,
            "Icon": f"Icons/ItemsGenerated/{item_id}.png",
            "ItemSoundSetId": "ISS_Items_Metal",
        }
        g.save_item("Transmitters", item_id, d)


def generate(g, gc):
    gen_machines(g, gc)
    gen_energy_cubes(g, gc)
    gen_cables(g, gc)
