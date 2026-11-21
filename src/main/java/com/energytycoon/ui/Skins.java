package com.energytycoon.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * Oyunun arayuz temasi. Gorseller "Energy Tycoon menu assets" paketinden (src/main/resources/ui):
 * dokuz parcali paneller ve butonlar, ikonlar, HUD cipleri, altigen hiz dugmeleri, anahtarlar.
 * Font DejaVu Sans (Turkce karakter destekli).
 */
public final class Skins implements Disposable {
    public static final Color PANEL = new Color(0.06f, 0.10f, 0.15f, 0.94f);
    public static final Color PANEL2 = new Color(0.08f, 0.14f, 0.20f, 0.92f);
    public static final Color ACCENT = new Color(0.22f, 0.80f, 0.50f, 1);
    public static final Color WARN = new Color(0.98f, 0.76f, 0.30f, 1);
    public static final Color BAD = new Color(0.98f, 0.40f, 0.36f, 1);
    public static final Color DIM = new Color(0.64f, 0.72f, 0.80f, 1);
    /** Sampanya-altin vurgu (basliklar). */
    public static final Color GOLD = new Color(0.95f, 0.84f, 0.62f, 1);

    // ---- panel geometrisi (yari cozunurluk panel_*.png, 360x270) * PANEL_K
    public static final float PANEL_K = 0.64f;
    /** Baslik yuvasi: kenardan uzakliklar (piksel, olceklenmis). */
    public static final float SLOT_TOP = 25 * PANEL_K, SLOT_H = 32 * PANEL_K, SLOT_LEFT = 38 * PANEL_K;
    /** Govde ic kenar bosluklari. */
    public static final float BODY_TOP = 80 * PANEL_K, BODY_SIDE = 26 * PANEL_K, BODY_BOTTOM = 30 * PANEL_K;
    /** Penceredeki X kutusu: sag ust koseye gore. */
    public static final float X_RIGHT = 32 * PANEL_K, X_TOP = 21 * PANEL_K, X_W = 44 * PANEL_K, X_H = 38 * PANEL_K;

    public final Skin skin = new Skin();
    public BitmapFont small, normal, medium, mediumSmall, big, huge;
    private final Array<Texture> textures = new Array<>();
    /** Birlestirmede kullanilan ham fontlar (dokulari bunlara ait). */
    private final Array<BitmapFont> fontSources = new Array<>();

    /** Arayuz fontu Manrope V5'te bulunmayan semboller; DejaVu Sans'tan yedek glif olarak eklenir. */
    static final String FALLBACK_SYMBOLS = "▲▼█░▒✓✗★☆↔⚠≤≥∞";
    static final String TEXT_CHARS = FreeTypeFontGenerator.DEFAULT_CHARS + "çÇğĞıİöÖşŞüÜâÂîÎûÛ•–—−…·×↑↓→←₂≈";

    public Skins() {
        // Manrope V5 (Mikhail Sharanda): metin Regular, buton/deger Medium, baslik SemiBold, ana baslik Bold.
        FreeTypeFontGenerator fallback = new FreeTypeFontGenerator(Gdx.files.classpath("fonts/DejaVuSans.ttf"));
        small = manrope("Regular", 13, 0, fallback);
        normal = manrope("Regular", 15, 0, fallback);
        mediumSmall = manrope("Medium", 13, 0, fallback);
        medium = manrope("Medium", 14, 0, fallback);
        big = manrope("SemiBold", 17, 0, fallback);
        huge = manrope("Bold", 48, 2, fallback);
        fallback.dispose();

        Pixmap pm = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE); pm.fill();
        Texture white = new Texture(pm); pm.dispose();
        skin.add("white", white);
        skin.add("small", small, BitmapFont.class);
        skin.add("default", normal, BitmapFont.class);
        skin.add("medium", medium, BitmapFont.class);
        skin.add("big", big, BitmapFont.class);
        skin.add("huge", huge, BitmapFont.class);

