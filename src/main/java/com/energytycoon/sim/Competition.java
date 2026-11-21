package com.energytycoon.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rekabet otoritesi: ilgili pazar (bagli sebeke parcasi), son 12 ayin uretim payi (bagli ortakliklar ana sirkete eklenir,
 * Kamu paydada), birlesme kontrolu (bildirim esigi, olcek farki muafiyeti, %60 / %80 kurallari, batan sirket savunmasi),
 * satis taahhutleri, hakim durum fiyat tavani ve duzenlenmis tekel (kamu hizmeti modu).
 */
public final class Competition {
    private final GameState s;

    Competition(GameState s) { this.s = s; }

    DealBook d() { return s.deals; }
    int idx(Company c) { return s.companies.indexOf(c); }

    // ------------------------------------------------------------ grup ve pay
    /** Grubun ana sirketi (bagli ortaklik zincirinin tepesi). */
    public Company top(Company c) {
        Company t = c;
        for (int g = 0; g < 8 && t.eq.parent >= 0; g++) t = s.companies.get(t.eq.parent);
        return t;
    }

    public boolean sameGroup(Company a, Company b) { return top(a) == top(b); }

    /** Grubun faal uyeleri (ana sirket + bagli ortakliklar). */
    public Set<Company> group(Company c) {
        Company t = top(c);
        Set<Company> g = new HashSet<>();
        for (Company x : s.companies) if (!x.eq.merged && top(x) == t) g.add(x);
        return g;
    }

    /** Pay olcusu: son 12 ay uretimi (henuz uretim yoksa kapasite). */
    double weight(Plant p, boolean byCap) { return byCap ? (p.operational() ? p.capacityMW() : 0) : p.gen12(); }

    public double share(GameState.Component comp, Set<Company> grp) {
        boolean byCap = true;
        for (Plant p : comp.plants) if (p.gen12() > 0) { byCap = false; break; }
        double tot = 0, mine = 0;
        for (Plant p : comp.plants) {
            double w = weight(p, byCap);
            tot += w;
            if (grp.contains(p.owner)) mine += w;
        }
        return tot > 0 ? mine / tot : 0;
    }

    public double nationalShare(Set<Company> grp) {
        boolean byCap = true;
        for (Plant p : s.plants) if (p.gen12() > 0) { byCap = false; break; }
        double tot = 0, mine = 0;
        for (Plant p : s.plants) {
            double w = weight(p, byCap);
            tot += w;
            if (grp.contains(p.owner)) mine += w;
        }
        return tot > 0 ? mine / tot : 0;
    }

    /** Ilgili pazarin adi: en kalabalik sehrin bolgesi. */
    public String marketName(GameState.Component comp) {
        City big = null;
        for (City c : comp.cities) if (big == null || c.population > big.population) big = c;
        return big == null ? "Bağlantısız bölge" : big.name + " bölgesi";
    }

    public double revenue12(Company c) { double t = 0; for (double v : c.eq.rev12) t += v; return t; }

    double groupRevenue(Set<Company> g) { double t = 0; for (Company c : g) t += revenue12(c); return t; }

    /** Batan sirket: temerrutte veya yeniden yapilandirmada. */
    public boolean failing(Company c) {
        if (c.restructUsed) return true;
        for (Loan l : c.loans) if (l.status == Loan.Status.DEFAULTED || l.status == Loan.Status.RESTRUCTURED) return true;
        return false;
    }

    public double regionalLimit(Company target) {
        if (d().relaxed) return FinanceBalance.RELAXED_REGIONAL;
        return failing(target) ? FinanceBalance.FAILING_LIMIT : FinanceBalance.REGIONAL_LIMIT;
    }

    // ------------------------------------------------------------ onizleme
    public static final class Row {
        public String market;
        public double acquirer, target, combined, limit, groupMW, divestMW;
    }

    public static final int APPROVE = 0, CONDITIONAL = 1, REJECT = 2;
    public static final String[] OUTCOME = { "Onay", "Koşullu onay", "Ret" };

    public static final class Preview {
        public final List<Row> rows = new ArrayList<>();
        public double national, valueRatio, divestMW, combinedRevenue, targetRevenue;
        public boolean notifiable, exempt, failing, alreadyControls;
        public int outcome, reviewDays;
        public String reason = "";
        public int[] marketPlants = new int[0];

