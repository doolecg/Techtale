"""Phase 2 fluid blocks for tools/gen_content.py: fluid tank, chemical tank, electric pump, electrolytic separator
and the mechanical pipe. Reuses the machine/cable helpers of gen_blocks.

generate(g, gc) is called from Gen.run() right after gen_blocks.generate.
The separator marks its hydrogen port on the left face and its oxygen port on the right face, as seen when
looking at the front.
"""
import random

from PIL import Image

import gen_blocks as gb
from gen_blocks import N, put, rect, frame, mixc, sc, front_base

CASING = gb.CASING
WATER = (70, 140, 230)
GAS = (170, 230, 120)
HYDROGEN = (90, 200, 255)
OXYGEN = (255, 110, 100)
PIPE_BASE = (86, 150, 120)


def gauge(im, x0, y0, x1, y1, fill, level):
    frame(im, x0, y0, x1, y1, gb.HI)
    rect(im, x0 + 1, y0 + 1, x1 - 1, y1 - 1, (16, 20, 26))
    top = y1 - 1 - int((y1 - y0 - 2) * level)
    for y in range(top, y1):
        for x in range(x0 + 1, x1):
            put(im, x, y, mixc(fill, sc(fill, 0.55), (y - top) / max(1, y1 - top)))
    put(im, x0 + 1, top, mixc(fill, (255, 255, 255), 0.5))


def front_fluid_tank(active, accent, seed):
    im = front_base(seed)
    gauge(im, 10, 7, 21, 24, WATER, 0.75)
    for y in range(9, 24, 3):
        put(im, 9, y, gb.METAL_LIGHT)
        put(im, 22, y, gb.METAL_LIGHT)
    return im


