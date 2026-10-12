"""Read Polyfield's `Weapon` MonoBehaviour values without a typetree.

The game is IL2CPP and ships no typetrees. Field names/order come from
global-metadata.dat (class `Weapon`, 54 fields); the serialized layout is
decoded here: the fixed head (weaponClass .. bulletShellDelay) is read in
order, and the tail (forces .. sounds) is anchored on the run of AudioClip
pointers (reloadSound, weaponSoundSub, Close, Tail, Distant, boltActionSound).
"""
import os
import struct


def _resolve(o, file_id, path_id):
    sf = o.assets_file
    if file_id == 0:
        return sf.objects.get(path_id)
    if file_id < 0 or file_id > len(sf.externals):
        return None
    ext = str(getattr(sf.externals[file_id - 1], "path", "")).lower()
    for f in sf.environment.files.values():
        for n, s in getattr(f, "files", {}).items():
            if hasattr(s, "objects") and os.path.basename(ext) in str(n).lower():
                return s.objects.get(path_id)
    return None


def _pptr(b, off):
    return struct.unpack_from("<iq", b, off)


def read_weapon(mono_obj):
    raw = mono_obj.get_raw_data()
    nlen = struct.unpack_from("<i", raw, 28)[0]
    b = raw[(32 + nlen + 3) & ~3:]
    i32 = lambda o: struct.unpack_from("<i", b, o)[0]
    f32 = lambda o: struct.unpack_from("<f", b, o)[0]
    out = {"weaponClass": i32(0)}
    slen = i32(4)
    out["allowedClass"] = b[8:8 + slen].decode("utf8", "replace")
    p = (8 + slen + 3) & ~3
    for name, kind in [("model", "i"), ("fireMode", "i"), ("fireRate", "i"), ("bulletSpeed", "i"),
                       ("range", "i"), ("damage", "i"), ("magnification", "f"), ("aimFOV", "i"),
                       ("aimZoffset", "f"), ("boltAction", "i"), ("bulletShellDelay", "f")]:
        out[name] = i32(p) if kind == "i" else f32(p)
        p += 4
    def is_clip(off):
        fid, pid = _pptr(b, off)
        tgt = _resolve(mono_obj, fid, pid) if pid else None
        return tgt is not None and tgt.type.name == "AudioClip"

    # anchor: first run of AudioClip pointers starting with reloadSound
    for k in range(p, len(b) - 72, 4):
        if not is_clip(k):
            continue
        ok = 0
        for j in range(5):
            fid, pid = _pptr(b, k + 12 * j)
            tgt = _resolve(mono_obj, fid, pid) if pid else None
            if tgt is not None and tgt.type.name == "AudioClip":
                ok += 1
        if ok >= 4:
            names = ["reloadSound", "weaponSoundSub", "weaponSoundClose", "weaponSoundTail",
                     "weaponSoundDistant", "boltActionSound"]
            for j, n in enumerate(names):
                fid, pid = _pptr(b, k + 12 * j)
                tgt = _resolve(mono_obj, fid, pid) if pid else None
                out[n] = tgt.read().m_Name if tgt is not None and tgt.type.name == "AudioClip" else None
            q = k
            out["pitchOffset"] = f32(q - 4)
            out["hasSoundLoop"] = i32(q - 8)
            out["mag"] = i32(q - 12)
            out["ammo"] = i32(q - 16)
            out["CLIP_SIZE"] = i32(q - 20)
            out["MAG_SIZE"] = i32(q - 24)
            out["forces"] = [f32(q - 48), f32(q - 44), f32(q - 40)]
            break
    return out
