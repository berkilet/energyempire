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

/** Scene2D arayuz: ust cubuk, sol alt arac cubugu + yatirim menusu, bilgi paneli, senaryo paneli, pencereler, bildirimler. */
public final class Hud {
    public enum Tool { SELECT, PLANT, LINE, DEMOLISH, SUBSTATION }

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
    private final Label company, cash, debt, value, date, price, demand, info, placeInfo;
    private final Table toasts = new Table();
    private final Table dock = new Table(), layerPop = new Table(), ctxBar = new Table();
    private final ImageButton investBtn, selectBtn, demBtn, layerBtn;
    private final TextTooltip layerTip;
    private final Map<Layer, TextButton> layerBtns = new EnumMap<>(Layer.class);
    private final InvestMenu invest;
    private final Label ctxTitle;
    @SuppressWarnings("rawtypes") private com.badlogic.gdx.scenes.scene2d.ui.Cell ctxTitleCell;
    private Tool curTool = Tool.SELECT;
    private LineTier curTier = LineTier.YG;
    /** Faz 4/5 ozellikleri acildiginda true yapilir. */
    public boolean substationsEnabled = true, operatorEnabled;
    private int curTmLevel;
    private static final Map<PlantType, String> PLANT_ICONS = new EnumMap<>(Map.of(
        PlantType.COAL, "icon_factory", PlantType.GAS, "icon_factory", PlantType.SOLAR, "icon_solar",
        PlantType.WIND, "icon_wind", PlantType.HYDRO, "icon_hydro", PlantType.NUCLEAR, "icon_bolt"));
    private final Map<PlantType, Equipment> chosen = new EnumMap<>(PlantType.class);
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

        // ----- sol alt: arac cubugu (dock) + yatirim menusu + katman listesi
        investBtn = dockButton("icon_build", "Yatırım (B)", () -> toggleInvest());
        selectBtn = dockButton("icon_cursor", "Seç / İncele (sağ tık veya Esc ile dönülür)", () -> { hideInvest(); cb.tool(Tool.SELECT, null); });
        demBtn = dockButton("icon_demolish", "Yık / Hat Kaldır (Delete)", () -> { hideInvest(); cb.tool(Tool.DEMOLISH, null); });
        layerBtn = dockButton("icon_layers", "Harita katmanı (L)", () -> layerPop.setVisible(!layerPop.isVisible()));
        layerTip = (TextTooltip) layerBtn.getListeners().select(l -> l instanceof TextTooltip).iterator().next();
        dock.setBackground(skin.getDrawable("card"));
        dock.pad(5);
        for (ImageButton b : new ImageButton[] { investBtn, selectBtn, demBtn, layerBtn }) dock.add(b).size(52, 54).pad(2);
        dock.pack();

