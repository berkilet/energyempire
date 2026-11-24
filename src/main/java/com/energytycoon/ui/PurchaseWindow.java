package com.energytycoon.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.energytycoon.sim.*;

import java.util.List;
import java.util.function.Consumer;

/**
 * Satin alma ve kurulum karari: model ve yer secildikten sonra acilir. Ustte projenin o sahadaki ozeti (rakiplerin
 * yatirim hesabiyla ayni degerlendirme), altta iki secenek yan yana: oz kaynakla ya da krediyle (ECA veya ticari).
 * Pencere acikken oyun duraklatilir (GameScreen).
 */
public final class PurchaseWindow extends Window {
    /** Oyuncunun karari. */
    public static final class Choice {
        public enum Mode { CASH, ECA, COMMERCIAL }
        public Mode mode;
        public Bank bank;
        public int freqMonths, years;
        public double loanAmount;
    }

    private final Skin skin;
    private final Table body = new Table();
    private GameState s;
    private Equipment e;
    private int x, y;
    private Consumer<Choice> onDecide;
    private Runnable onCancel;

    // kredi kartindaki secimler (pencere kapansa da hatirlanir)
    private boolean useEca = true;
    private Bank bank = Bank.KIYI;
    private int freq = 6, years = 7;
    private double share = 0.7;

    public PurchaseWindow(Skin skin) {
        super("Satın Alma ve Kurulum", skin);
        this.skin = skin;
        setMovable(true);
        add(body).pad(4);
        setVisible(false);
    }

    public void show(GameState s, Equipment e, int x, int y, Consumer<Choice> onDecide, Runnable onCancel) {
        this.s = s; this.e = e; this.x = x; this.y = y; this.onDecide = onDecide; this.onCancel = onCancel;
        if (!Finance.ecaEligible(e)) useEca = false;
        rebuild();
        setVisible(true);
        toFront();
    }

    public void cancel() {
        if (!isVisible()) return;
        setVisible(false);
        if (onCancel != null) onCancel.run();
    }

    private Label l(String t, String style) { return new Label(t, skin, style); }
    private Label wrap(String t, String style, float w) { Label lb = l(t, style); lb.setWrap(true); lb.setWidth(w); return lb; }

