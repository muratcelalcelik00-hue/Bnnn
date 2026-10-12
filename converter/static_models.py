"""Extract static (non-skinned) prefab models as coloured/textured parts."""
import numpy as np
from unitylib import walk, components, mesh_arrays, material_info, to_mc_points, to_mc_tris, trs

SKIP_NAME_PREFIX = ("Collider", "_", "Muzzle", "LOD1", "LOD2")


def extract_static(root_t, skip=lambda path, go: False, include_skinned_static=True, rel_to_root=True,
                   active_only=True):
    """Return list of parts (Minecraft space, relative to root_t's own frame).

    Skinned renderers (tank tracks) are baked in the prefab pose.
    Each part: dict(v, n, uv, tris, rgba, mat, texobj, node)
    """
    parts = []
    if rel_to_root:
        # drop the root's placement but keep its scale (props are scaled at the root)
        s = root_t.m_LocalScale
        base = np.linalg.inv(trs(root_t)) @ np.diag([s.x, s.y, s.z, 1.0]) if False else None
        r = root_t.m_LocalRotation
        from unitylib import quat_to_mat3
        rm = np.eye(4)
        rm[:3, :3] = quat_to_mat3((r.x, r.y, r.z, r.w))
        p = root_t.m_LocalPosition
        tm = np.eye(4)
        tm[:3, 3] = [p.x, p.y, p.z]
        base = np.linalg.inv(tm @ rm)
    else:
        base = np.eye(4)
    worlds = {}
    for t, go, m, path in walk(root_t, base, active_only=False):
        worlds[t.object_reader.path_id] = m
    for t, go, m, path in walk(root_t, base, active_only=active_only):
        if skip(path, go):
            continue
        comps = components(go)
        mf = next((c for c in comps if c.type.name == "MeshFilter"), None)
        mr = next((c for c in comps if c.type.name == "MeshRenderer"), None)
        smr = next((c for c in comps if c.type.name == "SkinnedMeshRenderer"), None) if include_skinned_static else None
        if smr is not None:
            mrr = smr.read()
            if not mrr.m_Mesh.path_id or not mrr.m_Enabled:
                continue
            mesh = mrr.m_Mesh.read()
            a = mesh_arrays(mesh)
            binds = [np.array([[getattr(bp, f"e{r}{c}") for c in range(4)] for r in range(4)]) for bp in mesh.m_BindPose]
            mats = np.stack([worlds.get(b.path_id, m) @ binds[k] for k, b in enumerate(mrr.m_Bones)])
            vh = np.c_[a["v"], np.ones(len(a["v"]))]
            v = np.zeros((len(vh), 4))
            n = np.zeros((len(vh), 3))
            for k in range(4):
                mk = mats[a["bi"][:, k]]
                v += a["bw"][:, k:k + 1] * np.einsum("nij,nj->ni", mk, vh)
                n += a["bw"][:, k:k + 1] * np.einsum("nij,nj->ni", mk[:, :3, :3], a["n"])
            v = v[:, :3]
            m = mats[0]
        elif mf and mr:
            mfr, mrr = mf.read(), mr.read()
            if not mfr.m_Mesh.path_id or not mrr.m_Enabled:
                continue
            mesh = mfr.m_Mesh.read()
            a = mesh_arrays(mesh)
            v = (np.c_[a["v"], np.ones(len(a["v"]))] @ m.T)[:, :3]
            nm = np.linalg.inv(m[:3, :3]).T
            n = a["n"] @ nm.T
        else:
            continue
        nl = np.linalg.norm(n, axis=1, keepdims=True)
        n = np.divide(n, nl, out=np.zeros_like(n), where=nl > 0)
        mirrored = np.linalg.det(m[:3, :3]) < 0
        mats = mrr.m_Materials
        for si, tris in enumerate(a["subs"]):
            if len(tris) == 0:
                continue
            mp = mats[si] if si < len(mats) else (mats[-1] if mats else None)
            name, col, tex = material_info(mp)
            used = np.unique(tris)
            remap = -np.ones(len(v), dtype=np.int64)
            remap[used] = np.arange(len(used))
            tt = remap[tris]
            if mirrored:
                tt = tt[:, [0, 2, 1]]
            uv = a["uv"][used].copy()
            uv[:, 1] = 1.0 - uv[:, 1]
            parts.append(dict(
                v=to_mc_points(v[used]), n=to_mc_points(n[used]), uv=uv,
                tris=to_mc_tris(tt), rgba=col, mat=name, texobj=tex, node=path,
            ))
    return parts


def bounds(parts):
    allv = np.concatenate([p["v"] for p in parts])
    return allv.min(0), allv.max(0)
