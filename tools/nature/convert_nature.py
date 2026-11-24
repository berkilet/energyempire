#!/usr/bin/env python3
"""
Doga paketi donusturucu (gelistirici araci; oyun calisirken kullanilmaz).

Yuksek poligonlu, dokulu OBJ doga modellerini oyunun arazi parcalarina "damga" olarak pisirilebilecek
hafif bir ikili bicime cevirir:
  * gövde/kaya/mantar/cim gibi opak malzemeler: dokudan kose basina renk ornekleme + kose kumeleme ile sadelestirme
    (duz golgeli low-poly gorunum);
  * yaprak/cicek gibi alfa kesimli malzemeler: tek bir doku atlasina (foliage_atlas.png) UV; cok kartli yapraklarda
    kartlarin bir kismi secilip buyutulur (ayni dolulukta daha az ucgen).

Kullanim:
  python3 -I tools/nature/convert_nature.py <OBJ klasoru> <doku klasoru> [<doku klasoru> ...] <cikis klasoru>

Cikti bicimi (gzip, big-endian): 'NTR2', int koseSayisi; kose basina (x,y,z, nx,ny,nz, r,g,b, u,v) float;
int ucgenSayisi; ucgen basina byte tur + 3 short kose indeksi.
tur: 0 opak (u,v kullanilmaz), 1 yaprak kumesi (atlas hucresi oyunda secilir), 2 cam yapragi, 3 bitki yapraklari, 4 cicek.
u,v: hucre ici [0,1] (v ust=0).
"""
import gzip, math, os, random, struct, sys

import numpy as np
from PIL import Image, ImageFilter

# model -> (opak ucgen hedefi, yaprak karti hedefi (bilesen sayisi), ek ayarlar)
MODELS = {
    # agaclar
    'CommonTree_1': (150, 130), 'CommonTree_2': (150, 130), 'CommonTree_3': (130, 130),
    'CommonTree_4': (130, 120), 'CommonTree_5': (130, 130),
    'Pine_1': (130, 20), 'Pine_2': (130, 20), 'Pine_3': (130, 18), 'Pine_4': (130, 22), 'Pine_5': (110, 22),
    'TwistedTree_1': (180, 160), 'TwistedTree_3': (180, 160), 'TwistedTree_5': (180, 160),
    'DeadTree_1': (240, 0), 'DeadTree_2': (240, 0), 'DeadTree_3': (240, 0),
    # calilar, bitkiler, cicekler
    'Bush_Common': (0, 90), 'Bush_Common_Flowers': (0, 90),
    'Fern_1': (0, 999), 'Clover_1': (0, 999), 'Clover_2': (0, 999), 'Plant_1': (0, 999), 'Plant_1_Big': (0, 999),
    'Plant_7': (0, 999), 'Plant_7_Big': (0, 999),
    'Flower_3_Group': (0, 999), 'Flower_3_Single': (0, 999), 'Flower_4_Single': (0, 999),
    # cim, mantar, kaya
    'Grass_Common_Short': (90, 0), 'Grass_Common_Tall': (110, 0), 'Grass_Wispy_Short': (120, 0), 'Grass_Wispy_Tall': (130, 0),
    'Mushroom_Common': (140, 0),
    'Rock_Medium_1': (220, 0), 'Rock_Medium_2': (220, 0), 'Rock_Medium_3': (220, 0),
}
# ayni geometri, farkli doku (col kayalari)
VARIANTS = {'Rock_Medium_1_Desert': ('Rock_Medium_1', {'Rocks': 'Rocks_Desert_Diffuse.png'}),
            'Rock_Medium_2_Desert': ('Rock_Medium_2', {'Rocks': 'Rocks_Desert_Diffuse.png'}),
            'Rock_Medium_3_Desert': ('Rock_Medium_3', {'Rocks': 'Rocks_Desert_Diffuse.png'})}

