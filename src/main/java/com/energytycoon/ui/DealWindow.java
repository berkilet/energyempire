package com.energytycoon.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.energytycoon.sim.*;

import java.util.function.Consumer;

/**
 * Islem penceresi: kurucuya teklif (teklif → karsi teklif → son karar), piyasadan alim/satis, pay alim teklifi (cagri).
 * Finansman secimi (nakit, devralma kredisi, pay takasi) ve islem oncesi rekabet onizlemesi: oyuncu reddedilecek bir isleme habersiz girmez.
 */
public final class DealWindow {
    public enum Mode { FOUNDER, MARKET, TENDER, SELL }

    public final Window win;
    private final Skin skin;
    private final Consumer<String> action;
    private final Table body = new Table();
    private final ScrollPane scroll;
    private GameState st;
    private Company target;
    private Mode mode;
    private double pct = 0.10, price, premium = 0.30;
    private Bank bank;
    private boolean swap;
    private Mna.Reply reply;
    private boolean finalRound;
    private String key = "";

    public DealWindow(Skin skin, Window win, Consumer<String> action) {
        this.skin = skin; this.win = win; this.action = action;
        scroll = new ScrollPane(body, skin);
        scroll.setFadeScrollBars(false); scroll.setOverscroll(false, false); scroll.setScrollingDisabled(true, false);
        win.add(scroll).pad(4);
    }

    public void show(GameState s, Company t, Mode m) {
        st = s; target = t; mode = m; reply = null; finalRound = false; swap = false;
        bank = s.finance.bestAcquisitionBank(s.player, 10e6, t);
        double fps = t.eq.fundamental / Math.max(1, t.eq.shares);
        pct = m == Mode.FOUNDER ? Math.min(0.20, t.eq.founderPct()) : m == Mode.SELL ? s.mna.stake(s.player, t) : Math.min(0.05, t.eq.freePct());
        price = Math.round(fps * 1.2 * 100) / 100.0;
        DealBook.TenderOffer open = s.mna.openOffer(t);
        premium = open != null ? Math.max(0.2, open.price * (1 + FinanceBalance.KNIGHT_STEP) / Math.max(0.01, t.eq.price) - 1) : 0.30;
        win.getTitleLabel().setText(switch (m) { case FOUNDER -> "Kurucuya teklif — " + t.name; case MARKET -> "Piyasadan pay alımı — " + t.name;
            case TENDER -> "Pay alım teklifi (çağrı) — " + t.name; case SELL -> "Pay satışı — " + t.name; });
        key = "";
        refresh();
        win.setVisible(true); win.toFront();
    }

    /** Dogrulama: istenen fiyatin %90'i ile teklif (karsi teklif asamasi). */
    public void devCounter() {
        double shares = Math.min(target.eq.founder, pct * target.eq.shares);
        price = Math.round(st.mna.founderAsk(target, shares).perShare * 0.9 * 100) / 100.0;
        reply = st.mna.founderReply(target, shares, price, false);
        key = ""; refresh();
    }

    public void refresh() {
        if (st == null || !win.isVisible() && !key.isEmpty()) return;
        String k = mode + "|" + pct + "|" + price + "|" + premium + "|" + bank + "|" + swap + "|" + (reply == null ? -1 : reply.kind) + "|" + finalRound + "|" + Math.round(st.player.cash / 1e6) + "|" + (st.hour / 24);
        if (k.equals(key)) return;
        key = k;
        body.clear();
        body.defaults().left();
        Company p = st.player, t = target;
        Equity e = t.eq;
        double fps = e.fundamental / Math.max(1, e.shares);
        Label info = new Label("Temel değer " + Fmt.money(e.fundamental) + " (" + CapitalTab.px(fps) + " / hisse)" + (e.listed ? " · piyasa fiyatı " + CapitalTab.px(e.price) : " · halka kapalı")
            + " · kurucu %" + CapitalTab.p1(e.founderPct()) + " · serbest %" + CapitalTab.p1(e.freePct()) + " · payınız %" + CapitalTab.p1(st.mna.stake(p, t))
            + " · not " + st.finance.rating(t) + (st.mna.distressed(t) ? " (zor durumda)" : ""), skin, "small");
        info.setWrap(true);
        body.add(info).width(720).padBottom(6).row();
        switch (mode) {
            case FOUNDER -> founder(p, t, fps);
            case MARKET -> market(p, t);
            case SELL -> sell(p, t);
            case TENDER -> tender(p, t);
        }
        Table close = new Table();
        close.add(btn("Kapat", () -> win.setVisible(false))).width(140).height(30);
        body.add(close).padTop(8).right();
        fit(win, scroll);
    }

