package com.energytycoon.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.energytycoon.sim.Balance;
import com.energytycoon.sim.Company;
import com.energytycoon.sim.GameState;

import java.util.List;
import java.util.Locale;

/**
 * Finans penceresi: KPI kartlari, iki sutunlu gelir tablosu (gecen ay / bu ay), kredi ve bilanco,
 * piyasa degeri/nakit grafigi. Tum sayilar sabit genislikli sutunlarda, giderler kirmizimsi, net renkli.
 */
public final class FinanceView {
    public final Table root = new Table();
    private final Skin skin;
    private GameState st;

    private final Label kCash, kValue, kStock, kNet, kNetSub, kStockSub;
    private final Label[][] pl = new Label[10][2];          // satir x (gecen ay, bu ay)
    private final Label genLast, genNow, avgLast, avgNow, dayNote;
    private final Label debt, limit, rate, monthlyInterest, creditNote;
    private final Label assets, cashB, debtB, equity;
    private final UiKit.Meter creditMeter;
    private final TextButton borrowBtn, repayBtn;
    public final LineChart chart;

    private static final String[] PL_ROWS = { "Elektrik satışı", "Erişim geliri", "Yakıt", "İşletme ve bakım", "Karbon vergisi", "Faiz", "Kesinti cezası", "Erişim gideri", "", "Net kâr" };
    private static final int NET = 9;

