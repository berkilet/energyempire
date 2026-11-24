package com.energytycoon.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;

/** Yonetim pencereleri icin ortak parcalar: KPI kartlari, bolum basliklari, tablo hucreleri, olcu cubugu. */
public final class UiKit {
    private UiKit() {}

    public static Label label(Skin skin, String text, String style) { return new Label(text, skin, style); }

    /** Bolum basligi: kucuk, soluk, buyuk harf. */
    public static Label section(Skin skin, String text) {
        Label l = new Label(text, skin, "dim");
        return l;
    }

    /** Soldaki kucuk ikon + etiket (logo/rozet satiri). */
    public static Table iconLabel(Skin skin, String icon, Label l, float w, float h) {
        Table t = new Table();
        t.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(skin.getDrawable("icon_" + icon))).size(w, h).padRight(6);
        t.add(l).left();
        return t;
    }

    /** Soldaki ikonlu dugme (sekme/menu). Stil, mevcut metin dugmesi stilinden turetilir; secili hali korunur. */
    public static com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton iconButton(Skin skin, String text, String icon, String style, float w, float h) {
        com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton.ImageTextButtonStyle st = new com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton.ImageTextButtonStyle(
            skin.get(style, com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle.class));
        st.imageUp = skin.getDrawable("icon_" + icon);
        com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton b = new com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton(text, st);
        b.getImageCell().size(w, h).padRight(4);
        return b;
    }

    /** KPI karti: ust satirda aciklama, altta buyuk deger; alt satir istege bagli (ör. degisim). */
    public static Table card(Skin skin, String caption, Label value, Label sub) { return card(skin, caption, value, sub, null); }

    /** Ikonlu KPI karti: baslik satirinin soluna kucuk ikon. */
    public static Table card(Skin skin, String caption, Label value, Label sub, String icon) {
        Table t = new Table(skin);
        t.setBackground("card");
        t.pad(6, 10, 6, 10);
        t.left().top();
        if (icon == null) t.add(new Label(caption, skin, "dim")).left().row();
        else {
            Table cap = new Table();
            cap.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(skin.getDrawable("icon_" + icon))).size(18, 19).padRight(4);
            cap.add(new Label(caption, skin, "dim")).left();
            t.add(cap).left().row();
        }
        t.add(value).left().padTop(1).row();
        // Alt satir yoksa bos satir: tum kartlar ayni yukseklikte kalsin.
        com.badlogic.gdx.scenes.scene2d.ui.Cell<Label> c = t.add(sub != null ? sub : new Label(" ", skin, "dim")).left().padTop(1);
        // kaydirmali alt satir kart genisligini doldurur (harf harf alt alta dizilmesin)
        if (sub != null && sub.getWrap()) c.growX();
        return t;
    }

    /** Tutar hucresi: sag hizali; isaretine gore renk. */
    public static Label money(Skin skin) {
        Label l = new Label("", skin, "value");
        l.setAlignment(Align.right);
        return l;
    }

    public static void setMoney(Skin skin, Label l, double v, boolean colorBySign) {
        l.setText(Fmt.money(v));
        String st = !colorBySign || Math.abs(v) < 1 ? "value" : v > 0 ? "valueGood" : "valueBad";
        Label.LabelStyle s = skin.get(st, Label.LabelStyle.class);
        if (l.getStyle() != s) l.setStyle(s);
    }

    /** Gider gosterimi: "−$X" kirmizimsi, sifirsa soluk. */
    public static void setCost(Skin skin, Label l, double cost) {
        if (cost < 1) { l.setText("—"); if (l.getStyle() != skin.get("dimValue", Label.LabelStyle.class)) l.setStyle(skin.get("dimValue", Label.LabelStyle.class)); return; }
        l.setText("−" + Fmt.money(cost));
        Label.LabelStyle s = skin.get("valueCost", Label.LabelStyle.class);
        if (l.getStyle() != s) l.setStyle(s);
    }

    /**
     * Isikli ilerleme cubugu (tools/ui/make_progress_bar.py ile uretilen parcalar): metal cerceve, yesil dolgu, %100'de
     * kivilcimli turkuaz dolgu. Dokuz-parca: her genislik ve yukseklikte uclar bozulmadan esner; ara degerler surekli.
     * tint: dolguyu renklendirir (ör. dusuk memnuniyette kirmiziya calar), beyaz = orijinal.
     */
    public static final class GlowBar extends Widget {
        /** Kaynak parcalarin yuksekligi ve olculeri (32 px): cerceve basligi, dolgu uclari, dolgunun cerceveye gore kaymasi. */
        private static final float SRC_H = 32, TRACK_CAP = 30, FILL_CAP = 15, FILL_OFFSET = 3.3f;
        private static com.badlogic.gdx.graphics.Texture trackTex, fillTex, fullTex;
        private final float prefW, prefH;
        private float value;
        private final Color tint = new Color(Color.WHITE);
        private com.badlogic.gdx.graphics.g2d.NinePatch track, fill, full;
        private float builtFor = -1;

        public GlowBar(float w, float h) { prefW = w; prefH = h; }

        private static com.badlogic.gdx.graphics.Texture tex(String name) {
            com.badlogic.gdx.graphics.Texture t = new com.badlogic.gdx.graphics.Texture(com.badlogic.gdx.Gdx.files.classpath("ui/" + name), true);
            t.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.MipMapLinearLinear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
            return t;
        }

        public GlowBar set(float v) { value = Math.max(0, Math.min(1, v)); return this; }
        public GlowBar tint(Color c) { tint.set(c); return this; }

        @Override public float getPrefWidth() { return prefW; }
        @Override public float getPrefHeight() { return prefH; }

        private void build(float h) {
            if (trackTex == null) { trackTex = tex("bar_track.png"); fillTex = tex("bar_fill.png"); fullTex = tex("bar_fill_full.png"); }
            float s = h / SRC_H;
            int tc = Math.round(TRACK_CAP), fc = Math.round(FILL_CAP);
            track = new com.badlogic.gdx.graphics.g2d.NinePatch(trackTex, tc, tc, 0, 0);
            fill = new com.badlogic.gdx.graphics.g2d.NinePatch(fillTex, fc, fc, 0, 0);
            full = new com.badlogic.gdx.graphics.g2d.NinePatch(fullTex, fc, fc + 10, 0, 0);
            track.scale(s, s); fill.scale(s, s); full.scale(s, s);
            builtFor = h;
        }

        @Override public void draw(Batch batch, float parentAlpha) {
            validate();
            float w = getWidth(), h = getHeight();
            if (h != builtFor) build(h);
            float s = h / SRC_H;
            Color old = batch.getColor().cpy();
            batch.setColor(1, 1, 1, parentAlpha);
            track.draw(batch, getX(), getY(), w, h);
            if (value > 0) {
                boolean done = value >= 0.999f;
                com.badlogic.gdx.graphics.g2d.NinePatch np = done ? full : fill;
                float off = FILL_OFFSET * s, maxW = w - off;
                float minW = np.getLeftWidth() + np.getRightWidth();
                float fw = Math.max(minW, maxW * value);
                batch.setColor(tint.r, tint.g, tint.b, parentAlpha);
                np.draw(batch, getX() + off, getY(), Math.min(fw, maxW), h);
            }
            batch.setColor(old);
        }
    }

    /** Sabit genislikli tablo hucresi ekler (orantili fontta sutunlar kaymasin). */
    public static Cell<Label> col(Table t, Label l, float width, int align) {
        l.setAlignment(align);
        return t.add(l).width(width);
    }
}