        public String summary() {
            String r = OUTCOME[outcome] + (outcome == CONDITIONAL ? " (" + Math.round(divestMW) + " MW satış taahhüdü, 12 ay)" : "");
            return r + " · " + reason;
        }
    }

    /** Islem oncesi rekabet onizlemesi: islem sonrasi her ilgili pazardaki birlesik pay ve beklenen sonuc. */
    public Preview preview(Company acquirer, Company target) {
        Preview p = new Preview();
        Set<Company> ga = group(acquirer), gt = group(target);
        p.alreadyControls = top(target) == top(acquirer);
        Set<Company> both = new HashSet<>(ga); both.addAll(gt);
        p.national = nationalShare(both);
        p.combinedRevenue = groupRevenue(both); p.targetRevenue = groupRevenue(gt);
        double va = s.capital.value(acquirer), vt = Math.max(1, s.capital.value(target));
        p.valueRatio = va / vt;
        p.failing = failing(target);
        double limit = regionalLimit(target);
        List<Integer> mp = new ArrayList<>();
        for (GameState.Component comp : s.components()) {
            boolean a = false, t = false;
            for (Plant pl : comp.plants) { if (ga.contains(pl.owner)) a = true; if (gt.contains(pl.owner)) t = true; }
            if (!a && !t) continue;
            Row r = new Row();
            r.market = marketName(comp);
            r.acquirer = share(comp, ga); r.target = share(comp, gt); r.combined = share(comp, both); r.limit = limit;
            for (Plant pl : comp.plants) if (both.contains(pl.owner) && pl.operational()) { r.groupMW += pl.capacityMW(); mp.add(pl.id); }
            if (a && t && r.combined > limit + 1e-9) r.divestMW = r.groupMW * (1 - limit / r.combined);
            p.rows.add(r);
        }
        p.rows.sort(Comparator.comparingDouble((Row r) -> -r.combined));
        p.notifiable = p.combinedRevenue > FinanceBalance.NOTIFY_COMBINED_REVENUE && p.targetRevenue > FinanceBalance.NOTIFY_TARGET_REVENUE;
        p.exempt = p.valueRatio >= FinanceBalance.SCALE_EXEMPTION_RATIO;
        double maxCombined = 0;
        for (Row r : p.rows) { p.divestMW += r.divestMW; if (r.divestMW > 0) maxCombined = Math.max(maxCombined, r.combined); }
        p.marketPlants = mp.stream().mapToInt(Integer::intValue).toArray();
        if (p.alreadyControls) { p.outcome = APPROVE; p.reason = "Aynı grup içinde: kontrol değişikliği yok"; return p; }
        if (!d().relaxed && p.national > FinanceBalance.NATIONAL_LIMIT) {
            p.outcome = REJECT; p.reviewDays = FinanceBalance.FINAL_REVIEW_MONTHS * Balance.DAYS_PER_MONTH;
            p.reason = "İşlem sonrası ulusal pay %" + pctS(p.national) + " > %80 (ulusal tekel sınırı; istisna yok)";
            return p;
        }
        if (!p.notifiable) {
            p.outcome = APPROVE; p.reviewDays = 0;
            p.reason = "Bildirim eşiğinin altında (birleşik gelir " + Finance.money(p.combinedRevenue) + ", hedef " + Finance.money(p.targetRevenue) + "): incelemesiz onay";
            return p;
        }
        if (p.exempt) {
            p.outcome = APPROVE; p.reviewDays = FinanceBalance.PRE_REVIEW_DAYS; p.divestMW = 0;
            p.reason = "Ölçek farkı muafiyeti: değer oranı " + num(p.valueRatio) + " ≥ " + num(FinanceBalance.SCALE_EXEMPTION_RATIO) + "; ön incelemede onay";
            return p;
        }
        if (p.divestMW > 0) {
            p.outcome = CONDITIONAL; p.reviewDays = FinanceBalance.FINAL_REVIEW_MONTHS * Balance.DAYS_PER_MONTH;
            p.reason = "Birleşik pay %" + pctS(maxCombined) + " > %" + pctS(limit) + (p.failing ? " (batan şirket savunması)" : "")
                + "; değer oranı " + num(p.valueRatio) + " < " + num(FinanceBalance.SCALE_EXEMPTION_RATIO) + ": nihai inceleme";
            return p;
        }
        p.outcome = APPROVE; p.reviewDays = FinanceBalance.PRE_REVIEW_DAYS;
        p.reason = "Tüm ilgili pazarlarda birleşik pay ≤ %" + pctS(limit) + "; ön incelemede koşulsuz onay";
        return p;
    }

