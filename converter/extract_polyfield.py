#!/usr/bin/env python3
"""Convert the user's own Polyfield (com.MA.Polyfield 0.7.5) install into
Minecraft resources for the `polyfield` Fabric mod.

Reads the game's Unity data offline with UnityPy and fsb5 and writes:
  assets/polyfield/pfmodels/*.pfm   static meshes (weapons, projectiles, vehicles)
  assets/polyfield/pfmodels/*.pfs   skinned soldiers (skeleton + weights)
  assets/polyfield/textures/pf/*.png
  assets/polyfield/sounds/**.ogg + sounds.json   (mono OGG Vorbis)

Nothing produced here may be committed or redistributed: it is converted from
the user's own copy of the game. Usage:

  python3 -I converter/extract_polyfield.py --input com.MA.Polyfield_0.7.5.apks \
      --out build/polyfield-assets [--preview build/polyfield-preview]
"""
import argparse
import io
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from unitylib import load_env, serialized_files, find_roots, walk, components  # noqa: E402
from static_models import extract_static  # noqa: E402
from skinned_models import extract_skinned  # noqa: E402
from weapon_stats import read_weapon  # noqa: E402

NS = "polyfield"
GUN_SCALE = 0.6  # Polyfield props are ~1.65x real size; this brings them to metres

# id -> (Prop prefab, Prefab with the Weapon/Gadget script)
WEAPONS = {
    "kar98k": ("Kar98k_Prop", "Kar98k_Prefab"),
    "m1_garand": ("M1_Garand_Prop", "M1_Garand_Prefab"),
    "mp40": ("MP40_Prop", "MP40_Prefab"),
    "thompson": ("ThompsonM1A1_Prop", "ThompsonM1A1_Prefab"),
    "stg44": ("STG44_Prop", "STG44_Prefab"),
    "sten": ("Sten_Prop", "Sten_Prefab"),
    "mg42": ("MG42_Prop", "MG42_Prefab"),
    "mg34": ("MG34_Prop", None),
    "bar": ("BAR_1918A2_Prop", "BAR_1918A2_Prefab"),
    "m3_grease_gun": ("M3GreaseGun_Prop", "M3GreaseGun_Prefab"),
    "vg15": ("VG15_Prop", "VG15_Prefab"),
    "walther_p38": ("WaltherP38_Prop", "WaltherP38_Prefab"),
    "m1915": ("M1915_Prop", "M1915_Prefab"),
    "welrod": ("Welrod_Prop", "Welrod_Prefab"),
    "panzerfaust": ("Panzerfaust_Prop", "Panzerfaust_Prefab"),
    "bazooka": ("Bazooka_M1A1_Prop", "Bazooka_M1A1_Prefab"),
}

SOLDIERS = {"soldier_ger": "PolySoldier_GER", "soldier_usa": "PolySoldier_USA"}

TANKS = {"sherman": "Tank_ShermanFirefly", "jagdpanther": "Tank_Jadgpanther"}