OPAQUE_TEX = {'Bark_NormalTree': 'Bark_NormalTree.png', 'Bark_DeadTree': 'Bark_DeadTree.png',
              'Bark_TwistedTree': 'Bark_TwistedTree.png', 'Grass': 'Grass.png', 'Mushrooms': 'Mushrooms.png',
              'Rocks': 'Rocks_Diffuse.png', 'PathRocks': 'PathRocks_Diffuse.png'}
# kesimli malzeme -> (tur, atlas hucresi dokusu)
CUTOUT = {'Leaves_NormalTree': 1, 'Leaves_TwistedTree': 1, 'Leaves_Pine': 2, 'Leaves': 3, 'Flowers': 4}
# atlas: 4x2 hucre, 512 px; hucre sirasi oyundaki NatureTemplate.CELL_* ile ayni
ATLAS = ['Leaves_NormalTree_C.png', 'Leaves_GiantPine_C.png', 'Leaves_TwistedTree_C.png', 'Leaf_Pine_C.png',
         'Leaves.png', 'Flowers.png']
CELL = 512


def find_tex(dirs, name):
    for d in dirs:
        p = os.path.join(d, name)
        if os.path.exists(p):
            return p
    raise FileNotFoundError(name)


def load_obj(path):
    V, T, F = [], [], []
    mat = None
    with open(path) as f:
        for line in f:
            t = line.split()
            if not t:
                continue
            if t[0] == 'v':
                V.append([float(x) for x in t[1:4]])
            elif t[0] == 'vt':
                T.append([float(x) for x in t[1:3]])
            elif t[0] == 'usemtl':
                mat = t[1]
            elif t[0] == 'f':
                idx = []
                for c in t[1:]:
                    p = c.split('/')
                    idx.append((int(p[0]) - 1, int(p[1]) - 1 if len(p) > 1 and p[1] else -1))
                for k in range(1, len(idx) - 1):
                    F.append((mat, idx[0], idx[k], idx[k + 1]))
    return np.array(V, dtype=np.float64), np.array(T if T else [[0, 0]], dtype=np.float64), F


_tex_cache = {}


def tex_small(dirs, name):
    """Renk orneklemek icin kucultulmus (yumusatilmis) doku dizisi."""
    if name not in _tex_cache:
        im = Image.open(find_tex(dirs, name)).convert('RGB').resize((128, 128), Image.BOX)
        _tex_cache[name] = np.asarray(im, dtype=np.float64) / 255.0
    return _tex_cache[name]


def sample(img, u, v):
    h, w, _ = img.shape
    x = int((u % 1.0) * (w - 1))
    y = int((1.0 - (v % 1.0)) * (h - 1))
    return img[y, x]


def face_normal(a, b, c):
    n = np.cross(b - a, c - a)
    l = np.linalg.norm(n)
    return n / l if l > 1e-12 else np.array([0.0, 1.0, 0.0])


def cluster_decimate(V, cols, faces, target):
    """Kose kumeleme: hedef ucgen sayisina inene kadar izgara buyutulur. faces: [(i,j,k)], cols: kose rengi."""
    if target <= 0 or len(faces) <= target:
        return V, cols, faces
    lo_b, hi_b = V.min(0), V.max(0)
    ext = float((hi_b - lo_b).max())
    lo, hi = ext / 400.0, ext / 2.0
    best = None
    for _ in range(22):
        cell = math.sqrt(lo * hi)
        key = np.floor((V - lo_b) / cell).astype(np.int64)
        ids = {}
        cid = np.empty(len(V), dtype=np.int64)
        for i, k in enumerate(map(tuple, key)):
            cid[i] = ids.setdefault(k, len(ids))
        out = set()
        nf = []
        for (a, b, c) in faces:
            ca, cb, cc = cid[a], cid[b], cid[c]
            if ca == cb or cb == cc or ca == cc:
                continue
            s = tuple(sorted((ca, cb, cc)))
            if s in out:
                continue
            out.add(s)
            nf.append((ca, cb, cc))
        if len(nf) > target:
            lo = cell
        else:
            hi = cell
            best = (cid, len(ids), nf)
        if hi / lo < 1.03:
            break
    if best is None:
        return V, cols, faces
    cid, n, nf = best
    NV = np.zeros((n, 3)); NC = np.zeros((n, 3)); cnt = np.zeros(n)
    np.add.at(NV, cid, V); np.add.at(NC, cid, cols); np.add.at(cnt, cid, 1)
    cnt[cnt == 0] = 1
    return NV / cnt[:, None], NC / cnt[:, None], nf