    static String pctS(double v) { return String.format(Locale.ROOT, "%.0f", v * 100); }
    static String num(double v) { return String.format(Locale.ROOT, "%.2f", v).replace('.', ','); }

    /** Rakip YZ taahhudu kabul eder mi: satilacak MW birlesik grubun ilgili kapasitesinin yarisini asmiyorsa. */
    public boolean aiAcceptsCommitment(Preview p) {
        double cap = 0;
        for (Row r : p.rows) if (r.divestMW > 0) cap += r.groupMW;
        return p.divestMW <= FinanceBalance.AI_ACCEPT_DIVEST * cap;
    }

    // ------------------------------------------------------------ inceleme sureci
    /** Kontrol degisikligi basvurusu. acceptCommitment: kosullu onayda satis taahhudu kabul edildi mi. */
    public DealBook.Review file(Company acquirer, Company target, boolean acceptCommitment) {
        for (DealBook.Review r : d().reviews) if (!r.decided && r.acquirer == idx(acquirer) && r.target == idx(target)) return r;
        Preview p = preview(acquirer, target);
        DealBook.Review r = new DealBook.Review();
        r.id = d().nextId++; r.acquirer = idx(acquirer); r.target = idx(target);
        r.startHour = s.hour; r.outcome = p.outcome; r.reason = p.reason; r.divestMW = p.divestMW; r.valueRatio = p.valueRatio;
        r.exempt = p.exempt; r.notifiable = p.notifiable; r.national = p.national; r.marketPlants = p.marketPlants;
        for (Row row : p.rows) r.maxShare = Math.max(r.maxShare, row.combined);
        r.finalPhase = p.reviewDays > FinanceBalance.PRE_REVIEW_DAYS;
        r.endHour = s.hour + (long) p.reviewDays * 24;
        r.commitmentAccepted = p.outcome == CONDITIONAL && acceptCommitment;
        d().reviews.add(r);
        d().attempts++;
        if (p.reviewDays > 0) s.notify("REKABET: " + acquirer.name + " → " + target.name + " kontrol değişikliği başvurusu: "
            + (r.finalPhase ? "nihai inceleme (4 ay)" : "ön inceleme (30 gün)") + ". " + p.reason, "openmna");
        if (r.endHour <= s.hour) decide(r);
        return r;
    }

    /** Oyuncunun rakip birlesmesine gorus bildirmesi: inceleme 1 ay uzar; islem basina bir kez. */
    public String comment(DealBook.Review r) {
        if (r.decided) return "İnceleme sonuçlandı";
        if (r.commented) return "Bu işlem için görüş zaten bildirildi";
        Company a = s.companies.get(r.acquirer);
        if (top(a) == top(s.player) || top(s.companies.get(r.target)) == top(s.player)) return "Taraf olduğunuz işleme görüş bildiremezsiniz";
        r.commented = true;
        r.endHour += (long) FinanceBalance.COMMENT_EXTRA_MONTHS * Balance.HOURS_PER_MONTH;
        s.notify("REKABET: Görüşünüz alındı; inceleme " + FinanceBalance.COMMENT_EXTRA_MONTHS + " ay uzadı.", "openmna");
        return null;
    }

    public DealBook.Review pendingReview(Company target) {
        for (DealBook.Review r : d().reviews) if (!r.decided && r.target == idx(target)) return r;
        return null;
    }

    void decide(DealBook.Review r) {
        if (r.decided) return;
        r.decided = true;
        Company a = s.companies.get(r.acquirer), t = s.companies.get(r.target);
        boolean ok = r.outcome == APPROVE || r.outcome == CONDITIONAL && r.commitmentAccepted;
        if (r.outcome == CONDITIONAL && !r.commitmentAccepted) r.reason = "Satış taahhüdü kabul edilmedi";
        if (ok && r.outcome == CONDITIONAL) {
            d().conditionals++;
            DealBook.Commitment c = new DealBook.Commitment();
            Company top = top(a);
            c.company = idx(top); c.reviewId = r.id; c.requiredMW = r.divestMW; c.plants = r.marketPlants;
            c.deadline = s.hour + (long) FinanceBalance.DIVEST_MONTHS * Balance.HOURS_PER_MONTH;
            d().commitments.add(c);
            // taban: kontrol gectiginde grubun bu santrallerdeki kapasitesi (kontrol asagida verilir)
            s.mna.onDecision(r, true);
            c.baseMW = commitmentMW(c);
            s.notify("REKABET: " + a.name + " → " + t.name + " koşullu onaylandı: 12 ay içinde " + Math.round(r.divestMW) + " MW satılmalı.", "openmna");
            return;
        }
        if (ok) { d().approvals++; s.notify("REKABET: " + a.name + " → " + t.name + " onaylandı.", "openmna"); s.mna.onDecision(r, true); return; }
        d().rejections++;
        String why = r.outcome == REJECT ? r.reason : "Taahhüt reddedildi";
        d().rejectReasons.add(why);
        s.notify("REKABET: " + a.name + " → " + t.name + " reddedildi: " + why, "openmna");
        s.mna.onDecision(r, false);
    }