# sound event -> list of AudioClip names (variants)
COMMON_SOUNDS = {
    "impact.body": [f"Bullet_Impact_Body{i}" for i in range(1, 8)],
    "impact.concrete": ["Bullet_Impact_Concrete1", "Bullet_Impact_Concrete2"],
    "impact.dirt": ["Bullet_Impact_Dirt1", "Bullet_Impact_Dirt2", "Bullet_Impact_Dirt1_Short"],
    "impact.ricochet": [f"Bullet_Ricochet{i}" for i in (1, 2, 3, 4, 5, 6, 7, 8, 9, 10)],
    "impact.wood": ["wood-impact-small-debris-destruction-smash-068", "wood-impact-small-debris-destruction-smash-079",
                    "wood-impact-small-debris-destruction-smash-093"],
    "hitmarker": ["HitmarkerSFX"],
    "headshot": ["HeadshotSFX"],
    "explosion.close": ["Explosion_Close1", "Explosion_Close2", "Explosion_Blast0"],
    "explosion.debris": ["Explosion_debris0", "Explosion_debris1"],
    "explosion.vehicle": ["Explosion_Close_Long"],
    "soldier.death": [f"Death{i}" for i in range(1, 15)],
    "soldier.scream": [f"Scream{i}" for i in (1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14)],
    "soldier.wilhelm": ["Wilhelm_Scream"],
    "soldier.whistle": ["Trench_Whistle"],
    "soldier.step": [f"Footsteps_stones{i}" for i in range(1, 8)],
    "shell.drop": ["BULLET_SHELL_DROP_RIFLE_METAL_01", "BULLET_SHELL_PISTOL_DROP_METAL_01",
                   "Shell_65_Metal_Hard_Single_01", "Shell_65_Metal_Hard_Single_03", "bullet_shell_drop_cement0",
                   "bullet_shell_drop_cement1"],
    "weapon.switch": [f"WeaponSwtich{i}" for i in range(1, 6)],
    "weapon.rattle": [f"WeaponRattle{i}" for i in range(1, 6)],
    "tank.cannon": ["535550-Bluezone-BC0271-tank-artillery-cannon-shot-edited",
                    "535543-Bluezone-BC0271-tank-artillery-cannon-shot-003"],
    "tank.sherman.idle": ["Sherman_M4A3_Idle"],
    "tank.sherman.drive": ["Sherman_M4A3_Med"],
    "tank.panther.idle": ["Panther_Tank_Idle_Low_Amp"],
    "tank.panther.drive": ["Panther_Tank_Acceleration"],
    "tank.tracks": ["Panther_Tank_Tracks"],
    "tank.fire": ["Flame_Leak0", "Flame_Leak1", "Flame_Leak2"],
    "tank.repair": ["Ratchet_wrench_twist"],
    "grenade.bounce": ["Impact-Rock-Rubble-Hard-Short_0", "Impact-Rock-Rubble-Hard-Short_1"],
}

# sounds that should be heard far away (attenuation distance in blocks)
LOUD = {"shot": 96, "tail": 96, "explosion.close": 160, "explosion.debris": 96, "explosion.vehicle": 160,
        "tank.cannon": 160, "soldier.whistle": 64}


def safe(name):
    return re.sub(r"[^a-z0-9_]+", "_", name.lower()).strip("_")


# ---------------------------------------------------------------- input

def locate_data(inp, work):
    """Return the Unity `Data` directory from an .apks/.apk/folder."""
    if os.path.isdir(inp):
        for root, dirs, files in os.walk(inp):
            if "data.unity3d" in files:
                return root
        raise SystemExit("data.unity3d not found under " + inp)
    with zipfile.ZipFile(inp) as z:
        names = z.namelist()
        if "base.apk" in names:  # .apks bundle
            z.extract("base.apk", work)
            return locate_data(os.path.join(work, "base.apk"), work)
        out = os.path.join(work, "Data")
        os.makedirs(out, exist_ok=True)
        for n in names:
            if n.startswith("assets/bin/Data/") and not n.endswith("/"):
                rel = n[len("assets/bin/Data/"):]
                dst = os.path.join(out, rel)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                with z.open(n) as src, open(dst, "wb") as fh:
                    shutil.copyfileobj(src, fh)
        return out


# ---------------------------------------------------------------- writers

class Textures:
    def __init__(self, out):
        self.out = out
        self.done = {}

    def get(self, tex, max_size):
        if tex is None:
            return ""
        key = safe(tex.m_Name)
        if key not in self.done:
            img = tex.image.convert("RGBA")
            if max(img.size) > max_size:
                f = max_size / max(img.size)
                img = img.resize((max(1, int(img.size[0] * f)), max(1, int(img.size[1] * f))), Image.LANCZOS)
            path = os.path.join(self.out, "assets", NS, "textures", "pf", key + ".png")
            os.makedirs(os.path.dirname(path), exist_ok=True)
            img.save(path)
            self.done[key] = f"{NS}:textures/pf/{key}.png"
        return self.done[key]


def write_utf(fh, s):
    b = s.encode("utf8")
    fh.write(struct.pack(">H", len(b)))
    fh.write(b)