    /** Pencere en fazla ekranin %70'i; asarsa icerik kaydirilir; ortalanir. */
    static void fit(Window win, ScrollPane scroll) {
        Cell<?> c = win.getCell(scroll);
        c.height(com.badlogic.gdx.scenes.scene2d.ui.Value.prefHeight).width(com.badlogic.gdx.scenes.scene2d.ui.Value.prefWidth);
        win.invalidate(); win.pack();
        if (win.getStage() == null) return;
        float h = win.getStage().getHeight(), max = h * 0.70f;
        if (win.getHeight() > max) {
            float chrome = win.getHeight() - scroll.getHeight();
            c.height(Math.max(120, max - chrome)).width(scroll.getPrefWidth() + 14);
            win.invalidate(); win.pack();
        }
        win.setPosition((win.getStage().getWidth() - win.getWidth()) / 2, Math.max(0, (h - win.getHeight()) / 2));
    }

    // ------------------------------------------------------------ kurucuya teklif
    private void founder(Company p, Company t, double fps) {
        double shares = Math.min(t.eq.founder, pct * t.eq.shares);
        Table a = new Table();
        a.add(new Label("Alınacak pay", skin, "small")).width(110);
        a.add(step("−", () -> { pct = Math.max(0.01, pct - 0.01); reply = null; })).size(30, 28);
        a.add(new Label("%" + Math.round(pct * 100), skin, "value")).width(60).center();
        a.add(step("+", () -> { pct = Math.min(t.eq.founderPct(), pct + 0.01); reply = null; })).size(30, 28);
        a.add(step("+10", () -> { pct = Math.min(t.eq.founderPct(), pct + 0.10); reply = null; })).size(44, 28).padLeft(3).padRight(16);
        a.add(new Label("Hisse başı teklif", skin, "small")).width(110);
        a.add(step("−", () -> { price = Math.max(0.01, Math.round((price - fps * 0.01) * 100) / 100.0); reply = null; })).size(30, 28);
        a.add(new Label(CapitalTab.px(price), skin, "value")).width(90).center();
        a.add(step("+", () -> { price = Math.round((price + fps * 0.01) * 100) / 100.0; reply = null; })).size(30, 28);
        body.add(a).padBottom(4).row();
        double cost = shares * price;
        Label s1 = new Label(CapitalTab.shares(shares) + " hisse · prim (temel değere göre) %" + Math.round((price / fps - 1) * 100) + " · toplam " + Fmt.money(cost)
            + " · işlem sonrası grup payınız %" + CapitalTab.p1(st.mna.groupStake(p, t) + shares / t.eq.shares), skin, "default");
        s1.setWrap(true);
        body.add(s1).width(720).padBottom(4).row();
        String block = st.mna.founderOfferBlocker(p, t, shares);
        Mna.Financing f = financing(p, t, cost);
        preview(p, t, st.mna.groupStake(p, t) + shares / t.eq.shares);
        if (block != null) { body.add(bad(block)).width(720).row(); return; }
        if (reply == null) {
            body.add(btn("Teklif ver", () -> reply = st.mna.founderReply(t, shares, price, false))).width(200).height(32).row();
            return;
        }
        Label r = new Label((reply.kind == Mna.ACCEPT ? "KABUL: " : reply.kind == Mna.COUNTER ? "KARŞI TEKLİF: " : "RET: ") + reply.reason, skin, reply.kind == Mna.REJECT ? "bad" : "good");
        r.setWrap(true);
        body.add(r).width(720).padBottom(4).row();
        Table b = new Table();
        String fin = finBlock(p, t, f, cost);
        if (reply.kind == Mna.ACCEPT) {
            if (fin != null) body.add(bad(fin)).width(720).row();
            b.add(button("Anlaşmayı imzala (" + Fmt.money(cost) + ")", "founder:" + st.companies.indexOf(t) + ":" + shares + ":" + price + ":" + enc(f), fin == null)).height(32).padRight(6);
        } else if (reply.kind == Mna.COUNTER) {
            b.add(btn("Karşı teklifi kabul et (" + CapitalTab.px(reply.perShare) + ")", () -> { price = reply.perShare; reply = st.mna.founderReply(t, shares, price, true); })).height(32).padRight(6);
            b.add(btn("Son teklif olarak " + CapitalTab.px(price) + " ver", () -> { finalRound = true; reply = st.mna.founderReply(t, shares, price, true); })).height(32);
        } else b.add(btn("Yeni teklif hazırla", () -> reply = null)).height(32);
        body.add(b).row();
    }