    double commitmentMW(DealBook.Commitment c) {
        Set<Company> g = group(s.companies.get(c.company));
        double mw = 0;
        for (int id : c.plants) {
            Plant p = plant(id);
            if (p != null && g.contains(p.owner)) mw += p.capacityMW();
        }
        return mw;
    }

    Plant plant(int id) { for (Plant p : s.plants) if (p.id == id) return p; return null; }

    /** Taahhutte satilmasi gereken kalan MW. */
    public double commitmentLeft(DealBook.Commitment c) { return Math.max(0, c.requiredMW - (c.baseMW - commitmentMW(c))); }

    // ------------------------------------------------------------ zamanlama
    void daily() {
        for (DealBook.Review r : new ArrayList<>(d().reviews)) if (!r.decided && s.hour >= r.endHour) decide(r);
        for (DealBook.Commitment c : d().commitments) {
            if (c.done || c.enforced) continue;
            if (commitmentLeft(c) <= 0.5) { c.done = true; s.notify("REKABET: " + s.companies.get(c.company).name + " satış taahhüdünü yerine getirdi.", "openmna"); continue; }
            if (s.hour >= c.deadline) enforce(c);
        }
    }

    /** Taahhut ihlali: otorite eksik MW'i en dusuk degerli santrallerden secip ihaleye cikarir; yillik gelirin %2'si ceza. */
    void enforce(DealBook.Commitment c) {
        c.enforced = true;
        Company co = s.companies.get(c.company);
        Set<Company> g = group(co);
        List<Plant> own = new ArrayList<>();
        for (int id : c.plants) { Plant p = plant(id); if (p != null && g.contains(p.owner)) own.add(p); }
        own.sort(Comparator.comparingDouble(p -> p.bookValue(s.hour)));
        double left = commitmentLeft(c), sold = 0;
        for (Plant p : own) {
            if (sold >= left) break;
            sold += p.capacityMW();
            auction(p);
        }
        double fine = FinanceBalance.DIVEST_FINE * groupRevenue(g);
        co.cash -= fine; co.fines += fine;
        s.notify("REKABET: " + co.name + " satış taahhüdünü süresinde yerine getirmedi: " + Math.round(sold) + " MW ihaleyle satıldı, ceza " + Finance.money(fine) + ".", "openmna");
    }

    /** Otorite ihalesi: grup disi en yuksek rakip teklifi, yoksa Kamu taban fiyattan. */
    void auction(Plant p) {
        Company from = p.owner, best = null;
        double bid = 0;
        for (Company c : s.companies) {
            if (c.neutral || c.bankrupt || sameGroup(c, from) || c.player && !s.aiPlayer) continue;
            double b = RivalAI.seizedPlantBid(s, c, p);
            if (b > bid) { bid = b; best = c; }
        }
        if (best != null) { best.cash -= bid; from.cash += bid; s.sales.transfer(p, from, best); return; }
        double floor = s.sales.floorPrice(p, from);
        if (s.neutral != null) { s.neutral.cash -= floor; from.cash += floor; s.sales.transfer(p, from, s.neutral); }
    }

    /** Aylik: 12 aylik gelir halkasi, hakim durum fiyat tavani, duzenlenmis tekel. */
    void monthly() {
        for (Company c : s.companies) {
            Equity e = c.eq;
            if (e.rev12 == null || e.rev12.length != 12) e.rev12 = new double[12];
            e.rev12[e.revIdx] = c.lastRevenue; e.revIdx = (e.revIdx + 1) % 12;
        }
        dominance();
        regulated();
        milestones();
    }

