"""Vanilla textures, read straight from the NeoForge client-extra jar the build downloads."""
import glob
import io
import os
import zipfile

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
MOD_TEXTURES = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'phantomstorage', 'textures')


def _jar():
    jars = glob.glob(os.path.join(ROOT, 'build', 'moddev', 'artifacts', 'neoforge-*-client-extra-aka-minecraft-resources.jar'))
    if not jars:
        raise SystemExit('No NeoForge client-extra jar: run ./gradlew build (or prepareClientRun) first.')
    return jars[0]


def texture(path):
    """A vanilla texture by its path under assets/minecraft/textures, e.g. 'entity/pig/pig_saddle.png'."""
    with zipfile.ZipFile(_jar()) as jar:
        return Image.open(io.BytesIO(jar.read('assets/minecraft/textures/' + path))).convert('RGBA')


def mod_texture(path):
    """One of this mod's textures, e.g. 'entity/phantom_chest.png'."""
    return Image.open(os.path.join(MOD_TEXTURES, path)).convert('RGBA')
