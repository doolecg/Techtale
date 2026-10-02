#!/usr/bin/env python3
"""Generates Techtale's ore worldgen for Hytale's new generator (HytaleGenerator / WorldGen V2).

V2 has no modifier asset: ore is a Props entry inside each biome, so a pack cannot add to a biome, only replace the
whole biome file. This script copies the vanilla biome files out of Assets.zip, appends our ore Props entries, and
writes them (plus one shared Assignments asset per ore) under src/main/resources/Server/HytaleGenerator/.
Re-run it after every Hytale update so the copied biomes follow the new vanilla data:

    python tools/gen_v2_ores.py [--assets path/to/Assets.zip]

Output is deterministic. The V1 (legacy Default generator) ore lives in Server/WorldGen/Modifier and is separate.
"""
import argparse
import json
import os
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/Server/HytaleGenerator"
PREFIX = "Server/HytaleGenerator/"
DEFAULT_ASSETS = Path(os.path.expandvars(r"%APPDATA%\Hytale\install\release\package\game\latest\Assets.zip"))

# Host rock -> ore variant (our ore blocks exist for Stone, Basalt, Shale and Slate hosts only). Like vanilla's Ore_Iron_Stone
# in Marble, the other light rocks take the Stone variant and the dark volcanic ones the Basalt variant.
HOSTS = {
    "Stone": ["Rock_Stone", "Rock_Marble", "Rock_Chalk", "Rock_Quartzite", "Rock_Sandstone", "Rock_Sandstone_White", "Rock_Sandstone_Red"],
    "Basalt": ["Rock_Basalt", "Rock_Volcanic", "Rock_Magma_Cooled"],
    "Shale": ["Rock_Shale"],
    "Slate": ["Rock_Slate"],
}

# Per ore: blob reach in blocks around the lattice point.
ORES = {"Osmium": 2, "Tin": 2, "Lead": 2, "Fluorite": 2, "Uranium": 2}

# Per zone and ore: (lattice spacing in blocks, min Y, max Y). A bigger spacing is rarer. Zone 1 mirrors the V1 modifiers;
# the other zones keep the common ores (each zone is its own world here) and make uranium deeper and rarer.
ZONES = {
    1: {"Osmium": (20, 10, 110), "Tin": (23, 30, 115), "Lead": (26, 10, 85), "Fluorite": (37, 20, 90)},
    2: {"Osmium": (24, 10, 110), "Tin": (27, 30, 115), "Lead": (26, 10, 85), "Fluorite": (37, 20, 90), "Uranium": (31, 5, 60)},
    3: {"Osmium": (24, 10, 110), "Tin": (27, 30, 115), "Lead": (26, 10, 85), "Fluorite": (37, 20, 90), "Uranium": (36, 5, 50)},
    4: {"Osmium": (24, 10, 110), "Tin": (27, 30, 115), "Lead": (26, 10, 85), "Fluorite": (37, 20, 90), "Uranium": (44, 5, 40)},
}

# Vanilla biome file (relative to Biomes/) -> zone profile. Oceans.json is shared by every zone and has no mineable ground, skipped.
BIOMES = {
    "Plains1/Plains1_Oak": 1, "Plains1/Plains1_Gorges": 1, "Plains1/Plains1_Deeproot": 1, "Plains1/Plains1_River": 1, "Plains1/Plains1_Shore": 1,
    "Desert1/Desert1_Rocky": 2, "Desert1/Desert1_Stacks": 2, "Desert1/Desert1_River": 2, "Desert1/Desert1_Shore": 2, "Desert1/Desert1_Oasis": 2,
    "Taiga1/Taiga1_Mountains": 3, "Taiga1/Taiga1_Redwood": 3, "Taiga1/Taiga1_River": 3, "Taiga1/Taiga1_Shore": 3,
    "Boreal1/Boreal1_Hedera": 3, "Boreal1/Boreal1_Henges": 3,
    "Volcanic1/Volcanic1_Caldera": 4, "Volcanic1/Volcanic1_Jungle": 4, "Volcanic1/Volcanic1_River": 4, "Volcanic1/Volcanic1_Shore": 4,
}

# World structures players reach that these biomes cover (Default_Flat has no underground; the dev structures are not player-facing).
STRUCTURES = ["Zone1_Plains1", "Zone2_Desert1", "Zone3_Taiga1", "Zone4_Volcanic1",
              "Portals_Hedera", "Portals_Henges", "Portals_Jungles", "Portals_Oasis", "Portals_Taiga"]
