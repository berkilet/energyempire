package com.energytycoon.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.energytycoon.sim.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** 3D sahne: arazi, santraller, sehirler, hatlar, golgeler, gun/gece, parcaciklar. */
@SuppressWarnings("deprecation")
public final class WorldRenderer implements Disposable {
    private static final class Light { final Vector3 off; final Color color; final float size; final boolean blink; Light(Vector3 o, Color c, float s, boolean b) { off = o; color = c; size = s; blink = b; } }
    private static final class PlantVis {
        ModelInstance body, flag, rotor; final List<ModelInstance> panels = new ArrayList<>();
        float rotorAngle, hubH, yaw; final List<Light> lights = new ArrayList<>(); String eqId;
        /** Kenney parcalari ve karedeki yerel donusumleri. */
        final List<ModelInstance> parts = new ArrayList<>(); final List<com.badlogic.gdx.math.Matrix4> partLocal = new ArrayList<>();
        /** Duman/buhar cikis noktalari (yerel) ve buhar mi. */
        final List<Vector3> emit = new ArrayList<>(); final List<Boolean> emitSteam = new ArrayList<>();
    }
    private static final class CityVis {
        ModelInstance body; int tier;
        final List<ModelInstance> hi = new ArrayList<>(), lo = new ArrayList<>(), parks = new ArrayList<>();
    }
    private static final class LineVis { Model model; ModelInstance inst; float lastUtil = -1; int sig; LineStatus status; LineTier tier; }
    private static final class Particle { final Vector3 p = new Vector3(); final Vector3 v = new Vector3(); float life, max, size; boolean steam; }

