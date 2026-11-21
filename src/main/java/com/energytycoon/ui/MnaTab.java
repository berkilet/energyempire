package com.energytycoon.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.energytycoon.sim.*;

import java.util.function.Consumer;

/**
 * Birlesme ve Satin Alma sekmesi: sirketler tablosu (deger, fiyat, halka acik/kapali, serbest dolasim, payiniz, kurucu payi,
 * not, pazar payi), secili sirket icin eylemler (kurucuya teklif, piyasadan al/sat, cagri, strateji, yonetim kurulu bilgisi),
 * acik teklifler, rekabet otoritesi basvurulari ve taahhutler, satistaki santraller.
 */
public final class MnaTab {
    public final Table root = new Table();
    private final Skin skin;
    private final Consumer<String> action;
    private GameState st;
    private int selected = -1;
    private double icAmount = 50e6;
    private String key = "";

    private static final String[] HEAD = { "Şirket", "Değer", "Hisse", "Durum", "Serbest", "Payınız", "Kurucu", "Not", "Ulusal pay", "" };
    private static final float[] W = { 150, 84, 70, 92, 62, 62, 62, 40, 70, 70 };

    public MnaTab(Skins skins, Consumer<String> action) {
        this.skin = skins.skin; this.action = action;
        root.defaults().left();
    }

    public void invalidate() { key = ""; }
    public void select(int companyIdx) { selected = companyIdx; key = ""; }

    public void update(GameState s) {
        st = s;
        StringBuilder k = new StringBuilder().append(selected).append('|').append(s.hour / 24).append('|').append(Math.round(s.player.cash / 1e6));
        for (Company c : s.companies) k.append(Math.round(c.eq.price * 10)).append(c.eq.listed).append(c.eq.parent).append(Math.round(c.eq.founder / 1e4)).append(c.bankrupt).append(',');
        for (DealBook.TenderOffer t : s.deals.offers) k.append(t.id).append(t.closed).append(t.playerTenders);
        for (DealBook.Review r : s.deals.reviews) k.append(r.id).append(r.decided).append(r.commented);
        for (DealBook.Order o : s.deals.orders) k.append(o.target).append(Math.round(o.remaining / 1e4));
        if (k.toString().equals(key)) return;
        key = k.toString();
        root.clear();
        root.defaults().left();
        Company p = s.player;
        int pi = s.companies.indexOf(p);
        Label intro = new Label("Pay oranına göre haklar: %0–20 finansal yatırım (temettü) · %20–50 iştirak (kâr payı; %25'ten itibaren yönetim kurulu: yatırım planı ve kredi takvimi görünür) · "
            + "%50 üstü kontrol: zorunlu pay alım teklifi, rekabet onayından sonra bağlı ortaklık · %90 üstü ortaklıktan çıkarma ve birleşme. Kamu Üretim satın alınamaz.", skin, "dim");
        intro.setWrap(true);
        root.add(intro).width(780).padBottom(6).row();
        // sirketler tablosu
        root.add(UiKit.section(skin, "ŞİRKETLER")).row();
        Table h = new Table();
        for (int i = 0; i < HEAD.length; i++) h.add(head(HEAD[i], i == 0 ? Align.left : Align.right)).width(W[i]).padRight(4);
        root.add(h).row();
        // grup sirasi: ana sirket, hemen altinda bagli ortakliklari
        java.util.List<Integer> order = new java.util.ArrayList<>();
        for (int i = 0; i < s.companies.size(); i++) {
            Company c = s.companies.get(i);
            if (c.neutral || c.eq.merged || c.eq.parent >= 0) continue;
            order.add(i);
            for (int j = 0; j < s.companies.size(); j++) if (s.companies.get(j).eq.parent == i && !s.companies.get(j).eq.merged) order.add(j);
        }
        for (int i : order) {
            Company c = s.companies.get(i);
            Table row = new Table(skin);
            row.setBackground(i == selected ? "rowSel" : c.player ? "card" : "row");
            row.pad(3, 6, 3, 6);
            String status = c.eq.merged ? "Birleşti" : c.bankrupt ? "İflas" : c.eq.parent >= 0 ? "Bağlı ort. (" + s.companies.get(c.eq.parent).name + ")" : c.eq.listed ? "Halka açık" : "Halka kapalı";
            String[] cells = { c.player ? c.name + " (siz)" : c.eq.parent >= 0 ? "↳ " + c.name : c.name, Fmt.money(s.capital.value(c)), c.eq.listed ? CapitalTab.px(c.eq.price) : "—", status,
                c.eq.listed ? "%" + Math.round(c.eq.freePct() * 100) : "—", c.player ? "—" : pct0(s.mna.stake(p, c)), "%" + Math.round(c.eq.founderPct() * 100),
                c.bankrupt ? "—" : s.finance.rating(c).name(), pct0(s.competition.nationalShare(java.util.Set.of(c))) };
            for (int j = 0; j < cells.length; j++) {
                Label l = new Label(cells[j], skin, "small"); l.setWrap(true); l.setAlignment(j == 0 ? Align.left : Align.right);
                row.add(l).width(W[j]).padRight(4);
            }
            if (!c.player && !c.bankrupt) { final int idx = i; row.add(step(i == selected ? "Seçili" : "Seç", () -> selected = idx)).width(W[9]).height(26); }
            else row.add().width(W[9]);
            root.add(row).padBottom(2).row();
        }
        if (selected >= 0 && selected < s.companies.size() && selected != pi && !s.companies.get(selected).bankrupt) detail(s, s.companies.get(selected));
        offers(s);
        reviews(s);
        listings(s);
    }