    // ------------------------------------------------------------ piyasa
    private void market(Company p, Company t) {
        double maxPct = t.eq.freePct();
        Table a = new Table();
        a.add(new Label("Alınacak pay", skin, "small")).width(110);
        a.add(step("−", () -> pct = Math.max(0.01, pct - 0.01))).size(30, 28);
        a.add(new Label("%" + Math.round(pct * 100), skin, "value")).width(60).center();
        a.add(step("+", () -> pct = Math.min(maxPct, pct + 0.01))).size(30, 28);
        a.add(step("+5", () -> pct = Math.min(maxPct, pct + 0.05))).size(40, 28).padLeft(3);
        body.add(a).padBottom(4).row();
        double n = pct * t.eq.shares, cost = n * t.eq.price * (1 + FinanceBalance.PRICE_IMPACT * pct / 2);
        double after = st.mna.groupStake(p, t) + pct;
        Label l = new Label(CapitalTab.shares(n) + " hisse · tahmini maliyet " + Fmt.money(cost) + " (her %1 alım fiyatı ~%1,5 yükseltir) · günde en fazla hisselerin %2'si → yaklaşık "
            + (int) Math.ceil(pct / FinanceBalance.DAILY_TRADE_MAX) + " gün · işlem sonrası payınız %" + CapitalTab.p1(after)
            + "\n%5, 10, 15, 20, 25, 33, 50 ve 67 eşikleri kamuya duyurulur; hedefin hissesi %5–10 yükselir ve hedef savunmaya geçebilir. Alım nakitle yapılır.", skin, "small");
        l.setWrap(true);
        body.add(l).width(720).padBottom(4).row();
        preview(p, t, after);
        String why = st.mna.marketBlocker(p, t, n);
        if (why != null) body.add(bad(why)).width(720).row();
        body.add(button("Alım emri ver", "order:" + st.companies.indexOf(t) + ":" + n, why == null)).height(32).row();
    }

    private void sell(Company p, Company t) {
        double max = st.mna.stake(p, t);
        Table a = new Table();
        a.add(new Label("Satılacak pay", skin, "small")).width(110);
        a.add(step("−", () -> pct = Math.max(0.01, pct - 0.01))).size(30, 28);
        a.add(new Label("%" + CapitalTab.p1(Math.min(pct, max)), skin, "value")).width(70).center();
        a.add(step("+", () -> pct = Math.min(max, pct + 0.01))).size(30, 28);
        body.add(a).padBottom(4).row();
        double n = Math.min(pct, max) * t.eq.shares;
        Label l = new Label(CapitalTab.shares(n) + " hisse · tahmini hasılat " + Fmt.money(n * t.eq.price * (1 - FinanceBalance.PRICE_IMPACT * pct / 2)) + " (satış fiyatı düşürür)", skin, "small");
        body.add(l).padBottom(4).row();
        body.add(button("Sat", "sell:" + st.companies.indexOf(t) + ":" + n, n > 0)).height(32).row();
    }

