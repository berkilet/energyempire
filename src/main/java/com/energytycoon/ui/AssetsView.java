package com.energytycoon.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.energytycoon.sim.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Varliklarim: oyuncunun santralleri, hatlari, trafo merkezleri, UŞİ basvurulari ve kazandigi ihaleler.
 * Her sekmede ozet satiri, sutuna gore siralama, "yalnizca sorunlu" filtresi; satira tiklaninca kamera varliga gider.
 */
public final class AssetsView {
    public enum Tab {
        PLANTS("Santraller"), LINES("Hatlar"), SUBSTATIONS("Trafo Merkezleri"), OPERATOR("UŞİ Başvurularım"), TENDERS("İhaleler");
        final String label;
        Tab(String l) { label = l; }
    }

    /** Bir tablo satiri: gosterilen metinler, siralama anahtarlari, sorunlu mu, tiklaninca eylem. */
    private static final class Row {
        final String[] text; final Comparable<?>[] key; boolean problem; String action;
        final List<String[]> buttons = new ArrayList<>(); // {etiket, eylem, "1"/"0"}
        Row(int n) { text = new String[n]; key = new Comparable<?>[n]; }
        void set(int i, String t, Comparable<?> k) { text[i] = t; key[i] = k; }
    }

    public final Table root = new Table();
    private final Skin skin;
    private final Consumer<String> action;
    private Tab tab = Tab.PLANTS;
    private int sortCol = 0;
    private boolean sortDesc;
    private boolean onlyProblems;
    private final Table tabs = new Table(), header = new Table(), body = new Table();
    private final Label summary;
    private final CheckBox problems;
    private final ScrollPane scroll;
    private String key = "";
    private float maxHeight = 500;
    private static final float GAP = 12;

