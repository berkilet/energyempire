package com.energytycoon.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finans motoru: kredi notu, bankalar ve iliski puani, kredi urunleri (ticari, ECA, isletme kredisi), otomatik tahsilat,
 * gecikme, temerrut, yeniden yapilandirma ve taahhutler. Oyuncu ve rakipler ayni kurallara tabidir; Kamu Uretim disarida.
 */
public final class Finance {
    private final GameState s;
    private static final int HPM = Balance.HOURS_PER_MONTH;

    Finance(GameState s) { this.s = s; }

    // ------------------------------------------------------------ yardimcilar
    static boolean exempt(Company c) { return c == null || c.neutral; }

    public double creditLimit(Company c) { return FinanceBalance.BASE_CREDIT + FinanceBalance.ASSET_CREDIT * s.assetValue(c); }

    /** Limit kapsamindaki bakiye: ticari + isletme kredisi (ECA haric). */
    public double limitUsage(Company c) {
        double v = 0;
        for (Loan l : c.loans) if (l.open() && l.type != Loan.Type.ECA) v += l.outstanding;
        return v;
    }

    public double bankExposure(Company c, Bank b) {
        double v = 0;
        for (Loan l : c.loans) if (l.open() && l.type != Loan.Type.ECA && l.bank == b) v += l.outstanding;
        return v;
    }

    public Loan revolver(Company c) {
        for (Loan l : c.loans) if (l.type == Loan.Type.REVOLVER && l.open()) return l;
        return null;
    }

    // ------------------------------------------------------------ 2. kredi notu
    /** Son 4 ceyregin FAVOK'u, net borc ve faiz giderinden not; yeni sirket BB, temerrut gecmisi B ile sinirli. */
    public Rating computeRating(Company c) {
        if (c.quarters < 4) return capped(c, Rating.BB);
        double e = 0, i = 0;
        for (int k = 0; k < 4; k++) { e += c.qEbitda[k]; i += c.qInterest[k]; }
        return capped(c, Rating.compute(e, c.debt() - c.cash, i));
    }

    private Rating capped(Company c, Rating r) {
        if (c.defaultCapUntil > s.hour) r = r.worse(Rating.B);
        if (c.restructUsed && c.restructProtectUntil > s.hour) r = Rating.CCC;
        return r;
    }

    /** Gecerli not: gecikmede bir kademe dusuk. */
    public Rating rating(Company c) { return c.lateNotch ? c.rating.down() : c.rating; }

    public double ebitda4(Company c) { double e = 0; for (double v : c.qEbitda) e += v; return e; }
    public double interest4(Company c) { double e = 0; for (double v : c.qInterest) e += v; return e; }

    public double leverage(Company c) { double e = ebitda4(c); return e <= 0 ? Double.POSITIVE_INFINITY : (c.debt() - c.cash) / e; }

    public double coverage(Company c) { double i = interest4(c); return i <= 1 ? Double.POSITIVE_INFINITY : ebitda4(c) / i; }

    public boolean greenEligible(Company c) {
        double r = 0, g = 0;
        for (int k = 0; k < 4; k++) { r += c.qRenew[k]; g += c.qGen[k]; }
        return g > 0 && r / g > FinanceBalance.GREEN_SHARE;
    }

    // ------------------------------------------------------------ 3-4. teklifler
    /** Bir bankanin teklifi (sihirbazda yan yana gosterilir). */
    public static final class Offer {
        public Bank bank;
        public boolean available;
        public String reason = "";
        public double rate, spread, upfront, covenant, relDiscount, installment, totalRepay;
        public int periods;
    }

    /** Ticari kredi faizi: politika + not primi + banka marji + olay primleri + zorluk eki - indirimler (+1 puan sabit). Tabani: ECA + 1 puan. */
    public double commercialSpread(Company c, Bank b, boolean renewPurpose, boolean fixed) {
        Rating r = rating(c);
        double sp = r.premium() + b.margin(r) + s.macro.eventSpread() + s.settings.difficulty.rateAdd
            - Bank.tierDiscount(c.bankScore[b.ordinal()]);
        if (greenEligible(c)) sp -= s.macro.greenDiscount();
        if (b == Bank.YESIL && renewPurpose) sp -= FinanceBalance.YESIL_RENEW_DISCOUNT;
        sp = Math.max(sp, FinanceBalance.ECA_SPREAD + FinanceBalance.COMMERCIAL_MIN_OVER_ECA);
        return sp + (fixed ? FinanceBalance.FIXED_PREMIUM : 0);
    }

    /** Neden kredi veremez (null: verebilir). */
    public String refusal(Company c, Bank b, Loan.Type type, double amount, Plant purpose) {
        if (exempt(c)) return "Kamu kredi kullanmaz";
        if (c.bankBanUntil[b.ordinal()] > s.hour) return "Temerrüt sonrası " + FinanceBalance.REL_BAN_YEARS + " yıl kredi vermez";
        Rating r = rating(c);
        if (type != Loan.Type.ECA && !b.lendsTo(r)) return "CCC nota yalnızca Demirtaş kredi verir";
        if (type == Loan.Type.COMMERCIAL && c.bankCovenantBreach[b.ordinal()]) return "Taahhüt ihlali: yeni ticari kredi yok";
        if (b == Bank.YESIL && purpose != null && !purpose.type.renewable) return "Fosil santrale kredi vermez";
        if (c.restructUsed && c.restructProtectUntil > s.hour) return "Yapılandırma sürüyor";
        if (type != Loan.Type.ECA) {
            if (limitUsage(c) + amount > creditLimit(c) + 1) return "Kredi limiti aşılır (" + money(creditLimit(c) - limitUsage(c)) + " kaldı)";
            if (bankExposure(c, b) + amount > b.concentration() * creditLimit(c) + 1)
                return "Yoğunlaşma limiti: bu bankada en fazla " + money(Math.max(0, b.concentration() * creditLimit(c) - bankExposure(c, b)));
        }
        return null;
    }

