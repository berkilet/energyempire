package com.energytycoon.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import com.energytycoon.world.Noise;
import com.energytycoon.world.WorldMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Arazi: tile sutunlari (koselerde AO, katmanli kayaliklar), ayri su yuzeyi (derinlik rengi, kiyi kopugu,
 * hareketli dalgaciklar) ve parcaya pisirilmis bitki ortusu (Kenney Nature Kit agac/cali/cim/kaya).
 */
public final class TerrainRenderer implements Disposable {
    public enum Layer {
        NORMAL("Normal"), WIND("Rüzgar"), SUN("Güneş"), COAL("Kömür"), GAS("Doğalgaz"), WATER("Su");
        public final String label;
        Layer(String l) { label = l; }
    }

    private static final int CHUNK = 16;
    /** Kisa indeksli mesh sinirinin altinda guvenli kose sayisi; asilinca yeni mesh parcasina gecilir. */
    private static final int MAX_VERTS = 60000;
    private static final String NAT = "models/nature/";

    private final WorldMap map;
    private final int[] occ;
    /** Agac konmayacak kareler (sehir cevresi vb.). */
    public final boolean[] reserved;
    /** Gorsel yol agi (WorldRenderer kurar); null ise yol cizilmez. */
    public RoadNetwork roads;
    private byte[] roadSnapshot;
    private final int chunksX;
    private final Model[] ground, water, ripples, foliage;
    private final ModelInstance[] groundInst, waterInst, rippleInst, foliageInst;
    /** Golge ve ana geciste cizilen arazi + su ornekleri. */
    public final Array<ModelInstance> instances = new Array<>();
    private Layer layer = Layer.NORMAL;
    private final int[] occSnapshot;
    private final boolean[] resSnapshot;
    private final Noise forest, macro;
    /** Su kareleri icin en yakin karaya uzaklik (kare). */
    private final int[] waterDist;
    /** Bitki ortusu yogunlugu: 0 az, 1 normal, 2 yogun. */
    private int detail = defaultDetail;
    /** Yeni olusturulan arazilerin bitki yogunlugu (ayarlardan). */
    public static int defaultDetail = 1;
    private final Material waterMat = new Material("water", ColorAttribute.createDiffuse(Color.WHITE));
    private final Material rippleMat = new Material("ripple", ColorAttribute.createDiffuse(Color.WHITE));
    /** Alfa kesimli yaprak/cicek atlasi (doga paketi); golge gecisinde de kesilir (BlendingAttribute + doku). */
    private final Material foliageMat;
    private com.badlogic.gdx.graphics.Texture foliageTex;

    private Material foliageMaterial() {
        foliageTex = new com.badlogic.gdx.graphics.Texture(Gdx.files.classpath(NatureTemplate.DIR + "foliage_atlas.png"), true);
        foliageTex.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.MipMapLinearLinear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
        return new Material("foliage", ColorAttribute.createDiffuse(Color.WHITE),
            com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.createDiffuse(foliageTex),
            com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.createAlphaTest(0.42f),
            com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.createCullFace(GL20.GL_NONE),
            // harmanlama etkisiz (ONE, ZERO): ana geciste opak cizilir, derinlik gecisi ise bu ozellik sayesinde alfa keser
            new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(false, GL20.GL_ONE, GL20.GL_ZERO, 1f));
    }

    public TerrainRenderer(WorldMap map, int[] occ) {
        this.map = map; this.occ = occ;
        this.reserved = new boolean[map.size * map.size];
        this.chunksX = (map.size + CHUNK - 1) / CHUNK;
        int n = chunksX * chunksX;
        ground = new Model[n]; water = new Model[n]; ripples = new Model[n]; foliage = new Model[n];
        groundInst = new ModelInstance[n]; waterInst = new ModelInstance[n]; rippleInst = new ModelInstance[n]; foliageInst = new ModelInstance[n];
        foliageMat = foliageMaterial();
        this.occSnapshot = new int[occ.length];
        this.resSnapshot = new boolean[occ.length];
        this.forest = new Noise(map.seed + 1234);
        this.macro = new Noise(map.seed + 4321);
        this.waterDist = computeWaterDistance();
        rebuild();
    }

    public Layer layer() { return layer; }
    public void setLayer(Layer l) { if (l != layer) { layer = l; rebuild(); } }
    public void setDetail(int d) { d = MathUtils.clamp(d, 0, 2); if (d != detail) { detail = d; rebuild(); } }

    public void rebuild() {
        for (int c = 0; c < ground.length; c++) buildChunk(c);
        System.arraycopy(occ, 0, occSnapshot, 0, occ.length);
        System.arraycopy(reserved, 0, resSnapshot, 0, occ.length);
        snapRoads();
        refreshInstances();
    }

    private void snapRoads() {
        if (roads == null) return;
        if (roadSnapshot == null) roadSnapshot = new byte[occ.length];
        System.arraycopy(roads.links, 0, roadSnapshot, 0, occ.length);
    }

    private boolean roadChanged(int i) {
        if (roads == null) return false;
        return roadSnapshot == null || roads.links[i] != roadSnapshot[i];
    }