def components(faces):
    par = {}

    def find(a):
        while par.setdefault(a, a) != a:
            par[a] = par[par[a]]
            a = par[a]
        return a
    for f in faces:
        r = find(f[0])
        for c in f[1:]:
            par[find(c)] = r
    groups = {}
    for fi, f in enumerate(faces):
        groups.setdefault(find(f[0]), []).append(fi)
    return list(groups.values())


def convert(name, src, obj_dir, tex_dirs, out_dir, tex_override=None):
    opaque_target, card_target = MODELS[src]
    V, T, F = load_obj(os.path.join(obj_dir, src + '.obj'))
    rnd = random.Random(hash(name) & 0xffff)
    verts = []   # (pos3, nrm3, col3, uv2)
    vkey = {}
    tris = []    # (kind, i0, i1, i2)

    def vid(key, make):
        if key not in vkey:
            vkey[key] = len(verts)
            verts.append(make())
        return vkey[key]

    # ---- opak malzemeler (kumeleme sonrasi paylasilan koseler, yumusak normaller)
    opaque = [f for f in F if f[0] not in CUTOUT]
    if opaque:
        # kose basina renk (her malzemenin kendi dokusundan); kose indeksleri malzemeler arasinda paylasilabilir
        cols = np.zeros((len(V), 3)); cnt = np.zeros(len(V))
        for m, *cs in opaque:
            texname = (tex_override or {}).get(m, OPAQUE_TEX.get(m))
            img = tex_small(tex_dirs, texname) if texname else None
            for (vi, ti) in cs:
                c = sample(img, *T[ti]) if img is not None and ti >= 0 else np.array([0.6, 0.6, 0.6])
                cols[vi] += c; cnt[vi] += 1
        cnt[cnt == 0] = 1
        cols /= cnt[:, None]
        used = sorted({vi for f in opaque for (vi, _) in f[1:]})
        remap = {v: i for i, v in enumerate(used)}
        sv = V[used]; sc = cols[used]
        sf = [(remap[a[0]], remap[b[0]], remap[c[0]]) for _, a, b, c in opaque]
        sv, sc, sf = cluster_decimate(sv, sc, sf, opaque_target)
        vn = np.zeros((len(sv), 3))
        for (a, b, c) in sf:
            n = np.cross(sv[b] - sv[a], sv[c] - sv[a])
            for i in (a, b, c):
                vn[i] += n
        ln = np.linalg.norm(vn, axis=1); ln[ln < 1e-12] = 1
        vn /= ln[:, None]
        for (a, b, c) in sf:
            ids = [vid(('o', i), lambda i=i: (sv[i], vn[i], sc[i], (0.0, 0.0))) for i in (a, b, c)]
            tris.append((0, *ids))

    # ---- kesimli malzemeler (atlas); kart icindeki koseler paylasilir
    for m, kind in CUTOUT.items():
        fs = [f for f in F if f[0] == m]
        if not fs:
            continue
        idx_faces = [tuple(c[0] for c in f[1:]) for f in fs]
        comps = components(idx_faces)
        keep = comps
        scale = 1.0
        if card_target and len(comps) > card_target:
            keep = rnd.sample(comps, card_target)
            scale = min(1.9, math.sqrt(len(comps) / card_target) ** 0.9)
        for ci, comp in enumerate(keep):
            pts = np.array([V[c[0]] for fi in comp for c in fs[fi][1:]])
            ctr = pts.mean(0)
            for fi in comp:
                f = fs[fi]
                p = [ctr + (V[c[0]] - ctr) * scale for c in f[1:]]
                n = face_normal(*p)
                if n[1] < -0.2:
                    n = -n  # yapraklar cift yuzlu; normal yukari baksin (isik tutarli)
                ids = []
                for k, c in enumerate(f[1:]):
                    u, v = T[c[1]] if c[1] >= 0 else (0, 0)
                    uv = (min(1.0, max(0.0, u)), min(1.0, max(0.0, 1.0 - v)))
                    ids.append(vid(('c', kind, id(comp), c[0], c[1]), lambda p=p[k], n=n, uv=uv: (p, n, np.array([1.0, 1.0, 1.0]), uv)))
                tris.append((kind, *ids))

    os.makedirs(out_dir, exist_ok=True)
    with gzip.open(os.path.join(out_dir, name + '.bin'), 'wb', compresslevel=9) as out:
        out.write(b'NTR2')
        out.write(struct.pack('>i', len(verts)))
        for (p, n, c, uv) in verts:
            out.write(struct.pack('>11f', p[0], p[1], p[2], n[0], n[1], n[2], c[0], c[1], c[2], uv[0], uv[1]))
        out.write(struct.pack('>i', len(tris)))
        for (kind, i0, i1, i2) in tris:
            out.write(struct.pack('>bhhh', kind, i0, i1, i2))
    print('   koseler', len(verts), end=' ')
    return len(tris), len(F)


