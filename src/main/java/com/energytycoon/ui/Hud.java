package com.energytycoon.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.energytycoon.render.TerrainRenderer.Layer;
import com.energytycoon.sim.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Scene2D arayuz: ust cubuk, insa paleti, bilgi paneli, senaryo paneli, pencereler, bildirimler. */
public final class Hud {
    public enum Tool { SELECT, PLANT, LINE, DEMOLISH }

    public interface Listener {
        void tool(Tool t, Equipment e);
        void speed(int s);
        void layer(Layer l);
        void action(String a);
    }

    public final Stage stage;
    private final Skins skins;
    private final Skin skin;
    private final Listener cb;
    private GameState st;

    @SuppressWarnings("rawtypes") private com.badlogic.gdx.scenes.scene2d.ui.Cell companyCell, debtCell;
    private Actor debtChip;
    private final Label company, cash, debt, value, date, price, demand, info, placeInfo, layerLbl;
    private final Table toasts = new Table();
    private final Map<PlantType, ImageTextButton> plantBtns = new EnumMap<>(PlantType.class);
    private static final Map<PlantType, String> PLANT_ICONS = new EnumMap<>(Map.of(
        PlantType.COAL, "icon_factory", PlantType.GAS, "icon_factory", PlantType.SOLAR, "icon_solar",
        PlantType.WIND, "icon_wind", PlantType.HYDRO, "icon_hydro", PlantType.NUCLEAR, "icon_bolt"));
    private final Map<PlantType, Equipment> chosen = new EnumMap<>(PlantType.class);
    private final TextButton selectBtn, lineBtn, demBtn;
    private final ImageButton[] speedBtns = new ImageButton[4];
    private Label speedLbl;
    /** Hiz kademelerinin gercek carpani (1x = bir gunde 24 sn). */
    private static final String[] SPEED_TEXT = { "", "1x", "4x", "20x" };
    private final Table actions = new Table();
    private String actionsKey = "";
    private final Label scenTitle, scenBody;
    private final Window finance, market, companies, help, menu, gameOverWin;
    private final EquipmentWindow equipWin;
    private final Label gameOverLbl;
    private final FinanceView financeView;
    private final MarketView marketView;
    private final CompaniesView companiesView;
    private Window settingsWin;
    private final List<LineChart> charts = new ArrayList<>();
    private boolean gameOverShown;

