package com.energytycoon.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.energytycoon.sim.*;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Finans merkezi: Ozet (gelir tablosu, bilanco, nakit akisi), Odeme Takvimi (12 ay), Krediler (not, krediler, bankalar,
 * eylemler), Piyasa ve Faiz (politika faizi, beklenti, olaylar, endeks). Butun metinler sarilir; uzun tablolar kaydirilir.
 */
public final class FinanceCenter {
    private static final String[] TAB_ICONS = { "fin_summary", "fin_schedule", "fin_loans", "fin_market", "fin_capital", "fin_mna" };
    public enum Tab { SUMMARY("Özet"), SCHEDULE("Ödeme Takvimi"), LOANS("Krediler"), MARKET("Piyasa ve Faiz"), CAPITAL("Sermaye"), MNA("Birleşme ve Satın Alma");
        final String label; Tab(String l) { label = l; } }

    public final Table root = new Table();
    private final Skin skin;
    private final Consumer<String> action;
    private GameState st;
    private Tab tab = Tab.SUMMARY;
    private final Table tabs = new Table();
    private final Table summary = new Table(), schedule = new Table(), loans = new Table(), market = new Table();
    private final Cell<Table> bodyCell;
    public final CapitalTab capitalTab;
    public final MnaTab mnaTab;
    public final LineChart valueChart, policyChart, indexChart;
    public final ScheduleChart scheduleChart;
    private String loansKey = "";
    private boolean settingTab;

    // ozet
    private final Label kCash, kValue, kRating, kNet, kNetSub, kRatingSub;
    private static final String[] PL_ROWS = { "Elektrik satışı", "Kapasite ödemesi", "Erişim geliri", "Yakıt", "İşletme ve bakım", "Karbon vergisi", "Faiz",
        "Finansman masrafları", "Kesinti cezası", "Erişim gideri", "Bağlantı bedeli (UŞİ)", "Temettü geliri", "İştirak kâr payı (nakitsiz)", "", "Net kâr" };
    private static final int NET = 14, DIV = 11, AFF = 12;
    private final Label[][] pl = new Label[PL_ROWS.length][2];
    private final Label invest;
    private final Label assets, cashB, debtB, equity, cfOp, cfDebt, cfCapex, cfNet, dayNote;
    // takvim
    private final Label scheduleNote;
    private final Table scheduleTable = new Table();
    // piyasa
    private final Label regime, events, index;

