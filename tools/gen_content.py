#!/usr/bin/env python3
"""Generates the Phase 1 Techtale content: item/block JSON, recipes, lang file, textures and icons.

Output goes to src/main/resources (Server/ and Common/). Everything under our own owned paths is wiped
and rewritten on each run, so the generated files always match this table.

Textures and icons are made by gradient-mapping the luminance of a vanilla Hytale texture/icon onto a
per-material palette, so the vanilla shading is kept. The vanilla art is read from Assets.zip and is not shipped.

Usage: python tools/gen_content.py   (env HYTALE_ASSETS overrides the Assets.zip path)
"""
import colorsys
import io
import json
import os
import random
import shutil
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src" / "main" / "resources"
ASSETS_ZIP = os.environ.get(
    "HYTALE_ASSETS",
    os.path.expandvars(r"%APPDATA%/Hytale/install/release/package/game/latest/Assets.zip"),
)
PREFIX = "Techtale"
LANG_PREFIX = "techtale"  # file name Server/Languages/en-US/techtale.lang => keys "techtale.<key>"

# Paths we own and rewrite on every run.
OWNED_DIRS = [
    RES / "Server/Item/Items/Techtale",
    RES / "Server/Item/Recipes/Techtale",
    RES / "Common/Resources/Techtale",
    RES / "Common/BlockTextures/Techtale",
    RES / "Common/Blocks/Techtale",
    RES / "Server/Item/Block/Hitboxes/Techtale",
]
OWNED_FILES = [
    RES / "Server/Languages/en-US/techtale.lang",
    RES / "Server/Item/Category/CreativeLibrary/Techtale.json",
    RES / "Common/Icons/ItemCategories/Techtale.png",
    RES / "Common/Icons/ItemCategories/TechtaleActive.png",
]
ICON_DIR = RES / "Common/Icons/ItemsGenerated"


# ----------------------------------------------------------------------------------------------
# Palettes: (shadow, base, highlight)
# ----------------------------------------------------------------------------------------------

def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def tohex(c):
    return "#%02x%02x%02x" % tuple(max(0, min(255, int(round(v)))) for v in c)


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def adjust(c, sat=1.0, val=1.0):
    h, s, v = colorsys.rgb_to_hsv(*(x / 255 for x in c))
    s = min(1, s * sat)
    v = min(1, v * val)
    return tuple(x * 255 for x in colorsys.hsv_to_rgb(h, s, v))


def pal(dark, base, light):
    return (hexc(dark), hexc(base), hexc(light))


def lighten(p, t):
    return tuple(mix(c, (255, 255, 255), t) for c in p)


def mudded(p, t, dark=1.0):
    mud = (96, 78, 58)
    return tuple(adjust(mix(c, mud, t), 0.9, dark) for c in p)


def vivid(p, sat, val=1.0):
    return tuple(adjust(c, sat, val) for c in p)


# Our own materials
PAL = {
    "Osmium": pal("#566a86", "#9db6d0", "#e2eef9"),         # pale blue-grey
    "Tin": pal("#96989e", "#cfd2d8", "#fafbfd"),            # light grey-white
    "Lead": pal("#232a3e", "#4e5a74", "#8c98b2"),           # dark blue-grey
    "Uranium": pal("#1c5a1c", "#52b835", "#c4ff7a"),        # green
    "Fluorite": pal("#6a3fb8", "#a9a6f0", "#c9fff4"),       # light purple-cyan gem
    "Steel": pal("#1f2126", "#51565f", "#9aa1ad"),          # dark grey
    "Refined_Obsidian": pal("#160828", "#4a1d78", "#a066dc"),
    "Obsidian": pal("#0c0716", "#2a1846", "#6a4a9a"),
    "Diamond": pal("#5fb7d9", "#a8e8f8", "#f2ffff"),
    "Charcoal": pal("#0e0e10", "#2c2c31", "#6a6a72"),
    "Carbon": pal("#08080a", "#26262c", "#6c6c78"),
    "Infused": pal("#5a0c0c", "#c92b2b", "#ff9a86"),         # red alloy
    "Reinforced": pal("#0e2870", "#3a76dc", "#a8d4ff"),      # blue alloy
    "Atomic": pal("#3c0a68", "#9a3ad2", "#f0a2ff"),          # purple alloy
    "Basic": pal("#2f4a36", "#6fa87c", "#d2f0d4"),
    "Advanced": pal("#6a1c10", "#dc5a28", "#ffc298"),
    "Elite": pal("#0a3a58", "#2ea6d8", "#b4f0ff"),
    "Ultimate": pal("#34104e", "#8a38c8", "#ecb4ff"),
    "Speed": pal("#163c80", "#3c98f0", "#c4f2ff"),
    "Energy": pal("#14602a", "#38cc58", "#c8ffa8"),
}
# Vanilla metals: palette is sampled from the vanilla ingot texture. Fallbacks are used when the zip lacks it.
VANILLA_METALS = ["Iron", "Copper", "Gold", "Silver", "Cobalt", "Thorium", "Bronze"]


