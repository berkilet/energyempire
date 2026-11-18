package com.energytycoon;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.energytycoon.save.SaveManager;
import com.energytycoon.sim.GameSettings;
import com.energytycoon.sim.GameState;
import com.energytycoon.ui.Skins;

public final class EnergyGame extends Game implements com.energytycoon.ui.SettingsPanel.Model {
    public Skins skins;
    private Preferences prefs;
    private DevHook dev;

    @Override public void create() {
        skins = new Skins();
        prefs = Gdx.app.getPreferences("energytycoon");
        dev = DevHook.fromEnv(this);
        com.energytycoon.render.TerrainRenderer.defaultDetail = natureDetail();
        if (fullscreen() && dev == null) applyFullscreen(true);
        if (dev == null || !dev.skipMenu()) toMenu();
    }

    @Override public int shadowQuality() { return prefs.getInteger("shadow", 1); }
    @Override public void setShadowQuality(int q) { prefs.putInteger("shadow", q); prefs.flush(); }
    @Override public boolean dayNight() { return prefs.getBoolean("daynight", true); }
    @Override public void setDayNight(boolean on) { prefs.putBoolean("daynight", on); prefs.flush(); }
    @Override public int natureDetail() { return prefs.getInteger("nature", 1); }
    @Override public void setNatureDetail(int d) { prefs.putInteger("nature", d); prefs.flush(); com.energytycoon.render.TerrainRenderer.defaultDetail = d; }
    @Override public boolean fullscreen() { return prefs.getBoolean("fullscreen", false); }
    @Override public void setFullscreen(boolean on) { prefs.putBoolean("fullscreen", on); prefs.flush(); applyFullscreen(on); }

    private static void applyFullscreen(boolean on) {
        if (on) Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        else Gdx.graphics.setWindowedMode(1360, 800);
    }

    public void toMenu() { swap(new MenuScreen(this)); }

    public void startNew(GameSettings settings) {
        if (settings.companyName == null || settings.companyName.isBlank()) settings.companyName = "Yeni Enerji A.Ş.";
        swap(new GameScreen(this, GameState.newGame(settings)));
    }

    public boolean continueGame(String slot) {
        GameState s = SaveManager.load(slot);
        if (s == null) return false;
        swap(new GameScreen(this, s));
        return true;
    }

    void swap(com.badlogic.gdx.Screen next) {
        com.badlogic.gdx.Screen old = getScreen();
        setScreen(next);
        if (old != null) old.dispose();
    }

    @Override public void render() {
        super.render();
        if (dev != null) dev.frame();
    }

    @Override public void dispose() {
        if (getScreen() != null) getScreen().dispose();
        com.energytycoon.render.KenneyModels.disposeShared();
        skins.dispose();
    }
}