def front_chemical_tank(active, accent, seed):
    im = front_base(seed)
    gauge(im, 10, 7, 21, 24, GAS, 0.6)
    for y in range(8, 24, 4):
        for x in (11, 13, 15):
            put(im, x + (y // 4) % 2, y, mixc(GAS, (255, 255, 255), 0.6))
    rect(im, 8, 8, 9, 9, (230, 200, 60))
    rect(im, 8, 22, 9, 23, (230, 200, 60))
    return im


def front_pump(active, accent, seed):
    im = front_base(seed)
    rect(im, 8, 14, 23, 17, (24, 26, 31))
    rect(im, 8, 15, 23, 16, WATER if active else sc(WATER, 0.4))
    for x, y, d in gb.disc(15.5, 15.5, 6.5):
        if d >= 4.5:
            put(im, x, y, gb.METAL_MID if d < 6 else (40, 43, 50))
    for k in range(-3, 4):
        put(im, 16 + k, 16, gb.METAL_LIGHT)
        put(im, 16, 16 + k, gb.METAL_LIGHT)
    for k in range(-4, 5, 4):
        put(im, 16 + k, 16 + k, gb.HI)
    return im


def front_separator(active, accent, seed):
    im = front_base(seed)
    frame(im, 9, 8, 22, 23, gb.HI)
    rect(im, 10, 9, 21, 22, (20, 24, 34))
    level = 14 if active else 11
    for y in range(level, 23):
        for x in range(10, 22):
            put(im, x, y, mixc(WATER, sc(WATER, 0.5), (y - level) / 9))
    for x in range(10, 22):
        put(im, x, level, mixc(WATER, (255, 255, 255), 0.45))
    for x in (13, 18):
        for y in range(10, 23):
            put(im, x, y, gb.METAL_LIGHT)
    if active:
        for bx, by in ((13, 12), (18, 13), (12, 18), (19, 17)):
            put(im, bx, by, (230, 245, 255))
    return im


def port_side(color, seed):
    im = gb.side_tex(color, seed)
    frame(im, 9, 9, 22, 22, sc(color, 0.55))
    rect(im, 10, 10, 21, 21, sc(color, 0.25))
    for x, y, d in gb.disc(15.5, 15.5, 5.5):
        put(im, x, y, mixc(color, sc(color, 0.45), d / 5.5))
    put(im, 14, 14, mixc(color, (255, 255, 255), 0.6))
    return im


def pipe_texture(seed):
    rnd = random.Random(seed)
    base = PIPE_BASE
    hi = mixc(base, (255, 255, 255), 0.35)
    sh = sc(base, 0.5)
    im = Image.new("RGBA", (N, N))
    gb.noisy(im, 0, 0, N - 1, N - 1, sc(base, 0.8), rnd, 3)
    for y in range(6):
        for x in range(6):
            edge = x in (0, 5) or y in (0, 5)
            put(im, x, y, sc(sh, 0.7) if edge else mixc(base, hi, 0.25 + 0.15 * ((x + y) % 2)))
    for x, y in ((1, 1), (4, 1), (1, 4), (4, 4)):
        put(im, x, y, hi)
    for ry in range(13):
        for rx in range(13):
            c = sc(sh, 1.2) if ry in (0, 12) else (base if ry % 4 else mixc(base, hi, 0.5))
            put(im, 8 + rx, ry, c)
    return im


def pipe_icon():
    tex = pipe_texture(700)
    face = tex.crop((8, 0, 21, 13)).resize((N, N), Image.NEAREST)
    return gb.cube_icon3(face, face, face)


MACHINES = [
    dict(key="Fluid_Tank", name="Fluid Tank", accent=(70, 140, 230), light=None, front=front_fluid_tank,
         desc="Stores up to 16,000 mB of fluid. Fills and drains through pipes.",
         recipe=[(CASING, 1), ("Ingredient_Bar_Iron", 4), ("Techtale_Ingot_Osmium", 1)]),
    dict(key="Chemical_Tank", name="Chemical Tank", accent=(150, 220, 90), light=None, front=front_chemical_tank,
         desc="Stores up to 16,000 mB of gas or chemicals. Fills and drains through pipes.",
         recipe=[(CASING, 1), ("Ingredient_Bar_Iron", 4), ("Techtale_Ingot_Osmium", 2), ("Techtale_Circuit_Basic", 1)]),
    dict(key="Electric_Pump", name="Electric Pump", accent=(80, 170, 220), light="#48c", front=front_pump,
         desc="Pumps fluid from the world into pipes. Needs power.",
         recipe=[(CASING, 1), ("Techtale_Circuit_Basic", 1), ("Ingredient_Bar_Iron", 3), ("Techtale_Ingot_Osmium", 1)]),
    dict(key="Electrolytic_Separator", name="Electrolytic Separator", accent=(120, 200, 240), light="#6be",
         front=front_separator, separator=True,
         desc="Splits water into hydrogen and oxygen. Hydrogen leaves the left face, oxygen the right. Needs power.",
         recipe=[(CASING, 1), ("Techtale_Circuit_Basic", 2), ("Ingredient_Bar_Iron", 2), ("Techtale_Ingot_Osmium", 2)]),
]


def gen_machines(g, gc):
    P = gc.PREFIX
    for i, m in enumerate(MACHINES):
        item_id = f"{P}_{m['key']}"
        seed = 800 + i * 10
        side = gb.side_tex(m["accent"], seed + 1)
        bottom = gb.bottom_tex(seed + 3)
        top = gb.top_tex(m["accent"], seed + 2)
        front = m["front"](False, m["accent"], seed + 4)
        front_on = m["front"](True, m["accent"], seed + 4)
        t_side = gb._save_tex(g, gc, f"{item_id}_Side", side)
        t_bottom = gb._save_tex(g, gc, f"{item_id}_Bottom", bottom)
        t_top = gb._save_tex(g, gc, f"{item_id}_Top", top)
        t_front = gb._save_tex(g, gc, f"{item_id}_Front", front)
        t_front_on = gb._save_tex(g, gc, f"{item_id}_Front_Active", front_on)
        t_left, t_right = t_side, t_side
        right = side
        if m.get("separator"):
            # north face is the front; seen from the front, East is on the left and West on the right
            left = port_side(HYDROGEN, seed + 5)
            right = port_side(OXYGEN, seed + 6)
            t_left = gb._save_tex(g, gc, f"{item_id}_Hydrogen", left)
            t_right = gb._save_tex(g, gc, f"{item_id}_Oxygen", right)
        # _faces(top, bottom, north, south, east, west)
        textures = gb._faces(t_top, t_bottom, t_front, t_side, t_left, t_right)
        active = gb._faces(t_top, t_bottom, t_front_on, t_side, t_left, t_right)
        color = gc.tohex(gc.avg_color(side))
        gb._cube_item(g, gc, item_id, m["name"], m["desc"], "Uncommon", gb._wb(m["recipe"], 1, 8), textures, active,
                      dict(top=top, left=front, right=right, avg=side), m["light"], color, "Machines")


def gen_pipe(g, gc):
    P = gc.PREFIX
    item_id = f"{P}_Mechanical_Pipe"
    tex = pipe_texture(700)
    tex_rel = "Blocks/Techtale/Pipe/Pipe_Basic_Texture.png"
    gc.save_png(tex, gc.RES / "Common" / tex_rel)
    gc.save_png(pipe_icon(), gc.ICON_DIR / f"{item_id}.png")

    def model_path(mask):
        return f"Blocks/Techtale/Cable/Cable_{mask}.blockymodel"

    block = gb._block_common(gc.tohex(PIPE_BASE))
    block.update({
        "DrawType": "Model",
        "Opacity": "Transparent",
        "CustomModel": model_path(0),
        "CustomModelTexture": [{"Texture": tex_rel, "Weight": 1}],
        "HitboxType": f"{P}_Cable",
        "BlockEntity": {"Components": {f"{P}_Pipe": {"Kind": "Fluid", "Tier": "Basic"}}},
        "State": {"Definitions": {f"C{m}": {"CustomModel": model_path(m)} for m in range(1, 64)}},
        "TextureComputedColor": gc.tohex(gc.avg_color(tex)).upper(),
    })
    g.ids.append(item_id)
    d = {
        "TranslationProperties": g.name_key(item_id, "Mechanical Pipe",
                                            "Carries fluids and gases between pumps, tanks and machines."),
        "ItemLevel": 10,
        "Quality": "Common",
        "MaxStack": 100,
        "Categories": [f"{P}.Transmitters"],
        "PlayerAnimationsId": "Block",
        "Recipe": gb._wb([("Ingredient_Bar_Iron", 3), (f"{P}_Ingot_Steel", 1)], 8, 3),
        "BlockType": block,
        "Tags": {"Type": ["Metal"]},
        "IconProperties": gb.ICON_PROPS,
        "Icon": f"Icons/ItemsGenerated/{item_id}.png",
        "ItemSoundSetId": "ISS_Items_Metal",
    }
    g.save_item("Transmitters", item_id, d)


def generate(g, gc):
    gen_machines(g, gc)
    gen_pipe(g, gc)
