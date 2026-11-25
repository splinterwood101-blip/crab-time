"""Turns a Blockbench Java Block/Item file into a Minecraft model json and its textures.

Usage: python3 tools/bbmodel_to_block.py blockbench/crab_nest.bbmodel block crab_nest [projres] [cutout]
  kind = block or item; writes models/<kind>/<name>.json and textures/<kind>/<name>[_N].png
"""
import json, sys, base64, io, os
from PIL import Image

src, kind, name = sys.argv[1:4]
opts = sys.argv[4:]  # 'projres': read every face UV in the project's UV size (Blockbench's own default); 'cutout': see-through pixels
RES = 'src/main/resources/assets/crabtime'
m = json.load(open(src))
texs = m['textures']
tex_ids, sizes = {}, {}
for i, t in enumerate(texs):
    tid = name if len(texs) == 1 else f'{name}_{i}'
    img = Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1]))).convert('RGBA')
    os.makedirs(f'{RES}/textures/{kind}', exist_ok=True)
    img.save(f'{RES}/textures/{kind}/{tid}.png')
    tex_ids[str(i)] = f'crabtime:{kind}/{tid}'
    sizes[str(i)] = ((m['resolution']['width'], m['resolution']['height']) if 'projres' in opts
                     else (t.get('uv_width', m['resolution']['width']), t.get('uv_height', m['resolution']['height'])))
els = []
for e in m['elements']:
    if not e.get('export', True): continue
    faces = {}
    for f, d in e['faces'].items():
        if d.get('texture') is None: continue
        k = str(d['texture']); w, h = sizes[k]
        u = d['uv']
        face = {'uv': [round(u[0] * 16 / w, 4), round(u[1] * 16 / h, 4), round(u[2] * 16 / w, 4), round(u[3] * 16 / h, 4)], 'texture': '#' + k}
        if d.get('rotation'): face['rotation'] = d['rotation']
        faces[f] = face
    el = {'from': e['from'], 'to': e['to'], 'faces': faces}
    r = e.get('rotation') or [0, 0, 0]
    for axis, ang in zip('xyz', r):
        if ang:
            el['rotation'] = {'angle': ang, 'axis': axis, 'origin': e.get('origin', [8, 8, 8])}
            break
    els.append(el)
textures = dict(tex_ids)
textures['particle'] = tex_ids[str(next((i for i, t in enumerate(texs) if t.get('particle')), 0))]
model = {'textures': textures, 'elements': els}
if kind == 'block': model = {'parent': 'minecraft:block/block', **model}  # vanilla block views (inventory angle etc.) unless the file sets its own
if 'cutout' in opts: model['render_type'] = 'minecraft:cutout'
if m.get('display'): model['display'] = m['display']
os.makedirs(f'{RES}/models/{kind}', exist_ok=True)
json.dump(model, open(f'{RES}/models/{kind}/{name}.json', 'w'), indent=1)
print(f'{kind}/{name}: {len(els)} cubes, {len(texs)} texture(s)')
