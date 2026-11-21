package com.energytycoon.ui;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.energytycoon.sim.City;
import com.energytycoon.sim.GridBalance;
import com.energytycoon.sim.GameState;
import com.energytycoon.sim.Plant;
import com.energytycoon.world.WorldMap;

/** Yeni oyun ekrani icin kucuk harita gorseli. */
public final class MapPreview {
    private MapPreview() {}

    /** Onizleme lejantindaki UŞİ hatti rengi. */
    public static final com.badlogic.gdx.graphics.Color OPERATOR_LINE = new com.badlogic.gdx.graphics.Color(0.75f, 0.92f, 1f, 1f);

    private static void setRgb(Pixmap pm, int rgb, float a) {
        pm.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, a);
    }

    public static Texture render(GameState s) {
        WorldMap m = s.map;
        int sc = Math.max(2, 192 / m.size);
        Pixmap pm = new Pixmap(m.size * sc, m.size * sc, Pixmap.Format.RGBA8888);
        for (int y = 0; y < m.size; y++) for (int x = 0; x < m.size; x++) {
            int i = m.idx(x, y);
            float r, g, b;
            float lv = m.level[i] / (float) Math.max(8, m.preset.relief);
            if (m.water[i]) {
                if (m.river[i]) { r = 0.30f; g = 0.60f; b = 0.90f; }
                else if (m.isSea(i)) { r = 0.12f; g = 0.34f; b = 0.58f; }
                else { r = 0.22f; g = 0.50f; b = 0.76f; }
            } else if (m.preset.desert) { r = 0.88f - lv * 0.15f; g = 0.72f - lv * 0.15f; b = 0.48f - lv * 0.12f; }
            else if (lv < 0.55f) { r = 0.32f; g = 0.60f - lv * 0.15f; b = 0.28f; }
            else if (lv < 0.85f) { r = 0.45f; g = 0.44f; b = 0.38f; }
            else { r = 0.88f; g = 0.89f; b = 0.92f; }
            // hafif golgelendirme
            float shade = 1f;
            if (x > 0 && y > 0 && m.top[m.idx(x - 1, y - 1)] > m.top[i] + 0.05f) shade = 0.82f;
            pm.setColor(r * shade, g * shade, b * shade, 1);
            pm.fillRectangle(x * sc, y * sc, sc, sc);
        }
        // hatlar: sirket hatlari sahibinin renginde, UŞİ hatlari acik camgobegi kesikli
        for (com.energytycoon.sim.PowerLine l : s.lines) {
            int ax = l.a.x * sc + sc / 2, ay = l.a.y * sc + sc / 2, bx = l.b.x * sc + sc / 2, by = l.b.y * sc + sc / 2;
            if (l.owner == null) {
                pm.setColor(OPERATOR_LINE);
                int n = Math.max(2, (int) (Math.hypot(bx - ax, by - ay) / 3));
                for (int k = 0; k < n; k += 2) {
                    pm.drawLine(ax + (bx - ax) * k / n, ay + (by - ay) * k / n, ax + (bx - ax) * (k + 1) / n, ay + (by - ay) * (k + 1) / n);
                }
            } else {
                setRgb(pm, l.owner.colorRgb, 0.9f);
                pm.drawLine(ax, ay, bx, by);
            }
        }
        // santraller: koyu kenarli isaret; kamu santrallerinde lacivert "K"
        int ms = Math.max(5, sc * 2 + 1);
        for (Plant p : s.plants) {
            int cx = p.x * sc + sc / 2 - ms / 2, cy = p.y * sc + sc / 2 - ms / 2;
            boolean kamu = p.owner.neutral;
            setRgb(pm, kamu ? GridBalance.NEUTRAL_OUTLINE : 0x101418, 1);
            pm.fillRectangle(cx - 1, cy - 1, ms + 2, ms + 2);
            setRgb(pm, p.owner.colorRgb, 1);
            pm.fillRectangle(cx, cy, ms, ms);
            if (kamu && ms >= 5) {
                setRgb(pm, GridBalance.NEUTRAL_OUTLINE, 1);
                int gx = cx + ms / 2 - 2, gy = cy + ms / 2 - 2;
                for (int k = 0; k < 5; k++) pm.drawPixel(gx, gy + k);           // K'nin dikey cizgisi
                for (int k = 0; k < 3; k++) { pm.drawPixel(gx + 1 + k, gy + 2 - k - (k > 0 ? 0 : 0)); pm.drawPixel(gx + 1 + k, gy + 2 + k); }
            }
        }
        for (City c : s.cities) {
            pm.setColor(0.1f, 0.1f, 0.12f, 1);
            pm.fillRectangle((c.x - 1) * sc - 1, (c.y - 1) * sc - 1, sc * 3 + 2, sc * 3 + 2);
            pm.setColor(1f, 1f, 1f, 1);
            pm.fillRectangle((c.x - 1) * sc, (c.y - 1) * sc, sc * 3, sc * 3);
        }
        Texture t = new Texture(pm);
        pm.dispose();
        return t;
    }
}