    public final GameState state;
    public final ModelFactory models = new ModelFactory();
    public final TerrainRenderer terrain;
    public final IsoCamera iso;
    private ModelBatch batch;
    private final ModelBatch shadowBatch = new ModelBatch(new DepthShaderProvider());
    private final Environment env = new Environment();
    private DirectionalShadowLight sun;
    private int shadowQuality = 1;
    private final ColorAttribute ambient = new ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.5f, 0.5f, 1);

    private final Map<Integer, PlantVis> plantVis = new HashMap<>();
    private final Map<Integer, CityVis> cityVis = new HashMap<>();
    private final Map<Integer, LineVis> lineVis = new HashMap<>();
    private static final class SubVis { ModelInstance body, flag; int level; }
    private final Map<Integer, SubVis> subVis = new HashMap<>();
    /** Toplayici hatlar (TM -> uye santral), anahtar "tm-santral". */
    private final Map<String, LineVis> collectorVis = new HashMap<>();
    private final List<ModelInstance> tilePool = new ArrayList<>();
    private int collectorSig = -1;
    private final List<ModelInstance> suburbs = new ArrayList<>();
    private final Array<ModelInstance> drawList = new Array<>();
    private final Array<ModelInstance> overlays = new Array<>();
    private final Array<ModelInstance> glows = new Array<>();
    private final List<ModelInstance> glowPool = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<ModelInstance> particlePool = new ArrayList<>();
    private final Random rnd = new Random(1);
    private float spawnAcc, time;
    private int lastSig = -1, lastCitySig = -1;

    // Etkilesim durumu (GameScreen doldurur)
    public int hoverX = -1, hoverY = -1;
    public int selectedNodeId = -1, selectedLineId = -1;
    public Equipment ghostEquipment;
    public boolean ghostValid;
    public GridNode lineFrom, lineHover;
    public boolean lineValid;
    /** Yaricap gosterimi (TM araci veya secili TM): merkez kare, yaricap; radiusX < 0 ise yok. */
    public int radiusX = -1, radiusY;
    public float radius;
    public boolean radiusValid = true;
    public float hourFloat = 12f;

    private ModelInstance ghostBody, highlight, selectBox;
    private final List<ModelInstance> ghostParts = new ArrayList<>();
    private final List<com.badlogic.gdx.math.Matrix4> ghostLocal = new ArrayList<>();
    private String ghostId;
    private Model previewModel; private ModelInstance previewInst; private String previewKey = "";

    private final Vector3 tmpV = new Vector3();

    public WorldRenderer(GameState state) {
        this.state = state;
        this.terrain = new TerrainRenderer(state.map, state.occ);
        this.iso = new IsoCamera(state.map.size);
        env.set(ambient);
        highlight = tinted(models.unitBox, 1, 1, 1, 0.45f);
        selectBox = tinted(models.unitBox, 1, 0.9f, 0.2f, 0.5f);
        String frag = DefaultShader.getDefaultFragmentShader()
            .replace("return step(v_shadowMapUv.z, dot(", "return step(v_shadowMapUv.z - 0.0006, dot(");
        DefaultShader.Config cfg = new DefaultShader.Config(DefaultShader.getDefaultVertexShader(), frag);
        cfg.numDirectionalLights = 1; cfg.numPointLights = 0; cfg.numSpotLights = 0;
        batch = new ModelBatch(new DefaultShaderProvider(cfg));
        setShadowQuality(1);
    }

    /** 0 kapali, 1 orta (1024), 2 yuksek (2048). */
    public void setShadowQuality(int q) {
        shadowQuality = q;
        if (sun != null) { env.remove(sun); sun.dispose(); }
        int res = q == 2 ? 2048 : 1024;
        sun = new DirectionalShadowLight(res, res, 60, 60, 1f, 120f);
        sun.set(1, 1, 1, -0.5f, -1f, -0.4f);
        env.add(sun);
        env.shadowMap = q == 0 ? null : sun;
    }

    private ModelInstance tinted(Model m, float r, float g, float b, float a) {
        ModelInstance mi = new ModelInstance(m);
        Material mat = mi.materials.get(0);
        mat.set(ColorAttribute.createDiffuse(r, g, b, 1));
        mat.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, a));
        return mi;
    }

    private void setTint(ModelInstance mi, Color c, float a) {
        for (Material mat : mi.materials) {
            ColorAttribute ca = (ColorAttribute) mat.get(ColorAttribute.Diffuse);
            if (ca != null) ca.color.set(c.r, c.g, c.b, 1);
            BlendingAttribute ba = (BlendingAttribute) mat.get(BlendingAttribute.Type);
            if (ba != null) ba.opacity = a;
        }
    }

    // -------------------------------------------------------------- yardimci
    /** Dunya noktasini ekran koordinatina (sol-alt orijin) cevirir; kamera onunde ve ekranda ise true. */
    public boolean project(float x, float y, float z, Vector3 out) {
        out.set(x, y, z);
        iso.cam.project(out);
        // Camera.project mantiksal pencere pikseli dondurur (sol-alt orijin)
        return out.z > 0 && out.z < 1 && out.x > -20 && out.y > -20 && out.x < Gdx.graphics.getWidth() + 20 && out.y < Gdx.graphics.getHeight() + 20;
    }

    public float topAt(float wx, float wz) {
        int x = MathUtils.clamp((int) Math.floor(wx), 0, state.map.size - 1);
        int z = MathUtils.clamp((int) Math.floor(wz), 0, state.map.size - 1);
        return state.map.top[state.map.idx(x, z)];
    }

    /** Hat baglanti noktasi: santralde trafo, sehirde merkez. */
    private Vector3 attach(GridNode n, Vector3 out) {
        if (n instanceof City) return out.set(n.x + 0.5f, topAt(n.x + 0.5f, n.y + 0.5f) + 0.5f, n.y + 0.5f);
        if (n instanceof Substation t) return out.set(n.x + 0.5f, topAt(n.x + 0.5f, n.y + 0.5f) + (t.level >= 2 ? 0.48f : 0.36f), n.y + 0.36f);
        PlantVis pv = plantVis.get(n.id);
        float yaw = pv == null ? 0 : pv.yaw;
        tmpV.set(0.33f, 0, 0.36f).rotate(Vector3.Y, yaw);
        return out.set(n.x + 0.5f + tmpV.x, topAt(n.x + 0.5f, n.y + 0.5f) + 0.22f, n.y + 0.5f + tmpV.z);
    }

    // -------------------------------------------------------------- secim
    public int[] pickTile(int sx, int sy) {
        Ray ray = iso.cam.getPickRay(sx, sy);
        Vector3 d = ray.direction, o = ray.origin;
        for (float t = 0; t < 900; t += 0.12f) {
            float px = o.x + d.x * t, py = o.y + d.y * t, pz = o.z + d.z * t;
            int x = (int) Math.floor(px), z = (int) Math.floor(pz);
            if (x < 0 || z < 0 || x >= state.map.size || z >= state.map.size) continue;
            if (py <= state.map.top[state.map.idx(x, z)]) return new int[] { x, z };
        }
        return null;
    }

    /**
     * Ekran uzayinda hat secimi: imlec, hattin ekrandaki cizgisine maxPx pikselden yakinsa secilir (yakinlastirmadan bagimsiz).
     * sx, sy: girdi koordinatlari (sol-ust orijin). Hat; uclar ve direk tepeleri boyunca cizgi parcalariyla yaklasiklanir,
     * planlanan/kuyruktaki hatlar zemindeki kesikli cizgiyle.
     */
    public PowerLine pickLineScreen(int sx, int sy, float maxPx) {
        // Camera.project mantiksal pencere pikseli verir; girdi de mantiksal (sol-ust orijin)
        float px = sx, py = Gdx.graphics.getHeight() - sy;
        double best = maxPx;
        PowerLine bestL = null;
        Vector3 a = new Vector3(), b = new Vector3(), p0 = new Vector3(), p1 = new Vector3();
        for (PowerLine l : state.lines) {
            boolean ground = l.status == LineStatus.PLANNED || l.status == LineStatus.QUEUED;
            if (ground) {
                a.set(l.a.x + 0.5f, topAt(l.a.x + 0.5f, l.a.y + 0.5f) + 0.03f, l.a.y + 0.5f);
                b.set(l.b.x + 0.5f, topAt(l.b.x + 0.5f, l.b.y + 0.5f) + 0.03f, l.b.y + 0.5f);
            } else { attach(l.a, a); attach(l.b, b); }
            float len = (float) Math.hypot(b.x - a.x, b.z - a.z);
            int n = ground ? 1 : Math.max(1, (int) Math.floor(len / 1.6f));
            for (int i = 0; i < n; i++) {
                float t0 = i / (float) n, t1 = (i + 1) / (float) n;
                point(a, b, t0, i == 0, ground, p0);
                point(a, b, t1, i + 1 == n, ground, p1);
                iso.cam.project(p0); iso.cam.project(p1);
                if (p0.z <= 0 || p0.z >= 1 || p1.z <= 0 || p1.z >= 1) continue;
                double d = segDist(px, py, p0.x, p0.y, p1.x, p1.y);
                if (d < best) { best = d; bestL = l; }
            }
        }
        return bestL;
    }

    /** Dogrulama: her hattin ilk parcasinin ortasindan dik yonde 6 ve 12 px uzakta secim denemesi. */
    public String devLinePickTest() {
        int ok6 = 0, miss12 = 0, n = 0, none6 = 0;
        Vector3 a = new Vector3(), b = new Vector3(), p0 = new Vector3(), p1 = new Vector3();
        for (PowerLine l : state.lines) {
            boolean ground = l.status == LineStatus.PLANNED || l.status == LineStatus.QUEUED;
            if (ground) { a.set(l.a.x + 0.5f, topAt(l.a.x + 0.5f, l.a.y + 0.5f) + 0.03f, l.a.y + 0.5f); b.set(l.b.x + 0.5f, topAt(l.b.x + 0.5f, l.b.y + 0.5f) + 0.03f, l.b.y + 0.5f); }
            else { attach(l.a, a); attach(l.b, b); }
            float len = (float) Math.hypot(b.x - a.x, b.z - a.z);
            int segs = ground ? 1 : Math.max(1, (int) Math.floor(len / 1.6f));
            point(a, b, 0, true, ground, p0); point(a, b, 1f / segs, segs == 1, ground, p1);
            iso.cam.project(p0); iso.cam.project(p1);
            float mx = (p0.x + p1.x) / 2, my = (p0.y + p1.y) / 2;
            float dx = p1.x - p0.x, dy = p1.y - p0.y, dl = (float) Math.hypot(dx, dy);
            if (dl < 20 || mx < 20 || my < 20 || mx > Gdx.graphics.getWidth() - 20 || my > Gdx.graphics.getHeight() - 20) continue;
            float nx = -dy / dl, ny = dx / dl;
            n++;
            PowerLine h6 = pickLineScreen(Math.round(mx + nx * 6), Math.round(Gdx.graphics.getHeight() - (my + ny * 6)), 8);
            if (h6 != null && (h6 == l || (h6.a == l.a && h6.b == l.b) || (h6.a == l.b && h6.b == l.a))) ok6++;
            else if (h6 == null) none6++;
            PowerLine h12 = pickLineScreen(Math.round(mx + nx * 12), Math.round(Gdx.graphics.getHeight() - (my + ny * 12)), 8);
            if (h12 != l) miss12++;
        }
        return "LINEPICK 6px isabet " + ok6 + "/" + n + " (hiç seçilmeyen " + none6 + ", kalanında daha yakın başka hat) · 12px'te bu hat seçilmedi " + miss12 + "/" + n;
    }

    private void point(Vector3 a, Vector3 b, float t, boolean end, boolean ground, Vector3 out) {
        float x = MathUtils.lerp(a.x, b.x, t), z = MathUtils.lerp(a.z, b.z, t);
        if (ground || end || t == 0) out.set(x, MathUtils.lerp(a.y, b.y, t), z);
        else out.set(x, topAt(x, z) + ModelFactory.PYLON_H, z); // ara direk tepesi
    }

    private static double segDist(float px, float py, float ax, float ay, float bx, float by) {
        double dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
        return Math.hypot(px - (ax + dx * t), py - (ay + dy * t));
    }

    public PowerLine pickLine(float wx, float wz) {
        PowerLine best = null; double bd = 0.7;
        for (PowerLine l : state.lines) {
            double ax = l.a.x + 0.5, az = l.a.y + 0.5, bx = l.b.x + 0.5, bz = l.b.y + 0.5;
            double dx = bx - ax, dz = bz - az, len2 = dx * dx + dz * dz;
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((wx - ax) * dx + (wz - az) * dz) / len2));
            double d = Math.hypot(wx - (ax + dx * t), wz - (az + dz * t));
            if (d < bd) { bd = d; best = l; }
        }
        return best;
    }

    // -------------------------------------------------------------- senkronizasyon
    private int signature() {
        int sig = state.plants.size() * 31 + state.lines.size() * 17 + state.cities.size();
        for (Plant p : state.plants) sig = sig * 31 + p.id;
        for (PowerLine l : state.lines) sig = sig * 31 + l.id;
        for (Substation t : state.substations) sig = sig * 31 + t.id * 7 + t.level;
        return sig;
    }

    private void sync() {
        int sig = signature();
        int citySig = 0;
        for (City c : state.cities) citySig = citySig * 7 + c.tier();
        if (sig == lastSig && citySig == lastCitySig) return;
        boolean topo = sig != lastSig;
        lastSig = sig; lastCitySig = citySig;
        plantVis.keySet().removeIf(id -> !(state.nodeById(id) instanceof Plant));
        subVis.keySet().removeIf(id -> !(state.nodeById(id) instanceof Substation));
        for (Substation t : state.substations) {
            SubVis sv = subVis.get(t.id);
            if (sv == null || sv.level != t.level) {
                sv = new SubVis(); sv.level = t.level;
                sv.body = new ModelInstance(models.substation(t.level));
                sv.flag = new ModelInstance(models.flag);
                sv.flag.materials.get(0).set(ColorAttribute.createDiffuse(t.owner == null ? new Color(0.75f, 0.92f, 1f, 1) : new Color((t.owner.colorRgb << 8) | 0xFF)));
                subVis.put(t.id, sv);
            }
        }
        for (Plant p : state.plants) if (!plantVis.containsKey(p.id)) plantVis.put(p.id, buildPlantVis(p));
        for (City c : state.cities) {
            CityVis cv = cityVis.computeIfAbsent(c.id, k -> new CityVis());
            if (cv.body == null || cv.tier != c.tier()) buildCityVis(c, cv);
        }
        rebuildSuburbs();
        terrain.refreshOccupancy();
        if (topo) {
            Iterator<Map.Entry<Integer, LineVis>> it = lineVis.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, LineVis> e = it.next();
                if (state.lines.stream().noneMatch(l -> l.id == e.getKey())) { models.untrack(e.getValue().model); it.remove(); }
            }
            for (PowerLine l : state.lines) if (!lineVis.containsKey(l.id)) lineVis.put(l.id, buildLineVis(l));
        }
        for (PowerLine l : state.lines) {
            LineVis lv = lineVis.get(l.id);
            if (lv != null && lv.tier != l.tier) { models.untrack(lv.model); lineVis.put(l.id, lv = buildLineVis(l)); }
            if (lv != null && lv.status != l.status) applyLineStatus(l, lv);
        }
    }

    private PlantVis buildPlantVis(Plant p) {
        PlantVis pv = new PlantVis();
        Equipment e = p.equipment;
        pv.eqId = e.id;
        List<Prefabs.Part> prefab = Prefabs.of(e);
        pv.body = new ModelInstance(prefab != null ? models.plantBase(e) : models.plant(e));
        if (prefab != null) {
            Color warning = new Color(1f, 0.15f, 0.1f, 1);
            for (Prefabs.Part part : prefab) {
                pv.parts.add(new ModelInstance(KenneyModels.get().model(part.path)));
                pv.partLocal.add(part.local());
                if (part.emit != Prefabs.Emit.NONE) {
                    Vector3 top = part.top();
                    pv.emit.add(new Vector3(top).add(0, 0.02f, 0));
                    pv.emitSteam.add(part.emit == Prefabs.Emit.STEAM);
                    pv.lights.add(new Light(new Vector3(top).add(0, 0.015f, 0), warning, 0.025f, true));
                }
            }
            pv.lights.add(new Light(new Vector3(0.33f, 0.12f, 0.36f), new Color(1f, 0.8f, 0.45f, 1), 0.022f, false));
        }
        pv.flag = new ModelInstance(models.flag);
        pv.flag.materials.get(0).set(ColorAttribute.createDiffuse(new Color((p.owner.colorRgb << 8) | 0xFF)));
        float top = 0;
        if (p.type == PlantType.HYDRO) pv.yaw = waterYaw(p);
        switch (p.type) {
            case WIND -> {
                pv.rotor = new ModelInstance(models.rotor(e.visB));
                pv.hubH = 0.03f + e.visA;
                pv.lights.add(new Light(new Vector3(0, pv.hubH + 0.07f, 0.03f), new Color(1f, 0.1f, 0.05f, 1), 0.035f, true));
            }
            case SOLAR -> { for (int i = 0; i < 4; i++) pv.panels.add(new ModelInstance(models.panelRow(e))); }
            case COAL -> {
                if (prefab != null) break;
                float sh = e.visA == 0 ? 0.77f : e.visA == 1 ? 0.75f : 0.95f;
                float sx = e.visA == 2 ? 0.18f : e.visA == 1 ? 0.34f : 0.28f, sz = e.visA == 2 ? 0.28f : e.visA == 1 ? -0.32f : -0.22f;
                pv.lights.add(new Light(new Vector3(sx, sh, sz), new Color(1f, 0.15f, 0.1f, 1), 0.03f, true));
                pv.lights.add(new Light(new Vector3(-0.1f, 0.3f, 0.36f), new Color(1f, 0.8f, 0.45f, 1), 0.025f, false));
            }
            case GAS -> { if (prefab == null) pv.lights.add(new Light(new Vector3(0.22f, 0.8f, 0.05f), new Color(1f, 0.15f, 0.1f, 1), 0.025f, true)); }
            case NUCLEAR -> {
                if (prefab != null) break;
                pv.lights.add(new Light(new Vector3(0.25f, 0.92f, -0.22f), new Color(1f, 0.15f, 0.1f, 1), 0.03f, true));
                pv.lights.add(new Light(new Vector3(-0.28f, 0.2f, 0.47f), new Color(0.75f, 0.85f, 1f, 1), 0.03f, false));
            }
            case HYDRO -> pv.lights.add(new Light(new Vector3(0.0f, 0.3f, 0.2f), new Color(1f, 0.8f, 0.45f, 1), 0.025f, false));
        }
        return pv;
    }

    /** Bu yakinlastirmanin altinda sehirlerde sade (low-detail) modeller kullanilir. */
    private static final float LOD_PPU = 17f;
    private static final String[] SKY = { "building-skyscraper-a", "building-skyscraper-b", "building-skyscraper-c", "building-skyscraper-d", "building-skyscraper-e" };
    private static final String[] MID = { "building-f", "building-g", "building-i", "building-l", "building-m" };
    private static final String[] LOW = { "building-a", "building-b", "building-c", "building-d", "building-h" };

    /** Kenney Commercial binalariyla sehir: merkezde gokdelenler, disa dogru alcalan yapilar. */
    private void buildCityVis(City c, CityVis cv) {
        cv.tier = c.tier();
        cv.body = new ModelInstance(models.cityBase());
        cv.hi.clear(); cv.lo.clear(); cv.parks.clear();
        Random r = new Random(c.id * 7919L + cv.tier * 31L);
        int tier = cv.tier;
        float top = state.map.topAt(c.x, c.y);
        KenneyModels km = KenneyModels.get();
        for (int gx = -2; gx <= 2; gx++) for (int gz = -2; gz <= 2; gz++) {
            int ring = Math.max(Math.abs(gx), Math.abs(gz));
            float cx = c.x + 0.5f + gx * 0.56f, cz = c.y + 0.5f + gz * 0.56f;
            float roll = r.nextFloat();
            if (ring == 2 && roll < 0.28f - tier * 0.04f) continue;                       // bos arsa (kucuk sehirde)
            if (ring > 0 && r.nextFloat() < (tier >= 5 && ring == 1 ? 0.04f : 0.11f)) {   // park
                ModelInstance pk = new ModelInstance(models.park());
                pk.transform.setToTranslation(cx, top + 0.04f, cz);
                cv.parks.add(pk);
                continue;
            }
            float pick = r.nextFloat();
            String[] pool;
            if (ring == 0) pool = tier >= 3 ? SKY : MID;
            else if (ring == 1) pool = tier >= 5 ? (pick < 0.6f ? SKY : MID) : tier >= 3 ? (pick < 0.25f ? SKY : MID) : (pick < 0.5f ? MID : LOW);
            else pool = tier >= 4 ? (pick < 0.45f ? MID : LOW) : LOW;
            String name = pool[r.nextInt(pool.length)];
            String path = "commercial/" + name;
            com.badlogic.gdx.math.collision.BoundingBox bb = km.bounds(path);
            float s = 0.46f / Math.max(bb.getWidth(), bb.getDepth());
            float yaw = 90f * r.nextInt(4);
            float tr = r.nextFloat();
            int alt = tr < 0.55f ? 0 : tr < 0.80f ? 1 : 2;
            Vector3 ctr = bb.getCenter(new Vector3());
            com.badlogic.gdx.math.Matrix4 m = new com.badlogic.gdx.math.Matrix4().translate(cx, top + 0.04f, cz)
                .rotate(Vector3.Y, yaw).scale(s, s, s).translate(-ctr.x, -bb.min.y, -ctr.z);
            ModelInstance hi = new ModelInstance(km.model(path));
            hi.transform.set(m);
            applyVariation(hi, alt);
            cv.hi.add(hi);
            String low = name.startsWith("building-skyscraper") ? path : "commercial/low-detail-" + name;
            ModelInstance lo = new ModelInstance(km.model(low));
            com.badlogic.gdx.math.collision.BoundingBox lb = km.bounds(low);
            Vector3 lc = lb.getCenter(new Vector3());
            lo.transform.set(new com.badlogic.gdx.math.Matrix4().translate(cx, top + 0.04f, cz).rotate(Vector3.Y, yaw).scale(s, s, s).translate(-lc.x, -lb.min.y, -lc.z));
            applyVariation(lo, alt);
            cv.lo.add(lo);
        }
    }

    private static void applyVariation(ModelInstance mi, int alt) {
        if (alt == 0) return;
        KenneyModels km = KenneyModels.get();
        for (Material m : mi.materials) {
            m.set(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.createDiffuse(km.palette(KenneyModels.COMMERCIAL, alt)));
            m.set(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.createEmissive(km.nightMap(KenneyModels.COMMERCIAL, alt)));
        }
    }

    /** Hidro barajinin suya bakmasi icin yaw (model -Z suya bakar). */
    private float waterYaw(Plant p) {
        int[][] dirs = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 }, { 1, -1 }, { 1, 1 }, { -1, 1 }, { -1, -1 } };
        for (int[] d : dirs) {
            int x = p.x + d[0], y = p.y + d[1];
            if (state.map.inBounds(x, y) && state.map.isWater(x, y)) {
                return MathUtils.atan2(-d[0], -d[1]) * MathUtils.radiansToDegrees;
            }
        }
        return 0;
    }

    private LineVis buildLineVis(PowerLine l) {
        LineVis lv = new LineVis();
        Vector3 a = attach(l.a, new Vector3()), b = attach(l.b, new Vector3());
        // ayni iki nokta arasi paralel hatlari kaydir
        int idx = 0;
        for (PowerLine o : state.lines) { if (o == l) break; if ((o.a == l.a && o.b == l.b) || (o.a == l.b && o.b == l.a)) idx++; }
        Vector3 perp = new Vector3(b.z - a.z, 0, a.x - b.x).nor().scl(idx * 0.35f);
        a.add(perp); b.add(perp);
        float len = (float) Math.hypot(b.x - a.x, b.z - a.z);
        int n = Math.max(1, (int) Math.floor(len / 1.6f));
        List<Vector3> pylons = new ArrayList<>();
        for (int i = 1; i < n; i++) {
            float t = i / (float) n;
            float x = MathUtils.lerp(a.x, b.x, t), z = MathUtils.lerp(a.z, b.z, t);
            pylons.add(new Vector3(x, topAt(x, z), z));
        }
        // kademeye gore iletken kalinligi: OG ince, ÇYG kalin
        models.cableWidth = l.tier == LineTier.OG ? 0.008f : l.tier == LineTier.YG ? 0.012f : 0.019f;
        lv.model = models.buildLine(pylons, a, b);
        models.cableWidth = 0.012f;
        lv.tier = l.tier;
        lv.inst = new ModelInstance(lv.model);
        applyLineStatus(l, lv);
        return lv;
    }

    /** Insaatta: yari saydam; planlama/kuyruk: model cizilmez, zeminde kesikli cizgi. */
    private void applyLineStatus(PowerLine l, LineVis lv) {
        lv.status = l.status;
        float a = l.status == LineStatus.BUILDING ? 0.4f : 1f;
        for (Material m : lv.inst.materials) {
            if (a < 1f) m.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, a));
            else m.remove(BlendingAttribute.Type);
        }
        lv.lastUtil = -1;
    }

    /** Sehir cevresinde nufusa gore banliyo evleri; agaclari bu karelerden kaldirir. */
    private RoadNetwork roads;

    private void rebuildSuburbs() {
        suburbs.clear();
        houseAt.clear();
        java.util.Arrays.fill(terrain.reserved, false);
        if (roads == null) { roads = new RoadNetwork(state.map); terrain.roads = roads; }
        long rt0 = System.nanoTime();
        roads.build(state.occ, state.cities, state.plants);
        if (System.getenv("ET_ROADDBG") != null) System.out.println("ROADDBG build ms=" + (System.nanoTime() - rt0) / 1e6);
        for (int i = 0; i < roads.links.length; i++) if (roads.road(i)) terrain.reserved[i] = true;
        for (City c : state.cities) {
            int tier = c.tier();
            int rad = RoadNetwork.suburbRadius(c);
            for (int dy = -rad; dy <= rad; dy++) for (int dx = -rad; dx <= rad; dx++) {
                int x = c.x + dx, y = c.y + dy;
                if (!state.map.inBounds(x, y)) continue;
                int i = state.map.idx(x, y);
                if (Math.max(Math.abs(dx), Math.abs(dy)) <= 1) { terrain.reserved[i] = true; continue; }
                if (state.map.water[i] || state.occ[i] >= 0 || roads.road(i) || houseAt.contains(i)) continue;
                // ev parseli yalnizca ayni seviyedeki bir sehir sokagina cephe verir
                // ayni seviyedeki sokak tercih edilir; yoksa bir kademe farkli sokaga da cephe verebilir (yamac evi)
                int face = -1;
                float bestDh = 9;
                for (int d = 0; d < 4; d++) {
                    int nx = x + RoadNetwork.DX[d], ny = y + RoadNetwork.DY[d];
                    if (!state.map.inBounds(nx, ny)) continue;
                    int j = state.map.idx(nx, ny);
                    float dh = Math.abs(state.map.top[j] - state.map.top[i]);
                    if (roads.road(j) && !roads.bridge[j] && dh < com.energytycoon.world.WorldMap.LEVEL_H + 0.01f && dh < bestDh - 0.01f) { face = d; bestDh = dh; }
                }
                if (face < 0) continue;
                double dist = Math.hypot(dx, dy);
                double density = 1.45 - dist / (rad + 2.0) + tier * 0.05;
                if (hash(x, y) > density) continue;
                terrain.reserved[i] = true;
                houseAt.add(i);
                int cheb = Math.max(Math.abs(dx), Math.abs(dy));
                boolean apt = cheb <= RoadNetwork.apartmentRadius(c) && tier >= 2;
                Model[] set = apt ? models.apartmentModels : models.houseModels;
                ModelInstance mi = new ModelInstance(set[(int) (hash(y, x) * 4) % 4]);
                // parsel modeli kuzeye (-z) bakar; face yonune dondur (N=0, W=90, S=180, E=270)
                int k = new int[] { 0, 3, 2, 1 }[face];
                mi.transform.setToTranslation(x + 0.5f, state.map.top[i], y + 0.5f).rotate(Vector3.Y, 90f * k);
                suburbs.add(mi);
            }
        }
        if (System.getenv("ET_ROADDBG") != null) System.out.println("ROADDBG houses=" + suburbs.size() + " roads=" + java.util.stream.IntStream.range(0, roads.links.length).filter(roads::road).count());
    }

    private final java.util.HashSet<Integer> houseAt = new java.util.HashSet<>();

    private static float hash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    private static final Color C_OK = new Color(0.25f, 0.85f, 0.35f, 1), C_MID = new Color(0.95f, 0.8f, 0.2f, 1),
                               C_HI = new Color(0.95f, 0.25f, 0.2f, 1), C_IDLE = new Color(0.14f, 0.14f, 0.15f, 1);

    private void updateLineColors() {
        for (PowerLine l : state.lines) {
            LineVis lv = lineVis.get(l.id);
            if (lv == null) continue;
            if (l.outageHoursLeft > 0) {
                // arizali hat: kirmizi yanip soner
                Material m = lv.inst.getMaterial("cable");
                if (m != null) ((ColorAttribute) m.get(ColorAttribute.Diffuse)).color.set((time * 3f) % 1f < 0.5f ? C_HI : C_IDLE);
                lv.lastUtil = -1;
                continue;
            }
            float u = (float) l.utilization();
            if (Math.abs(u - lv.lastUtil) < 0.01f) continue;
            lv.lastUtil = u;
            Color c = u < 0.02f ? C_IDLE : u < 0.6f ? C_OK : u < 0.95f ? C_MID : C_HI;
            Material m = lv.inst.getMaterial("cable");
            if (m != null) ((ColorAttribute) m.get(ColorAttribute.Diffuse)).color.set(c);
        }
    }

    // -------------------------------------------------------------- gun/gece
    public float daylight() {
        float s = MathUtils.sin(MathUtils.PI * (hourFloat - 6f) / 12f);
        return MathUtils.clamp(s * 1.6f + 0.15f, 0f, 1f);
    }

    private float nightFactor() { return 1 - daylight(); }

    private final Vector3 sunDir = new Vector3();

    private void updateLighting() {
        float d = daylight(), night = 1 - d;
        // Gunes kameraya gore konumlanir: golgeler ekranin sag-altina (kameraya dogru) duser ve gorunur kalir.
        // Gun icinde azimut ±35 derece doner; yukseklik ogle en fazla.
        float yawR = iso.yaw * MathUtils.degreesToRadians;
        float fx = -MathUtils.sin(yawR), fz = -MathUtils.cos(yawR);      // kamera ileri (yatay)
        float rx = MathUtils.cos(yawR), rz = -MathUtils.sin(yawR);       // kamera sag
        float sweep = MathUtils.clamp((hourFloat - 12f) / 6f, -1f, 1f) * 35f * MathUtils.degreesToRadians;
        float base = MathUtils.atan2(0.62f, 0.78f);                      // sag agirlikli, hafif kameraya dogru
        float ang = base + sweep;
        float hx = rx * MathUtils.cos(ang) - fx * MathUtils.sin(ang), hz = rz * MathUtils.cos(ang) - fz * MathUtils.sin(ang);
        float a = MathUtils.PI * MathUtils.clamp((hourFloat - 6f) / 12f, 0f, 1f);
        float elev = night > 0.6f ? 1.4f : 0.75f + MathUtils.sin(a) * 0.75f;
        sunDir.set(hx, -elev, hz).nor();
        float warm = MathUtils.clamp(1 - Math.abs(hourFloat - 12f) / 6f, 0, 1); // ogle 1, sabah/aksam 0
        float lr = MathUtils.lerp(1.0f, 0.95f, warm), lg = MathUtils.lerp(0.72f, 0.92f, warm), lb = MathUtils.lerp(0.50f, 0.84f, warm);
        float inten = MathUtils.lerp(0.18f, 0.95f, d);
        sun.set(MathUtils.lerp(0.20f, lr * inten, d), MathUtils.lerp(0.24f, lg * inten, d), MathUtils.lerp(0.40f, lb * inten, d), sunDir);
        ambient.color.set(MathUtils.lerp(0.44f, 0.17f, night), MathUtils.lerp(0.47f, 0.20f, night), MathUtils.lerp(0.54f, 0.33f, night), 1);
        float glow = MathUtils.clamp((night - 0.25f) / 0.6f, 0, 1);
        for (CityVis cv : cityVis.values()) {
            for (ModelInstance mi : cv.hi) setEmissive(mi, glow);
            for (ModelInstance mi : cv.lo) setEmissive(mi, glow);
        }
        for (PlantVis pv : plantVis.values()) for (ModelInstance mi : pv.parts) setEmissive(mi, glow * 0.7f);
        for (ModelInstance mi : suburbs) setEmissive(mi, glow * 0.9f);
    }

    private static void setEmissive(ModelInstance mi, float k) {
        if (mi == null) return;
        for (Material m : mi.materials) {
            if (!"walls".equals(m.id) && !KenneyModels.LIT_MATERIAL.equals(m.id)) continue;
            ColorAttribute ca = (ColorAttribute) m.get(ColorAttribute.Emissive);
            if (ca != null) ca.color.set(k, k, k, 1);
        }
    }

    public Color skyColor() {
        float n = nightFactor();
        float dusk = MathUtils.clamp(1 - Math.abs(hourFloat - 18.5f) / 1.8f, 0, 1) + MathUtils.clamp(1 - Math.abs(hourFloat - 6f) / 1.5f, 0, 1);
        Color c = new Color(MathUtils.lerp(0.60f, 0.03f, n), MathUtils.lerp(0.77f, 0.045f, n), MathUtils.lerp(0.90f, 0.10f, n), 1);
        return c.lerp(0.85f, 0.50f, 0.38f, 1, Math.min(0.5f, dusk * 0.5f));
    }

    // -------------------------------------------------------------- cizim
    public void render(float dt, int w, int h) {
        time += dt;
        terrain.animate(time);
        sync();
        iso.update(dt, w, h);
        updateLighting();
        updateLineColors();
        animate(dt);

        drawList.clear(); overlays.clear(); glows.clear();
        drawList.addAll(terrain.instances);
        float nf = nightFactor();
        int glowIdx = 0;

        for (Plant p : state.plants) {
            PlantVis pv = plantVis.get(p.id);
            if (pv == null) continue;
            float top = state.map.topAt(p.x, p.y);
            float cx = p.x + 0.5f, cz = p.y + 0.5f;
            float prog = 1f;
            if (!p.operational()) prog = Math.max(0.12f, 1f - p.buildHoursLeft / (p.equipment.buildDays() * 24f));
            pv.body.transform.setToTranslation(cx, top, cz).rotate(Vector3.Y, pv.yaw).scale(1, prog, 1);
            drawList.add(pv.body);
            for (int i = 0; i < pv.parts.size(); i++) {
                ModelInstance mi = pv.parts.get(i);
                mi.transform.set(pv.body.transform).mul(pv.partLocal.get(i));
                drawList.add(mi);
            }
            pv.flag.transform.setToTranslation(cx - 0.42f, top, cz + 0.42f);
            ((ColorAttribute) pv.flag.materials.get(0).get(ColorAttribute.Diffuse)).color.set((state.displayColor(p.owner) << 8) | 0xFF);
            drawList.add(pv.flag);
            boolean built = p.operational();
            if (pv.rotor != null) {
                float hubY = top + pv.hubH * prog;
                pv.rotor.transform.setToTranslation(cx, hubY, cz + 0.17f).rotate(0, 0, 1, pv.rotorAngle);
                drawList.add(pv.rotor);
            }
            if (!pv.panels.isEmpty() && built) {
                boolean tracker = p.upgrades.contains(Upgrade.TRACKER);
                float postH = p.equipment.visA == 2 ? 0.20f : 0.145f;
                float trackAngle = MathUtils.clamp((hourFloat - 12f) / 6f * 50f, -55f, 55f);
                if (hourFloat < 5.5f || hourFloat > 18.5f) trackAngle = 0;
                for (int i = 0; i < pv.panels.size(); i++) {
                    ModelInstance mi = pv.panels.get(i);
                    float off = -0.34f + i * 0.23f;
                    if (tracker) mi.transform.setToTranslation(cx + off * 0.95f, top + postH, cz).rotate(0, 1, 0, 90f).rotate(1, 0, 0, trackAngle);
                    else mi.transform.setToTranslation(cx, top + postH, cz + off).rotate(1, 0, 0, -25f);
                    drawList.add(mi);
                }
            }
            if (nf > 0.3f && built) {
                for (Light lt : pv.lights) {
                    float k = lt.blink ? (MathUtils.sin(time * 3.5f + p.id) > 0.3f ? 1f : 0.1f) : 1f;
                    tmpV.set(lt.off).rotate(Vector3.Y, pv.yaw);
                    glowIdx = addGlow(glowIdx, cx + tmpV.x, top + tmpV.y, cz + tmpV.z, lt.color, lt.size, k * (nf - 0.3f) / 0.7f);
                }
            }
        }
        for (City c : state.cities) {
            CityVis cv = cityVis.get(c.id);
            if (cv == null) continue;
            cv.body.transform.setToTranslation(c.x + 0.5f, state.map.topAt(c.x, c.y), c.y + 0.5f);
            drawList.add(cv.body);
            for (ModelInstance mi : (iso.ppu < LOD_PPU ? cv.lo : cv.hi)) drawList.add(mi);
            for (ModelInstance mi : cv.parks) drawList.add(mi);
        }
        for (ModelInstance mi : suburbs) drawList.add(mi);
        int dashIdx = 0;
        for (PowerLine l : state.lines) {
            LineVis lv = lineVis.get(l.id);
            if (lv == null) continue;
            if (l.status == LineStatus.PLANNED || l.status == LineStatus.QUEUED) dashIdx = dashes(dashIdx, l);
            else if (l.status == LineStatus.BUILDING) overlays.add(lv.inst); // saydam: golge ve opak geciste yok
            else drawList.add(lv.inst);
        }

        renderGridExtras();

        // secim ve hover
        if (selectedNodeId >= 0) {
            GridNode n = state.nodeById(selectedNodeId);
            if (n != null) {
                float s = n instanceof City ? 3.05f : 1.05f;
                selectBox.transform.setToTranslation(n.x + 0.5f - s / 2, state.map.topAt(n.x, n.y) + 0.02f, n.y + 0.5f - s / 2).scale(s, 0.06f, s);
                overlays.add(selectBox);
                // baglanti noktasinin (kume/TM) diger uyeleri de vurgulanir
                ConnectionPoint cp = n instanceof Plant p ? p.cp : n instanceof Substation t ? t.cp : null;
                if (cp != null && (cp.members.size() > 1 || cp.tm != null)) {
                    int ti = selTileBase;
                    for (Plant m : cp.members) if (m != n) ti = tile(ti, m.x, m.y, SEL_CP, 0.4f, 1.04f);
                    if (cp.tm != null && cp.tm != n) ti = tile(ti, cp.tm.x, cp.tm.y, SEL_CP, 0.4f, 1.04f);
                }
            }
        }
        if (selectedLineId >= 0) {
            LineVis lv = lineVis.get(selectedLineId);
            if (lv != null) {
                Material m = lv.inst.getMaterial("cable");
                if (m != null) ((ColorAttribute) m.get(ColorAttribute.Diffuse)).color.set(1f, 0.9f, 0.2f, 1);
                lv.lastUtil = -1;
            }
        }
        if (hoverX >= 0 && hoverY >= 0 && state.map.inBounds(hoverX, hoverY)) {
            Color hc = ghostEquipment != null ? (ghostValid ? C_OK : C_HI) : lineFrom != null ? (lineValid ? C_OK : C_HI) : Color.WHITE;
            setTint(highlight, hc, 0.5f);
            highlight.transform.setToTranslation(hoverX, state.map.topAt(hoverX, hoverY) + 0.015f, hoverY).scale(1, 0.05f, 1);
            overlays.add(highlight);
            if (ghostEquipment != null) {
                if (ghostBody == null || !ghostEquipment.id.equals(ghostId)) {
                    List<Prefabs.Part> prefab = Prefabs.of(ghostEquipment);
                    ghostBody = new ModelInstance(prefab != null ? models.plantBase(ghostEquipment) : models.plant(ghostEquipment));
                    ghostId = ghostEquipment.id;
                    ghostParts.clear(); ghostLocal.clear();
                    if (prefab != null) for (Prefabs.Part part : prefab) {
                        ModelInstance gi = new ModelInstance(KenneyModels.get().model(part.path));
                        ghostParts.add(gi); ghostLocal.add(part.local());
                    }
                    for (Material m : ghostBody.materials) m.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.6f));
                    for (ModelInstance gi : ghostParts) for (Material m : gi.materials) m.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.6f));
                }
                Color gc = ghostValid ? Color.WHITE : new Color(1f, 0.45f, 0.45f, 1);
                setTint(ghostBody, gc, 0.65f);
                ghostBody.transform.setToTranslation(hoverX + 0.5f, state.map.topAt(hoverX, hoverY) + 0.03f, hoverY + 0.5f);
                overlays.add(ghostBody);
                for (int i = 0; i < ghostParts.size(); i++) {
                    ModelInstance gi = ghostParts.get(i);
                    setTint(gi, gc, 0.65f);
                    gi.transform.set(ghostBody.transform).mul(ghostLocal.get(i));
                    overlays.add(gi);
                }
            }
        }
        updatePreview();
        if (previewInst != null) overlays.add(previewInst);
        updateParticleInstances();
        for (int i = 0; i < glowIdx; i++) glows.add(glowPool.get(i));

        // golge gecisi
        if (shadowQuality > 0) {
            OrthographicCamera sc = (OrthographicCamera) sun.getCamera();
            float span = Math.max(iso.cam.viewportWidth, iso.cam.viewportHeight * 1.8f) * 1.15f;
            span = Math.min(span, state.map.size * 1.5f);
            sc.viewportWidth = span; sc.viewportHeight = span;
            sun.begin(tmpV.set(iso.target.x, 1.5f, iso.target.z), sunDir);
            shadowBatch.begin(sun.getCamera());
            shadowBatch.render(drawList);
            shadowBatch.end();
            sun.end();
        }

        Color sky = skyColor();
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Gdx.gl.glClearColor(sky.r, sky.g, sky.b, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT | (Gdx.graphics.getBufferFormat().coverageSampling ? GL20.GL_COVERAGE_BUFFER_BIT_NV : 0));
        batch.begin(iso.cam);
        batch.render(drawList, env);
        batch.render(activeParticleInstances, env);
        batch.render(overlays, env);
        batch.render(glows, env);
        batch.end();
    }

    /** Trafo merkezleri, toplayici hatlar, kume cercevesi ve yaricap gosterimi. */
    private void renderGridExtras() {
        for (Substation t : state.substations) {
            SubVis sv = subVis.get(t.id);
            if (sv == null) continue;
            float top = state.map.topAt(t.x, t.y);
            float prog = t.operational() ? 1f : Math.max(0.15f, 1f - t.buildHoursLeft / (state.substationDays(t.level) * 24f));
            sv.body.transform.setToTranslation(t.x + 0.5f, top, t.y + 0.5f).scale(1, prog, 1);
            drawList.add(sv.body);
            sv.flag.transform.setToTranslation(t.x + 0.08f, top, t.y + 0.92f);
            if (t.owner != null) ((ColorAttribute) sv.flag.materials.get(0).get(ColorAttribute.Diffuse)).color.set((state.displayColor(t.owner) << 8) | 0xFF);
            drawList.add(sv.flag);
        }
        // toplayici hatlar: topoloji degistikce yeniden
        int sig = 0;
        for (ConnectionPoint cp : state.connectionPoints()) if (cp.tm != null) for (Plant m : cp.members) sig = sig * 31 + cp.tm.id * 1009 + m.id;
        if (sig != collectorSig) {
            collectorSig = sig;
            for (LineVis lv : collectorVis.values()) models.untrack(lv.model);
            collectorVis.clear();
            for (ConnectionPoint cp : state.connectionPoints()) if (cp.tm != null) for (Plant m : cp.members) {
                LineVis lv = new LineVis();
                Vector3 a = attach(cp.tm, new Vector3()).add(0, -0.18f, 0), b = attach(m, new Vector3());
                lv.model = models.buildLine(new ArrayList<>(), a, b);
                lv.inst = new ModelInstance(lv.model);
                Material mat = lv.inst.getMaterial("cable");
                if (mat != null) ((ColorAttribute) mat.get(ColorAttribute.Diffuse)).color.set(m.owner == cp.tm.owner ? new Color(0.35f, 0.6f, 0.9f, 1) : new Color(0.95f, 0.6f, 0.2f, 1));
                collectorVis.put(cp.tm.id + "-" + m.id, lv);
            }
        }
        for (LineVis lv : collectorVis.values()) drawList.add(lv.inst);

        // ince kume cercevesi: oyuncunun bara/TM uyeleri altinda soluk renkli taban
        int ti = 0;
        for (ConnectionPoint cp : state.connectionPoints()) {
            if (cp.members.size() < 2 && cp.tm == null) continue;
            Company owner = cp.tm != null ? cp.tm.owner : cp.members.get(0).owner;
            if (owner == null) continue;
            if (!state.playerGroup(owner)) continue;
            Color col = cp.tm != null ? new Color(0.35f, 0.6f, 0.9f, 1) : new Color(1f, 0.85f, 0.3f, 1);
            for (Plant m : cp.members) ti = tile(ti, m.x, m.y, col, 0.22f, 1.06f);
        }
        // yaricap
        if (radiusX >= 0) {
            int r = (int) Math.ceil(radius);
            Color col = radiusValid ? new Color(0.35f, 0.75f, 1f, 1) : C_HI;
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                int x = radiusX + dx, y = radiusY + dy;
                if (!state.map.inBounds(x, y) || Math.hypot(dx, dy) > radius + 1e-6) continue;
                ti = tile(ti, x, y, col, dx == 0 && dy == 0 ? 0.45f : 0.18f, 1f);
            }
        }
        selTileBase = ti;
    }

    private final List<ModelInstance> dashPool = new ArrayList<>();
    /** renderGridExtras'in kullandigi kare sayisi; secim vurgusu havuzda bunun ardindan baslar. */
    private int selTileBase;
    private static final Color SEL_CP = new Color(1f, 0.9f, 0.2f, 1);
    private static final Color C_PLAN = new Color(0.55f, 0.8f, 1f, 1), C_QUEUE = new Color(1f, 0.8f, 0.35f, 1);

    /** Planlanan/kuyruktaki hat: iki uc arasinda zeminde kesikli cizgi (UŞİ planlari mavi, basvurular sari). */
    private int dashes(int idx, PowerLine l) {
        float ax = l.a.x + 0.5f, az = l.a.y + 0.5f, bx = l.b.x + 0.5f, bz = l.b.y + 0.5f;
        float len = (float) Math.hypot(bx - ax, bz - az);
        int n = Math.max(2, (int) (len / 0.6f));
        float yaw = MathUtils.atan2(bx - ax, bz - az) * MathUtils.radiansToDegrees;
        Color c = l.operatorPlan ? C_PLAN : C_QUEUE;
        for (int i = 0; i < n; i += 2) {
            float t = (i + 0.5f) / n;
            float x = MathUtils.lerp(ax, bx, t), z = MathUtils.lerp(az, bz, t);
            while (dashPool.size() <= idx) dashPool.add(tinted(models.unitBox, 1, 1, 1, 0.85f));
            ModelInstance mi = dashPool.get(idx++);
            setTint(mi, c, 0.85f);
            mi.transform.setToTranslation(x, topAt(x, z) + 0.03f, z).rotate(Vector3.Y, yaw).translate(-0.06f, 0, -len / n * 0.5f).scale(0.12f, 0.03f, len / n);
            overlays.add(mi);
        }
        return idx;
    }

    private int tile(int idx, int x, int y, Color c, float alpha, float scale) {
        while (tilePool.size() <= idx) tilePool.add(tinted(models.unitBox, 1, 1, 1, 0.2f));
        ModelInstance mi = tilePool.get(idx);
        setTint(mi, c, alpha);
        float o = (1 - scale) / 2;
        mi.transform.setToTranslation(x + o, state.map.topAt(x, y) + 0.012f, y + o).scale(scale, 0.03f, scale);
        overlays.add(mi);
        return idx + 1;
    }

    private int addGlow(int idx, float x, float y, float z, Color c, float size, float k) {
        while (glowPool.size() <= idx * 2 + 1) {
            ModelInstance core = new ModelInstance(models.glow);
            core.materials.get(0).set(ColorAttribute.createDiffuse(0, 0, 0, 1), ColorAttribute.createEmissive(1, 1, 1, 1));
            ModelInstance halo = new ModelInstance(models.glow);
            halo.materials.get(0).set(ColorAttribute.createDiffuse(0, 0, 0, 1), ColorAttribute.createEmissive(1, 1, 1, 1),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE, 0.3f));
            glowPool.add(core); glowPool.add(halo);
        }
        ModelInstance core = glowPool.get(idx * 2), halo = glowPool.get(idx * 2 + 1);
        ((ColorAttribute) core.materials.get(0).get(ColorAttribute.Emissive)).color.set(c.r * k, c.g * k, c.b * k, 1);
        ((ColorAttribute) halo.materials.get(0).get(ColorAttribute.Emissive)).color.set(c.r * k, c.g * k, c.b * k, 1);
        ((BlendingAttribute) halo.materials.get(0).get(BlendingAttribute.Type)).opacity = 0.35f * k;
        core.transform.setToTranslation(x, y, z).scale(size, size, size);
        halo.transform.setToTranslation(x, y, z).scale(size * 5f, size * 5f, size * 5f);
        return idx + 1;
    }

    private int glowCount(int idx) { return idx * 2; }

    /** Hat cekme onizlemesi: tek model, uclar degisince yeniden kurulur. */
    private void updatePreview() {
        if (lineFrom == null || hoverX < 0) {
            if (previewModel != null) { models.untrack(previewModel); previewModel = null; previewInst = null; previewKey = ""; }
            return;
        }
        Vector3 a = attach(lineFrom, new Vector3());
        Vector3 b = lineHover != null ? attach(lineHover, new Vector3())
                                      : new Vector3(hoverX + 0.5f, state.map.topAt(hoverX, hoverY) + 0.5f, hoverY + 0.5f);
        String key = lineFrom.id + ":" + (lineHover != null ? "n" + lineHover.id : hoverX + "," + hoverY);
        if (!key.equals(previewKey)) {
            if (previewModel != null) models.untrack(previewModel);
            float len = (float) Math.hypot(b.x - a.x, b.z - a.z);
            int n = Math.max(1, (int) Math.floor(len / 1.6f));
            List<Vector3> pylons = new ArrayList<>();
            for (int i = 1; i < n; i++) {
                float t = i / (float) n;
                float x = MathUtils.lerp(a.x, b.x, t), z = MathUtils.lerp(a.z, b.z, t);
                pylons.add(new Vector3(x, topAt(x, z), z));
            }
            previewModel = models.buildLine(pylons, a, b);
            previewInst = new ModelInstance(previewModel);
            for (Material m : previewInst.materials) m.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.75f));
            previewKey = key;
        }
        Color c = lineValid ? C_OK : C_HI;
        for (Material m : previewInst.materials) ((ColorAttribute) m.get(ColorAttribute.Diffuse)).color.set(c);
    }

    private final Array<ModelInstance> activeParticleInstances = new Array<>();

    private void animate(float dt) {
        for (Plant p : state.plants) {
            PlantVis pv = plantVis.get(p.id);
            if (pv != null && pv.rotor != null) {
                float cf = p.online() ? (float) (p.outputMW / p.capacityMW()) : 0f;
                float avail = p.online() ? (float) state.capacityFactorNow(p) : 0f;
                pv.rotorAngle += dt * (15f + 240f * Math.max(cf, avail * 0.8f)) / Math.max(0.6f, p.equipment.visB / 0.6f);
            }
        }
        spawnAcc += dt;
        while (spawnAcc > 0.22f) {
            spawnAcc -= 0.22f;
            for (Plant p : state.plants) {
                if (!p.online() || p.outputMW <= 0.5) continue;
                PlantType t = p.type;
                if (t != PlantType.COAL && t != PlantType.GAS && t != PlantType.NUCLEAR) continue;
                if (particles.size() > 300) break;
                double load = p.outputMW / p.capacityMW();
                if (rnd.nextDouble() > 0.35 + 0.65 * load) continue;
                float top = state.map.topAt(p.x, p.y);
                float bx = p.x + 0.5f, bz = p.y + 0.5f;
                int v = (int) p.equipment.visA;
                boolean ccs = p.upgrades.contains(Upgrade.CCS) || p.upgrades.contains(Upgrade.GAS_CCS);
                PlantVis epv = plantVis.get(p.id);
                if (epv != null && !epv.emit.isEmpty()) {
                    for (int i = 0; i < epv.emit.size(); i++) {
                        tmpV.set(epv.emit.get(i)).rotate(Vector3.Y, epv.yaw);
                        spawn(bx + tmpV.x, top + tmpV.y, bz + tmpV.z, ccs || epv.emitSteam.get(i));
                    }
                    continue;
                }
                switch (t) {
                    case COAL -> {
                        if (v == 0) { spawn(bx + 0.28f, top + 0.79f, bz - 0.22f, ccs); spawn(bx + 0.28f, top + 0.79f, bz + 0.04f, ccs); }
                        else if (v == 1) { spawn(bx + 0.34f, top + 0.77f, bz - 0.32f, ccs); spawn(bx + 0.34f, top + 0.77f, bz - 0.10f, ccs); }
                        else { spawn(bx + 0.18f, top + 0.97f, bz + 0.28f, ccs); spawn(bx + 0.22f, top + 0.82f, bz - 0.20f, true); }
                    }
                    case GAS -> {
                        if (v == 0) spawn(bx + 0.24f, top + 0.41f, bz + 0.05f, ccs);
                        else if (v == 1) spawn(bx + 0.22f, top + 0.79f, bz + 0.05f, ccs);
                        else { spawn(bx - 0.24f, top + 0.87f, bz - 0.06f, ccs); spawn(bx + 0.10f, top + 0.87f, bz - 0.06f, ccs); }
                    }
                    default -> {
                        if (v == 0) { spawn(bx + 0.25f, top + 0.93f, bz - 0.22f, true); spawn(bx + 0.25f, top + 0.93f, bz + 0.24f, true); }
                        else spawn(bx + 0.36f, top + 0.22f, bz - 0.10f, true);
                    }
                }
            }
        }
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle q = it.next();
            q.life += dt;
            if (q.life >= q.max) { it.remove(); continue; }
            q.p.mulAdd(q.v, dt);
            q.size += dt * 0.12f;
        }
    }

    private void spawn(float x, float y, float z, boolean steam) {
        Particle q = new Particle();
        q.p.set(x, y, z);
        q.v.set((rnd.nextFloat() - 0.4f) * 0.08f + 0.05f, 0.30f + rnd.nextFloat() * 0.1f, (rnd.nextFloat() - 0.5f) * 0.06f);
        q.max = 3.2f + rnd.nextFloat(); q.size = 0.07f; q.steam = steam;
        particles.add(q);
    }

    private void updateParticleInstances() {
        activeParticleInstances.clear();
        float light = 0.35f + 0.65f * daylight();
        for (int i = 0; i < particles.size(); i++) {
            while (particlePool.size() <= i) particlePool.add(tinted(models.sphere, 0.5f, 0.5f, 0.5f, 0.5f));
            ModelInstance mi = particlePool.get(i);
            Particle q = particles.get(i);
            float k = q.life / q.max;
            float shade = (q.steam ? 0.95f : 0.45f - 0.2f * k) * light;
            setTint(mi, new Color(shade, shade, shade, 1), 0.55f * (1 - k));
            mi.transform.setToTranslation(q.p).scale(q.size * 2, q.size * 2, q.size * 2);
            activeParticleInstances.add(mi);
        }
    }

    @Override public void dispose() {
        batch.dispose(); shadowBatch.dispose(); sun.dispose(); terrain.dispose(); models.dispose();
    }
}
