package com.energytycoon.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.energytycoon.sim.Equipment;
import com.energytycoon.sim.PlantType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/** Tum modeller prosedurel uretilir; harici asset yok. */
public final class ModelFactory implements Disposable {
    static final long ATTR = Usage.Position | Usage.Normal | Usage.ColorUnpacked;
    static final long ATTR_UV = ATTR | Usage.TextureCoordinates;

    private final Array<Model> all = new Array<>();
    private final Map<String, Model> plantModels = new HashMap<>();
    private final Map<String, Model> panelModels = new HashMap<>();
    private final Map<Float, Model> rotorModels = new HashMap<>();
    private final Model[] cityModels = new Model[7];
    public final Model[] houseModels = new Model[4];
    public Model unitBox, centerBox, sphere, flag, glow;
    public final Texture windowTex, windowLitTex;
    private final Matrix4 tmp = new Matrix4();

    /** Pylon iletken yuksekligi ve kol yari genisligi. */
    public static final float PYLON_H = 0.82f, PYLON_ARM = 0.14f;

    public ModelFactory() {
        windowTex = makeWindowTexture(false);
        windowLitTex = makeWindowTexture(true);
        for (int tier = 1; tier <= 6; tier++) cityModels[tier] = buildCity(tier);
        for (int i = 0; i < houseModels.length; i++) houseModels[i] = buildHouses(i);
        unitBox = build(b -> { b.setColor(Color.WHITE); b.box(0.5f, 0.5f, 0.5f, 1, 1, 1); });
        centerBox = build(b -> { b.setColor(Color.WHITE); b.box(0, 0, 0, 1, 1, 1); });
        sphere = build(b -> { b.setColor(Color.WHITE); b.sphere(1, 1, 1, 8, 6); });
        glow = build(b -> { b.setColor(Color.WHITE); b.sphere(1, 1, 1, 6, 4); });
        flag = build(b -> {
            b.setColor(new Color(0.75f, 0.75f, 0.78f, 1)); b.box(0, 0.25f, 0, 0.02f, 0.5f, 0.02f);
            b.setColor(Color.WHITE); b.box(0.09f, 0.43f, 0, 0.16f, 0.1f, 0.01f);
        });
    }

    // ------------------------------------------------------------------ dokular
    private static Texture makeWindowTexture(boolean lit) {
        int cw = 16, ch = 16, n = 8;
        Pixmap pm = new Pixmap(cw * n, ch * n, Pixmap.Format.RGBA8888);
        Random r = new Random(lit ? 7 : 3);
        pm.setColor(lit ? Color.BLACK : Color.WHITE);
        pm.fill();
        for (int gy = 0; gy < n; gy++) for (int gx = 0; gx < n; gx++) {
            int x0 = gx * cw + 4, y0 = gy * ch + 4;
            if (lit) {
                if (r.nextFloat() < 0.45f) {
                    float k = 0.7f + r.nextFloat() * 0.3f;
                    pm.setColor(1f * k, 0.82f * k, 0.45f * k, 1);
                    pm.fillRectangle(x0, y0, cw - 8, ch - 7);
                }
            } else {
                pm.setColor(0.42f, 0.50f, 0.60f, 1);
                pm.fillRectangle(x0, y0, cw - 8, ch - 7);
                pm.setColor(0.62f, 0.70f, 0.78f, 1);
                pm.fillRectangle(x0, y0, cw - 8, 2);
            }
        }
        Texture t = new Texture(pm, true);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        pm.dispose();
        return t;
    }

    // ------------------------------------------------------------------ erisim
    public Model plant(Equipment e) { return plantModels.computeIfAbsent(e.id, k -> buildPlant(e)); }

    public Model panelRow(Equipment e) {
        return panelModels.computeIfAbsent(e.id, k -> build(b -> {
            Color panel = switch ((int) e.visA) {
                case 0 -> c(0.12f, 0.22f, 0.50f); case 1 -> c(0.07f, 0.11f, 0.26f); case 2 -> c(0.05f, 0.06f, 0.10f); default -> c(0.20f, 0.14f, 0.40f);
            };
            b.setColor(c(0.75f, 0.77f, 0.80f)); b.box(0, -0.012f, 0, 0.86f, 0.008f, 0.19f);
            b.setColor(panel); b.box(0, 0, 0, 0.84f, 0.018f, 0.17f);
            b.setColor(new Color(panel).lerp(Color.WHITE, 0.25f));
            for (int i = 1; i < 6; i++) b.box(-0.42f + i * 0.14f, 0.0095f, 0, 0.006f, 0.001f, 0.17f);
        }));
    }

    public Model rotor(float radius) {
        return rotorModels.computeIfAbsent(radius, k -> build(b -> {
            Color white = new Color(0.95f, 0.95f, 0.97f, 1);
            for (int i = 0; i < 3; i++) {
                tmp.idt().rotate(0, 0, 1, i * 120f).translate(0, radius / 2, 0);
                b.setVertexTransform(tmp);
                b.setColor(white);
                b.box(0, 0, 0, 0.05f, radius, 0.014f);
                b.box(0, -radius * 0.32f, 0, 0.075f, radius * 0.25f, 0.016f);
                b.setVertexTransform(null);
            }
            cyl(b, 0, 0, 0.0f, 0.075f, 0.09f, 0.075f, 10, new Color(0.85f, 0.85f, 0.88f, 1), 90f);
        }));
    }

    public Model city(int tier) { return cityModels[Math.max(1, Math.min(6, tier))]; }

    private final Model[] substationModels = new Model[3];