    private void rebuild() {
        body.clear();
        Company p = s.player;
        City city = nearestCity();
        RivalStrategy.Evaluation ev = RivalStrategy.evaluate(s, p, e, city, x, y);

        // ---- proje ozeti
        Table head = new Table(skin);
        head.setBackground("card");
        head.pad(10);
        head.add(UiKit.iconLabel(skin, "sup_" + e.supplier.id, l(e.fullName() + " · " + Fmt.mw(e.capacityMW), "title"), 26, 28)).left().row();
        int dist = (int) Math.round(Math.hypot(city.x - x, city.y - y));
        head.add(wrap("Yer: " + x + "," + y + " · en yakın şehir " + city.name + " (" + dist + " kare) · inşaat " + e.buildDays()
            + " gün · garanti " + e.supplier.warrantyYears + " yıl · " + e.supplier.origin + " menşeli", "small", 640)).width(640).left().padTop(4).row();
        String site = e.type.renewable ? "Bu sahada beklenen kapasite faktörü %" + Math.round(ev.cf1 * 100)
            : "Beklenen çalışma oranı %" + Math.round(ev.cf1 * 100) + " (yakıt, karbon ve fiyata göre)";
        head.add(wrap(site + " · yıllık üretim ≈ " + Math.round(ev.mwh1 / 1000) + " GWh · ilk yıl işletme kârı ≈ " + Fmt.money(ev.ebitda1)
            + (ev.capacity1 > 1 ? " (kapasite ödemesi " + Fmt.money(ev.capacity1) + " dahil)" : ""), "small", 640)).width(640).left().padTop(2).row();
        Label idx = l("Proje getirisi (NBD / yatırım): " + (ev.index >= 0 ? "+" : "") + Fmt.f2(ev.index)
            + (ev.index >= 0 ? "  · ömrü boyunca sermaye maliyetinin üzerinde kazandırması beklenir" : "  · bugünkü beklentilerle yatırımı geri kazandırmaz"),
            ev.index >= 0 ? "value" : "valueBad");
        idx.setWrap(true);
        head.add(idx).width(640).left().padTop(4).row();
        body.add(head).colspan(2).growX().padBottom(8).row();

        // ---- oz kaynak karti
        double capex = e.capex();
        boolean cashOk = p.cash >= capex;
        Table own = card("ÖZ KAYNAKLA", "coin");
        own.add(wrap("Bedel " + Fmt.money(capex) + " sipariş anında peşin ödenir. Borç ve faiz yok; nakdiniz azalır, "
            + "kredi limitiniz başka yatırımlar için boş kalır.", "small", 300)).width(300).left().padTop(4).row();
        own.add(l("Nakit: " + Fmt.money(p.cash) + " → " + Fmt.money(p.cash - capex), cashOk ? "value" : "valueBad")).left().padTop(6).row();
        if (!cashOk) own.add(wrap("Nakit yetmiyor (" + Fmt.money(capex - p.cash) + " eksik). Krediyle kurabilirsiniz.", "dim", 300)).width(300).left().padTop(2).row();
        own.add().expandY().row();
        TextButton ownBtn = button("Öz kaynakla kur", "accent", cashOk, () -> decide(Choice.Mode.CASH, 0));
        own.add(ownBtn).width(300).height(34).padTop(8);

        // ---- kredi karti
        Table loan = card("KREDİ İLE", "fin_loans");
        Table typeRow = new Table();
        boolean ecaOk = Finance.ecaEligible(e);
        typeRow.add(toggle("ECA (bedelin %" + Math.round(FinanceBalance.ECA_SHARE * 100) + "'i)", useEca, ecaOk, () -> useEca = true)).height(26).padRight(4);
        typeRow.add(toggle("Ticari kredi", !useEca, true, () -> useEca = false)).height(26);
        loan.add(typeRow).left().padTop(4).row();
        if (!ecaOk) loan.add(l("ECA yok: " + e.supplier.origin + " tedarikçisi ihracat kredisi sunmuyor", "dim")).left().padTop(2).row();
        Table bankRow = new Table();
        for (Bank b : Bank.values()) bankRow.add(toggle(b.label, bank == b, true, () -> bank = b)).height(26).padRight(3);
        loan.add(bankRow).left().padTop(4).row();
        Table freqRow = new Table();
        freqRow.add(toggle("6 ayda bir", freq == 6, true, () -> freq = 6)).height(26).padRight(3);
        freqRow.add(toggle("yılda bir", freq == 12, true, () -> freq = 12)).height(26).padRight(10);
        if (!useEca) {
            freqRow.add(toggle("borç %50", share == 0.5, true, () -> share = 0.5)).height(26).padRight(3);
            freqRow.add(toggle("%70", share == 0.7, true, () -> share = 0.7)).height(26);
        }
        loan.add(freqRow).left().padTop(4).row();
        if (!useEca) {
            Table yr = new Table();
            yr.add(l("Vade:", "dim")).padRight(6);
            for (int v : new int[] { 3, 5, 7, 10 }) if (v <= bank.maxYears()) yr.add(toggle(v + " yıl", years == v, true, () -> years = v)).height(26).padRight(3);
            loan.add(yr).left().padTop(4).row();
            years = Math.min(years, bank.maxYears());
        }

        String detail, refusal = null;
        double cashNeed, amount = 0, annualService;
        if (useEca && ecaOk) {
            refusal = s.finance.ecaRefusal(p, e, bank);
            Finance.EcaQuote q = s.finance.ecaQuote(p, e, bank, freq);
            amount = q.amount;
            cashNeed = s.finance.ecaCashNeed(p, e, bank, freq);
            annualService = q.installment * 12.0 / freq;
            detail = "Kredi " + Fmt.money(q.amount) + " · faiz " + Macro.pct(q.rate) + " (sabit) · " + q.years + " yıl\n"
                + "Ödemesiz dönem " + q.graceMonths + " ay · taksit " + Fmt.money(q.installment) + " / " + (freq == 6 ? "6 ay" : "yıl") + "\n"
                + "Sigorta primi + banka masrafı " + Fmt.money(q.premium + q.bankFee) + "\n"
                + "Peşin öz kaynak: " + Fmt.money(cashNeed);
        } else {
            amount = Math.round(capex * share / 1e5) * 1e5;
            Plant purpose = new Plant(); purpose.type = e.type; purpose.equipment = e;
            List<Finance.Offer> offers = s.finance.commercialOffers(p, amount, years, freq, false, purpose);
            Finance.Offer o = offers.stream().filter(of -> of.bank == bank).findFirst().orElse(null);
            refusal = o == null ? "Teklif yok" : o.available ? null : o.reason;
            cashNeed = capex - amount + (o == null ? 0 : o.upfront);
            annualService = o == null ? 0 : o.installment * 12.0 / freq;
            detail = o == null ? "" : "Kredi " + Fmt.money(amount) + " · faiz " + Macro.pct(o.rate) + " (değişken) · " + years + " yıl\n"
                + "Taksit " + Fmt.money(o.installment) + " / " + (freq == 6 ? "6 ay" : "yıl") + " · masraf " + Fmt.money(o.upfront) + "\n"
                + "Kredi limitinden düşer · taahhüt: Net borç/FAVÖK ≤ " + Fmt.f1(o.covenant) + "\n"
                + "Peşin öz kaynak: " + Fmt.money(cashNeed);
        }
        double dscr = annualService > 0 ? ev.ebitda1 / annualService : Double.POSITIVE_INFINITY;
        loan.add(wrap(detail, "small", 300)).width(300).left().padTop(6).row();
        String dscrText = Double.isInfinite(dscr) ? "—" : dscr < 0 ? "negatif (ilk yıl işletme zararı)" : Fmt.f2(dscr) + (dscr < 1.2 ? "  · zayıf (bankalar ≥ 1,20 ister)" : "");
        loan.add(l("Geri ödeme gücü (DSCR) ≈ " + dscrText, dscr < 1.2 ? "valueBad" : "value")).left().padTop(4).row();
        boolean loanCashOk = p.cash >= cashNeed;
        String why = refusal != null ? refusal : !loanCashOk ? "Peşin öz kaynak için nakit yetmiyor (" + Fmt.money(cashNeed - p.cash) + " eksik)" : null;
        if (why != null) loan.add(wrap("✗ " + why, "dim", 300)).width(300).left().padTop(2).row();
        loan.add().expandY().row();
        final double amt = amount;
        loan.add(button("Kredi ile kur", "default", why == null, () -> decide(useEca && ecaOk ? Choice.Mode.ECA : Choice.Mode.COMMERCIAL, amt))).width(300).height(34).padTop(8);

        body.add(own).top().growY().padRight(8);
        body.add(loan).top().growY().row();

        Table foot = new Table();
        foot.add(button("Vazgeç (yer seçimine dön)", "default", true, this::cancel)).width(240).height(30);
        foot.add(l("Karar verirken oyun duraklatılır.", "dim")).padLeft(12);
        body.add(foot).colspan(2).padTop(8);
        pack();
        if (getStage() != null) setPosition(Math.round((getStage().getWidth() - getWidth()) / 2), Math.round((getStage().getHeight() - getHeight()) / 2));
    }

