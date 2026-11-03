#!/usr/bin/env python3
"""Bundle the offline city map into the app (app/src/main/assets/map/).

Input is what the backend's map pipeline builds (piyo-backend: deploy/tiles/build-tiles.sh):
  <data>/tiles/santa-cruz.pmtiles        vector tiles of the metro area (OpenStreetMap, Protomaps schema)
  <data>/style/style-{light,dark}.json   one Protomaps layer set in two palettes (make-style.mjs)
  <data>/assets/fonts, assets/sprites/v4 glyph PBFs and sprites (protomaps/basemaps-assets)

Output, read by MapLibre Native from the APK (docs/implementation/MAP_HOSTING.md §Bundled map):
  map/style-{light,dark}.json  glyphs/sprite -> asset://map/..., source -> pmtiles://{PMTILES}
                               ({PMTILES} is replaced at runtime by the copied file's file:// URL)
  map/fonts/<Font-Name>/<range>.pbf  only the three fonts the styles use, Latin + punctuation ranges.
                               Font names lose their spaces so the asset:// glyph URL needs no escaping.
  map/sprites/{light,dark}[@2x].{json,png}
  map/santa-cruz.pmtiles       stored uncompressed (noCompress in build.gradle.kts)
  map/LICENSES.txt

Usage (from app/):  python scripts/bundle-map.py ../deploy/data
Re-run after rebuilding the tiles; commit the result. Standard library only.
"""
import json
import shutil
import sys
from pathlib import Path

# Latin, Latin Extended A/B, IPA, combining marks, Latin Extended Additional, punctuation, letterlike
# symbols (№, ™), arrows/math. Labels in Santa Cruz need nothing else; a glyph outside these ranges is
# simply not drawn.
RANGES = ["0-255", "256-511", "512-767", "768-1023", "7680-7935", "8192-8447", "8448-8703", "8704-8959"]
THEMES = ["light", "dark"]
ATTRIBUTION = "© OpenStreetMap contributors · Protomaps"


def font_id(name: str) -> str:
    return name.replace(" ", "-")


def rename_fonts(node, used: set):
    """Rewrite every font name inside text-font values (plain arrays or expressions)."""
    if isinstance(node, list):
        if node and node[0] == "literal" and len(node) == 2 and isinstance(node[1], list):
            return ["literal", rename_fonts(node[1], used)]
        if node and all(isinstance(x, str) and x.startswith("Noto Sans") for x in node):
            used.update(node)
            return [font_id(x) for x in node]
        return [rename_fonts(x, used) for x in node]
    return node


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    data = Path(sys.argv[1])
    out = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "map"
    if out.exists():
        shutil.rmtree(out)
    (out / "sprites").mkdir(parents=True)

    used: set = set()
    for theme in THEMES:
        style = json.loads((data / "style" / f"style-{theme}.json").read_text(encoding="utf-8"))
        for layer in style["layers"]:
            layout = layer.get("layout", {})
            if "text-font" in layout:
                layout["text-font"] = rename_fonts(layout["text-font"], used)
        style["name"] = f"Piyo {theme} (bundled)"
        style["glyphs"] = "asset://map/fonts/{fontstack}/{range}.pbf"
        style["sprite"] = f"asset://map/sprites/{theme}"
        style["sources"] = {"protomaps": {"type": "vector", "url": "pmtiles://{PMTILES}", "attribution": ATTRIBUTION}}
        (out / f"style-{theme}.json").write_text(json.dumps(style, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        for suffix in ("", "@2x"):
            for ext in ("json", "png"):
                shutil.copy(data / "assets" / "sprites" / "v4" / f"{theme}{suffix}.{ext}", out / "sprites" / f"{theme}{suffix}.{ext}")

    for name in sorted(used):
        dst = out / "fonts" / font_id(name)
        dst.mkdir(parents=True)
        for r in RANGES:
            src = data / "assets" / "fonts" / name / f"{r}.pbf"
            if src.exists():
                shutil.copy(src, dst / f"{r}.pbf")
    shutil.copy(data / "assets" / "fonts" / "OFL.txt", out / "fonts" / "OFL.txt")
    shutil.copy(data / "tiles" / "santa-cruz.pmtiles", out / "santa-cruz.pmtiles")
    (out / "LICENSES.txt").write_text(
        "Map data: © OpenStreetMap contributors, ODbL 1.0 (https://www.openstreetmap.org/copyright).\n"
        "Tiles schema and styles: Protomaps basemaps (https://github.com/protomaps/basemaps).\n"
        "Fonts: Noto Sans, SIL Open Font License 1.1 (fonts/OFL.txt).\n"
        "Sprites: protomaps/basemaps-assets (https://github.com/protomaps/basemaps-assets).\n",
        encoding="utf-8",
    )
    size = sum(f.stat().st_size for f in out.rglob("*") if f.is_file())
    print(f"bundled map -> {out} ({size / 1e6:.1f} MB; fonts: {', '.join(sorted(font_id(f) for f in used))})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
