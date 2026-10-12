"""Polyfield's "Normandy" map (the one in the Map TextAsset) as Minecraft data.

Polyfield builds the map at runtime: a 250x250 HeightMap (3 m per pixel) and a
ColorMap splat for the ground, plus ~545 objects listed in the Map text as
`Name_N: x, y, z:rx, ry, rz:sx, sy, sz`. The heightmap orientation and scale
were fitted against those objects' heights (mean error 0.36 m):
    u = -x / 3 + 125,  v = z / 3 + 125,  y = 0.1092 * pixel - 0.97   (Unity space)

Writes:
  assets/polyfield/pfmodels/map_normandy.pfm   terrain + all objects, merged per texture
  assets/polyfield/textures/pf/map_terrain.png baked ground texture
  data/polyfield/map/normandy.pfc              collision data for the server
"""
import os
import re
import struct

import numpy as np
from PIL import Image

from unitylib import find_roots, quat_to_mat3
from static_models import extract_static

N = 250
CELL = 3.0
H_SCALE = 0.1092
H_OFF = -0.97
SEA = -0.3
SKIP_COLLIDE = ("Bush", "Path_Entities")


def euler_to_mat(rx, ry, rz):
    """Unity euler (degrees, applied Z then X then Y)."""
    def q(axis, deg):
        a = np.radians(deg) / 2
        v = np.zeros(4)
        v[axis] = np.sin(a)
        v[3] = np.cos(a)
        return quat_to_mat3(v)
    return q(1, ry) @ q(0, rx) @ q(2, rz)


def parse_map(text):
    objs, anchors = [], {}
    for line in text.replace("\r", "").split("\n"):
        m = re.match(r"([A-Za-z_]+?)(?:_(\d+))?: ([^:]+):([^:]+)(?::([^:]+))?", line)
        if not m:
            continue
        name = m.group(1)
        pos = [float(x) for x in m.group(3).split(",")]
        rot = [float(x) for x in m.group(4).split(",")]
        sc = [float(x) for x in m.group(5).split(",")] if m.group(5) else [1, 1, 1]
        if name.startswith(("Flag_", "SpawnPoint")):
            anchors[name] = pos
        else:
            objs.append((name, pos, rot, sc))
    return objs, anchors


def height_grid(hm):
    return np.asarray(hm.convert("L"), dtype=np.float64) * H_SCALE + H_OFF


def terrain_texture(colormap, tex_by_name, size=2048, tile_m=6.0):
    """Blend the ground textures by Polyfield's ColorMap splat."""
    cm = np.asarray(colormap.convert("RGB").resize((size, size), Image.BILINEAR), dtype=np.float64) / 255.0
    reps = (N * CELL) / tile_m

    def tiled(name):
        t = tex_by_name[name].image.convert("RGB").resize((256, 256), Image.LANCZOS)
        t = np.asarray(t, dtype=np.float64) / 255.0
        idx = (np.arange(size) * (256 * reps / size)).astype(int) % 256
        return t[idx][:, idx]

    grass, sand, gravel = tiled("Grass_dark"), tiled("Sand_Dry_Soil"), tiled("Ground_Gravel")
    r, g, b = cm[..., 0:1], cm[..., 1:2], cm[..., 2:3]
    dark = np.clip(1.0 - (r + g + b) * 1.5, 0, 1)
    w = r + g + b + dark + 1e-6
    out = (sand * r + grass * g + gravel * b + gravel * 0.75 * dark) / w
    # image columns run along +x and rows along +z in Minecraft space
    return Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))


def terrain_part(H):
    xs = (np.arange(N) - N / 2) * CELL
    zs = (np.arange(N) - N / 2) * CELL
    X, Z = np.meshgrid(xs, zs)  # MC space: x_mc = -x_unity = (u - 125) * 3
    v = np.stack([X, H, Z], -1).reshape(-1, 3)
    gy, gx = np.gradient(H, CELL)
    n = np.stack([-gx, np.ones_like(H), -gy], -1).reshape(-1, 3)
    n /= np.linalg.norm(n, axis=1, keepdims=True)
    uv = np.stack([(np.arange(N)[None, :].repeat(N, 0) + 0.5) / N, (np.arange(N)[:, None].repeat(N, 1) + 0.5) / N], -1).reshape(-1, 2)
    i = np.arange(N * N).reshape(N, N)
    a, b, c, d = i[:-1, :-1].ravel(), i[:-1, 1:].ravel(), i[1:, :-1].ravel(), i[1:, 1:].ravel()
    tris = np.concatenate([np.stack([a, c, b], 1), np.stack([b, c, d], 1)])
    return dict(v=v, n=n, uv=uv, tris=tris, rgba=(1, 1, 1, 1), texobj=None, mat="terrain")