    private City nearestCity() {
        City best = s.cities.get(0); double bd = Double.MAX_VALUE;
        for (City c : s.cities) { double d = Math.hypot(c.x - x, c.y - y); if (d < bd) { bd = d; best = c; } }
        return best;
    }

    private Table card(String title, String icon) {
        Table t = new Table(skin);
        t.setBackground("card");
        t.pad(10);
        t.top().left();
        t.add(UiKit.iconLabel(skin, icon, l(title, "title"), 24, 26)).left().row();
        return t;
    }

    private void decide(Choice.Mode mode, double amount) {
        Choice c = new Choice();
        c.mode = mode; c.bank = bank; c.freqMonths = freq; c.years = years; c.loanAmount = amount;
        setVisible(false);
        if (onDecide != null) onDecide.accept(c);
    }

    private TextButton button(String text, String style, boolean enabled, Runnable r) {
        TextButton b = new TextButton(text, skin, style);
        b.setDisabled(!enabled);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent ev, Actor a) { if (!b.isDisabled()) r.run(); } });
        return b;
    }

    private TextButton toggle(String text, boolean on, boolean enabled, Runnable r) {
        TextButton b = new TextButton(text, skin, "small");
        b.setChecked(on);
        b.setDisabled(!enabled);
        b.setProgrammaticChangeEvents(false);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent ev, Actor a) { if (!b.isDisabled()) r.run(); rebuild(); } });
        return b;
    }
}
