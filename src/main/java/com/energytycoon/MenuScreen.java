package com.energytycoon;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.energytycoon.render.WorldRenderer;
import com.energytycoon.save.SaveManager;
import com.energytycoon.sim.*;
import com.energytycoon.ui.MapPreview;
import com.energytycoon.world.MapPreset;

import java.util.Random;

public final class MenuScreen implements Screen {
    private final EnergyGame game;
    private final Skin skin;
    private final Stage stage = new Stage(new ScreenViewport());
    private final Texture bgTex, logoTex;
    private final Table root = new Table();
    private Label status;

    // yeni oyun ayarlari
    private final GameSettings settings = new GameSettings();
    private Image preview;
    private Texture previewTex;
    private Label scenDesc, presetDesc, cityLbl, rivalLbl, previewInfo;
    /** Onizleme lejanti: oyuncu, rakipler, Kamu Uretim, UŞİ hatti, sehir. */
    private final Table legend = new Table();
    private final ButtonGroup<TextButton> presetGroup = new ButtonGroup<>();
    private TextField seedField;
    private boolean dirty = true;
    private float dirtyTimer;

    public MenuScreen(EnergyGame game) {
        this.game = game;
        this.skin = game.skins.skin;
        settings.seed = 1 + new Random().nextInt(99999);
        bgTex = new Texture(Gdx.files.classpath("ui/menu_background.jpg"), true);
        bgTex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        logoTex = new Texture(Gdx.files.classpath("ui/logo_emblem.png"), true);
        logoTex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        Image bg = new Image(bgTex);
        bg.setScaling(Scaling.fill);
        bg.setFillParent(true);
        stage.addActor(bg);
        // sol kenari koyulastiran yumusak gecis: menu metni okunakli kalsin
        Image shade = new Image(skin.newDrawable("white", 0.01f, 0.03f, 0.07f, 0.35f));
        shade.setFillParent(true);
        stage.addActor(shade);

        root.setFillParent(true);
        stage.addActor(root);
        showMain();
    }