NO_GROUND = {"Oceans", "Void", "Void_Buffer", "Void_Buffer_Oasis"}


def solid(block):
    return {"Solid": block}


def blob_density(reach):
    """A noisy ball, the shape vanilla uses for its Density ore props, sized to `reach` blocks."""
    noise = {"Type": "SimplexNoise3D", "Skip": False, "Lacunarity": 2, "Persistence": 0.5, "Octaves": 1, "ScaleXZ": 4, "ScaleY": 4, "Seed": "A"}
    falloff = {"Type": "Anchor", "Skip": False, "Reversed": False, "Inputs": [
        {"Type": "Cube", "Skip": False, "Curve": {"Type": "Manual", "Points": [{"In": 0, "Out": 1}, {"In": reach + 1, "Out": -2}]}}]}
    return {"Type": "Sum", "Skip": False, "Inputs": [
        {"Type": "Normalizer", "Skip": False, "FromMin": -1, "FromMax": 1, "ToMin": -0.3, "ToMax": 0.3, "Inputs": [noise]},
        {"Type": "Normalizer", "Skip": False, "FromMin": -1, "FromMax": 1, "ToMin": -0.7, "ToMax": 0.7, "Inputs": [falloff]}]}


def ore_prop(ore, host, reach):
    return {
        "Type": "Density", "Skip": False,
        "Range": {"X": reach, "Y": reach, "Z": reach},
        "Pattern": {"Type": "Constant", "Value": True},
        "Scanner": {"Type": "Origin", "Skip": False},
        "Material": {"Type": "Solidity", "Solid": {"Type": "Constant", "Material": solid(f"Techtale_Ore_{ore}_{host}")}},
        "Density": blob_density(reach),
        "PlacementMask": {
            "DontPlace": {"Inclusive": True, "Materials": [solid("Empty")]},
            # Replaces only this host's rocks, so each ore block matches the stone it sits in.
            "DontReplace": {"Inclusive": False, "Materials": [solid(b) for b in HOSTS[host]]},
        },
    }


def assignments(ore):
    return {
        "Type": "Constant",
        "ExportAs": f"Techtale_Ore_{ore}",
        "Prop": {"Type": "Union", "Skip": False, "Props": [ore_prop(ore, host, ORES[ore]) for host in HOSTS]},
    }


def props_entry(ore, spacing, min_y, max_y):
    return {
        "Skip": False, "Runtime": 0,
        "Positions": {
            "Type": "SimpleHorizontal", "Skip": False,
            "RangeY": {"MinInclusive": min_y, "MaxExclusive": max_y},
            "Positions": {"Type": "Mesh3D", "Skip": False, "PointGenerator": {
                "Type": "Mesh", "Jitter": 0.5, "ScaleX": spacing, "ScaleY": spacing, "ScaleZ": spacing, "Seed": f"Techtale-{ore}"}},
        },
        "Assignments": {"Type": "Imported", "Name": f"Techtale_Ore_{ore}"},
    }


def write(path, data, pretty):
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(data, indent=2) if pretty else json.dumps(data, separators=(",", ":"))
    path.write_text(text + "\n", encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--assets", type=Path, default=DEFAULT_ASSETS, help="Hytale Assets.zip (default: the installed release)")
    args = parser.parse_args()
    if not args.assets.is_file():
        sys.exit(f"Assets.zip not found: {args.assets}")

    for ore in ORES:
        write(OUT / "Assignments/Techtale" / f"Techtale_Ore_{ore}.json", assignments(ore), True)

    covered = {b.rsplit("/", 1)[1] for b in BIOMES}
    with zipfile.ZipFile(args.assets) as zf:
        for biome, zone in BIOMES.items():
            data = json.loads(zf.read(f"{PREFIX}Biomes/{biome}.json"))
            data.pop("$NodeEditorMetadata", None)  # editor-only layout data, by far the bulk of the file
            data.setdefault("Props", []).extend(props_entry(ore, *params) for ore, params in ZONES[zone].items())
            write(OUT / "Biomes" / f"{biome}.json", data, False)
        for structure in STRUCTURES:
            used = {b["Biome"] for b in json.loads(zf.read(f"{PREFIX}WorldStructures/{structure}.json")).get("Biomes", [])}
            missing = sorted(used - covered - NO_GROUND)
            if missing:
                print(f"warning: {structure} uses biomes without ore: {missing}")
    print(f"wrote {len(ORES)} assignments and {len(BIOMES)} biomes to {OUT}")


if __name__ == "__main__":
    main()