# ----------------------------------------------------------------------------------------------
# Vanilla asset access + gradient mapping
# ----------------------------------------------------------------------------------------------

class Vanilla:
    def __init__(self, zip_path):
        self.z = zipfile.ZipFile(zip_path)

    def img(self, name):
        return Image.open(io.BytesIO(self.z.read("Common/" + name))).convert("RGBA")


def luminance(px):
    return 0.2126 * px[0] + 0.7152 * px[1] + 0.0722 * px[2]


def grad(p, t):
    t = max(0.0, min(1.0, t))
    if t < 0.5:
        return mix(p[0], p[1], t * 2)
    return mix(p[1], p[2], (t - 0.5) * 2)


def percentile(vals, q):
    vals = sorted(vals)
    return vals[min(len(vals) - 1, max(0, int(round(q * (len(vals) - 1)))))]


def gradient_map(im, p, weights=None, gamma=1.0):
    """Recolour the opaque pixels of im by their luminance. weights (same size, 0..1) limits where it applies."""
    im = im.convert("RGBA")
    px = im.load()
    w, h = im.size
    lums = []
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a > 0 and (weights is None or weights[y][x] > 0.5):
                lums.append(luminance((r, g, b)))
    if not lums:
        return im
    lo, hi = percentile(lums, 0.03), percentile(lums, 0.97)
    span = max(1.0, hi - lo)
    out = Image.new("RGBA", im.size)
    op = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                op[x, y] = (0, 0, 0, 0)
                continue
            t = ((luminance((r, g, b)) - lo) / span) ** gamma
            new = grad(p, t)
            wt = 1.0 if weights is None else weights[y][x]
            col = mix((r, g, b), new, wt)
            op[x, y] = (int(round(col[0])), int(round(col[1])), int(round(col[2])), a)
    return out


def pixels(im):
    px = im.load()
    return [px[x, y] for y in range(im.height) for x in range(im.width)]


def sample_palette(im):
    """(p3-15, p42-58, p85-97) mean colours of the opaque pixels, ordered by luminance."""
    px = [p for p in pixels(im) if p[3] > 0]
    px.sort(key=lambda c: luminance(c))
    n = len(px)

    def avg(lo, hi):
        seg = px[int(n * lo):max(int(n * lo) + 1, int(n * hi))]
        return tuple(sum(c[i] for c in seg) / len(seg) for i in range(3))

    return (avg(0.03, 0.15), avg(0.42, 0.58), avg(0.85, 0.97))


def avg_color(im):
    px = [p for p in pixels(im) if p[3] > 128]
    return tuple(sum(c[i] for c in px) / len(px) for i in range(3))


def save_png(im, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path, optimize=True)


# ----------------------------------------------------------------------------------------------
# Content table
# ----------------------------------------------------------------------------------------------

