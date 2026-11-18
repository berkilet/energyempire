package com.energytycoon.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Disposable;

import java.util.HashMap;
import java.util.Map;

/**
 * Kenney City Kit (Commercial + Industrial, CC0) modelleri. JAR icindeki OBJ dosyalarindan bir kez yuklenir,
 * tum ekranlar arasinda paylasilir. Her kitin renk paleti dokusu ve geceleri pencereleri yakan bir isik dokusu vardir.
 */
public final class KenneyModels implements Disposable {
    public static final String COMMERCIAL = "commercial", INDUSTRIAL = "industrial";
    /** Gece aydinlatmasinin bagli oldugu malzeme kimligi (tum Kenney malzemeleri bu id'yi alir). */
    public static final String LIT_MATERIAL = "kenney";

    private static KenneyModels instance;
    /** 0: ana tema (beyaz/gri), 1-2: Kenney renk varyasyonlari (ayni UV duzeni, farkli renkler). */
    private static final String[] VARIANTS = { "colormap", "variation-a", "variation-b" };

    private final Map<String, Model> models = new HashMap<>();
    private final Map<String, BoundingBox> bounds = new HashMap<>();
    private final Map<String, Texture> palettes = new HashMap<>();
    private final Map<String, Texture> nightMaps = new HashMap<>();

    public static synchronized KenneyModels get() {
        if (instance == null) instance = new KenneyModels();
        return instance;
    }

    public static synchronized void disposeShared() {
        if (instance != null) { instance.dispose(); instance = null; }
    }

    private KenneyModels() {
        for (String kit : new String[] { COMMERCIAL, INDUSTRIAL }) {
            for (String variation : VARIANTS) {
                Pixmap pm = new Pixmap(Gdx.files.classpath(base(kit) + "Textures/" + variation + ".png"));
                Texture t = new Texture(pm);
                t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                palettes.put(kit + "/" + variation, t);
                Texture night = new Texture(nightMap(pm));
                night.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                nightMaps.put(kit + "/" + variation, night);
                pm.dispose();
            }
        }
    }

    private static String base(String kit) { return "models/kenney/" + kit + "/"; }

    /** Pencere camlari paletteki mavi tonlardir; geceleri sicak sari yanar, digerleri karanlik kalir. */
    private static Pixmap nightMap(Pixmap src) {
        Pixmap out = new Pixmap(src.getWidth(), src.getHeight(), Pixmap.Format.RGBA8888);
        Color c = new Color();
        for (int y = 0; y < src.getHeight(); y++) for (int x = 0; x < src.getWidth(); x++) {
            Color.rgba8888ToColor(c, src.getPixel(x, y));
            boolean glass = c.b > 0.55f && c.b > c.r + 0.12f && c.b >= c.g - 0.02f;
            if (glass) out.drawPixel(x, y, Color.rgba8888(1f, 0.80f, 0.48f, 1f));
            else out.drawPixel(x, y, Color.rgba8888(0, 0, 0, 1));
        }
        return out;
    }

    /** Ad: "industrial/chimney-large" gibi. */
    public synchronized Model model(String path) {
        Model m = models.get(path);
        if (m != null) return m;
        String kit = path.substring(0, path.indexOf('/'));
        FileHandle f = Gdx.files.classpath("models/kenney/" + path + ".obj");
        m = new ObjLoader().loadModel(f, new ObjLoader.ObjLoaderParameters(true));
        Texture pal = palettes.get(kit + "/colormap"), night = nightMaps.get(kit + "/colormap");
        for (Material mat : m.materials) {
            // Yukleyicinin olusturdugu dokular yerine paylasilan, filtreli paletleri kullan.
            mat.id = LIT_MATERIAL;
            mat.set(ColorAttribute.createDiffuse(Color.WHITE));
            mat.set(TextureAttribute.createDiffuse(pal));
            mat.set(TextureAttribute.createEmissive(night));
            mat.set(ColorAttribute.createEmissive(0, 0, 0, 1));
            mat.remove(ColorAttribute.Specular);
        }
        BoundingBox bb = new BoundingBox();
        m.calculateBoundingBox(bb);
        models.put(path, m);
        bounds.put(path, bb);
        return m;
    }

    public BoundingBox bounds(String path) { model(path); return bounds.get(path); }

    public Texture palette(String kit, int variant) { return palettes.get(kit + "/" + VARIANTS[variant]); }
    public Texture nightMap(String kit, int variant) { return nightMaps.get(kit + "/" + VARIANTS[variant]); }

    @Override public void dispose() {
        for (Model m : models.values()) m.dispose();
        for (Texture t : palettes.values()) t.dispose();
        for (Texture t : nightMaps.values()) t.dispose();
        models.clear(); palettes.clear(); nightMaps.clear();
    }
}
