"""Puts the grow orb into the farming claw's jaws as its own group (one model, one 64x64 texture) for the game.
The orb is hidden except during the growing animations. Usage: python3 tools/merge_claw_orb.py out.bbmodel"""
import json, base64, io, copy, sys
from PIL import Image
claw = json.load(open('blockbench/farming_claw.bbmodel')); orb = json.load(open('blockbench/grow_orb.bbmodel'))
def img(m): return Image.open(io.BytesIO(base64.b64decode(m['textures'][0]['source'].split(',')[1]))).convert('RGBA')
tex = img(claw); otex = img(orb)
OX, OY = 48, 48                      # orb texture goes in the claw sheet's empty bottom-right corner
tex.paste(otex.resize((16, 16), Image.NEAREST), (OX, OY))
s = io.BytesIO(); tex.save(s, 'PNG')
m = copy.deepcopy(claw)
m['textures'][0]['source'] = 'data:image/png;base64,' + base64.b64encode(s.getvalue()).decode()
k = 16 / orb['textures'][0].get('uv_width', orb['resolution']['width'])
OZ = -6
oe = copy.deepcopy(orb['elements'][0]); oe['from'][2] += OZ; oe['to'][2] += OZ; oe['origin'][2] += OZ
for f in oe['faces'].values():
    u1, v1, u2, v2 = f['uv']; f['uv'] = [OX + u1 * k, OY + v1 * k, OX + u2 * k, OY + v2 * k]
oe['box_uv'] = False
og = copy.deepcopy(orb['groups'][0]); og['name'] = 'grow orb'; og['origin'] = [0, 5, OZ]
OG = og['uuid']
m['elements'].append(oe); m['groups'].append(og); m['outliner'].append({'uuid': OG, 'isOpen': True, 'children': [oe['uuid']]})
spin = orb['animations'][0]['animators'][OG]['keyframes']
def kf(ch, t, v): return {'channel': ch, 'data_points': [{'x': str(v[0]), 'y': str(v[1]), 'z': str(v[2])}], 'time': t, 'interpolation': 'linear', 'uuid': f'{ch}{t}'}
HIDE = (0.001, 0.001, 0.001)
for a in m['animations']:
    if a['name'] == 'growing crop': keys = [kf('scale', 0, HIDE), kf('scale', 0.25, (1, 1, 1))] + spin
    elif a['name'] == 'growing crop loop': keys = [kf('scale', 0, (1, 1, 1))] + spin
    else: keys = [kf('scale', 0, HIDE)]
    a['animators'][OG] = {'name': 'grow orb', 'type': 'bone', 'keyframes': keys}
m['animations'].append({'name': 'idle', 'loop': 'loop', 'length': 1, 'animators': {OG: {'name': 'grow orb', 'type': 'bone', 'keyframes': [kf('scale', 0, HIDE)]}}})
json.dump(m, open(sys.argv[1], 'w'))
