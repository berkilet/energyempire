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
    private Table right, top;
    private ScrollPane selScroll;
    private Cell<ScrollPane> selCell;
    /** Kamu santrali "K" rozetleri: ekran uzayinda sabit boyut, her yakinlastirmada okunur. */
    private final com.badlogic.gdx.scenes.scene2d.Group badges = new com.badlogic.gdx.scenes.scene2d.Group();
    private Label.LabelStyle badgeStyle;
    private final ImageButton investBtn, financeBtn, assetsBtn, selectBtn, demBtn, layerBtn;
    private final TextTooltip layerTip;
    private final Map<Layer, TextButton> layerBtns = new EnumMap<>(Layer.class);
    private final InvestMenu invest;
    private final Label ctxTitle;
    @SuppressWarnings("rawtypes") private com.badlogic.gdx.scenes.scene2d.ui.Cell ctxTitleCell, placeInfoCell;

    /** Ekran genisligini asan metin kirpilmaz: genislik sinirlanir ve satir kaydirilir; kisa metin icerige gore genisler. */
    @SuppressWarnings("rawtypes")
    private void wrapToScreen(Label l, com.badlogic.gdx.scenes.scene2d.ui.Cell cell) {
        if (cell == null || cell.getActor() == null) return;
        float max = Math.max(320, stage.getWidth() - 340); // sol arac cubugu ve sag panel payi
        l.setWrap(false);
        float pref = l.getPrefWidth();
        boolean wrap = pref > max;
        l.setWrap(wrap);
        cell.width(wrap ? max : pref);
    }
    private Tool curTool = Tool.SELECT;
    private LineTier curTier = LineTier.YG;
    /** Faz 4/5 ozellikleri acildiginda true yapilir. */
    public boolean substationsEnabled = true, operatorEnabled = true;
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
    private final Window finance, market, companies, help, menu, gameOverWin, lineChoice, assetsWin;
    private final AssetsView assets;
    private long assetsRefreshAt;
    private final Label lcTitle, lcOwn, lcOp;
    private final TextButton lcOwnBtn, lcOpBtn;
    private final EquipmentWindow equipWin;
    private final Label gameOverLbl;
    private final FinanceCenter financeView;
    private LoanWizard wizard;
    private Window wizardWin, restructWin, dealWin, defenseWin, confirmWin;
    private DealWindow deal;
    private DefenseWindow defense;
    private Label confirmText;
    private String confirmAction;
    private long financeRefreshAt;
    /** Ekipman penceresinde ECA secimi: acik mi, banka, taksit sikligi (ay). */
    private boolean ecaOn;
    private Bank ecaBank = Bank.KIYI;
    private int ecaFreq = 6;
    private final MarketView marketView;
    private final CompaniesView companiesView;
    private Window settingsWin;
    private final List<LineChart> charts = new ArrayList<>();
    private boolean gameOverShown;

    public Hud(Skins skins, Listener cb) {
        this.skins = skins; this.skin = skins.skin; this.cb = cb;
        stage = new Stage(new ScreenViewport());

        // ----- ust cubuk
        top = new Table(skin);
        top.setBackground("bar");
        top.pad(4, 8, 4, 8);
        company = l("", "title"); cash = l("", "default"); debt = l("", "default"); value = l("", "default");
        date = l("", "default"); price = l("", "default"); demand = l("", "default");
        companyCell = top.add(company).left().padRight(10);
        Table cashChip = skins.chip("coin", cash, false);
        cashChip.setTouchable(Touchable.enabled);
        cashChip.addListener(new ClickListener() { @Override public void clicked(InputEvent e, float x, float y) { toggle("finance"); } });
        top.add(tip(cashChip, "Nakit · tıklayınca Finans (F)")).padRight(4);
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
        financeBtn = dockButton("icon_coin", "Finans (F)", () -> { hideInvest(); toggle("finance"); });
        selectBtn = dockButton("icon_cursor", "Seç / İncele (sağ tık veya Esc ile dönülür)", () -> { hideInvest(); cb.tool(Tool.SELECT, null); });
        demBtn = dockButton("icon_demolish", "Yık / Hat Kaldır (Delete)", () -> { hideInvest(); cb.tool(Tool.DEMOLISH, null); });
        assetsBtn = dockButton("icon_assets", "Varlıklarım (V)", () -> { hideInvest(); toggle("assets"); });
        layerBtn = dockButton("icon_layers", "Harita katmanı (L)", () -> layerPop.setVisible(!layerPop.isVisible()));
        layerTip = (TextTooltip) layerBtn.getListeners().select(l -> l instanceof TextTooltip).iterator().next();
        dock.setBackground(skin.getDrawable("card"));
        dock.pad(5);
        for (ImageButton b : new ImageButton[] { investBtn, financeBtn, assetsBtn, selectBtn, demBtn, layerBtn }) dock.add(b).size(52, 54).pad(2);
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
        // Ihaleler: acik ihaleler (sabit sayida yuva, gorunurluk listeye gore) + kazanilanlar
        InvestMenu.Group openTenders = new InvestMenu.Group("Açık ihaleler");
        for (int k = 0; k < MAX_TENDER_ROWS; k++) {
            final int idx = k;
            openTenders.add(new InvestMenu.Item("icon_city", () -> tenderAt(idx) == null ? "" : tenderAt(idx).city.name + " · " + Math.round(tenderAt(idx).mw) + " MW acil kapasite",
                () -> tenderSub(tenderAt(idx)), () -> tenderAt(idx) != null, () -> true, () -> false,
                () -> { Tender t = tenderAt(idx); if (t != null) { showTender(t.id); if (!invest.pinned()) hideInvest(); } }));
        }
        InvestMenu.Group wonTenders = new InvestMenu.Group(null).add(new InvestMenu.Item("icon_coin",
            () -> "Kazanılan ihaleler (" + (st == null ? 0 : st.tenders.wonBy(st.player).size()) + ")", () -> "Yükümlülükler Varlıklarım → İhaleler'de",
            () -> st != null && !st.tenders.wonBy(st.player).isEmpty(), () -> true, () -> false, () -> cb.action("assets:tenders")));
        invest.add(plantsCat).add(new InvestMenu.Category("Bağlantı Yatırımları", false).add(lineGroup).add(tmGroup))
            .add(new InvestMenu.Category("İhaleler", false).add(openTenders).add(wonTenders))
            .add(new InvestMenu.Category("Satıştaki Varlıklar", false).add(saleGroup()));
        invest.update();
        invest.root.setVisible(false);

        // ----- sag: secim + senaryo
        right = new Table(skin);
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
        selScroll = new ScrollPane(right, skin);
        selScroll.setFadeScrollBars(false);
        selScroll.setOverscroll(false, false);
        selScroll.setScrollingDisabled(true, false);
        Table selBody = new Table();
        selCell = selBody.add(selScroll).width(284);
        rightCol.add(skins.framedPanel(l("SEÇİM", "title"), selBody)).top().right().growX().row();
        rightCol.add(skins.framedPanel(scenTitle, scen)).top().right().growX().padTop(4);

        placeInfo = l("", "small");
        placeInfo.setAlignment(Align.center);
        ctxTitle = l("", "default");
        ctxTitle.setAlignment(Align.center);
        ctxBar.pad(6, 16, 6, 16);
        ctxTitleCell = ctxBar.add(ctxTitle);
        ctxBar.row();
        placeInfoCell = ctxBar.add(placeInfo);
        toasts.bottom();

        Table root = new Table();
        root.setFillParent(true);
        root.setTouchable(Touchable.childrenOnly);
        root.add(top).growX().row();
        Table mid = new Table();
        mid.add().grow();
        mid.add(rightCol).top().right().padRight(6).padTop(4);
        root.add(mid).grow();
        stage.addActor(badges); // rozetler tum panellerin altinda
        badges.setTouchable(Touchable.disabled);
        stage.addActor(root);
        stage.addActor(dock);
        stage.addActor(layerPop);
        stage.addActor(invest.root);
        // Bildirimler ve alt bilgi ayri katmanda: sayilari artinca ana yerlesimi (ust cubugu) itmesinler.
        Table bottom = new Table();
        bottom.setFillParent(true);
        // Bildirimler tiklanabilir (ihale vb.); baglam cubugu tiklama almaz.
        bottom.setTouchable(Touchable.childrenOnly);
        ctxBar.setTouchable(Touchable.disabled);
        toasts.setTouchable(Touchable.childrenOnly);
        bottom.bottom();
        bottom.add(ctxBar).padBottom(4).row();
        bottom.add(toasts).padBottom(12);
        stage.addActor(bottom);

        // ----- pencereler
        finance = window("Finans");
        financeView = new FinanceCenter(skins, a -> {
            if (a.equals("wizard")) { if (st != null) { wizard.show(st); wizardWin.toFront(); } }
            else cb.action(a);
        });
        finance.add(scrolled(finance, financeView.root)).pad(4);
        charts.add(financeView.valueChart); charts.add(financeView.policyChart); charts.add(financeView.indexChart); charts.add(financeView.capitalTab.priceChart);
        dealWin = window("İşlem");
        deal = new DealWindow(skin, dealWin, cb::action);
        dealWin.setVisible(false);
        defenseWin = window("Savunma — pay alım teklifi");
        defense = new DefenseWindow(skin, defenseWin, cb::action);
        defenseWin.setVisible(false);
        confirmWin = window("Onay");
        confirmText = l("", "default"); confirmText.setWrap(true);
        confirmWin.add(confirmText).width(480).pad(8).row();
        Table cbt = new Table();
        TextButton okB = new TextButton("Onayla", skin, "accent"), noB = new TextButton("Vazgeç", skin);
        okB.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { confirmWin.setVisible(false); if (confirmAction != null) cb.action(confirmAction); } });
        noB.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { confirmWin.setVisible(false); } });
        cbt.add(okB).width(180).height(34).padRight(8); cbt.add(noB).width(180).height(34);
        confirmWin.add(cbt).padBottom(6);
        confirmWin.setVisible(false);
        wizardWin = window("Yeni Kredi");
        wizard = new LoanWizard(skin, wizardWin, cb::action);
        wizardWin.setVisible(false);
        restructWin = window("Yeniden Yapılandırma Teklifi");
        Label rt = l("Ticari krediniz temerrüde düştü ve tüm ticari krediler muaccel oldu. Bankalar oyunda bir kez kullanılabilecek bir yapılandırma öneriyor:\n"
            + "• 3 ay koruma süresi (tahsilat yok)\n• Ticari kredilerin vadesi 3 yıl uzar, faiz +2 puan\n• 6 ay içinde en az bir santralinizi satmalısınız\n• Kredi notunuz CCC olur\n\n"
            + "Reddederseniz şirketiniz iflas eder.", "small");
        rt.setWrap(true);
        restructWin.add(rt).width(520).pad(8).row();
        Table rb = new Table();
        rb.add(btn("Kabul et", "restruct:accept", "accent")).width(200).height(34).padRight(8);
        rb.add(btn("Reddet (iflas)", "restruct:reject", "default")).width(200).height(34);
        restructWin.add(rb).padBottom(6);
        restructWin.pack();
        restructWin.setVisible(false);

        market = window("Piyasa");
        marketView = new MarketView(skins);
        market.add(scrolled(market, marketView.root)).pad(4);
        charts.addAll(marketView.charts);

        companies = window("Şirketler ve Şehirler");
        companiesView = new CompaniesView(skins);
        companies.add(scrolled(companies, companiesView.root)).pad(4).padRight(16);

        help = window("Nasıl Oynanır?");
        Label hl = l(HELP, "small"); hl.setWrap(true);
        Table hlBox = new Table(); hlBox.add(hl).width(560).pad(10);
        help.add(scrolled(help, hlBox));
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

        // ----- hat: kendin kur / UŞİ'ye basvur
        lineChoice = window("Hat Bağlantısı");
        lcTitle = l("", "default"); lcTitle.setWrap(true);
        lineChoice.add(lcTitle).colspan(2).width(560).left().padBottom(8).row();
        lcOwn = l("", "small"); lcOwn.setWrap(true);
        lcOp = l("", "small"); lcOp.setWrap(true);
        Table own = new Table(skin); own.setBackground("card"); own.pad(10);
        own.add(l("KENDİN KUR", "title")).left().row();
        own.add(lcOwn).width(250).left().padTop(4).padBottom(8).row();
        lcOwnBtn = btn("Kendin kur", "lineOwn", "accent");
        own.add(lcOwnBtn).width(250).height(34);
        Table op = new Table(skin); op.setBackground("card"); op.pad(10);
        op.add(l("UŞİ'YE BAŞVUR", "title")).left().row();
        op.add(lcOp).width(250).left().padTop(4).padBottom(8).row();
        lcOpBtn = btn("UŞİ'ye başvur", "lineOp", "default");
        op.add(lcOpBtn).width(250).height(34);
        lineChoice.add(own).top().padRight(8).growY();
        lineChoice.add(op).top().growY().row();
        lineChoice.add(btn("Vazgeç", "lineCancel", "default")).colspan(2).width(160).height(30).padTop(8);
        lineChoice.pack();

        // ----- UŞİ Basvurularim
        // ----- Varliklarim (santraller, hatlar, TM, UŞİ basvurulari, ihaleler)
        assetsWin = window("Varlıklarım");
        assets = new AssetsView(skins, cb::action);
        assetsWin.add(assets.root).pad(4);

        equipWin = new EquipmentWindow(skin);
        skins.decorateWindow(equipWin, () -> equipWin.setVisible(false));

        stage.addActor(wizardWin); stage.addActor(restructWin); stage.addActor(dealWin); stage.addActor(defenseWin); stage.addActor(confirmWin);
        for (Window w : new Window[] { finance, market, companies, help, menu, gameOverWin, equipWin, lineChoice, assetsWin }) {
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
      + "Bir şehir 12 ay üst üste %60'ın altında kalırsa UŞİ acil kapasite ihalesi açar (Yatırım → İhaleler): en düşük 10 yıllık fiyat teklifi kazanır, kazanan 24 ayda kurmazsa teminatı yanar.\n"
      + "Nakit sıkışırsa santralinizi/TM'nizi satışa çıkarabilirsiniz (seçim paneli): 30 gün şirketlere açık; alıcı çıkmazsa santrali Kamu (isterse), TM'yi UŞİ taban fiyattan alır. "
      + "Rakiplerin ilanları Yatırım → Satıştaki Varlıklar'da. İflas eden şirketin varlıkları yıkılmaz, Kamu'ya ve UŞİ'ye geçer.\n"
      + "4) Her saat talep en ucuz santralden başlayarak karşılanır (merit order). Fiyat son devreye giren santralin maliyetidir.\n"
      + "5) Kendi santralinizi seçince yükseltme butonları çıkar: güneşte tracker ve robotik temizlik, rüzgarda repowering, "
      + "kömür/gazda karbon yakalama, nükleerde ömür uzatma vb.\n"
      + "6) Yeni teknolojiler yıllar içinde piyasaya çıkar (ör. 2028 dev türbin, 2030 SMR, 2033 tandem panel).\n"
      + "7) Karşılanamayan talep ceza getirir, karbon vergisi her yıl artar.\n"
      + "8) Finans (F, araç çubuğu veya nakit göstergesi): Kredi notunuz Net borç/FAVÖK ve FAVÖK/faiz oranlarından çeyreklik hesaplanır; faiz = politika faizi + not primi. "
      + "Krediler sekmesindeki 'Yeni kredi…' sihirbazı üç bankanın teklifini yan yana gösterir. ECA kredisi (ekipman bedelinin %85'i, ödemesiz dönem, daha ucuz) "
      + "yalnızca santral siparişinde, ekipman penceresinden seçilir. Taksitler vade günü nakitten otomatik düşer; nakit yetmezse işletme kredisi limiti kullanılır, "
      + "o da yetmezse gecikme (30 gün tolerans) ve 90 günde temerrüt başlar. Ödeme Takvimi sekmesi önümüzdeki 12 ayın taksitlerini ve olası nakit açığını gösterir.\n\n"
      + "Kontroller: Sağ tık sürükle = kaydır, tekerlek = yakınlaştır, Q/E = döndür, WASD/oklar = kaydır, Boşluk = duraklat, "
      + "1/2/3 = hız (1x: bir gün 24 sn), B = yatırım, V = varlıklarım, Delete = yık, Tab = hat kademesi, L = katman, F5/F9 = kaydet/yükle, Esc = iptal/menü.";

    private TextButton btn(String text, String action, String style) {
        TextButton b = new TextButton(text, skin, style);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor x) { cb.action(action); } });
        return b;
    }

    private void openEquipment(PlantType t) {
        if (st == null) return;
        equipWin.ecaLine = e -> {
            String no = st.finance.ecaRefusal(st.player, e, ecaBank);
            if (no != null) return no;
            Finance.EcaQuote q = st.finance.ecaQuote(st.player, e, ecaBank, ecaFreq);
            return "ECA uygun · " + e.supplier.origin + " · tahmini taksit " + Fmt.money(q.installment) + " / " + (ecaFreq == 6 ? "6 ay" : "yıl")
                + " · peşin " + Fmt.money(st.finance.ecaCashNeed(st.player, e, ecaBank, ecaFreq)) + " · tahmini DSCR " + FinanceCenter.num(st.finance.expectedDscr(st.player, e, st.cities.get(0).x, st.cities.get(0).y, ecaBank, ecaFreq));
        };
        equipWin.footer.clear();
        Table ft = equipWin.footer;
        ft.add(l("Finansman:", "default")).padRight(8);
        ft.add(ecaToggle("ECA kredisi ile al (bedelin %85'i)", ecaOn, () -> ecaOn = !ecaOn, t)).height(28).padRight(10);
        for (Bank b : Bank.values()) ft.add(ecaToggle(b.label, ecaBank == b, () -> ecaBank = b, t)).height(28).padRight(3);
        ft.add(ecaToggle("6 ayda bir", ecaFreq == 6, () -> ecaFreq = 6, t)).height(28).padLeft(10).padRight(3);
        ft.add(ecaToggle("yılda bir", ecaFreq == 12, () -> ecaFreq = 12, t)).height(28);
        equipWin.show(st, t, chosen.get(t), e -> {
            chosen.put(t, e);
            cb.tool(Tool.PLANT, e);
            if (!invest.pinned()) hideInvest();
        });
    }

    private TextButton ecaToggle(String text, boolean on, Runnable r, PlantType t) {
        TextButton b = new TextButton(text, skin, "small");
        b.setChecked(on);
        b.setProgrammaticChangeEvents(false);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { r.run(); openEquipment(t); } });
        return b;
    }

    public boolean ecaOn() { return ecaOn; }
    public Bank ecaBank() { return ecaBank; }
    public int ecaFreq() { return ecaFreq; }

    /** Yapilandirma teklifi penceresi (oyuncu karar verene kadar). */
    public void showRestructuring(boolean show) {
        if (show == restructWin.isVisible()) return;
        restructWin.setVisible(show);
        if (show) { center(restructWin); restructWin.toFront(); }
    }

    /** Dogrulama: sihirbazi Kiyibank teklifi secili halde acar. */
    public void devWizard() { if (st != null) { wizard.show(st); wizard.devSelect(Bank.KIYI); } }

    /** Islem penceresi (kurucuya teklif, piyasadan alim/satis, cagri). */
    public void openDeal(Company target, DealWindow.Mode mode) { if (st != null) deal.show(st, target, mode); }

    public void devDealCounter() { deal.devCounter(); }

    /** Savunma ekrani (sirketinize cagri). */
    public void showDefense() { if (st != null) defense.show(st); }
    public boolean defenseOpen() { return defenseWin.isVisible(); }

    /** Onay penceresi: onaylanirsa action calisir. */
    public void confirm(String text, String action) {
        confirmText.setText(text); confirmAction = action;
        confirmWin.pack(); center(confirmWin); confirmWin.setVisible(true); confirmWin.toFront();
    }

    public void selectMnaCompany(int idx) { financeView.mnaTab.select(idx); }

    public void openFinance(FinanceCenter.Tab tab) {
        financeView.setTab(tab);
        financeRefreshAt = 0;
        if (!finance.isVisible()) toggle("finance");
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

    static final int MAX_TENDER_ROWS = 8;

    // ----- satistaki varliklar (baska sirketlerin ilanlari)
    private Listing listingAt(int i) {
        if (st == null) return null;
        int k = 0;
        for (Listing l : st.sales.open()) if (l.seller != st.player && k++ == i) return l;
        return null;
    }

    private InvestMenu.Group saleGroup() {
        InvestMenu.Group g = new InvestMenu.Group(null);
        for (int k = 0; k < MAX_TENDER_ROWS; k++) {
            final int idx = k;
            g.add(new InvestMenu.Item("icon_coin", () -> listingAt(idx) == null ? "" : listingAt(idx).assetName(),
                () -> { Listing l = listingAt(idx); return l == null ? "" : l.seller.name + " · " + Fmt.money(l.price) + " · kalan " + Math.max(0, (l.expiresHour - st.hour) / 24) + " gün"; },
                () -> listingAt(idx) != null, () -> listingAt(idx) == null || st.player.cash >= listingAt(idx).price, () -> false,
                () -> { Listing l = listingAt(idx); if (l != null) { showListing(l.id); if (!invest.pinned()) hideInvest(); } }));
        }
        return g;
    }

    private Window listingWin;
    private Label listingInfo;
    private TextButton listingBuy;
    private int listingId;

    public void showListing(int id) {
        if (st == null || st.sales.get(id) == null) return;
        listingId = id;
        if (listingWin == null) {
            listingWin = window("Varlık Satışı");
            listingInfo = l("", "small"); listingInfo.setWrap(true);
            listingWin.add(listingInfo).width(480).left().row();
            Table bt = new Table();
            listingBuy = btnRun("Satın al", () -> cb.action("buy:" + listingId));
            bt.add(listingBuy).width(220).height(34).padRight(8);
            bt.add(btnRun("Haritada göster", () -> { Listing l = st.sales.get(listingId); if (l != null) cb.action("focus:node:" + l.asset.id); })).width(150).height(34).padRight(8);
            bt.add(btnRun("Kapat", () -> listingWin.setVisible(false))).width(100).height(34);
            listingWin.add(bt).padTop(10);
            stage.addActor(listingWin);
        }
        Listing l = st.sales.get(id);
        StringBuilder sb = new StringBuilder();
        sb.append(l.assetName()).append('\n').append("Satıcı: ").append(l.seller.name).append(" · durum: ").append(l.status.label).append('\n')
          .append("Fiyat: ").append(Fmt.money(l.price)).append(" (defter değerinin %").append(Math.round(Balance.SALE_ASK * 100)).append("'i; sahibinin bağlı hatları dahil)\n");
        if (l.asset instanceof Plant p) sb.append("Geçen ay kâr: ").append(Fmt.money(p.lastMonthProfit)).append(" · yaş ").append(Fmt.f1(p.ageYears(st.hour))).append(" yıl\n");
        if (l.status == Listing.Status.OPEN) sb.append("Kalan süre: ").append(Math.max(0, (l.expiresHour - st.hour) / 24)).append(" gün. Alıcı çıkmazsa ")
            .append(l.asset instanceof Substation ? "UŞİ" : "Kamu (isterse)").append(" taban fiyattan alır.");
        listingInfo.setText(sb.toString());
        listingBuy.setText("Satın al — " + Fmt.money(l.price));
        listingBuy.setDisabled(l.status != Listing.Status.OPEN || l.seller == st.player || st.player.cash < l.price);
        listingWin.pack(); center(listingWin);
        listingWin.setVisible(true); listingWin.toFront();
    }

    public void hideListing() { if (listingWin != null) listingWin.setVisible(false); }

    private Tender tenderAt(int i) {
        if (st == null) return null;
        List<Tender> o = st.tenders.open();
        return i < o.size() ? o.get(i) : null;
    }

    private String tenderSub(Tender t) {
        if (t == null || st == null) return "";
        Tender.Bid b = t.bidOf(st.player);
        return "kalan " + st.tenders.daysLeft(t) + " gün · " + (b == null ? "teklifiniz yok" : "teklifiniz $" + Fmt.f1(b.price) + "/MWh");
    }

    // ----- ihale teklif penceresi
    private Window tenderWin;
    private int tenderId;
    private double tenderPrice;
    private Label tenderInfo, tenderPriceLbl;

    public void showTender(int id) {
        if (st == null || st.tenders.get(id) == null) return;
        tenderId = id;
        Tender t = st.tenders.get(id);
        Tender.Bid b = t.bidOf(st.player);
        tenderPrice = b != null ? b.price : Math.round(Math.max(Balance.TENDER_MIN_PRICE, st.statPrice * 1.3));
        if (tenderWin == null) {
            tenderWin = window("Acil Kapasite İhalesi");
            tenderInfo = l("", "small"); tenderInfo.setWrap(true);
            tenderWin.add(tenderInfo).width(520).left().row();
            Table pr = new Table();
            pr.add(l("Teklif ($/MWh, 10 yıl):", "default")).padRight(10);
            for (int d : new int[] { -10, -1 }) pr.add(btnRun((d > 0 ? "+" : "−") + Math.abs(d), () -> adjustTender(d))).size(46, 30).padRight(2);
            tenderPriceLbl = l("", "kpi");
            pr.add(tenderPriceLbl).width(90).center();
            for (int d : new int[] { 1, 10 }) pr.add(btnRun("+" + d, () -> adjustTender(d))).size(46, 30).padLeft(2);
            tenderWin.add(pr).padTop(10).row();
            Table bt = new Table();
            bt.add(btnRun("Teklif ver / güncelle", () -> cb.action("bid:" + tenderId + ":" + tenderPrice))).width(220).height(34).padRight(8);
            bt.add(btnRun("Kapat", () -> tenderWin.setVisible(false))).width(120).height(34);
            tenderWin.add(bt).padTop(10);
            stage.addActor(tenderWin);
        }
        refreshTender();
        tenderWin.pack(); center(tenderWin);
        tenderWin.setVisible(true); tenderWin.toFront();
    }

    private void adjustTender(double d) {
        tenderPrice = Math.max(Balance.TENDER_MIN_PRICE, Math.min(Balance.TENDER_MAX_PRICE, tenderPrice + d));
        refreshTender();
    }

    private TextButton btnRun(String text, Runnable r) {
        TextButton b = new TextButton(text, skin, "small");
        b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { r.run(); } });
        return b;
    }

    private void refreshTender() {
        if (tenderWin == null || st == null) return;
        Tender t = st.tenders.get(tenderId);
        if (t == null) return;
        Tender.Bid b = t.bidOf(st.player);
        StringBuilder sb = new StringBuilder();
        sb.append(t.city.name).append(" için ").append(Math.round(t.mw)).append(" MW acil kapasite · durum: ").append(t.status.label).append('\n')
          .append("Teknoloji serbest. Santral şehre en fazla ").append((int) Balance.TENDER_MAX_DIST).append(" kare uzakta ve şehrin şebekesine bağlı olmalı.\n")
          .append("Kazanan, ihale MW'ına kadar üretimini ").append(Balance.TENDER_CONTRACT_YEARS).append(" yıl teklif fiyatından satar; fazlası piyasa fiyatından.\n")
          .append("Teminat: ").append(Fmt.money(st.tenders.depositFor(t))).append(" (ilk teklifte; kaybederseniz iade, kazanıp ")
          .append(Balance.TENDER_BUILD_MONTHS).append(" ayda kurmazsanız yanar).\n");
        if (t.status == Tender.Status.OPEN) sb.append("Kalan süre: ").append(st.tenders.daysLeft(t)).append(" gün · en düşük teklif kazanır · bugünkü piyasa fiyatı $")
            .append(Fmt.f1(st.statPrice)).append("/MWh\n");
        else if (t.winner != null) sb.append("Kazanan: ").append(t.winner.name).append(" · $").append(Fmt.f1(t.price)).append("/MWh\n");
        sb.append(b == null ? "Henüz teklif vermediniz." : "Teklifiniz: $" + Fmt.f1(b.price) + "/MWh");
        tenderInfo.setText(sb.toString());
        tenderPriceLbl.setText("$" + Fmt.f1(tenderPrice));
    }

    /** Tiklanabilir bildirim (ör. ihale): tiklaninca eylem. */
    public void toast(String msg, String action) {
        toast(msg);
        if (action == null) return;
        Actor last = toasts.getChildren().peek();
        last.setTouchable(Touchable.enabled);
        last.addListener(new ClickListener() { @Override public void clicked(InputEvent e, float x, float y) { cb.action(action); } });
        if (last instanceof Table t && t.getChildren().size > 0 && t.getChildren().first() instanceof Label lb) lb.setText(msg + "  ›");
    }

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
        invest.setMaxBodyHeight(stage.getHeight() * 0.70f - 120);
        // secim paneli: uzun metin/eylem listesi ekranin %70'ini asarsa kaydirilir
        right.pack();
        float selMax = Math.max(160, stage.getHeight() * 0.70f - 120);
        float want = Math.min(right.getPrefHeight(), selMax);
        if (Math.abs(selCell.getMaxHeight() - want) > 0.5f) { selCell.height(want); selScroll.invalidateHierarchy(); }
        dock.setPosition(8, 8);
        layerPop.setPosition(dock.getX() + layerBtn.getX(), dock.getY() + dock.getHeight() + 6);
        if (invest.root.isVisible() && invest.root.getActions().isEmpty()) {
            invest.root.pack();
            invest.root.setPosition(investX(), investY());
        }
    }

    private Label l(String t, String style) { return new Label(t, skin, style); }

    /** Pencere icerigini kaydirilabilir yapar; toggle() penceresini ekranin %70'i ile sinirlar. */
    private final java.util.Map<Window, ScrollPane> scrolls = new java.util.HashMap<>();

    private ScrollPane scrolled(Window w, Actor content) {
        ScrollPane sp = new ScrollPane(content, skin);
        sp.setFadeScrollBars(false);
        sp.setOverscroll(false, false);
        sp.setScrollingDisabled(true, false);
        scrolls.put(w, sp);
        return sp;
    }

    /** Pencere yuksekligi en fazla ekranin %70'i: asarsa icerik kaydirilir. */
    private void limitHeight(Window w) {
        ScrollPane sp = scrolls.get(w);
        if (sp == null) return;
        Cell<?> c = w.getCell(sp);
        if (c == null) return;
        c.height(Value.prefHeight).width(Value.prefWidth);
        w.invalidate();
        w.pack();
        float max = stage.getHeight() * 0.70f;
        if (w.getHeight() > max) {
            // pencere cercevesi (baslik, kenar bosluklari) disinda kalan alan kaydirma alanina verilir
            float chrome = w.getHeight() - sp.getHeight();
            c.height(Math.max(120, max - chrome)).width(sp.getPrefWidth() + 14);
            w.invalidate();   // hucre boyutu degisti: tablo onbellekteki olculeri yeniden hesaplasin
            w.pack();
        }
    }

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
        if (finance.isVisible() && System.currentTimeMillis() >= financeRefreshAt) {
            financeRefreshAt = System.currentTimeMillis() + 300;
            float h0 = finance.getHeight();
            financeView.update(s);
            finance.invalidateHierarchy();
            limitHeight(finance);
            finance.setPosition(Math.max(0, Math.min(finance.getX(), stage.getWidth() - finance.getWidth())), Math.max(0, finance.getY() + h0 - finance.getHeight()));
        }
        if (wizardWin.isVisible()) wizard.refresh();
        if (dealWin.isVisible()) deal.refresh();
        if (defenseWin.isVisible()) defense.refresh();
        if (market.isVisible()) marketView.update(s);
        if (companies.isVisible()) companiesView.update(s);
        if (assetsWin.isVisible() && System.currentTimeMillis() >= assetsRefreshAt) {
            assetsRefreshAt = System.currentTimeMillis() + 400;
            assets.setMaxHeight(stage.getHeight() * 0.70f - 170);
            float h0 = assetsWin.getHeight(), w0 = assetsWin.getWidth();
            assets.update(s);
            assetsWin.pack();
            // ust kenar sabit kalsin; pencere ekrandan tasmasin
            assetsWin.setPosition(Math.max(0, Math.min(assetsWin.getX(), stage.getWidth() - assetsWin.getWidth())),
                Math.max(0, assetsWin.getY() + h0 - assetsWin.getHeight()));
            if (w0 == 0) center(assetsWin);
        }
        if (tenderWin != null && tenderWin.isVisible() && (s.hour % 24 == 0)) refreshTender();
    }

    public void toggle(String name) {
        if (name.equals("settings")) { showSettings(); return; }
        Window w = switch (name) { case "finance" -> finance; case "market" -> market; case "companies" -> companies; case "help" -> help; case "operator", "assets" -> assetsWin; default -> menu; };
        if (name.equals("operator")) { assets.setTab(AssetsView.Tab.OPERATOR); if (assetsWin.isVisible()) return; }
        if (w == assetsWin) assetsRefreshAt = 0;
        boolean show = !w.isVisible();
        w.setVisible(show);
        if (show) {
            // Once icerik doldurulur, sonra boyutlandirilir: bos metinle paketlenip sonradan tasmasin.
            refreshWindows(st);
            w.toFront(); w.pack(); limitHeight(w); center(w);
        }
    }

    /** Hat cekerken ikinci uca tiklaninca: iki secenek yan yana. */
    public void showLineChoice(String title, String own, boolean ownOk, String op, boolean opOk) {
        lcTitle.setText(title); lcOwn.setText(own); lcOp.setText(op);
        lcOwnBtn.setDisabled(!ownOk); lcOpBtn.setDisabled(!opOk);
        lineChoice.pack(); center(lineChoice);
        lineChoice.setVisible(true); lineChoice.toFront();
    }
    public void hideLineChoice() { lineChoice.setVisible(false); }
    public boolean lineChoiceOpen() { return lineChoice.isVisible(); }

    /** Varliklarim penceresini belirli sekmede acar. */
    public void openAssets(AssetsView.Tab tab) {
        assets.setTab(tab);
        assetsRefreshAt = 0;
        if (!assetsWin.isVisible()) toggle("assets");
    }

    public boolean anyWindowOpen() {
        return finance.isVisible() || market.isVisible() || companies.isVisible() || help.isVisible() || menu.isVisible() || equipWin.isVisible()
            || lineChoice.isVisible() || assetsWin.isVisible() || wizardWin.isVisible() || dealWin.isVisible() || defenseWin.isVisible() || confirmWin.isVisible()
            || (settingsWin != null && settingsWin.isVisible());
    }
    public void closeWindows() {
        finance.setVisible(false); market.setVisible(false); companies.setVisible(false); help.setVisible(false);
        menu.setVisible(false); equipWin.setVisible(false); lineChoice.setVisible(false); assetsWin.setVisible(false); wizardWin.setVisible(false);
        dealWin.setVisible(false); defenseWin.setVisible(false); confirmWin.setVisible(false);
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
        t.setTouchable(Touchable.disabled);
        // ekrandan genis metin kirpilmaz, satir kayar
        float maxW = Math.max(300, stage.getWidth() - 120);
        if (lb.getPrefWidth() > maxW) { lb.setWrap(true); t.add(lb).width(maxW).pad(4, 16, 4, 16); }
        else t.add(lb).pad(4, 16, 4, 16);
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
        fitTopBar();
        for (Actor a : stage.getRoot().getChildren()) if (a instanceof Table t) t.invalidateHierarchy();
    }

    /** Ust cubuk ekrana sigmazsa once sirket adi, sonra borc cipi gizlenir (olculen genislige gore; kirpma yok). */
    @SuppressWarnings("unchecked")
    private void fitTopBar() {
        float w = stage.getWidth();
        companyCell.setActor(company); debtCell.setActor(debtChip); company.setVisible(true);
        top.invalidate();
        if (top.getPrefWidth() > w) { companyCell.setActor(null); company.setVisible(false); top.invalidate(); }
        if (top.getPrefWidth() > w) { debtCell.setActor(null); top.invalidate(); }
    }

    private String lastCompanyText = "";
    private int fitCounter;

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
        company.setText(p.name); // kirpilmaz; sigmazsa fitTopBar() tamamen gizler
        // metinler (nakit, tarih...) uzayip kisaldikca cubuk genisligi degisir: periyodik olarak yeniden sigdir
        if (!p.name.equals(lastCompanyText) || (fitCounter++ % 20) == 0) { lastCompanyText = p.name; fitTopBar(); }
        cash.setText(Fmt.money(p.cash));
        Label.LabelStyle cs = skin.get(p.cash < 0 ? "valueBad" : "value", Label.LabelStyle.class);
        if (cash.getStyle() != cs) cash.setStyle(cs);
        debt.setText(Fmt.money(p.debt()));
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
        assetsBtn.setChecked(assetsWin.isVisible());
        for (Map.Entry<Layer, TextButton> e : layerBtns.entrySet()) e.getValue().setChecked(e.getKey() == layer);
        ((Label) layerTip.getActor()).setText("Harita katmanı (L) · Aktif: " + layer.label);
        if (investOpen()) invest.update();
        layoutOverlays();
        boolean ctx = tool != Tool.SELECT;
        ctxBar.setBackground(ctx ? skin.getDrawable("card") : null);
        ctxTitle.setText(contextText(tool, eq, tier));
        wrapToScreen(ctxTitle, ctxTitleCell);
        wrapToScreen(placeInfo, placeInfoCell);
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

    /** xy: ekran koordinatlari (sol-alt), n adet. */
    public void setBadges(float[] xy, int n) {
        if (badgeStyle == null) {
            badgeStyle = new Label.LabelStyle(skin.get("small", Label.LabelStyle.class));
            badgeStyle.fontColor = Color.WHITE;
            badgeStyle.background = skin.newDrawable("white", new Color((GridBalance.NEUTRAL_OUTLINE << 8) | 0xFF));
        }
        while (badges.getChildren().size < n) {
            Label l = new Label("K", badgeStyle);
            l.setAlignment(Align.center);
            l.setSize(17, 17);
            badges.addActor(l);
        }
        for (int i = 0; i < badges.getChildren().size; i++) {
            Actor a = badges.getChildren().get(i);
            // ekran kenarindan tasan rozet yarim gorunmesin: tamami sigmiyorsa gizlenir
            boolean on = i < n && xy[i * 2] - 8.5f >= 0 && xy[i * 2] + 8.5f <= stage.getWidth() && xy[i * 2 + 1] - 8.5f >= 0 && xy[i * 2 + 1] + 8.5f <= stage.getHeight();
            a.setVisible(on);
            if (on) a.setPosition(xy[i * 2] - 8.5f, xy[i * 2 + 1] - 8.5f);
        }
    }

    /** Dogrulama: gorunen etiketlerde kirpma/tasma taramasi. Bulunan her sorun icin bir satir. */
    public List<String> devClipScan() {
        stage.act(0);
        List<String> out = new ArrayList<>();
        scan(stage.getRoot(), out);
        for (Actor a : stage.getActors()) if (a instanceof Window w && w.isVisible()) {
            if (w.getX() < -1 || w.getY() < -1 || w.getX() + w.getWidth() > stage.getWidth() + 1 || w.getY() + w.getHeight() > stage.getHeight() + 1)
                out.add("Pencere ekran dışına taşıyor: " + w.getTitleLabel().getText() + " (" + Math.round(w.getWidth()) + "×" + Math.round(w.getHeight()) + ")");
        }
        return out;
    }

    private void scan(Actor a, List<String> out) {
        if (!a.isVisible() || a.getColor().a < 0.05f) return;
        if (a instanceof Label l && l.getText().length() > 0) {
            boolean inScroll = false;
            for (Actor p = l.getParent(); p != null; p = p.getParent()) if (p instanceof ScrollPane) { inScroll = true; break; }
            String t = l.getText().toString().replace('\n', ' ');
            if (t.length() > 60) t = t.substring(0, 60) + "…";
            if (!l.getWrap() && l.getPrefWidth() > l.getWidth() + 1.5f) out.add("Genişlik: \"" + t + "\" (" + Math.round(l.getPrefWidth()) + " > " + Math.round(l.getWidth()) + ")");
            else if (l.getWrap() && l.getPrefHeight() > l.getHeight() + 1.5f && l.getHeight() > 0) out.add("Yükseklik: \"" + t + "\" (" + Math.round(l.getPrefHeight()) + " > " + Math.round(l.getHeight()) + ")");
            if (!inScroll && l.getWidth() > 0) {
                com.badlogic.gdx.math.Vector2 v = l.localToStageCoordinates(new com.badlogic.gdx.math.Vector2(0, 0));
                if (v.x < -1 || v.x + Math.min(l.getWidth(), l.getPrefWidth()) > stage.getWidth() + 1)
                    out.add("Ekran dışı: \"" + t + "\" (x " + Math.round(v.x) + ", genişlik " + Math.round(l.getWidth()) + ", ekran " + Math.round(stage.getWidth())
                        + (l == company ? ", üst çubuk " + Math.round(top.getPrefWidth()) : "") + ")");
            }
        }
        if (a instanceof com.badlogic.gdx.scenes.scene2d.Group g) for (Actor c : g.getChildren()) scan(c, out);
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