    public List<Offer> commercialOffers(Company c, double amount, int years, int freqMonths, boolean fixed, Plant purpose) {
        List<Offer> out = new ArrayList<>();
        boolean renew = purpose != null && purpose.type.renewable;
        for (Bank b : Bank.values()) {
            Offer o = new Offer();
            o.bank = b;
            int y = Math.min(years, b.maxYears());
            String why = refusal(c, b, Loan.Type.COMMERCIAL, amount, purpose);
            if (why == null && years > b.maxYears()) why = "En fazla " + b.maxYears() + " yıl vade";
            o.available = why == null;
            o.reason = why == null ? "" : why;
            o.relDiscount = Bank.tierDiscount(c.bankScore[b.ordinal()]);
            o.spread = commercialSpread(c, b, renew, fixed);
            o.rate = s.macro.policyRate + o.spread;
            o.upfront = amount * FinanceBalance.COMMERCIAL_FEE * Bank.feeMult(c.bankScore[b.ordinal()]);
            o.covenant = b.covenantLimit();
            o.periods = y * 12 / freqMonths;
            o.installment = Loan.annuity(amount, o.rate, 12 / freqMonths, o.periods);
            o.totalRepay = o.installment * o.periods;
            out.add(o);
        }
        return out;
    }

    /** Ticari kredi imzasi. Null donerse yapilamadi. */
    public Loan signCommercial(Company c, Bank b, double amount, int years, int freqMonths, boolean fixed, Plant purpose) {
        if (refusal(c, b, Loan.Type.COMMERCIAL, amount, purpose) != null || years > b.maxYears()) return null;
        Loan l = new Loan();
        l.id = s.nextId++; l.type = Loan.Type.COMMERCIAL; l.bank = b; l.fixed = fixed;
        l.spread = commercialSpread(c, b, purpose != null && purpose.type.renewable, fixed);
        l.rate = s.macro.policyRate + l.spread;
        l.principal = l.outstanding = amount;
        l.tenorMonths = years * 12; l.frequencyMonths = freqMonths;
        l.startHour = s.hour; l.lastResetHour = s.hour;
        l.nextDueHour = s.hour + (long) freqMonths * HPM;
        l.maturityHour = s.hour + (long) l.tenorMonths * HPM;
        double fee = amount * FinanceBalance.COMMERCIAL_FEE * Bank.feeMult(c.bankScore[b.ordinal()]);
        c.cash += amount - fee; c.fees += fee;
        c.loans.add(l);
        return l;
    }

    // ------------------------------------------------------------ ECA
    /** Tedarikci ulkesi icin ECA sigorta primi (risk); yerli tedarikcide ECA yok. */
    public static double ecaPremiumRate(String origin) {
        return switch (origin) {
            case "İskandinavya" -> 0.04; case "Alpler" -> 0.045; case "Avrupa" -> 0.05; case "Kuzey Amerika" -> 0.055; case "Uzak Doğu" -> 0.08;
            default -> -1;
        };
    }

    public static boolean ecaEligible(Equipment e) { return ecaPremiumRate(e.supplier.origin) > 0; }

    public static int ecaYears(PlantType t) {
        return switch (t) { case NUCLEAR -> FinanceBalance.ECA_NUCLEAR_YEARS; case COAL, HYDRO -> FinanceBalance.ECA_MAX_YEARS; case GAS -> 12; default -> FinanceBalance.ECA_MIN_YEARS; };
    }

    public static final class EcaQuote {
        public double amount, rate, premium, bankFee, installment, totalRepay;
        public int years, graceMonths, freqMonths;
        public String agency;
    }

    public EcaQuote ecaQuote(Company c, Equipment e, Bank b, int freqMonths) {
        if (!ecaEligible(e)) return null;
        EcaQuote q = new EcaQuote();
        q.amount = e.capex() * FinanceBalance.ECA_SHARE;
        q.rate = s.macro.policyRate + FinanceBalance.ECA_SPREAD;
        q.premium = q.amount * ecaPremiumRate(e.supplier.origin) * s.macro.ecaPremiumMult();
        q.bankFee = q.amount * FinanceBalance.ECA_BANK_FEE * Bank.feeMult(c.bankScore[b.ordinal()]);
        q.years = ecaYears(e.type);
        q.graceMonths = (int) Math.ceil(e.buildDays() / 30.0) + FinanceBalance.ECA_GRACE_EXTRA_MONTHS;
        q.freqMonths = freqMonths;
        int n = q.years * 12 / freqMonths;
        q.installment = Loan.annuity(q.amount, q.rate, 12 / freqMonths, n);
        q.totalRepay = q.installment * n;
        q.agency = e.supplier.origin + " İhracat Kredi Kuruluşu";
        return q;
    }

