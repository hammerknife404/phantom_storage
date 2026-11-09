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
from iso import SHADE, cube, outline, quad, render, to_screen  # noqa: E402
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
# The bell's gold, deepened toward the chest's brass so it sits with the rest of the set.
BRASS = (0.92, 0.76, 0.52)
# Where the Eye of Ender looks out from: the body's front corner, halfway up its window.
BELL_EYE_AT = (3.02, 9.5, 3.02)


def bell_faces():
    """The vanilla bell's shape (BellRenderer's body and lip) in brass, with a window onto the End portal's
    starfield round its front corner in a 1 px frame (bell_sprite puts an Eye of Ender in it)."""
    bell = texture('entity/bell/bell_body.png')
    void = Image.new('RGBA', (4, 4), VOID)
    faces = (cube(bell, 0, 0, (-3, -6, -3), (6, 7, 6), offset=(8, 12, 8), tint=BRASS)
             + cube(bell, 0, 13, (4, 4, 4), (8, 2, 8), tint=BRASS))
    for f in faces:
        f.verts = [(x - 8, y, z - 8) for x, y, z in f.verts]
    # Framed on three sides of each face; open where the two meet, so the void wraps round the corner.
    y0, y1 = 7, 12
    faces.append(quad(void, [(-2, y1, 3.02), (3.02, y1, 3.02), (3.02, y0, 3.02), (-2, y0, 3.02)], (0, 0, 4, 4), bright=True))
    faces.append(quad(void, [(3.02, y1, 3.02), (3.02, y1, -2), (3.02, y0, -2), (3.02, y0, 3.02)], (0, 0, 4, 4), bright=True))
    return faces


def bell_sprite(fill=58):
    """The bell with an Eye of Ender in its void, facing out of the sprite: it looks at the player."""
    faces = bell_faces()
    img = paint_void(outline(render(faces, fill=fill)), 0.7)
    eye = texture('item/ender_eye.png').crop((2, 2, 15, 15))
    x, y = to_screen(faces, BELL_EYE_AT, fill=fill)
    img.alpha_composite(eye, (round(x - eye.width / 2), round(y - eye.height / 2)))
    return img


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
    for name, image in (('phantom_charm', lambda: outline(render(chest_faces(), fill=58))),
                        ('phantom_charm_saddle', lambda: outline(render(saddled_faces(), fill=58))),
                        ('phantom_charm_gateway', lambda: outline(render(gateway_faces(), fill=60))),
                        ('phantom_charm_bell', bell_sprite)):
        image().save(os.path.join(out, name + '.png'))
        print('wrote', os.path.join(out, name + '.png'))