    /** Trafo merkezi: beton saha, trafolar, portal direkleri, cit. level 2 (TM380) daha buyuk ve yuksek. */
    public Model substation(int level) {
        int lv = Math.max(1, Math.min(2, level));
        if (substationModels[lv] != null) return substationModels[lv];
        return substationModels[lv] = build(b -> {
            Color pad = c(0.60f, 0.59f, 0.55f), gravel = c(0.47f, 0.46f, 0.44f), tr = c(0.30f, 0.34f, 0.32f), fin = c(0.24f, 0.27f, 0.26f),
                  steel = c(0.62f, 0.64f, 0.67f), ins = c(0.75f, 0.62f, 0.45f), house = c(0.80f, 0.78f, 0.72f), roof = c(0.42f, 0.22f, 0.18f);
            float big = lv == 2 ? 1.25f : 1f;
            box(b, 0, 0, 0, 0.96f, 0.03f, 0.96f, pad);
            box(b, 0.05f, 0.03f, 0.05f, 0.80f, 0.006f, 0.70f, gravel);
            int nTr = lv == 2 ? 3 : 2;
            for (int i = 0; i < nTr; i++) {
                float x = -0.25f + i * (0.5f / Math.max(1, nTr - 1));
                box(b, x, 0.036f, 0.12f, 0.13f * big, 0.12f * big, 0.16f * big, tr);
                box(b, x + 0.075f * big, 0.05f, 0.12f, 0.02f, 0.09f * big, 0.14f * big, fin);
                for (int k = 0; k < 3; k++) cyl(b, x - 0.04f + k * 0.04f, 0.036f + 0.12f * big, 0.12f, 0.014f, 0.07f * big, 0.014f, 6, ins);
            }
            // portal direkleri (gantry)
            float gh = lv == 2 ? 0.48f : 0.36f;
            for (int i = -1; i <= 1; i += 2) {
                box(b, i * 0.36f, 0.03f, -0.22f, 0.025f, gh, 0.025f, steel);
                box(b, i * 0.36f, 0.03f, -0.08f, 0.025f, gh, 0.025f, steel);
            }
            box(b, 0, 0.03f + gh - 0.02f, -0.22f, 0.76f, 0.025f, 0.025f, steel);
            box(b, 0, 0.03f + gh - 0.02f, -0.08f, 0.76f, 0.025f, 0.025f, steel);
            for (int k = -1; k <= 1; k++) cyl(b, k * 0.2f, 0.03f + gh - 0.09f, -0.15f, 0.012f, 0.07f, 0.012f, 6, ins);
            // kontrol binasi
            box(b, 0.32f, 0.03f, 0.33f, 0.20f, 0.10f, 0.14f, house);
            box(b, 0.32f, 0.13f, 0.33f, 0.22f, 0.02f, 0.16f, roof);
            // cit
            for (int i = 0; i <= 8; i++) {
                float t = -0.44f + i * 0.11f;
                box(b, t, 0.03f, -0.44f, 0.008f, 0.07f, 0.008f, steel);
                box(b, t, 0.03f, 0.44f, 0.008f, 0.07f, 0.008f, steel);
                box(b, -0.44f, 0.03f, t, 0.008f, 0.07f, 0.008f, steel);
                box(b, 0.44f, 0.03f, t, 0.008f, 0.07f, 0.008f, steel);
            }
            box(b, 0, 0.09f, -0.44f, 0.88f, 0.006f, 0.006f, steel); box(b, 0, 0.09f, 0.44f, 0.88f, 0.006f, 0.006f, steel);
            box(b, -0.44f, 0.09f, 0, 0.006f, 0.006f, 0.88f, steel); box(b, 0.44f, 0.09f, 0, 0.006f, 0.006f, 0.88f, steel);
        });
    }

    private final Map<String, Model> plantBases = new HashMap<>();
    private Model cityBase, park;

    /** Kenney parcalarinin altina gelen prosedurel taban: zemin, trafo sahasi ve ture ozel detaylar. */
    public Model plantBase(Equipment e) {
        return plantBases.computeIfAbsent(e.id, k -> build(b -> {
            Color pad = c(0.50f, 0.51f, 0.53f), coal = c(0.10f, 0.10f, 0.11f), steel = c(0.62f, 0.64f, 0.67f), dark = c(0.28f, 0.29f, 0.32f);
            box(b, 0, 0, 0, 0.96f, 0.03f, 0.96f, pad);
            // zemin cizgileri (yol)
            box(b, 0, 0.03f, 0.02f, 0.94f, 0.003f, 0.05f, c(0.36f, 0.37f, 0.40f));
            switch (e.id) {
                case "fk-sub" -> { cone(b, -0.30f, 0.03f, -0.27f, 0.32f, 0.20f, 0.28f, 8, coal); cone(b, -0.05f, 0.03f, -0.33f, 0.20f, 0.12f, 0.18f, 8, coal); }
                case "fk-sc" -> {
                    cone(b, -0.30f, 0.03f, -0.28f, 0.30f, 0.18f, 0.26f, 8, coal);
                    cone(b, -0.05f, 0.03f, -0.32f, 0.24f, 0.14f, 0.22f, 8, coal);
                    tmp.idt().translate(-0.16f, 0.16f, -0.12f).rotate(1, 0, 0, -32f);
                    b.setVertexTransform(tmp); b.setColor(steel); b.box(0, 0, 0, 0.05f, 0.03f, 0.30f); b.setVertexTransform(null);
                }
                case "th-usc" -> {
                    cone(b, -0.32f, 0.03f, -0.30f, 0.28f, 0.16f, 0.26f, 8, coal);
                    tmp.idt().translate(-0.30f, 0.18f, -0.06f).rotate(1, 0, 0, -36f);
                    b.setVertexTransform(tmp); b.setColor(steel); b.box(0, 0, 0, 0.05f, 0.03f, 0.34f); b.setVertexTransform(null);
                }
                case "atr-600" -> {
                    Color cont = c(0.70f, 0.72f, 0.76f);
                    cyl(b, -0.25f, 0.03f, -0.20f, 0.34f, 0.30f, 0.34f, 20, cont);
                    tmp.idt().translate(-0.25f, 0.33f, -0.20f);
                    b.setVertexTransform(tmp); b.setColor(cont); b.sphere(0.34f, 0.28f, 0.34f, 18, 10); b.setVertexTransform(null);
                    box(b, -0.25f, 0.03f, 0.02f, 0.10f, 0.12f, 0.12f, c(0.60f, 0.62f, 0.66f));
                }
                case "ps-smr" -> {
                    Color cont = c(0.70f, 0.72f, 0.76f);
                    for (int i = 0; i < 3; i++) {
                        float z = -0.32f + i * 0.21f;
                        cyl(b, -0.25f, 0.03f, z, 0.16f, 0.15f, 0.16f, 16, cont);
                        tmp.idt().translate(-0.25f, 0.18f, z);
                        b.setVertexTransform(tmp); b.setColor(cont); b.sphere(0.16f, 0.12f, 0.16f, 14, 7); b.setVertexTransform(null);
                    }
                    for (int i = 0; i < 2; i++) { box(b, 0.38f, 0.03f, -0.32f + i * 0.17f, 0.15f, 0.12f, 0.15f, steel); cyl(b, 0.38f, 0.15f, -0.32f + i * 0.17f, 0.10f, 0.01f, 0.10f, 10, dark); }
                }
                default -> { }
            }
            transformerYard(b, 0.33f, 0.36f);
        }));
    }

