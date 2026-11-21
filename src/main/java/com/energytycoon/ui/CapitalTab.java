package com.energytycoon.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.energytycoon.sim.*;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Sermaye sekmesi: ortaklik tablosu, hisse fiyati grafigi, halka arz sihirbazi (oran girisi, anlik kurucu payi ve hasilat),
 * bedelli sermaye artirimi, geri alim, temettu politikasi, kontrol esigi uyarisi. Metinler sarilir.
 */
public final class CapitalTab {
    public final Table root = new Table();
    public final LineChart priceChart;
    private final Skin skin;
    private final Consumer<String> action;
    private final Table body = new Table();
    private GameState st;
    private String key = "";
    private double ipoFraction = 0.25, issueFraction = 0.10, buyback = 100e6;

    public CapitalTab(Skins skins, Consumer<String> action) {
        this.skin = skins.skin; this.action = action;
        priceChart = new LineChart("Hisse fiyatı (aylık; halka kapalıyken temel değer / hisse)", LineChart.Unit.PLAIN, skins.small, 640, 120, false)
            .add("Fiyat", Skins.ACCENT, () -> st == null ? new double[0] : st.player.eq.priceMonthly)
            .empty("Grafik en az iki ay kapandığında oluşur.");
        root.defaults().left();
        root.add(body).left().row();
        root.add(priceChart).size(640, 120).padTop(6);
    }

    public void invalidate() { key = ""; }

    public void update(GameState s) {
        st = s;
        Company p = s.player;
        Equity e = p.eq;
        String k = e.listed + "|" + Math.round(e.price * 100) + "|" + Math.round(e.shares) + "|" + Math.round(e.founder) + "|" + e.ipoReadyHour + "|" + e.ipoCrisisPending
            + "|" + e.dividendPct + "|" + Math.round(e.buybackBudget / 1e6) + "|" + ipoFraction + "|" + issueFraction + "|" + buyback + "|" + Math.round(p.cash / 1e6)
            + "|" + s.capital.ipoBlocker(p) + "|" + s.capital.issueBlocker(p) + "|" + (s.hour / Balance.HOURS_PER_MONTH);
        for (double h : e.held) k += "," + Math.round(h);
        if (k.equals(key)) return;
        key = k;
        body.clear();
        body.defaults().left();
        // durum ve kontrol uyarisi
        double fp = e.founderPct();
        Label head = new Label((e.listed ? "Halka açık · hisse fiyatı " + px(e.price) + " · piyasa değeri " + Fmt.money(e.price * e.shares)
                : "Halka kapalı · temel değer " + Fmt.money(e.fundamental) + " (" + px(e.fundamental / e.shares) + " / hisse)")
            + "\nTemel değer = özsermaye " + Fmt.money(s.capital.bookEquity(p)) + " + F/K " + FinanceCenter.num(s.capital.multiple(p)) + " × normalleştirilmiş yıllık kâr "
            + Fmt.money(s.capital.normalizedProfit(p)) + " (F/K politika faizine bağlı; not ve büyüme ±%20)", skin, "small");
        head.setWrap(true);
        body.add(head).width(760).padBottom(6).row();
        if (fp < FinanceBalance.CONTROL) {
            Label w = new Label("UYARI: Kurucu payınız %" + p1(fp) + " — %50'nin altında. Şirketiniz dağınık ortaklı: rakipler pay alım teklifiyle (çağrı) sizi devralmaya çalışabilir. "
                + "Geri alımla payınızı yükseltebilirsiniz.", skin, "bad");
            w.setWrap(true);
            body.add(w).width(760).padBottom(6).row();
        }
        // ortaklik tablosu
        body.add(UiKit.section(skin, "ORTAKLIK TABLOSU")).row();
        Table t = new Table();
        t.defaults().padBottom(1);
        t.add(dim("Ortak")).width(260); t.add(dim("Hisse")).width(110); t.add(dim("Pay")).width(80).row();
        row(t, "Kurucu (şirketiniz)", e.founder, e.shares);
        for (int j = 0; j < s.companies.size(); j++) if (e.held(j) > 0) row(t, s.companies.get(j).name, e.held(j), e.shares);
        row(t, "Serbest dolaşım (halka açık kısım)", e.free(), e.shares);
        t.add(new Label("Toplam", skin, "default")).width(260).left(); t.add(right(shares(e.shares))).width(110); t.add(right("%100")).width(80).row();
        body.add(t).padBottom(8).row();
        // halka arz
        if (!e.listed) ipo(s, p);
        else listedOps(s, p);
        // temettu
        body.add(UiKit.section(skin, "TEMETTÜ POLİTİKASI (kârın oranı, çeyrek sonunda)")).padTop(8).row();
        Table dv = new Table();
        for (int pct : FinanceBalance.DIVIDEND_OPTIONS) dv.add(toggle("%" + pct, e.dividendPct == pct, "div:" + pct)).height(28).padRight(4);
        String db = s.capital.dividendBlocker(p);
        Label dn = new Label((db != null ? db + ". " : "") + "Kurucu payınıza düşen kısım şirkette kalır; diğer ortaklara düşen kısım şirketten çıkar. Yüksek temettü piyasa duyarlılığını hafifçe destekler."
            + (e.lastDividend > 0 ? " Son çeyrek: " + Fmt.money(e.lastDividend) + " dağıtıldı." : ""), skin, db != null ? "bad" : "dim");
        dn.setWrap(true);
        dv.add(dn).width(560).padLeft(8);
        body.add(dv).padBottom(4).row();
    }