        skin.add("default", new Label.LabelStyle(normal, Color.WHITE));
        skin.add("small", new Label.LabelStyle(small, Color.WHITE));
        skin.add("dim", new Label.LabelStyle(small, DIM));
        skin.add("value", new Label.LabelStyle(medium, Color.WHITE));
        skin.add("valueBad", new Label.LabelStyle(medium, BAD));
        skin.add("valueGood", new Label.LabelStyle(medium, ACCENT));
        skin.add("valueCost", new Label.LabelStyle(medium, new Color(0.95f, 0.66f, 0.60f, 1)));
        skin.add("dimValue", new Label.LabelStyle(medium, DIM));
        skin.add("kpi", new Label.LabelStyle(big, Color.WHITE));
        skin.add("kpiGood", new Label.LabelStyle(big, ACCENT));
        skin.add("kpiBad", new Label.LabelStyle(big, BAD));
        skin.add("card", skin.newDrawable("white", 0.03f, 0.07f, 0.11f, 0.78f), com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("title", new Label.LabelStyle(big, GOLD));
        skin.add("huge", new Label.LabelStyle(huge, Color.WHITE));
        skin.add("good", new Label.LabelStyle(normal, ACCENT));
        skin.add("bad", new Label.LabelStyle(normal, BAD));
        skin.add("warn", new Label.LabelStyle(normal, WARN));

        // ---- dokuz parcali gorseller
        Drawable steel = nine("btn_secondary", 50, 50, 45, 42, 0.36f);
        Drawable green = nine("btn_primary", 50, 50, 45, 45, 0.36f);
        Drawable pillS = nine("pill_silver", 50, 50, 36, 36, 0.32f);
        Drawable pillG = nine("pill_gold", 50, 50, 36, 36, 0.32f);
        Drawable steelBig = nine("btn_secondary", 50, 50, 45, 42, 0.52f);
        Drawable greenBig = nine("btn_primary", 50, 50, 45, 45, 0.52f);

        TextButton.TextButtonStyle tb = new TextButton.TextButtonStyle();
        tb.font = medium; tb.fontColor = Color.WHITE; tb.disabledFontColor = new Color(1, 1, 1, 0.35f);
        tb.up = tint(steel, 0.62f); tb.over = tint(steel, 0.80f); tb.down = tint(steel, 0.48f);
        tb.checked = green; tb.checkedOver = tint(green, 1.15f); tb.checkedFontColor = GOLD;
        tb.disabled = tint(steel, 0.38f);
        skin.add("default", tb);

        TextButton.TextButtonStyle ts = new TextButton.TextButtonStyle(tb);
        ts.font = mediumSmall;
        ts.up = pillS; ts.over = tint(pillS, 1.25f); ts.down = tint(pillS, 0.75f);
        // Secili kucuk buton: parlak yesil-altin buton (secili olmayan koyu haplardan net ayrilsin)
        Drawable greenSmall = nine("btn_primary", 50, 50, 45, 45, 0.24f);
        ts.checked = greenSmall; ts.checkedOver = tint(greenSmall, 1.15f); ts.disabled = tint(pillS, 0.5f);
        skin.add("small", ts);

        TextButton.TextButtonStyle accent = new TextButton.TextButtonStyle(tb);
        accent.up = green; accent.over = tint(green, 1.18f); accent.down = tint(green, 0.80f); accent.fontColor = GOLD;
        skin.add("accent", accent);

        TextButton.TextButtonStyle menuBig = new TextButton.TextButtonStyle(tb);
        menuBig.font = big; menuBig.up = tint(steelBig, 0.62f); menuBig.over = tint(steelBig, 0.82f); menuBig.down = tint(steelBig, 0.5f);
        menuBig.disabled = tint(steelBig, 0.36f); menuBig.checked = null;
        skin.add("menu", menuBig);
        TextButton.TextButtonStyle menuAccent = new TextButton.TextButtonStyle(menuBig);
        menuAccent.up = greenBig; menuAccent.over = tint(greenBig, 1.18f); menuAccent.down = tint(greenBig, 0.8f); menuAccent.fontColor = GOLD;
        skin.add("menuAccent", menuAccent);

        // satir (liste) secimi: kucuk butonlarla ayni hap gorseli
        skin.add("row", tint(pillS, 0.55f), Drawable.class);
        skin.add("rowHover", tint(pillS, 0.85f), Drawable.class);
        skin.add("rowSel", pillG, Drawable.class);
        skin.add("rowLocked", tint(pillS, 0.30f), Drawable.class);

        // ---- zaman kontrolleri (yuvarlak dugmeler; secili olan yesil halka parlamasiyla belirgin)
        Drawable glow = glowRing();
        skin.add("timePause", timeStyle("time_pause", glow, false));
        skin.add("timeResume", timeStyle("time_resume", glow, true));
        skin.add("time1", timeStyle("time_speed_1x", glow, false));
        skin.add("time2", timeStyle("time_speed_2x", glow, false));
        skin.add("time3", timeStyle("time_speed_4x", glow, false));

        // ---- pencereler ve paneller
        Drawable panelWindow = nine("panel_window", 150, 195, 125, 126, PANEL_K);
        Drawable panelPlain = nine("panel_plain", 150, 195, 125, 126, PANEL_K);
        skin.add("panelFrame", panelPlain, Drawable.class);
        skin.add("windowFrame", panelWindow, Drawable.class);
        Window.WindowStyle ws = new Window.WindowStyle();
        ws.titleFont = big; ws.titleFontColor = GOLD;
        ws.background = panelWindow;
        skin.add("default", ws);
        skin.add("panel", skin.newDrawable("white", PANEL), Drawable.class);
        skin.add("panel2", skin.newDrawable("white", PANEL2), Drawable.class);
        skin.add("bar", skin.newDrawable("white", 0.03f, 0.06f, 0.10f, 0.92f), Drawable.class);

        // Pencerenin cizili X kutusunun ustune oturan gorunmez kapatma dugmesi
        Button.ButtonStyle close = new Button.ButtonStyle();
        close.over = skin.newDrawable("white", 1f, 1f, 1f, 0.18f);
        close.down = skin.newDrawable("white", 0f, 0f, 0f, 0.25f);
        skin.add("close", close);

        // ---- HUD cipleri (ikon yuvali gostergeler)
        skin.add("chip", nine("chip_gold", 88, 20, 22, 22, 0.38f), Drawable.class);
        skin.add("chipGreen", nine("chip_green", 88, 20, 22, 22, 0.38f), Drawable.class);

        // ---- anahtar (toggle)
        CheckBox.CheckBoxStyle toggle = new CheckBox.CheckBoxStyle();
        toggle.font = normal; toggle.fontColor = Color.WHITE;
        toggle.checkboxOff = image("toggle_off", 50, 26);
        toggle.checkboxOn = image("toggle_on", 53, 26);
        skin.add("toggle", toggle);

        // ---- ikonlar
        for (String n : new String[] { "solar", "wind", "hydro", "pylon", "battery", "bolt", "factory", "leaf", "coin", "city", "upgrade", "gear", "build", "cursor", "demolish", "layers", "assets" })
            skin.add("icon_" + n, new TextureRegionDrawable(new TextureRegion(tex("icon_" + n))), Drawable.class);
        skin.add("closeRound", new TextureRegionDrawable(new TextureRegion(tex("close_round"))), Drawable.class);
        skin.add("gearButton", new TextureRegionDrawable(new TextureRegion(tex("btn_icon_gear"))), Drawable.class);

        TextField.TextFieldStyle tf = new TextField.TextFieldStyle();
        tf.font = normal; tf.fontColor = Color.WHITE; tf.messageFont = normal; tf.messageFontColor = DIM;
        tf.background = tint(pillS, 0.45f);
        tf.cursor = skin.newDrawable("white", GOLD);
        tf.selection = skin.newDrawable("white", 0.2f, 0.6f, 0.45f, 0.6f);
        skin.add("default", tf);

        TextTooltip.TextTooltipStyle tts = new TextTooltip.TextTooltipStyle(new Label.LabelStyle(small, Color.WHITE), tint(pillS, 0.5f));
        tts.wrapWidth = 260;
        skin.add("default", tts);
        TooltipManager.getInstance().initialTime = 0.35f;

        ScrollPane.ScrollPaneStyle sp = new ScrollPane.ScrollPaneStyle();
        sp.vScroll = skin.newDrawable("white", 0.1f, 0.12f, 0.16f, 0.6f);
        sp.vScrollKnob = skin.newDrawable("white", GOLD);
        skin.add("default", sp);
    }