    public FinanceView(Skins skins, Runnable onBorrow, Runnable onRepay) {
        this.skin = skins.skin;
        root.defaults().left();

        // ---- KPI kartlari
        kCash = new Label("", skin, "kpi"); kValue = new Label("", skin, "kpi"); kStock = new Label("", skin, "kpi"); kNet = new Label("", skin, "kpi");
        kNetSub = new Label("", skin, "dim"); kStockSub = new Label("", skin, "dim");
        Table kpis = new Table();
        kpis.add(UiKit.card(skin, "Nakit", kCash, null)).width(150).padRight(6);
        kpis.add(UiKit.card(skin, "Piyasa değeri", kValue, null)).width(150).padRight(6);
        kpis.add(UiKit.card(skin, "Hisse fiyatı", kStock, kStockSub)).width(150).padRight(6);
        kpis.add(UiKit.card(skin, "Geçen ay net kâr", kNet, kNetSub)).width(150);
        root.add(kpis).padBottom(10).row();

        // ---- gelir tablosu
        Table inc = new Table();
        inc.defaults().padBottom(3);
        inc.add(UiKit.section(skin, "GELİR TABLOSU")).width(150).left();
        inc.add(header("Geçen ay")).width(90);
        inc.add(header("Bu ay*")).width(90).row();
        for (int r = 0; r < PL_ROWS.length; r++) {
            if (PL_ROWS[r].isEmpty()) {
                Image line = new Image(skin.newDrawable("white", 0.35f, 0.42f, 0.48f, 1f));
                inc.add(line).colspan(3).height(1).growX().padTop(2).padBottom(4).row();
                continue;
            }
            inc.add(new Label(PL_ROWS[r], skin, r == NET ? "title" : "default")).left();
            for (int c = 0; c < 2; c++) { pl[r][c] = UiKit.money(skin); inc.add(pl[r][c]).width(90).right(); }
            inc.row();
        }
        genLast = UiKit.money(skin); genNow = UiKit.money(skin); avgLast = UiKit.money(skin); avgNow = UiKit.money(skin);
        inc.add(new Label("Satılan elektrik", skin, "small")).left().padTop(8);
        inc.add(genLast).right().padTop(8); inc.add(genNow).right().padTop(8).row();
        inc.add(new Label("Ortalama satış fiyatı", skin, "small")).left();
        inc.add(avgLast).right(); inc.add(avgNow).right().row();
        dayNote = new Label("", skin, "dim");
        inc.add(dayNote).colspan(3).left().padTop(4);

        // ---- kredi ve bilanco
        Table cr = new Table();
        cr.defaults().left().padBottom(3);
        cr.add(UiKit.section(skin, "KREDİ")).colspan(2).row();
        debt = UiKit.money(skin); limit = UiKit.money(skin); rate = UiKit.money(skin); monthlyInterest = UiKit.money(skin);
        row(cr, "Borç", debt);
        row(cr, "Kredi limiti", limit);
        creditMeter = new UiKit.Meter(skin, 230, 8);
        cr.add(creditMeter).colspan(2).width(230).height(8).padTop(2).padBottom(6).row();
        row(cr, "Faiz oranı (yıllık)", rate);
        row(cr, "Aylık faiz yükü", monthlyInterest);
        borrowBtn = new TextButton("Borçlan +" + Fmt.money(Balance.LOAN_STEP), skin, "accent");
        repayBtn = new TextButton("Öde −" + Fmt.money(Balance.LOAN_STEP), skin, "default");
        borrowBtn.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { onBorrow.run(); } });
        repayBtn.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { onRepay.run(); } });
        Table btns = new Table();
        btns.add(borrowBtn).width(136).height(32).padRight(4);
        btns.add(repayBtn).width(92).height(32);
        cr.add(btns).colspan(2).padTop(6).row();
        creditNote = new Label("", skin, "dim");
        creditNote.setWrap(true);
        cr.add(creditNote).colspan(2).width(230).row();

        cr.add(UiKit.section(skin, "BİLANÇO")).colspan(2).padTop(10).row();
        assets = UiKit.money(skin); cashB = UiKit.money(skin); debtB = UiKit.money(skin); equity = UiKit.money(skin);
        row(cr, "Tesisler ve hatlar (defter)", assets);
        row(cr, "Nakit", cashB);
        row(cr, "Borç", debtB);
        row(cr, "Özsermaye", equity);

        Table mid = new Table();
        mid.add(inc).top().padRight(22);
        mid.add(cr).top().width(232);
        root.add(mid).padBottom(10).row();

        chart = new LineChart("Piyasa değeri ve nakit (aylık)", LineChart.Unit.MONEY, skins.small, 618, 150, false)
            .add("Değer", Skins.ACCENT, () -> arr(st.player.valuationHistory))
            .add("Nakit", Skins.WARN, () -> arr(st.player.cashHistory))
            .empty("Grafik en az iki ay kapandığında oluşur.");
        root.add(chart).width(618).height(150);
    }

    private Label header(String t) {
        Label l = new Label(t, skin, "dim");
        l.setAlignment(Align.right);
        return l;
    }

    private void row(Table t, String caption, Label value) {
        t.add(new Label(caption, skin, "small")).width(150);
        t.add(value).width(80).right().row();
    }

    private static double[] arr(List<Double> l) {
        double[] a = new double[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    public void update(GameState s) {
        this.st = s;
        Company p = s.player;
        double assetsV = s.assetValue(p), limitV = s.creditLimit(p), rateV = s.interestRate(p);
        kCash.setText(Fmt.money(p.cash)); kCash.setStyle(skin.get(p.cash < 0 ? "kpiBad" : "kpi", Label.LabelStyle.class));
        kValue.setText(Fmt.money(p.valuation));
        kStock.setText("$" + Fmt.f1(p.stockPrice));
        kStockSub.setText(String.format(Locale.ROOT, "%.0f milyon hisse", Balance.SHARES / 1e6));
        boolean anyMonth = !p.netHistory.isEmpty();
        kNet.setText(anyMonth ? Fmt.money(p.lastNet) : "—");
        kNet.setStyle(skin.get(!anyMonth ? "kpi" : p.lastNet >= 0 ? "kpiGood" : "kpiBad", Label.LabelStyle.class));
        double yearNet = p.trailingNet();
        kNetSub.setText(anyMonth ? "Son " + p.netHistory.size() + " ay: " + Fmt.money(yearNet) : "Henüz kapanmış ay yok");

        // gelir tablosu: [gelir, erisim geliri, yakit, isletme, karbon, faiz, ceza, erisim gideri, -, net]
        double[] last = { p.lastRevenue, p.lastAccessIncome, p.lastFuel, p.lastOm, p.lastCarbon, p.lastInterest, p.lastFines, p.lastAccessCost, 0, p.lastNet };
        double[] now = { p.revenue, p.accessIncome, p.fuelCost, p.omCost, p.carbonCost, p.interest, p.fines, p.accessCost, 0, p.monthNetSoFar() };
        for (int r = 0; r < PL_ROWS.length; r++) {
            if (pl[r][0] == null) continue;
            for (int c = 0; c < 2; c++) {
                double v = c == 0 ? last[r] : now[r];
                Label l = pl[r][c];
                if (r == 0) UiKit.setMoney(skin, l, v, false);
                else if (r == 1) { if (v < 1) UiKit.setCost(skin, l, 0); else UiKit.setMoney(skin, l, v, true); }
                else if (r == NET) UiKit.setMoney(skin, l, v, true);
                else UiKit.setCost(skin, l, v);
                if (c == 0 && !anyMonth) { l.setText("—"); l.setStyle(skin.get("dimValue", Label.LabelStyle.class)); }
            }
        }
        genLast.setText(anyMonth ? gwh(p.lastMwhGenerated) : "—");
        genNow.setText(gwh(p.mwhGenerated));
        avgLast.setText(anyMonth && p.lastMwhGenerated > 0 ? price(p.lastRevenue / p.lastMwhGenerated) : "—");
        avgNow.setText(p.mwhGenerated > 0 ? price(p.revenue / p.mwhGenerated) : "—");
        dayNote.setText("* ayın " + s.dayOfMonth() + ". günü itibarıyla (ay " + Balance.DAYS_PER_MONTH + " gün)");

        // kredi
        UiKit.setMoney(skin, debt, p.debt, false);
        UiKit.setMoney(skin, limit, limitV, false);
        float use = (float) (limitV > 0 ? p.debt / limitV : 0);
        creditMeter.set(use, use < 0.6f ? Skins.ACCENT : use < 0.9f ? Skins.WARN : Skins.BAD);
        rate.setText(String.format(Locale.ROOT, "%%%.1f", rateV * 100));
        monthlyInterest.setText(p.debt > 0 ? "−" + Fmt.money(p.debt * rateV / 12) : "—");
        boolean canBorrow = p.debt + Balance.LOAN_STEP <= limitV;
        double repayAmt = Math.min(Balance.LOAN_STEP, p.debt);
        boolean canRepay = repayAmt > 0 && p.cash >= repayAmt;
        borrowBtn.setDisabled(!canBorrow);
        repayBtn.setDisabled(!canRepay);
        String note;
        if (!canBorrow) note = "Kredi limiti dolu. Limit, tesis ve hatlarınızın defter değeriyle büyür.";
        else if (p.debt <= 0) note = "Borcunuz yok. Borçlanınca faiz oranı kaldıraca göre artar.";
        else if (!canRepay) note = "Geri ödeme için yeterli nakit yok.";
        else note = "Kullanılan kredi: %" + Math.round(use * 100) + ". Faiz her ay sonunda ödenir.";
        creditNote.setText(note);

        UiKit.setMoney(skin, assets, assetsV, false);
        UiKit.setMoney(skin, cashB, p.cash, true);
        debtB.setText(p.debt > 0 ? "−" + Fmt.money(p.debt) : "—");
        UiKit.setMoney(skin, equity, p.cash + assetsV - p.debt, true);
    }

    private static String gwh(double mwh) { return String.format(Locale.ROOT, "%.1f GWh", mwh / 1000); }
    private static String price(double v) { return "$" + Fmt.f1(v) + "/MWh"; }
}