    /** Santral siparisiyle ECA kredisi (kredi insaat suresince kullandirilir; burada siparis aninda santral bedeline mahsup edilir). */
    /** Santralin beklenen DSCR'si: yillik beklenen nakit akisi / ECA yillik borc servisi (rakip YZ ve oyuncu penceresi ayni hesap). */
    public double expectedDscr(Company c, Equipment e, int x, int y, Bank b, int freqMonths) {
        EcaQuote q = ecaQuote(c, e, b, freqMonths);
        if (q == null) return Double.NaN;
        Plant probe = new Plant();
        probe.type = e.type; probe.equipment = e; probe.owner = c; probe.x = x; probe.y = y; probe.builtAtHour = s.hour; probe.ageStartHour = s.hour;
        double cf = switch (e.type) { case SOLAR -> s.siteScore(e, x, y); case WIND -> s.siteScore(e, x, y); case HYDRO -> s.siteScore(e, x, y);
            case NUCLEAR -> 0.9; case COAL -> 0.7; default -> 0.55; };
        double margin = Math.max(0, s.statPrice - s.marginalCost(probe));
        double cash = e.capacityMW * cf * Balance.HOURS_PER_YEAR * margin - e.fixedOmPerHour() * Balance.HOURS_PER_YEAR;
        double service = q.installment * 12.0 / freqMonths;
        return service <= 0 ? Double.POSITIVE_INFINITY : cash / service;
    }

    public Loan signEca(Company c, Plant p, Bank b, int freqMonths) {
        if (exempt(c) || c.bankBanUntil[b.ordinal()] > s.hour) return null;
        EcaQuote q = ecaQuote(c, p.equipment, b, freqMonths);
        if (q == null) return null;
        Loan l = new Loan();
        l.id = s.nextId++; l.type = Loan.Type.ECA; l.bank = b; l.fixed = true; l.ecaOrigin = p.equipment.supplier.origin; l.linkedPlant = p.id;
        l.rate = q.rate; l.spread = FinanceBalance.ECA_SPREAD;
        l.principal = l.outstanding = q.amount;
        l.graceMonths = q.graceMonths; l.tenorMonths = q.years * 12; l.frequencyMonths = freqMonths;
        l.startHour = s.hour; l.lastResetHour = s.hour;
        l.graceEndHour = s.hour + (long) q.graceMonths * HPM;
        l.nextDueHour = s.hour + (long) freqMonths * HPM;
        l.maturityHour = l.graceEndHour + (long) l.tenorMonths * HPM;
        c.cash += q.amount - q.premium - q.bankFee; c.fees += q.premium + q.bankFee;
        c.loans.add(l);
        return l;
    }

    /**
     * ECA ile santral siparisi (oyuncu ve rakip ayni yol): kredi siparisle birlikte kullandirilir, kalan tutari sirket oder.
     * Null: kurulamadi (yer, nakit veya ECA uygun degil).
     */
    public Plant buildWithEca(Company c, Equipment e, int x, int y, Bank b, int freqMonths) {
        EcaQuote q = ecaQuote(c, e, b, freqMonths);
        if (q == null || exempt(c) || c.bankBanUntil[b.ordinal()] > s.hour) return null;
        if (c.cash + q.amount - q.premium - q.bankFee < e.capex()) return null;
        c.cash += q.amount;
        Plant p = s.buildPlant(c, e, x, y);
        c.cash -= q.amount;
        if (p != null) signEca(c, p, b, freqMonths);
        return p;
    }

    /** Santral icin pesin gereken nakit (ECA ile): bedel - kredi + prim + masraf. */
    public double ecaCashNeed(Company c, Equipment e, Bank b, int freqMonths) {
        EcaQuote q = ecaQuote(c, e, b, freqMonths);
        return q == null ? e.capex() : e.capex() - q.amount + q.premium + q.bankFee;
    }

    // ------------------------------------------------------------ takvim projeksiyonu
    /** Aylik tahmini faaliyet nakdi: son 4 ceyregin FAVOK ortalamasi; yoksa gecen ayin FAVOK'u. */
    public double monthlyOperatingCash(Company c) {
        if (c.quarters >= 4) return ebitda4(c) / 12;
        return c.lastRevenue - c.lastFuel - c.lastOm - c.lastCarbon - c.lastFines + c.lastAccessIncome - c.lastAccessCost - c.lastConnectionCharge;
    }

    /** 12 aylik nakit projeksiyonu: nakit + isletme kredisi bosluğu, her ay faaliyet nakdi eklenir, taksitler duser. Aylik bakiye. */
    public double[] cashProjection(Company c, double[] service) {
        double[] out = new double[12];
        double cash = c.cash + revolverRoom(c), op = monthlyOperatingCash(c);
        for (int m = 0; m < 12; m++) { cash += op - service[m]; out[m] = cash; }
        return out;
    }

    // ------------------------------------------------------------ isletme kredisi
    public double revolverLimitFor(Company c) { return FinanceBalance.REVOLVER_LIMIT[rating(c).ordinal()]; }

    /** Isletme kredisi limiti acar (komisyon %0,5). */
    public Loan openRevolver(Company c, Bank b) {
        if (exempt(c) || revolver(c) != null) return null;
        if (!b.lendsTo(rating(c)) || c.bankBanUntil[b.ordinal()] > s.hour) return null;
        Loan l = new Loan();
        l.id = s.nextId++; l.type = Loan.Type.REVOLVER; l.bank = b; l.fixed = false;
        l.limit = revolverLimitFor(c);
        l.spread = rating(c).premium() + FinanceBalance.REVOLVER_SPREAD + s.macro.eventSpread() + s.settings.difficulty.rateAdd;
        l.rate = s.macro.policyRate + l.spread;
        l.frequencyMonths = 1; l.startHour = s.hour; l.lastResetHour = s.hour;
        l.nextDueHour = Long.MAX_VALUE; l.maturityHour = Long.MAX_VALUE;
        double fee = l.limit * FinanceBalance.REVOLVER_FEE;
        c.cash -= fee; c.fees += fee;
        c.loans.add(l);
        return l;
    }