    private void detail(GameState s, Company t) {
        Company p = s.player;
        Equity e = t.eq;
        root.add(UiKit.section(skin, t.name.toUpperCase(new java.util.Locale("tr")) + " — EYLEMLER")).padTop(8).row();
        double stake = s.mna.stake(p, t);
        boolean ctrl = s.mna.controls(p, t);
        StringBuilder info = new StringBuilder();
        info.append("Temel değer ").append(Fmt.money(e.fundamental)).append(" (").append(CapitalTab.px(e.fundamental / Math.max(1, e.shares))).append(" / hisse)")
            .append(" · kredi notu ").append(s.finance.rating(t)).append(" · ").append(s.mna.distressed(t) ? "zor durumda" : "finansal durumu normal")
            .append(" · payınız %").append(CapitalTab.p1(stake));
        if (stake >= FinanceBalance.ASSOCIATE) info.append(" (iştirak: aylık kâr payı ").append(Fmt.money(stake * t.lastNet)).append(")");
        if (s.mna.order(p, t) != null) info.append("\nAlım emri: kalan ").append(CapitalTab.shares(s.mna.order(p, t).remaining)).append(" hisse (günde en fazla %2)");
        DealBook.Review r = s.competition.pendingReview(t);
        if (r != null) info.append("\nRekabet incelemesi sürüyor: ").append(s.companies.get(r.acquirer).name).append(" · ").append(Math.max(0, (r.endHour - s.hour) / 24)).append(" gün kaldı");
        Label il = new Label(info.toString(), skin, "small"); il.setWrap(true);
        root.add(il).width(780).padBottom(4).row();
        Table b = new Table();
        int ti = s.companies.indexOf(t);
        if (!ctrl) {
            String fb = s.mna.founderOfferBlocker(p, t, Math.min(t.eq.founder, t.eq.shares * 0.01));
            b.add(button("Kurucuya teklif…", "deal:founder:" + ti, fb == null)).height(30).padRight(4);
            b.add(button("Piyasadan pay al…", "deal:market:" + ti, t.eq.listed && !t.eq.merged)).height(30).padRight(4);
            b.add(button("Pay alım teklifi (çağrı)…", "deal:tender:" + ti, t.eq.listed && t.eq.founderPct() < FinanceBalance.CONTROL
                && (s.mna.openOffer(t) == null || s.mna.openOffer(t).bidder != s.companies.indexOf(p)))).height(30).padRight(4);
        }
        if (stake > 0 && t.eq.listed) b.add(button("Payımı sat…", "deal:sell:" + ti, true)).height(30);
        root.add(b).padBottom(4).row();
        if (!t.eq.listed && !ctrl) {
            Label n = new Label("Halka kapalı şirkette yalnızca kurucuya teklif götürülebilir.", skin, "dim");
            root.add(n).row();
        }
        if (stake >= FinanceBalance.BOARD_SEAT) {
            StringBuilder bd = new StringBuilder("YÖNETİM KURULU (%25+): ");
            int building = 0; double mw = 0;
            for (Plant pl : s.plants) if (pl.owner == t && !pl.operational()) { building++; mw += pl.capacityMW(); }
            bd.append("planlanan yatırımlar: ").append(building).append(" santral inşa halinde (").append(Math.round(mw)).append(" MW)");
            if (s.capital.ipoPreparing(t)) bd.append(", halka arz hazırlığı");
            double[] sch = s.finance.schedule12(t);
            double sum = 0; for (double v : sch) sum += v;
            bd.append(" · 12 aylık kredi takvimi toplam ").append(Fmt.money(sum)).append(" (aylık: ");
            for (int m = 0; m < 12; m++) bd.append(m == 0 ? "" : ", ").append(Math.round(sch[m] / 1e6));
            bd.append(" M$)");
            Label bl = new Label(bd.toString(), skin, "small"); bl.setWrap(true);
            root.add(bl).width(780).padBottom(4).row();
        }
        if (ctrl) {
            Table sb = new Table();
            sb.add(new Label("Bağlı ortaklık stratejisi:", skin, "small")).padRight(6);
            for (int k = 0; k < 3; k++) sb.add(toggleIcon(Mna.STRATEGY[k], STRAT_ICONS[k], t.eq.strategy == k, "strategy:" + ti + ":" + k)).height(28).padRight(4);
            root.add(sb).padBottom(2).row();
            Label sl = new Label("Büyüme: rakip YZ gibi yatırım yapar, kârın %" + FinanceBalance.SUB_DIVIDEND[0] + "'ini temettü öder · Temkinli: yalnızca düşük kaldıraç ve bol nakitle yatırım, %"
                + FinanceBalance.SUB_DIVIDEND[1] + " temettü · Nakit üret: yeni santral yok, %" + FinanceBalance.SUB_DIVIDEND[2] + " temettü. Temettünün payınıza düşen kısmı nakit olarak size gelir. "
                + "Kredi notunuz ve banka taahhütleriniz bağlı ortaklıklarla birlikte (konsolide) hesaplanır; üretimi pazar payınıza eklenir.", skin, "dim");
            sl.setWrap(true);
            root.add(sl).width(780).padBottom(4).row();
            // grup ici nakit
            double wmax = s.mna.subWithdrawable(t), bal = t.eq.icBalance;
            Label cl = new Label("GRUP İÇİ NAKİT · " + t.name + " kasası " + Fmt.money(t.cash) + " (rezerv korunarak çekilebilir " + Fmt.money(wmax) + ")"
                + (Math.abs(bal) >= 1 ? " · grup içi borç: " + (bal > 0 ? "siz " + t.name + "'e " : t.name + " size ") + Fmt.money(Math.abs(bal)) + " borçlu" : "")
                + "\nYönetim kurulunu siz atadığınız için kasalar arasında para aktarabilirsiniz. Aktarım grup içi borç olarak kaydedilir; borçlu taraf her ay politika faizi + 1 puan öder "
                + "(azınlık ortakların hakkı korunur). Özel temettü bütün ortaklara paylarıyla orantılı dağıtılır.", skin, "small");
            cl.setWrap(true);
            root.add(cl).width(780).padTop(4).row();
            Table ct = new Table();
            ct.add(step("−", () -> icAmount = Math.max(10e6, icAmount - 50e6))).size(30, 28);
            ct.add(new Label(Fmt.money(icAmount), skin, "value")).width(90).center();
            ct.add(step("+", () -> icAmount += 50e6)).size(30, 28).padRight(8);
            ct.add(button("Bağlı ortaklıktan çek", "ic:" + ti + ":" + icAmount, icAmount <= wmax + 1)).height(28).padRight(4);
            ct.add(button("Bağlı ortaklığa gönder", "ic:" + ti + ":" + (-icAmount), s.player.cash >= icAmount)).height(28).padRight(4);
            if (bal > 1) ct.add(button("Borcu öde", "ic:" + ti + ":" + (-Math.min(bal, s.player.cash)), s.player.cash > 1)).height(28).padRight(4);
            if (bal < -1) ct.add(button("Alacağı tahsil et", "ic:" + ti + ":" + Math.min(-bal, wmax), wmax > 1)).height(28).padRight(4);
            ct.add(button("Özel temettü", "sdiv:" + ti + ":" + icAmount, true)).height(28);
            root.add(ct).padBottom(4).row();
        }
    }