    // ------------------------------------------------------------ yardimcilar
    private Texture tex(String name) {
        Texture t = new Texture(Gdx.files.classpath("ui/" + name + ".png"), true);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        textures.add(t);
        return t;
    }

    /** Dokuz parcali drawable; kenar degerleri yari-cozunurluk PNG pikselinde, scale ile kucultulur. */
    private Drawable nine(String name, int l, int r, int t, int b, float scale) {
        NinePatch np = new NinePatch(tex(name), l, r, t, b);
        np.scale(scale, scale);
        NinePatchDrawable d = new NinePatchDrawable(np);
        d.setMinSize(np.getTotalWidth(), np.getTotalHeight());
        d.setLeftWidth(np.getLeftWidth() * 0.55f); d.setRightWidth(np.getRightWidth() * 0.55f);
        d.setTopHeight(np.getTopHeight() * 0.35f); d.setBottomHeight(np.getBottomHeight() * 0.35f);
        return d;
    }

    private Drawable image(String name, float w, float h) {
        TextureRegionDrawable d = new TextureRegionDrawable(new TextureRegion(tex(name)));
        d.setMinSize(w, h);
        return d;
    }

    private static Drawable tint(Drawable d, float k) {
        Color c = new Color(Math.min(1, k), Math.min(1, k), Math.min(1, k), 1);
        if (d instanceof NinePatchDrawable n) {
            NinePatchDrawable t = n.tint(c);
            t.setLeftWidth(n.getLeftWidth()); t.setRightWidth(n.getRightWidth());
            t.setTopHeight(n.getTopHeight()); t.setBottomHeight(n.getBottomHeight());
            t.setMinSize(n.getMinWidth(), n.getMinHeight());
            return t;
        }
        if (d instanceof TextureRegionDrawable r) {
            Drawable t = r.tint(c);
            t.setMinWidth(r.getMinWidth()); t.setMinHeight(r.getMinHeight());
            return t;
        }
        return d;
    }