# kind -> how the item looks. tex/icon are vanilla sources that get recoloured.
KINDS = {
    "Raw": dict(model="Resources/Ores/Ore_Large.blockymodel", tex="Resources/Ores/Ore_Textures/Copper.png",
                icon="Icons/ItemsGenerated/Ore_Copper.png", texdir="Ore_Textures",
                props=dict(Scale=0.57323, Translation=[-3.6, -16.2], Rotation=[22.5, 45, 22.5])),
    "Ingot": dict(model="Resources/Materials/Ingot.blockymodel", tex="Resources/Materials/Ingot_Textures/Copper.png",
                  icon="Icons/ItemsGenerated/Ingredient_Bar_Copper.png", texdir="Ingot_Textures",
                  props=dict(Scale=1, Translation=[0, -3], Rotation=[22.5, 45, 22.5])),
    "Gem": dict(model="Resources/Ores/Gem.blockymodel", tex="Resources/Ores/Gem_Textures/Emerald.png",
                icon="Icons/ItemsGenerated/Rock_Gem_Emerald.png", texdir="Gem_Textures",
                props=dict(Scale=0.6, Translation=[0, -10], Rotation=[22.5, 45, 22.5]), scale=0.55),
    "Dust": dict(model="Blocks/Stone/Rubble_Charcoal.blockymodel", tex="Items/Rubble/Rubble_Textures/Stone.png",
                 icon="Icons/ItemsGenerated/Rubble_Charcoal_Small.png", texdir="Dust_Textures",
                 props=dict(Scale=0.9, Translation=[0, -6], Rotation=[22.5, 45, 22.5]), scale=0.6),
    "Dirty_Dust": dict(model="Blocks/Stone/Rubble_Small.blockymodel", tex="Items/Rubble/Rubble_Textures/Stone.png",
                       icon="Icons/ItemsGenerated/Rubble_Stone.png", texdir="Dirty_Dust_Textures",
                       props=dict(Scale=0.9, Translation=[0, -6], Rotation=[22.5, 45, 22.5]), scale=0.6),
    "Clump": dict(model="Blocks/Stone/Rubble_Medium.blockymodel", tex="Items/Rubble/Rubble_Textures/Stone.png",
                  icon="Icons/ItemsGenerated/Rubble_Stone_Medium.png", texdir="Clump_Textures",
                  props=dict(Scale=0.8, Translation=[0, -6], Rotation=[22.5, 45, 22.5]), scale=0.55),
    "Shard": dict(model="Resources/Crystals/Crystal_Fragment.blockymodel",
                  tex="Resources/Crystals/Crystal_Fragment_Textures/Blue.png",
                  icon="Icons/ItemsGenerated/Ingredient_Crystal_Fragments_Blue.png", texdir="Shard_Textures",
                  props=dict(Scale=0.75, Translation=[-0.3, 0.3], Rotation=[34.315, 29.815, 22.5]), scale=1.2),
    "Crystal": dict(model="Resources/Crystals/Crystal_Small.blockymodel",
                    tex="Resources/Crystals/Crystal_Big_Textures/Blue.png",
                    icon="Icons/ItemsGenerated/Rock_Crystal_Blue_Small.png", texdir="Crystal_Textures",
                    props=dict(Scale=0.8, Translation=[0, -8], Rotation=[22.5, 45, 22.5]), scale=0.7),
    "Enriched": dict(model="Resources/Ingredients/Essence.blockymodel",
                     tex="Resources/Ingredients/Essence_Textures/Fire_Essence_Texture.png",
                     icon="Icons/ItemsGenerated/Ingredient_Fire_Essence.png", texdir="Enriched_Textures",
                     props=dict(Scale=0.6, Translation=[0, -13], Rotation=[0, 0, 0]), scale=0.8),
    "Alloy": dict(model="Resources/Materials/Ingot.blockymodel", tex="Resources/Materials/Ingot_Textures/Iron.png",
                  icon="Icons/ItemsGenerated/Ingredient_Bar_Iron.png", texdir="Alloy_Textures",
                  props=dict(Scale=1, Translation=[0, -3], Rotation=[22.5, 45, 22.5])),
    "Circuit": dict(model="Resources/Materials/Fabric_Scrap.blockymodel",
                    tex="Resources/Materials/Fabric_Scrap_Textures/Cotton.png",
                    icon="Icons/ItemsGenerated/Ingredient_Fabric_Scrap_Cotton.png", texdir="Circuit_Textures",
                    props=dict(Scale=0.6, Translation=[0, -6], Rotation=[22.5, 45, 22.5])),
    "Upgrade": dict(model="Resources/Ingredients/Essence_Concentrated.blockymodel",
                    tex="Resources/Ingredients/Essence_Textures/Life_Essence_Texture.png",
                    icon="Icons/ItemsGenerated/Ingredient_Life_Essence_Concentrated.png", texdir="Upgrade_Textures",
                    props=dict(Scale=0.6, Translation=[0, -13], Rotation=[0, 0, 0]), scale=0.55),
}
# per-kind palette transform applied on top of the material palette
KIND_TWEAK = {
    "Dust": lambda p: lighten(p, 0.12),
    "Dirty_Dust": lambda p: mudded(p, 0.45, 0.8),
    "Clump": lambda p: mudded(p, 0.22, 0.95),
    "Shard": lambda p: vivid(lighten(p, 0.1), 1.15),
    "Crystal": lambda p: vivid(lighten(p, 0.05), 1.3),
}

ORE_METALS = ["Osmium", "Tin", "Lead", "Uranium"]
ORE_ALL = ORE_METALS + ["Fluorite"]
HOSTS = ["Stone", "Basalt", "Shale", "Slate"]
PROCESS_METALS = ["Osmium", "Tin", "Lead", "Uranium", "Iron", "Copper", "Gold", "Silver", "Cobalt", "Thorium"]
INGOTS = ["Osmium", "Tin", "Lead", "Uranium", "Steel", "Refined_Obsidian"]
DUSTS = ["Osmium", "Tin", "Lead", "Uranium", "Iron", "Copper", "Gold", "Silver", "Cobalt", "Thorium", "Steel", "Bronze",
         "Obsidian", "Refined_Obsidian", "Diamond", "Charcoal", "Fluorite"]