    private void offers(GameState s) {
        boolean any = false;
        for (DealBook.TenderOffer t : s.deals.offers) if (!t.closed) { any = true; break; }
        if (!any) return;
        root.add(UiKit.section(skin, "AÇIK PAY ALIM TEKLİFLERİ")).padTop(8).row();
        int pi = s.companies.indexOf(s.player);
        for (DealBook.TenderOffer t : s.deals.offers) {
            if (t.closed) continue;
            Company b = s.companies.get(t.bidder), tg = s.companies.get(t.target);
            Table row = new Table(skin); row.setBackground("row"); row.pad(3, 6, 3, 6);
            String txt = (t.mandatory ? "Zorunlu teklif: " : t.knight ? "Karşı teklif (beyaz şövalye): " : "Çağrı: ") + b.name + " → " + tg.name + " · hisse başı " + CapitalTab.px(t.price)
                + " (prim %" + Math.round((t.price / t.prePrice - 1) * 100) + ", etkin prim %" + Math.round(s.mna.effectivePremium(t) * 100)
                + ", tahmini kabul %" + Math.round(Mna.acceptance(s.mna.effectivePremium(t)) * 100) + ") · " + Math.max(0, (t.endHour - s.hour) / 24) + " gün kaldı";
            Label l = new Label(txt, skin, "small"); l.setWrap(true);
            row.add(l).width(600).padRight(6);
            if (tg.eq.held(pi) > 0 && t.bidder != pi) row.add(toggle(t.playerTenders ? "Paylarım teklife verilecek" : "Paylarımı teklife ver", t.playerTenders, "tenderme:" + t.id)).height(26);
            if (tg.player) row.add(button("Savunma…", "defense", true)).height(26);
            root.add(row).padBottom(2).row();
        }
    }

