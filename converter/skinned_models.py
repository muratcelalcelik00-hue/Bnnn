"""Extract Polyfield's skinned soldier (Mixamo rig) with its skeleton.

Output keeps the rest pose of every bone (local TRS in Minecraft space) and
the inverse bind matrices, so the mod can pose bones procedurally and skin on
the CPU.
"""
import numpy as np
from unitylib import walk, components, mesh_arrays, material_info, trs, quat_to_mat3

FLIP = np.diag([-1.0, 1.0, 1.0, 1.0])


def mat_unity_to_mc(m):
    return FLIP @ m @ FLIP


def extract_skinned(root_t):
    nodes = {}
    for t, go, m, path in walk(root_t, np.eye(4), active_only=False):
        nodes[t.object_reader.path_id] = (t, go, m, path)
    renderers = []
    for t, go, m, path in walk(root_t, np.eye(4)):
        for c in components(go):
            if c.type.name == "SkinnedMeshRenderer":
                renderers.append((go, c.read()))
    # unified bone list (by transform path id), ordered parent-first
    bone_ids = []
    for go, r in renderers:
        for b in r.m_Bones:
            if b.path_id not in bone_ids:
                bone_ids.append(b.path_id)
    # include ancestors up to (not including) the root so the chain is complete
    def parent_id(pid):
        t = nodes[pid][0]
        return t.m_Father.path_id
    root_id = root_t.object_reader.path_id
    changed = True
    while changed:
        changed = False
        for pid in list(bone_ids):
            par = parent_id(pid)
            if par and par != root_id and par not in bone_ids:
                bone_ids.append(par)
                changed = True
    depth = {}
    def d(pid):
        if pid in depth:
            return depth[pid]
        par = parent_id(pid)
        depth[pid] = 0 if (not par or par == root_id or par not in bone_ids) else d(par) + 1
        return depth[pid]
    bone_ids.sort(key=lambda p: (d(p), nodes[p][3]))
    index = {pid: i for i, pid in enumerate(bone_ids)}
    bones = []
    for pid in bone_ids:
        t, go, m, path = nodes[pid]
        par = parent_id(pid)
        pr = t.m_LocalPosition, t.m_LocalRotation, t.m_LocalScale
        bones.append(dict(
            name=go.m_Name,
            parent=index.get(par, -1),
            pos=(-pr[0].x, pr[0].y, pr[0].z),
            rot=(pr[1].x, -pr[1].y, -pr[1].z, pr[1].w),
            scale=(pr[2].x, pr[2].y, pr[2].z),
            world=mat_unity_to_mc(m),
        ))
    # bones whose parent is not a bone hang off the root through intermediate nodes
    # (e.g. Armature): bake that offset into their rest transform
    for i, pid in enumerate(bone_ids):
        if bones[i]["parent"] == -1:
            t, go, m, path = nodes[pid]
            mm = mat_unity_to_mc(m)
            bones[i]["pos"] = tuple(mm[:3, 3])
            sc = np.linalg.norm(mm[:3, :3], axis=0)
            rm = mm[:3, :3] / sc
            bones[i]["rot"] = tuple(mat3_to_quat(rm))
            bones[i]["scale"] = tuple(sc)
    parts = []
    for go, r in renderers:
        if not r.m_Mesh.path_id:
            continue
        mesh = r.m_Mesh.read()
        a = mesh_arrays(mesh)
        bind = [np.array(bp.e if hasattr(bp, 'e') else _m44(bp)).reshape(4, 4) for bp in mesh.m_BindPose]
        local_bones = [index[b.path_id] for b in r.m_Bones]
        mats = r.m_Materials
        bi = np.array(local_bones)[a["bi"]] if a["bi"] is not None else None
        for si, tris in enumerate(a["subs"]):
            if len(tris) == 0:
                continue
            mp = mats[si] if si < len(mats) else mats[-1]
            name, col, tex = material_info(mp)
            used = np.unique(tris)
            remap = -np.ones(len(a["v"]), dtype=np.int64)
            remap[used] = np.arange(len(used))
            tt = remap[tris][:, [0, 2, 1]]
            v = a["v"][used].copy(); v[:, 0] *= -1
            n = a["n"][used].copy(); n[:, 0] *= -1
            uv = a["uv"][used].copy(); uv[:, 1] = 1 - uv[:, 1]
            parts.append(dict(v=v, n=n, uv=uv, tris=tt, rgba=col, mat=name, texobj=tex,
                              node=go.m_Name, bi=bi[used], bw=a["bw"][used],
                              bind={local_bones[k]: mat_unity_to_mc(bind[k]) for k in range(len(bind))}))
    # one inverse-bind per global bone (they agree across renderers of the same rig)
    inv_bind = [None] * len(bones)
    for p in parts:
        for k, m in p["bind"].items():
            if inv_bind[k] is None:
                inv_bind[k] = m
    for i in range(len(bones)):
        if inv_bind[i] is None:
            inv_bind[i] = np.linalg.inv(bones[i]["world"])
        bones[i]["inv_bind"] = inv_bind[i]
    return bones, parts