# material whose palette an enriched/other id uses
ENRICHED = ["Carbon", "Copper", "Diamond", "Refined_Obsidian", "Tin", "Iron"]
ALLOYS = ["Infused", "Reinforced", "Atomic"]
CIRCUITS = ["Basic", "Advanced", "Elite", "Ultimate"]
UPGRADES = ["Speed", "Energy"]

# dust -> furnace target ingot item id (vanilla or ours)
DUST_SMELTS_TO = {m: f"{PREFIX}_Ingot_{m}" for m in ["Osmium", "Tin", "Lead", "Uranium", "Steel", "Refined_Obsidian"]}
DUST_SMELTS_TO.update({m: f"Ingredient_Bar_{m}" for m in ["Iron", "Copper", "Gold", "Silver", "Cobalt", "Thorium", "Bronze"]})

HOST_COBBLE = {h: f"Rock_{h}_Cobble" for h in HOSTS}
HOST_ICON_SOURCE = {h: f"Icons/ItemsGenerated/Ore_Cobalt_{h}.png" for h in HOSTS}  # blue ore on each host rock

CATEGORIES = [
    ("Ores", "Ores", "Icons/ItemCategories/Natural-Ore.png"),
    ("Materials", "Materials", "Icons/ItemCategories/Metal.png"),
    ("Dusts", "Dusts", "Icons/ItemCategories/Items-Ingredients.png"),
    ("Processing", "Processing", "Icons/ItemCategories/TechnicalBlocks.png"),
    ("Components", "Components", "Icons/ItemCategories/Misc.png"),
    ("Machines", "Machines", "Icons/ItemCategories/Bench.png"),
    ("Transmitters", "Transmitters", "Icons/ItemCategories/Blocks.png"),
]


def nice(s):
    return s.replace("_", " ")


# ----------------------------------------------------------------------------------------------
# Generator state
# ----------------------------------------------------------------------------------------------