    private void ipo(GameState s, Company p) {
        Equity e = p.eq;
        body.add(UiKit.section(skin, "HALKA ARZ")).row();
        if (s.capital.ipoPreparing(p)) {
            long left = Math.max(0, e.ipoReadyHour - s.hour) / Balance.HOURS_PER_MONTH + 1;
            Label l = new Label("Hazırlık sürüyor (aracı kurum, izahname, düzenleyici onayı): yaklaşık " + left + " ay. Arz oranı %" + Math.round(e.ipoFraction * 100)
                + " yeni hisse. Fiyat arz günü talebe göre temel değerin %80–95'i olur; maliyet hasılatın %3'ü.", skin, "small");
            l.setWrap(true);
            body.add(l).width(760).row();
            if (e.ipoCrisisPending) {
                Label c = new Label("Küresel finans krizi: halka arz penceresi kapalı. Arzı erteleyin veya iptal edin.", skin, "bad");
                c.setWrap(true);
                body.add(c).width(760).padTop(4).row();
                Table b = new Table();
                b.add(button("Ertele (3 ay)", "ipo:postpone", true)).height(30).padRight(6);
                b.add(button("İptal et", "ipo:cancel", true)).height(30);
                body.add(b).padTop(4).row();
            } else body.add(button("Halka arzı iptal et", "ipo:cancel", true)).height(28).padTop(4).row();
            return;
        }
        String why = s.capital.ipoBlocker(p);
        Label cond = new Label("Koşullar: en az 8 çeyrek faaliyet · son 4 çeyrekte toplam net kâr pozitif · kredi notu en az BB · küresel finans krizi yok. "
            + "Hazırlık 6 ay sürer." + (why != null ? "\nŞu an yapılamaz: " + why : ""), skin, why != null ? "bad" : "small");
        cond.setWrap(true);
        body.add(cond).width(760).padBottom(4).row();
        Table w = new Table();
        w.add(new Label("Arz edilecek yeni hisse (mevcut sermayenin oranı)", skin, "small")).padRight(8);
        w.add(step("−", () -> ipoFraction = Capital.clampIpo(ipoFraction - 0.01))).size(30, 28);
        w.add(new Label("%" + Math.round(ipoFraction * 100), skin, "value")).width(60).center();
        w.add(step("+", () -> ipoFraction = Capital.clampIpo(ipoFraction + 0.01))).size(30, 28);
        w.add(step("−5", () -> ipoFraction = Capital.clampIpo(ipoFraction - 0.05))).size(40, 28).padLeft(6);
        w.add(step("+5", () -> ipoFraction = Capital.clampIpo(ipoFraction + 0.05))).size(40, 28).padLeft(3);
        body.add(w).padBottom(4).row();
        double[] q = s.capital.ipoEstimate(p, ipoFraction);
        Label pv = new Label("Yeni hisse " + shares(q[3]) + " (sınırlar %15–49) · arz sonrası kurucu payınız %" + p1(q[0])
            + " · tahmini net hasılat " + Fmt.money(q[1]) + " – " + Fmt.money(q[2]) + " (fiyat temel değerin %80–95'i, maliyet %3)", skin, "default");
        pv.setWrap(true);
        body.add(pv).width(760).padBottom(4).row();
        body.add(button("Halka arz hazırlığını başlat", "ipo:" + ipoFraction + (q[0] < FinanceBalance.CONTROL ? ":confirm" : ""), why == null)).height(32).row();
    }

