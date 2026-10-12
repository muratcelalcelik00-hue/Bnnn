#!/usr/bin/env python3
"""Adds the converted Polyfield assets (from extract_polyfield.py) to an
already-built mod jar, producing a personal jar with models and sounds.

  python3 -I converter/pack_jar.py build/libs/polyfield-1.0.0.jar build/polyfield-assets out.jar
"""
import os
import shutil
import sys
import zipfile


def main(jar, assets, out):
    shutil.copyfile(jar, out)
    with zipfile.ZipFile(jar) as zin:
        existing = set(zin.namelist())
    added = 0
    with zipfile.ZipFile(out, "a", compression=zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(assets):
            for f in files:
                full = os.path.join(root, f)
                rel = os.path.relpath(full, assets).replace(os.sep, "/")
                if not rel.startswith(("assets/", "data/")) or rel in existing:
                    continue
                z.write(full, rel)
                added += 1
    print(f"{out}: added {added} asset files")


if __name__ == "__main__":
    main(*sys.argv[1:4])