    private BitmapFont font(FreeTypeFontGenerator g, int size, String chars, int shadow) {
        FreeTypeFontParameter p = new FreeTypeFontParameter();
        p.size = size; p.characters = chars; p.minFilter = Texture.TextureFilter.Linear; p.magFilter = Texture.TextureFilter.Linear;
        p.borderWidth = 0; p.hinting = FreeTypeFontGenerator.Hinting.Slight;
        if (shadow > 0) { p.shadowOffsetX = shadow; p.shadowOffsetY = shadow; p.shadowColor = new Color(0, 0, 0, 0.6f); }
        return g.generateFont(p);
    }

    /**
     * Manrope agirligindan font uretir ve Manrope'ta olmayan sembolleri (FALLBACK_SYMBOLS) DejaVu'dan
     * ayni boyutta uretip glif olarak ekler. Font dosyalari degistirilmez; birlestirme yalnizca bellekte.
     */
    private BitmapFont manrope(String weight, int size, int shadow, FreeTypeFontGenerator fallbackGen) {
        FreeTypeFontGenerator g = new FreeTypeFontGenerator(Gdx.files.classpath("fonts/ManropeV5-" + weight + ".otf"));
        BitmapFont main = font(g, size, TEXT_CHARS, shadow);
        g.dispose();
        BitmapFont fb = font(fallbackGen, size, FALLBACK_SYMBOLS, shadow);
        fontSources.add(main); fontSources.add(fb);

        BitmapFont.BitmapFontData d = main.getData(), fd = fb.getData();
        Array<TextureRegion> regions = new Array<>(main.getRegions());
        int pageBase = regions.size;
        regions.addAll(fb.getRegions());
        // Taban cizgilerini hizala: iki fontun "ust kenardan taban cizgisine" uzakligi farki kadar kaydir.
        float shift = (fd.ascent + fd.capHeight) - (d.ascent + d.capHeight);
        for (char c : FALLBACK_SYMBOLS.toCharArray()) {
            BitmapFont.Glyph src = fd.getGlyph(c);
            if (src == null || d.getGlyph(c) != null) continue;
            BitmapFont.Glyph gl = new BitmapFont.Glyph();
            gl.id = src.id; gl.srcX = src.srcX; gl.srcY = src.srcY; gl.width = src.width; gl.height = src.height;
            gl.u = src.u; gl.v = src.v; gl.u2 = src.u2; gl.v2 = src.v2;
            gl.xoffset = src.xoffset; gl.yoffset = Math.round(src.yoffset + shift); gl.xadvance = src.xadvance;
            gl.page = pageBase + src.page;
            d.setGlyph(c, gl);
        }
        // Yeni BitmapFont: sayfa sayisi (doku) birlesik listeden kurulur; aksi halde kendi cizim onbellegi eksik kalir.
        BitmapFont merged = new BitmapFont(d, regions, main.usesIntegerPositions());
        return merged;
    }