def write_pfm(path, parts, anchors, textures, max_tex=1024):
    """PFM1: static model. Big-endian; see PfModel.java."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    allv = np.concatenate([p["v"] for p in parts])
    lo, hi = allv.min(0), allv.max(0)
    with open(path, "wb") as fh:
        fh.write(b"PFM1")
        fh.write(struct.pack(">6f", *lo, *hi))
        fh.write(struct.pack(">i", len(anchors)))
        for name, pos in anchors.items():
            write_utf(fh, name)
            fh.write(struct.pack(">3f", *pos))
        fh.write(struct.pack(">i", len(parts)))
        for p in parts:
            write_utf(fh, p.get("texture_path") or textures.get(p.get("texobj"), max_tex))
            fh.write(struct.pack(">4f", *p["rgba"]))
            fh.write(struct.pack(">B", p.get("group", 0)))
            v, n, uv = p["v"], p["n"], p["uv"]
            fh.write(struct.pack(">i", len(v)))
            fh.write(np.c_[v, n, uv].astype(">f4").tobytes())
            fh.write(struct.pack(">i", len(p["tris"])))
            fh.write(np.asarray(p["tris"]).astype(">i4").tobytes())
    return lo, hi


def write_pfs(path, bones, parts, textures, max_tex=512):
    """PFS1: skinned model. Big-endian; see PfSkinnedModel.java."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(b"PFS1")
        fh.write(struct.pack(">i", len(bones)))
        for b in bones:
            write_utf(fh, b["name"].split(":")[-1])
            fh.write(struct.pack(">i", b["parent"]))
            fh.write(struct.pack(">3f", *b["pos"]))
            fh.write(struct.pack(">4f", *b["rot"]))
            fh.write(struct.pack(">3f", *b["scale"]))
        fh.write(struct.pack(">i", len(parts)))
        for p in parts:
            write_utf(fh, textures.get(p.get("texobj"), max_tex))
            fh.write(struct.pack(">4f", *p["rgba"]))
            v, n, uv = p["v"], p["n"], p["uv"]
            fh.write(struct.pack(">i", len(v)))
            fh.write(np.c_[v, n, uv].astype(">f4").tobytes())
            fh.write(np.asarray(p["bi"]).astype(">i1").tobytes())
            w = np.asarray(p["bw"], dtype=np.float64)
            s = w.sum(1, keepdims=True)
            w = np.divide(w, s, out=np.zeros_like(w), where=s > 0)
            fh.write(w.astype(">f4").tobytes())
            fh.write(struct.pack(">i", len(p["tris"])))
            fh.write(np.asarray(p["tris"]).astype(">i4").tobytes())
            for i in range(len(bones)):
                m = p["bind"].get(i, bones[i]["inv_bind"])
                fh.write(np.asarray(m, dtype=np.float64).astype(">f4").tobytes())  # row-major 4x4


# ---------------------------------------------------------------- models

def skip_common(path, go):
    n = go.m_Name
    return (n.startswith(("Collider", "Muzzle", "Interior", "Point Light")) or n in ("Gunner", "Screen")
            or "LOD1" in n or "LOD2" in n or "/_Colliders" in path or "/_Shadow" in path)


def transform_parts(parts, origin, scale):
    for p in parts:
        p["v"] = (p["v"] - origin) * scale
    return parts


def weapon_model(roots, prop):
    parts = extract_static(roots[prop], skip=lambda path, go: skip_common(path, go) or go.m_Name.startswith("_"))
    allv = np.concatenate([p["v"] for p in parts])
    trig = [p for p in parts if p["node"].split("/")[-1].lower().startswith("trigger")]
    if trig:
        origin = np.concatenate([p["v"] for p in trig]).mean(0)
    else:  # grip at the lower rear third
        lo, hi = allv.min(0), allv.max(0)
        origin = np.array([(lo[0] + hi[0]) / 2, lo[1] + (hi[1] - lo[1]) * 0.6, lo[2] + (hi[2] - lo[2]) * 0.35])
    if not trig:
        origin[0] = (allv[:, 0].min() + allv[:, 0].max()) / 2
    transform_parts(parts, origin, GUN_SCALE)
    allv = np.concatenate([p["v"] for p in parts])
    zmax = allv[:, 2].max()
    tip = allv[allv[:, 2] > zmax - 0.03]
    muzzle = (float(tip[:, 0].mean()), float(tip[:, 1].mean()), float(zmax))
    # line of sight: just over the receiver (between grip and 70 % of the barrel)
    rec = allv[(allv[:, 2] > -0.05) & (allv[:, 2] < zmax * 0.7)]
    sight_y = max(muzzle[1] + 0.035, (rec[:, 1].max() if len(rec) else muzzle[1]) + 0.012)
    anchors = {
        "grip": (0.0, 0.0, 0.0),
        "muzzle": muzzle,
        "sight": (0.0, float(sight_y), 0.0),
        "stock": (0.0, 0.0, float(allv[:, 2].min())),
    }
    return parts, anchors


