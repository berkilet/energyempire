package com.energytycoon.render;

import com.badlogic.gdx.Gdx;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * tools/nature/convert_nature.py ile donusturulmus doga modeli (models/nature2/*.bin).
 * Ucgen basina tur: 0 opak (kose rengi dokudan orneklenmis), 1 yaprak kumesi (atlas hucresi damgada secilir),
 * 2 cam yapragi, 3 bitki yapraklari, 4 cicek. Kesimli turlerde u,v hucre icinde [0,1].
 */
public final class NatureTemplate {
    public static final String DIR = "models/nature2/";
    /** foliage_atlas.png hucreleri (4x2, her biri 512 px). */
    public static final int CELL_LEAF = 0, CELL_LEAF_DENSE = 1, CELL_LEAF_AUTUMN = 2, CELL_PINE = 3, CELL_PLANTS = 4, CELL_FLOWERS = 5;

    public final int tris, verts;
    public final byte[] kind;
    /** Kose basina 11 float: x,y,z, nx,ny,nz, r,g,b, u,v. */
    public final float[] data;
    /** Ucgen basina 3 kose indeksi. */
    public final short[] index;
    public final float minY, maxY;

    private static final Map<String, NatureTemplate> CACHE = new HashMap<>();

    public static synchronized NatureTemplate get(String name) {
        return CACHE.computeIfAbsent(name, NatureTemplate::new);
    }

    private NatureTemplate(String name) {
        try (InputStream raw = Gdx.files.classpath(DIR + name + ".bin").read();
             DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(raw)))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'N' || magic[1] != 'T' || magic[2] != 'R' || magic[3] != '2') throw new IOException("bicim hatali: " + name);
            verts = in.readInt();
            data = new float[verts * 11];
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (int v = 0; v < verts; v++) {
                for (int k = 0; k < 11; k++) data[v * 11 + k] = in.readFloat();
                lo = Math.min(lo, data[v * 11 + 1]); hi = Math.max(hi, data[v * 11 + 1]);
            }
            tris = in.readInt();
            kind = new byte[tris];
            index = new short[tris * 3];
            for (int t = 0; t < tris; t++) {
                kind[t] = in.readByte();
                for (int k = 0; k < 3; k++) index[t * 3 + k] = in.readShort();
            }
            minY = lo; maxY = hi;
        } catch (IOException e) {
            throw new RuntimeException("Doga modeli okunamadi: " + name, e);
        }
    }

    public float height() { return Math.max(1e-3f, maxY - minY); }

    /** Bir turun atlas hucresi; yaprak kumesi icin damgada secilen hucre kullanilir. */
    public static int cellFor(int kind, int leafCell) {
        switch (kind) {
            case 1: return leafCell;
            case 2: return CELL_PINE;
            case 3: return CELL_PLANTS;
            default: return CELL_FLOWERS;
        }
    }
}