    /** Doluluk/rezerv degisen chunk'lari yeniden kurar. */
    public void refreshOccupancy() {
        boolean[] dirty = new boolean[ground.length];
        boolean any = false;
        for (int i = 0; i < occ.length; i++) {
            if ((occ[i] >= 0) != (occSnapshot[i] >= 0) || reserved[i] != resSnapshot[i] || roadChanged(i)) {
                int x = i % map.size, y = i / map.size;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int cx = (x + dx) / CHUNK, cy = (y + dy) / CHUNK;
                    if (x + dx >= 0 && y + dy >= 0 && cx < chunksX && cy < chunksX) dirty[cy * chunksX + cx] = true;
                }
                any = true;
            }
        }
        if (!any) return;
        for (int c = 0; c < ground.length; c++) if (dirty[c]) buildChunk(c);
        System.arraycopy(occ, 0, occSnapshot, 0, occ.length);
        System.arraycopy(reserved, 0, resSnapshot, 0, occ.length);
        snapRoads();
        refreshInstances();
    }

    private void refreshInstances() {
        instances.clear();
        for (ModelInstance mi : groundInst) if (mi != null) instances.add(mi);
        for (ModelInstance mi : waterInst) if (mi != null) instances.add(mi);
        for (ModelInstance mi : rippleInst) if (mi != null) instances.add(mi);
        for (ModelInstance mi : foliageInst) if (mi != null) instances.add(mi);
    }

    /** Su yuzeyi hafifce parlar-soner, dalgaciklar yavasca kayar. */
    public void animate(float time) {
        float k = 1f + 0.035f * MathUtils.sin(time * 0.9f);
        ((ColorAttribute) waterMat.get(ColorAttribute.Diffuse)).color.set(k, k, Math.min(1.2f, k * 1.01f), 1);
        float ox = 0.05f * MathUtils.sin(time * 0.55f), oz = 0.05f * MathUtils.cos(time * 0.43f);
        for (ModelInstance mi : rippleInst) if (mi != null) mi.transform.setToTranslation(ox, 0, oz);
    }

    // ------------------------------------------------------------ mesh biriktirici (otomatik bolme)
    private static final class Accum {
        final FloatArray v = new FloatArray();
        final ShortArray idx = new ShortArray();
        final List<Mesh> meshes = new ArrayList<>();
        /** true: kose basina doku koordinati da tutulur (12 float). */
        final boolean uv;
        final int stride;

        Accum() { this(false); }
        Accum(boolean uv) { this.uv = uv; this.stride = uv ? 12 : 10; }

        int base() { return v.size / stride; }
        void reserve(int verts) { if (base() + verts > MAX_VERTS) flush(); }
        void vert(float x, float y, float z, float nx, float ny, float nz, float r, float g, float b) {
            v.add(x); v.add(y); v.add(z); v.add(nx); v.add(ny); v.add(nz); v.add(r); v.add(g); v.add(b); v.add(1f);
            if (uv) { v.add(0f); v.add(0f); }
        }
        void vertUV(float x, float y, float z, float nx, float ny, float nz, float r, float g, float b, float u, float vv) {
            v.add(x); v.add(y); v.add(z); v.add(nx); v.add(ny); v.add(nz); v.add(r); v.add(g); v.add(b); v.add(1f); v.add(u); v.add(vv);
        }
        void flush() {
            if (v.size == 0) return;
            VertexAttributes attrs = uv
                ? new VertexAttributes(VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.ColorUnpacked(), VertexAttribute.TexCoords(0))
                : new VertexAttributes(VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.ColorUnpacked());
            Mesh mesh = new Mesh(true, v.size / stride, idx.size, attrs);
            mesh.setVertices(v.items, 0, v.size);
            mesh.setIndices(idx.items, 0, idx.size);
            meshes.add(mesh);
            v.clear(); idx.clear();
        }
        Model build(Material mat) {
            flush();
            if (meshes.isEmpty()) return null;
            ModelBuilder mb = new ModelBuilder();
            mb.begin();
            for (int i = 0; i < meshes.size(); i++) mb.part("p" + i, meshes.get(i), GL20.GL_TRIANGLES, mat);
            Model m = mb.end();
            for (Mesh me : meshes) m.manageDisposable(me);
            meshes.clear();
            return m;
        }
    }

    private Accum g, w, rp, fol;

    private void buildChunk(int c) {
        if (ground[c] != null) ground[c].dispose();
        if (water[c] != null) water[c].dispose();
        if (ripples[c] != null) ripples[c].dispose();
        if (foliage[c] != null) foliage[c].dispose();
        ground[c] = water[c] = ripples[c] = foliage[c] = null;
        groundInst[c] = waterInst[c] = rippleInst[c] = foliageInst[c] = null;
        int cx0 = (c % chunksX) * CHUNK, cy0 = (c / chunksX) * CHUNK;
        g = new Accum(); w = new Accum(); rp = new Accum(); fol = new Accum(true);
        for (int y = cy0; y < Math.min(map.size, cy0 + CHUNK); y++) {
            for (int x = cx0; x < Math.min(map.size, cx0 + CHUNK); x++) {
                int i = map.idx(x, y);
                float t = map.top[i];
                Color col = tileColor(x, y);
                if (map.water[i] && layer == Layer.NORMAL) {
                    waterTop(x, y, t, col);
                } else {
                    float a00 = ao(x, y, -1, -1, t), a01 = ao(x, y, -1, 1, t), a11 = ao(x, y, 1, 1, t), a10 = ao(x, y, 1, -1, t);
                    g.reserve(4);
                    quad(g, x, t, y, x, t, y + 1, x + 1, t, y + 1, x + 1, t, y, 0, 1, 0, col, a00, a01, a11, a10);
                }
                wall(x, y, 1, 0, t); wall(x, y, -1, 0, t); wall(x, y, 0, 1, t); wall(x, y, 0, -1, t);
                if (layer == Layer.NORMAL) decorate(x, y, i, t);
                if (layer == Layer.NORMAL && roads != null && roads.road(i)) roadTile(x, y, i);
            }
        }
        ground[c] = g.build(new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        water[c] = w.build(waterMat);
        ripples[c] = rp.build(rippleMat);
        foliage[c] = fol.build(foliageMat);
        if (foliage[c] != null) foliageInst[c] = new ModelInstance(foliage[c]);
        if (ground[c] != null) groundInst[c] = new ModelInstance(ground[c]);
        if (water[c] != null) waterInst[c] = new ModelInstance(water[c]);
        if (ripples[c] != null) rippleInst[c] = new ModelInstance(ripples[c]);
        g = w = rp = fol = null;
    }

    // ------------------------------------------------------------ su
    private int[] computeWaterDistance() {
        int n = map.size * map.size;
        int[] d = new int[n];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            if (!map.water[i]) { d[i] = 0; q.add(i); } else d[i] = Integer.MAX_VALUE;
        }
        while (!q.isEmpty()) {
            int i = q.poll(), x = i % map.size, y = i / map.size;
            int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
            for (int[] dd : dirs) {
                int nx = x + dd[0], ny = y + dd[1];
                if (!map.inBounds(nx, ny)) continue;
                int j = map.idx(nx, ny);
                if (d[j] > d[i] + 1) { d[j] = d[i] + 1; q.add(j); }
            }
        }
        return d;
    }

    private static final Color FOAM = new Color(0.66f, 0.82f, 0.86f, 1);

    /** Su yuzeyi: koselerde karaya bitisiklik varsa kopuk rengi; rastgele karelere dalgacik seridi. */
    private void waterTop(int x, int y, float t, Color col) {
        float f00 = foam(x, y), f01 = foam(x, y + 1), f11 = foam(x + 1, y + 1), f10 = foam(x + 1, y);
        w.reserve(4);
        int b = w.base();
        vertC(w, x, t, y, col, f00); vertC(w, x, t, y + 1, col, f01); vertC(w, x + 1, t, y + 1, col, f11); vertC(w, x + 1, t, y, col, f10);
        w.idx.add(b); w.idx.add(b + 1); w.idx.add(b + 2); w.idx.add(b); w.idx.add(b + 2); w.idx.add(b + 3);
        float h = hash(x * 5 + 3, y * 7 + 1);
        if (h < 0.35f && waterDist[map.idx(x, y)] >= 1) {
            MeshTemplate rip = MeshTemplate.get(NAT + "low-poly-nature-water-tile.obj");
            // Innerscene su karosu 2x2; 0.5 olcekle tek kareye, yalnizca dalgacik seritleri (Kit_glass).
            stamp(rp, rip, x + 0.5f, t - 0.2f * 0.5f + 0.003f, y + 0.5f, 90f * (int) (h * 40 % 4), 0.5f, 0.5f, 0.5f,
                mat -> mat.equals("Kit_glass") ? RIPPLE : null, 1f);
        }
    }

    private static final Color RIPPLE = new Color(0.58f, 0.78f, 0.84f, 1);

    private void vertC(Accum a, float x, float y, float z, Color c, float foam) {
        float r = MathUtils.lerp(c.r, FOAM.r, foam), gg = MathUtils.lerp(c.g, FOAM.g, foam), bb = MathUtils.lerp(c.b, FOAM.b, foam);
        a.vert(x, y, z, 0, 1, 0, r, gg, bb);
    }

    /** Kose (cx,cy) kara karesine degiyorsa kopuk miktari. */
    private float foam(int cx, int cy) {
        int land = 0;
        for (int dy = -1; dy <= 0; dy++) for (int dx = -1; dx <= 0; dx++) {
            int x = cx + dx, y = cy + dy;
            if (map.inBounds(x, y) && !map.water[map.idx(x, y)]) land++;
        }
        return land == 0 ? 0 : 0.14f + 0.06f * land;
    }

    // ------------------------------------------------------------ kayaliklar
    /** Kose AO: kosedeki 3 komsudan yuksek olanlar karartir. */
    private float ao(int x, int y, int sx, int sy, float t) {
        int n = 0;
        if (higher(x + sx, y, t)) n++;
        if (higher(x, y + sy, t)) n++;
        if (higher(x + sx, y + sy, t)) n++;
        return 1f - 0.12f * n;
    }

    private boolean higher(int x, int y, float t) {
        return map.inBounds(x, y) && map.top[map.idx(x, y)] > t + 0.05f;
    }

    /** Duvar: arazi kademesi basina bir bant; bantlar hafif farkli tonda (tortul katman gorunumu). */
    private void wall(int x, int y, int dx, int dy, float t) {
        int nx = x + dx, ny = y + dy;
        float nt = map.inBounds(nx, ny) ? map.top[map.idx(nx, ny)] : 0f;
        if (nt >= t - 0.001f) return;
        int i = map.idx(x, y);
        float side = dx != 0 ? 0.80f : 0.90f;
        float nxn = dx, nzn = dy;
        float y0 = nt;
        int band = 0;
        while (y0 < t - 0.001f) {
            float bandTop = (float) Math.floor((y0 - WorldMap.LAND_BASE) / WorldMap.LEVEL_H + 1e-4) * WorldMap.LEVEL_H + WorldMap.LAND_BASE + WorldMap.LEVEL_H;
            if (bandTop <= y0 + 1e-4f) bandTop = y0 + WorldMap.LEVEL_H;
            float y1 = Math.min(t, Math.max(bandTop, y0 + 0.02f));
            int lvBand = (int) Math.floor((y0 - WorldMap.LAND_BASE) / WorldMap.LEVEL_H + 0.5f);
            Color wc = wallColor(i, lvBand, y1 >= t - 0.001f);
            float lo = 0.72f + 0.26f * ((y0 - nt) / Math.max(0.01f, t - nt));
            float hi = 0.72f + 0.26f * ((y1 - nt) / Math.max(0.01f, t - nt));
            lo *= side; hi *= side;
            g.reserve(4);
            if (dx == 1) quad(g, x + 1, y0, y, x + 1, y1, y, x + 1, y1, y + 1, x + 1, y0, y + 1, nxn, 0, nzn, wc, lo, hi, hi, lo);
            else if (dx == -1) quad(g, x, y0, y + 1, x, y1, y + 1, x, y1, y, x, y0, y, nxn, 0, nzn, wc, lo, hi, hi, lo);
            else if (dy == 1) quad(g, x + 1, y0, y + 1, x + 1, y1, y + 1, x, y1, y + 1, x, y0, y + 1, nxn, 0, nzn, wc, lo, hi, hi, lo);
            else quad(g, x, y0, y, x, y1, y, x + 1, y1, y, x + 1, y0, y, nxn, 0, nzn, wc, lo, hi, hi, lo);
            y0 = y1;
            if (++band > 40) break;
        }
    }

    private Color wallColor(int i, int lvBand, boolean topBand) {
        if (layer != Layer.NORMAL) return new Color(0.18f, 0.19f, 0.20f, 1);
        if (map.water[i]) return new Color(0.10f, 0.26f, 0.42f, 1);
        float stripe = (Math.floorMod(lvBand * 7 + (int) (map.seed & 3), 3) == 0) ? 0.90f : (lvBand % 2 == 0 ? 1.0f : 0.95f);
        float hl = Math.min(1, lvBand / (float) Math.max(8, map.preset.relief));
        Color c;
        if (map.preset.desert) c = new Color(0.78f + 0.06f * hl, 0.56f + 0.05f * hl, 0.36f + 0.04f * hl, 1);
        else if (hl > 0.6f) c = new Color(0.50f, 0.47f, 0.43f, 1);
        else c = new Color(0.50f + 0.06f * hl, 0.39f + 0.05f * hl, 0.27f + 0.06f * hl, 1);
        c.mul(stripe, stripe, stripe, 1);
        // en ust bant: cim saçagi hissi icin hafif yesile
        if (topBand && !map.preset.desert && hl < 0.7f) c.lerp(0.36f, 0.48f, 0.22f, 1, 0.35f);
        return c;
    }

    // ------------------------------------------------------------ zemin renkleri
    private static float hash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    /** Innerscene "Kit grass" ana rengi; genis alanli gurultuyle kuru/canli lekeler. */
    private static final Color GRASS = new Color(0.47f, 0.62f, 0.24f, 1);
    private static final Color GRASS_DRY = new Color(0.60f, 0.63f, 0.30f, 1);
    private static final Color GRASS_LUSH = new Color(0.32f, 0.54f, 0.20f, 1);
    /** Innerscene su renkleri: sig (Kit glass) ve derin (Kit water, koyulastirilmis). */
    private static final Color SHALLOW = new Color(0.22f, 0.53f, 0.62f, 1);
    private static final Color DEEP = new Color(0.05f, 0.22f, 0.42f, 1);

    private Color tileColor(int x, int y) {
        int i = map.idx(x, y);
        float n = hash(x, y);
        if (layer != Layer.NORMAL) return overlayColor(i);
        if (map.water[i]) {
            if (map.river[i]) return new Color(0.17f, 0.45f + 0.03f * n, 0.60f, 1);
            if (!map.isSea(i)) return new Color(0.12f, 0.38f + 0.03f * n, 0.54f, 1);
            float d = MathUtils.clamp((waterDist[i] - 1) / 6f, 0, 1);
            Color c = new Color(SHALLOW).lerp(DEEP, d);
            return c.add(0.015f * n, 0.015f * n, 0.02f * n, 0);
        }
        int lv = map.level[i];
        int relief = Math.max(8, map.preset.relief);
        float rl = lv / (float) relief;
        Color c;
        boolean coast = nearWater(x, y);
        float m = (float) macro.fbm(x * 0.045, y * 0.045, 3);
        if (map.preset.desert) {
            if (coast && lv <= 1) c = new Color(0.90f, 0.84f, 0.62f, 1);
            else c = new Color(0.86f + 0.04f * n - rl * 0.08f, 0.70f + 0.04f * n - rl * 0.10f, 0.46f + 0.03f * n - rl * 0.08f, 1);
            c.lerp(0.80f, 0.60f, 0.40f, 1, MathUtils.clamp((m - 0.5f) * 1.5f, 0, 0.4f));
            if (nearRiver(x, y)) c.lerp(GRASS, 0.65f);
        } else {
            if (lv <= 1 && coast && !nearRiver(x, y)) c = new Color(0.88f, 0.82f, 0.60f, 1);
            else if (rl < 0.55f) {
                c = new Color(GRASS);
                if (m > 0.5f) c.lerp(GRASS_DRY, MathUtils.clamp((m - 0.5f) * 3f, 0, 0.8f));
                else c.lerp(GRASS_LUSH, MathUtils.clamp((0.5f - m) * 3f, 0, 0.8f));
                c.mul(0.96f + 0.08f * n, 0.96f + 0.08f * n, 0.96f + 0.08f * n, 1).lerp(0.30f, 0.44f, 0.22f, 1, rl * 0.4f);
            } else if (rl < 0.8f) c = new Color(0.33f + 0.04f * n, 0.45f + 0.04f * n, 0.24f, 1).lerp(0.48f, 0.46f, 0.40f, 1, (rl - 0.55f) * 2f);
            else if (lv < 19 || rl < 0.95f) c = new Color(0.52f + 0.05f * n, 0.50f + 0.05f * n, 0.45f + 0.04f * n, 1);
            else c = new Color(0.93f, 0.94f, 0.97f, 1);
        }
        float coal = map.coal[i], gas = map.gas[i];
        // Yataklar normal gorunumde yalnizca hafif ipucu; ayrintisi Komur/Dogalgaz katmanlarinda.
        if (coal > 0.05f) c.lerp(0.30f, 0.28f, 0.26f, 1, 0.22f * coal);
        if (gas > 0.05f) c.lerp(0.78f, 0.62f, 0.30f, 1, 0.16f * gas);
        return c;
    }

    private boolean nearWater(int x, int y) {
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
            if (map.inBounds(x + dx, y + dy) && map.water[map.idx(x + dx, y + dy)]) return true;
        return false;
    }

    private boolean nearRiver(int x, int y) {
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
            if (map.inBounds(x + dx, y + dy) && map.river[map.idx(x + dx, y + dy)]) return true;
        return false;
    }

    private Color overlayColor(int i) {
        float val; float r, gg, b;
        switch (layer) {
            case WIND: val = (map.wind[i] - 0.4f) / 1.0f; r = 0.25f; gg = 0.85f; b = 0.95f; break;
            case SUN: val = (map.sun[i] - 0.6f) / 0.5f; r = 1f; gg = 0.85f; b = 0.1f; break;
            case COAL: val = map.coal[i]; r = 0.75f; gg = 0.5f; b = 0.3f; break;
            case GAS: val = map.gas[i]; r = 1f; gg = 0.55f; b = 0.1f; break;
            default: val = map.hydro[i]; r = 0.2f; gg = 0.6f; b = 1f; break;
        }
        val = Math.max(0, Math.min(1, val));
        if (map.water[i] && layer != Layer.WATER) return new Color(0.16f, 0.18f, 0.24f, 1);
        if (map.water[i]) return new Color(0.2f, 0.45f, 0.9f, 1);
        return new Color(0.21f, 0.23f, 0.22f, 1).lerp(r, gg, b, 1, val);
    }

    // ------------------------------------------------------------ bitki ortusu (Kenney Nature Kit)
    private static final float[] DENSITY = { 0.45f, 1.0f, 1.6f };

    /** Gelistirici vitrini (ET_NATSHOW): bu karelerden baslayarak her doga modeli kendi karesine konur. */
    public static int[] showcase;
    private static final String[] SHOW = { "CommonTree_1", "CommonTree_2", "CommonTree_3", "CommonTree_4", "CommonTree_5", "Pine_1", "Pine_2", "Pine_3", "Pine_4", "Pine_5",
        "TwistedTree_1", "TwistedTree_3", "TwistedTree_5", "DeadTree_1", "DeadTree_2", "DeadTree_3", "Bush_Common", "Bush_Common_Flowers", "Fern_1", "Clover_1",
        "Clover_2", "Plant_1", "Plant_1_Big", "Plant_7", "Plant_7_Big", "Flower_3_Group", "Flower_3_Single", "Flower_4_Single", "Grass_Common_Short", "Grass_Common_Tall",
        "Grass_Wispy_Short", "Grass_Wispy_Tall", "Mushroom_Common", "Rock_Medium_1", "Rock_Medium_2", "Rock_Medium_3", "Rock_Medium_1_Desert" };

    private boolean showcaseTile(int x, int y, float t) {
        if (showcase == null) return false;
        int dx = x - showcase[0], dy = y - showcase[1];
        if (dx < 0 || dy < 0 || dx >= 8 || dy >= 5) return false;
        int k = dy * 8 + dx;
        if (k >= SHOW.length) return true;
        String nm = SHOW[k];
        float h = nm.contains("Tree") ? 0.45f : nm.startsWith("Pine") ? 0.46f : nm.startsWith("Rock") ? 0.2f : nm.startsWith("Bush") ? 0.16f : nm.startsWith("Grass") ? 0.1f : nm.startsWith("Mushroom") ? 0.05f : 0.08f;
        stampNature(nm, x + 0.5f, t, y + 0.5f, 30f, h, 1f, NatureTemplate.CELL_LEAF, WHITE, 0.8f);
        return true;
    }

    private void decorate(int x, int y, int i, float t) {
        if (showcaseTile(x, y, t)) return;
        if (map.water[i] || occ[i] >= 0 || reserved[i]) return;
        int relief = Math.max(8, map.preset.relief);
        float rl = map.level[i] / (float) relief;
        float mult = DENSITY[detail];
        double f = forest.fbm(x * 0.09, y * 0.09, 3);
        // cicekli cayir lekeleri ve sonbahar koruluklari icin dusuk frekansli gurultu
        double meadow = macro.fbm(x * 0.06 + 40, y * 0.06 - 17, 2);
        float n = hash(x * 3 + 1, y * 7 + 2), n2 = hash(x * 11 + 5, y * 3 + 9), n3 = hash(x * 17 + 3, y * 5 + 11);
        if (map.preset.desert) {
            if (nearRiver(x, y) && n < 0.6f) { palm(x + 0.3f + n * 0.4f, t, y + 0.5f); if (n3 < 0.35f) natPlant(x, y, t, n2, n3); }
            else if (n2 < 0.07f * mult) { if (n3 < 0.5f) natRock(x, y, t, n, true); else rock(x, y, t, n, true); }
            else if (n2 > 1 - 0.012f * mult) deadTree(x, y, t, n, n3);
            return;
        }
        if (map.level[i] <= 1 && nearWater(x, y)) {           // kumsal: arada bir kaya
            if (n2 < 0.05f * mult) rock(x, y, t, n, false);
            return;
        }
        if (rl > 0.82f) {                                       // dag: kayalar, tek tuk kurumus agac
            if (n2 < 0.22f * mult && map.level[i] < WorldMap.MAX_LEVEL - 1) { if (n3 < 0.5f) natRock(x, y, t, n, false); else rock(x, y, t, n, false); }
            else if (n2 > 1 - 0.03f * mult) deadTree(x, y, t, n, n3);
            return;
        }
        boolean riverBank = nearRiver(x, y);
        double density = (f - 0.48) * 4.0;
        if (n < density) {                                      // orman
            int count = 1 + (int) (hash(x, y * 5) * 2 * Math.min(1, density) * Math.min(1.4f, mult));
            boolean autumnGrove = meadow > 0.66;
            for (int k = 0; k < count; k++) {
                float ox = 0.22f + hash(x * 11 + k, y) * 0.56f, oz = 0.22f + hash(x, y * 13 + k) * 0.56f;
                float s = 0.85f + hash(x + k * 7, y + k * 3) * 0.35f;
                float hn = hash(k + x, y - k), pick = hash(x * 7 - k, y * 3 + k);
                if (rl > 0.5f && hn < 0.65f) {
                    if (pick < 0.5f) natPine(x + ox, t, y + oz, s, hn); else conifer(x + ox, t, z(y, oz), s, hn);
                } else if (autumnGrove && pick < 0.45f) natTwisted(x + ox, t, y + oz, s, hn);
                else if (pick < 0.45f) natTree(x + ox, t, y + oz, s, hn);
                else kenneyTree(x + ox, t, y + oz, s, hn);
            }
            if (n2 < 0.45f * mult) {
                if (n3 < 0.5f) natBush(x + 0.15f + n2 * 0.7f, t, y + 0.2f + n * 0.6f, n2, autumnGrove);
                else bush(x + 0.15f + n2 * 0.7f, t, y + 0.2f + n * 0.6f, n2);
            }
            // orman tabani: egrelti, mantar
            if (n3 < 0.22f * mult) natUnderstory(x, y, t, n, n3);
        } else {                                                // acik cayir: cim kumeleri, cicekler, cali, tas
            if (n2 < 0.32f * mult) { if (n3 < 0.5f) natGrass(x, y, t, n, n2); else grassTuft(x, y, t, n, n2); }
            if (meadow > 0.58 && n3 < (meadow - 0.58) * 3.0 * mult) natFlowers(x, y, t, n, n3);
            if (riverBank && n3 > 0.55f) natPlant(x, y, t, n2, n3);
            if (n2 > 1 - 0.05f * mult) { if (n < 0.5f) natBush(x + 0.3f + n * 0.4f, t, y + 0.3f + n2 * 0.4f, n, false); else bush(x + 0.3f + n * 0.4f, t, y + 0.3f + n2 * 0.4f, n); }
            if (n > 1 - 0.025f * mult) { if (n3 < 0.5f) natRock(x, y, t, n2, false); else rock(x, y, t, n2, false); }
        }
    }

    // ---- doga paketi yerlesimleri (boyutlar dunya biriminde; agaclar Kenney agaclariyla ayni boyda)
    private static final Color WHITE = new Color(1, 1, 1, 1);
    private final Color natTint = new Color();

    private Color leafTint(float n) { return natTint.set(0.92f + 0.12f * n, 0.95f + 0.08f * n, 0.90f, 1); }

    private void natTree(float x, float y, float z, float s, float n) {
        String[] m = { "CommonTree_1", "CommonTree_2", "CommonTree_3", "CommonTree_4", "CommonTree_5" };
        stampNature(m[(int) (n * 97) % m.length], x, y, z, n * 720f, 0.44f * s, 1f, n < 0.5f ? NatureTemplate.CELL_LEAF : NatureTemplate.CELL_LEAF_DENSE, leafTint(n), 0.8f);
    }

    private void natPine(float x, float y, float z, float s, float n) {
        String[] m = { "Pine_1", "Pine_2", "Pine_3", "Pine_4", "Pine_5" };
        stampNature(m[(int) (n * 89) % m.length], x, y, z, n * 640f, 0.46f * s, 0.9f, 0, leafTint(n), 0.8f);
    }

    private void natTwisted(float x, float y, float z, float s, float n) {
        String[] m = { "TwistedTree_1", "TwistedTree_3", "TwistedTree_5" };
        stampNature(m[(int) (n * 61) % m.length], x, y, z, n * 500f, 0.50f * s, 0.85f, n < 0.4f ? NatureTemplate.CELL_LEAF_AUTUMN : NatureTemplate.CELL_LEAF, leafTint(n), 0.8f);
    }

    private void deadTree(int x, int y, float t, float n, float n2) {
        String[] m = { "DeadTree_1", "DeadTree_2", "DeadTree_3" };
        stampNature(m[(int) (n2 * 31) % m.length], x + 0.3f + 0.4f * n, t, y + 0.3f + 0.4f * n2, n * 800f, 0.34f + 0.1f * n2, 0.8f, 0,
            map.preset.desert ? natTint.set(1.15f, 1.05f, 0.95f, 1) : WHITE, 0.8f);
    }

    private void natBush(float x, float y, float z, float n, boolean autumn) {
        boolean flowers = !autumn && n > 0.6f;
        stampNature(flowers ? "Bush_Common_Flowers" : "Bush_Common", x, y, z, n * 900f, 0.13f + 0.06f * n, 1f,
            autumn ? NatureTemplate.CELL_LEAF_AUTUMN : NatureTemplate.CELL_LEAF_DENSE, leafTint(n), 0.85f);
    }

    private void natGrass(int x, int y, float t, float n, float n2) {
        String[] m = { "Grass_Common_Short", "Grass_Common_Tall", "Grass_Wispy_Short", "Grass_Wispy_Tall" };
        // cim dokusu acik serit renkleri; arazi yesiline yaklastir
        Color c = natTint.set(0.62f + 0.1f * n, 0.85f + 0.1f * n2, 0.50f, 1);
        for (int k = 0; k < 1 + (n2 < 0.12f ? 1 : 0); k++) {
            float hx = hash(x * 5 + k, y * 9 - k), hz = hash(x * 13 - k, y * 3 + k);
            stampNature(m[(int) ((n + k * 0.37f) * 41) % m.length], x + 0.15f + 0.7f * hx, t, y + 0.15f + 0.7f * hz, hx * 700f, 0.09f + 0.05f * n2, 1f, 0, c, 0.85f);
        }
    }

    private void natFlowers(int x, int y, float t, float n, float n2) {
        String[] m = { "Flower_3_Group", "Flower_3_Single", "Flower_4_Single", "Clover_1", "Clover_2", "Plant_7", "Plant_7_Big" };
        int cnt = 1 + (int) (n2 * 2.5f);
        for (int k = 0; k < cnt; k++) {
            float hx = hash(x * 7 + k * 3, y * 5 - k), hz = hash(x * 3 - k, y * 11 + k * 5), hm = hash(x + k * 13, y * 17 + k);
            String name = m[(int) (hm * 7) % m.length];
            float h = name.startsWith("Plant_7") ? 0.045f : name.startsWith("Clover") ? 0.09f : 0.11f;
            stampNature(name, x + 0.15f + 0.7f * hx, t, y + 0.15f + 0.7f * hz, hm * 600f, h * (0.85f + 0.3f * n), 1f, 0, WHITE, 0.85f);
        }
    }

    private void natPlant(int x, int y, float t, float n, float n2) {
        String name = n < 0.5f ? "Plant_1_Big" : n < 0.8f ? "Plant_1" : "Fern_1";
        stampNature(name, x + 0.25f + 0.5f * n2, t, y + 0.25f + 0.5f * n, n * 720f, name.equals("Fern_1") ? 0.12f : 0.15f, 1f, 0, WHITE, 0.85f);
    }

    private void natUnderstory(int x, int y, float t, float n, float n3) {
        float px = x + 0.2f + 0.6f * n3, pz = y + 0.2f + 0.6f * n;
        if (n < 0.55f) stampNature("Fern_1", px, t, pz, n * 900f, 0.10f + 0.04f * n3, 1f, 0, WHITE, 0.8f);
        else stampNature("Mushroom_Common", px, t, pz, n * 900f, 0.06f, 1f, 0, WHITE, 0.85f);
    }

    private void natRock(int x, int y, float t, float n, boolean desert) {
        String[] m = { "Rock_Medium_1", "Rock_Medium_2", "Rock_Medium_3" };
        String name = m[(int) (n * 53) % m.length] + (desert ? "_Desert" : "");
        stampNature(name, x + 0.5f, t - 0.01f, y + 0.5f, n * 360f, 0.12f + 0.12f * n, 1f, 0, WHITE, 0.8f);
    }

    private static float z(int y, float oz) { return y + oz; }

    private final Color tmpC = new Color();

    /** Kenney renkleri pastel turkuaz; malzeme adina gore arazi paletine eslenir. */
    private Color natureColor(String mat, float n, boolean desert) {
        switch (mat) {
            case "leafsGreen": {
                if (n < 0.06f) return tmpC.set(0.74f, 0.52f, 0.18f, 1);           // seyrek sonbahar agaci
                return tmpC.set(0.24f + 0.10f * n, 0.46f + 0.10f * n, 0.17f + 0.04f * n, 1);
            }
            case "woodBark": return tmpC.set(0.42f, 0.30f, 0.20f, 1);
            case "grass": return tmpC.set(0.33f + 0.08f * n, 0.55f + 0.08f * n, 0.20f, 1);
            case "dirt": return desert ? tmpC.set(0.78f, 0.58f, 0.40f, 1) : tmpC.set(0.52f, 0.44f, 0.34f, 1);
            case "stone": return desert ? tmpC.set(0.82f, 0.66f, 0.48f, 1) : tmpC.set(0.62f, 0.62f, 0.60f, 1);
            case "stoneDark": return tmpC.set(0.48f, 0.48f, 0.47f, 1);
            default: return tmpC.set(0.6f, 0.6f, 0.6f, 1);
        }
    }

    private void kenneyTree(float x, float y, float z, float s, float n) {
        MeshTemplate t = MeshTemplate.get(NAT + (n < 0.5f ? "tree_oak.obj" : "tree_default.obj"));
        float sc = 0.36f * s;
        stamp(g, t, x, y, z, n * 360f, sc, sc * (0.9f + 0.2f * n), sc, mat -> natureColor(mat, n, false), 0.75f);
    }

    private void bush(float x, float y, float z, float n) {
        MeshTemplate t = MeshTemplate.get(NAT + (n < 0.5f ? "plant_bush.obj" : "plant_bushLarge.obj"));
        float sc = 0.55f + 0.25f * n;
        stamp(g, t, x, y, z, n * 720f, sc, sc, sc, mat -> natureColor(mat, n * 0.8f + 0.1f, false), 0.8f);
    }

    private void grassTuft(int x, int y, float t, float n, float n2) {
        String[] names = { "grass.obj", "grass_large.obj", "grass_leafs.obj", "grass_leafsLarge.obj" };
        MeshTemplate tm = MeshTemplate.get(NAT + names[(int) (n * 4) % 4]);
        float sc = 0.45f + 0.25f * n2;
        stamp(g, tm, x + 0.2f + 0.6f * n2, t, y + 0.2f + 0.6f * n, n * 500f, sc, sc * 0.9f, sc, mat -> natureColor(mat, n2, false), 0.85f);
    }

    private void rock(int x, int y, float t, float n, boolean desert) {
        boolean large = n > 0.55f;
        MeshTemplate tm = MeshTemplate.get(NAT + (large ? "rock_largeA.obj" : "rock_smallA.obj"));
        float sc = large ? 0.35f + 0.25f * n : 0.6f + 0.4f * n;
        // Kenney kayalarinin ust kismi "grass" malzemeli; dagda/colde tas rengine boyanir.
        stamp(g, tm, x + 0.5f, t, y + 0.5f, n * 360f, sc, sc * (0.8f + 0.4f * n), sc,
            mat -> mat.equals("grass") ? natureColor(desert ? "stone" : (map.preset.relief > 0 && n > 0.3f ? "grass" : "stone"), n, desert)
                                       : natureColor(desert ? "stone" : mat.equals("dirt") ? "stone" : mat, n, desert), 0.8f);
    }

    /**
     * Sablonu donusturup biriktiriciye ekler. color: malzeme adi -> renk (null = o malzemeyi atla).
     * baseShade: tabandaki koselerin karartma orani (zemine oturma hissi).
     */
    private void stamp(Accum a, MeshTemplate t, float x, float y, float z, float yawDeg, float sx, float sy, float sz,
                       java.util.function.Function<String, Color> color, float baseShade) {
        int nv = t.uMat.length;
        a.reserve(nv);
        float cs = MathUtils.cosDeg(yawDeg), sn = MathUtils.sinDeg(yawDeg);
        float h = Math.max(0.001f, t.maxY - t.minY);
        float[] d = t.uData;
        Color[] matColor = new Color[t.materials.size()];
        for (int m = 0; m < matColor.length; m++) {
            Color c = color.apply(t.materials.get(m));
            matColor[m] = c == null ? null : new Color(c);
        }
        int[] remap = natRemap.length >= nv ? natRemap : (natRemap = new int[nv * 2]);
        java.util.Arrays.fill(remap, 0, nv, -1);
        int tris = t.triangleCount();
        for (int tri = 0; tri < tris; tri++) {
            Color c = matColor[t.triMaterial[tri]];
            if (c == null) continue;
            for (int k = 0; k < 3; k++) {
                int vi = t.uIndex[tri * 3 + k];
                if (remap[vi] < 0) {
                    remap[vi] = a.base();
                    int o = vi * 6;
                    float lx = d[o] * sx, ly = d[o + 1] * sy, lz = d[o + 2] * sz;
                    float wx = lx * cs + lz * sn, wz = -lx * sn + lz * cs;
                    float nx = d[o + 3] * cs + d[o + 5] * sn, nz = -d[o + 3] * sn + d[o + 5] * cs;
                    float shade = MathUtils.lerp(baseShade, 1f, MathUtils.clamp((d[o + 1] - t.minY) / h * 2.5f, 0, 1));
                    a.vert(x + wx, y + ly, z + wz, nx, d[o + 4], nz, c.r * shade, c.g * shade, c.b * shade);
                }
                a.idx.add((short) remap[vi]);
            }
        }
    }

    // ------------------------------------------------------------ doga paketi (models/nature2)
    private static final float ATLAS_INSET_U = 3f / 2048f, ATLAS_INSET_V = 3f / 1024f;

    /**
     * Donusturulmus doga modelini damgalar: opak ucgenler arazi mesh'ine (kose rengi x tint), kesimli ucgenler
     * yaprak atlasli mesh'e. height: modelin dunya birimindeki hedef yuksekligi. Koseler paylasilir (indeksli).
     */
    private void stampNature(String name, float x, float y, float z, float yawDeg, float height, float widthMul,
                             int leafCell, Color tint, float baseShade) {
        NatureTemplate t = NatureTemplate.get(name);
        float s = height / t.height(), sw = s * widthMul;
        float cs = MathUtils.cosDeg(yawDeg), sn = MathUtils.sinDeg(yawDeg);
        float h = t.height();
        g.reserve(t.verts);
        fol.reserve(t.verts);
        float[] d = t.data;
        int[] remap = natRemap.length >= t.verts ? natRemap : (natRemap = new int[t.verts * 2]);
        java.util.Arrays.fill(remap, 0, t.verts, -1);
        for (int tri = 0; tri < t.tris; tri++) {
            int kind = t.kind[tri];
            Accum a = kind == 0 ? g : fol;
            for (int k = 0; k < 3; k++) {
                int vi = t.index[tri * 3 + k];
                if (remap[vi] < 0) {
                    remap[vi] = a.base();
                    int o = vi * 11;
                    float lx = d[o] * sw, ly = (d[o + 1] - t.minY) * s, lz = d[o + 2] * sw;
                    float wx = lx * cs + lz * sn, wz = -lx * sn + lz * cs;
                    float nx = d[o + 3] * cs + d[o + 5] * sn, nz = -d[o + 3] * sn + d[o + 5] * cs;
                    float shade = MathUtils.lerp(baseShade, 1f, MathUtils.clamp((d[o + 1] - t.minY) / h * 2.5f, 0, 1));
                    float r = d[o + 6] * tint.r * shade, gg = d[o + 7] * tint.g * shade, bb = d[o + 8] * tint.b * shade;
                    if (kind == 0) a.vert(x + wx, y + ly, z + wz, nx, d[o + 4], nz, r, gg, bb);
                    else {
                        int cell = NatureTemplate.cellFor(kind, leafCell);
                        float u0 = (cell % 4) * 0.25f + ATLAS_INSET_U, v0 = (cell / 4) * 0.5f + ATLAS_INSET_V;
                        float uw = 0.25f - 2 * ATLAS_INSET_U, vh = 0.5f - 2 * ATLAS_INSET_V;
                        a.vertUV(x + wx, y + ly, z + wz, nx, d[o + 4], nz, r, gg, bb, u0 + d[o + 9] * uw, v0 + d[o + 10] * vh);
                    }
                }
                a.idx.add((short) remap[vi]);
            }
        }
    }

    /** Damgalarda sablon kosesi -> biriktirici kosesi eslemesi (yeniden kullanilan tampon). */
    private int[] natRemap = new int[1024];

    // ------------------------------------------------------------ yollar (Kenney Road Tiles, CC0)
    private static final String RD = "models/roads/roadTile_";
    /** Karo modeli 3x3 birim; cimen ust yuzeyi y=0.55, kaldirim 0.63. Dikey olcek (yol arazinin hafif ustunde). */
    private static final float ROAD_SY = 0.3f, ROAD_GRASS = 0.55f;
    private static final Color ASPH_URBAN = new Color(0.30f, 0.30f, 0.32f, 1), ASPH_RURAL = new Color(0.35f, 0.35f, 0.36f, 1);
    private static final Color WALK = new Color(0.68f, 0.67f, 0.63f, 1), SHOULDER = new Color(0.60f, 0.56f, 0.46f, 1);
    private static final Color CONCRETE = new Color(0.64f, 0.64f, 0.62f, 1), DASH = new Color(0.93f, 0.90f, 0.78f, 1);

    /** Maskeyi saat yonunun tersine 90 derece dondurur (E->N, N->W, W->S, S->E). */
    private static int rotMask(int m) { return (m >> 1) | ((m & 1) << 3); }

    private void roadTile(int x, int y, int i) {
        int m = roads.links[i] & 0xF;
        int cnt = Integer.bitCount(m);
        String name; int base;
        if (cnt == 1) { name = "143"; base = RoadNetwork.E; }
        else if (cnt == 2 && (m == (RoadNetwork.E | RoadNetwork.W) || m == (RoadNetwork.N | RoadNetwork.S))) { name = "160"; base = RoadNetwork.E | RoadNetwork.W; }
        else if (cnt == 2) { name = "155"; base = RoadNetwork.N | RoadNetwork.E; }
        else if (cnt == 3) { name = "033"; base = RoadNetwork.E | RoadNetwork.S | RoadNetwork.W; }
        else { name = "166"; base = 15; }
        int k = 0;
        while (base != m && k < 4) { base = rotMask(base); k++; }
        boolean straight = name.equals("160");
        float own = roads.deck[i];
        // bagli komsu daha yuksekse yol bu karede ona dogru rampa yapar (dolgu: kaldirim yanlari uzar)
        float[] raise = new float[4];
        for (int d = 0; d < 4; d++) {
            if ((m & RoadNetwork.BIT[d]) == 0) continue;
            int nx = x + RoadNetwork.DX[d], ny = y + RoadNetwork.DY[d];
            if (!map.inBounds(nx, ny)) continue;
            float nh = roads.deck[map.idx(nx, ny)];
            if (nh > own + 0.005f && nh - own < WorldMap.LEVEL_H * 1.5f) raise[d] = nh - own;
        }
        boolean urb = roads.urban[i], br = roads.bridge[i];
        Color asph = urb ? ASPH_URBAN : ASPH_RURAL, side = urb ? WALK : SHOULDER;
        MeshTemplate t = MeshTemplate.get(RD + name + ".obj");
        int tris = t.triangleCount();
        g.reserve(tris * 3);
        float cs = MathUtils.cosDeg(90f * k), sn = MathUtils.sinDeg(90f * k);
        Color[] matColor = new Color[t.materials.size()];
        for (int q = 0; q < matColor.length; q++) {
            String mat = t.materials.get(q);
            matColor[q] = mat.equals("Asphalt") ? asph : mat.equals("Stone") ? side : br ? CONCRETE : null;
        }
        float[] d = t.data;
        float cx = x + 0.5f, cz = y + 0.5f;
        for (int tri = 0; tri < tris; tri++) {
            Color c = matColor[t.triMaterial[tri]];
            if (c == null) continue;
            int b = g.base();
            for (int v = 0; v < 3; v++) {
                int o = (tri * 3 + v) * 6;
                float lx = (d[o] - 1.5f) / 3f, lz = (d[o + 2] + 1.5f) / 3f, oy = d[o + 1];
                float wx = lx * cs + lz * sn, wz = -lx * sn + lz * cs;
                float yy;
                if (br && oy < 0.5f) yy = own - 0.07f;
                else yy = own + (oy - ROAD_GRASS) * ROAD_SY + (oy > ROAD_GRASS + 0.01f ? roadRaise(raise, wx, wz, straight) : 0);
                float nx = d[o + 3] * cs + d[o + 5] * sn, nz = -d[o + 3] * sn + d[o + 5] * cs;
                float sh = oy > ROAD_GRASS + 0.01f ? 1f : 0.85f;
                g.vert(cx + wx, yy, cz + wz, nx, d[o + 4], nz, c.r * sh, c.g * sh, c.b * sh);
            }
            g.idx.add(b); g.idx.add(b + 1); g.idx.add(b + 2);
        }
        // kirsal duz yolda kesik orta cizgi
        if (straight && !urb) {
            boolean ew = (m & RoadNetwork.E) != 0;
            float top = (0.60f - ROAD_GRASS) * ROAD_SY + 0.0015f;
            for (int s = 0; s < 2; s++) {
                float a0 = -0.42f + s * 0.5f, a1 = a0 + 0.2f, hw = 0.006f;
                float x0 = ew ? a0 : -hw, x1 = ew ? a1 : hw, z0 = ew ? -hw : a0, z1 = ew ? hw : a1;
                float h00 = own + top + roadRaise(raise, x0, z0, true), h01 = own + top + roadRaise(raise, x0, z1, true);
                float h11 = own + top + roadRaise(raise, x1, z1, true), h10 = own + top + roadRaise(raise, x1, z0, true);
                g.reserve(4);
                quad(g, cx + x0, h00, cz + z0, cx + x0, h01, cz + z1, cx + x1, h11, cz + z1, cx + x1, h10, cz + z0, 0, 1, 0, DASH, 1, 1, 1, 1);
            }
        }
        // kopru ayagi: sudan tabliyeye beton ayak (yol yonune dik genis)
        if (br) {
            boolean ew = (m & RoadNetwork.E) != 0;
            float hx = ew ? 0.06f : 0.26f, hz = ew ? 0.26f : 0.06f, y0 = map.top[i] - 0.05f, y1 = own - 0.07f;
            g.reserve(20);
            quad(g, cx - hx, y0, cz + hz, cx - hx, y1, cz + hz, cx + hx, y1, cz + hz, cx + hx, y0, cz + hz, 0, 0, 1, CONCRETE, 0.7f, 0.9f, 0.9f, 0.7f);
            quad(g, cx + hx, y0, cz - hz, cx + hx, y1, cz - hz, cx - hx, y1, cz - hz, cx - hx, y0, cz - hz, 0, 0, -1, CONCRETE, 0.7f, 0.9f, 0.9f, 0.7f);
            quad(g, cx + hx, y0, cz + hz, cx + hx, y1, cz + hz, cx + hx, y1, cz - hz, cx + hx, y0, cz - hz, 1, 0, 0, CONCRETE, 0.6f, 0.8f, 0.8f, 0.6f);
            quad(g, cx - hx, y0, cz - hz, cx - hx, y1, cz - hz, cx - hx, y1, cz + hz, cx - hx, y0, cz + hz, -1, 0, 0, CONCRETE, 0.6f, 0.8f, 0.8f, 0.6f);
            // tabliye alt yuzu
            float u = 0.5f;
            quad(g, cx - u, y1, cz - u, cx + u, y1, cz - u, cx + u, y1, cz + u, cx - u, y1, cz + u, 0, -1, 0, CONCRETE, 0.55f, 0.55f, 0.55f, 0.55f);
        }
    }

    /** Karedeki (merkeze gore) bir noktada rampa yukseltisi: duz karede tum kare boyunca, digerlerinde yarim kare. */
    private static float roadRaise(float[] raise, float px, float pz, boolean straight) {
        float h = 0;
        for (int d = 0; d < 4; d++) {
            if (raise[d] <= 0) continue;
            float dot = px * RoadNetwork.DX[d] + pz * RoadNetwork.DY[d];
            float u = straight ? dot + 0.5f : dot * 2f;
            h += raise[d] * MathUtils.clamp(u, 0, 1);
        }
        return h;
    }

    // ------------------------------------------------------------ prosedurel (dag koknarlari, palmiye)
    private void conifer(float x, float y, float z, float s, float n) {
        Color trunk = new Color(0.36f, 0.26f, 0.16f, 1);
        prism(x, y, z, 0.025f * s, 0.10f * s, 4, trunk);
        Color c = new Color(0.12f + 0.04f * n, 0.32f + 0.06f * n, 0.18f, 1);
        cone(x, y + 0.06f * s, z, 0.13f * s, 0.30f * s, 6, c);
        cone(x, y + 0.18f * s, z, 0.10f * s, 0.22f * s, 6, c);
    }

    private void palm(float x, float y, float z) {
        Color trunk = new Color(0.52f, 0.40f, 0.26f, 1), leaf = new Color(0.30f, 0.55f, 0.22f, 1);
        prism(x, y, z, 0.02f, 0.28f, 4, trunk);
        cone(x, y + 0.24f, z, 0.17f, 0.06f, 6, leaf);
    }

    private void prism(float x, float y, float z, float r, float h, int div, Color side) {
        g.reserve(div * 4);
        for (int k = 0; k < div; k++) {
            float a0 = MathUtils.PI2 * k / div, a1 = MathUtils.PI2 * (k + 1) / div, am = (a0 + a1) / 2;
            float x0 = x + MathUtils.cos(a0) * r, z0 = z + MathUtils.sin(a0) * r, x1 = x + MathUtils.cos(a1) * r, z1 = z + MathUtils.sin(a1) * r;
            quad(g, x0, y, z0, x0, y + h, z0, x1, y + h, z1, x1, y, z1, MathUtils.cos(am), 0, MathUtils.sin(am), side, 0.8f, 1f, 1f, 0.8f);
        }
    }

    private void cone(float x, float y, float z, float r, float h, int div, Color c) {
        g.reserve(div * 3);
        for (int k = 0; k < div; k++) {
            float a0 = MathUtils.PI2 * k / div, a1 = MathUtils.PI2 * (k + 1) / div, am = (a0 + a1) / 2;
            float x0 = x + MathUtils.cos(a0) * r, z0 = z + MathUtils.sin(a0) * r, x1 = x + MathUtils.cos(a1) * r, z1 = z + MathUtils.sin(a1) * r;
            float ny = r / h, len = (float) Math.sqrt(1 + ny * ny);
            tri(g, x0, y, z0, x1, y, z1, x, y + h, z, MathUtils.cos(am) / len, ny / len, MathUtils.sin(am) / len, c, 0.82f, 1.05f);
        }
    }

    private static void tri(Accum a, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
                            float nx, float ny, float nz, Color c, float baseShade, float apexShade) {
        int base = a.base();
        a.vert(x0, y0, z0, nx, ny, nz, c.r * baseShade, c.g * baseShade, c.b * baseShade);
        a.vert(x1, y1, z1, nx, ny, nz, c.r * baseShade, c.g * baseShade, c.b * baseShade);
        a.vert(x2, y2, z2, nx, ny, nz, Math.min(1, c.r * apexShade), Math.min(1, c.g * apexShade), Math.min(1, c.b * apexShade));
        float ax = x1 - x0, ay = y1 - y0, az = z1 - z0, bx = x2 - x0, by = y2 - y0, bz = z2 - z0;
        float cxn = ay * bz - az * by, cyn = az * bx - ax * bz, czn = ax * by - ay * bx;
        if (cxn * nx + cyn * ny + czn * nz >= 0) { a.idx.add(base); a.idx.add(base + 1); a.idx.add(base + 2); }
        else { a.idx.add(base); a.idx.add(base + 2); a.idx.add(base + 1); }
    }

    private static void quad(Accum a, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz, Color c, float s0, float s1, float s2, float s3) {
        int base = a.base();
        a.vert(x0, y0, z0, nx, ny, nz, c.r * s0, c.g * s0, c.b * s0);
        a.vert(x1, y1, z1, nx, ny, nz, c.r * s1, c.g * s1, c.b * s1);
        a.vert(x2, y2, z2, nx, ny, nz, c.r * s2, c.g * s2, c.b * s2);
        a.vert(x3, y3, z3, nx, ny, nz, c.r * s3, c.g * s3, c.b * s3);
        float ax = x1 - x0, ay = y1 - y0, az = z1 - z0, bx = x2 - x0, by = y2 - y0, bz = z2 - z0;
        float cxn = ay * bz - az * by, cyn = az * bx - ax * bz, czn = ax * by - ay * bx;
        boolean flip = (cxn * nx + cyn * ny + czn * nz) < 0;
        // AO icin kosegeni daha karanlik kose ciftine gore sec (anizotropiyi azaltir)
        boolean altDiag = s0 + s2 < s1 + s3;
        int p = base, q = base + 1, r = base + 2, s = base + 3;
        if (altDiag) { int t0 = p; p = q; q = r; r = s; s = t0; }
        if (!flip) { a.idx.add(p); a.idx.add(q); a.idx.add(r); a.idx.add(p); a.idx.add(r); a.idx.add(s); }
        else { a.idx.add(p); a.idx.add(r); a.idx.add(q); a.idx.add(p); a.idx.add(s); a.idx.add(r); }
    }

    /** Dogrulama: toplam kose sayisi (arazi + su + dalgacik). */
    public int vertexCount() {
        int n = 0;
        for (Model[] arr : new Model[][] { ground, water, ripples, foliage }) for (Model m : arr) if (m != null)
            for (com.badlogic.gdx.graphics.Mesh me : m.meshes) n += me.getNumVertices();
        return n;
    }

    @Override public void dispose() {
        for (Model[] arr : new Model[][] { ground, water, ripples, foliage }) for (Model m : arr) if (m != null) m.dispose();
        if (foliageTex != null) foliageTex.dispose();
        instances.clear();
    }
}