    private TextButton button(String text, String style, Runnable r) {
        TextButton b = new TextButton(text, skin, style);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { r.run(); } });
        return b;
    }

    /** Logo rozeti + oyun adi ve alt basligi. */
    private Table titleBlock() {
        Table t = new Table();
        t.add(new Image(logoTex)).size(88, 100).padRight(16);
        Table names = new Table();
        names.add(new Label("ENERJİ İMPARATORLUĞU", skin, "huge")).left().row();
        names.add(new Label("Enerji şirketi yönetim ve ekonomi simülasyonu", skin, "title")).left().row();
        t.add(names).left();
        return t;
    }

    private void showMain() {
        root.clear();
        Table col = new Table();
        col.add(titleBlock()).left().padBottom(26).row();

        Table body = new Table();
        body.defaults().width(330).padTop(8);
        body.add(button("Yeni Oyun", "menuAccent", this::showSetup)).height(56).padTop(4).row();
        TextButton cont = button("Devam Et", "menu", () -> {
            if (!(SaveManager.exists("save") && game.continueGame("save")) && !game.continueGame("autosave")) status.setText("Kayıt yüklenemedi.");
        });
        cont.setDisabled(!SaveManager.exists("save") && !SaveManager.exists("autosave"));
        if (cont.isDisabled()) cont.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        body.add(cont).height(50).row();
        body.add(button("Ayarlar", "menu", this::showSettings)).height(50).row();
        body.add(button("Çıkış", "menu", () -> Gdx.app.exit())).height(50).row();
        status = new Label("", skin, "warn");
        body.add(status).padTop(6).row();
        col.add(game.skins.framedPanel(new Label("ANA MENÜ", skin, "title"), body)).left().width(380).row();
        root.add(col).expand().left().padLeft(Math.max(40, Gdx.graphics.getWidth() * 0.07f));
    }

    /** Ayarlar: ana menu ile ayni yerlesimde, paylasilan SettingsPanel icerigiyle. */
    public void showSettings() {
        root.clear();
        Table col = new Table();
        col.add(titleBlock()).left().padBottom(26).row();
        Table body = new Table();
        body.add(com.energytycoon.ui.SettingsPanel.build(skin, game)).left().row();
        body.add(button("Geri", "menu", this::showMain)).width(330).height(50).padTop(14);
        col.add(game.skins.framedPanel(new Label("AYARLAR", skin, "title"), body)).left().width(380);
        root.add(col).expand().left().padLeft(Math.max(40, Gdx.graphics.getWidth() * 0.07f));
    }

    public void showSetup() {
        root.clear();
        scenDesc = presetDesc = cityLbl = rivalLbl = null;
        presetGroup.clear();
        Table card = new Table(skin);

        Table left = new Table();
        left.defaults().left();
        TextField name = new TextField(settings.companyName, skin);
        name.setTextFieldListener((f, c) -> settings.companyName = f.getText());
        left.add(new Label("Şirket adı", skin, "dim")).row();
        left.add(name).width(470).height(30).padBottom(8).row();

        left.add(new Label("Senaryo", skin, "dim")).row();
        Table scen = new Table();
        ButtonGroup<TextButton> sg = new ButtonGroup<>();
        sg.setMinCheckCount(0);
        presetGroup.setMinCheckCount(0);
        int col = 0;
        for (Scenario s : Scenarios.all()) {
            TextButton b = new TextButton(s.title + (s.deadlineYear > 0 ? "  (" + s.deadlineYear + ")" : ""), skin, "small");
            b.setChecked(s.id.equals(settings.scenarioId));
            b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) {
                if (b.isChecked()) { settings.scenarioId = s.id; refreshLabels(); dirty = true; }
            } });
            sg.add(b);
            scen.add(b).width(154).height(30).pad(1);
            if (++col % 3 == 0) scen.row();
        }
        left.add(scen).row();
        scenDesc = new Label("", skin, "small"); scenDesc.setWrap(true);
        left.add(scenDesc).width(470).padBottom(8).padTop(2).row();

        left.add(new Label("Harita tipi", skin, "dim")).row();
        Table pres = new Table();
        col = 0;
        for (MapPreset mp : MapPreset.values()) {
            TextButton b = new TextButton(mp.label, skin, "small");
            b.setName(mp.name());
            b.setChecked(mp == settings.preset);
            b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) {
                if (b.isChecked()) { settings.preset = mp; refreshLabels(); dirty = true; }
            } });
            presetGroup.add(b);
            pres.add(b).width(154).height(30).pad(1);
            if (++col % 3 == 0) pres.row();
        }
        left.add(pres).row();
        presetDesc = new Label("", skin, "small"); presetDesc.setWrap(true);
        left.add(presetDesc).width(470).padBottom(8).padTop(2).row();

        Table opts = new Table();
        opts.defaults().left().padRight(6);
        opts.add(new Label("Boyut", skin, "dim")).width(70);
        ButtonGroup<TextButton> sizeG = new ButtonGroup<>();
        sizeG.setMinCheckCount(0);
        Table sizes = new Table();
        for (int i = 0; i < GameSettings.SIZES.length; i++) {
            final int sz = GameSettings.SIZES[i];
            TextButton b = new TextButton(GameSettings.SIZE_LABELS[i] + " " + sz, skin, "small");
            b.setChecked(sz == settings.size);
            b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) {
                if (b.isChecked()) { settings.size = sz; settings.cities = Math.min(settings.cities, GameSettings.maxCities(sz)); refreshLabels(); dirty = true; }
            } });
            sizeG.add(b); sizes.add(b).width(100).height(28).pad(1);
        }
        opts.add(sizes).colspan(3).row();
        cityLbl = new Label("", skin); rivalLbl = new Label("", skin);
        opts.add(new Label("Şehir", skin, "dim")).width(70);
        opts.add(button("-", "small", () -> { settings.cities = Math.max(2, settings.cities - 1); refreshLabels(); dirty = true; })).size(28, 28);
        opts.add(cityLbl).width(30).center();
        opts.add(button("+", "small", () -> { settings.cities = Math.min(GameSettings.maxCities(settings.size), settings.cities + 1); refreshLabels(); dirty = true; })).size(28, 28).row();
        opts.add(new Label("Rakip", skin, "dim")).width(70);
        opts.add(button("-", "small", () -> { settings.rivals = Math.max(0, settings.rivals - 1); refreshLabels(); dirty = true; })).size(28, 28);
        opts.add(rivalLbl).width(30).center();
        opts.add(button("+", "small", () -> { settings.rivals = Math.min(3, settings.rivals + 1); refreshLabels(); dirty = true; })).size(28, 28).row();
        opts.add();
        CheckBox fleet = new CheckBox("  Rakipler kurulu santrallerle başlasın", skin, "toggle");
        fleet.setChecked(settings.rivalsStartWithFleet);
        fleet.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { settings.rivalsStartWithFleet = fleet.isChecked(); dirty = true; } });
        opts.add(fleet).colspan(3).left().padTop(2).row();
        opts.add();
        CheckBox grow = new CheckBox("  Ulusal şebeke gelişimi (UŞİ planları)", skin, "toggle");
        grow.setChecked(settings.operatorGrowth);
        grow.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { settings.operatorGrowth = grow.isChecked(); } });
        opts.add(grow).colspan(3).left().padTop(2).row();
        opts.add();
        CheckBox legacy = new CheckBox("  Kamu eski filosu", skin, "toggle");
        legacy.setChecked(settings.legacyFleet);
        legacy.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { settings.legacyFleet = legacy.isChecked(); dirty = true; } });
        opts.add(legacy).colspan(3).left().padTop(2).padBottom(4).row();
        opts.add(new Label("Zorluk", skin, "dim")).width(70);
        Table diffs = new Table();
        ButtonGroup<TextButton> dg = new ButtonGroup<>();
        dg.setMinCheckCount(0);
        for (Difficulty d : Difficulty.values()) {
            TextButton b = new TextButton(d.label, skin, "small");
            b.setChecked(d == settings.difficulty);
            b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { if (b.isChecked()) settings.difficulty = d; } });
            dg.add(b); diffs.add(b).width(100).height(28).pad(1);
        }
        opts.add(diffs).colspan(3).row();
        opts.add(new Label("Tohum", skin, "dim")).width(70);
        seedField = new TextField(String.valueOf(settings.seed), skin);
        seedField.setTextFieldListener((f, c) -> { settings.seed = parseSeed(f.getText()); dirty = true; });
        opts.add(seedField).colspan(2).width(150).height(28);
        opts.add(button("Rastgele", "small", () -> {
            settings.seed = 1 + new Random().nextInt(99999); seedField.setText(String.valueOf(settings.seed)); dirty = true;
        })).height(28).row();
        left.add(opts).row();

        // Ilk eklenen butonun otomatik isaretlenmesini onlemek icin gruplar 0 ile kuruldu; simdi tek secim zorunlu.
        for (ButtonGroup<TextButton> g : java.util.List.of(sg, presetGroup, sizeG, dg)) g.setMinCheckCount(1);
        Table right = new Table(skin);
        right.setBackground("panel2");
        right.pad(8);
        preview = new Image();
        preview.setScaling(Scaling.fit);
        right.add(new Label("Harita önizleme", skin, "dim")).left().row();
        right.add(preview).size(300, 300).row();
        previewInfo = new Label("", skin, "small"); previewInfo.setWrap(true);
        right.add(previewInfo).width(300).padTop(4).row();
        right.add(legend).width(300).left().padTop(4);

        card.add(left).top().padRight(14);
        card.add(right).top().row();
        Table buttons = new Table();
        buttons.add(button("Geri", "menu", this::showMain)).width(150).height(46).padRight(8);
        buttons.add(button("Oyunu Başlat", "menuAccent", () -> game.startNew(settings.copy()))).width(240).height(50);
        card.add(buttons).colspan(2).right().padTop(12);
        root.add(game.skins.framedPanel(new Label("YENİ OYUN", skin, "title"), card));
        refreshLabels();
        dirty = true;
    }

    private static long parseSeed(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return s.hashCode(); }
    }

    private void refreshLabels() {
        // ButtonGroup.add() secili butonu yeniden isaretler ve dinleyiciyi tetikler; etiketler henuz yoksa atla.
        if (scenDesc == null || presetDesc == null || cityLbl == null || rivalLbl == null) return;
        Scenario sc = settings.scenario();
        scenDesc.setText(sc.desc);
        MapPreset eff = sc.forcedPreset != null ? sc.forcedPreset : settings.preset;
        for (TextButton b : presetGroup.getButtons()) {
            boolean forced = sc.forcedPreset != null;
            b.setDisabled(forced && !b.getName().equals(sc.forcedPreset.name()));
            if (forced) b.setChecked(b.getName().equals(sc.forcedPreset.name()));
        }
        presetDesc.setText(eff.desc + (sc.forcedPreset != null ? "  (Bu senaryo haritayı belirler.)" : ""));
        cityLbl.setText(String.valueOf(settings.cities));
        rivalLbl.setText(String.valueOf(settings.rivals));
    }

    private void regeneratePreview() {
        GameState s = GameState.newGame(settings.copy());
        if (previewTex != null) previewTex.dispose();
        previewTex = MapPreview.render(s);
        preview.setDrawable(new TextureRegionDrawable(previewTex));
        int land = 0;
        for (boolean w : s.map.water) if (!w) land++;
        previewInfo.setText(s.map.preset.label + " · " + s.map.size + "×" + s.map.size + " · %" + Math.round(100.0 * land / s.map.water.length)
            + " kara · " + s.cities.size() + " şehir · " + s.companies.stream().filter(c -> !c.player && !c.neutral).count() + " rakip");
        legend.clear();
        legend.defaults().left().padBottom(1);
        legendRow(new Color[] { rgb(s.player.colorRgb) }, false, "Siz (oyuncu)");
        java.util.List<Color> rc = new java.util.ArrayList<>();
        for (com.energytycoon.sim.Company c : s.companies) if (!c.player && !c.neutral) rc.add(rgb(c.colorRgb));
        if (!rc.isEmpty()) legendRow(rc.toArray(new Color[0]), false, "Rakipler");
        if (s.neutral != null) legendRow(new Color[] { rgb(com.energytycoon.sim.GridBalance.NEUTRAL_COLOR) }, false, "Kamu Üretim (eski filo, \"K\")");
        legendRow(new Color[] { com.energytycoon.ui.MapPreview.OPERATOR_LINE }, true, "UŞİ hattı");
        legendRow(new Color[] { Color.WHITE }, false, "Şehir");
    }

    private static Color rgb(int v) { return new Color((v << 8) | 0xFF); }

    private void legendRow(Color[] colors, boolean line, String text) {
        Table sw = new Table();
        for (Color c : colors) {
            Image im = new Image(skin.newDrawable("white", c));
            if (line) sw.add(im).size(18, 3).padRight(2);
            else sw.add(im).size(10, 10).padRight(2);
        }
        legend.add(sw).width(48).left();
        legend.add(new Label(text, skin, "small")).left().row();
    }

    @Override public void show() { Gdx.input.setInputProcessor(stage); }

    @Override public void render(float dt) {
        Gdx.gl.glClearColor(0.02f, 0.04f, 0.08f, 1);
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);
        if (dirty && preview != null && preview.getStage() != null) {
            dirtyTimer += dt;
            if (dirtyTimer > 0.15f) { dirty = false; dirtyTimer = 0; regeneratePreview(); }
        }
        stage.act(dt);
        stage.draw();
    }

    @Override public void resize(int w, int h) { stage.getViewport().update(w, h, true); }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}
    @Override public void dispose() { stage.dispose(); bgTex.dispose(); logoTex.dispose(); if (previewTex != null) previewTex.dispose(); }
}
