"""Helpers to read Polyfield's Unity data with UnityPy: hierarchy walking,
mesh decoding and Unity -> Minecraft coordinate conversion.

Unity is left-handed (x right, y up, z forward); Minecraft is right-handed.
We convert by negating X (a change of basis, not a mirror of the object),
negating the X component of normals, flipping V and reversing the triangle
winding (Unity front faces are clockwise, OpenGL's are counter-clockwise).
"""
import os
import numpy as np
import UnityPy
from UnityPy.helpers.MeshHelper import MeshHandler


def load_env(data_dir):
    env = UnityPy.Environment(path="")
    env.load_file(os.path.join(data_dir, "data.unity3d"))
    dr = os.path.join(data_dir, "unity default resources")
    if os.path.exists(dr):
        env.load_file(dr)
    return env


def serialized_files(env):
    out = {}
    for f in env.files.values():
        for name, sf in getattr(f, "files", {}).items():
            if hasattr(sf, "objects"):
                out[name] = sf
    return out


def find_roots(sf):
    roots = {}
    for obj in sf.objects.values():
        if obj.type.name == "Transform":
            t = obj.read()
            if t.m_Father.path_id == 0:
                roots[t.m_GameObject.read().m_Name] = t
    return roots


def quat_to_mat3(q):
    x, y, z, w = q
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ], dtype=np.float64)


def trs(t):
    p, r, s = t.m_LocalPosition, t.m_LocalRotation, t.m_LocalScale
    m = np.eye(4)
    m[:3, :3] = quat_to_mat3((r.x, r.y, r.z, r.w)) @ np.diag([s.x, s.y, s.z])
    m[:3, 3] = [p.x, p.y, p.z]
    return m


def components(go):
    out = []
    for c in go.m_Component:
        try:
            out.append(c.component.deref())
        except Exception:
            pass
    return out


def walk(t, parent=np.eye(4), active_only=True, path=""):
    """Yield (transform, gameobject, world_matrix, path) depth first."""
    go = t.m_GameObject.read()
    if active_only and not go.m_IsActive:
        return
    m = parent @ trs(t)
    p = (path + "/" + go.m_Name) if path else go.m_Name
    yield t, go, m, p
    for ch in t.m_Children:
        yield from walk(ch.read(), m, active_only, p)


def find_child(t, name):
    for tt, go, m, p in walk(t, active_only=False):
        if go.m_Name == name:
            return tt
    return None


def mesh_arrays(mesh):
    h = MeshHandler(mesh)
    h.process()
    v = np.array(h.m_Vertices, dtype=np.float64).reshape(-1, 3)
    n = np.array(h.m_Normals, dtype=np.float64).reshape(-1, 3) if h.m_Normals else np.zeros_like(v)
    uv = np.array(h.m_UV0, dtype=np.float64).reshape(-1, 2) if h.m_UV0 else np.zeros((len(v), 2))
    subs = [np.array(s, dtype=np.int64).reshape(-1, 3) for s in h.get_triangles()]
    bw = _per_vertex4(h.m_BoneWeights, len(v), np.float64)
    bi = _per_vertex4(h.m_BoneIndices, len(v), np.int64)
    if bi is not None and bw is None:
        bw = np.zeros((len(v), 4)); bw[:, 0] = 1.0
    colors = np.array(h.m_Colors, dtype=np.float64).reshape(len(v), -1) if h.m_Colors else None
    return dict(v=v, n=n, uv=uv, subs=subs, bw=bw, bi=bi, colors=colors)


def _per_vertex4(data, n, dtype):
    """Bone weights/indices may have 1..4 components per vertex; pad to 4."""
    if not data:
        return None
    a = np.array(data, dtype=dtype).reshape(n, -1)
    if a.shape[1] < 4:
        a = np.c_[a, np.zeros((n, 4 - a.shape[1]), dtype=dtype)]
    if dtype == np.float64 and a.shape[1] == 4:
        # single-bone channels carry no weight: weight 1 on the first bone
        if np.allclose(a[:, 1:], 0) and np.allclose(a[:, 0], 0):
            a[:, 0] = 1.0
    return a[:, :4]


def material_info(mat_ptr):
    """(name, rgba, main texture object or None)"""
    if not mat_ptr or mat_ptr.path_id == 0:
        return ("none", (1, 1, 1, 1), None)
    mat = mat_ptr.read()
    col = (1.0, 1.0, 1.0, 1.0)
    tex = None
    for k, c in mat.m_SavedProperties.m_Colors:
        if k == "_Color":
            col = (c.r, c.g, c.b, c.a)
    for k, te in mat.m_SavedProperties.m_TexEnvs:
        if k == "_MainTex" and te.m_Texture.path_id:
            tex = te.m_Texture.read()
    return (mat.m_Name, col, tex)


def to_mc_points(p):
    p = np.array(p, dtype=np.float64).copy()
    p[..., 0] *= -1
    return p


def to_mc_tris(tris):
    return tris[:, [0, 2, 1]]
