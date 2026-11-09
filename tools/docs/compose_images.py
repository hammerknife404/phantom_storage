"""Builds the wiki's images (docs/wiki/images) from the dev harness's "docs" run.

Run the harness first (PHANTOM_HARNESS=docs, see CLAUDE.md): it leaves 1280x720 screenshots in
run/screenshots/d_*.png and each panel's crop box as a PANEL line in run/harness.log. Then, from the
repository root: python3 tools/docs/compose_images.py (needs Pillow). World shots become JPEGs, panels
PNG crops; the charm sprites are copied from the mod's item textures (an animated one becomes an animated PNG).
"""
import os
import re
import shutil

from PIL import Image

SHOTS = 'run/screenshots/'
OUT = 'docs/wiki/images/'
SPRITES = 'src/main/resources/assets/phantomstorage/textures/item/'


def shot(name):
    return Image.open(SHOTS + name + '.png').convert('RGB')


def jpeg(image, name):
    image.save(OUT + name, quality=86, optimize=True, progressive=True)


def strip(crops, name):
    """Three shots side by side, each cropped to its box and scaled to 480x360."""
    tiles = [shot(n).crop(box).resize((480, 360), Image.LANCZOS) for n, box in crops]
    out = Image.new('RGB', (480 * 3 + 16, 360), (255, 255, 255))
    for i, tile in enumerate(tiles):
        out.paste(tile, (i * 488, 0))
    jpeg(out, name)


def animated(strip, mcmeta, out):
    """An animated sprite (frames stacked top to bottom, timed by its .mcmeta) as an animated PNG, which
    browsers play: the wiki shows the charm as the game does, but for the game's blending between frames."""
    import json
    ticks = json.load(open(mcmeta))['animation'].get('frametime', 1)
    size = strip.width
    frames = [strip.crop((0, i * size, size, (i + 1) * size)).convert('RGBA') for i in range(strip.height // size)]
    frames[0].save(out, save_all=True, append_images=frames[1:], duration=ticks * 50, loop=0, optimize=True)


def main():
    os.makedirs(OUT, exist_ok=True)
    jpeg(shot('d_pets_100').crop((150, 190, 1130, 460)), 'pets.jpg')

    full = shot('d_pets_100').crop((180, 200, 1100, 450)).resize((640, 174), Image.LANCZOS)
    faded = shot('d_pets_40').crop((180, 200, 1100, 450)).resize((640, 174), Image.LANCZOS)
    pair = Image.new('RGB', (1288, 174), (255, 255, 255))
    pair.paste(full, (0, 0))
    pair.paste(faded, (648, 0))
    jpeg(pair, 'opacity.jpg')

    for line in open('run/harness.log'):
        m = re.match(r'PANEL (\w+) (\d+) (\d+) (\d+) (\d+)', line)
        if m:
            x, y, w, h = (int(v) for v in m.groups()[1:])
            Image.open(SHOTS + 'd_' + m.group(1) + '_panel.png').convert('RGB').crop((x, y, x + w, y + h)) \
                .save(OUT + m.group(1) + '-panel.png', optimize=True)

    jpeg(shot('d_saddle').crop((360, 250, 1080, 650)), 'saddle.jpg')
    wide = (200, 60, 1080, 720)
    strip([('d_gate_3x3', wide), ('d_gate_2x3', wide), ('d_gate_1x2', wide)], 'gateway-standing.jpg')
    # The mid-air shot is cropped tighter: the chest, following after the saddle shot, sits in its corner.
    strip([('d_gate_floor', wide), ('d_gate_air', (220, 60, 840, 525)), ('d_gate_ceiling', wide)], 'gateway-flat.jpg')

    for f in sorted(os.listdir(SPRITES)):
        if not f.endswith('.png'):
            continue
        mode = f[len('phantom_charm'):-len('.png')].lstrip('_') or 'chest'
        sprite = Image.open(SPRITES + f)
        if sprite.height == sprite.width:
            shutil.copy(SPRITES + f, OUT + 'charm-' + mode + '.png')
        else:
            animated(sprite, SPRITES + f + '.mcmeta', OUT + 'charm-' + mode + '.png')


if __name__ == '__main__':
    main()