def build_atlas(tex_dirs, out_dir):
    W = CELL * 4
    atlas = Image.new('RGBA', (W, CELL * 2), (0, 0, 0, 0))
    for i, name in enumerate(ATLAS):
        im = Image.open(find_tex(tex_dirs, name)).convert('RGBA').resize((CELL, CELL), Image.LANCZOS)
        a = np.asarray(im, dtype=np.float64)
        # saydam piksellerin rengini komsu opak renklerle doldur (mipmap'te koyu/mor hale olmasin)
        rgb, al = a[..., :3], a[..., 3:4] / 255.0
        pre = Image.fromarray(np.uint8(np.clip(rgb * al, 0, 255)))
        msk = Image.fromarray(np.uint8(al[..., 0] * 255))
        acc = np.zeros_like(rgb); wsum = np.zeros_like(al)
        for r in (2, 6, 16, 48):
            pb = np.asarray(pre.filter(ImageFilter.GaussianBlur(r)), dtype=np.float64)
            mb = np.asarray(msk.filter(ImageFilter.GaussianBlur(r)), dtype=np.float64)[..., None] / 255.0
            fill = (wsum < 1e-3) & (mb > 1e-3)
            acc = np.where(fill, pb / np.maximum(mb, 1e-6), acc)
            wsum = np.where(fill, 1.0, wsum)
        out_rgb = np.where(al > 0.5, rgb, acc)
        cell = np.concatenate([np.clip(out_rgb, 0, 255), al * 255], axis=-1).astype(np.uint8)
        atlas.paste(Image.fromarray(cell, 'RGBA'), ((i % 4) * CELL, (i // 4) * CELL))
    atlas.save(os.path.join(out_dir, 'foliage_atlas.png'), optimize=True)


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(1)
    obj_dir, tex_dirs, out_dir = sys.argv[1], sys.argv[2:-1], sys.argv[-1]
    total = 0
    for name in MODELS:
        n, orig = convert(name, name, obj_dir, tex_dirs, out_dir)
        total += n
        print('%-22s %5d -> %4d ucgen' % (name, orig, n))
    for name, (src, ov) in VARIANTS.items():
        n, orig = convert(name, src, obj_dir, tex_dirs, out_dir, ov)
        print('%-22s %5d -> %4d ucgen' % (name, orig, n))
    build_atlas(tex_dirs, out_dir)
    print('toplam', total)


if __name__ == '__main__':
    main()