    public Hud(Skins skins, Listener cb) {
        this.skins = skins; this.skin = skins.skin; this.cb = cb;
        stage = new Stage(new ScreenViewport());

        // ----- ust cubuk
        Table top = new Table(skin);
        top.setBackground("bar");
        top.pad(4, 8, 4, 8);
        company = l("", "title"); cash = l("", "default"); debt = l("", "default"); value = l("", "default");
        date = l("", "default"); price = l("", "default"); demand = l("", "default");
        companyCell = top.add(company).left().padRight(10);
        top.add(tip(skins.chip("coin", cash, false), "Nakit")).padRight(4);
        debtChip = tip(skins.chip("battery", debt, false), "Borç");
        debtCell = top.add(debtChip).padRight(4);
        top.add(tip(skins.chip("upgrade", value, true), "Şirket piyasa değeri")).padRight(4);
        top.add(tip(skins.chip("bolt", price, false), "Ortalama elektrik fiyatı")).padRight(4);
        top.add(tip(skins.chip("city", demand, false), "Toplam talep / sizin üretiminiz")).padRight(8);
        top.add(date).padRight(6);
        top.add().expandX();
        // Zaman kontrolleri: 0 = duraklat/devam (tek dugme, durumuna gore ikon degisir), 1-3 = hiz kademeleri.
        String[] styles = { "timePause", "time1", "time2", "time3" };
        String[] tips = { "Duraklat / devam et (Boşluk)", "Normal hız: bir gün 24 sn (1)", "Hızlı: 4 kat (2)", "Çok hızlı: 20 kat (3)" };
        for (int i = 0; i < 4; i++) {
            final int s = i;
            ImageButton b = new ImageButton(skin, styles[i]);
            b.getImageCell().size(28, 28);
            // update() her karede setChecked cagirir; bu kodla degisiklikler "tiklandi" olayi uretmemeli,
            // yoksa onceki hiz butonu isaretten cikarken oyunu yeniden baslatir (durdurma calismaz).
            b.setProgrammaticChangeEvents(false);
            b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) {
                if (s == 0) cb.action("togglePause"); else cb.speed(s);
            } });
            speedBtns[i] = b;
            top.add(tip(b, tips[i])).size(34, 34).padLeft(1);
        }
        speedLbl = l("", "value");
        top.add(speedLbl).width(34).padLeft(4).padRight(6);
        String[][] wins = { { "icon_coin", "finance", "Finans (F)" }, { "icon_bolt", "market", "Piyasa ve tedarikçiler (M)" },
                            { "icon_city", "companies", "Şirketler ve şehirler (C)" }, { null, "help", "Nasıl oynanır? (H)" },
                            { "icon_gear", "menu", "Menü: kaydet, yükle, ayarlar (Esc)" } };
        for (String[] w : wins) {
            final String a = w[1];
            Button b;
            if (w[0] != null) {
                ImageButton.ImageButtonStyle st = new ImageButton.ImageButtonStyle(skin.get("small", TextButton.TextButtonStyle.class));
                st.imageUp = skin.getDrawable(w[0]);
                ImageButton ib = new ImageButton(st);
                ib.getImageCell().size(22, 23);
                b = ib;
            } else {
                b = new TextButton("?", skin, "small");
            }
            b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor x) { cb.action(a); } });
            top.add(tip(b, w[2])).size(42, 32).padLeft(2);
        }

        // ----- sol: insa paleti
        Table left = new Table(skin);
        Label hint = l("Tür seçin → tedarikçi/model seçin → haritaya tıklayın", "dim");
        hint.setWrap(true);
        left.add(hint).width(216).left().padBottom(6).row();
        for (PlantType t : PlantType.values()) {
            ImageTextButton.ImageTextButtonStyle st = new ImageTextButton.ImageTextButtonStyle(skin.get("small", TextButton.TextButtonStyle.class));
            st.imageUp = skin.getDrawable(PLANT_ICONS.get(t));
            ImageTextButton b = new ImageTextButton("", st);
            b.getImageCell().size(30, 32).padLeft(4);
            b.getLabel().setAlignment(Align.left);
            b.getLabel().setWrap(true);
            b.getLabelCell().pad(2, 6, 2, 6).width(166);
            b.setProgrammaticChangeEvents(false);
            b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) {
                b.setChecked(!b.isChecked()); // durum update()'te belirlenir
                openEquipment(t);
            } });
            plantBtns.put(t, b);
            left.add(b).width(216).minHeight(44).padBottom(3).row();
        }
        selectBtn = tool("Seç / İncele", Tool.SELECT);
        lineBtn = tool("Hat Çek (iletim)", Tool.LINE);
        demBtn = tool("Yık / Hat Kaldır", Tool.DEMOLISH);
        left.add(l("ARAÇLAR", "dim")).left().padTop(8).row();
        left.add(selectBtn).growX().height(30).padBottom(3).row();
        left.add(lineBtn).growX().height(30).padBottom(3).row();
        left.add(demBtn).growX().height(30).padBottom(3).row();
        left.add(l("HARİTA KATMANI (L)", "dim")).left().padTop(8).row();
        Table lay = new Table();
        int col = 0;
        for (Layer ly : Layer.values()) {
            TextButton b = new TextButton(ly.label, skin, "small");
            final Layer fl = ly;
            b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { cb.layer(fl); } });
            // Sabit genislik: 3 x 70 + bosluklar = 216 (panel icerik genisligi); yazi genisligi paneli buyutmesin.
            lay.add(b).height(26).width(70).pad(1);
            if (++col % 3 == 0) lay.row();
        }
        left.add(lay).left().row();
        layerLbl = l("", "dim");
        left.add(layerLbl).left().padTop(3);
        Table leftFrame = skins.framedPanel(l("İNŞA", "title"), left);

        // ----- sag: secim + senaryo
        Table right = new Table(skin);
        info = l("Haritada bir santrale veya şehre tıklayın.", "small");
        info.setWrap(true);
        right.add(info).width(270).left().row();
        right.add(actions).growX().padTop(6).row();

        Table scen = new Table(skin);
        scenTitle = l("", "title");
        scenBody = l("", "small");
        scenBody.setWrap(true);
        scen.add(scenBody).width(270).left();

        Table rightCol = new Table();
        rightCol.add(skins.framedPanel(l("SEÇİM", "title"), right)).top().right().growX().row();
        rightCol.add(skins.framedPanel(scenTitle, scen)).top().right().growX().padTop(4);

        placeInfo = l("", "default");
        placeInfo.setAlignment(Align.center);
        toasts.bottom();

        Table root = new Table();
        root.setFillParent(true);
        root.setTouchable(Touchable.childrenOnly);
        root.add(top).growX().row();
        Table mid = new Table();
        mid.add(leftFrame).top().left().padLeft(6).padTop(4);
        mid.add().grow();
        mid.add(rightCol).top().right().padRight(6).padTop(4);
        root.add(mid).grow();
        stage.addActor(root);
        // Bildirimler ve alt bilgi ayri katmanda: sayilari artinca ana yerlesimi (ust cubugu) itmesinler.
        Table bottom = new Table();
        bottom.setFillParent(true);
        bottom.setTouchable(Touchable.disabled);
        bottom.bottom();
        bottom.add(placeInfo).padBottom(4).row();
        bottom.add(toasts).padBottom(12);
        stage.addActor(bottom);

        // ----- pencereler
        finance = window("Finans");
        financeView = new FinanceView(skins, () -> cb.action("borrow"), () -> cb.action("repay"));
        finance.add(financeView.root).pad(4);
        charts.add(financeView.chart);

        market = window("Piyasa");
        marketView = new MarketView(skins);
        market.add(marketView.root).pad(4);
        charts.addAll(marketView.charts);

        companies = window("Şirketler ve Şehirler");
        companiesView = new CompaniesView(skins);
        companies.add(companiesView.root).pad(4).padRight(16);

        help = window("Nasıl Oynanır?");
        Label hl = l(HELP, "small"); hl.setWrap(true);
        help.add(hl).width(560).pad(10);
        help.pack();

        menu = window("Menü");
        String[][] mi = { { "Devam Et", "menu" }, { "Kaydet (F5)", "save" }, { "Yükle (F9)", "load" }, { "Ayarlar", "settings" } };
        for (String[] it : mi) menu.add(btn(it[0], it[1], "default")).width(240).height(34).pad(4).row();
        menu.add(btn("Ana Menü", "main", "default")).width(240).height(34).pad(4).row();
        menu.add(btn("Çıkış", "exit", "default")).width(240).height(34).pad(4).row();
        menu.pack();

        gameOverLbl = l("", "default"); gameOverLbl.setWrap(true);
        gameOverWin = window("Oyun Bitti");
        gameOverWin.add(gameOverLbl).width(400).pad(12).row();
        gameOverWin.add(btn("Ana Menüye Dön", "main", "accent")).width(200).height(36).padBottom(12);
        gameOverWin.pack();

        equipWin = new EquipmentWindow(skin);
        skins.decorateWindow(equipWin, () -> equipWin.setVisible(false));

        for (Window w : new Window[] { finance, market, companies, help, menu, gameOverWin, equipWin }) {
            stage.addActor(w);
            w.setVisible(false);
        }
        help.setVisible(true);
        center(help);
    }

    private static final String HELP =
        "Amaç: Senaryo hedeflerine ulaşın (sağ alttaki panel). Serbest oyunda hedef $4 milyar piyasa değeri.\n\n"
      + "1) Soldan bir santral türüne tıklayın: tedarikçi ve model penceresi açılır. Panel/türbin/kazan modeli üretimi, "
      + "verimi, yaşlanmayı, fiyatı ve inşaat süresini belirler. Tedarikçi itibarı arıza ve teslim gecikmesi riskini, garanti süresi ücretsiz onarımı belirler.\n"
      + "2) Modeli seçtikten sonra haritaya tıklayın. Yeşil kutu = uygun yer. Alttaki çubuk o sahada beklenen performansı gösterir.\n"
      + "3) 'Hat Çek' ile önce santrale, sonra şehre tıklayarak iletim hattı kurun. Hatsız santral elektrik satamaz.\n"
      + "4) Her saat talep en ucuz santralden başlayarak karşılanır (merit order). Fiyat son devreye giren santralin maliyetidir.\n"
      + "5) Kendi santralinizi seçince yükseltme butonları çıkar: güneşte tracker ve robotik temizlik, rüzgarda repowering, "
      + "kömür/gazda karbon yakalama, nükleerde ömür uzatma vb.\n"
      + "6) Yeni teknolojiler yıllar içinde piyasaya çıkar (ör. 2028 dev türbin, 2030 SMR, 2033 tandem panel).\n"
      + "7) Karşılanamayan talep ceza getirir, karbon vergisi her yıl artar. Nakit yetmezse Finans'tan borçlanın.\n\n"
      + "Kontroller: Sağ tık sürükle = kaydır, tekerlek = yakınlaştır, Q/E = döndür, WASD/oklar = kaydır, Boşluk = duraklat, "
      + "1/2/3 = hız (1x: bir gün 24 sn), L = katman, F5/F9 = kaydet/yükle, Esc = iptal/menü.";

    private TextButton btn(String text, String action, String style) {
        TextButton b = new TextButton(text, skin, style);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor x) { cb.action(action); } });
        return b;
    }

    private void openEquipment(PlantType t) {
        if (st == null) return;
        equipWin.show(st, t, chosen.get(t), e -> {
            chosen.put(t, e);
            cb.tool(Tool.PLANT, e);
        });
    }

    public Equipment chosen(PlantType t) { return chosen.get(t); }
    public void devOpenEquipment(PlantType t) { openEquipment(t); }
    /** Dogrulama: hiz butonunun ekran koordinati (sol-ust orijinli). */
    public int[] devSpeedButtonScreenPos(int i) {
        com.badlogic.gdx.math.Vector2 v = speedBtns[i].localToStageCoordinates(new com.badlogic.gdx.math.Vector2(speedBtns[i].getWidth() / 2, speedBtns[i].getHeight() / 2));
        stage.stageToScreenCoordinates(v);
        return new int[] { (int) v.x, (int) v.y };
    }

    private TextButton tool(String text, Tool t) {
        TextButton b = new TextButton(text, skin, "small");
        b.setProgrammaticChangeEvents(false);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { cb.tool(t, null); } });
        return b;
    }

    private Label l(String t, String style) { return new Label(t, skin, style); }

    private Window window(String title) {
        Window win = new Window(title, skin);
        win.setMovable(true);
        skins.decorateWindow(win, () -> win.setVisible(false));
        return win;
    }

    private void center(Window w) {
        w.setPosition((stage.getWidth() - w.getWidth()) / 2, (stage.getHeight() - w.getHeight()) / 2);
    }

    /** Acik yonetim pencerelerinin icerigini gunceller. */
    private void refreshWindows(GameState s) {
        if (s == null) return;
        if (finance.isVisible()) financeView.update(s);
        if (market.isVisible()) marketView.update(s);
        if (companies.isVisible()) companiesView.update(s);
    }

    public void toggle(String name) {
        if (name.equals("settings")) { showSettings(); return; }
        Window w = switch (name) { case "finance" -> finance; case "market" -> market; case "companies" -> companies; case "help" -> help; default -> menu; };
        boolean show = !w.isVisible();
        w.setVisible(show);
        if (show) {
            // Once icerik doldurulur, sonra boyutlandirilir: bos metinle paketlenip sonradan tasmasin.
            refreshWindows(st);
            w.toFront(); w.pack(); center(w);
        }
    }

    public boolean anyWindowOpen() {
        return finance.isVisible() || market.isVisible() || companies.isVisible() || help.isVisible() || menu.isVisible() || equipWin.isVisible()
            || (settingsWin != null && settingsWin.isVisible());
    }
    public void closeWindows() {
        finance.setVisible(false); market.setVisible(false); companies.setVisible(false); help.setVisible(false);
        menu.setVisible(false); equipWin.setVisible(false);
        if (settingsWin != null) settingsWin.setVisible(false);
    }
    public void hideHelp() { help.setVisible(false); }
    /** Oyun ici "Ayarlar" penceresini, ayarlari okuyup uygulayan modelle kurar. */
    public void attachSettings(SettingsPanel.Model model) {
        settingsWin = window("Ayarlar");
        settingsWin.add(SettingsPanel.build(skin, model)).pad(6).row();
        settingsWin.add(btn("Tamam", "settings", "accent")).width(200).height(34).padTop(10);
        settingsWin.pack();
        settingsWin.setVisible(false);
        stage.addActor(settingsWin);
    }

    private void showSettings() {
        menu.setVisible(false);
        boolean show = !settingsWin.isVisible();
        settingsWin.setVisible(show);
        if (show) { settingsWin.toFront(); center(settingsWin); }
    }

    private static double[] arr(List<Double> l) {
        double[] a = new double[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    public void setState(GameState s) { this.st = s; gameOverShown = false; }

    public void toast(String msg) {
        Label lb = new Label(msg, skin, "default");
        Table t = new Table(skin);
        t.setBackground("row");
        t.add(lb).pad(4, 16, 4, 16);
        t.getColor().a = 0;
        t.addAction(Actions.sequence(Actions.fadeIn(0.2f), Actions.delay(5f), Actions.fadeOut(0.6f), Actions.removeActor()));
        toasts.add(t).padTop(3).row();
        if (toasts.getChildren().size > 5) toasts.getChildren().first().remove();
        toasts.invalidateHierarchy();
    }

    public void setPlaceInfo(String s) { placeInfo.setText(s); }

    /** actionList: {etiket, eylem-id, "1" etkin / "0" pasif} */
    public void setSelection(String text, List<String[]> actionList) {
        info.setText(text);
        StringBuilder key = new StringBuilder();
        for (String[] a : actionList) key.append(a[0]).append('|').append(a[1]).append('|').append(a[2]).append(';');
        if (key.toString().equals(actionsKey)) return;
        actionsKey = key.toString();
        actions.clear();
        for (String[] a : actionList) {
            TextButton b = new TextButton(a[0], skin, "small");
            b.getLabel().setWrap(true);
            b.setDisabled("0".equals(a[2]));
            final String id = a[1];
            b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor x) { if (!((TextButton) x).isDisabled()) cb.action(id); } });
            actions.add(b).width(270).minHeight(28).padBottom(3).row();
        }
    }

    @SuppressWarnings("unchecked")
    public void resize(int w, int h) {
        stage.getViewport().update(w, h, true);
        // Dar pencerede ust cubuk tasmasin: once sirket adi, sonra borc cipi gizlenir.
        company.setVisible(w >= 1280);
        companyCell.setActor(w >= 1280 ? company : null);
        debtCell.setActor(w >= 1130 ? debtChip : null);
        for (Actor a : stage.getRoot().getChildren()) if (a instanceof Table t) t.invalidateHierarchy();
    }

    private <T extends Actor> T tip(T actor, String text) {
        TextTooltip t = new TextTooltip(text, skin);
        t.setInstant(false);
        actor.addListener(t);
        return actor;
    }

    private static String bar(double p) {
        int n = (int) Math.round(Math.max(0, Math.min(1, p)) * 10);
        return "█".repeat(n) + "░".repeat(10 - n);
    }

    public void update(GameState s, Tool tool, Equipment eq, int speed, Layer layer) {
        this.st = s;
        Company p = s.player;
        company.setText(p.name.length() > 18 ? p.name.substring(0, 18) : p.name);
        cash.setText(Fmt.money(p.cash));
        Label.LabelStyle cs = skin.get(p.cash < 0 ? "valueBad" : "value", Label.LabelStyle.class);
        if (cash.getStyle() != cs) cash.setStyle(cs);
        debt.setText(Fmt.money(p.debt));
        value.setText(Fmt.money(p.valuation));
        price.setText("$" + Fmt.f1(s.statPrice) + "/MWh");
        demand.setText(Fmt.mw(s.statDemand) + " · siz " + Fmt.mw(s.statPlayerMW));
        date.setText(String.format("%02d %s %d %02d:00", s.dayOfMonth(), Fmt.MONTHS[s.month()], s.year(), s.hourOfDay()) + (speed == 0 ? "  DURAKLATILDI" : ""));
        for (int i = 1; i < 4; i++) speedBtns[i].setChecked(i == speed);
        // Duraklatildiginda ayni dugme "devam et" ikonuna ve parlak halkaya doner.
        ImageButton.ImageButtonStyle ps = skin.get(speed == 0 ? "timeResume" : "timePause", ImageButton.ImageButtonStyle.class);
        if (speedBtns[0].getStyle() != ps) speedBtns[0].setStyle(ps);
        speedLbl.setText(speed == 0 ? "" : SPEED_TEXT[speed]);
        if (speed == 0) date.setColor(Skins.WARN); else date.setColor(Color.WHITE);
        for (Map.Entry<PlantType, ImageTextButton> e : plantBtns.entrySet()) {
            PlantType t = e.getKey();
            Equipment ce = chosen.get(t);
            Equipment shown = ce != null ? ce : s.catalog.defaultFor(t);
            String txt = t.label + "  ·  " + Fmt.money(shown.capex()) + "\n" + (ce != null ? shown.supplier.name + " " + shown.model : "model seçmek için tıklayın");
            if (!e.getValue().getText().toString().equals(txt)) e.getValue().setText(txt);
            boolean can = p.cash >= shown.capex();
            e.getValue().getLabel().setColor(can ? Color.WHITE : new Color(1f, 0.55f, 0.5f, 1));
            e.getValue().setChecked(tool == Tool.PLANT && eq != null && eq.type == t);
        }
        selectBtn.setChecked(tool == Tool.SELECT); lineBtn.setChecked(tool == Tool.LINE); demBtn.setChecked(tool == Tool.DEMOLISH);
        layerLbl.setText("Aktif: " + layer.label);

        // senaryo paneli
        Scenario sc = s.scenario;
        scenTitle.setText(sc.title + (s.won ? "  ✓" : ""));
        StringBuilder sb = new StringBuilder();
        if (sc.deadlineYear > 0) sb.append("Süre: ").append(sc.deadlineYear).append(" sonuna kadar (")
            .append(Math.max(0, sc.deadlineYear - s.year() + 1)).append(" yıl)\n");
        for (Objective o : sc.objectives) {
            boolean met = o.met(s);
            sb.append(met ? "✓ " : "• ").append(o.label).append("\n  ").append(bar(o.progress(s))).append("  ").append(o.status(s)).append('\n');
        }
        scenBody.setText(sb.toString().trim());

        refreshWindows(s);
        if (s.gameOver && !gameOverShown) {
            gameOverShown = true;
            gameOverLbl.setText(s.gameOverReason + "\n\nSon piyasa değeri: " + Fmt.money(s.player.valuation) + "\nSüre: " + (s.year() - Balance.START_YEAR) + " yıl");
            gameOverWin.setVisible(true); center(gameOverWin); gameOverWin.toFront();
        }
    }

    public void render(float dt) {
        stage.act(dt);
        stage.draw();
    }

    public void dispose() {
        for (LineChart c : charts) c.dispose();
        stage.dispose();
    }
}
