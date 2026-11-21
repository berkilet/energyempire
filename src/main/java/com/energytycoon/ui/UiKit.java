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

    /** Ince dolum cubugu (ör. kredi kullanimi, memnuniyet). */
    public static final class Meter extends Widget {
        private final Drawable bg, fill;
        private float value;
        private final Color color = new Color();
        private final float prefW, prefH;

        public Meter(Skin skin, float w, float h) {
            bg = skin.newDrawable("white", 0.10f, 0.15f, 0.20f, 1f);
            fill = skin.newDrawable("white", Color.WHITE);
            prefW = w; prefH = h;
        }

        public void set(float v, Color c) { value = Math.max(0, Math.min(1, v)); color.set(c); }

        @Override public float getPrefWidth() { return prefW; }
        @Override public float getPrefHeight() { return prefH; }

        @Override public void draw(Batch batch, float parentAlpha) {
            validate();
            Color old = batch.getColor().cpy();
            batch.setColor(1, 1, 1, parentAlpha);
            bg.draw(batch, getX(), getY(), getWidth(), getHeight());
            batch.setColor(color.r, color.g, color.b, parentAlpha);
            if (value > 0) fill.draw(batch, getX(), getY(), Math.max(2, getWidth() * value), getHeight());
            batch.setColor(old);
        }
    }

    /** Sabit genislikli tablo hucresi ekler (orantili fontta sutunlar kaymasin). */
    public static Cell<Label> col(Table t, Label l, float width, int align) {
        l.setAlignment(align);
        return t.add(l).width(width);
    }
}