    // ------------------------------------------------------------ cagri
    private void tender(Company p, Company t) {
        DealBook.TenderOffer open = st.mna.openOffer(t);
        double min = open != null ? open.price * (1 + FinanceBalance.KNIGHT_STEP) / t.eq.price - 1 : FinanceBalance.TENDER_MIN_PREMIUM;
        Table a = new Table();
        a.add(new Label("Prim (piyasa fiyatına göre)", skin, "small")).padRight(8);
        a.add(step("−", () -> premium = Math.max(min, premium - 0.01))).size(30, 28);
        a.add(new Label("%" + Math.round(premium * 100), skin, "value")).width(60).center();
        a.add(step("+", () -> premium += 0.01)).size(30, 28);
        a.add(step("+5", () -> premium += 0.05)).size(40, 28).padLeft(3);
        body.add(a).padBottom(4).row();
        double px = t.eq.price * (1 + premium);
        double n = st.mna.tenderShares(p, t, px), cost = n * px;
        double acc = Mna.acceptance(premium);
        double expected = st.mna.groupStake(p, t) + (t.eq.free() * acc + (n - t.eq.free())) / t.eq.shares;
        Label l = new Label("Hisse başı " + CapitalTab.px(px) + " · teklif kapsamı " + CapitalTab.shares(n) + " hisse (serbest dolaşım + diğer ortaklar"
            + (!t.player && t.eq.founder > 0 && px >= st.mna.founderAsk(t, t.eq.founder).perShare ? " + kurucu (istediği fiyat karşılanıyor)" : "") + ") · finansman gereken tutar " + Fmt.money(cost)
            + "\nSerbest dolaşımın tahmini kabul oranı %" + Math.round(acc * 100) + " (eğri: %20 primde ~%40, %35'te ~%65, %50'de ~%85) · beklenen payınız ~%" + CapitalTab.p1(Math.min(1, expected))
            + " · teklif 30 gün açık kalır; hedef geri alım, temettü artışı veya beyaz şövalyeyle savunabilir."
            + (open != null ? "\nAçık teklif var (" + st.companies.get(open.bidder).name + ", " + CapitalTab.px(open.price) + "): karşı teklif en az %5 yüksek olmalı." : ""), skin, "small");
        l.setWrap(true);
        body.add(l).width(720).padBottom(4).row();
        Mna.Financing f = financing(p, t, cost);
        Competition.Preview pv = preview(p, t, Math.max(0.51, expected));
        String why = st.mna.tenderBlocker(p, t, px, f, open != null);
        if (why != null) body.add(bad(why)).width(720).row();
        boolean cond = pv != null && pv.outcome == Competition.CONDITIONAL;
        body.add(button(cond ? "Satış taahhüdünü kabul ederek çağrıyı başlat" : "Çağrıyı başlat", "takeover:" + st.companies.indexOf(t) + ":" + px + ":" + enc(f), why == null)).height(32).row();
    }

    // ------------------------------------------------------------ ortak: finansman ve rekabet onizlemesi
    private Mna.Financing financing(Company p, Company t, double cost) {
        Mna.Financing f = new Mna.Financing();
        f.cash = Math.max(0, Math.min(p.cash, cost));
        double left = cost - f.cash;
        if (bank != null && left > 1) {
            double max = st.finance.maxAcquisitionLoan(p, bank, t);
            f.loan = Math.min(left, max); f.bank = bank; left -= f.loan;
        }
        if (swap && left > 1 && p.eq.listed) { f.swapValue = left; left = 0; }
        if (f.loan <= 0) f.bank = null;
        Table ft = new Table();
        ft.add(new Label("Finansman:", skin, "small")).padRight(6);
        ft.add(toggle("Kredisiz", bank == null, () -> bank = null)).height(26).padRight(3);
        for (Bank b : Bank.values()) ft.add(toggle(b.label, bank == b, () -> bank = b)).height(26).padRight(3);
        if (p.eq.listed) ft.add(toggle("Pay takası", swap, () -> swap = !swap)).height(26).padLeft(6);
        body.add(ft).padBottom(2).row();
        String loanInfo = f.bank != null ? " · devralma kredisi " + Fmt.money(f.loan) + " (" + f.bank.label + ", %" + Fmt.f2(st.finance.acquisitionRate(p, f.bank) * 100)
            + ", 7 yıl, en fazla hedef değerinin %50'si)" : bank != null ? " · " + bank.label + ": " + st.finance.acquisitionRefusal(p, bank, Math.max(10e6, cost - f.cash), t) : "";
        String swapInfo = f.swapValue > 0 ? " · pay takası " + Fmt.money(f.swapValue) + " (yeni hisse " + CapitalTab.shares(f.swapValue / p.eq.price) + "; kurucu payınız %"
            + CapitalTab.p1(p.eq.founderPct()) + " → %" + CapitalTab.p1(p.eq.founder / (p.eq.shares + f.swapValue / p.eq.price)) + ")" : "";
        Label l = new Label("Nakit " + Fmt.money(f.cash) + " (elinizde " + Fmt.money(p.cash) + ")" + loanInfo + swapInfo + " · toplam " + Fmt.money(f.total()) + " / gereken " + Fmt.money(cost), skin,
            f.total() < cost - 1 ? "bad" : "dim");
        l.setWrap(true);
        body.add(l).width(720).padBottom(4).row();
        return f;
    }