class Gen:
    def __init__(self):
        self.v = Vanilla(ASSETS_ZIP)
        self.lang = {}
        self.ids = []
        self.palettes = dict(PAL)
        for m in VANILLA_METALS:
            self.palettes[m] = sample_palette(self.v.img(f"Resources/Materials/Ingot_Textures/{m}.png"))
        self._src = {}

    def src(self, name):
        if name not in self._src:
            self._src[name] = self.v.img(name)
        return self._src[name]

    # -- output helpers
    def write_json(self, rel, data):
        path = RES / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    def name_key(self, item_id, name, desc=None):
        self.lang[f"items.{item_id}.name"] = name
        props = {"Name": f"{LANG_PREFIX}.items.{item_id}.name"}
        if desc:
            self.lang[f"items.{item_id}.description"] = desc
            props["Description"] = f"{LANG_PREFIX}.items.{item_id}.description"
        return props

    def palette(self, material, kind):
        p = self.palettes[material]
        tw = KIND_TWEAK.get(kind)
        return tw(p) if tw else p

    def make_assets(self, kind, material, item_id, tex_name):
        """Writes the recoloured model texture and icon; returns (texture path, icon path)."""
        k = KINDS[kind]
        p = self.palette(material, kind)
        tex = gradient_map(self.src("" + k["tex"]), p)
        tex_rel = f"Resources/{PREFIX}/{k['texdir']}/{tex_name}.png"
        save_png(tex, RES / "Common" / tex_rel)
        icon = gradient_map(self.src(k["icon"]), p)
        save_png(icon, ICON_DIR / f"{item_id}.png")
        return tex_rel, f"Icons/ItemsGenerated/{item_id}.png"

    def item(self, kind, material, item_id, name, category, tag="Ingredient", extra=None, desc=None, tex_name=None,
             quality=None, sound="ISS_Items_Ingots"):
        k = KINDS[kind]
        tex_rel, icon_rel = self.make_assets(kind, material, item_id, tex_name or material)
        d = {
            "TranslationProperties": self.name_key(item_id, name, desc),
            "Categories": [f"{PREFIX}.{category}"],
        }
        if quality:
            d["Quality"] = quality
        d.update({
            "Icon": icon_rel,
            "Model": k["model"],
            "Texture": tex_rel,
            "PlayerAnimationsId": "Item",
            "IconProperties": k["props"],
            "Tags": {"Type": [tag]},
            "ItemEntity": {"ParticleSystemId": None},
        })
        if "scale" in k:
            d["Scale"] = k["scale"]
        d["ItemSoundSetId"] = sound
        d["DropOnDeath"] = True
        if extra:
            d.update(extra)
        self.ids.append(item_id)
        return d

    def save_item(self, folder, item_id, d):
        self.write_json(f"Server/Item/Items/{PREFIX}/{folder}/{item_id}.json", d)

    # -- recipes
    @staticmethod
    def furnace(inputs, output_qty=1, secs=10):
        return {
            "Input": [{"ItemId": i, "Quantity": q} for i, q in inputs],
            "BenchRequirement": [{"Type": "Processing", "Id": "Furnace"}],
            "OutputQuantity": output_qty,
            "TimeSeconds": secs,
        }

    def standalone_recipe(self, name, inputs, out_item, out_qty, bench, secs):
        out = {"ItemId": out_item, "Quantity": out_qty}
        self.write_json(f"Server/Item/Recipes/{PREFIX}/{name}.json", {
            "Input": inputs,
            "PrimaryOutput": out,
            "Output": [dict(out)],
            "BenchRequirement": [bench],
            "TimeSeconds": secs,
        })

    # -- block icon helpers
    def ore_block_icon(self, metal, host, item_id):
        base = self.src(HOST_ICON_SOURCE[host])
        w, h = base.size
        px = base.load()
        weights = [[0.0] * w for _ in range(h)]
        for y in range(h):
            for x in range(w):
                r, g, b, a = px[x, y]
                if a == 0:
                    continue
                hh, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                hue = hh * 360
                if 185 <= hue <= 255 and s > 0.30 and b > r + 12:
                    weights[y][x] = min(1.0, (s - 0.25) / 0.2)
        icon = gradient_map(base, self.palettes[metal], weights)
        save_png(icon, ICON_DIR / f"{item_id}.png")
        return icon

    # -- generation sections
    def gen_ores(self):
        for metal in ORE_ALL:
            p = self.palettes[metal]
            # ore overlay texture shared by all four host variants
            ore_tex = gradient_map(self.src(KINDS["Raw"]["tex"]), p)
            ore_rel = f"Resources/{PREFIX}/Ore_Textures/{metal}.png"
            save_png(ore_tex, RES / "Common" / ore_rel)
            quality = 3 if metal == "Uranium" else 2
            mid = tohex(p[1])
            for host in HOSTS:
                item_id = f"{PREFIX}_Ore_{metal}_{host}"
                if metal == "Fluorite":
                    drop = {"Type": "Single", "Item": {"ItemId": f"{PREFIX}_Fluorite_Gem", "QuantityMin": 2, "QuantityMax": 2}}
                else:
                    drop = {"Type": "Single", "Item": {"ItemId": f"{PREFIX}_Raw_{metal}"}}
                icon = self.ore_block_icon(metal, host, item_id)
                host_px = self.src(f"BlockTextures/Rock_{host}.png")
                computed = tohex(mix(avg_color(host_px), avg_color(icon), 0.5))
                d = {
                    "TranslationProperties": self.name_key(item_id, f"{metal} Ore - {host}"),
                    "Icon": f"Icons/ItemsGenerated/{item_id}.png",
                    "Categories": [f"{PREFIX}.Ores"],
                    "BlockType": {
                        "Material": "Solid",
                        "DrawType": "CubeWithModel",
                        "CustomModel": "Resources/Ores/Ore_Large.blockymodel",
                        "CustomModelTexture": [{"Texture": ore_rel, "Weight": 1}],
                        "Group": "Stone",
                        "Flags": {},
                        "RandomRotation": "YawStep90",
                        "Gathering": {"Breaking": {
                            "GatherType": "Rocks",
                            "Quality": quality,
                            "DropList": {"Container": {"Type": "Multiple", "Containers": [
                                drop,
                                {"Type": "Single", "Item": {"ItemId": HOST_COBBLE[host]}},
                            ]}},
                        }},
                        "BlockParticleSetId": "Ore",
                        "Textures": [{"Weight": 1, "All": f"BlockTextures/Rock_{host}.png"}],
                        "ParticleColor": mid,
                        "BlockSoundSetId": "Ore",
                        "PhysicalMaterialId": "Stone",
                        "TextureComputedColor": computed.upper(),
                    },
                    "PlayerAnimationsId": "Block",
                    "Tags": {"Type": ["Ore"], "Family": [metal]},
                    "MaxStack": 25,
                    "ItemSoundSetId": "ISS_Blocks_Stone",
                }
                self.ids.append(item_id)
                self.save_item("Ores", item_id, d)

    def gen_raw_and_gem(self):
        for metal in ORE_METALS:
            item_id = f"{PREFIX}_Raw_{metal}"
            d = self.item("Raw", metal, item_id, f"Raw {metal}", "Ores", tag="Ore", sound="ISS_Blocks_Stone",
                          extra={"PlayerAnimationsId": "Block", "ItemLevel": 10, "MaxStack": 25})
            self.save_item("Ores", item_id, d)
        item_id = f"{PREFIX}_Fluorite_Gem"
        d = self.item("Gem", "Fluorite", item_id, "Fluorite Gem", "Ores", sound="ISS_Items_Gems",
                      extra={"MaxStack": 25, "Tags": {"Type": ["Ingredient"], "Family": ["Gem"]}})
        self.save_item("Ores", item_id, d)

    def gen_ingots(self):
        for m in INGOTS:
            item_id = f"{PREFIX}_Ingot_{m}"
            extra = {"ResourceTypes": [{"Id": "Metal_Bars"}]}
            if m in ORE_METALS:
                extra["Recipe"] = self.furnace([(f"{PREFIX}_Raw_{m}", 1)])
            d = self.item("Ingot", m, item_id, f"{nice(m)} Ingot", "Materials", extra=extra)
            self.save_item("Materials", item_id, d)

    def gen_dusts(self):
        for m in DUSTS:
            item_id = f"{PREFIX}_Dust_{m}"
            d = self.item("Dust", m, item_id, f"{nice(m)} Dust", "Dusts", sound="ISS_Blocks_Stone")
            self.save_item("Dusts", item_id, d)
            target = DUST_SMELTS_TO.get(m)
            if target:
                self.standalone_recipe(
                    f"{PREFIX}_Smelt_Dust_{m}", [{"ItemId": item_id, "Quantity": 1}], target, 1,
                    {"Type": "Processing", "Id": "Furnace"}, 10)

    def gen_processing(self):
        for kind, label in [("Dirty_Dust", "Dirty {} Dust"), ("Clump", "{} Clump"), ("Shard", "{} Shard"),
                            ("Crystal", "{} Crystal")]:
            for m in PROCESS_METALS:
                item_id = f"{PREFIX}_{kind}_{m}"
                extra = {"Tags": {"Type": ["Ingredient"], "Family": [kind.replace("_", "")]}} if kind in ("Shard", "Crystal") else None
                d = self.item(kind, m, item_id, label.format(m), "Processing", sound="ISS_Blocks_Stone"
                              if kind in ("Dirty_Dust", "Clump") else "ISS_Items_Gems", extra=extra)
                self.save_item("Processing", item_id, d)

    def gen_enriched(self):
        for m in ENRICHED:
            item_id = f"{PREFIX}_Enriched_{m}"
            d = self.item("Enriched", m, item_id, f"Enriched {nice(m)}", "Materials", quality="Uncommon",
                          sound="ISS_Items_Gems")
            self.save_item("Materials", item_id, d)
        for m in ALLOYS:
            item_id = f"{PREFIX}_Alloy_{m}"
            d = self.item("Alloy", m, item_id, f"{m} Alloy", "Materials", quality="Uncommon",
                          extra={"ResourceTypes": [{"Id": "Metal_Bars"}]})
            self.save_item("Materials", item_id, d)

    def gen_components(self):
        quals = {"Basic": "Common", "Advanced": "Uncommon", "Elite": "Rare", "Ultimate": "Epic"}
        circuit_recipe = {  # Mekanism style: previous circuit + 2 of the matching alloy; Basic comes from the infuser
            "Advanced": ("Basic", "Infused"), "Elite": ("Advanced", "Reinforced"), "Ultimate": ("Elite", "Atomic"),
        }
        for m in CIRCUITS:
            item_id = f"{PREFIX}_Circuit_{m}"
            extra = None
            if m in circuit_recipe:
                prev, alloy = circuit_recipe[m]
                extra = {"Recipe": {
                    "Input": [{"ItemId": f"{PREFIX}_Circuit_{prev}", "Quantity": 1},
                              {"ItemId": f"{PREFIX}_Alloy_{alloy}", "Quantity": 2}],
                    "BenchRequirement": [{"Type": "Crafting", "Id": "Workbench", "Categories": ["Workbench_Crafting"]}],
                    "OutputQuantity": 1,
                    "TimeSeconds": 5,
                }}
            d = self.item("Circuit", m, item_id, f"{m} Control Circuit", "Components", quality=quals[m],
                          sound="ISS_Items_Ingots", extra=extra)
            self.save_item("Components", item_id, d)
        for m in UPGRADES:
            item_id = f"{PREFIX}_Upgrade_{m}"
            recipe_in = [{"ItemId": f"{PREFIX}_Alloy_Infused", "Quantity": 2},
                         {"ItemId": f"{PREFIX}_Dust_{'Osmium' if m == 'Speed' else 'Gold'}", "Quantity": 1},
                         {"ResourceTypeId": "Crystal_Shards", "Quantity": 2}]
            extra = {"MaxStack": 8, "Recipe": {
                "Input": recipe_in,
                "BenchRequirement": [{"Type": "Crafting", "Id": "Workbench", "Categories": ["Workbench_Crafting"]}],
                "OutputQuantity": 1,
                "TimeSeconds": 5,
            }}
            d = self.item("Upgrade", m, item_id, f"{m} Upgrade", "Components", quality="Uncommon",
                          sound="ISS_Items_Gems", extra=extra,
                          desc=f"A machine upgrade ({m.lower()}). Up to 8 can be installed in a machine.")
            self.save_item("Components", item_id, d)

    # -- Steel casing
    def steel_texture(self):
        rnd = random.Random(7)
        n = 32
        im = Image.new("RGBA", (n, n))
        px = im.load()
        base = (62, 66, 74)
        edge_dark = (26, 28, 33)
        hi = (118, 124, 136)
        sh = (40, 43, 50)
        for y in range(n):
            for x in range(n):
                j = rnd.randint(-4, 4)
                px[x, y] = tuple(max(0, min(255, c + j)) for c in base) + (255,)
        # outer border
        for i in range(n):
            for (x, y) in ((i, 0), (i, n - 1), (0, i), (n - 1, i)):
                px[x, y] = edge_dark + (255,)
        # bevel inside the border
        for i in range(1, n - 1):
            px[i, 1] = hi + (255,)
            px[1, i] = hi + (255,)
            px[i, n - 2] = sh + (255,)
            px[n - 2, i] = sh + (255,)
        # recessed centre plate
        for y in range(8, 24):
            for x in range(8, 24):
                j = rnd.randint(-3, 3)
                c = (50 + j, 54 + j, 62 + j)
                if x == 8 or y == 8:
                    c = sh
                elif x == 23 or y == 23:
                    c = (92, 98, 110)
                px[x, y] = c + (255,)
        # horizontal vent slots in the plate
        for y in (12, 15, 18):
            for x in range(11, 21):
                px[x, y] = (24, 26, 31, 255)
                px[x, y + 1] = (84, 90, 102, 255)
        # rivets in the corners
        for cx, cy in ((4, 4), (27, 4), (4, 27), (27, 27)):
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                px[cx + dx, cy + dy] = (150, 156, 168, 255) if (dx, dy) != (1, 1) else (96, 102, 114, 255)
            px[cx + 2, cy + 2] = (24, 26, 31, 255)
        return im

    def cube_icon(self, tex):
        """64x64 isometric cube from a 32x32 block texture."""
        icon = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        out = icon.load()
        tp = tex.load()
        tw, th = tex.size
        faces = [  # origin, U axis, V axis, shade
            ((8, 20), (24, -12), (24, 12), 1.0),   # top
            ((8, 20), (24, 12), (0, 26), 0.78),    # left
            ((32, 32), (24, -12), (0, 26), 0.56),  # right
        ]
        for y in range(64):
            for x in range(64):
                for (o, u, v, shade) in faces:
                    px_, py_ = x + 0.5 - o[0], y + 0.5 - o[1]
                    det = u[0] * v[1] - u[1] * v[0]
                    a = (px_ * v[1] - py_ * v[0]) / det
                    b = (u[0] * py_ - u[1] * px_) / det
                    if 0 <= a < 1 and 0 <= b < 1:
                        r, g, bb, al = tp[int(a * tw), int(b * th)]
                        out[x, y] = (int(r * shade), int(g * shade), int(bb * shade), 255)
                        break
        return icon

    def gen_casing(self):
        item_id = f"{PREFIX}_Steel_Casing"
        tex = self.steel_texture()
        tex_rel = f"BlockTextures/{PREFIX}/Steel_Casing.png"
        save_png(tex, RES / "Common" / tex_rel)
        icon = self.cube_icon(tex)
        save_png(icon, ICON_DIR / f"{item_id}.png")
        self.ids.append(item_id)
        d = {
            "TranslationProperties": self.name_key(item_id, "Steel Casing",
                                                   "A sturdy steel block used to build machines and multiblock structures."),
            "ItemLevel": 10,
            "Quality": "Common",
            "MaxStack": 50,
            "Categories": [f"{PREFIX}.Components"],
            "PlayerAnimationsId": "Block",
            "Recipe": {
                "Input": [
                    {"ItemId": f"{PREFIX}_Ingot_Steel", "Quantity": 4},
                    {"ItemId": f"{PREFIX}_Ingot_Osmium", "Quantity": 1},
                    {"ResourceTypeId": "Crystal_Shards", "Quantity": 4},
                ],
                "BenchRequirement": [{"Type": "Crafting", "Id": "Workbench", "Categories": ["Workbench_Crafting"]}],
                "OutputQuantity": 1,
                "TimeSeconds": 5,
            },
            "BlockType": {
                "Material": "Solid",
                "DrawType": "Cube",
                "Group": "Metal",
                "HitboxType": "Full",
                "Flags": {},
                "Gathering": {"Breaking": {"GatherType": "Rocks"}},
                "BlockParticleSetId": "Metal",
                "Textures": [{"Weight": 1, "All": tex_rel}],
                "ParticleColor": "#5b606a",
                "BlockSoundSetId": "Metal",
                "PhysicalMaterialId": "Metal",
                "TransitionToGroups": ["Stone", "Sand", "Wood", "Mud", "Dirt"],
                "CubeShadingMode": "Standard",
                "TextureComputedColor": tohex(avg_color(tex)).upper(),
            },
            "Tags": {"Type": ["Metal"]},
            "IconProperties": {"Scale": 0.58823, "Rotation": [22.5, 45, 22.5], "Translation": [0, -13.5]},
            "Icon": f"Icons/ItemsGenerated/{item_id}.png",
            "ItemSoundSetId": "ISS_Items_Metal",
        }
        self.save_item("Components", item_id, d)

    # -- category
    def category_icons(self):
        """Techtale top-level tab icon (grey) and its Active (gold) variant: a cog, 88x88."""
        import math
        for name, body, edge in (("Techtale", (92, 96, 108), (60, 63, 72)), ("TechtaleActive", (246, 196, 70), (190, 130, 30))):
            S = 4
            n = 88 * S
            im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
            dr = ImageDraw.Draw(im)
            cx = cy = n / 2
            teeth = 8
            pts = []
            r_out, r_in = 38 * S, 30 * S
            for i in range(teeth * 4):
                ang = 2 * math.pi * i / (teeth * 4)
                r = r_out if (i % 4) in (1, 2) else r_in
                pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
            dr.polygon(pts, fill=edge + (255,))
            inset = []
            for (x, y) in pts:
                inset.append((cx + (x - cx) * 0.9, cy + (y - cy) * 0.9))
            dr.polygon(inset, fill=body + (255,))
            dr.ellipse([cx - 13 * S, cy - 13 * S, cx + 13 * S, cy + 13 * S], fill=(0, 0, 0, 0))
            dr.ellipse([cx - 13 * S, cy - 13 * S, cx + 13 * S, cy + 13 * S], outline=edge + (255,), width=2 * S)
            # clear the hole
            mask = Image.new("L", (n, n), 0)
            ImageDraw.Draw(mask).ellipse([cx - 11 * S, cy - 11 * S, cx + 11 * S, cy + 11 * S], fill=255)
            im.paste((0, 0, 0, 0), mask=mask)
            im = im.resize((88, 88), Image.LANCZOS)
            save_png(im, RES / f"Common/Icons/ItemCategories/{name}.png")

    def gen_category(self):
        children = []
        for cid, label, icon in CATEGORIES:
            self.lang[f"ui.itemcategory.{cid.lower()}"] = label
            children.append({"Id": cid, "Name": f"{LANG_PREFIX}.ui.itemcategory.{cid.lower()}", "Icon": icon})
        self.write_json("Server/Item/Category/CreativeLibrary/Techtale.json", {
            "Icon": "Icons/ItemCategories/Techtale.png",
            "Order": 10,
            "Children": children,
        })
        self.category_icons()

    def gen_lang(self):
        lines = ["# Generated by tools/gen_content.py. Keys are prefixed with the file name: techtale.<key>.", ""]
        for k in sorted(self.lang):
            lines.append(f"{k} = {self.lang[k]}")
        path = RES / "Server/Languages/en-US/techtale.lang"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    def run(self):
        for d in OWNED_DIRS:
            shutil.rmtree(d, ignore_errors=True)
        for f in OWNED_FILES:
            f.unlink(missing_ok=True)
        ICON_DIR.mkdir(parents=True, exist_ok=True)
        for f in ICON_DIR.glob(f"{PREFIX}_*.png"):
            f.unlink()
        self.gen_ores()
        self.gen_raw_and_gem()
        self.gen_ingots()
        self.gen_dusts()
        self.gen_processing()
        self.gen_enriched()
        self.gen_components()
        self.gen_casing()
        import gen_blocks
        gen_blocks.generate(self, sys.modules[__name__])
        __import__("gen_fluids").generate(self, sys.modules[__name__])
        self.gen_category()
        self.gen_lang()
        print(f"Generated {len(self.ids)} items/blocks, {len(self.lang)} lang keys.")


if __name__ == "__main__":
    Gen().run()
