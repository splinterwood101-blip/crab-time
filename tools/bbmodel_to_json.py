"""Turns a Blockbench Modded Entity file (.bbmodel) into the crab's model data and texture.

Usage: python3 tools/bbmodel_to_json.py blockbench/crab.bbmodel crab [json_out png_out]
Writes src/main/resources/assets/crabtime/models/entity/<name>.json and textures/entity/<name>.png.
Blank or mistyped keyframe numbers are read as 0 (or 1 for scale), the same way Blockbench reads them.
"""
import json, sys, base64, io, os
from PIL import Image

src, name = sys.argv[1], sys.argv[2]
JSON_OUT = sys.argv[3] if len(sys.argv) > 3 else None
PNG_OUT = sys.argv[4] if len(sys.argv) > 4 else None
m = json.load(open(src))
RES = 'src/main/resources/assets/crabtime'

def num(v, d=0.0):
    try: return float(str(v).strip())
    except ValueError: return d

# texture
t = m['textures'][0]
img = Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1]))).convert('RGBA')
png = PNG_OUT or f'{RES}/textures/entity/{name}.png'
os.makedirs(os.path.dirname(png), exist_ok=True)
img.save(png)
uw, uh = t.get('uv_width', m['resolution']['width']), t.get('uv_height', m['resolution']['height'])

# bones in outliner order (parents first)
groups = {g['uuid']: g for g in m['groups']}
bones, index, owner = [], {}, {}
def walk(node, parent):
    if isinstance(node, str):
        owner[node] = parent; return
    g = groups[node['uuid']]
    index[node['uuid']] = len(bones)
    bones.append({'name': g['name'], 'pivot': g['origin'], 'rot': g.get('rotation', [0, 0, 0]), 'parent': parent})
    me = index[node['uuid']]
    for c in node['children']: walk(c, me)
for n in m['outliner']: walk(n, -1)

cubes = []
for e in m['elements']:
    if e.get('type', 'cube') != 'cube' or not e.get('export', True): continue
    faces = {f: [d['uv'][0] / uw, d['uv'][1] / uh, d['uv'][2] / uw, d['uv'][3] / uh]
             for f, d in e['faces'].items() if d.get('texture') is not None}
    cubes.append({'bone': owner.get(e['uuid'], -1), 'from': e['from'], 'to': e['to'], 'origin': e.get('origin', [0, 0, 0]),
                  'rot': e.get('rotation', [0, 0, 0]), 'inflate': e.get('inflate', 0), 'faces': faces})

INTERP = {'linear': 0, 'catmullrom': 1, 'step': 2}
anims = {}
for a in m.get('animations', []):
    out = {}
    for uuid, an in a.get('animators', {}).items():
        if uuid not in index: continue
        chans = {}
        for k in an.get('keyframes', []):
            d = 1.0 if k['channel'] == 'scale' else 0.0
            p = k['data_points'][0]
            chans.setdefault(k['channel'], []).append([k['time'], num(p.get('x'), d), num(p.get('y'), d), num(p.get('z'), d), INTERP.get(k.get('interpolation'), 0)])
        for c in chans.values(): c.sort(key=lambda r: r[0])
        if chans: out[str(index[uuid])] = chans
    anims[a['name']] = {'length': a.get('length', 1), 'loop': a.get('loop', 'once'), 'bones': out}

out = JSON_OUT or f'{RES}/models/entity/{name}.json'
os.makedirs(os.path.dirname(out), exist_ok=True)
json.dump({'bones': bones, 'cubes': cubes, 'anims': anims}, open(out, 'w'), separators=(',', ':'))
print(len(bones), 'bones,', len(cubes), 'cubes, animations:', ', '.join(anims))