def node_world(root_t, name, rel=True):
    import numpy as np
    from unitylib import trs
    base = np.linalg.inv(trs(root_t)) if rel else np.eye(4)
    for t, go, m, path in walk(root_t, base, active_only=False):
        if go.m_Name == name:
            p = m[:3, 3].copy()
            p[0] *= -1
            return p
    return None


def tank_model(root_t):
    def group_of(path):
        if "/_Cannon/" in path or path.endswith("/Turret_Cannon") or "/Turret_Cannon/" in path:
            return 2
        if "/_Turret/" in path:
            return 1
        return 0

    from unitylib import trs
    parts = extract_static(root_t, skip=lambda path, go: skip_common(path, go) or "/_Seats" in path)
    # extract_static drops the root placement; anchors must use the same frame
    rp = root_t.m_LocalPosition
    r = root_t.m_LocalRotation
    for p in parts:
        p["group"] = group_of(p["node"])
    turret = node_world(root_t, "_Turret")
    gun = node_world(root_t, "_Cannon")
    if gun is None:
        gun = turret
    muzzle = node_world(root_t, "_Cannon_Explosion")
    if muzzle is None:
        muzzle = node_world(root_t, "Sound_Cannon")
    gunv = [p["v"] for p in parts if p["group"] == 2]
    if gunv:
        gv = np.concatenate(gunv)
        zmax = gv[:, 2].max()
        tip = gv[gv[:, 2] > zmax - 0.05]
        muzzle = np.array([tip[:, 0].mean(), tip[:, 1].mean(), zmax])
    anchors = {"turret": tuple(turret), "gun": tuple(gun), "muzzle": tuple(muzzle)}
    for seat in ("_Seats_Driver", "_Seats_Gunner", "_Seats_Passenger", "_Seats_Passanger"):
        p = node_world(root_t, seat)
        if p is not None:
            anchors[seat.lower().strip("_")] = tuple(p)
    mg = node_world(root_t, "_MachineGun")
    if mg is not None:
        anchors["mg"] = tuple(mg)
    return parts, anchors


# ---------------------------------------------------------------- sounds

def audio_bytes(data_dir, clip):
    r = clip.m_Resource
    with open(os.path.join(data_dir, os.path.basename(r.m_Source)), "rb") as fh:
        fh.seek(r.m_Offset)
        return fh.read(r.m_Size)


def decode_clip(data_dir, clip, dst):
    import fsb5
    data = audio_bytes(data_dir, clip)
    f = fsb5.FSB5(data)
    ogg = f.rebuild_sample(f.samples[0])
    with tempfile.NamedTemporaryFile(suffix=".ogg", delete=False) as tmp:
        tmp.write(ogg)
        src = tmp.name
    try:
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        # Minecraft only positions/attenuates mono sounds
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", src, "-ac", "1", "-c:a", "libvorbis", "-q:a", "4", dst],
                       check=True)
    finally:
        os.unlink(src)