    /** Eski kayit donusumu: masrafsiz isletme kredisi limiti. */
    public void openRevolverFree(Company c, Bank b) {
        Loan l = openRevolver(c, b);
        if (l != null) { double fee = l.limit * FinanceBalance.REVOLVER_FEE; c.cash += fee; c.fees -= fee; }
    }

    /** Eski kayit donusumu: tek borc tutari Kiyibank'tan alinmis 5 yillik, 3 ayda bir taksitli, degisken faizli ticari kredi olur. */
    void convertLegacyDebt(Company c, double amount) {
        Loan l = new Loan();
        l.id = s.nextId++; l.type = Loan.Type.COMMERCIAL; l.bank = Bank.KIYI; l.fixed = false;
        l.spread = commercialSpread(c, Bank.KIYI, false, false);
        l.rate = s.macro.policyRate + l.spread;
        l.principal = l.outstanding = amount;
        l.tenorMonths = 60; l.frequencyMonths = 3;
        l.startHour = s.hour; l.lastResetHour = s.hour;
        l.nextDueHour = s.hour + 3L * HPM; l.maturityHour = s.hour + 60L * HPM;
        c.loans.add(l);
    }

    public double revolverRoom(Company c) {
        Loan r = revolver(c);
        if (r == null) return 0;
        double room = r.limit - r.outstanding;
        room = Math.min(room, creditLimit(c) - limitUsage(c));
        room = Math.min(room, r.bank.concentration() * creditLimit(c) - bankExposure(c, r.bank));
        return Math.max(0, room);
    }

    /** Isletme kredisinden kullanim (otomatik veya elle). Kullanilan tutar. */
    public double drawRevolver(Company c, double amount) {
        Loan r = revolver(c);
        double take = Math.min(amount, revolverRoom(c));
        if (r == null || take <= 0) return 0;
        if (r.outstanding <= 1) r.drawnSinceHour = s.hour;
        r.outstanding += take; c.cash += take;
        return take;
    }

    public double repayRevolver(Company c, double amount) {
        Loan r = revolver(c);
        if (r == null) return 0;
        double pay = Math.min(Math.min(amount, r.outstanding), Math.max(0, c.cash));
        r.outstanding -= pay; c.cash -= pay;
        if (r.outstanding <= 1) { r.outstanding = 0; r.drawnSinceHour = -1; }
        return pay;
    }

    // ------------------------------------------------------------ erken kapama
    public double prepayCost(Company c, Loan l) {
        if (l.type == Loan.Type.REVOLVER) return l.outstanding;
        double pen = Bank.noPrepayPenalty(c.bankScore[l.bank.ordinal()]) ? 0 : l.outstanding * (l.type == Loan.Type.ECA ? FinanceBalance.ECA_PREPAY : FinanceBalance.COMMERCIAL_PREPAY);
        return l.outstanding + l.accrued + l.dueAmount + pen;
    }

    public String prepay(Company c, Loan l) {
        if (!l.open()) return "Kredi kapalı";
        double cost = prepayCost(c, l);
        if (c.cash < cost) return "Yetersiz nakit (" + money(cost) + ")";
        if (l.type == Loan.Type.REVOLVER) { repayRevolver(c, cost); return null; }
        double pen = cost - l.outstanding - l.accrued - l.dueAmount;
        c.cash -= cost; c.fees += pen;
        l.outstanding = 0; l.accrued = 0; l.dueAmount = 0;
        close(c, l);
        return null;
    }

    /** Santral satilinca ona bagli ECA kredisi (cezasiz) kapatilir; nakit yetmezse isletme kredisi kullanilir. */
    void settleLinkedEca(Company c, Plant p) {
        for (Loan l : c.loans) {
            if (!l.open() || l.type != Loan.Type.ECA || l.linkedPlant != p.id) continue;
            double owed = l.outstanding + l.accrued + l.dueAmount - l.pendingPrincipal + l.demand;
            if (c.cash < owed) drawRevolver(c, owed - c.cash);
            c.cash -= owed;
            l.outstanding = 0; l.accrued = 0; l.dueAmount = 0; l.pendingPrincipal = 0; l.demand = 0;
            close(c, l);
            notifyFin(c, "Santral satışıyla " + l.name() + " kapatıldı (" + money(owed) + ").");
        }
    }

    private void close(Company c, Loan l) {
        l.status = Loan.Status.CLOSED;
        if (!l.everLate) rel(c, l.bank, FinanceBalance.REL_CLOSED);
    }

    // ------------------------------------------------------------ iliski puani
    void rel(Company c, Bank b, int d) {
        int i = b.ordinal();
        c.bankScore[i] = Math.max(0, Math.min(FinanceBalance.REL_MAX, c.bankScore[i] + d));
    }

    // ------------------------------------------------------------ 5. tahakkuk ve tahsilat
    /** Aylik: faiz tahakkuku (gelir tablosuna), isletme kredisi faizi, eksi nakit cezasi. */
    void monthlyAccrual(Company c) {
        if (exempt(c)) return;
        for (Loan l : c.loans) {
            if (!l.open()) continue;
            double i = l.outstanding * l.effectiveRate() / 12;
            c.interest += i;
            c.qInterestAcc += i;
            if (l.type == Loan.Type.REVOLVER) l.dueAmount += i; // faiz her ay odenir
            else l.accrued += i;
        }
        if (c.cash < 0) { double o = -c.cash * FinanceBalance.OVERDRAFT_RATE / 12; c.cash -= o; c.interest += o; c.qInterestAcc += o; }
    }

