package com.energytycoon.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Coklu serili cizgi grafigi. Baslik ve aciklama ustte, eksen etiketleri birime gore bicimli,
 * yeterli veri yokken bos durum mesaji gosterir.
 */
public final class LineChart extends Widget implements Disposable {
    public enum Unit { MONEY, MW, PRICE, PLAIN }

    private static final class Series {
        final Supplier<double[]> data; final Color color; final String label;
        Series(String label, Color color, Supplier<double[]> data) { this.label = label; this.color = color; this.data = data; }
    }

    private final ShapeRenderer sr = new ShapeRenderer();
    private final BitmapFont font;
    private final List<Series> series = new ArrayList<>();
    private final String title;
    private final Unit unit;
    private final boolean zeroBased;
    private final float prefW, prefH;
    private String xLeft = "", xRight = "", emptyText = "Veri birikiyor…";
    private final GlyphLayout gl = new GlyphLayout();

    public LineChart(String title, Unit unit, BitmapFont font, float w, float h, boolean zeroBased) {
        this.title = title; this.unit = unit; this.font = font; this.zeroBased = zeroBased;
        this.prefW = w; this.prefH = h;
        setSize(w, h);
    }

    public LineChart add(String label, Color c, Supplier<double[]> data) { series.add(new Series(label, c, data)); return this; }
    public LineChart xLabels(String left, String right) { xLeft = left; xRight = right; return this; }
    public LineChart empty(String text) { emptyText = text; return this; }

    @Override public float getPrefWidth() { return prefW; }
    @Override public float getPrefHeight() { return prefH; }

    static String fmt(Unit u, double v) {
        return switch (u) {
            case MONEY -> Fmt.money(v);
            case MW -> Fmt.mw(v);
            case PRICE -> "$" + (Math.abs(v) >= 100 ? String.valueOf(Math.round(v)) : Fmt.f1(v));
            default -> Fmt.f1(v);
        };
    }

    @Override public void draw(Batch batch, float parentAlpha) {
        validate();
        float x = getX(), y = getY(), w = getWidth(), h = getHeight();
        float padL = 64, padB = xLeft.isEmpty() ? 10 : 22, padT = 26, padR = 10;
        float px = x + padL, py = y + padB, pw = w - padL - padR, ph = h - padB - padT;

        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        int maxLen = 0;
        List<double[]> datas = new ArrayList<>();
        for (Series s : series) {
            double[] d = s.data.get();
            datas.add(d);
            maxLen = Math.max(maxLen, d.length);
            for (double v : d) { if (!Double.isFinite(v)) continue; max = Math.max(max, v); min = Math.min(min, v); }
        }
        boolean hasData = maxLen >= 2 && Double.isFinite(min) && Double.isFinite(max);
        if (hasData) {
            if (zeroBased) min = Math.min(0, min);
            if (max - min < 1e-9) { max += Math.max(1, Math.abs(max) * 0.1); min -= Math.max(1, Math.abs(min) * 0.1); if (zeroBased) min = Math.min(0, min); }
            double range = max - min;
            max += range * 0.08;
            if (!zeroBased) min -= range * 0.06;
        }
        double range = max - min;

        batch.end();
        sr.setProjectionMatrix(batch.getProjectionMatrix());
        sr.setTransformMatrix(batch.getTransformMatrix());
        com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.03f, 0.06f, 0.10f, 0.85f);
        sr.rect(x, y, w, h);
        sr.end();
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(0.20f, 0.28f, 0.34f, 1);
        for (int g = 0; g <= 4; g++) { float gy = py + ph * g / 4f; sr.line(px, gy, px + pw, gy); }
        if (hasData) {
            for (int si = 0; si < series.size(); si++) {
                double[] d = datas.get(si);
                if (d.length < 2) continue;
                sr.setColor(series.get(si).color);
                for (int i = 1; i < d.length; i++) {
                    float x0 = px + pw * (i - 1) / (float) (d.length - 1), x1 = px + pw * i / (float) (d.length - 1);
                    float y0 = py + (float) ((d[i - 1] - min) / range * ph), y1 = py + (float) ((d[i] - min) / range * ph);
                    sr.line(x0, y0, x1, y1);
                    sr.line(x0, y0 + 1, x1, y1 + 1);
                }
            }
        }
        sr.end();
        batch.begin();

        // baslik (sol ust) ve aciklama (sag ust)
        font.setColor(Skins.GOLD);
        font.draw(batch, title, x + 8, y + h - 7);
        float lx = x + w - padR;
        for (int i = series.size() - 1; i >= 0; i--) {
            Series s = series.get(i);
            gl.setText(font, s.label);
            lx -= gl.width;
            font.setColor(s.color);
            font.draw(batch, s.label, lx, y + h - 7);
            font.draw(batch, "—", lx - 14, y + h - 7);
            lx -= 26;
        }
        font.setColor(Skins.DIM);
        if (!hasData) {
            gl.setText(font, emptyText, Skins.DIM, pw, Align.center, true);
            font.draw(batch, gl, px, py + ph / 2 + gl.height / 2);
        } else {
            for (int g = 0; g <= 4; g++) {
                double v = min + range * g / 4.0;
                font.draw(batch, fmt(unit, v), x + 4, py + ph * g / 4f + 5, padL - 10, Align.right, false);
            }
        }
        if (!xLeft.isEmpty()) {
            font.draw(batch, xLeft, px, y + 15);
            font.draw(batch, xRight, px, y + 15, pw, Align.right, false);
        }
        font.setColor(Color.WHITE);
    }

    @Override public void dispose() { sr.dispose(); }
}