# ---------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--input", required=True, help=".apks, .apk or extracted folder of com.MA.Polyfield")
    ap.add_argument("--out", required=True, help="resources root to write (e.g. build/polyfield-assets)")
    ap.add_argument("--preview", help="write preview PNGs of every converted model here")
    args = ap.parse_args()

    work = tempfile.mkdtemp(prefix="pfconv_")
    try:
        data_dir = locate_data(args.input, work)
        print("Unity data:", data_dir)
        env = load_env(data_dir)
        sfs = serialized_files(env)
        shared = find_roots(sfs["sharedassets1.assets"])
        res = find_roots(sfs["resources.assets"])
        out = args.out
        mdir = os.path.join(out, "assets", NS, "pfmodels")
        os.makedirs(mdir, exist_ok=True)
        tex = Textures(out)
        report = {"weapons": {}, "models": {}, "sounds": {}}
        previews = []

        def preview(name, parts, yaw=-60.0):
            if not args.preview:
                return
            from preview import render
            for p in parts:
                p["tex"] = p["texobj"].image if p.get("texobj") is not None else None
            previews.append((name, render(parts, 256, yaw=yaw, pitch=20)))

        # weapons
        for wid, (prop, prefab) in WEAPONS.items():
            parts, anchors = weapon_model(shared, prop)
            lo, hi = write_pfm(os.path.join(mdir, wid + ".pfm"), parts, anchors, tex)
            report["models"][wid] = {"bounds": [lo.round(3).tolist(), hi.round(3).tolist()],
                                     "anchors": {k: np.round(v, 3).tolist() for k, v in anchors.items()}}
            if prefab:
                go = shared[prefab].m_GameObject.read()
                for c in go.m_Component:
                    o = c.component.deref()
                    if o.type.name == "MonoBehaviour":
                        try:
                            report["weapons"][wid] = read_weapon(o)
                        except Exception as e:  # Gadget layout differs; sounds still decode
                            report["weapons"][wid] = {"error": str(e)}
                        break
            preview(wid, parts)
            print("weapon", wid, len(parts), "parts")

        # projectiles
        def single_node(root, node_name, out_name, scale=GUN_SCALE):
            parts = extract_static(root, skip=lambda path, go: skip_common(path, go), active_only=False)
            parts = [p for p in parts if p["node"].split("/")[-1] == node_name]
            allv = np.concatenate([p["v"] for p in parts])
            c = (allv.min(0) + allv.max(0)) / 2
            transform_parts(parts, c, scale)
            write_pfm(os.path.join(mdir, out_name + ".pfm"), parts, {}, tex)
            preview(out_name, parts)

        single_node(shared["Bazooka_M1A1_Prop"], "Rocket", "rocket")
        single_node(shared["Panzerfaust_Prop"], "Panzerfaust Warhead", "panzerfaust_warhead")
        for gid, root in (("grenade_m24", "Grenade_M24"), ("grenade_mk2", "Grenade_MK2")):
            parts = extract_static(shared[root], skip=skip_common)
            allv = np.concatenate([p["v"] for p in parts])
            c = (allv.min(0) + allv.max(0)) / 2
            ext = (allv.max(0) - allv.min(0)).max()
            # real sizes: M24 ~0.36 m long, Mk2 ~0.11 m
            target = 0.36 if gid == "grenade_m24" else 0.12
            transform_parts(parts, c, target / ext)
            write_pfm(os.path.join(mdir, gid + ".pfm"), parts, {}, tex)
            preview(gid, parts)

        # soldiers
        for sid, root in SOLDIERS.items():
            bones, parts = extract_skinned(shared[root])
            write_pfs(os.path.join(mdir, sid + ".pfs"), bones, parts, tex)
            report["models"][sid] = {"bones": [b["name"] for b in bones]}
            print("soldier", sid, len(bones), "bones", len(parts), "parts")
            if args.preview:
                from skinned_models import pose_world, skin
                preview(sid, skin(parts, bones, pose_world(bones)), yaw=30)

        # vehicles
        for vid, root in TANKS.items():
            parts, anchors = tank_model(shared[root])
            lo, hi = write_pfm(os.path.join(mdir, vid + ".pfm"), parts, anchors, tex)
            report["models"][vid] = {"bounds": [lo.round(3).tolist(), hi.round(3).tolist()],
                                     "anchors": {k: np.round(v, 3).tolist() for k, v in anchors.items()}}
            preview(vid, parts)
            print("tank", vid, len(parts), "parts", anchors)
        parts = extract_static(res["Boat_Higgins_A"], skip=skip_common)
        # the ramp (bow) points to -X; turn it to +Z and bring it to ~11 m
        rot = np.array([[0, 0, 1], [0, 1, 0], [-1, 0, 0]], dtype=np.float64)
        for p in parts:
            p["v"] = (p["v"] @ rot.T) * 0.75
            p["n"] = p["n"] @ rot.T
        lo, hi = write_pfm(os.path.join(mdir, "higgins_boat.pfm"), parts, {}, tex)
        report["models"]["higgins_boat"] = {"bounds": [lo.round(3).tolist(), hi.round(3).tolist()]}
        preview("higgins_boat", parts)

        # the Normandy map (terrain + objects) and its collision data
        import map_normandy
        mparts, manchors = map_normandy.build(env, sfs, tex, out)
        lo, hi = write_pfm(os.path.join(mdir, "map_normandy.pfm"), mparts, manchors, tex, max_tex=512)
        report["models"]["map_normandy"] = {"bounds": [lo.round(1).tolist(), hi.round(1).tolist()],
                                            "anchors": {k: np.round(v, 1).tolist() for k, v in manchors.items()}}
        if args.preview:
            from preview import render
            for p in mparts:
                p["tex"] = None
            previews.append(("map_normandy", render(mparts, 768, yaw=0, pitch=60)))

        # sounds
        clips = {}
        for obj in env.objects:
            if obj.type.name == "AudioClip":
                c = obj.read()
                clips.setdefault(c.m_Name, c)
        events = {}

        def add_event(event, names, category):
            files = []
            for n in names:
                if n not in clips:
                    print("  missing clip", n)
                    continue
                rel = f"{category}/{safe(n)}"
                dst = os.path.join(out, "assets", NS, "sounds", rel + ".ogg")
                if not os.path.exists(dst):
                    decode_clip(data_dir, clips[n], dst)
                files.append(rel)
            if files:
                key = event.split(".")[-1]
                dist = LOUD.get(event, LOUD.get(key))
                ent = []
                for f in files:
                    e = {"name": f"{NS}:{f}"}
                    if dist:
                        e["attenuation_distance"] = dist
                    ent.append(e)
                events[event] = {"sounds": ent, "subtitle": f"subtitles.{NS}.{event}"}

        for wid, stats in report["weapons"].items():
            if "error" in stats:
                continue
            add_event(f"weapon.{wid}.shot", [stats["weaponSoundClose"]], "weapon")
            if stats.get("weaponSoundTail"):
                add_event(f"weapon.{wid}.tail", [stats["weaponSoundTail"]], "weapon")
            if stats.get("reloadSound"):
                add_event(f"weapon.{wid}.reload", [stats["reloadSound"]], "weapon")
            if stats.get("boltActionSound"):
                add_event(f"weapon.{wid}.bolt", [stats["boltActionSound"]], "weapon")
        # weapons whose script did not decode: map by hand from the clip names
        manual = {
            "mg34": {"shot": ["MG34_Burst_Loop_Low"], "tail": ["MG34_Burst_Tail_Bounce"], "reload": ["MG42_Reload"]},
            "bazooka": {"shot": ["Bazooka_Close_Tail"], "reload": ["Bazooka_Reload_New"]},
            "panzerfaust": {"shot": ["Panzerfaust_Close_Tail"], "reload": ["Panzerfaust_Reload"]},
        }
        for wid, m in manual.items():
            for kind, names in m.items():
                if f"weapon.{wid}.{kind}" not in events:
                    add_event(f"weapon.{wid}.{kind}", names, "weapon")
        add_event("weapon.m1_garand.ping", ["M1_Grand_Ping"], "weapon")
        add_event("weapon.kar98k.bolt", ["Kar98k_Bolt_Action"], "weapon")
        for ev, names in COMMON_SOUNDS.items():
            add_event(ev, names, ev.split(".")[0])
        with open(os.path.join(out, "assets", NS, "sounds.json"), "w") as fh:
            json.dump(events, fh, indent=1, sort_keys=True)
        report["sounds"] = sorted(events)
        print(len(events), "sound events")

        with open(os.path.join(out, "polyfield_report.json"), "w") as fh:
            json.dump(report, fh, indent=1, default=lambda o: o.tolist() if hasattr(o, "tolist") else str(o))

        if args.preview and previews:
            from preview import sheet
            from PIL import ImageDraw
            os.makedirs(args.preview, exist_ok=True)
            imgs = []
            for name, im in previews:
                ImageDraw.Draw(im).text((4, 4), name, fill=(255, 255, 0, 255))
                imgs.append(im)
            sheet(imgs, 6).save(os.path.join(args.preview, "models.png"))
        print("done ->", out)
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