    private String finBlock(Company p, Company t, Mna.Financing f, double cost) { return st.mna.financingBlocker(p, t, f, cost); }

    /** Islem sonrasi grup payi %50'yi asiyorsa rekabet onizlemesi. */
    private Competition.Preview preview(Company p, Company t, double after) {
        if (after <= FinanceBalance.CONTROL) return null;
        Competition.Preview pv = st.competition.preview(p, t);
        body.add(UiKit.section(skin, "REKABET ÖNİZLEMESİ (kontrol değişikliği)")).padTop(4).row();
        Table tb = new Table();
        String[] hd = { "İlgili pazar", "Siz", "Hedef", "Birleşik", "Eşik" };
        float[] w = { 220, 70, 70, 80, 70 };
        for (int i = 0; i < hd.length; i++) { Label h = new Label(hd[i], skin, "dim"); h.setAlignment(i == 0 ? Align.left : Align.right); tb.add(h).width(w[i]); }
        tb.row();
        for (Competition.Row r : pv.rows) {
            String[] c = { r.market, "%" + Math.round(r.acquirer * 100), "%" + Math.round(r.target * 100), "%" + Math.round(r.combined * 100), "%" + Math.round(r.limit * 100) };
            for (int i = 0; i < c.length; i++) {
                Label lb = new Label(c[i], skin, i == 3 && r.combined > r.limit ? "bad" : "small"); lb.setWrap(true); lb.setAlignment(i == 0 ? Align.left : Align.right);
                tb.add(lb).width(w[i]);
            }
            tb.row();
        }
        body.add(tb).padBottom(2).row();
        Label s = new Label("Ulusal pay (işlem sonrası) %" + Math.round(pv.national * 100) + " · değer oranı " + FinanceCenter.num(pv.valueRatio)
            + (pv.exempt ? " ≥ 1,5: ölçek farkı muafiyeti uygulanır" : " < 1,5: pazar payı kuralları uygulanır")
            + "\nBeklenen sonuç: " + pv.summary() + (pv.reviewDays > 0 ? " · inceleme " + pv.reviewDays + " gün" : "")
            + "\nOnay çıkmadan kontrol hakları (bağlı ortaklık, strateji) kullanılamaz.", skin, pv.outcome == Competition.REJECT ? "bad" : pv.outcome == Competition.CONDITIONAL ? "default" : "good");
        s.setWrap(true);
        body.add(s).width(720).padBottom(4).row();
        return pv;
    }

    private static String enc(Mna.Financing f) { return f.cash + ":" + f.loan + ":" + (f.bank == null ? "-" : f.bank.name()) + ":" + f.swapValue; }

    private Label bad(String s) { Label l = new Label(s, skin, "bad"); l.setWrap(true); return l; }

    private TextButton button(String text, String act, boolean enabled) {
        TextButton b = new TextButton(text, skin, "small");
        b.setDisabled(!enabled);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent ev, Actor a) { if (!b.isDisabled()) { action.accept(act); win.setVisible(false); } } });
        return b;
    }

    private TextButton btn(String text, Runnable r) {
        TextButton b = new TextButton(text, skin, "small");
        b.addListener(new ChangeListener() { public void changed(ChangeEvent ev, Actor a) { r.run(); key = ""; refresh(); } });
        return b;
    }

    private TextButton step(String text, Runnable r) { return btn(text, r); }

    private TextButton toggle(String text, boolean on, Runnable r) {
        TextButton b = new TextButton(text, skin, "small");
        b.setChecked(on);
        b.setProgrammaticChangeEvents(false);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent ev, Actor a) { r.run(); key = ""; refresh(); } });
        return b;
    }
}