    /** Gunluk: vade gelen taksitler, degisken faiz guncellemesi, gecikme/temerrut, eksi nakit. */
    void daily(Company c) {
        if (exempt(c) || c.bankrupt) return;
        boolean protect = c.restructProtectUntil > s.hour;
        for (Loan l : new ArrayList<>(c.loans)) {
            if (!l.open()) continue;
            // degisken faiz: 3 ayda bir
            if (!l.fixed && s.hour - l.lastResetHour >= (long) FinanceBalance.FLOAT_RESET_MONTHS * HPM) {
                l.lastResetHour = s.hour;
                l.rate = s.macro.policyRate + l.spread;
            }
            if (l.type == Loan.Type.REVOLVER) {
                if (l.outstanding > 1 && l.drawnSinceHour >= 0 && s.hour - l.drawnSinceHour >= (long) FinanceBalance.REVOLVER_CLEAR_MONTHS * HPM && l.demand <= 0) {
                    l.demand = l.outstanding; // 12 ayda kapatilmali
                    notifyFin(c, "İşletme kredisinin " + money(l.outstanding) + " kullanımı 12 ayı doldurdu; kapatılması gerekiyor.");
                }
            } else if (s.hour >= l.nextDueHour && l.dueAmount <= 0) {
                boolean grace = s.hour < l.graceEndHour;
                double interest = l.accrued;
                double principal = 0;
                if (!grace) {
                    double inst = Loan.annuity(l.outstanding, l.effectiveRate(), 12 / l.frequencyMonths, l.remainingPeriods());
                    principal = Math.min(l.outstanding, Math.max(0, inst - interest));
                    if (l.remainingPeriods() <= 1) principal = l.outstanding;
                }
                l.dueAmount = interest + principal;
                l.accrued = 0;
                l.nextDueHour += (long) l.periodHours();
                l.pendingPrincipal += principal;
            }
            if (protect) continue;
            collect(c, l);
        }
        // eksi nakit once isletme kredisinden karsilanir; yalnizca limit doluyken eksi kalir (60 gunde temerrut sureci)
        if (c.cash < 0 && !protect) drawRevolver(c, -c.cash);
        if (c.cash < 0) {
            if (c.negativeSinceHour < 0) c.negativeSinceHour = s.hour;
            else if (!protect && s.hour - c.negativeSinceHour >= FinanceBalance.NEGATIVE_CASH_DAYS * 24L) {
                c.negativeSinceHour = -1;
                commercialDefault(c, null, "60 gündür eksi nakit");
            }
        } else c.negativeSinceHour = -1;
        // yapilandirma kosulu: 6 ayda en az bir santral satisi
        if (c.mustSellBy > 0 && s.hour > c.mustSellBy) {
            c.mustSellBy = 0;
            if (s.totalPlantsOf(c) >= c.plantsAtRestruct) bankrupt(c, "Yapılandırma şartı (6 ayda santral satışı) yerine getirilmedi");
        }
    }

    /** Vadesi gelen tutari tahsil eder: nakit, yetmezse isletme kredisi, o da yetmezse gecikme. */
    private void collect(Company c, Loan l) {
        double due = l.dueAmount + l.demand;
        if (due <= 0.5) return;
        if (c.cash < due && l.type != Loan.Type.REVOLVER) {
            double drawn = drawRevolver(c, due - Math.max(0, c.cash));
            if (drawn > 0) notifyFin(c, "Taksit için işletme kredisinden " + money(drawn) + " otomatik kullanıldı (" + l.name() + ").");
        }
        if (c.cash >= due) {
            c.cash -= due; c.debtServicePaid += due;
            double principalPaid = Math.min(l.outstanding, l.pendingPrincipal + l.demand);
            l.outstanding -= principalPaid;
            l.pendingPrincipal = 0; l.dueAmount = 0; l.demand = 0;
            if (l.type == Loan.Type.REVOLVER && l.outstanding <= 1) { l.outstanding = 0; l.drawnSinceHour = -1; }
            if (l.status == Loan.Status.LATE) {
                l.status = l.restructured ? Loan.Status.RESTRUCTURED : Loan.Status.ACTIVE;
                l.daysLate = 0;
                c.lateNotch = anyLate(c);
                notifyFin(c, l.name() + ": geciken taksit ödendi.");
            } else if (l.type != Loan.Type.REVOLVER && l.onTimePayments < FinanceBalance.REL_ON_TIME_CAP) {
                l.onTimePayments++;
                rel(c, l.bank, FinanceBalance.REL_ON_TIME);
            }
            if (l.type != Loan.Type.REVOLVER && l.outstanding <= 1) { l.outstanding = 0; close(c, l); }
            return;
        }
        // gecikme
        if (l.status != Loan.Status.LATE) {
            l.status = Loan.Status.LATE; l.everLate = true; l.daysLate = 0;
            c.lateNotch = true;
            rel(c, l.bank, FinanceBalance.REL_LATE);
            c.lateEvents++;
            notifyFin(c, "GECİKME: " + l.name() + " taksiti (" + money(due) + ") ödenemedi. 30 gün tolerans; gecikme faizi +2 puan, not bir kademe düştü.");
        }
        l.daysLate++;
        if (l.daysLate == FinanceBalance.LATE_TOLERANCE_DAYS + 1) {
            rel(c, l.bank, FinanceBalance.REL_LATE30 - FinanceBalance.REL_LATE);
            notifyFin(c, "GECİKME 30 günü aştı: " + l.name() + ". 90 günde temerrüt.");
        }
        if (l.daysLate >= FinanceBalance.DEFAULT_DAYS) {
            if (l.type == Loan.Type.ECA) ecaDefault(c, l);
            else commercialDefault(c, l, l.name() + " 90 gün ödenmedi");
        }
    }