    public AssetsView(Skins skins, Consumer<String> action) {
        this.skin = skins.skin;
        this.action = action;
        root.defaults().left();
        ButtonGroup<TextButton> g = new ButtonGroup<>();
        for (Tab t : Tab.values()) {
            TextButton b = new TextButton(t.label, skin, "small");
            b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { if (b.isChecked()) setTab(t); } });
            g.add(b);
            tabs.add(b).height(30).padRight(3);
            if (t == tab) b.setChecked(true);
        }
        root.add(tabs).padBottom(6).row();
        Table bar = new Table();
        summary = new Label("", skin, "small");
        summary.setWrap(true);
        problems = new CheckBox("  Yalnızca sorunlu olanlar", skin, "toggle");
        problems.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { onlyProblems = problems.isChecked(); key = ""; } });
        bar.add(summary).width(560).left().padRight(10);
        bar.add(problems).right();
        root.add(bar).padBottom(6).row();
        root.add(header).left().row();
        scroll = new ScrollPane(body, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(false, false);
        scroll.setOverscroll(false, false);
        root.add(scroll).left().growX();
    }

    private boolean settingTab;

    public void setTab(Tab t) {
        if (tab != t) { tab = t; sortCol = 0; sortDesc = false; }
        key = "";
        if (settingTab) return;
        settingTab = true; // setChecked dinleyiciyi tetikler; ic ice cagri olmasin
        for (int i = 0; i < tabs.getChildren().size; i++) ((TextButton) tabs.getChildren().get(i)).setChecked(Tab.values()[i] == t);
        settingTab = false;
    }

    /** Pencerenin en fazla ekran yuksekliginin %70'i kadar olmasi icin listeye ayrilan yukseklik. */
    public void setMaxHeight(float h) { if (Math.abs(h - maxHeight) > 1) { maxHeight = h; key = ""; } }

    // ------------------------------------------------------------ sutunlar
    private record Col(String name, float width, int align) {}

    private static final Col[] PLANT_COLS = {
        new Col("Ad", 170, Align.left), new Col("Tür", 92, Align.left), new Col("MW", 56, Align.right), new Col("En yakın şehir", 104, Align.left),
        new Col("Durum", 150, Align.left), new Col("KF (30 g)", 70, Align.right), new Col("Kâr (YB)", 84, Align.right),
        new Col("Yaş / kalan", 92, Align.right), new Col("Garanti", 82, Align.right), new Col("Yükseltmeler", 150, Align.left) };
    private static final Col[] LINE_COLS = {
        new Col("Uçlar", 250, Align.left), new Col("Kademe", 70, Align.left), new Col("Uzunluk", 70, Align.right), new Col("Kapasite", 80, Align.right),
        new Col("Doluluk", 70, Align.right), new Col("Durum", 190, Align.left), new Col("Sahip", 150, Align.left) };
    private static final Col[] TM_COLS = {
        new Col("Konum", 210, Align.left), new Col("Seviye", 70, Align.left), new Col("Trafo", 80, Align.right), new Col("Üye santral", 86, Align.right),
        new Col("Doluluk", 70, Align.right), new Col("Erişim", 130, Align.left), new Col("Erişim geliri (ay)", 120, Align.right) };
    private static final Col[] OP_COLS = {
        new Col("Hat", 250, Align.left), new Col("Durum", 150, Align.left), new Col("Tahmin", 90, Align.right), new Col("Yıllık bedel", 96, Align.right),
        new Col("", 260, Align.left) };
    private static final Col[] TENDER_COLS = {
        new Col("Şehir", 120, Align.left), new Col("MW", 60, Align.right), new Col("Fiyat", 90, Align.right), new Col("Durum", 160, Align.left),
        new Col("Yükümlülük", 230, Align.left), new Col("Ödenen gelir", 100, Align.right) };

    private Col[] cols() {
        return switch (tab) { case PLANTS -> PLANT_COLS; case LINES -> LINE_COLS; case SUBSTATIONS -> TM_COLS; case OPERATOR -> OP_COLS; case TENDERS -> TENDER_COLS; };
    }

    // ------------------------------------------------------------ guncelleme
    public void update(GameState s) {
        List<Row> rows = rows(s);
        String sum = summary(s, rows);
        if (onlyProblems) rows.removeIf(r -> !r.problem);
        final int sc = sortCol;
        Comparator<Row> cmp = (a, b) -> compare(a.key[sc], b.key[sc]);
        rows.sort(sortDesc ? cmp.reversed() : cmp);
        StringBuilder k = new StringBuilder(tab.name()).append(sortCol).append(sortDesc).append(onlyProblems).append(sum);
        for (Row r : rows) { for (String t : r.text) k.append(t).append('|'); for (String[] b : r.buttons) k.append(b[0]).append(b[2]); }
        if (k.toString().equals(key)) return;
        key = k.toString();
        summary.setText(sum);
        buildHeader();
        body.clear();
        body.top().left();
        Col[] cs = cols();
        if (rows.isEmpty()) body.add(new Label(onlyProblems ? "Sorunlu varlık yok." : emptyText(), skin, "small")).left().pad(6);
        for (Row r : rows) {
            Table row = new Table(skin);
            row.setBackground(r.problem ? "rowHover" : "row");
            row.pad(3, 6, 3, 6);
            for (int i = 0; i < cs.length; i++) {
                if (tab == Tab.OPERATOR && i == cs.length - 1) {
                    Table bt = new Table();
                    for (String[] b : r.buttons) {
                        TextButton tb = new TextButton(b[0], skin, "small");
                        tb.setDisabled("0".equals(b[2]));
                        tb.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { if (!tb.isDisabled()) action.accept(b[1]); } });
                        bt.add(tb).height(28).padRight(4);
                    }
                    row.add(bt).width(cs[i].width).left();
                    continue;
                }
                Label l = new Label(r.text[i] == null ? "" : r.text[i], skin, "small");
                l.setWrap(true); // dar sutunda metin kirpilmaz, satir kayar
                l.setAlignment(cs[i].align);
                row.add(l).width(cs[i].width).padRight(GAP);
            }
            if (r.action != null) {
                row.setTouchable(Touchable.enabled);
                final String act = r.action;
                row.addListener(new ClickListener() { @Override public void clicked(InputEvent e, float x, float y) { if (!(e.getTarget() instanceof TextButton) && !(e.getTarget().getParent() instanceof TextButton)) action.accept(act); } });
            }
            body.add(row).left().padBottom(2).row();
        }
        body.pack();
        float rowsH = body.getPrefHeight();
        root.getCell(scroll).height(Math.min(rowsH + 4, Math.max(120, maxHeight))).width(totalWidth() + 18);
        root.invalidateHierarchy();
    }

    private String emptyText() {
        return switch (tab) {
            case PLANTS -> "Henüz santraliniz yok. Yatırım menüsünden santral kurun.";
            case LINES -> "Henüz hattınız veya UŞİ başvurunuz yok.";
            case SUBSTATIONS -> "Henüz trafo merkeziniz yok (Yatırım → Bağlantı → Trafo Merkezi).";
            case OPERATOR -> "Henüz UŞİ başvurunuz yok. Hat Çek aracında ikinci uca tıklayınca 'UŞİ'ye başvur' seçeneği çıkar.";
            case TENDERS -> "Kazanılmış ihaleniz yok. Açık ihaleler Yatırım → İhaleler'de.";
        };
    }

    private float totalWidth() {
        float w = 12;
        for (Col c : cols()) w += c.width + GAP;
        return w;
    }

    private void buildHeader() {
        header.clear();
        header.left().padLeft(6);
        Col[] cs = cols();
        for (int i = 0; i < cs.length; i++) {
            final int idx = i;
            String arrow = i == sortCol ? (sortDesc ? " ↓" : " ↑") : "";
            Label l = new Label(cs[i].name + arrow, skin, i == sortCol ? "default" : "dim");
            l.setWrap(true);
            l.setAlignment(cs[i].align);
            if (!cs[i].name.isEmpty()) {
                l.setTouchable(Touchable.enabled);
                l.addListener(new ClickListener() { @Override public void clicked(InputEvent e, float x, float y) {
                    if (sortCol == idx) sortDesc = !sortDesc; else { sortCol = idx; sortDesc = false; }
                    key = "";
                } });
            }
            header.add(l).width(cs[i].width).padRight(GAP).padBottom(3);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static int compare(Comparable a, Comparable b) {
        if (a == null) return b == null ? 0 : -1;
        if (b == null) return 1;
        if (a instanceof String sa && b instanceof String sb) return sa.compareToIgnoreCase(sb);
        return a.compareTo(b);
    }

    // ------------------------------------------------------------ satirlar
    private List<Row> rows(GameState s) {
        s.components();
        return switch (tab) {
            case PLANTS -> plantRows(s);
            case LINES -> lineRows(s);
            case SUBSTATIONS -> tmRows(s);
            case OPERATOR -> opRows(s);
            case TENDERS -> tenderRows(s);
        };
    }

    static String date(long hour) {
        return GameState.MONTH_NAMES[(int) ((hour / Balance.HOURS_PER_MONTH) % 12)].substring(0, 3) + " " + (Balance.START_YEAR + hour / Balance.HOURS_PER_YEAR);
    }

    static City nearestCity(GameState s, int x, int y) {
        City best = null; double bd = Double.MAX_VALUE;
        for (City c : s.cities) { double d = Math.hypot(c.x - x, c.y - y); if (d < bd) { bd = d; best = c; } }
        return best;
    }

    /** Durum metni ve siralama sirasi: 0 sorun yok ... 4 en ciddi. */
    static Object[] plantStatus(GameState s, Plant p) {
        if (!p.operational()) {
            double total = Math.max(1, p.equipment.buildDays() * 24.0);
            return new Object[] { "İnşaatta %" + Math.round(100 * Math.max(0, Math.min(1, 1 - p.buildHoursLeft / total))), 1, false };
        }
        if (p.outageHoursLeft > 0) return new Object[] { "Arızada (" + Math.max(1, p.outageHoursLeft / 24) + " gün)", 4, true };
        if (p.idleReason != null && (p.idleReason.startsWith("Şehre bağlı değil") || p.idleReason.startsWith("Bağlantı hattı yok")))
            return new Object[] { "Bağlantı bekliyor", 3, true };
        if (p.curtailedMW > 0.05 || p.curtailShare30() > 0.02)
            return new Object[] { "Kısıntıda (%" + Math.round(p.curtailShare30() * 100) + ")", 2, true };
        return new Object[] { "Çalışıyor", 0, false };
    }

    private List<Row> plantRows(GameState s) {
        List<Row> out = new ArrayList<>();
        for (Plant p : s.plants) {
            if (p.owner != s.player) continue;
            Row r = new Row(PLANT_COLS.length);
            City c = nearestCity(s, p.x, p.y);
            Object[] st = plantStatus(s, p);
            long warrantyEnd = p.builtAtHour + (long) p.equipment.supplier.warrantyYears * Balance.HOURS_PER_YEAR;
            boolean warrantySoon = p.operational() && warrantyEnd > s.hour && warrantyEnd - s.hour <= 6L * Balance.HOURS_PER_MONTH;
            double age = p.ageYears(s.hour), left = Math.max(0, p.type.lifeYears - p.wearYears(s.hour));
            r.set(0, p.name() + (p.customName == null ? " #" + p.id : ""), p.name() + p.id);
            r.set(1, p.type.label.replace(" Santrali", ""), p.type.label);
            r.set(2, String.valueOf(Math.round(p.capacityMW())), p.capacityMW());
            r.set(3, c == null ? "—" : c.name, c == null ? "" : c.name);
            r.set(4, (String) st[0] + (warrantySoon ? " · garanti bitiyor" : ""), (Integer) st[1] * 10 + (warrantySoon ? 1 : 0));
            r.set(5, p.operational() ? "%" + Math.round(p.cf30() * 100) : "—", p.cf30());
            double ytd = p.yearProfit + p.monthProfit;
            r.set(6, Fmt.money(ytd), ytd);
            r.set(7, Fmt.f1(age) + " / " + Math.round(left) + " yıl", left);
            r.set(8, warrantyEnd <= s.hour ? "bitti" : date(warrantyEnd), warrantyEnd);
            StringBuilder up = new StringBuilder();
            for (Upgrade u : p.upgrades) up.append(up.length() > 0 ? ", " : "").append(u.label);
            if (p.pendingUpgrade != null) up.append(up.length() > 0 ? ", " : "").append(p.pendingUpgrade.label).append(" (sürüyor)");
            r.set(9, up.length() == 0 ? "—" : up.toString(), up.toString());
            r.problem = (Boolean) st[2] || warrantySoon;
            r.action = "focus:node:" + p.id;
            out.add(r);
        }
        return out;
    }

    private List<Row> lineRows(GameState s) {
        List<Row> out = new ArrayList<>();
        for (PowerLine l : s.lines) {
            boolean mine = l.owner == s.player, requested = l.requester == s.player;
            if (!mine && !requested) continue;
            Row r = new Row(LINE_COLS.length);
            r.set(0, l.a.name() + " – " + l.b.name(), l.a.name() + l.b.name());
            r.set(1, l.tier.shortLabel, l.tier.ordinal());
            r.set(2, Fmt.f1(l.lengthTiles) + " kare", l.lengthTiles);
            r.set(3, Fmt.mw(l.capacityMW()), l.capacityMW());
            double u = l.usable() ? l.utilization() : 0;
            r.set(4, l.usable() ? "%" + Math.round(u * 100) : "—", u);
            String st; int sev;
            if (l.outageHoursLeft > 0) { st = "Arızalı (" + l.outageHoursLeft + " saat)"; sev = 5; }
            else if (l.status == LineStatus.ACTIVE) { st = u > GridBalance.SATURATION_THRESHOLD ? "Aktif · dolu" : "Aktif"; sev = u > GridBalance.SATURATION_THRESHOLD ? 2 : 0; }
            else if (l.status == LineStatus.BUILDING || l.status == LineStatus.UPGRADING) { st = l.status.label + " (" + Math.max(1, l.buildHoursLeft / 24) + " gün)"; sev = 1; }
            else { int[] e = s.operator.eta(l); st = l.status.label + " (" + e[0] + "–" + e[1] + " ay)"; sev = 1; }
            r.set(5, st, sev);
            r.set(6, mine ? "Kendi" : "UŞİ başvurusu", mine ? 0 : 1);
            r.problem = sev >= 2;
            r.action = "focus:line:" + l.id;
            out.add(r);
        }
        return out;
    }

    private List<Row> tmRows(GameState s) {
        List<Row> out = new ArrayList<>();
        for (Substation t : s.substations) {
            if (t.owner != s.player) continue;
            Row r = new Row(TM_COLS.length);
            City c = nearestCity(s, t.x, t.y);
            r.set(0, t.name() + " · " + (c == null ? "" : c.name + " yakını") + " (" + t.x + "," + t.y + ")", t.id);
            r.set(1, t.levelLabel() + (t.operational() ? "" : " (inşaat)"), t.level);
            r.set(2, Fmt.mw(t.transformerMW), t.transformerMW);
            int members = t.cp == null ? 0 : t.cp.members.size();
            r.set(3, String.valueOf(members), members);
            double u = t.cp == null ? 0 : t.cp.utilization();
            r.set(4, t.cp == null ? "—" : "%" + Math.round(u * 100), u);
            r.set(5, (t.openAccess ? "açık" : "kapalı") + " · $" + Fmt.f1(t.accessFeePerMWh) + "/MWh", t.openAccess ? 1 : 0);
            r.set(6, Fmt.money(t.lastAccessIncome), t.lastAccessIncome);
            r.problem = !t.operational() || u > GridBalance.SATURATION_THRESHOLD;
            r.action = "focus:node:" + t.id;
            out.add(r);
        }
        return out;
    }

    private List<Row> opRows(GameState s) {
        List<Row> out = new ArrayList<>();
        GridOperator op = s.operator;
        for (PowerLine l : s.lines) {
            if (l.requester != s.player) continue;
            Row r = new Row(OP_COLS.length);
            int[] e = op.eta(l);
            r.set(0, GridOperator.label(l) + (l.accelerated ? " (hızlandırıldı)" : ""), l.appliedAtHour);
            String st = l.status == LineStatus.BUILDING ? "İnşaatta (" + Math.max(1, l.buildHoursLeft / 24) + " gün)" : l.status.label;
            r.set(1, st, l.status.ordinal());
            r.set(2, l.status == LineStatus.ACTIVE ? "—" : e[0] + "–" + e[1] + " ay", e[0]);
            r.set(3, Fmt.money(l.annualCharge), l.annualCharge);
            r.set(4, "", 0);
            boolean pending = l.status == LineStatus.PLANNED || l.status == LineStatus.QUEUED;
            r.buttons.add(new String[] { "Hızlandır " + Fmt.money(op.accelerateCost(l)), "opacc:" + l.id,
                pending && !l.accelerated && s.player.cash >= op.accelerateCost(l) ? "1" : "0" });
            r.buttons.add(new String[] { l.status == LineStatus.PLANNED ? "İptal (+" + Fmt.money(l.contribution * GridBalance.OP_CANCEL_REFUND) + ")" : "İptal",
                "opcancel:" + l.id, pending ? "1" : "0" });
            r.problem = pending && e[0] > GridBalance.OP_PLANNING_MONTHS + 6;
            r.action = "focus:line:" + l.id;
            out.add(r);
        }
        // UŞİ'nin kendi aciklanmis planlari (ucretsiz, bilgi amacli)
        for (PowerLine l : s.lines) {
            if (!l.operatorPlan || l.status == LineStatus.ACTIVE) continue;
            Row r = new Row(OP_COLS.length);
            int[] e = op.eta(l);
            r.set(0, "UŞİ planı: " + GridOperator.label(l), Long.MAX_VALUE);
            r.set(1, "Planlama (ücretsiz)", 99);
            r.set(2, e[0] + "–" + e[1] + " ay", e[0]);
            r.set(3, "—", 0.0);
            r.set(4, "", 0);
            r.action = "focus:line:" + l.id;
            out.add(r);
        }
        return out;
    }

    private List<Row> tenderRows(GameState s) {
        List<Row> out = new ArrayList<>();
        for (Tender t : s.tenders.wonBy(s.player)) {
            Row r = new Row(TENDER_COLS.length);
            r.set(0, t.city.name, t.city.name);
            r.set(1, String.valueOf(Math.round(t.mw)), t.mw);
            r.set(2, "$" + Fmt.f1(t.price) + "/MWh", t.price);
            r.set(3, t.status.label, t.status.ordinal());
            String ob;
            if (t.status == Tender.Status.AWARDED) {
                double have = s.tenders.commissionedMW(t);
                ob = Math.round(have) + "/" + Math.round(t.mw) + " MW devrede · son tarih " + date(t.commissionDeadline);
            } else if (t.status == Tender.Status.ACTIVE) ob = "Sözleşme " + date(t.contractEndHour) + "'e kadar";
            else ob = t.status == Tender.Status.FORFEITED ? "Teminat yandı (" + Fmt.money(t.deposit) + ")" : "—";
            r.set(4, ob, t.commissionDeadline);
            r.set(5, Fmt.money(t.paidRevenue), t.paidRevenue);
            r.problem = t.status == Tender.Status.AWARDED && t.commissionDeadline - s.hour < 6L * Balance.HOURS_PER_MONTH;
            r.action = "focus:node:" + t.city.id;
            out.add(r);
        }
        return out;
    }

    private String summary(GameState s, List<Row> rows) {
        switch (tab) {
            case PLANTS -> {
                double mw = 0, cfw = 0, prof = 0; int n = 0, op = 0;
                for (Plant p : s.plants) if (p.owner == s.player) {
                    n++; mw += p.capacityMW(); prof += p.yearProfit + p.monthProfit;
                    if (p.operational()) { op++; cfw += p.cf30() * p.capacityMW(); }
                }
                double opMw = 0;
                for (Plant p : s.plants) if (p.owner == s.player && p.operational()) opMw += p.capacityMW();
                return n + " santral · " + Fmt.mw(mw) + " · ort. kapasite faktörü (30 g) %" + Math.round(opMw > 0 ? 100 * cfw / opMw : 0)
                    + " · yılbaşından bu yana kâr " + Fmt.money(prof);
            }
            case LINES -> {
                double len = 0, cap = 0; int n = 0, own = 0;
                for (PowerLine l : s.lines) if (l.owner == s.player || l.requester == s.player) { n++; len += l.lengthTiles; cap += l.capacityMW(); if (l.owner == s.player) own++; }
                return n + " hat (" + own + " kendi, " + (n - own) + " UŞİ başvurusu) · toplam " + Math.round(len) + " kare · " + Fmt.mw(cap) + " kapasite";
            }
            case SUBSTATIONS -> {
                double mw = 0, inc = 0; int n = 0;
                for (Substation t : s.substations) if (t.owner == s.player) { n++; mw += t.transformerMW; inc += t.lastAccessIncome; }
                return n + " trafo merkezi · " + Fmt.mw(mw) + " trafo · geçen ay erişim geliri " + Fmt.money(inc);
            }
            case OPERATOR -> {
                double ann = 0; int n = 0;
                for (PowerLine l : s.lines) if (l.requester == s.player) { n++; ann += l.annualCharge; }
                return n + " başvuru · yıllık bağlantı bedeli " + Fmt.money(ann) + " · UŞİ aynı anda " + s.operator.slots() + " hat inşa eder";
            }
            case TENDERS -> {
                double mw = 0, paid = 0; int n = 0;
                for (Tender t : s.tenders.wonBy(s.player)) { n++; mw += t.mw; paid += t.paidRevenue; }
                return n + " ihale · " + Math.round(mw) + " MW · garantili fiyattan ödenen " + Fmt.money(paid);
            }
        }
        return String.format(Locale.ROOT, "%d", rows.size());
    }
}
