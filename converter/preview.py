"""Tiny software rasteriser used to eyeball converted models (oracle images).

Parts are dicts with 'v' (N,3 Minecraft space), 'tris' (M,3), 'rgba' and an
optional 'tex' (PIL image) + 'uv' (N,2, Minecraft V-down convention).
"""
import numpy as np
from PIL import Image, ImageDraw


def _rot(yaw_deg, pitch_deg):
    y, p = np.radians(yaw_deg), np.radians(pitch_deg)
    ry = np.array([[np.cos(y), 0, np.sin(y)], [0, 1, 0], [-np.sin(y), 0, np.cos(y)]])
    rx = np.array([[1, 0, 0], [0, np.cos(p), -np.sin(p)], [0, np.sin(p), np.cos(p)]])
    return rx @ ry


def render(parts, size=512, yaw=0.0, pitch=20.0, bg=(70, 80, 90, 255), light=(0.4, 0.8, -0.45)):
    """Camera looks along -Z of view space; Minecraft right-handed: x right after rotation."""
    R = _rot(yaw, pitch)
    allv = np.concatenate([p["v"] for p in parts if len(p["v"])])
    c = (allv.min(0) + allv.max(0)) / 2
    vv = (allv - c) @ R.T
    ext = max(np.ptp(vv[:, 0]), np.ptp(vv[:, 1])) * 1.1 + 1e-6
    scale = size / ext
    L = np.array(light) / np.linalg.norm(light)
    polys = []
    for p in parts:
        v = (p["v"] - c) @ R.T
        tris = p["tris"]
        if len(tris) == 0:
            continue
        tex = p.get("tex")
        tw = th = 0
        if tex is not None:
            ta = np.asarray(tex.convert("RGBA"), dtype=np.float64)
            th, tw = ta.shape[:2]
        for t in tris:
            a, b, cc = v[t[0]], v[t[1]], v[t[2]]
            n = np.cross(b - a, cc - a)
            nl = np.linalg.norm(n)
            if nl == 0:
                continue
            n /= nl
            if n[2] < 0:  # back face (camera looks down -Z, towards viewer is +Z)
                continue
            shade = 0.45 + 0.55 * max(0.0, float(np.dot(n, L)))
            col = np.array(p["rgba"][:3], dtype=np.float64)
            if tex is not None:
                uv = p["uv"][t].mean(0)
                px = int(np.clip(uv[0], 0, 0.9999) * tw)
                py = int(np.clip(uv[1], 0, 0.9999) * th)
                col = col * ta[py, px, :3] / 255.0
            col = np.clip(col * shade * 255, 0, 255).astype(int)
            pts = [(size / 2 + q[0] * scale, size / 2 - q[1] * scale) for q in (a, b, cc)]
            polys.append(((a[2] + b[2] + cc[2]) / 3, pts, tuple(col) + (255,)))
    polys.sort(key=lambda x: x[0])
    img = Image.new("RGBA", (size, size), bg)
    d = ImageDraw.Draw(img)
    for _, pts, col in polys:
        d.polygon(pts, fill=col)
    return img


def sheet(images, cols=4):
    w, h = images[0].size
    rows = (len(images) + cols - 1) // cols
    out = Image.new("RGBA", (w * cols, h * rows), (40, 40, 40, 255))
    for i, im in enumerate(images):
        out.paste(im, ((i % cols) * w, (i // cols) * h))
    return out