    boolean anyLate(Company c) {
        for (Loan l : c.loans) if (l.status == Loan.Status.LATE) return true;
        return false;
    }

    // ------------------------------------------------------------ temerrut
    private void defaultBankEffects(Company c, Bank b) {
        c.bankScore[b.ordinal()] = 0;
        c.bankBanUntil[b.ordinal()] = s.hour + (long) FinanceBalance.REL_BAN_YEARS * Balance.HOURS_PER_YEAR;
        for (Bank o : Bank.values()) if (o != b) rel(c, o, FinanceBalance.REL_DEFAULT_OTHERS);
        c.defaultCapUntil = s.hour + (long) FinanceBalance.DEFAULT_CAP_YEARS * Balance.HOURS_PER_YEAR;
        c.defaults++;
    }

    /** ECA temerrudu: banka bagli santrale el koyar; rakiplerden en yuksek teklif, yoksa defterin %60'i (Kamu varlik fonuna). Diger krediler etkilenmez. */
    void ecaDefault(Company c, Loan l) {
        Plant p = s.nodeById(l.linkedPlant) instanceof Plant pp ? pp : null;
        double owed = l.outstanding + l.accrued + l.dueAmount - l.pendingPrincipal + l.demand;
        l.status = Loan.Status.DEFAULTED; // satis sirasinda kapatma (settleLinkedEca) bu krediyi atlar
        double proceeds = 0;
        String buyerName = "Kamu varlık fonu";
        if (p != null && p.owner == c) {
            Company best = null; double bid = 0;
            for (Company r : s.companies) {
                if (r == c || r.player || r.neutral || r.bankrupt) continue;
                double b = RivalAI.seizedPlantBid(s, r, p);
                if (b > bid) { bid = b; best = r; }
            }
            if (best != null) { best.cash -= bid; proceeds = bid; s.sales.transfer(p, c, best); buyerName = best.name; }
            else { proceeds = p.bookValue(s.hour) * FinanceBalance.SEIZE_FLOOR; if (s.neutral != null) s.sales.transfer(p, c, s.neutral); }
        }
        double surplus = proceeds - owed;
        if (surplus > 0) c.cash += surplus;
        l.outstanding = 0; l.accrued = 0; l.dueAmount = 0; l.pendingPrincipal = 0; l.demand = 0;
        l.status = Loan.Status.DEFAULTED;
        defaultBankEffects(c, l.bank);
        c.lateNotch = anyLate(c);
        notifyFin(c, "TEMERRÜT (ECA): " + l.bank.label + " " + (p != null ? p.name() : "santral") + " santraline el koydu; " + buyerName + " aldı ("
            + money(proceeds) + ")" + (surplus > 0 ? ", artan " + money(surplus) + " şirkete döndü." : "."));
    }

    /** Ticari temerrut: capraz temerrut (tum ticari krediler muaccel); bir kez yapilandirma teklifi. */
    void commercialDefault(Company c, Loan trigger, String why) {
        if (c.bankrupt) return;
        if (trigger != null) trigger.status = Loan.Status.DEFAULTED;
        defaultBankEffects(c, trigger != null ? trigger.bank : Bank.KIYI);
        if (c.restructUsed) { bankrupt(c, why + " (yapılandırma hakkı kullanılmış)"); return; }
        if (c.player && !s.aiPlayer) {
            c.restructOfferPending = true; c.restructOfferHour = s.hour;
            c.restructProtectUntil = s.hour + (long) FinanceBalance.RESTRUCT_PROTECT_MONTHS * HPM;
            notifyFin(c, "TEMERRÜT: " + why + ". Tüm ticari krediler muaccel oldu. Yeniden yapılandırma teklifi Finans menüsünde.", "restruct");
        } else acceptRestructuring(c); // rakip YZ kabul eder
    }

