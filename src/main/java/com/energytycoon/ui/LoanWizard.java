package com.energytycoon.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.energytycoon.sim.*;

import java.util.List;
import java.util.function.Consumer;

/**
 * Yeni kredi sihirbazi: urun (ticari kredi / isletme kredisi limiti), tutar, vade, taksit sikligi, sabit/degisken;
 * uc bankanin teklifi yan yana; secilen teklif icin taksit, toplam geri odeme, 12 aylik takvime ve nota olasi etkisi.
 */
public final class LoanWizard {
    public final Window win;
    private final Skin skin;
    private final Consumer<String> action;
    private final Table body = new Table();
    private GameState st;
    private boolean revolver;
    private double amount = 100e6;
    private int years = 5, freq = 3;
    private boolean fixed;
    private Bank selected;
    private String key = "";

    public LoanWizard(Skin skin, Window win, Consumer<String> action) {
        this.skin = skin; this.win = win; this.action = action;
        win.add(body).pad(4);
    }

    public void devSelect(Bank b) { selected = b; key = ""; refresh(); }

    public void show(GameState s) {
        st = s; selected = null; key = "";
        refresh();
        win.setVisible(true); win.toFront();
    }

    public void refresh() {
        if (st == null || !win.isVisible() && !key.isEmpty()) return;
        Company c = st.player;
        StringBuilder k = new StringBuilder().append(revolver).append(amount).append(years).append(freq).append(fixed).append(selected)
            .append(Math.round(c.cash / 1e6)).append(st.finance.rating(c)).append(Math.round(st.macro.policyRate * 1e4));
        if (k.toString().equals(key)) return;
        key = k.toString();
        body.clear();
        body.defaults().left();
        // urun ve kosullar
        Table prod = new Table();
        prod.add(new Label("Ürün:", skin, "default")).padRight(8);
        prod.add(toggle("Ticari kredi", !revolver, () -> { revolver = false; selected = null; })).height(28).padRight(4);
        prod.add(toggle("İşletme kredisi limiti", revolver, () -> { revolver = true; selected = null; })).height(28);
        body.add(prod).padBottom(6).row();
        if (!revolver) {
            Table t = new Table();
            t.add(new Label("Tutar", skin, "small")).width(70);
            t.add(btn("−", () -> amount = Math.max(FinanceBalance.LOAN_STEP, amount - FinanceBalance.LOAN_STEP))).size(30, 28);
            t.add(new Label(Fmt.money(amount), skin, "value")).width(90).center();
            t.add(btn("+", () -> amount += FinanceBalance.LOAN_STEP)).size(30, 28).padRight(16);
            t.add(new Label("Vade", skin, "small")).width(50);
            t.add(btn("−", () -> years = Math.max(FinanceBalance.COMMERCIAL_MIN_YEARS, years - 1))).size(30, 28);
            t.add(new Label(years + " yıl", skin, "value")).width(70).center();
            t.add(btn("+", () -> years = Math.min(FinanceBalance.YESIL_MAX_YEARS, years + 1))).size(30, 28).row();
            body.add(t).padBottom(4).row();
            Table t2 = new Table();
            t2.add(new Label("Taksit", skin, "small")).width(70);
            t2.add(toggle("Aylık", freq == 1, () -> freq = 1)).height(28).padRight(4);
            t2.add(toggle("3 ayda bir", freq == 3, () -> freq = 3)).height(28).padRight(16);
            t2.add(new Label("Faiz", skin, "small")).width(50);
            t2.add(toggle("Değişken (3 ayda bir güncellenir)", !fixed, () -> fixed = false)).height(28).padRight(4);
            t2.add(toggle("Sabit (+1 puan)", fixed, () -> fixed = true)).height(28);
            body.add(t2).padBottom(8).row();
        } else {
            Label n = new Label(st.finance.revolver(c) != null ? "İşletme kredisi limitiniz zaten açık (" + Fmt.money(st.finance.revolver(c).limit) + "). Kullanım ve ödeme Krediler sekmesinde."
                : "Nota göre " + Fmt.money(st.finance.revolverLimitFor(c)) + " limit · politika faizi + not primi + 3 puan, değişken · %0,5 taahhüt komisyonu · kullanılan tutar 12 ayda kapatılmalı.", skin, "small");
            n.setWrap(true);
            body.add(n).width(780).padBottom(8).row();
        }
        // teklifler
        Table offers = new Table();
        if (!revolver) {
            List<Finance.Offer> os = st.finance.commercialOffers(c, amount, years, freq, fixed, null);
            for (Finance.Offer o : os) offers.add(offerCard(o)).width(256).top().padRight(6);
        } else if (st.finance.revolver(c) == null) {
            for (Bank b : Bank.values()) {
                Table card = new Table(skin); card.setBackground(selected == b ? "rowSel" : "card"); card.pad(8);
                boolean ok = b.lendsTo(st.finance.rating(c)) && c.bankBanUntil[b.ordinal()] <= st.hour;
                card.add(new Label(b.label, skin, "default")).left().row();
                Label d = new Label(ok ? "Limit " + Fmt.money(st.finance.revolverLimitFor(c)) + "\nFaiz ≈ " + Macro.pct(st.macro.policyRate + st.finance.rating(c).premium() + FinanceBalance.REVOLVER_SPREAD)
                    + " değişken\nKomisyon " + Fmt.money(st.finance.revolverLimitFor(c) * FinanceBalance.REVOLVER_FEE) : "Bu banka şu an limit açmıyor", skin, "small");
                d.setWrap(true);
                card.add(d).width(236).left().padBottom(6).row();
                TextButton b2 = new TextButton("Bu bankayla aç", skin, "small"); b2.setDisabled(!ok);
                b2.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { if (!b2.isDisabled()) { action.accept("revopen:" + b.name()); win.setVisible(false); } } });
                card.add(b2).width(236).height(28);
                offers.add(card).width(256).top().padRight(6);
            }
        }
        body.add(offers).padBottom(8).row();
        // onay
        if (!revolver && selected != null) {
            Finance.Offer o = null;
            for (Finance.Offer x : st.finance.commercialOffers(c, amount, years, freq, fixed, null)) if (x.bank == selected) o = x;
            if (o != null && o.available) {
                double[] sch = st.finance.schedule12(c);
                double before = 0; for (double v : sch) before += v;
                double after = before + o.installment * Math.floor(12.0 / freq);
                double e4 = st.finance.ebitda4(c), nd = c.debt() - c.cash, i4 = st.finance.interest4(c);
                String ratingText;
                if (c.quarters < 4) ratingText = "Yeni şirket: ilk 4 çeyrek notunuz BB kabul edilir.";
                else {
                    double levB = e4 <= 0 ? Double.POSITIVE_INFINITY : nd / e4, levA = e4 <= 0 ? Double.POSITIVE_INFINITY : (nd + amount) / e4;
                    Rating rb = st.finance.rating(c), ra = Rating.compute(e4, nd + amount, i4 + amount * o.rate);
                    ratingText = "Bu kredi (tutar yatırıma harcanırsa) Net borç / FAVÖK oranınızı " + FinanceCenter.num(levB) + "'den " + FinanceCenter.num(levA)
                        + "'e çıkarır; " + (ra.ordinal() > rb.ordinal() ? "notunuz " + rb + "'den " + ra + "'e düşebilir." : "notunuz " + rb + " kalır.")
                        + (levA > selected.covenantLimit() ? " Taahhüt eşiği " + FinanceCenter.num(selected.covenantLimit()) + " aşılır!" : "");
                }
                Label conf = new Label(selected.label + " · " + Fmt.money(amount) + " · " + years + " yıl · " + (freq == 1 ? "aylık" : "3 ayda bir") + " · "
                    + Macro.pct(o.rate) + (fixed ? " sabit" : " değişken")
                    + "\nTaksit " + Fmt.money(o.installment) + " · toplam geri ödeme " + Fmt.money(o.totalRepay) + " · peşin masraf " + Fmt.money(o.upfront)
                    + "\n12 aylık takvime etkisi: " + Fmt.money(before) + " → " + Fmt.money(after)
                    + "\n" + ratingText, skin, "small");
                conf.setWrap(true);
                Table ct = new Table(skin); ct.setBackground("card"); ct.pad(8);
                ct.add(conf).width(760).left().row();
                TextButton ok = new TextButton("Onayla ve imzala", skin, "accent");
                final Finance.Offer fo = o;
                ok.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) {
                    action.accept("loan:" + fo.bank.name() + ":" + (long) amount + ":" + years + ":" + freq + ":" + fixed); win.setVisible(false);
                } });
                ct.add(ok).width(220).height(32).padTop(6).left();
                body.add(ct).row();
            }
        }
        win.pack();
        if (win.getStage() != null) {
            float h = win.getStage().getHeight();
            win.setPosition((win.getStage().getWidth() - win.getWidth()) / 2, Math.max(0, (h - win.getHeight()) / 2));
        }
    }

    private Table offerCard(Finance.Offer o) {
        Table card = new Table(skin);
        card.setBackground(selected == o.bank ? "rowSel" : "card");
        card.pad(8);
        card.add(new Label(o.bank.label, skin, "default")).left().row();
        String txt = o.available
            ? "Faiz " + Macro.pct(o.rate) + (fixed ? " sabit" : " değişken") + "\nTaksit " + Fmt.money(o.installment) + " / " + (freq == 1 ? "ay" : "3 ay")
              + "\nPeşin masraf " + Fmt.money(o.upfront) + "\nTaahhüt: Net borç/FAVÖK ≤ " + FinanceCenter.num(o.covenant)
              + "\nİlişki indirimi " + (o.relDiscount > 0 ? "−" + Fmt.f2(o.relDiscount * 100) + " puan" : "yok")
              + (o.bank == Bank.YESIL ? "\nYenilenebilir amaçlı krediye −0,5 puan" : "")
            : "Teklif yok: " + o.reason;
        Label d = new Label(txt, skin, o.available ? "small" : "bad");
        d.setWrap(true);
        card.add(d).width(236).left().padBottom(6).row();
        TextButton b = new TextButton(selected == o.bank ? "Seçildi" : "Bu teklifi seç", skin, "small");
        b.setDisabled(!o.available);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { if (!b.isDisabled()) { selected = o.bank; key = ""; refresh(); } } });
        card.add(b).width(236).height(28);
        return card;
    }

    private TextButton btn(String text, Runnable r) {
        TextButton b = new TextButton(text, skin, "small");
        b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { r.run(); selected = null; key = ""; refresh(); } });
        return b;
    }

    private TextButton toggle(String text, boolean on, Runnable r) {
        TextButton b = new TextButton(text, skin, "small");
        b.setChecked(on);
        b.setProgrammaticChangeEvents(false);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { r.run(); selected = null; key = ""; refresh(); } });
        return b;
    }
}
