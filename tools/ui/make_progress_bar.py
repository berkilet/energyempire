#!/usr/bin/env python3
"""
Ilerleme cubugu parcalari (gelistirici araci). Kullanicinin verdigi 10 kareli cubuk gorselinden (macenta zemin):
  bar_track.png      bos cerceve (sol baslik sag basligin aynasi), dokuz-parca olarak esner
  bar_fill.png       yesil dolgu (%90 karesinden)
  bar_fill_full.png  tamamlanmis dolgu (%100 karesi, kivilcimli)
Kullanim: python3 -I tools/ui/make_progress_bar.py <kaynak.jpg> <cikis klasoru> [onizleme.png]
"""
import sys
import numpy as np
from PIL import Image

ROW1, ROW9, ROW10, ROW_H = 22, 649, 726, 77     # kare ust kenarlari (piksel) ve yukseklik
X0, X1 = 12, 1018                               # cerceve yatay siniri
CAP = 72                                         # sag basligin genisligi (sol baslik ayna)
OUT_H = 32                                       # cikis yuksekligi (oyunda olceklenir)


def key(rgb):
    """Macenta zemini seffaflastirir, kenardaki pembe sacagi giderir."""
    a = rgb.astype(float)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    magness = np.clip(((r + b) / 2 - g - 60) / 80, 0, 1)        # 1 = saf macenta
    alpha = 1 - magness
    # sacak: macenta katkisini cikar (r,b'yi g'ye dogru cek)
    spill = np.clip((np.minimum(r, b) - g) / 255, 0, 1) * (1 - alpha)
    r2 = r - (r - g) * np.clip(magness * 1.2, 0, 1)
    b2 = b - (b - g) * np.clip(magness * 1.2, 0, 1)
    out = np.dstack([r2, g, b2, alpha * 255]).clip(0, 255).astype(np.uint8)
    return out


def main():
    src, out_dir = sys.argv[1], sys.argv[2]
    im = np.asarray(Image.open(src).convert('RGB'))
    row1 = key(im[ROW1:ROW1 + ROW_H, X0:X1])
    W = row1.shape[1]
    right = row1[:, W - CAP:]
    mid = row1[:, 560:W - CAP]                            # duz bos kanal (devre cizgisi yok)
    track = np.concatenate([right[:, ::-1], mid, right], axis=1)

    def fill_from(top, end_x):
        f = key(im[top:top + ROW_H, X0:X1]).astype(float)
        rgb = f[..., :3]
        r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
        # dolgu: parlak yesil/turkuaz; cerceve rayi (acik gri) ve karanlik kanal disarida
        green = np.clip((g - np.maximum(r, 40) - 25) / 60, 0, 1) * np.clip((g - 90) / 60, 0, 1)
        a = np.zeros(g.shape)
        ys = slice(6, 59)
        a[ys, :end_x - X0 + 1] = 1
        # yuvarlak uclarda sekil maskesi (dolgu rengine gore)
        edge = 34
        a[ys, :edge] = green[ys, :edge]
        a[ys, end_x - X0 - edge:end_x - X0 + 1] = np.maximum(green[ys, end_x - X0 - edge:end_x - X0 + 1], 0)
        a = np.minimum(a, f[..., 3] / 255)
        f[..., 3] = a * 255
        return f[:, :end_x - X0 + 8].clip(0, 255).astype(np.uint8)

    fill = fill_from(ROW9, 894)
    full = fill_from(ROW10, 1012)

    def save(arr, name):
        img = Image.fromarray(arr, 'RGBA')
        w = round(img.width * OUT_H / img.height)
        img.resize((w, OUT_H), Image.LANCZOS).save(f'{out_dir}/{name}', optimize=True)
        return img.resize((w, OUT_H), Image.LANCZOS)

    t = save(track, 'bar_track.png'); f = save(fill, 'bar_fill.png'); u = save(full, 'bar_fill_full.png')
    print('track', t.size, 'fill', f.size, 'full', u.size, 'cap', round(CAP * OUT_H / ROW_H))
    if len(sys.argv) > 3:
        prev = Image.new('RGBA', (t.width, OUT_H * 3 + 8), (40, 40, 40, 255))
        prev.alpha_composite(t, (0, 0)); prev.alpha_composite(t, (0, OUT_H + 4)); prev.alpha_composite(f, (0, OUT_H + 4))
        prev.alpha_composite(t, (0, 2 * OUT_H + 8)); prev.alpha_composite(u, (0, 2 * OUT_H + 8))
        prev.resize((prev.width * 2, prev.height * 2)).save(sys.argv[3])


if __name__ == '__main__':
    main()