    /** Secili zaman dugmesinin arkasindaki yesil halka parlamasi (kodla uretilir). */
    private Drawable glowRing() {
        int n = 96;
        Pixmap pm = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float c = (n - 1) / 2f;
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            float r = (float) Math.hypot(x - c, y - c) / c;           // 0 merkez, 1 kenar
            float ring = (float) Math.exp(-Math.pow((r - 0.86f) / 0.10f, 2)); // halka
            float halo = Math.max(0, 1 - r) * 0.25f;                 // ice dogru yumusak dolgu
            float a = Math.min(1, ring * 0.95f + halo);
            pm.drawPixel(x, y, Color.rgba8888(0.25f, 0.95f, 0.55f, a));
        }
        Texture t = new Texture(pm, true);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        pm.dispose();
        textures.add(t);
        TextureRegionDrawable d = new TextureRegionDrawable(new TextureRegion(t));
        d.setMinSize(34, 34);
        return d;
    }

    /** Zaman kontrol dugmesi: secili degilken hafif soluk, uzerine gelince parlak, seciliyken parlak + halka. */
    private ImageButton.ImageButtonStyle timeStyle(String icon, Drawable glow, boolean alwaysLit) {
        Drawable img = image(icon, 30, 30);
        ImageButton.ImageButtonStyle st = new ImageButton.ImageButtonStyle();
        st.imageUp = alwaysLit ? img : tint(img, 0.72f);
        st.imageOver = img;
        st.imageDown = tint(img, 0.6f);
        st.imageChecked = img;
        st.checked = glow;
        if (alwaysLit) st.up = glow;
        return st;
    }

    // ------------------------------------------------------------ bilesen fabrikalari
    /**
     * Cerceveli panel: baslik, cercevenin baslik yuvasina oturur; govde altinda.
     * Donen tablo dis cercevedir; icerik body tablosuna eklenir.
     */
    public Table framedPanel(Label title, Table body) {
        Table t = new Table(skin);
        t.setBackground("panelFrame");
        t.pad(SLOT_TOP, BODY_SIDE, BODY_BOTTOM, BODY_SIDE);
        t.add(title).height(SLOT_H).left().padLeft(SLOT_LEFT - BODY_SIDE + 4).growX().row();
        t.add(body).grow().padTop(BODY_TOP - SLOT_TOP - SLOT_H);
        body.top().left();
        title.setAlignment(Align.left);
        return t;
    }

    /** Ikon yuvali gosterge cipi. */
    public Table chip(String icon, Label value, boolean green) {
        Table t = new Table(skin);
        t.setBackground(green ? "chipGreen" : "chip");
        Image im = new Image(skin.getDrawable("icon_" + icon));
        t.add(im).size(22, 23).padLeft(6.5f);
        value.setStyle(skin.get("value", Label.LabelStyle.class));
        t.add(value).padLeft(8).padRight(12).left();
        return t;
    }

    /** Pencereye cizili X'in ustune oturan kapatma dugmesi ekler ve baslik yerlesimini ayarlar. */
    public void decorateWindow(Window win, Runnable onClose) {
        win.pad(BODY_TOP, BODY_SIDE + 4, BODY_BOTTOM + 4, BODY_SIDE + 4);
        win.getTitleLabel().setAlignment(Align.left);
        Table tt = win.getTitleTable();
        tt.clearChildren();
        tt.top();
        // Baslik tablosu pencerenin ust pad bolgesini kaplar; ust kenara yaslanip yuvaya hizalanir.
        tt.add(win.getTitleLabel()).growX().left().height(SLOT_H).padLeft(SLOT_LEFT - BODY_SIDE).padTop(SLOT_TOP);
        Button x = new Button(skin, "close");
        x.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            public void changed(ChangeEvent e, com.badlogic.gdx.scenes.scene2d.Actor a) { onClose.run(); }
        });
        tt.add(x).size(X_W, X_H).padRight(X_RIGHT - BODY_SIDE - 4).padTop(X_TOP);
    }

    @Override public void dispose() {
        skin.dispose();
        for (Texture t : textures) t.dispose();
        for (BitmapFont f : fontSources) f.dispose();
    }
}