    /** Senaryo ara hedefleri: yil sonunda (ocak basinda) ulusal pay kontrolu. */
    void milestones() {
        Scenario sc = s.scenario;
        if (s.month() != 0) return;
        for (int i = 0; i < sc.milestoneYears.length; i++) {
            if (sc.milestoneYears[i] != s.year() - 1) continue;
            double share = nationalShare(group(s.player));
            boolean ok = share >= sc.milestoneShares[i];
            s.notify("SENARYO ara hedefi (" + sc.milestoneYears[i] + " sonu, ulusal pay %" + pctS(sc.milestoneShares[i]) + "): "
                + (ok ? "tutturuldu" : "tutturulamadı") + " (payınız %" + pctS(share) + ").", "openmna");
            if (ok) d().achievements.add("Ara hedef " + sc.milestoneYears[i] + ": %" + pctS(share));
        }
    }

    /** Hakim durum: bir pazarda payi %50'yi asan grubun santrallerine o pazarda fiyat tavani (ortalama uretim maliyeti + %15). */
    void dominance() {
        for (Plant p : s.plants) p.priceCap = 0;
        Set<Company> tops = new HashSet<>();
        for (Company c : s.companies) if (!c.neutral && !c.bankrupt && !c.eq.merged) tops.add(top(c));
        boolean playerDominant = false;
        for (GameState.Component comp : s.components()) {
            for (Company t : tops) {
                Set<Company> g = group(t);
                if (share(comp, g) <= FinanceBalance.DOMINANCE) continue;
                double mwh = 0, cost = 0;
                for (Plant p : comp.plants) if (g.contains(p.owner)) { mwh += p.lastMonthMWh; cost += p.lastMonthCost; }
                if (mwh <= 0) continue;
                double cap = cost / mwh * (1 + FinanceBalance.DOMINANCE_MARKUP);
                for (Plant p : comp.plants) if (g.contains(p.owner)) p.priceCap = cap;
                if (t == top(s.player)) playerDominant = true;
            }
        }
        if (playerDominant && !s.player.eq.dominant) s.notify("REKABET: Bir pazarda payınız %50'yi aştı: hakim durumdasınız. Satış fiyatınız ortalama üretim maliyeti + %15 ile sınırlı.", "openmna");
        s.player.eq.dominant = playerDominant;
    }

    /** Organik buyumeyle ulusal payi %80'i gecen grup: kamu hizmeti modu, kar varlik getirisinin %8'iyle sinirli. */
    void regulated() {
        if (d().regulated < 0) {
            for (Company c : s.companies) {
                if (c.neutral || c.bankrupt || c.eq.merged || top(c) != c) continue;
                if (nationalShare(group(c)) > FinanceBalance.REGULATED_SHARE && !d().relaxed) {
                    d().regulated = idx(c);
                    String a = c.name + ": düzenlenmiş tekel (" + s.year() + ")";
                    d().achievements.add(a);
                    s.notify("REKABET: " + c.name + " ulusal üretimin %80'ini geçti: kamu hizmeti moduna geçildi. Kâr varlık getirisinin %8'iyle sınırlı; fazlası tüketiciye iade edilir."
                        + (c.player ? " Başarım kazanıldı." : ""), "openmna");
                    break;
                }
            }
        }
        if (d().regulated < 0) return;
        Company t = s.companies.get(d().regulated);
        Set<Company> g = group(t);
        double assets = 0, profit = 0;
        for (Company c : g) { assets += s.assetValue(c); profit += c.lastNet; }
        double allowed = FinanceBalance.REGULATED_RETURN * assets / 12;
        if (profit > allowed) { double refund = profit - allowed; t.cash -= refund; t.eq.refunds += refund; t.eq.lastRefund = refund; }
        else t.eq.lastRefund = 0;
    }

    /** Kamu hizmeti skoru: arz guvenligi, karbon yogunlugu, tuketici fiyati ve bilesik skor (0-100). */
    public double[] regulatedScore() {
        if (d().regulated < 0) return null;
        Set<Company> g = group(s.companies.get(d().regulated));
        double mwh = 0, co2 = 0;
        for (Company c : g) { mwh += c.lastMwhGenerated; co2 += c.lastCo2; }
        double served = s.lastServedRatio, intensity = mwh > 0 ? co2 / mwh : 0, price = s.statPrice;
        double score = 100 * (0.5 * Math.min(1, served) + 0.25 * Math.max(0, 1 - intensity) + 0.25 * Math.max(0, 1 - price / 150));
        return new double[] { served, intensity, price, score };
    }
}