    private void reviews(GameState s) {
        root.add(UiKit.section(skin, "REKABET OTORİTESİ")).padTop(8).row();
        boolean any = false;
        for (DealBook.Review r : s.deals.reviews) {
            if (r.decided && s.hour - r.endHour > Balance.HOURS_PER_YEAR) continue;
            any = true;
            Company a = s.companies.get(r.acquirer), t = s.companies.get(r.target);
            Table row = new Table(skin); row.setBackground("row"); row.pad(3, 6, 3, 6);
            String phase = r.decided ? "Karar: " + (r.outcome == Competition.REJECT || r.outcome == Competition.CONDITIONAL && !r.commitmentAccepted ? "ret" : r.outcome == Competition.CONDITIONAL ? "koşullu onay" : "onay")
                : (r.finalPhase ? "Nihai inceleme" : "Ön inceleme") + " · " + Math.max(0, (r.endHour - s.hour) / 24) + " gün kaldı · beklenen: " + Competition.OUTCOME[r.outcome];
            String txt = a.name + " → " + t.name + " · " + phase + " · en yüksek bölgesel birleşik pay %" + Math.round(r.maxShare * 100) + " · ulusal %" + Math.round(r.national * 100)
                + " · değer oranı " + FinanceCenter.num(r.valueRatio) + (r.exempt ? " (ölçek muafiyeti)" : "") + "\n" + r.reason;
            Label l = new Label(txt, skin, "small"); l.setWrap(true);
            row.add(l).width(640).padRight(6);
            boolean party = s.competition.sameGroup(a, s.player) || s.competition.sameGroup(t, s.player);
            if (!r.decided && !party) row.add(button(r.commented ? "Görüş bildirildi" : "Görüş bildir (+1 ay)", "comment:" + r.id, !r.commented)).height(26);
            root.add(row).padBottom(2).row();
        }
        for (DealBook.Commitment c : s.deals.commitments) {
            if (c.done && s.hour - c.deadline > Balance.HOURS_PER_YEAR) continue;
            any = true;
            Company co = s.companies.get(c.company);
            String txt = "Satış taahhüdü: " + co.name + " · " + Math.round(c.requiredMW) + " MW · kalan " + Math.round(s.competition.commitmentLeft(c)) + " MW · son tarih " + FinanceCenter.date(c.deadline)
                + (c.done ? " · yerine getirildi" : c.enforced ? " · süresi doldu: otorite ihaleye çıkardı, ceza kesildi" : "");
            Label l = new Label(txt, skin, c.enforced ? "bad" : "small"); l.setWrap(true);
            root.add(l).width(780).padBottom(2).row();
        }
        if (!any) root.add(new Label("Açık başvuru veya taahhüt yok.", skin, "dim")).row();
        double[] sc = s.competition.regulatedScore();
        if (sc != null) {
            Label l = new Label("KAMU HİZMETİ MODU (" + s.companies.get(s.deals.regulated).name + "): arz güvenliği %" + Math.round(sc[0] * 100) + " · karbon yoğunluğu "
                + Fmt.f2(sc[1]) + " t/MWh · tüketici fiyatı $" + Fmt.f1(sc[2]) + "/MWh · bileşik skor " + Math.round(sc[3]) + "/100", skin, "default");
            l.setWrap(true);
            root.add(l).width(780).padTop(4).row();
        }
        if (s.player.eq.dominant) {
            Label l = new Label("Hakim durumdasınız (bir pazarda payınız %50'nin üstünde): o pazardaki satış fiyatınız ortalama üretim maliyetiniz + %15 ile sınırlı.", skin, "bad");
            l.setWrap(true);
            root.add(l).width(780).padTop(4).row();
        }
    }