    /** Sehir tabani: kaldirim + yol izgarasi (binalar Kenney modelleriyle ayrica konur). */
    public Model cityBase() {
        if (cityBase != null) return cityBase;
        cityBase = build(b -> {
            box(b, 0, 0, 0, 2.94f, 0.04f, 2.94f, c(0.66f, 0.66f, 0.64f));
            Color road = c(0.24f, 0.25f, 0.27f), line = c(0.85f, 0.82f, 0.60f);
            for (int i = 0; i < 6; i++) {
                float p = -1.40f + i * 0.56f;
                box(b, p, 0.04f, 0, 0.09f, 0.004f, 2.9f, road);
                box(b, 0, 0.04f, p, 2.9f, 0.004f, 0.09f, road);
                box(b, p, 0.044f, 0, 0.008f, 0.001f, 2.9f, line);
                box(b, 0, 0.044f, p, 2.9f, 0.001f, 0.008f, line);
            }
        });
        return cityBase;
    }

    /** Sehir icinde bir park hucresi. */
    public Model park() {
        if (park != null) return park;
        Random r = new Random(5);
        park = build(b -> {
            box(b, 0, 0, 0, 0.44f, 0.012f, 0.44f, c(0.34f, 0.58f, 0.30f));
            box(b, 0, 0.012f, 0, 0.06f, 0.002f, 0.40f, c(0.80f, 0.74f, 0.58f));
            for (int k = 0; k < 4; k++) {
                float tx = (k % 2 == 0 ? -0.12f : 0.12f) + (r.nextFloat() - 0.5f) * 0.06f, tz = (k < 2 ? -0.12f : 0.12f) + (r.nextFloat() - 0.5f) * 0.06f;
                box(b, tx, 0.012f, tz, 0.02f, 0.05f, 0.02f, c(0.4f, 0.3f, 0.2f));
                cone(b, tx, 0.05f, tz, 0.12f, 0.18f, 0.12f, 7, c(0.20f, 0.46f, 0.22f));
            }
        });
        return park;
    }