def build(env, sfs, tex_store, out_dir):
    from unitylib import material_info  # noqa: F401
    res = find_roots(sfs["resources.assets"])
    shared2 = find_roots(sfs["sharedassets2.assets"])
    textures = {}
    text = None
    for obj in env.objects:
        if obj.type.name == "Texture2D":
            t = obj.read()
            textures.setdefault(t.m_Name, t)
        elif obj.type.name == "TextAsset":
            t = obj.read()
            if t.m_Name == "Map":
                text = t.m_Script if isinstance(t.m_Script, str) else t.m_Script.decode("utf8", "replace")
    objs, anchors = parse_map(text)
    H = height_grid(textures["HeightMap"].image)

    # ground texture (PNG written straight into the pack)
    tpath = os.path.join(out_dir, "assets", "polyfield", "textures", "pf", "map_terrain.png")
    os.makedirs(os.path.dirname(tpath), exist_ok=True)
    terrain_texture(textures["ColorMap"].image, textures).save(tpath)

    S = np.diag([-1.0, 1.0, 1.0])
    prefab_cache = {}
    merged = {}
    voxels = set()
    placed = 0
    for name, pos, rot, sc in objs:
        root = res.get(name) or shared2.get(name)
        if root is None:
            continue
        if name not in prefab_cache:
            prefab_cache[name] = extract_static(root, skip=lambda p, go: go.m_Name.startswith(("Collider", "_")) or "LOD1" in go.m_Name
                                                or "LOD2" in go.m_Name or "Destroyed" in go.m_Name)
        R = S @ euler_to_mat(*rot) @ np.diag(sc) @ S
        t = np.array([-pos[0], pos[1], pos[2]])
        for p in prefab_cache[name]:
            v = p["v"] @ R.T + t
            nrm = p["n"] @ np.linalg.inv(R).T
            nrm /= np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-9)
            tris = p["tris"] if np.linalg.det(R) > 0 else p["tris"][:, [0, 2, 1]]
            key = (p["texobj"].m_Name if p["texobj"] is not None else "", tuple(np.round(p["rgba"], 3)))
            m = merged.setdefault(key, dict(v=[], n=[], uv=[], tris=[], count=0, rgba=p["rgba"], texobj=p["texobj"], mat=p["mat"]))
            m["tris"].append(tris + m["count"])
            m["v"].append(v)
            m["n"].append(nrm)
            m["uv"].append(p["uv"])
            m["count"] += len(v)
            veg = any(k in (p["mat"] or "") for k in ("Branch", "Bush", "Grass", "Vegetation"))
            if not name.startswith(SKIP_COLLIDE) and not veg:
                tri = v[tris]
                for k in range(len(tri)):
                    a, b, c = tri[k]
                    steps = int(max(np.linalg.norm(b - a), np.linalg.norm(c - a)) / 0.4) + 1
                    for i in range(steps + 1):
                        for j in range(steps + 1 - i):
                            q = a + (b - a) * (i / steps) + (c - a) * (j / steps)
                            voxels.add((int(np.floor(q[0])), int(np.floor(q[1])), int(np.floor(q[2]))))
        placed += 1
    parts = [terrain_part(H)]
    parts[0]["texture_path"] = "polyfield:textures/pf/map_terrain.png"
    for m in merged.values():
        parts.append(dict(v=np.concatenate(m["v"]), n=np.concatenate(m["n"]), uv=np.concatenate(m["uv"]),
                          tris=np.concatenate(m["tris"]), rgba=m["rgba"], texobj=m["texobj"], mat=m["mat"]))
    mc_anchors = {k: (-v[0], v[1], v[2]) for k, v in anchors.items()}

    # server collision data
    dpath = os.path.join(out_dir, "data", "polyfield", "map", "normandy.pfc")
    os.makedirs(os.path.dirname(dpath), exist_ok=True)
    vox = np.array(sorted(voxels), dtype=np.int32).reshape(-1, 3)
    with open(dpath, "wb") as fh:
        fh.write(b"PFC1")
        fh.write(struct.pack(">ifff", N, CELL, SEA, 0.0))
        fh.write(H.astype(">f4").tobytes())
        fh.write(struct.pack(">i", len(mc_anchors)))
        for k, v in mc_anchors.items():
            b = k.encode()
            fh.write(struct.pack(">H", len(b)) + b + struct.pack(">3f", *v))
        fh.write(struct.pack(">i", len(vox)))
        fh.write(vox.astype(">i4").tobytes())
    print("map: %d objects placed, %d parts, %d collision voxels" % (placed, len(parts), len(vox)))
    return parts, mc_anchors