    private void listings(GameState s) {
        boolean any = false;
        for (Listing l : s.sales.open()) if (l.seller != s.player) { any = true; break; }
        root.add(UiKit.section(skin, "SATIŞTAKİ SANTRALLER VE TRAFO MERKEZLERİ")).padTop(8).row();
        if (!any) { root.add(new Label("Şu an satışta varlık yok. Kendi santralinizi Varlıklarım menüsünden satışa çıkarabilirsiniz.", skin, "dim")).row(); return; }
        for (Listing l : s.sales.open()) {
            if (l.seller == s.player) continue;
            Table row = new Table(skin); row.setBackground("row"); row.pad(3, 6, 3, 6);
            Label lb = new Label(l.seller.name + " · " + l.assetName() + " · " + Fmt.money(l.price) + " · " + Math.max(0, (l.expiresHour - s.hour) / 24) + " gün", skin, "small");
            lb.setWrap(true);
            row.add(lb).width(600).padRight(6);
            row.add(button("İncele", "sale:" + l.id, true)).height(26).padRight(3);
            row.add(button("Satın al", "buy:" + l.id, s.player.cash >= l.price)).height(26);
            root.add(row).padBottom(2).row();
        }
    }

    static String pct0(double v) { return v <= 0 ? "—" : "%" + Math.round(v * 100); }

    private Label head(String t, int align) { Label l = new Label(t, skin, "dim"); l.setAlignment(align); l.setWrap(true); return l; }

    private TextButton button(String text, String act, boolean enabled) {
        TextButton b = new TextButton(text, skin, "small");
        b.setDisabled(!enabled);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent ev, Actor a) { if (!b.isDisabled()) { action.accept(act); key = ""; } } });
        return b;
    }

    private static final String[] STRAT_ICONS = { "grp_growth", "grp_cautious", "grp_cashcow" };

    private com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton toggleIcon(String text, String icon, boolean on, String act) {
        com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton b = UiKit.iconButton(skin, text, icon, "small", 20, 21);
        b.setChecked(on);
        b.setProgrammaticChangeEvents(false);
        b.addListener(new ChangeListener() { public void changed(ChangeEvent ev, Actor a) { action.accept(act); key = ""; } });
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
