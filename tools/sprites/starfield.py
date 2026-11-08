"""The End portal shader's starfield (rendertype_end_portal.fsh), emulated as an image for sprites."""
import math

from PIL import Image

from vanilla import texture

COLORS = [(0.022087, 0.098399, 0.110818), (0.011892, 0.095924, 0.089485), (0.027636, 0.101689, 0.100326),
          (0.046564, 0.109883, 0.114838), (0.064901, 0.117696, 0.097189), (0.063761, 0.086895, 0.123646),
          (0.084817, 0.111994, 0.166380), (0.097489, 0.154120, 0.091064), (0.106152, 0.131144, 0.195191),
          (0.097721, 0.110188, 0.187229), (0.133516, 0.138278, 0.148582), (0.070006, 0.243332, 0.235792),
          (0.196766, 0.142899, 0.214696), (0.047281, 0.315338, 0.321970), (0.204675, 0.390010, 0.302066),
          (0.080955, 0.314821, 0.661491)]


def starfield(w, h, sx, sy, layers=15, game_time=0.37, boost=1.8, ox=0.0, oy=0.0):
    """A w x h image of the starfield; (sx, sy) is how much of the screen it spans. boost brightens it
    so it reads at hotbar size."""
    portal = texture('entity/end_portal.png').convert('RGB')
    sky = texture('environment/end_sky.png').convert('RGB')
    pp, sp = portal.load(), sky.load()
    pw, ph = portal.size
    sw, sh = sky.size
    out = Image.new('RGB', (w, h))
    op = out.load()
    for y in range(h):
        for x in range(w):
            px, py = ox + x / w * sx, oy + y / h * sy
            c0 = sp[int(px * sw) % sw, int(py * sh) % sh]
            r, g, b = [c * k for c, k in zip(c0, COLORS[0])]
            for i in range(layers):
                layer = i + 1
                tx = px * 0.5 + 0.25 + 17.0 / layer
                ty = py * 0.5 + 0.25 + (2.0 + layer / 1.5) * (game_time * 1.5)
                a = math.radians((layer * layer * 4321.0 + layer * 9.0) * 2.0)
                scale = (4.5 - layer / 4.0) * 2.0
                ux = scale * (math.cos(a) * tx - math.sin(a) * ty)
                uy = scale * (math.sin(a) * tx + math.cos(a) * ty)
                c = pp[int((ux % 1) * pw) % pw, int((uy % 1) * ph) % ph]
                k = COLORS[i]
                r += c[0] * k[0]
                g += c[1] * k[1]
                b += c[2] * k[2]
            op[x, y] = (min(255, int(r * boost)), min(255, int(g * boost)), min(255, int(b * boost)))
    return out