        layerPop.setBackground(skin.getDrawable("card"));
        layerPop.pad(6);
        layerPop.add(l("HARİTA KATMANI (L)", "dim")).left().padBottom(4).row();
        for (Layer ly : Layer.values()) {
            TextButton b = new TextButton(ly.label, skin, "small");
            b.setProgrammaticChangeEvents(false);
            final Layer fl = ly;
            b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { cb.layer(fl); layerPop.setVisible(false); } });
            layerBtns.put(ly, b);
            layerPop.add(b).width(150).height(28).padBottom(2).row();
        }
        layerPop.pack();
        layerPop.setVisible(false);

        invest = new InvestMenu(skins);
        InvestMenu.Category plantsCat = new InvestMenu.Category("Santral Yatırımları", true)
            .add(plantGroup("Fosil", PlantType.COAL, PlantType.GAS))
            .add(plantGroup("Yenilenebilir", PlantType.SOLAR, PlantType.WIND, PlantType.HYDRO))
            .add(plantGroup("Nükleer", PlantType.NUCLEAR));
        InvestMenu.Group lineGroup = new InvestMenu.Group("Hat Çek");
        for (LineTier t : LineTier.values()) lineGroup.add(new InvestMenu.Item("icon_pylon", t.label + "  (en çok " + (int) t.maxLength + " kare)",
            () -> Fmt.mw(t.capacityMW) + " · " + Fmt.money(t.costPerTile) + "/kare",
            () -> true, () -> st == null || st.player.cash >= t.costPerTile * 3,
            () -> curTool == Tool.LINE && curTier == t,
            () -> { cb.action("line:" + t.name()); if (!invest.pinned()) hideInvest(); }));
        // Faz 4/5'te gorunur olacak kalemler: veri tabanli; gorunur satiri olmayan grup/kategori cizilmez.
        InvestMenu.Group tmGroup = new InvestMenu.Group("Trafo Merkezi")
            .add(tmItem(1, "TM154  (yarıçap " + (int) GridBalance.TM_RADIUS + ")", Fmt.mw(GridBalance.TM154_MW) + " trafo · " + Fmt.money(GridBalance.TM154_COST) + " · " + GridBalance.TM154_DAYS + " gün"))
            .add(tmItem(2, "TM380  (ÇYG bağlanabilir)", Fmt.mw(GridBalance.TM380_MW) + " trafo · " + Fmt.money(GridBalance.TM380_COST) + " · " + GridBalance.TM380_DAYS + " gün"));
        InvestMenu.Group opGroup = new InvestMenu.Group(null)
            .add(new InvestMenu.Item("icon_city", "UŞİ Başvurularım", () -> "", () -> operatorEnabled, () -> true, () -> false, () -> cb.action("operatorApps")));
        invest.add(plantsCat).add(new InvestMenu.Category("Bağlantı Yatırımları", false).add(lineGroup).add(tmGroup).add(opGroup));
        invest.update();
        invest.root.setVisible(false);

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

        placeInfo = l("", "small");
        placeInfo.setAlignment(Align.center);
        ctxTitle = l("", "default");
        ctxTitle.setAlignment(Align.center);
        ctxBar.pad(6, 16, 6, 16);
        ctxTitleCell = ctxBar.add(ctxTitle);
        ctxBar.row();
        ctxBar.add(placeInfo);
        toasts.bottom();

        Table root = new Table();
        root.setFillParent(true);
        root.setTouchable(Touchable.childrenOnly);
        root.add(top).growX().row();
        Table mid = new Table();
        mid.add().grow();
        mid.add(rightCol).top().right().padRight(6).padTop(4);
        root.add(mid).grow();
        stage.addActor(root);
        stage.addActor(dock);
        stage.addActor(layerPop);
        stage.addActor(invest.root);
        // Bildirimler ve alt bilgi ayri katmanda: sayilari artinca ana yerlesimi (ust cubugu) itmesinler.
        Table bottom = new Table();
        bottom.setFillParent(true);
        bottom.setTouchable(Touchable.disabled);
        bottom.bottom();
        bottom.add(ctxBar).padBottom(4).row();
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
      + "1) Sol alttaki Yatırım düğmesine (B) basın, Santral Yatırımları altından bir türe tıklayın: tedarikçi ve model penceresi açılır. Panel/türbin/kazan modeli üretimi, "
      + "verimi, yaşlanmayı, fiyatı ve inşaat süresini belirler. Tedarikçi itibarı arıza ve teslim gecikmesi riskini, garanti süresi ücretsiz onarımı belirler.\n"
      + "2) Modeli seçtikten sonra haritaya tıklayın. Yeşil kutu = uygun yer. Alttaki çubuk o sahada beklenen performansı gösterir.\n"
      + "3) Yatırım → Bağlantı Yatırımları → Hat Çek (OG/YG/ÇYG) seçip önce santrale, sonra şehre tıklayın. Tab kademeyi değiştirir. "
      + "Hattın kapasitesi santralin satabileceği gücü sınırlar; uzun hatta kayıp artar. Hatsız santral elektrik satamaz.\n"
      + "Bitişik kendi santralleriniz tek bağlantı noktasıdır (küme): tek hattı paylaşırlar. Trafo merkezi (Yatırım → Bağlantı) "
      + "yarıçapındaki santrallerinizi toplar; ihracatı trafo gücü ve hatlarla sınırlıdır. Başkasının açık TM'sine bağlanınca MWh başına erişim bedeli ödersiniz.\n"
      + "4) Her saat talep en ucuz santralden başlayarak karşılanır (merit order). Fiyat son devreye giren santralin maliyetidir.\n"
      + "5) Kendi santralinizi seçince yükseltme butonları çıkar: güneşte tracker ve robotik temizlik, rüzgarda repowering, "
      + "kömür/gazda karbon yakalama, nükleerde ömür uzatma vb.\n"
      + "6) Yeni teknolojiler yıllar içinde piyasaya çıkar (ör. 2028 dev türbin, 2030 SMR, 2033 tandem panel).\n"
      + "7) Karşılanamayan talep ceza getirir, karbon vergisi her yıl artar. Nakit yetmezse Finans'tan borçlanın.\n\n"
      + "Kontroller: Sağ tık sürükle = kaydır, tekerlek = yakınlaştır, Q/E = döndür, WASD/oklar = kaydır, Boşluk = duraklat, "
      + "1/2/3 = hız (1x: bir gün 24 sn), B = yatırım, Delete = yık, Tab = hat kademesi, L = katman, F5/F9 = kaydet/yükle, Esc = iptal/menü.";

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
            if (!invest.pinned()) hideInvest();
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

    private ImageButton dockButton(String icon, String tipText, Runnable r) {
        ImageButton.ImageButtonStyle st = new ImageButton.ImageButtonStyle(skin.get("small", TextButton.TextButtonStyle.class));
        st.imageUp = skin.getDrawable(icon);
        ImageButton b = new ImageButton(st);
        b.getImageCell().size(40, 42);
        b.setProgrammaticChangeEvents(false);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { r.run(); } });
        return tip(b, tipText);
    }

    private InvestMenu.Item tmItem(int level, String title, String sub) {
        double cost = level >= 2 ? GridBalance.TM380_COST : GridBalance.TM154_COST;
        return new InvestMenu.Item("icon_pylon", title, () -> sub, () -> substationsEnabled, () -> st == null || st.player.cash >= cost,
            () -> curTool == Tool.SUBSTATION && curTmLevel == level,
            () -> { cb.action("substation:" + level); if (!invest.pinned()) hideInvest(); });
    }

    public void setTmLevel(int level) { curTmLevel = level; }

    private InvestMenu.Group plantGroup(String title, PlantType... types) {
        InvestMenu.Group g = new InvestMenu.Group(title);
        for (PlantType t : types) g.add(new InvestMenu.Item(PLANT_ICONS.get(t), t.label, () -> plantSub(t), () -> true,
            () -> st == null || st.player.cash >= shownEquipment(t).capex(),
            () -> curTool == Tool.PLANT && curEq != null && curEq.type == t,
            () -> openEquipment(t)));
        return g;
    }

    private Equipment curEq;

    /** Secili model varsa o, yoksa bugun alinabilen en ucuz model. */
    private Equipment shownEquipment(PlantType t) {
        Equipment ce = chosen.get(t);
        if (ce != null || st == null) return ce != null ? ce : null;
        Equipment best = null;
        for (Equipment e : st.catalog.available(t, st.year())) if (best == null || e.capex() < best.capex()) best = e;
        return best != null ? best : st.catalog.defaultFor(t);
    }

    private String plantSub(PlantType t) {
        if (st == null) return "";
        Equipment ce = chosen.get(t);
        if (ce != null) return "Seçili: " + ce.supplier.name + " " + ce.model + " · " + Fmt.money(ce.capex());
        return "En düşük model fiyatı: " + Fmt.money(shownEquipment(t).capex());
    }

    // ----- yatirim menusu: ac/kapa (~150 ms kayarak/solarak)
    /** Kapanma animasyonu suresince de kapali sayilir. */
    public boolean investOpen() { return investShown; }
    private boolean investShown;
    public void toggleInvest() { if (investOpen()) hideInvest(); else showInvest(); }
    public void showInvest() {
        if (investShown) return;
        investShown = true;
        layerPop.setVisible(false);
        invest.update();
        invest.root.clearActions();
        invest.root.pack();
        float x = investX(), y = investY();
        invest.root.setVisible(true);
        invest.root.getColor().a = 0;
        invest.root.setPosition(x, y - 12);
        invest.root.addAction(Actions.parallel(Actions.fadeIn(0.15f), Actions.moveTo(x, y, 0.15f)));
        invest.root.toFront();
    }
    public void hideInvest() {
        if (!investShown) return;
        investShown = false;
        invest.root.clearActions();
        invest.root.addAction(Actions.sequence(Actions.parallel(Actions.fadeOut(0.15f), Actions.moveBy(0, -12, 0.15f)), Actions.visible(false)));
    }
    /** Dogrulama: menuyu animasyonsuz ac. */
    public void devShowInvest(String category) {
        invest.open(category);
        showInvest();
        invest.root.clearActions();
        invest.root.getColor().a = 1;
        invest.root.setPosition(investX(), investY());
    }
    private float investX() { return dock.getX(); }
    private float investY() { return dock.getY() + dock.getHeight() + 6; }

    private void layoutOverlays() {
        dock.setPosition(8, 8);
        layerPop.setPosition(dock.getX() + layerBtn.getX(), dock.getY() + dock.getHeight() + 6);
        if (invest.root.isVisible() && invest.root.getActions().isEmpty()) {
            invest.root.pack();
            invest.root.setPosition(investX(), investY());
        }
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

    private String contextText(Tool tool, Equipment eq, LineTier tier) {
        return switch (tool) {
            case PLANT -> eq == null ? "" : eq.type.label + " · " + eq.supplier.name + " " + eq.model + " · " + Fmt.money(eq.capex())
                + " · Sol tık: yerleştir · Esc: iptal";
            case LINE -> "Hat Çek · " + tier.label + " · " + Fmt.mw(tier.capacityMW) + " · " + Fmt.money(tier.costPerTile) + "/kare"
                + " · Sol tık: başlangıç, sonra bitiş · Tab: kademe · Esc: iptal";
            case DEMOLISH -> "Yık / Hat Kaldır · Sol tık: kendi santraliniz, trafo merkeziniz veya hattınız · Esc: iptal";
            case SUBSTATION -> (curTmLevel >= 2 ? "TM380" : "TM154") + " trafo merkezi · " + Fmt.money(curTmLevel >= 2 ? GridBalance.TM380_COST : GridBalance.TM154_COST)
                + " · yarıçap " + (int) GridBalance.TM_RADIUS + " · Sol tık: kur · Esc: iptal";
            default -> "";
        };
    }

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

    public void update(GameState s, Tool tool, Equipment eq, int speed, Layer layer, LineTier tier) {
        this.st = s;
        curTool = tool; curEq = eq; curTier = tier;
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
        investBtn.setChecked(investOpen() || tool == Tool.PLANT || tool == Tool.LINE || tool == Tool.SUBSTATION);
        selectBtn.setChecked(tool == Tool.SELECT && !investOpen());
        demBtn.setChecked(tool == Tool.DEMOLISH);
        layerBtn.setChecked(layerPop.isVisible());
        for (Map.Entry<Layer, TextButton> e : layerBtns.entrySet()) e.getValue().setChecked(e.getKey() == layer);
        ((Label) layerTip.getActor()).setText("Harita katmanı (L) · Aktif: " + layer.label);
        if (investOpen()) invest.update();
        layoutOverlays();
        boolean ctx = tool != Tool.SELECT;
        ctxBar.setBackground(ctx ? skin.getDrawable("card") : null);
        ctxTitle.setText(contextText(tool, eq, tier));
        if (ctx != (ctxTitleCell.getActor() != null)) { ctxTitleCell.setActor(ctx ? ctxTitle : null); ctxBar.invalidateHierarchy(); }

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