    // ------------------------------------------------------------------ yardimcilar
    Model build(Consumer<MeshPartBuilder> f) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder b = mb.part("m", GL20.GL_TRIANGLES, ATTR, new Material("m", ColorAttribute.createDiffuse(Color.WHITE)));
        f.accept(b);
        Model m = mb.end();
        all.add(m);
        return m;
    }

    public void track(Model m) { all.add(m); }
    public void untrack(Model m) { all.removeValue(m, true); m.dispose(); }

    static Color c(float r, float g, float b) { return new Color(r, g, b, 1); }

    void box(MeshPartBuilder b, float x, float y0, float z, float w, float h, float d, Color c) {
        b.setColor(c);
        b.box(x, y0 + h / 2, z, w, h, d);
    }

    void cyl(MeshPartBuilder b, float x, float y0, float z, float w, float h, float d, int div, Color c, float rotXdeg) {
        tmp.idt().translate(x, y0 + h / 2, z);
        if (rotXdeg != 0) tmp.rotate(1, 0, 0, rotXdeg);
        b.setVertexTransform(tmp);
        b.setColor(c);
        b.cylinder(w, h, d, div);
        b.setVertexTransform(null);
    }

    void cyl(MeshPartBuilder b, float x, float y0, float z, float w, float h, float d, int div, Color c) { cyl(b, x, y0, z, w, h, d, div, c, 0); }

    void cone(MeshPartBuilder b, float x, float y0, float z, float w, float h, float d, int div, Color c) {
        tmp.idt().translate(x, y0 + h / 2, z);
        b.setVertexTransform(tmp);
        b.setColor(c);
        b.cone(w, h, d, div);
        b.setVertexTransform(null);
    }

    /** Donel yuzey: radii[i] yukseklik heights[i]'de. Hiperbolik sogutma kulesi vb. */
    void lathe(MeshPartBuilder b, float cx, float y0, float cz, float[] radii, float[] heights, int div, Color col) {
        b.setColor(col);
        Vector3 p00 = new Vector3(), p10 = new Vector3(), p11 = new Vector3(), p01 = new Vector3(), n = new Vector3();
        for (int i = 0; i < radii.length - 1; i++) {
            float r0 = radii[i], r1 = radii[i + 1], h0 = y0 + heights[i], h1 = y0 + heights[i + 1];
            float slope = (r0 - r1) / Math.max(0.001f, h1 - h0);
            for (int k = 0; k < div; k++) {
                float a0 = MathUtils.PI2 * k / div, a1 = MathUtils.PI2 * (k + 1) / div, am = (a0 + a1) / 2;
                p00.set(cx + MathUtils.cos(a0) * r0, h0, cz + MathUtils.sin(a0) * r0);
                p10.set(cx + MathUtils.cos(a1) * r0, h0, cz + MathUtils.sin(a1) * r0);
                p11.set(cx + MathUtils.cos(a1) * r1, h1, cz + MathUtils.sin(a1) * r1);
                p01.set(cx + MathUtils.cos(a0) * r1, h1, cz + MathUtils.sin(a0) * r1);
                n.set(MathUtils.cos(am), slope, MathUtils.sin(am)).nor();
                b.rect(p00, p01, p11, p10, n);
            }
        }
    }

    void coolingTower(MeshPartBuilder b, float cx, float y0, float cz, float r, float h, Color col) {
        float[] heights = { 0, h * 0.15f, h * 0.35f, h * 0.55f, h * 0.72f, h * 0.86f, h };
        float[] radii = new float[heights.length];
        for (int i = 0; i < heights.length; i++) {
            float t = heights[i] / h;
            radii[i] = r * (0.62f + 0.55f * (float) Math.pow(t - 0.78f, 2) * 2.2f);
        }
        lathe(b, cx, y0, cz, radii, heights, 18, col);
        // ic yuzey hissi icin koyu agiz
        b.setColor(c(0.25f, 0.25f, 0.27f));
        tmp.idt().translate(cx, y0 + h - 0.005f, cz);
        b.setVertexTransform(tmp);
        b.cylinder(radii[radii.length - 1] * 1.9f, 0.01f, radii[radii.length - 1] * 1.9f, 18);
        b.setVertexTransform(null);
    }

    void transformerYard(MeshPartBuilder b, float x, float z) {
        Color gray = c(0.55f, 0.57f, 0.60f), dark = c(0.32f, 0.34f, 0.36f), ins = c(0.75f, 0.62f, 0.45f);
        box(b, x, 0.03f, z, 0.22f, 0.012f, 0.18f, c(0.62f, 0.60f, 0.55f));
        box(b, x - 0.05f, 0.04f, z, 0.08f, 0.08f, 0.10f, dark);
        box(b, x + 0.06f, 0.04f, z, 0.06f, 0.07f, 0.08f, gray);
        for (int i = 0; i < 3; i++) cyl(b, x - 0.08f + i * 0.03f, 0.12f, z, 0.012f, 0.05f, 0.012f, 5, ins);
        // cit
        for (int i = 0; i < 5; i++) {
            box(b, x - 0.11f + i * 0.055f, 0.03f, z - 0.09f, 0.006f, 0.05f, 0.006f, gray);
            box(b, x - 0.11f + i * 0.055f, 0.03f, z + 0.09f, 0.006f, 0.05f, 0.006f, gray);
        }
    }

    // ------------------------------------------------------------------ santraller
    private Model buildPlant(Equipment e) {
        PlantType t = e.type;
        int v = (int) e.visA;
        return build(b -> {
            Color concrete = c(0.72f, 0.72f, 0.74f), dark = c(0.28f, 0.29f, 0.32f), red = c(0.85f, 0.2f, 0.18f),
                  white = c(0.95f, 0.95f, 0.95f), pad = c(0.47f, 0.48f, 0.50f), steel = c(0.62f, 0.64f, 0.67f);
            box(b, 0, 0, 0, 0.96f, 0.03f, 0.96f, pad);
            switch (t) {
                case COAL -> {
                    Color hall = c(0.56f, 0.50f, 0.45f), boiler = c(0.44f, 0.42f, 0.42f), coal = c(0.10f, 0.10f, 0.11f);
                    if (v == 0) {
                        box(b, -0.12f, 0.03f, 0.15f, 0.62f, 0.26f, 0.42f, hall);
                        box(b, -0.10f, 0.29f, 0.15f, 0.30f, 0.30f, 0.30f, boiler);
                        for (float sz : new float[] { -0.22f, 0.04f }) {
                            cyl(b, 0.28f, 0.03f, sz, 0.12f, 0.50f, 0.12f, 10, c(0.65f, 0.63f, 0.60f));
                            cyl(b, 0.28f, 0.53f, sz, 0.11f, 0.12f, 0.11f, 10, red);
                            cyl(b, 0.28f, 0.65f, sz, 0.10f, 0.12f, 0.10f, 10, white);
                        }
                        cone(b, -0.32f, 0.03f, -0.28f, 0.34f, 0.22f, 0.30f, 8, coal);
                    } else if (v == 1) {
                        box(b, -0.05f, 0.03f, 0.18f, 0.70f, 0.30f, 0.36f, hall);
                        box(b, -0.18f, 0.33f, 0.18f, 0.30f, 0.38f, 0.30f, boiler);
                        box(b, 0.14f, 0.33f, 0.18f, 0.22f, 0.18f, 0.26f, c(0.50f, 0.48f, 0.46f));
                        for (float sz : new float[] { -0.20f, 0.02f }) {
                            cyl(b, 0.34f, 0.03f, sz - 0.12f, 0.11f, 0.78f, 0.11f, 12, c(0.70f, 0.68f, 0.65f));
                            cyl(b, 0.34f, 0.69f, sz - 0.12f, 0.10f, 0.06f, 0.10f, 12, red);
                        }
                        cone(b, -0.30f, 0.03f, -0.28f, 0.30f, 0.18f, 0.26f, 8, coal);
                        cone(b, -0.05f, 0.03f, -0.32f, 0.24f, 0.14f, 0.22f, 8, coal);
                        tmp.idt().translate(-0.18f, 0.25f, -0.06f).rotate(1, 0, 0, -38f);
                        b.setVertexTransform(tmp); b.setColor(steel); b.box(0, 0, 0, 0.05f, 0.03f, 0.36f); b.setVertexTransform(null);
                    } else {
                        coolingTower(b, 0.22f, 0.03f, -0.20f, 0.24f, 0.78f, c(0.82f, 0.82f, 0.84f));
                        box(b, -0.20f, 0.03f, 0.22f, 0.50f, 0.30f, 0.36f, hall);
                        box(b, -0.30f, 0.33f, 0.22f, 0.28f, 0.42f, 0.28f, boiler);
                        cyl(b, 0.18f, 0.03f, 0.28f, 0.10f, 0.95f, 0.10f, 12, c(0.72f, 0.70f, 0.66f));
                        cyl(b, 0.18f, 0.88f, 0.28f, 0.095f, 0.07f, 0.095f, 12, red);
                        cone(b, -0.32f, 0.03f, -0.28f, 0.28f, 0.16f, 0.26f, 8, coal);
                        tmp.idt().translate(-0.30f, 0.28f, -0.02f).rotate(1, 0, 0, -40f);
                        b.setVertexTransform(tmp); b.setColor(steel); b.box(0, 0, 0, 0.05f, 0.03f, 0.40f); b.setVertexTransform(null);
                    }
                }
                case GAS -> {
                    if (v == 0) {
                        box(b, -0.05f, 0.03f, 0.05f, 0.46f, 0.16f, 0.24f, c(0.80f, 0.82f, 0.84f));
                        box(b, -0.33f, 0.03f, 0.05f, 0.14f, 0.24f, 0.26f, c(0.60f, 0.64f, 0.70f));
                        cyl(b, 0.24f, 0.03f, 0.05f, 0.12f, 0.36f, 0.12f, 10, c(0.68f, 0.68f, 0.70f));
                        cyl(b, -0.10f, 0.03f, -0.30f, 0.20f, 0.20f, 0.20f, 14, white);
                        box(b, 0.20f, 0.03f, -0.28f, 0.22f, 0.10f, 0.16f, concrete);
                    } else if (v == 1) {
                        box(b, -0.10f, 0.03f, 0.05f, 0.58f, 0.24f, 0.40f, c(0.78f, 0.80f, 0.82f));
                        box(b, 0.22f, 0.03f, 0.05f, 0.22f, 0.34f, 0.30f, c(0.55f, 0.60f, 0.66f));
                        cyl(b, 0.22f, 0.37f, 0.05f, 0.09f, 0.40f, 0.09f, 10, c(0.7f, 0.7f, 0.72f));
                        cyl(b, -0.25f, 0.03f, -0.30f, 0.26f, 0.26f, 0.26f, 14, white);
                        cyl(b, 0.05f, 0.03f, -0.30f, 0.26f, 0.26f, 0.26f, 14, white);
                    } else {
                        box(b, -0.08f, 0.03f, 0.20f, 0.78f, 0.22f, 0.28f, c(0.80f, 0.82f, 0.85f));
                        for (float x : new float[] { -0.24f, 0.10f }) {
                            box(b, x, 0.03f, -0.06f, 0.18f, 0.40f, 0.22f, c(0.52f, 0.57f, 0.63f));
                            cyl(b, x, 0.43f, -0.06f, 0.08f, 0.42f, 0.08f, 10, c(0.72f, 0.72f, 0.74f));
                        }
                        // hava sogutmali kondenser: fan bloklari
                        for (int i = 0; i < 3; i++) for (int k = 0; k < 2; k++) {
                            float fx = -0.30f + i * 0.20f, fz = -0.34f + k * 0.12f;
                            box(b, fx, 0.03f, fz, 0.18f, 0.16f, 0.11f, steel);
                            cyl(b, fx, 0.19f, fz, 0.09f, 0.01f, 0.09f, 10, dark);
                        }
                        box(b, 0.36f, 0.03f, -0.28f, 0.16f, 0.12f, 0.20f, concrete);
                    }
                }
                case SOLAR -> {
                    Color frame = c(0.70f, 0.72f, 0.75f);
                    box(b, 0, 0.03f, 0, 0.94f, 0.004f, 0.94f, c(0.52f, 0.55f, 0.46f));
                    float post = v == 2 ? 0.17f : 0.11f;
                    for (int row = 0; row < 4; row++) {
                        float z = -0.34f + row * 0.23f;
                        box(b, -0.30f, 0.03f, z, 0.022f, post, 0.022f, frame);
                        box(b, 0.30f, 0.03f, z, 0.022f, post, 0.022f, frame);
                    }
                    box(b, 0.38f, 0.03f, 0.40f, 0.10f, 0.08f, 0.07f, c(0.85f, 0.85f, 0.82f));
                }
                case WIND -> {
                    float h = e.visA;
                    float[] hs = { 0, h };
                    lathe(b, 0, 0.03f, 0, new float[] { 0.055f, 0.032f }, hs, 12, white);
                    box(b, 0, 0.03f + h - 0.02f, 0.03f, 0.10f, 0.10f, 0.26f, c(0.92f, 0.92f, 0.94f));
                    box(b, 0.30f, 0.03f, 0.28f, 0.16f, 0.09f, 0.12f, concrete);
                }
                case HYDRO -> {
                    Color dam = c(0.68f, 0.68f, 0.70f), house = c(0.82f, 0.82f, 0.80f), roof = c(0.55f, 0.25f, 0.2f);
                    if (v == 0) {
                        box(b, 0, 0.03f, -0.30f, 0.98f, 0.22f, 0.22f, dam);
                        for (int i = 0; i < 5; i++) box(b, -0.40f + i * 0.20f, 0.25f, -0.30f, 0.06f, 0.14f, 0.24f, c(0.55f, 0.56f, 0.58f));
                        box(b, 0, 0.36f, -0.30f, 0.98f, 0.03f, 0.10f, dark);
                        box(b, 0.10f, 0.03f, 0.18f, 0.50f, 0.18f, 0.28f, house);
                        cone(b, 0.10f, 0.21f, 0.18f, 0.54f, 0.10f, 0.32f, 4, roof);
                    } else if (v == 1) {
                        box(b, 0, 0.03f, -0.28f, 0.96f, 0.46f, 0.20f, dam);
                        box(b, 0, 0.49f, -0.28f, 0.96f, 0.03f, 0.24f, dark);
                        box(b, 0.15f, 0.03f, 0.25f, 0.50f, 0.22f, 0.26f, house);
                        cone(b, 0.15f, 0.25f, 0.25f, 0.54f, 0.14f, 0.30f, 4, roof);
                        cyl(b, -0.25f, 0.10f, 0.02f, 0.10f, 0.40f, 0.10f, 10, dark, 90f);
                    } else {
                        box(b, -0.05f, 0.03f, -0.34f, 0.80f, 0.60f, 0.16f, dam);
                        for (float x : new float[] { -0.25f, -0.05f, 0.15f }) {
                            tmp.idt().translate(x, 0.32f, -0.04f).rotate(1, 0, 0, 58f);
                            b.setVertexTransform(tmp); b.setColor(c(0.30f, 0.30f, 0.33f)); b.cylinder(0.06f, 0.56f, 0.06f, 8); b.setVertexTransform(null);
                        }
                        box(b, -0.05f, 0.03f, 0.30f, 0.62f, 0.20f, 0.24f, house);
                        cone(b, -0.05f, 0.23f, 0.30f, 0.66f, 0.12f, 0.28f, 4, roof);
                    }
                }
                case NUCLEAR -> {
                    Color tw = c(0.84f, 0.84f, 0.86f), cont = c(0.66f, 0.69f, 0.73f);
                    if (v == 0) {
                        coolingTower(b, 0.25f, 0.03f, -0.22f, 0.21f, 0.88f, tw);
                        coolingTower(b, 0.25f, 0.03f, 0.24f, 0.21f, 0.88f, tw);
                        box(b, -0.28f, 0.03f, 0.25f, 0.34f, 0.22f, 0.40f, c(0.65f, 0.68f, 0.72f));
                        cyl(b, -0.28f, 0.03f, -0.20f, 0.34f, 0.30f, 0.34f, 18, cont);
                        b.setColor(cont);
                        tmp.idt().translate(-0.28f, 0.33f, -0.20f);
                        b.setVertexTransform(tmp); b.sphere(0.34f, 0.26f, 0.34f, 14, 8); b.setVertexTransform(null);
                    } else {
                        for (int i = 0; i < 3; i++) {
                            float z = -0.30f + i * 0.22f;
                            cyl(b, -0.22f, 0.03f, z, 0.17f, 0.16f, 0.17f, 14, cont);
                            tmp.idt().translate(-0.22f, 0.19f, z);
                            b.setVertexTransform(tmp); b.setColor(cont); b.sphere(0.17f, 0.12f, 0.17f, 12, 6); b.setVertexTransform(null);
                        }
                        box(b, 0.12f, 0.03f, -0.05f, 0.24f, 0.20f, 0.50f, c(0.72f, 0.75f, 0.78f));
                        for (int i = 0; i < 3; i++) {
                            box(b, 0.36f, 0.03f, -0.30f + i * 0.20f, 0.16f, 0.14f, 0.18f, steel);
                            cyl(b, 0.36f, 0.17f, -0.30f + i * 0.20f, 0.11f, 0.01f, 0.11f, 10, dark);
                        }
                    }
                }
            }
            transformerYard(b, 0.33f, 0.36f);
        });
    }

    // ------------------------------------------------------------------ sehirler
    private Model buildCity(int tier) {
        Random r = new Random(tier * 977L);
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        Material plain = new Material("plain", ColorAttribute.createDiffuse(Color.WHITE));
        Material walls = new Material("walls", ColorAttribute.createDiffuse(Color.WHITE),
            TextureAttribute.createDiffuse(windowTex), TextureAttribute.createEmissive(windowLitTex), ColorAttribute.createEmissive(0, 0, 0, 1));
        MeshPartBuilder base = mb.part("base", GL20.GL_TRIANGLES, ATTR, plain);
        // zemin: kaldirim + yol izgarasi + parklar
        box(base, 0, 0, 0, 2.94f, 0.04f, 2.94f, c(0.64f, 0.64f, 0.62f));
        Color road = c(0.25f, 0.26f, 0.28f);
        for (int i = 0; i < 6; i++) {
            float p = -1.40f + i * 0.56f;
            box(base, p, 0.04f, 0, 0.08f, 0.004f, 2.9f, road);
            box(base, 0, 0.04f, p, 2.9f, 0.004f, 0.08f, road);
        }
        MeshPartBuilder wb = mb.part("walls", GL20.GL_TRIANGLES, ATTR_UV, walls);
        MeshPartBuilder roof = mb.part("roof", GL20.GL_TRIANGLES, ATTR, plain);
        Color[] pal = { c(0.86f, 0.84f, 0.80f), c(0.74f, 0.78f, 0.86f), c(0.90f, 0.86f, 0.74f),
                        c(0.70f, 0.72f, 0.78f), c(0.82f, 0.68f, 0.60f), c(0.84f, 0.86f, 0.90f) };
        for (int gx = -2; gx <= 2; gx++) for (int gz = -2; gz <= 2; gz++) {
            float cx = gx * 0.56f, cz = gz * 0.56f;
            float centerBias = 1.0f - (Math.abs(gx) + Math.abs(gz)) / 4.5f;
            if (r.nextFloat() < 0.12f + 0.05f * (6 - tier) && !(gx == 0 && gz == 0)) {
                // park
                box(base, cx, 0.04f, cz, 0.44f, 0.01f, 0.44f, c(0.34f, 0.58f, 0.30f));
                for (int k = 0; k < 3; k++) {
                    float tx = cx + (r.nextFloat() - 0.5f) * 0.3f, tz = cz + (r.nextFloat() - 0.5f) * 0.3f;
                    box(base, tx, 0.05f, tz, 0.02f, 0.05f, 0.02f, c(0.4f, 0.3f, 0.2f));
                    cone(base, tx, 0.09f, tz, 0.11f, 0.16f, 0.11f, 6, c(0.20f, 0.45f, 0.22f));
                }
                continue;
            }
            int nb = 1 + (r.nextFloat() < 0.5f ? 1 : 0);
            for (int k = 0; k < nb; k++) {
                float w = nb == 1 ? 0.30f + r.nextFloat() * 0.12f : 0.18f + r.nextFloat() * 0.06f;
                float d = w * (0.8f + r.nextFloat() * 0.3f);
                float ox = nb == 1 ? 0 : (k == 0 ? -0.11f : 0.11f);
                float h = 0.12f + r.nextFloat() * 0.14f + Math.max(0, centerBias) * (0.12f + tier * 0.22f) * (0.4f + r.nextFloat() * 0.6f);
                float bx = cx + ox, bz = cz + (r.nextFloat() - 0.5f) * 0.06f;
                Color col = pal[r.nextInt(pal.length)];
                wb.setColor(col);
                wb.setUVRange(0, 0, Math.max(1, Math.round(w / 0.07f)) / 8f, Math.max(1, Math.round(h / 0.065f)) / 8f);
                wb.box(bx, 0.04f + h / 2, bz, w, h, d);
                box(roof, bx, 0.04f + h, bz, w + 0.012f, 0.018f, d + 0.012f, c(0.36f, 0.37f, 0.40f));
                if (h > 0.5f) {
                    box(roof, bx, 0.058f + h, bz, w * 0.45f, 0.05f, d * 0.45f, c(0.50f, 0.52f, 0.55f));
                    if (r.nextFloat() < 0.5f) box(roof, bx + w * 0.2f, 0.058f + h, bz, 0.01f, 0.14f, 0.01f, c(0.7f, 0.7f, 0.72f));
                }
            }
        }
        Model m = mb.end();
        all.add(m);
        return m;
    }

    /** Banliyo: kucuk evler ve kisa yol parcasi (1 kare). */
    private Model buildHouses(int variant) {
        Random r = new Random(100 + variant * 31L);
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        Material plain = new Material("plain", ColorAttribute.createDiffuse(Color.WHITE));
        Material walls = new Material("walls", ColorAttribute.createDiffuse(Color.WHITE),
            TextureAttribute.createDiffuse(windowTex), TextureAttribute.createEmissive(windowLitTex), ColorAttribute.createEmissive(0, 0, 0, 1));
        MeshPartBuilder base = mb.part("base", GL20.GL_TRIANGLES, ATTR, plain);
        boolean vertical = variant % 2 == 0;
        if (vertical) box(base, 0, 0, 0, 0.12f, 0.006f, 1.0f, c(0.30f, 0.30f, 0.32f));
        else box(base, 0, 0, 0, 1.0f, 0.006f, 0.12f, c(0.30f, 0.30f, 0.32f));
        MeshPartBuilder wb = mb.part("walls", GL20.GL_TRIANGLES, ATTR_UV, walls);
        Color[] wallC = { c(0.92f, 0.88f, 0.78f), c(0.86f, 0.82f, 0.80f), c(0.80f, 0.86f, 0.88f), c(0.94f, 0.92f, 0.88f) };
        Color[] roofC = { c(0.62f, 0.26f, 0.20f), c(0.45f, 0.30f, 0.25f), c(0.35f, 0.38f, 0.42f) };
        int n = 3 + variant;
        for (int i = 0; i < n; i++) {
            float along = -0.38f + (i / (float) Math.max(1, n - 1)) * 0.76f;
            float side = (i % 2 == 0 ? -1 : 1) * (0.20f + r.nextFloat() * 0.12f);
            float x = vertical ? side : along, z = vertical ? along : side;
            float w = 0.13f + r.nextFloat() * 0.05f, h = 0.08f + r.nextFloat() * 0.05f;
            wb.setColor(wallC[r.nextInt(wallC.length)]);
            wb.setUVRange(0, 0, 0.25f, 0.125f);
            wb.box(x, h / 2, z, w, h, w);
            cone(base, x, h, z, w * 1.45f, 0.08f, w * 1.45f, 4, roofC[r.nextInt(roofC.length)]);
            if (r.nextFloat() < 0.6f) {
                float tx = x + (r.nextFloat() - 0.5f) * 0.18f, tz = z + (r.nextFloat() - 0.5f) * 0.18f;
                cone(base, tx, 0.0f, tz, 0.07f, 0.13f, 0.07f, 5, c(0.22f, 0.46f, 0.22f));
            }
        }
        Model m = mb.end();
        all.add(m);
        return m;
    }

    // ------------------------------------------------------------------ iletim hatti
    /**
     * Bir hat icin tek model: direkler + iki iletken. Iletken malzemesi "cable" yuk rengini tasir.
     * points: direk taban konumlari (y = zemin), uclar dahil (uclarda direk cizilmez, sadece baglanti).
     */
    public Model buildLine(List<Vector3> pylons, Vector3 startAttach, Vector3 endAttach) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        // Not: ayni vertex formatindaki parcalar tek MeshBuilder'i paylasir; once direkler bitmeli, sonra kablo parcasi acilmali.
        MeshPartBuilder pb = mb.part("pylons", GL20.GL_TRIANGLES, ATTR, new Material("pylons", ColorAttribute.createDiffuse(Color.WHITE)));
        Vector3 dir = new Vector3(endAttach).sub(startAttach); dir.y = 0; dir.nor();
        float yaw = MathUtils.atan2(dir.x, dir.z) * MathUtils.radiansToDegrees;
        Vector3 side = new Vector3(dir.z, 0, -dir.x).scl(PYLON_ARM);
        Color steel = c(0.58f, 0.60f, 0.63f);
        for (Vector3 p : pylons) {
            tmp.idt().translate(p).rotate(0, 1, 0, yaw);
            pylon(pb, tmp, steel);
        }
        // iletkenler: iki faz
        MeshPartBuilder cb = mb.part("cable", GL20.GL_TRIANGLES, ATTR, new Material("cable", ColorAttribute.createDiffuse(0.12f, 0.12f, 0.12f, 1)));
        for (int s = -1; s <= 1; s += 2) {
            Vector3 prev = new Vector3(startAttach).mulAdd(side, s * 0.5f);
            for (int i = 0; i <= pylons.size(); i++) {
                Vector3 next = i < pylons.size()
                    ? new Vector3(pylons.get(i)).add(0, PYLON_H, 0).mulAdd(side, s)
                    : new Vector3(endAttach).mulAdd(side, s * 0.5f);
                sagSpan(cb, prev, next);
                prev = next;
            }
        }
        Model m = mb.end();
        all.add(m);
        return m;
    }

    private final Matrix4 tmp2 = new Matrix4();

    private void pylon(MeshPartBuilder b, Matrix4 base, Color steel) {
        float h = PYLON_H, foot = 0.07f, topW = 0.02f;
        // 4 bacak (egik)
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
            Vector3 a = new Vector3(sx * foot, 0, sz * foot), t = new Vector3(sx * topW, h, sz * topW);
            strut(b, base, a, t, 0.012f, steel);
        }
        // capraz baglar
        for (int lv = 0; lv < 3; lv++) {
            float y0 = h * lv / 3f, y1 = h * (lv + 1) / 3f;
            float w0 = MathUtils.lerp(foot, topW, lv / 3f), w1 = MathUtils.lerp(foot, topW, (lv + 1) / 3f);
            strut(b, base, new Vector3(-w0, y0, -w0), new Vector3(w1, y1, -w1), 0.006f, steel);
            strut(b, base, new Vector3(-w0, y0, w0), new Vector3(w1, y1, w1), 0.006f, steel);
            strut(b, base, new Vector3(w0, y0, -w0), new Vector3(w1, y1, w1), 0.006f, steel);
            strut(b, base, new Vector3(-w0, y0, -w0), new Vector3(-w1, y1, w1), 0.006f, steel);
        }
        // kollar (hat yonune dik = yerel X)
        strut(b, base, new Vector3(-PYLON_ARM - 0.02f, h, 0), new Vector3(PYLON_ARM + 0.02f, h, 0), 0.016f, steel);
        strut(b, base, new Vector3(-PYLON_ARM * 0.6f, h + 0.08f, 0), new Vector3(PYLON_ARM * 0.6f, h + 0.08f, 0), 0.012f, steel);
        strut(b, base, new Vector3(0, h - 0.04f, 0), new Vector3(0, h + 0.12f, 0), 0.014f, steel);
    }

    private final Vector3 sa = new Vector3(), sb = new Vector3();
    private final com.badlogic.gdx.math.Quaternion sq = new com.badlogic.gdx.math.Quaternion();

    private void strut(MeshPartBuilder b, Matrix4 base, Vector3 a, Vector3 c2, float thick, Color col) {
        sa.set(a).mul(base); sb.set(c2).mul(base);
        Vector3 d = new Vector3(sb).sub(sa);
        float len = d.len();
        if (len < 1e-4f) return;
        sq.setFromCross(Vector3.Y, d.nor());
        tmp2.set(new Vector3(sa).add(sb).scl(0.5f), sq);
        b.setVertexTransform(tmp2);
        b.setColor(col);
        b.box(0, 0, 0, thick, len, thick);
        b.setVertexTransform(null);
    }

    private void sagSpan(MeshPartBuilder b, Vector3 p, Vector3 q) {
        float len = (float) Math.hypot(q.x - p.x, q.z - p.z);
        int segs = Math.max(2, (int) Math.ceil(len * 3));
        Vector3 prev = new Vector3(p), cur = new Vector3();
        Matrix4 ident = new Matrix4();
        for (int i = 1; i <= segs; i++) {
            float t = i / (float) segs;
            cur.set(MathUtils.lerp(p.x, q.x, t), MathUtils.lerp(p.y, q.y, t) - 0.12f * 4f * t * (1 - t) * Math.min(1f, len / 1.6f), MathUtils.lerp(p.z, q.z, t));
            strut(b, ident, prev, cur, 0.012f, Color.WHITE);
            prev.set(cur);
        }
    }

    public Model flatBox() { return unitBox; }

    @Override public void dispose() {
        for (Model m : all) m.dispose();
        all.clear();
        windowTex.dispose(); windowLitTex.dispose();
    }
}