    /** Yapilandirma: 3 ay koruma, ticari kredilerde +3 yil vade ve +2 puan, 6 ayda en az bir santral satisi, not CCC. */
    public void acceptRestructuring(Company c) {
        c.restructOfferPending = false;
        c.restructUsed = true;
        c.restructProtectUntil = s.hour + (long) FinanceBalance.RESTRUCT_PROTECT_MONTHS * HPM;
        c.mustSellBy = s.hour + (long) FinanceBalance.RESTRUCT_SELL_MONTHS * HPM;
        c.plantsAtRestruct = s.totalPlantsOf(c);
        for (Loan l : new ArrayList<>(c.loans)) {
            if (l.type != Loan.Type.COMMERCIAL && l.type != Loan.Type.REVOLVER) continue;
            if (l.status == Loan.Status.CLOSED) continue;
            // gecikmis tutar anaparaya eklenir
            l.outstanding += l.accrued + l.dueAmount - l.pendingPrincipal;
            l.accrued = 0; l.dueAmount = 0; l.pendingPrincipal = 0; l.demand = 0;
            if (l.type == Loan.Type.REVOLVER) {
                // isletme kredisi bakiyesi yeni bir ticari krediye donusur
                if (l.outstanding > 1) {
                    Loan t = new Loan();
                    t.id = s.nextId++; t.type = Loan.Type.COMMERCIAL; t.bank = l.bank; t.fixed = false;
                    t.spread = l.spread + FinanceBalance.RESTRUCT_RATE; t.rate = s.macro.policyRate + t.spread;
                    t.principal = t.outstanding = l.outstanding; t.frequencyMonths = 3; t.tenorMonths = FinanceBalance.RESTRUCT_EXTEND_YEARS * 12;
                    t.startHour = s.hour; t.lastResetHour = s.hour; t.restructured = true; t.status = Loan.Status.RESTRUCTURED;
                    t.nextDueHour = c.restructProtectUntil + 3L * HPM; t.maturityHour = s.hour + (long) t.tenorMonths * HPM;
                    c.loans.add(t);
                }
                l.outstanding = 0; l.status = Loan.Status.CLOSED; l.drawnSinceHour = -1;
                continue;
            }
            l.status = Loan.Status.RESTRUCTURED; l.restructured = true; l.daysLate = 0;
            l.spread += FinanceBalance.RESTRUCT_RATE; l.rate += FinanceBalance.RESTRUCT_RATE;
            l.maturityHour = Math.max(l.maturityHour, s.hour) + (long) FinanceBalance.RESTRUCT_EXTEND_YEARS * Balance.HOURS_PER_YEAR;
            l.nextDueHour = c.restructProtectUntil + (long) l.frequencyMonths * HPM;
        }
        c.rating = Rating.CCC; c.lateNotch = false;
        c.restructures++;
        notifyFin(c, "YAPILANDIRMA: " + c.name + " — 3 ay koruma, ticari kredilerde +3 yıl vade ve +2 puan faiz, 6 ay içinde en az bir santral satışı şartı. Not CCC.");
    }

    public void rejectRestructuring(Company c) {
        c.restructOfferPending = false;
        bankrupt(c, "Yapılandırma teklifi reddedildi");
    }

    void bankrupt(Company c, String why) {
        if (c.bankrupt) return;
        c.bankrupt = true;
        for (Loan l : c.loans) if (l.open()) l.status = Loan.Status.DEFAULTED;
        if (c.player) { s.gameOver = true; s.gameOverReason = "Şirketiniz iflas etti: " + why + "."; }
        else s.sales.bankruptcy(c);
        notifyFin(c, c.name + " iflas etti: " + why + ".");
    }

    // ------------------------------------------------------------ ceyreklik
    /** Ceyrek sonu: FAVOK/faiz gecmisi, not, isletme kredisi limiti, taahhutler. */
    void quarterly(Company c) {
        if (exempt(c) || c.bankrupt) return;
        int k = c.quarters % 4;
        c.qEbitda[k] = c.qEbitdaAcc; c.qInterest[k] = c.qInterestAcc; c.qRenew[k] = c.qRenewAcc; c.qGen[k] = c.qGenAcc;
        c.qEbitdaAcc = c.qInterestAcc = c.qRenewAcc = c.qGenAcc = 0;
        c.quarters++;
        Rating old = rating(c);
        c.rating = computeRating(c);
        c.lateNotch = anyLate(c);
        if (rating(c) != old) notifyFin(c, (c.player ? "Kredi notunuz " : c.name + " kredi notu ") + old + " → " + rating(c) + (c.quarters < 4 ? " (yeni şirket)" : ""));
        Loan r = revolver(c);
        if (r != null) r.limit = Math.max(r.outstanding, revolverLimitFor(c));
        // ticari taahhut: Net borc / FAVOK
        double lev = leverage(c);
        for (Bank b : Bank.values()) {
            boolean has = false;
            for (Loan l : c.loans) if (l.open() && l.type == Loan.Type.COMMERCIAL && l.bank == b) has = true;
            int i = b.ordinal();
            if (!has) { c.bankCovenantBreach[i] = false; c.bankBreachQuarters[i] = 0; continue; }
            boolean breach = c.quarters >= 4 && lev > b.covenantLimit();
            if (breach) {
                c.bankBreachQuarters[i]++;
                if (!c.bankCovenantBreach[i]) notifyFin(c, "TAAHHÜT İHLALİ: " + b.label + " — Net borç/FAVÖK " + fmt(lev) + " > " + fmt(b.covenantLimit()) + ". Faiz +1 puan, yeni ticari kredi yok, temettü yasağı.");
                c.bankCovenantBreach[i] = true;
                for (Loan l : c.loans) if (l.open() && l.type == Loan.Type.COMMERCIAL && l.bank == b) { l.covenantPenalty = true; l.covenantBreaches++; }
                if (c.bankBreachQuarters[i] == 2) {
                    for (Loan l : c.loans) if (l.open() && l.type == Loan.Type.COMMERCIAL && l.bank == b) { l.demand += l.outstanding * FinanceBalance.COVENANT_DEMAND; }
                    notifyFin(c, b.label + " ihlal iki çeyrek sürdüğü için kalan anaparanın %25'ini erken talep etti.");
                }
            } else {
                c.bankCovenantBreach[i] = false; c.bankBreachQuarters[i] = 0;
                for (Loan l : c.loans) if (l.bank == b && l.type == Loan.Type.COMMERCIAL) l.covenantPenalty = false;
            }
            // uyari: esigin %90'i
            if (!breach && c.quarters >= 4 && lev > b.covenantLimit() * FinanceBalance.COVENANT_WARNING && c.player)
                notifyFin(c, "Taahhüt riski: Net borç/FAVÖK " + fmt(lev) + " (" + b.label + " eşiği " + fmt(b.covenantLimit()) + ")");
        }
        c.dividendBan = false;
        for (boolean b : c.bankCovenantBreach) c.dividendBan |= b;
        // ECA DSCR
        for (Loan l : new ArrayList<>(c.loans)) {
            // DSCR: odemesiz donem bittikten bir ceyrek sonra (tam bir faaliyet ceyregi) olculur
            if (!l.open() || l.type != Loan.Type.ECA || s.hour < l.graceEndHour + 3L * HPM) continue;
            Plant p = s.nodeById(l.linkedPlant) instanceof Plant pp ? pp : null;
            if (p == null) continue;
            double service = Loan.annuity(l.outstanding, l.effectiveRate(), 12 / l.frequencyMonths, l.remainingPeriods()) * 3.0 / l.frequencyMonths;
            double dscr = service <= 0 ? 99 : p.qProfitLast / service;
            l.lastDscr = dscr;
            if (dscr < FinanceBalance.ECA_DSCR) {
                l.dscrLowQuarters++;
                if (!l.dscrPenalty) notifyFin(c, "ECA taahhüdü: " + p.name() + " DSCR " + fmt(dscr) + " < 1,20 — faiz +0,5 puan.");
                l.dscrPenalty = true;
                if (l.dscrLowQuarters >= FinanceBalance.ECA_DSCR_DEFAULT_QUARTERS) ecaDefault(c, l);
            } else { l.dscrLowQuarters = 0; l.dscrPenalty = false; }
        }
    }