def _m44(bp):
    return [[getattr(bp, f"e{r}{c}") for c in range(4)] for r in range(4)]


def mat3_to_quat(m):
    t = np.trace(m)
    if t > 0:
        s = np.sqrt(t + 1.0) * 2
        w = 0.25 * s; x = (m[2, 1] - m[1, 2]) / s; y = (m[0, 2] - m[2, 0]) / s; z = (m[1, 0] - m[0, 1]) / s
    elif m[0, 0] > m[1, 1] and m[0, 0] > m[2, 2]:
        s = np.sqrt(1.0 + m[0, 0] - m[1, 1] - m[2, 2]) * 2
        w = (m[2, 1] - m[1, 2]) / s; x = 0.25 * s; y = (m[0, 1] + m[1, 0]) / s; z = (m[0, 2] + m[2, 0]) / s
    elif m[1, 1] > m[2, 2]:
        s = np.sqrt(1.0 + m[1, 1] - m[0, 0] - m[2, 2]) * 2
        w = (m[0, 2] - m[2, 0]) / s; x = (m[0, 1] + m[1, 0]) / s; y = 0.25 * s; z = (m[1, 2] + m[2, 1]) / s
    else:
        s = np.sqrt(1.0 + m[2, 2] - m[0, 0] - m[1, 1]) * 2
        w = (m[1, 0] - m[0, 1]) / s; x = (m[0, 2] + m[2, 0]) / s; y = (m[1, 2] + m[2, 1]) / s; z = 0.25 * s
    q = np.array([x, y, z, w])
    return q / np.linalg.norm(q)


def pose_world(bones, local_rot_override=None):
    """World matrices from rest local TRS (optionally replacing local rotations)."""
    out = []
    for i, b in enumerate(bones):
        q = b["rot"] if not local_rot_override or i not in local_rot_override else local_rot_override[i]
        m = np.eye(4)
        m[:3, :3] = quat_to_mat3(q) @ np.diag(b["scale"])
        m[:3, 3] = b["pos"]
        out.append(m if b["parent"] < 0 else out[b["parent"]] @ m)
    return out


def skin(parts, bones, world):
    skinned = []
    for p in parts:
        mats = np.stack([world[i] @ p["bind"].get(i, bones[i]["inv_bind"]) for i in range(len(bones))])
        vh = np.c_[p["v"], np.ones(len(p["v"]))]
        out = np.zeros((len(vh), 4))
        for k in range(4):
            mk = mats[p["bi"][:, k]]
            out += p["bw"][:, k:k + 1] * np.einsum("nij,nj->ni", mk, vh)
        q = dict(p)
        q["v"] = out[:, :3]
        skinned.append(q)
    return skinned
