"""Puts the End portal starfield into the anchor's and collector's charm sprites, in place of their violet
void pixels (the shader is screen-space and unshaded, so in game it looks just as flat).

    python3 tools/sprites/void_sprites.py [out_dir]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from starfield import starfield  # noqa: E402
from vanilla import MOD_TEXTURES, ROOT  # noqa: E402
from PIL import Image  # noqa: E402

OUTLINE = (18, 12, 30)

if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'build', 'sprites')
    os.makedirs(out, exist_ok=True)
    for name, ox in (('phantom_charm_anchor', 0.1), ('phantom_charm_collector', 0.4)):
        im = Image.open(os.path.join(MOD_TEXTURES, 'item', name + '.png')).convert('RGBA')
        stars = starfield(64, 64, 0.35, 0.35, ox=ox).load()
        px = im.load()
        for y in range(64):
            for x in range(64):
                r, g, b, a = px[x, y]
                # The void: violet, blue well above green (the eyes are green-yellow, the stone grey).
                if a and (r, g, b) != OUTLINE and b > g + 18 and b >= r:
                    px[x, y] = stars[x, y] + (255,)
        im.save(os.path.join(out, name + '.png'))
        print('wrote', os.path.join(out, name + '.png'))
