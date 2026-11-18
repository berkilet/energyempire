package com.energytycoon.ui;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.energytycoon.sim.City;
import com.energytycoon.sim.GameState;
import com.energytycoon.sim.Plant;
import com.energytycoon.world.WorldMap;

/** Yeni oyun ekrani icin kucuk harita gorseli. */
public final class MapPreview {
    private MapPreview() {}

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
        for (Plant p : s.plants) {
            int rgb = p.owner.colorRgb;
            pm.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1);
            pm.fillRectangle(p.x * sc, p.y * sc, sc, sc);
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