    // ------------------------------------------------------------ takvim
    /** Onumuzdeki 12 ayin borc servisi (aylik). */
    public double[] schedule12(Company c) {
        double[] out = new double[12];
        long monthStart = (s.hour / HPM) * HPM;
        for (Loan l : c.loans) {
            if (!l.open()) continue;
            if (l.type == Loan.Type.REVOLVER) {
                for (int m = 0; m < 12; m++) out[m] += l.outstanding * l.effectiveRate() / 12;
                out[0] += l.dueAmount + l.demand;
                continue;
            }
            out[0] += l.dueAmount + l.demand;
            double bal = l.outstanding - l.pendingPrincipal;
            long due = l.nextDueHour;
            int periodsLeft = l.remainingPeriods();
            while (due < monthStart + 12L * HPM && bal > 1) {
                int m = (int) Math.max(0, (due - monthStart) / HPM);
                double interest = bal * l.effectiveRate() * l.frequencyMonths / 12;
                double pay = due < l.graceEndHour ? interest : Loan.annuity(bal, l.effectiveRate(), 12 / l.frequencyMonths, Math.max(1, periodsLeft));
                double principal = due < l.graceEndHour ? 0 : Math.max(0, pay - interest);
                if (m < 12) out[m] += pay;
                bal -= principal;
                if (due >= l.graceEndHour) periodsLeft--;
                due += (long) l.periodHours();
            }
        }
        return out;
    }

    /** Sonraki taksit (tarih saati ve tutar). */
    public double[] nextPayment(Loan l) {
        if (l.type == Loan.Type.REVOLVER) return new double[] { (s.hour / HPM + 1) * (double) HPM, l.outstanding * l.effectiveRate() / 12 };
        if (l.dueAmount > 0) return new double[] { s.hour, l.dueAmount + l.demand };
        double interest = (l.outstanding - l.pendingPrincipal) * l.effectiveRate() * l.frequencyMonths / 12;
        double pay = l.nextDueHour < l.graceEndHour ? interest : Loan.annuity(l.outstanding, l.effectiveRate(), 12 / l.frequencyMonths, l.remainingPeriods());
        return new double[] { l.nextDueHour, pay + l.demand };
    }

    /** Vadesine 30 gun kalan taksit uyarilari (oyuncu). */
    void dueWarnings(Company c) {
        if (!c.player) return;
        for (Loan l : c.loans) {
            if (!l.open() || l.type == Loan.Type.REVOLVER) continue;
            long d = l.nextDueHour - s.hour;
            if (d > (FinanceBalance.DUE_WARNING_DAYS - 1) * 24L && d <= FinanceBalance.DUE_WARNING_DAYS * 24L) {
                double[] np = nextPayment(l);
                notifyFin(c, "Taksit yaklaşıyor: " + l.name() + " " + FinanceBalance.DUE_WARNING_DAYS + " gün sonra " + money(np[1])
                    + (c.cash + revolverRoom(c) < np[1] ? " — nakit ve işletme kredisi yetmiyor!" : ""));
            }
        }
    }

    void notifyFin(Company c, String msg) { notifyFin(c, msg, "openfin"); }

    void notifyFin(Company c, String msg, String action) {
        if (c.player) s.notify("FİNANS: " + msg, action);
        else if (msg.contains("iflas") || msg.startsWith("TEMERRÜT") || msg.startsWith("YAPILANDIRMA")) s.notify("FİNANS: " + (msg.contains(c.name) ? "" : c.name + " — ") + msg);
    }

    static String money(double v) { return String.format(Locale.ROOT, "%.1fM $", v / 1e6).replace('.', ','); }
    static String fmt(double v) { return Double.isInfinite(v) ? "∞" : String.format(Locale.ROOT, "%.1f", v).replace('.', ','); }
}
