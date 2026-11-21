package com.energytycoon.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;

/** 12 aylik odeme takvimi: aylik borc servisi cubuklari, tahmini faaliyet nakdi cizgisi; acik olan aylar kirmizi. */
public final class ScheduleChart extends Widget implements Disposable {
    private final ShapeRenderer sr = new ShapeRenderer();
    private final BitmapFont font;
    private final GlyphLayout gl = new GlyphLayout();
    private final float prefW, prefH;
    public double[] service = new double[12], opCash = new double[12];
    public boolean[] gap = new boolean[12];
    public String[] labels = new String[12];

    public ScheduleChart(BitmapFont font, float w, float h) { this.font = font; prefW = w; prefH = h; }

    @Override public float getPrefWidth() { return prefW; }
    @Override public float getPrefHeight() { return prefH; }

    @Override public void draw(Batch batch, float parentAlpha) {
        validate();
        float x = getX(), y = getY(), w = getWidth(), h = getHeight();
        float padL = 64, padB = 22, padT = 26, padR = 10;
        float px = x + padL, py = y + padB, pw = w - padL - padR, ph = h - padB - padT;
        double max = 1;
        for (int i = 0; i < 12; i++) max = Math.max(max, Math.max(service[i], opCash[i]));
        max *= 1.12;
        batch.end();
        sr.setProjectionMatrix(batch.getProjectionMatrix());
        sr.setTransformMatrix(batch.getTransformMatrix());
        com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.03f, 0.06f, 0.10f, 0.85f);
        sr.rect(x, y, w, h);
        float bw = pw / 12f;
        for (int i = 0; i < 12; i++) {
            float bh = (float) (service[i] / max * ph);
            if (gap[i]) sr.setColor(Skins.BAD); else sr.setColor(0.35f, 0.6f, 0.9f, 1);
            sr.rect(px + i * bw + bw * 0.18f, py, bw * 0.64f, bh);
        }
        sr.end();
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(0.20f, 0.28f, 0.34f, 1);
        for (int g = 0; g <= 4; g++) { float gy = py + ph * g / 4f; sr.line(px, gy, px + pw, gy); }
        sr.setColor(Skins.ACCENT);
        for (int i = 1; i < 12; i++) {
            float x0 = px + (i - 0.5f) * bw, x1 = px + (i + 0.5f) * bw;
            float y0 = py + (float) (opCash[i - 1] / max * ph), y1 = py + (float) (opCash[i] / max * ph);
            sr.line(x0, y0, x1, y1); sr.line(x0, y0 + 1, x1, y1 + 1);
        }
        sr.end();
        batch.begin();
        font.setColor(Skins.GOLD);
        font.draw(batch, "Önümüzdeki 12 ay: taksitler (çubuk) ve tahmini faaliyet nakdi (çizgi)", x + 8, y + h - 7);
        font.setColor(Skins.DIM);
        for (int g = 0; g <= 4; g++) font.draw(batch, Fmt.money(max * g / 4.0), x + 4, py + ph * g / 4f + 5, padL - 10, Align.right, false);
        for (int i = 0; i < 12; i++) {
            if (labels[i] == null) continue;
            font.setColor(gap[i] ? Skins.BAD : Skins.DIM);
            font.draw(batch, labels[i], px + i * bw, y + 15, bw, Align.center, false);
        }
        font.setColor(Color.WHITE);
    }

    @Override public void dispose() { sr.dispose(); }
}
