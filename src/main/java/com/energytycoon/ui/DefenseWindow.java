package com.energytycoon.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.energytycoon.sim.*;

import java.util.function.Consumer;

/**
 * Savunma ekrani: sirketinize pay alim teklifi (cagri) yapildiginda oyun durur ve acilir. 30 gun icinde geri alim,
 * temettu artisi veya beyaz sovalye daveti; tahmini kabul orani ve teklif sonrasi kontrol durumu gosterilir.
 */
public final class DefenseWindow {
    public final Window win;
    private final Skin skin;
    private final Consumer<String> action;
    private final Table body = new Table();
    private final ScrollPane scroll;
    private GameState st;
    private double budget = 100e6;
    private String key = "";

    public DefenseWindow(Skin skin, Window win, Consumer<String> action) {
        this.skin = skin; this.win = win; this.action = action;
        scroll = new ScrollPane(body, skin);
        scroll.setFadeScrollBars(false); scroll.setOverscroll(false, false); scroll.setScrollingDisabled(true, false);
        win.add(scroll).pad(4);
    }

    public void show(GameState s) { st = s; key = ""; refresh(); win.setVisible(true); win.toFront(); }

    public void refresh() {
        if (st == null) return;
        Company p = st.player;
        java.util.List<DealBook.TenderOffer> offers = st.mna.openOffers(p);
        StringBuilder k = new StringBuilder().append(budget).append(Math.round(p.cash / 1e6)).append(p.eq.dividendPct).append(Math.round(p.eq.buybackBudget / 1e6)).append(st.hour / 24);
        for (DealBook.TenderOffer t : offers) k.append(t.id).append(t.knightInvited).append(Math.round(t.defenseUplift * 1000));
        if (k.toString().equals(key)) return;
        key = k.toString();
        body.clear();
        body.defaults().left();
        if (offers.isEmpty()) {
            body.add(new Label("Şirketinize yönelik açık pay alım teklifi yok.", skin, "small")).row();
            body.add(btn("Kapat", () -> win.setVisible(false))).width(140).height(30).padTop(6);
            DealWindow.fit(win, scroll);
            return;
        }
        for (DealBook.TenderOffer t : offers) {
            Company b = st.companies.get(t.bidder);
            double ep = st.mna.effectivePremium(t), acc = Mna.acceptance(ep);
            double own = st.mna.groupStake(b, p);
            double after = own + p.eq.freePct() * acc;
            Label l = new Label((t.knight ? "Karşı teklif (beyaz şövalye): " : t.mandatory ? "Zorunlu teklif: " : "ÇAĞRI: ") + b.name + " hisse başı " + CapitalTab.px(t.price)
                + " teklif ediyor (teklif öncesi fiyat " + CapitalTab.px(t.prePrice) + ", prim %" + Math.round((t.price / t.prePrice - 1) * 100) + ") · " + Math.max(0, (t.endHour - st.hour) / 24) + " gün kaldı"
                + "\nSavunmalar sonrası etkin prim %" + Math.round(ep * 100) + " → serbest dolaşımın tahmini kabul oranı %" + Math.round(acc * 100)
                + " · teklifçinin beklenen payı ~%" + CapitalTab.p1(after) + (after > FinanceBalance.CONTROL ? " (kontrolü alabilir; ardından rekabet onayı)" : " (kontrol eşiğinin altında)")
                + "\nKurucu payınız %" + CapitalTab.p1(p.eq.founderPct()) + ". Kontrol el değiştirirse oyun \"Şirketiniz devralındı\" sonucuyla biter.", skin, after > FinanceBalance.CONTROL ? "bad" : "default");
            l.setWrap(true);
            body.add(l).width(680).padBottom(8).row();
        }
        body.add(UiKit.section(skin, "1. GERİ ALIM (fiyatı yükseltir, etkin primi düşürür; hisseler iptal edilir, kurucu payınız artar)")).row();
        Table bb = new Table();
        bb.add(btn("−", () -> budget = Math.max(50e6, budget - 50e6))).size(30, 28);
        bb.add(new Label(Fmt.money(budget), skin, "value")).width(90).center();
        bb.add(btn("+", () -> budget += 50e6)).size(30, 28).padRight(8);
        bb.add(button("Geri alımı başlat", "def:buyback:" + budget, p.cash >= budget)).height(28);
        body.add(bb).padBottom(2).row();
        if (p.eq.buybackBudget > 1) body.add(new Label("Süren geri alım: kalan " + Fmt.money(p.eq.buybackBudget), skin, "dim")).row();
        body.add(UiKit.section(skin, "2. TEMETTÜ ARTIŞI (%50; kabul oranını düşürür)")).padTop(6).row();
        String db = st.capital.dividendBlocker(p);
        body.add(button(p.eq.dividendPct >= 50 ? "Temettü zaten %50" : "Temettüyü %50'ye çıkar", "def:div", db == null && p.eq.dividendPct < 50)).height(28).row();
        if (db != null) body.add(new Label(db, skin, "bad")).row();
        body.add(UiKit.section(skin, "3. BEYAZ ŞÖVALYE (başka bir şirketi en az %5 yüksek karşı teklife davet edin)")).padTop(6).row();
        boolean invited = false;
        for (DealBook.TenderOffer t : offers) invited |= t.knightInvited;
        Table kt = new Table();
        DealBook.TenderOffer first = offers.get(0);
        for (Company c : st.companies) {
            if (!st.mna.active(c) || c.player || st.companies.indexOf(c) == first.bidder || st.competition.sameGroup(c, st.companies.get(first.bidder))) continue;
            kt.add(button(c.name + " (" + Fmt.money(c.cash) + " nakit)", "def:knight:" + st.companies.indexOf(c), !invited)).height(28).padRight(4);
        }
        body.add(kt).row();
        if (invited) body.add(new Label("Beyaz şövalye davet edildi.", skin, "dim")).row();
        Label note = new Label("Beyaz şövalye kazanırsa şirketiniz onun kontrolüne geçebilir; bu da oyunun sonu demektir. En güvenli savunma geri alımla kurucu payınızı %50'nin üstüne çıkarmaktır.", skin, "dim");
        note.setWrap(true);
        body.add(note).width(680).padTop(6).row();
        body.add(btn("Kapat (oyun devam eder)", () -> win.setVisible(false))).width(220).height(30).padTop(8);
        DealWindow.fit(win, scroll);
    }

    private TextButton button(String text, String act, boolean enabled) {
        TextButton b = new TextButton(text, skin, "small");
        b.setDisabled(!enabled);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent ev, Actor a) { if (!b.isDisabled()) { action.accept(act); key = ""; refresh(); } } });
        return b;
    }

    private TextButton btn(String text, Runnable r) {
        TextButton b = new TextButton(text, skin, "small");
        b.addListener(new ChangeListener() { public void changed(ChangeEvent ev, Actor a) { r.run(); key = ""; refresh(); } });
        return b;
    }
}
