"""Isometric pre-renderer for Phantom Charm sprites.

Faces are planar parallelograms in model pixels (y up), textured with nearest sampling, shaded per
direction like the existing charm sprites (top 1.0, +z front 0.82, +x side 0.62), seen from (+x, +y, +z)
at a 30 degree pitch (2:1 dimetric, like the game's GUI items), then outlined 1 px in #120C1E.
"""
import math
from PIL import Image

OUTLINE = (18, 12, 30, 255)
SHADE = {'up': 1.0, 'down': 0.5, 'south': 0.82, 'north': 0.82, 'east': 0.62, 'west': 0.62}
C30, S30 = math.cos(math.radians(30)), math.sin(math.radians(30))
R2 = math.sqrt(0.5)
# The set's sprites are a touch squatter than a true 30 degree view.
VERT = 0.875


def project(p):
    x, y, z = p
    x1 = (x - z) * R2
    z1 = (x + z) * R2
    up = y * C30 * VERT - z1 * S30
    depth = z1 * C30 + y * S30
    return x1, up, depth


class Face:
    def __init__(self, verts, uvs, tex, shade=1.0, bright=False, tint=(1.0, 1.0, 1.0)):
        self.verts = verts  # 4 model points, rectangle; 0-1 and 0-3 adjacent
        self.uvs = uvs      # texture pixel coords per vertex
        self.tex = tex
        self.shade = 1.0 if bright else shade
        self.tint = tint


def cube(tex, u, v, origin, dims, offset=(0, 0, 0), faces=('up', 'down', 'north', 'south', 'east', 'west'),
         grow=0.0, shade=None, bright=False, tint=(1.0, 1.0, 1.0)):
    """Vanilla ModelPart.Cube: texOffs (u, v), addBox(origin, dims), part offset; returns Faces."""
    ox, oy, oz = offset
    x0, y0, z0 = origin[0] + ox - grow, origin[1] + oy - grow, origin[2] + oz - grow
    w, h, d = dims
    x1, y1, z1 = x0 + w + 2 * grow, y0 + h + 2 * grow, z0 + d + 2 * grow
    v7 = (x0, y0, z0); v0 = (x1, y0, z0); v1 = (x1, y1, z0); v2 = (x0, y1, z0)
    v3 = (x0, y0, z1); v4 = (x1, y0, z1); v5 = (x1, y1, z1); v6 = (x0, y1, z1)
    f4 = u; f5 = u + d; f6 = u + d + w; f7 = u + d + w + w; f8 = u + d + w + d; f9 = u + d + w + d + w
    f10 = v; f11 = v + d; f12 = v + d + h
    table = {
        'down': ([v4, v3, v7, v0], f5, f10, f6, f11),
        'up': ([v1, v2, v6, v5], f6, f11, f7, f10),
        'west': ([v7, v3, v6, v2], f4, f11, f5, f12),
        'north': ([v0, v7, v2, v1], f5, f11, f6, f12),
        'east': ([v4, v0, v1, v5], f6, f11, f8, f12),
        'south': ([v3, v4, v5, v6], f8, f11, f9, f12),
    }
    out = []
    for name in faces:
        verts, ua, va, ub, vb = table[name]
        uvs = [(ub, va), (ua, va), (ua, vb), (ub, vb)]
        out.append(Face(verts, uvs, tex, SHADE[name] if shade is None else shade, bright, tint))
    return out


def render(faces, size=64, fill=60, margin_center=None, scale=None):
    projected = []
    for f in faces:
        pts = [project(p) for p in f.verts]
        projected.append((f, pts))
    xs = [p[0] for _, pts in projected for p in pts]
    ups = [p[1] for _, pts in projected for p in pts]
    w = max(xs) - min(xs)
    h = max(ups) - min(ups)
    s = scale if scale else fill / max(w, h)
    cx = size / 2 - s * (max(xs) + min(xs)) / 2
    cy = size / 2 + s * (max(ups) + min(ups)) / 2
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    zbuf = [[-1e9] * size for _ in range(size)]
    for f, pts in projected:
        P = [(cx + s * a, cy - s * b, dz) for a, b, dz in pts]
        ax, ay, az = P[0]
        ex, ey, ez = P[1][0] - ax, P[1][1] - ay, P[1][2] - az
        fx, fy, fz = P[3][0] - ax, P[3][1] - ay, P[3][2] - az
        det = ex * fy - ey * fx
        if abs(det) < 1e-9:
            continue
        minx = int(max(0, math.floor(min(p[0] for p in P))))
        maxx = int(min(size - 1, math.ceil(max(p[0] for p in P))))
        miny = int(max(0, math.floor(min(p[1] for p in P))))
        maxy = int(min(size - 1, math.ceil(max(p[1] for p in P))))
        (u0, v0), (u1, v1), _, (u3, v3) = f.uvs
        tw, th = f.tex.size
        for py in range(miny, maxy + 1):
            for px in range(minx, maxx + 1):
                qx, qy = px + 0.5 - ax, py + 0.5 - ay
                a = (qx * fy - qy * fx) / det
                b = (ex * qy - ey * qx) / det
                if a < -1e-6 or a > 1 + 1e-6 or b < -1e-6 or b > 1 + 1e-6:
                    continue
                depth = az + a * ez + b * fz
                if depth <= zbuf[py][px]:
                    continue
                tu = u0 + a * (u1 - u0) + b * (u3 - u0)
                tv = v0 + a * (v1 - v0) + b * (v3 - v0)
                ix = min(tw - 1, max(0, int(math.floor(tu))))
                iy = min(th - 1, max(0, int(math.floor(tv))))
                r, g, bb, al = f.tex.getpixel((ix, iy))
                if al < 128:
                    continue
                k = f.shade
                col = (min(255, round(r * k * f.tint[0])), min(255, round(g * k * f.tint[1])),
                       min(255, round(bb * k * f.tint[2])), 255)
                zbuf[py][px] = depth
                img.putpixel((px, py), col)
    return img


def outline(img):
    size = img.size[0]
    out = img.copy()
    a = img.split()[3]
    for y in range(size):
        for x in range(size):
            if a.getpixel((x, y)):
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < size and 0 <= ny < size and a.getpixel((nx, ny)):
                    out.putpixel((x, y), OUTLINE)
                    break
    return out


def quad(tex, corners, uv_rect, shade=1.0, bright=False, tint=(1.0, 1.0, 1.0)):
    """A flat quad: corners in order (origin, along-u, opposite, along-v); uv_rect (u0, v0, u1, v1) in tex px."""
    u0, v0, u1, v1 = uv_rect
    return Face(corners, [(u0, v0), (u1, v0), (u1, v1), (u0, v1)], tex, shade, bright, tint)