    public FinanceCenter(Skins skins, Consumer<String> action) {
        this.skin = skins.skin;
        this.action = action;
        capitalTab = new CapitalTab(skins, action);
        mnaTab = new MnaTab(skins, action);
        root.defaults().left();
        ButtonGroup<com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton> g = new ButtonGroup<>();
        settingTab = true;
        for (Tab t : Tab.values()) {
            com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton b = UiKit.iconButton(skin, t.label, TAB_ICONS[t.ordinal()], "small", 20, 21);
            b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { if (b.isChecked() && !settingTab) setTab(t); } });
            g.add(b);
            tabs.add(b).height(30).padRight(3);
        }
        settingTab = false;
        root.add(tabs).padBottom(8).row();
        bodyCell = root.add(summary).left().top();

        // ---- Ozet
        kCash = new Label("", skin, "kpi"); kValue = new Label("", skin, "kpi"); kRating = new Label("", skin, "kpi"); kNet = new Label("", skin, "kpi");
        kNetSub = new Label("", skin, "dim"); kRatingSub = new Label("", skin, "dim");
        kNetSub.setWrap(true); kRatingSub.setWrap(true);
        Table kpis = new Table();
        kpis.add(UiKit.card(skin, "Nakit", kCash, null)).width(150).padRight(6);
        kpis.add(UiKit.card(skin, "Şirket değeri", kValue, null)).width(150).padRight(6);
        kpis.add(UiKit.card(skin, "Kredi notu", kRating, kRatingSub)).width(150).padRight(6);
        kpis.add(UiKit.card(skin, "Geçen ay net kâr", kNet, kNetSub)).width(160);
        summary.add(kpis).left().padBottom(10).row();
        Table inc = new Table();
        inc.defaults().padBottom(2);
        inc.add(UiKit.section(skin, "GELİR TABLOSU")).width(170).left();
        inc.add(dim("Geçen ay")).width(90); inc.add(dim("Bu ay*")).width(90).row();
        for (int r = 0; r < PL_ROWS.length; r++) {
            if (PL_ROWS[r].isEmpty()) { inc.add(new Image(skin.newDrawable("white", 0.35f, 0.42f, 0.48f, 1f))).colspan(3).height(1).growX().padTop(2).padBottom(4).row(); continue; }
            Label c = new Label(PL_ROWS[r], skin, r == NET ? "title" : "small"); c.setWrap(true);
            inc.add(c).width(170).left();
            for (int k = 0; k < 2; k++) { pl[r][k] = UiKit.money(skin); inc.add(pl[r][k]).width(90).right(); }
            inc.row();
        }
        dayNote = new Label("", skin, "dim"); dayNote.setWrap(true);
        inc.add(dayNote).colspan(3).width(350).left().padTop(4);
        Table bal = new Table();
        bal.defaults().left().padBottom(2);
        bal.add(UiKit.section(skin, "BİLANÇO")).colspan(2).row();
        assets = UiKit.money(skin); cashB = UiKit.money(skin); debtB = UiKit.money(skin); equity = UiKit.money(skin);
        invest = UiKit.money(skin);
        row(bal, "Tesisler ve hatlar (defter)", assets); row(bal, "Finansal yatırımlar (paylar)", invest); row(bal, "Nakit", cashB); row(bal, "Borç (krediler)", debtB); row(bal, "Özsermaye", equity);
        bal.add(UiKit.section(skin, "NAKİT AKIŞI (geçen ay)")).colspan(2).padTop(10).row();
        cfOp = UiKit.money(skin); cfDebt = UiKit.money(skin); cfCapex = UiKit.money(skin); cfNet = UiKit.money(skin);
        row(bal, "Faaliyet nakdi (FAVÖK)", cfOp); row(bal, "Kredi ödemeleri", cfDebt); row(bal, "Yatırım harcaması", cfCapex); row(bal, "Toplam", cfNet);
        Table mid = new Table();
        mid.add(inc).top().padRight(22);
        mid.add(bal).top().width(250);
        summary.add(mid).left().padBottom(8).row();
        valueChart = new LineChart("Piyasa değeri ve nakit (aylık)", LineChart.Unit.MONEY, skins.small, 640, 140, false)
            .add("Değer", Skins.ACCENT, () -> arr(st.player.valuationHistory))
            .add("Nakit", Skins.WARN, () -> arr(st.player.cashHistory))
            .empty("Grafik en az iki ay kapandığında oluşur.");
        summary.add(valueChart).size(640, 140);

        // ---- Odeme takvimi
        scheduleChart = new ScheduleChart(skins.small, 640, 200);
        schedule.add(scheduleChart).size(640, 200).padBottom(6).row();
        scheduleNote = new Label("", skin, "default"); scheduleNote.setWrap(true);
        schedule.add(scheduleNote).width(640).left().padBottom(6).row();
        schedule.add(scheduleTable).left();

        // ---- Piyasa ve faiz
        policyChart = new LineChart("Politika faizi: son 5 yıl ve 12 aylık piyasa beklentisi", LineChart.Unit.PERCENT, skins.small, 640, 170, false)
            .add("Politika faizi", Skins.WARN, () -> policySeries(true))
            .add("Beklenti", new Color(0.55f, 0.8f, 1f, 1), () -> policySeries(false))
            .xLabels("5 yıl önce", "12 ay sonra");
        indexChart = new LineChart("Hisse endeksi (gösterge)", LineChart.Unit.PLAIN, skins.small, 640, 110, false)
            .add("Endeks", Skins.ACCENT, () -> arr(st.macro.indexHistory, 60));
        regime = new Label("", skin, "default"); regime.setWrap(true);
        events = new Label("", skin, "small"); events.setWrap(true);
        index = new Label("", skin, "small");
        market.add(policyChart).size(640, 170).padBottom(6).row();
        market.add(regime).width(640).left().padBottom(4).row();
        market.add(UiKit.section(skin, "KÜRESEL OLAYLAR")).left().row();
        market.add(events).width(640).left().padBottom(6).row();
        market.add(indexChart).size(640, 110).padBottom(4).row();
        market.add(index).left();
        setTab(Tab.SUMMARY);
    }

    public void setTab(Tab t) {
        tab = t;
        settingTab = true;
        for (int i = 0; i < tabs.getChildren().size; i++) ((com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton) tabs.getChildren().get(i)).setChecked(Tab.values()[i] == t);
        settingTab = false;
        bodyCell.setActor(switch (t) { case SUMMARY -> summary; case SCHEDULE -> schedule; case LOANS -> loans; case MARKET -> market;
            case CAPITAL -> capitalTab.root; case MNA -> mnaTab.root; });
        capitalTab.invalidate(); mnaTab.invalidate();
        loansKey = "";
        root.invalidateHierarchy();
    }

    private Label dim(String t) { Label l = new Label(t, skin, "dim"); l.setAlignment(Align.right); return l; }

    private void row(Table t, String caption, Label value) {
        Label c = new Label(caption, skin, "small"); c.setWrap(true);
        t.add(c).width(160); t.add(value).width(86).right().row();
    }

    private static double[] arr(List<Double> l) { return arr(l, Integer.MAX_VALUE); }

    private static double[] arr(List<Double> l, int last) {
        int n = Math.min(l.size(), last), off = l.size() - n;
        double[] a = new double[n];
        for (int i = 0; i < n; i++) a[i] = l.get(off + i);
        return a;
    }

    /** Gecmis 60 ay + 12 ay beklenti ayni eksende (gecmis serisinde gelecek bos, beklenti serisinde gecmis bos). */
    private double[] policySeries(boolean history) {
        double[] h = arr(st.macro.rateHistory, 60);
        double[] out = new double[h.length + 12];
        for (int i = 0; i < out.length; i++) out[i] = Double.NaN;
        if (history) System.arraycopy(h, 0, out, 0, h.length);
        else { if (h.length > 0) out[h.length - 1] = h[h.length - 1]; for (int k = 0; k < 12; k++) out[h.length + k] = st.macro.expectedPath[k]; }
        return out;
    }

    static String date(long hour) {
        return GameState.MONTH_NAMES[(int) ((hour / Balance.HOURS_PER_MONTH) % 12)] + " " + (Balance.START_YEAR + hour / Balance.HOURS_PER_YEAR);
    }

    static String pct(double r) { return Macro.pct(r); }

    // ------------------------------------------------------------ guncelleme
    public void update(GameState s) {
        this.st = s;
        switch (tab) {
            case SUMMARY -> updateSummary(s);
            case SCHEDULE -> updateSchedule(s);
            case LOANS -> updateLoans(s);
            case MARKET -> updateMarket(s);
            case CAPITAL -> capitalTab.update(s);
            case MNA -> mnaTab.update(s);
        }
    }

    private void updateSummary(GameState s) {
        Company p = s.player;
        double assetsV = s.assetValue(p);
        kCash.setText(Fmt.money(p.cash)); kCash.setStyle(skin.get(p.cash < 0 ? "kpiBad" : "kpi", Label.LabelStyle.class));
        kValue.setText(Fmt.money(s.capital.value(p)));
        Rating r = s.finance.rating(p);
        kRating.setText(r.name()); kRating.setStyle(skin.get(r.ordinal() <= 1 ? "kpiGood" : r.ordinal() >= 3 ? "kpiBad" : "kpi", Label.LabelStyle.class));
        kRatingSub.setText((p.quarters < 4 ? "yeni şirket · " : "") + "prim +" + Fmt.f1(r.premium() * 100) + " puan");
        boolean anyMonth = !p.netHistory.isEmpty();
        kNet.setText(anyMonth ? Fmt.money(p.lastNet) : "—");
        kNet.setStyle(skin.get(!anyMonth ? "kpi" : p.lastNet >= 0 ? "kpiGood" : "kpiBad", Label.LabelStyle.class));
        kNetSub.setText(anyMonth ? "Son " + p.netHistory.size() + " ay: " + Fmt.money(p.trailingNet()) : "Henüz kapanmış ay yok");
        double[] last = { p.lastRevenue, p.lastCapacityIncome, p.lastAccessIncome, p.lastFuel, p.lastOm, p.lastCarbon, p.lastInterest, p.lastFees, p.lastFines, p.lastAccessCost, p.lastConnectionCharge,
            p.lastDividendIncome, p.lastAffiliateIncome, 0, p.lastNet + p.lastDividendIncome + p.lastAffiliateIncome };
        double[] now = { p.revenue, p.capacityIncome, p.accessIncome, p.fuelCost, p.omCost, p.carbonCost, p.interest, p.fees, p.fines, p.accessCost, p.connectionCharge,
            p.dividendIncome, p.affiliateIncome, 0, p.monthNetSoFar() + p.dividendIncome + p.affiliateIncome };
        for (int i = 0; i < PL_ROWS.length; i++) {
            if (pl[i][0] == null) continue;
            for (int k = 0; k < 2; k++) {
                double v = k == 0 ? last[i] : now[i];
                Label l = pl[i][k];
                if (i == 0) UiKit.setMoney(skin, l, v, false);
                else if (i == 1 || i == 2 || i == DIV || i == AFF) { if (Math.abs(v) < 1) UiKit.setCost(skin, l, 0); else UiKit.setMoney(skin, l, v, true); }
                else if (i == NET) UiKit.setMoney(skin, l, v, true);
                else UiKit.setCost(skin, l, v);
                if (k == 0 && !anyMonth) { l.setText("—"); l.setStyle(skin.get("dimValue", Label.LabelStyle.class)); }
            }
        }
        dayNote.setText("* ayın " + s.dayOfMonth() + ". günü itibarıyla. Faiz her ay tahakkuk eder; nakit çıkışı taksit gününde.");
        UiKit.setMoney(skin, assets, assetsV, false);
        UiKit.setMoney(skin, cashB, p.cash, true);
        debtB.setText(p.debt() > 0 ? "−" + Fmt.money(p.debt()) : "—");
        double inv = s.capital.holdingsValue(p);
        if (inv < 1) { invest.setText("—"); invest.setStyle(skin.get("dimValue", Label.LabelStyle.class)); } else UiKit.setMoney(skin, invest, inv, false);
        UiKit.setMoney(skin, equity, p.cash + assetsV + inv - p.debt(), true);
        double op = p.lastRevenue + p.lastCapacityIncome - p.lastFuel - p.lastOm - p.lastCarbon - p.lastFines + p.lastAccessIncome - p.lastAccessCost - p.lastConnectionCharge;
        UiKit.setMoney(skin, cfOp, op, true);
        UiKit.setCost(skin, cfDebt, p.lastDebtService);
        UiKit.setCost(skin, cfCapex, p.lastCapex);
        UiKit.setMoney(skin, cfNet, op - p.lastDebtService - p.lastCapex - p.lastFees, true);
    }

    private void updateSchedule(GameState s) {
        Company p = s.player;
        double[] svc = s.finance.schedule12(p);
        double[] proj = s.finance.cashProjection(p, svc);
        double op = s.finance.monthlyOperatingCash(p);
        long m0 = (s.hour / Balance.HOURS_PER_MONTH) * Balance.HOURS_PER_MONTH;
        String note = null;
        for (int m = 0; m < 12; m++) {
            scheduleChart.service[m] = svc[m];
            scheduleChart.opCash[m] = Math.max(0, op);
            scheduleChart.gap[m] = proj[m] < 0;
            long h = m0 + (long) m * Balance.HOURS_PER_MONTH;
            scheduleChart.labels[m] = GameState.MONTH_NAMES[(int) ((h / Balance.HOURS_PER_MONTH) % 12)].substring(0, 3);
            if (proj[m] < 0 && note == null) note = date(h) + ": tahmini açık " + Fmt.money(-proj[m]) + " (nakit + işletme kredisi boşluğu dahil)";
        }
        scheduleNote.setText(note != null ? note : "Önümüzdeki 12 ayda nakit açığı öngörülmüyor (tahmini faaliyet nakdi aylık " + Fmt.money(op) + ").");
        scheduleNote.setStyle(skin.get(note != null ? "bad" : "good", Label.LabelStyle.class));
        StringBuilder key = new StringBuilder();
        for (int m = 0; m < 12; m++) key.append(Math.round(svc[m] / 1e5)).append(Math.round(proj[m] / 1e6)).append(',');
        if (key.toString().equals(loansKey)) return;
        loansKey = key.toString();
        scheduleTable.clear();
        scheduleTable.defaults().padBottom(1);
        String[] head = { "Ay", "Taksitler", "Faaliyet nakdi", "Tahmini kullanılabilir nakit" };
        float[] w = { 130, 110, 120, 200 };
        for (int i = 0; i < head.length; i++) scheduleTable.add(dimL(head[i], i == 0 ? Align.left : Align.right)).width(w[i]);
        scheduleTable.row();
        for (int m = 0; m < 12; m++) {
            long h = m0 + (long) m * Balance.HOURS_PER_MONTH;
            String[] c = { date(h), Fmt.money(svc[m]), Fmt.money(op), Fmt.money(proj[m]) };
            for (int i = 0; i < c.length; i++) {
                Label l = new Label(c[i], skin, i == 3 && proj[m] < 0 ? "bad" : "small");
                l.setAlignment(i == 0 ? Align.left : Align.right);
                scheduleTable.add(l).width(w[i]);
            }
            scheduleTable.row();
        }
    }

    private Label dimL(String t, int align) { Label l = new Label(t, skin, "dim"); l.setAlignment(align); l.setWrap(true); return l; }

    private void updateLoans(GameState s) {
        Company p = s.player;
        Finance f = s.finance;
        StringBuilder key = new StringBuilder(f.rating(p).name()).append(Math.round(p.cash / 1e6));
        for (Loan l : p.loans) key.append(l.id).append(l.status).append(Math.round(l.outstanding / 1e5)).append(Math.round(l.effectiveRate() * 1e4)).append(l.nextDueHour).append(l.dueAmount > 0);
        for (int b : p.bankScore) key.append(b).append(',');
        if (key.toString().equals(loansKey)) return;
        loansKey = key.toString();
        loans.clear();
        loans.defaults().left();
        // not ve bilesenleri
        Rating r = f.rating(p);
        double lev = f.leverage(p), cov = f.coverage(p);
        loans.add(UiKit.section(skin, "KREDİ NOTU")).row();
        Label rl = new Label("Not: " + r + (p.quarters < 4 ? " (yeni şirket: ilk 4 çeyrek veri yetersiz)" : "") + (p.lateNotch ? " · gecikme nedeniyle bir kademe düşük" : "")
            + (p.defaultCapUntil > s.hour ? " · temerrüt geçmişi: en fazla B (" + date(p.defaultCapUntil) + "'e kadar)" : "")
            + "\nNet borç / FAVÖK: " + num(lev) + "   (A ≤ 2,0 · BBB ≤ 3,5 · BB ≤ 5,0 · B ≤ 7,0)"
            + "\nFAVÖK / faiz gideri: " + num(cov) + "   (A ≥ 8,0 · BBB ≥ 4,0 · BB ≥ 2,5 · B ≥ 1,5)"
            + "\nNot primi +" + Fmt.f1(r.premium() * 100) + " puan · politika faizi " + pct(s.macro.policyRate)
            + (s.settings.difficulty.rateAdd != 0 ? " · zorluk eki " + (s.settings.difficulty.rateAdd > 0 ? "+" : "") + Fmt.f1(s.settings.difficulty.rateAdd * 100) + " puan" : "")
            + (s.macro.eventSpread() > 0 ? " · olay primi +" + Fmt.f1(s.macro.eventSpread() * 100) + " puan" : "")
            + (f.greenEligible(p) ? " · yeşil indirim −" + Fmt.f1(s.macro.greenDiscount() * 100) + " puan" : "")
            + "\nKredi limiti " + Fmt.money(f.creditLimit(p)) + " (300M$ + varlıkların %50'si; ECA hariç) · kullanılan " + Fmt.money(f.limitUsage(p))
            + (p.dividendBan ? "\nTemettü yasağı (taahhüt ihlali)" : ""), skin, "small");
        rl.setWrap(true);
        loans.add(rl).width(760).padBottom(8).row();
        // krediler
        Table head = new Table();
        loans.add(UiKit.section(skin, "KREDİLER")).row();
        String[] hd = { "Tür · banka", "Bakiye", "Faiz", "Sonraki taksit", "Taahhüt", "Durum", "" };
        float[] w = { 190, 80, 110, 130, 120, 90, 150 };
        for (int i = 0; i < hd.length; i++) head.add(dimL(hd[i], i == 1 ? Align.right : Align.left)).width(w[i]).padRight(6);
        loans.add(head).padBottom(2).row();
        Table rows = new Table();
        boolean any = false;
        for (Loan l : p.loans) {
            if (!l.open() && l.status != Loan.Status.DEFAULTED) continue;
            any = true;
            Table row = new Table(skin);
            row.setBackground(l.status == Loan.Status.LATE ? "rowHover" : "row");
            row.pad(3, 6, 3, 6);
            double[] np = l.open() ? f.nextPayment(l) : new double[] { 0, 0 };
            String cov2 = l.type == Loan.Type.COMMERCIAL ? "ND/FAVÖK ≤ " + num(l.bank.covenantLimit()) + (l.covenantPenalty ? " · İHLAL" : "")
                : l.type == Loan.Type.ECA ? "DSCR ≥ 1,20" + (Double.isNaN(l.lastDscr) ? "" : " (" + num(l.lastDscr) + ")") + (l.dscrPenalty ? " · DÜŞÜK" : "") : "—";
            String[] c = {
                l.type.label + (l.type == Loan.Type.ECA ? " · " + l.ecaOrigin : "") + "\n" + l.bank.label,
                l.type == Loan.Type.REVOLVER ? Fmt.money(l.outstanding) + "\n/ " + Fmt.money(l.limit) : Fmt.money(l.outstanding),
                pct(l.effectiveRate()) + "\n" + (l.fixed ? "sabit" : "değişken"),
                l.type == Loan.Type.REVOLVER ? "aylık faiz " + Fmt.money(np[1]) : date((long) np[0]) + "\n" + Fmt.money(np[1])
                    + (l.nextDueHour < l.graceEndHour ? " (ödemesiz dönem)" : ""),
                cov2, l.status.label + (l.daysLate > 0 ? " (" + l.daysLate + " gün)" : "") };
            for (int i = 0; i < c.length; i++) {
                Label lb = new Label(c[i], skin, i == 5 && l.status == Loan.Status.LATE ? "bad" : "small");
                lb.setWrap(true); lb.setAlignment(i == 1 ? Align.right : Align.left);
                row.add(lb).width(w[i]).padRight(6);
            }
            Table bt = new Table();
            if (l.type == Loan.Type.REVOLVER && l.open()) {
                bt.add(button("Kullan " + Fmt.money(FinanceBalance.LOAN_STEP), "revdraw", f.revolverRoom(p) >= 1e6)).height(26).padRight(3).row();
                bt.add(button("Öde", "revrepay", l.outstanding > 1 && p.cash > 1)).height(26);
            } else if (l.open()) {
                double cost = f.prepayCost(p, l);
                bt.add(button("Erken kapat " + Fmt.money(cost), "prepay:" + l.id, p.cash >= cost)).height(26);
            }
            row.add(bt).width(w[6]).left();
            rows.add(row).left().padBottom(2).row();
        }
        if (!any) rows.add(new Label("Aktif krediniz yok.", skin, "small")).row();
        loans.add(rows).padBottom(6).row();
        Table acts = new Table();
        acts.add(button("Yeni kredi…", "wizard", true)).width(160).height(32).padRight(6);
        if (f.revolver(p) == null) acts.add(button("İşletme kredisi limiti aç (" + Bank.KIYI.label + ")", "revopen", true)).height(32);
        loans.add(acts).padBottom(10).row();
        // bankalar
        loans.add(UiKit.section(skin, "BANKALAR")).row();
        Table bh = new Table();
        String[] bhd = { "Banka", "İlişki", "Kademe", "Bakiye", "Yoğunlaşma limiti", "Profil" };
        float[] bw = { 190, 60, 70, 90, 130, 260 };
        for (int i = 0; i < bhd.length; i++) bh.add(dimL(bhd[i], Align.left)).width(bw[i]).padRight(6);
        loans.add(bh).row();
        double lim = f.creditLimit(p);
        for (Bank b : Bank.values()) {
            Table row = new Table(skin); row.setBackground("row"); row.pad(3, 6, 3, 6);
            double exp = f.bankExposure(p, b), cap = b.concentration() * lim;
            String[] c = { b.label + (p.bankBanUntil[b.ordinal()] > s.hour ? "\n(kredi yok: " + date(p.bankBanUntil[b.ordinal()]) + "'e kadar)" : ""),
                String.valueOf(p.bankScore[b.ordinal()]), Bank.tierLabel(p.bankScore[b.ordinal()]), Fmt.money(exp),
                "%" + Math.round(100 * exp / Math.max(1, cap)) + " (" + Fmt.money(cap) + ")", b.profile };
            for (int i = 0; i < c.length; i++) {
                Label lb = new Label(c[i], skin, "small"); lb.setWrap(true);
                if (i == 0) { lb.setWrap(false); row.add(UiKit.iconLabel(skin, "bank_" + b.name().toLowerCase(java.util.Locale.ROOT), lb, 26, 28)).width(bw[i]).left().padRight(6); }
                else row.add(lb).width(bw[i]).padRight(6);
            }
            loans.add(row).padBottom(2).row();
        }
        Label tiers = new Label("Kademeler: 30+ peşin masrafta %50 indirim · 60+ prim −0,15 puan, peşin masraf yok · 90+ prim −0,30 puan, erken kapama cezası yok. "
            + "Vadesinde ödenen her taksit +1 (kredi başına en fazla +10), gecikmesiz kapanan kredi +10, gecikme −15 / 30 günü aşan −40, temerrüt 0.", skin, "dim");
        tiers.setWrap(true);
        loans.add(tiers).width(760).padTop(4);
    }

    private TextButton button(String text, String act, boolean enabled) {
        TextButton b = new TextButton(text, skin, "small");
        b.setDisabled(!enabled);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { if (!b.isDisabled()) action.accept(act); } });
        return b;
    }

    private void updateMarket(GameState s) {
        Macro m = s.macro;
        int toMeeting = FinanceBalance.MEETING_MONTHS - (s.month() % FinanceBalance.MEETING_MONTHS);
        regime.setText("Politika faizi " + pct(m.policyRate) + " · rejim: " + m.effectiveRegime().label + " (hedef " + pct(m.effectiveRegime().target) + ")"
            + " · sonraki toplantı " + toMeeting + " ay sonra\nPiyasa beklentisi: 12 ay sonra " + pct(m.expectedPath[11])
            + " (" + (m.expectingCuts() ? "indirim bekleniyor" : m.expectedPath[11] > m.policyRate + 0.005 ? "artış bekleniyor" : "yatay") + "; tahmin isabeti yaklaşık %70)");
        StringBuilder ev = new StringBuilder();
        for (Macro.Event e : m.events) {
            ev.append("• ").append(e.type.label).append(e.type == Macro.EventType.ENERGY ? " — gaz şoku sürdükçe" : " — kalan " + e.monthsLeft + " ay");
            ev.append(switch (e.type) { case CRISIS -> " · kredi primleri +3 puan, gevşeme"; case INFLATION -> " · sıkılaşma, adımlar en az 0,50";
                case COUNTRY -> " · kredi primleri +2 puan, ECA primi ×2"; case GREEN_WAVE -> " · yeşil indirim −1,0 puan"; case ENERGY -> " · faiz +0,5–1 puan"; }).append('\n');
        }
        events.setText(ev.length() == 0 ? "Aktif küresel olay yok." : ev.toString().trim());
        index.setText(String.format(Locale.ROOT, "Hisse endeksi: %.1f", m.equityIndex).replace('.', ','));
    }

    static String num(double v) { return Double.isInfinite(v) ? "∞" : Double.isNaN(v) ? "—" : String.format(Locale.ROOT, "%.1f", v).replace('.', ','); }
}
