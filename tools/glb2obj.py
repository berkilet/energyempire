#!/usr/bin/env python3
"""
GLB (glTF 2.0 binary) -> OBJ + MTL donusturucu.

LibGDX cekirdegi GLB yukleyemez; oyun OBJ kullanir. Bu arac assets-src/ altindaki GLB dosyalarini
src/main/resources/models/nature/ altina cevirir:
  * dugum (node) donusumleri (translation/rotation/scale/matrix) kose verisine pisirilir,
  * her primitive kendi malzemesiyle yazilir (usemtl),
  * malzeme rengi: baseColorFactor (dogrusal -> sRGB) ya da dokunun ortalama rengi.

Kullanim: python3 tools/glb2obj.py <girdi.glb> <cikti_klasoru>
Yalnizca standart kutuphane + Pillow (dokulu malzemelerin ortalama rengi icin) gerekir.
"""
import io
import json
import math
import os
import struct
import sys

COMPONENT = {5120: ('b', 1), 5121: ('B', 1), 5122: ('h', 2), 5123: ('H', 2), 5125: ('I', 4), 5126: ('f', 4)}
NCOMP = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}


def read_glb(path):
    b = open(path, 'rb').read()
    if b[:4] != b'glTF':
        raise ValueError('GLB degil: ' + path)
    jlen = struct.unpack('<I', b[12:16])[0]
    j = json.loads(b[20:20 + jlen])
    pos = 20 + jlen
    binchunk = b''
    if pos < len(b):
        blen = struct.unpack('<I', b[pos:pos + 4])[0]
        binchunk = b[pos + 8:pos + 8 + blen]
    return j, binchunk


def accessor(j, binchunk, idx):
    a = j['accessors'][idx]
    bv = j['bufferViews'][a['bufferView']]
    fmt, size = COMPONENT[a['componentType']]
    n = NCOMP[a['type']]
    stride = bv.get('byteStride', size * n)
    base = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
    out = []
    for i in range(a['count']):
        off = base + i * stride
        vals = struct.unpack_from('<' + fmt * n, binchunk, off)
        out.append(vals if n > 1 else vals[0])
    return out


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def node_matrix(n):
    if 'matrix' in n:
        m = n['matrix']  # sutun oncelikli
        return [[m[c * 4 + r] for c in range(4)] for r in range(4)]
    t = n.get('translation', [0, 0, 0])
    x, y, z, w = n.get('rotation', [0, 0, 0, 1])
    s = n.get('scale', [1, 1, 1])
    r = [[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
         [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
         [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]]
    return [[r[0][0] * s[0], r[0][1] * s[1], r[0][2] * s[2], t[0]],
            [r[1][0] * s[0], r[1][1] * s[1], r[1][2] * s[2], t[1]],
            [r[2][0] * s[0], r[2][1] * s[1], r[2][2] * s[2], t[2]],
            [0, 0, 0, 1]]


def to_srgb(c):
    c = max(0.0, min(1.0, c))
    return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055


def material_color(j, binchunk, mi):
    m = j['materials'][mi]
    pbr = m.get('pbrMetallicRoughness', {})
    if 'baseColorTexture' in pbr:
        try:
            from PIL import Image
            img_i = j['textures'][pbr['baseColorTexture']['index']]['source']
            bv = j['bufferViews'][j['images'][img_i]['bufferView']]
            data = binchunk[bv.get('byteOffset', 0): bv.get('byteOffset', 0) + bv['byteLength']]
            im = Image.open(io.BytesIO(data)).convert('RGB').resize((1, 1), Image.BOX)
            return tuple(v / 255 for v in im.getpixel((0, 0)))  # doku zaten sRGB
        except Exception as e:  # Pillow yoksa beyaz
            print('  uyari: doku rengi okunamadi', e)
            return (1, 1, 1)
    f = pbr.get('baseColorFactor', [1, 1, 1, 1])
    return tuple(to_srgb(v) for v in f[:3])


def convert(src, outdir):
    j, binchunk = read_glb(src)
    name = os.path.splitext(os.path.basename(src))[0]
    mats = {}
    lines = ['# ' + os.path.basename(src) + ' kaynagindan glb2obj.py ile uretildi', 'mtllib ' + name + '.mtl']
    vbase = 1
    scene = j['scenes'][j.get('scene', 0)]

    def walk(ni, parent):
        nonlocal vbase
        n = j['nodes'][ni]
        m = mat_mul(parent, node_matrix(n))
        if 'mesh' in n:
            mesh = j['meshes'][n['mesh']]
            nm = [[m[r][c] for c in range(3)] for r in range(3)]  # normal icin (olcek esit varsayimi)
            for p in mesh['primitives']:
                if p.get('mode', 4) != 4:
                    continue
                pos = accessor(j, binchunk, p['attributes']['POSITION'])
                nor = accessor(j, binchunk, p['attributes']['NORMAL']) if 'NORMAL' in p['attributes'] else None
                ind = accessor(j, binchunk, p['indices']) if 'indices' in p else list(range(len(pos)))
                mi = p.get('material', -1)
                mname = (j['materials'][mi].get('name', 'mat%d' % mi) if mi >= 0 else 'default').replace(' ', '_')
                if mname not in mats:
                    mats[mname] = material_color(j, binchunk, mi) if mi >= 0 else (1, 1, 1)
                # LibGDX ObjLoader grup basina tek malzeme kullanir: her primitive kendi grubunda olmali.
                lines.append('g ' + (n.get('name', 'node%d' % ni)).replace(' ', '_') + '_' + mname)
                lines.append('usemtl ' + mname)
                for v in pos:
                    x = m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2] + m[0][3]
                    y = m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2] + m[1][3]
                    z = m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2] + m[2][3]
                    lines.append('v %.5f %.5f %.5f' % (x, y, z))
                for v in (nor or [(0, 1, 0)] * len(pos)):
                    x = nm[0][0] * v[0] + nm[0][1] * v[1] + nm[0][2] * v[2]
                    y = nm[1][0] * v[0] + nm[1][1] * v[1] + nm[1][2] * v[2]
                    z = nm[2][0] * v[0] + nm[2][1] * v[1] + nm[2][2] * v[2]
                    l = math.sqrt(x * x + y * y + z * z) or 1
                    lines.append('vn %.5f %.5f %.5f' % (x / l, y / l, z / l))
                for t in range(0, len(ind), 3):
                    a, b, c = (ind[t] + vbase, ind[t + 1] + vbase, ind[t + 2] + vbase)
                    lines.append('f %d//%d %d//%d %d//%d' % (a, a, b, b, c, c))
                vbase += len(pos)
        for c in n.get('children', []):
            walk(c, m)

    ident = [[1 if r == c else 0 for c in range(4)] for r in range(4)]
    for ni in scene['nodes']:
        walk(ni, ident)
    os.makedirs(outdir, exist_ok=True)
    with open(os.path.join(outdir, name + '.obj'), 'w') as f:
        f.write('\n'.join(lines) + '\n')
    with open(os.path.join(outdir, name + '.mtl'), 'w') as f:
        for k, (r, g, b) in mats.items():
            f.write('newmtl %s\nKd %.4f %.4f %.4f\n\n' % (k, r, g, b))
    return name, vbase - 1, mats


if __name__ == '__main__':
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    nm, nv, mats = convert(sys.argv[1], sys.argv[2])
    print('%-40s %5d kose  malzemeler: %s' % (nm, nv, ', '.join('%s(%.2f,%.2f,%.2f)' % (k, *v) for k, v in mats.items())))
