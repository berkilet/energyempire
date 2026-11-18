package com.energytycoon;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import com.energytycoon.sim.*;
import com.energytycoon.world.MapPreset;

import java.util.Map;

/**
 * Gelistirici dogrulama kancasi; yalnizca ET_SHOT ortam degiskeni varsa calisir (normal oyunda etkisiz).
 * ET_SHOT=png yolu, ET_SCENARIO=menu|setup|game, ET_PRESET=MapPreset, ET_SIM=senaryo id, ET_HOUR=saat,
 * ET_OPEN=equip:TYPE|market|select, ET_ZOOM=px/birim, ET_FOCUS=plant|overview, ET_SHADOW=0..2, ET_FRAMES=n
 */
final class DevHook {
    private final EnergyGame game;
    private final Map<String, String> env = System.getenv();
    private final String shot;
    private int frame;
    private GameScreen gs;

    private DevHook(EnergyGame g, String shot) { this.game = g; this.shot = shot; }

    static DevHook fromEnv(EnergyGame g) {
        String s = System.getenv("ET_SHOT");
        return s == null ? null : new DevHook(g, s);
    }

    private String get(String k, String def) { return env.getOrDefault(k, def); }

    boolean skipMenu() { return get("ET_SCENARIO", "menu").equals("game"); }

    void frame() {
        frame++;
        if (frame == 2 && env.containsKey("ET_SHADOW")) game.setShadowQuality(Integer.parseInt(get("ET_SHADOW", "1")));
        if (frame == 3 && get("ET_SCENARIO", "menu").equals("setup") && game.getScreen() instanceof MenuScreen ms) ms.showSetup();
        if (frame == 3 && get("ET_SCENARIO", "menu").equals("settings") && game.getScreen() instanceof MenuScreen ms) ms.showSettings();
        if (frame == 3 && get("ET_SCENARIO", "menu").equals("game")) startGame();
        if (frame == 30 && gs != null) System.out.println(gs.devPickTest());
        if (frame == 31 && gs != null && env.containsKey("ET_VERTS")) for (int d = 0; d < 3; d++) System.out.println("VERTS detay=" + d + " kose=" + gs.devTerrainVerts(d));
        if (gs != null && env.containsKey("ET_PAUSETEST")) pauseTest();
        if (frame == 6 && gs != null) {
            String open = get("ET_OPEN", "");
            if (open.startsWith("equip:")) gs.devOpenEquipment(PlantType.valueOf(open.substring(6)));
            else if (open.equals("market") || open.equals("menu") || open.equals("finance") || open.equals("help") || open.equals("settings") || open.equals("companies")) gs.devToggle(open);
            else if (open.equals("select")) {
                for (Plant p : gameState().plants) if (p.owner.player && p.type == PlantType.SOLAR) { gs.devSelect(p.id); break; }
            }
        }
        if (frame == 35 && gs != null && env.containsKey("ET_SAVE")) com.energytycoon.save.SaveManager.save(state, "save");
        if (frame == Integer.parseInt(get("ET_FRAMES", "40"))) {
            Pixmap pm = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            int w = pm.getWidth(), h = pm.getHeight();
            Pixmap flipped = new Pixmap(w, h, pm.getFormat());
            for (int y = 0; y < h; y++) flipped.drawPixmap(pm, 0, y, w, 1, 0, h - 1 - y, w, 1);
            PixmapIO.writePNG(Gdx.files.absolute(shot), flipped);
            flipped.dispose(); pm.dispose();
            Gdx.app.exit();
        }
    }

    private long h0, t0, t1;
    /** Durdurma testi: fareyle II'ye tikla, sonra bosluk ile devam/durdur; saat ilerlemesini raporla. */
    private void pauseTest() {
        switch (frame) {
            case 8 -> gs.devClickSpeed(0);
            case 10 -> h0 = gs.devHour();
            case 30 -> { System.out.println("PAUSETEST tikla: hiz=" + gs.devSpeed() + " saat " + h0 + " -> " + gs.devHour()); gs.devKey(com.badlogic.gdx.Input.Keys.SPACE); }
            case 32 -> { h0 = gs.devHour(); t0 = System.currentTimeMillis(); }
            case 149 -> t1 = System.currentTimeMillis();
            case 150 -> { System.out.println("PAUSETEST bosluk(devam): hiz=" + gs.devSpeed() + " saat " + h0 + " -> " + gs.devHour() + "  (" + Math.round(t1 - t0) / 1000.0 + " sn)"); gs.devKey(com.badlogic.gdx.Input.Keys.SPACE); }
            case 152 -> h0 = gs.devHour();
            case 170 -> System.out.println("PAUSETEST bosluk(durdur): hiz=" + gs.devSpeed() + " saat " + h0 + " -> " + gs.devHour());
            default -> {}
        }
    }

    private GameState state;
    private GameState gameState() { return state; }

    private void startGame() {
        GameSettings g = new GameSettings();
        g.seed = Long.parseLong(get("ET_SEED", "42"));
        g.preset = MapPreset.valueOf(get("ET_PRESET", "CONTINENT"));
        g.scenarioId = get("ET_SIM", "free");
        g.companyName = "Test A.Ş.";
        GameState s = GameState.newGame(g);
        s.player.cash = 6e9;
        City c0 = s.cities.get(0), c1 = s.cities.get(Math.min(1, s.cities.size() - 1));
        Plant focus = RivalAI.buildNear(s, s.player, s.catalog.get("lu-hjt"), c0, true);
        RivalAI.buildNear(s, s.player, s.catalog.get("nw-45"), c0, true);
        RivalAI.buildNear(s, s.player, s.catalog.get("rs-36"), c0, true);
        RivalAI.buildNear(s, s.player, s.catalog.get("mp-ha"), c0, true);
        RivalAI.buildNear(s, s.player, s.catalog.get("th-usc"), c1, true);
        RivalAI.buildNear(s, s.player, s.catalog.get("atr-600"), c1, true);
        RivalAI.buildNear(s, s.player, s.catalog.get("al-pelton"), c1, true);
        RivalAI.buildNear(s, s.player, s.catalog.get("sp-perc"), c1, true);
        if (env.containsKey("ET_ALLPLANTS")) {
            City c2 = s.cities.get(Math.min(2, s.cities.size() - 1));
            for (String id : new String[] { "fk-sub", "fk-sc", "at-oc", "at-cc" }) RivalAI.buildNear(s, s.player, s.catalog.get(id), c2, true);
        }
        if (focus != null) { focus.upgrades.add(Upgrade.TRACKER); }
        for (int i = 0; i < 24 * 40; i++) s.tick();
        s.player.cash = 900e6;
        state = s;
        gs = new GameScreen(game, s);
        game.swap(gs);
        gs.speed(Integer.parseInt(get("ET_SPEED", "1")));
        if (env.containsKey("ET_HOUR")) gs.fixedHour = Float.parseFloat(get("ET_HOUR", "12"));
        float zoom = Float.parseFloat(get("ET_ZOOM", "48"));
        String focusEq = get("ET_FOCUS", "city");
        Plant fp = null;
        if (focusEq.startsWith("eq:")) for (Plant p : s.plants) if (p.equipment.id.equals(focusEq.substring(3))) { fp = p; break; }
        if (fp != null) gs.devFocus(fp.x, fp.y, zoom);
        else if (focusEq.equals("overview")) gs.devFocus(s.map.size / 2, s.map.size / 2, zoom);
        else gs.devFocus(c0.x, c0.y, zoom);
    }
}
