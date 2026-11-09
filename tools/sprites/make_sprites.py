"""Charm sprites in the set's style: 64x64 pre-renders as the game draws them (see iso.py).

    python3 tools/sprites/make_sprites.py [out_dir]

Writes phantom_charm (chest), phantom_charm_saddle, phantom_charm_gateway and phantom_charm_bell to
out_dir (default build/sprites). Copy what you want into src/main/resources/.../textures/item. The tank,
anchor and collector sprites came from an earlier session's renderer; void_sprites.py keeps the
anchor's and collector's void in step with the End portal shader. The committed gateway sprite's stars
came from an earlier starfield.py; its frame and shape match this output.
"""
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from iso import SHADE, cube, outline, quad, render  # noqa: E402
from starfield import starfield  # noqa: E402
from vanilla import ROOT, mod_texture, texture  # noqa: E402


def chest_faces():
    # The chest texture is 1024 px (16x a vanilla 64 px one); reduce it to two texels per model pixel.
    tex = mod_texture('entity/phantom_chest.png').resize((128, 128), resample=4)  # BOX
    faces = []
    faces += cube(tex, 0, 19, (1, 0, 1), (14, 10, 14))
    faces += cube(tex, 0, 0, (1, 0, 0), (14, 5, 14), offset=(0, 9, 1), grow=0.02)
    faces += cube(tex, 0, 0, (7, -2, 14), (2, 4, 1), offset=(0, 9, 1))
    for f in faces:
        f.verts = [(x - 8, y, z - 8) for x, y, z in f.verts]
        f.uvs = [(u * 2, v * 2) for u, v in f.uvs]
    return faces


def saddled_faces():
    """The chest wearing the pig's saddle, as PhantomChestRenderer draws it while ridden."""
    saddle = texture('entity/pig/pig_saddle.png')
    faces = chest_faces()
    top, strap, x = 14.3, 14.15, 7.15
    faces.append(quad(saddle, [(-5.5, top, 5.5), (5.5, top, 5.5), (5.5, top, -5.5), (-5.5, top, -5.5)], (53, 30, 64, 19)))
    faces.append(quad(saddle, [(5.5, strap, -1.5), (x, strap, -1.5), (x, strap, 1.5), (5.5, strap, 1.5)], (29, 23, 31, 26)))
    faces.append(quad(saddle, [(x, strap, 1.5), (x, strap, -1.5), (x, 6, -1.5), (x, 6, 1.5)], (31, 26, 36, 23), shade=SHADE['east']))
    return faces


def gateway_faces():
    """Lodestone frame round the End portal's starfield, the phantom eyes in the void; turned toward us."""
    side = texture('block/lodestone_side.png')
    eyes = texture('entity/phantom_eyes.png')
    hw, h, fr, d = 12, 32, 2, 1

    def box(x0, y0, z0, x1, y1, z1):
        w, hh, dd = x1 - x0, y1 - y0, z1 - z0
        return [
            quad(side, [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)], (0, 0, min(w, 16), min(hh, 16)), SHADE['south']),
            quad(side, [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)], (0, 0, min(dd, 16), min(hh, 16)), SHADE['east']),
            quad(side, [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], (0, 0, min(w, 16), min(dd, 16)), SHADE['up']),
        ]

    inner = hw - fr
    faces = []
    for y in (0, h / 2):
        faces += box(-hw, y, -d, -inner, y + h / 2, d)
        faces += box(inner, y, -d, hw, y + h / 2, d)
    for x0, x1 in ((-inner, 0), (0, inner)):
        faces += box(x0, 0, -d, x1, fr, d)
        faces += box(x0, h - fr, -d, x1, h, d)
    stars = starfield(48, 96, 0.3, 0.6).convert('RGBA')
    faces.append(quad(stars, [(-inner, h - fr, 0), (inner, h - fr, 0), (inner, fr, 0), (-inner, fr, 0)], (0, 0, 48, 96), bright=True))
    top = h - fr - 2
    faces.append(quad(eyes, [(-5.25, top, 0.05), (5.25, top, 0.05), (5.25, top - 4.5, 0.05), (-5.25, top - 4.5, 0.05)], (5, 5, 12, 8), bright=True))
    a = math.radians(25)
    for f in faces:
        f.verts = [(x * math.cos(a) + z * math.sin(a), y, -x * math.sin(a) + z * math.cos(a)) for x, y, z in f.verts]
    return faces


# The bell's windows are drawn in this colour, then painted with the starfield in screen space (paint_void).
VOID = (255, 0, 255, 255)
# The anchor sprite's eye colours: yellow-lime at the outer ends, green toward the middle.
EYE_COLOURS = [(240, 255, 104), (221, 255, 85), (197, 255, 68), (158, 255, 115), (125, 255, 125), (113, 255, 114)]
# The bell's gold, deepened toward the chest's brass so it sits with the rest of the set.
BRASS = (0.92, 0.76, 0.52)


def eye_bars():
    """The anchor's two eye bars as a 16 x 2 texture: 6 texels each, mirrored, a 4-texel gap."""
    img = Image.new('RGBA', (16, 2), (0, 0, 0, 0))
    for i, c in enumerate(EYE_COLOURS):
        for y in range(2):
            img.putpixel((i, y), c + (255,))
            img.putpixel((15 - i, y), c + (255,))
    return img


def bell_faces():
    """The vanilla bell's shape (BellRenderer's body and lip) in brass, each side of the body a window onto
    the End portal's starfield in a 1 px frame, like the anchor's, the phantom eyes across the front one."""
    bell = texture('entity/bell/bell_body.png')
    void = Image.new('RGBA', (4, 4), VOID)
    faces = (cube(bell, 0, 0, (-3, -6, -3), (6, 7, 6), offset=(8, 12, 8), tint=BRASS)
             + cube(bell, 0, 13, (4, 4, 4), (8, 2, 8), tint=BRASS))
    for f in faces:
        f.verts = [(x - 8, y, z - 8) for x, y, z in f.verts]
    w, y0, y1 = 2, 7, 12
    faces.append(quad(void, [(-w, y1, 3.02), (w, y1, 3.02), (w, y0, 3.02), (-w, y0, 3.02)], (0, 0, 4, 4), bright=True))
    faces.append(quad(void, [(3.02, y1, w), (3.02, y1, -w), (3.02, y0, -w), (3.02, y0, w)], (0, 0, 4, 4), bright=True))
    faces.append(quad(eye_bars(), [(-w, 11.6, 3.05), (w, 11.6, 3.05), (w, 10.5, 3.05), (-w, 10.5, 3.05)], (0, 0, 16, 2), bright=True))
    return faces


def paint_void(img, ox):
    """Fills the VOID pixels with the starfield laid over the whole sprite, as the game draws the void (screen-space)."""
    stars = starfield(64, 64, 0.35, 0.35, ox=ox).load()
    px = img.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            if px[x, y] == VOID:
                px[x, y] = stars[x, y] + (255,)
    return img


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'build', 'sprites')
    os.makedirs(out, exist_ok=True)
    for name, faces, fill in (('phantom_charm', chest_faces(), 58), ('phantom_charm_saddle', saddled_faces(), 58),
                              ('phantom_charm_gateway', gateway_faces(), 60), ('phantom_charm_bell', bell_faces(), 58)):
        paint_void(outline(render(faces, fill=fill)), 0.7).save(os.path.join(out, name + '.png'))
        print('wrote', os.path.join(out, name + '.png'))