    private void listedOps(GameState s, Company p) {
        Equity e = p.eq;
        if (e.ipoHour >= 0) {
            Label l = new Label("Halka arz: " + FinanceCenter.date(e.ipoHour) + " · arz fiyatı " + px(e.ipoPrice) + " · net hasılat " + Fmt.money(e.ipoProceeds), skin, "dim");
            body.add(l).padBottom(4).row();
        }
        body.add(UiKit.section(skin, "BEDELLİ SERMAYE ARTIRIMI (yeni hisse)")).row();
        String why = s.capital.issueBlocker(p);
        Table w = new Table();
        w.add(new Label("Yeni hisse oranı", skin, "small")).padRight(8);
        w.add(step("−", () -> issueFraction = Capital.clampIssue(issueFraction - 0.01))).size(30, 28);
        w.add(new Label("%" + Math.round(issueFraction * 100), skin, "value")).width(60).center();
        w.add(step("+", () -> issueFraction = Capital.clampIssue(issueFraction + 0.01))).size(30, 28);
        body.add(w).padBottom(2).row();
        double[] q = s.capital.issueEstimate(p, issueFraction);
        Label pv = new Label("İhraç fiyatı " + px(q[4]) + " (%" + Math.round(q[2] * 100) + " iskonto) · net hasılat " + Fmt.money(q[1]) + " (maliyet %3) · duyuruda hisse yaklaşık %"
            + Math.round(q[3] * 100) + " düşer · kurucu payınız %" + p1(e.founderPct()) + " → %" + p1(q[0]) + ". Yılda en fazla bir ihraç."
            + (why != null ? "\nŞu an yapılamaz: " + why : ""), skin, why != null ? "bad" : "small");
        pv.setWrap(true);
        body.add(pv).width(760).padBottom(4).row();
        body.add(button("Sermaye artırımı yap", "issue:" + issueFraction + (q[0] < FinanceBalance.CONTROL && e.founderPct() >= FinanceBalance.CONTROL ? ":confirm" : ""), why == null)).height(30).padBottom(8).row();
        body.add(UiKit.section(skin, "GERİ ALIM (hisseler iptal edilir; günde en fazla serbest dolaşımın %2'si)")).row();
        Table b = new Table();
        b.add(new Label("Bütçe", skin, "small")).padRight(8);
        b.add(step("−", () -> buyback = Math.max(50e6, buyback - 50e6))).size(30, 28);
        b.add(new Label(Fmt.money(buyback), skin, "value")).width(90).center();
        b.add(step("+", () -> buyback += 50e6)).size(30, 28).padRight(10);
        b.add(button("Geri alımı başlat", "buyback:" + buyback, p.cash >= buyback && e.free() > 0)).height(28).padRight(4);
        if (e.buybackBudget > 1) b.add(button("Durdur", "buyback:0", true)).height(28);
        body.add(b).row();
        double n = buyback / e.price;
        Label bl = new Label((e.buybackBudget > 1 ? "Süren geri alım: kalan bütçe " + Fmt.money(e.buybackBudget) + ". " : "")
            + "Bu bütçe yaklaşık " + shares(n) + " hisse alır; kurucu payınız %" + p1(e.founderPct()) + " → yaklaşık %" + p1(e.founder / Math.max(e.founder, e.shares - Math.min(n, e.free())))
            + ". Alım fiyatı yükseltir (her %1 için ~%1,5).", skin, "dim");
        bl.setWrap(true);
        body.add(bl).width(760).row();
    }

    private void row(Table t, String name, double n, double total) {
        Label l = new Label(name, skin, "small"); l.setWrap(true);
        t.add(l).width(260).left(); t.add(right(shares(n))).width(110); t.add(right("%" + p1(n / total))).width(80).row();
    }

    private Label right(String s) { Label l = new Label(s, skin, "small"); l.setAlignment(Align.right); return l; }
    private Label dim(String s) { Label l = new Label(s, skin, "dim"); l.setWrap(true); return l; }
    static String shares(double n) { return String.format(Locale.ROOT, "%.2fM", n / 1e6).replace('.', ','); }
    static String p1(double v) { return String.format(Locale.ROOT, "%.1f", v * 100).replace('.', ','); }
    static String px(double v) { return String.format(Locale.ROOT, "%.2f $", v).replace('.', ','); }

    private TextButton button(String text, String act, boolean enabled) {
        TextButton b = new TextButton(text, skin, "small");
        b.setDisabled(!enabled);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent ev, Actor a) { if (!b.isDisabled()) { action.accept(act); key = ""; } } });
        return b;
    }

    private TextButton toggle(String text, boolean on, String act) {
        TextButton b = new TextButton(text, skin, "small");
        b.setChecked(on);
        b.setProgrammaticChangeEvents(false);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent ev, Actor a) { action.accept(act); key = ""; } });
        return b;
    }

    private TextButton step(String text, Runnable r) {
        TextButton b = new TextButton(text, skin, "small");
        b.addListener(new ChangeListener() { public void changed(ChangeEvent ev, Actor a) { r.run(); key = ""; if (st != null) update(st); } });
        return b;
    }
}
