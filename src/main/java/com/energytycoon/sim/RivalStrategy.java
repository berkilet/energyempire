package com.energytycoon.sim;

import java.util.Random;

/**
 * Rakip yatirim stratejisi: sinirli rasyonellik. Her sirket KENDI beklentileriyle tutarli karar verir (kendi gozunde
 * degeri negatif proje yapmaz), ama beklentileri, ogrenme hizlari, risk istahlari ve aliskanliklari her oyunda farkli
 * cizilir: kimi fosil kokenli sirket karbonu gec ciddiye alir ve zorlanir, kimi erken donusur; kimi yesil sirket kitlik
 * doneminde gaza yonelir. Ara sira yonetim degisikligi, buyume atagi, temkin donemi, teknoloji bahsi gibi stratejik
 * surprizler olur. Boylece her oyun farkli akar; hicbir sirketin kaderi bastan belli degildir.
 * <ul>
 *   <li>Teknoloji secimi her kararda projenin beklenen getirisine dayanir (NBD / yatirim): elektrik fiyati, yakit,
 *       karbon fiyati ve saha verimi. Karbon fiyatinin ne kadar hizli artacagi sirketin <b>kendi beklentisidir</b>
 *       (carbonBelief: gercek artis hizina oran); fosil kokenli sirket baslangicta karbonu hafife alir.</li>
 *   <li>Her ocak sirket ogrenir: karbon beklentisi gozlenen fiyata yaklasir (kendi fosil santralleri zarar ettiyse
 *       daha hizli), piyasadaki teknolojilerin gecen yilki getirisi teknoloji gorusunu gunceller, tarzin agirligi
 *       azalir. Yonelim degisirse (ör. fosilden yenilenebilire) haber olarak duyurulur.</li>
 *   <li>Zarar eden ve beklentisi de olumsuz santraller satisa cikarilir ya da omrunun sonuna yakinsa kapatilir.</li>
 * </ul>
 * Oyuncuyla ayni bilgi: piyasa fiyati gecmisi, ilan edilen karbon/yakit fiyatlari ve santrallerin kamuya acik
 * sonuclari. Hile yok; yalnizca karar kurali.
 */
public final class RivalStrategy {
    private RivalStrategy() {}

    static final PlantType[] TYPES = PlantType.values();
    /** Karbon fiyatinin elektrik fiyatina yansima orani ($/MWh basina $/ton; marjinal gaz santrali ~0,4 t/MWh). */
    static final double CARBON_PASS_THROUGH = 0.35;
    /** Sermaye maliyeti: politika faizi + proje risk primi + kredi notu primi (sirketin gercek borclanma kosuluna gore). */
    public static double costOfCapital(GameState s, Company c) {
        return Math.max(0.06, s.macro.policyRate + 0.02 + s.finance.rating(c).premium());
    }
    /** Bu getiri endeksinin (NBD/yatirim) altinda proje yapilmaz. */
    /** Getiri endeksi (NBD / yatirim) bunun altindaysa proje yapilmaz: deger yaratmayan yatirim yok. */
    static final double HURDLE = 0.0; // referans (sirketlerin kendi esigi Company.hurdle)
    /** Bu kaldiracin (Net borc / FAVOK) ustunde yeni santral yatirimi yapilmaz. */
    static final double MAX_BUILD_LEVERAGE = 3.0;
    /** Yenilenebilirde uretim profili nedeniyle ortalama fiyata gore elde edilen fiyat orani. */
    static double captureRate(GameState s, PlantType t) {
        // yenilenebilir payi arttikca gunes/ruzgar ayni saatlerde uretir ve fiyati kendisi dusurur (kanibalizasyon)
        double share = Math.max(0, Math.min(1, s.statRenewShare));
        return t == PlantType.SOLAR ? 0.88 - 0.45 * share : t == PlantType.WIND ? 0.95 - 0.30 * share : t == PlantType.HYDRO ? 1.05 : 1.0;
    }

    /** Ayni havzada (sehrin 8 kare cevresinde) her mevcut hidro santral akisi paylasir. */
    static double hydroSharing(GameState s, City city) {
        int n = 0;
        for (Plant p : s.plants) if (p.type == PlantType.HYDRO && Math.hypot(p.x - city.x, p.y - city.y) <= 10) n++;
        return Math.pow(0.75, n);
    }

    /**
     * Piyasa kiyaslamasi: bu turdeki (eski filo haric) tam yil calismis santrallerin gecen yilki isletme nakdi
     * (kar + sabit gider) / MW-yil. Veri yoksa NaN. Oyuncunun da gorebildigi kamuya acik sonuclar.
     */
    public static double observedCashPerMw(GameState s, PlantType t) {
        double cash = 0, mw = 0; int n = 0;
        for (Plant p : s.plants) {
            if (p.type != t || p.legacy || !fullYear(s, p)) continue;
            cash += p.lastYearProfit - p.lastYearCapacity + p.equipment.fixedOmPerHour() * Balance.HOURS_PER_YEAR;
            mw += p.capacityMW(); n++;
        }
        return n >= 2 && mw > 0 ? cash / mw : Double.NaN;
    }

    /** Tarza gore baslangic egilimi (getiri endeksine eklenen tercih puani). */
    static double prior(String style, PlantType t) {
        switch (style) {
            // fosil kokenli: yakit tedarik ve isletme deneyimi, esnek kapasiteye guven; yesil: proje gelistirme deneyimi
            case "fossil": return switch (t) { case GAS -> 0.10; case COAL -> 0.07; case NUCLEAR -> 0.03; default -> 0; };
            case "green": return switch (t) { case SOLAR, WIND -> 0.10; case HYDRO -> 0.06; default -> 0; };
            default: return switch (t) { case GAS -> 0.03; default -> 0; };
        }
    }

    /** Ilk kurulum (kayitlarda alanlar yoksa da). Sirkete ozgu tohumla: oyunun rastgele sayi akisina dokunmaz. */
    public static void init(Company c, long seed) {
        Random r = new Random(seed * 31 + c.name.hashCode());
        c.techView = new double[TYPES.length];
        c.priorWeight = 1;
        // Karakter her oyunda yeniden cizilir; koken yalnizca ortalamayi kaydirir (genis dagilim: surprizler).
        double beliefMean = switch (c.aiStyle) { case "fossil" -> 0.62; case "green" -> 1.10; default -> 0.85; };
        c.carbonBelief = clamp(beliefMean + 0.22 * r.nextGaussian(), 0.25, 1.45);
        c.learnRate = 0.08 + 0.42 * r.nextDouble();                 // kimi hizli, kimi cok yavas ogrenir
        c.hurdleBase = clamp(0.02 * r.nextGaussian() + (r.nextDouble() < 0.3 ? -0.06 : 0.02), -0.12, 0.10);
        c.hurdle = c.hurdleBase;
        c.scarcityView = 0.5 + r.nextDouble();                       // arz sikisikligina duyarlilik
        c.styleStrength = 0.4 + 1.4 * r.nextDouble();                // kokenine bagliligi
        c.orientation = orientation(c);
    }

    /**
     * Ortalama elektrik fiyati beklentisi: son 12 ayin ortalamasi, arz-talep gorunumuyle duzeltilmis. Insaattaki
     * santraller dahil beklenen uretim talebi asiyorsa fiyat duser (yatirimcilar boru hattini izler; herkes ayni anda
     * kurarsa fiyat coker).
     */
    static double expectedPrice(GameState s, Company c) {
        java.util.ArrayList<Double> m = s.history.monthlyPrice;
        double p0;
        if (m.isEmpty()) p0 = s.statPrice;
        else {
            double sum = 0; int n = 0;
            for (int i = Math.max(0, m.size() - 12); i < m.size(); i++) { sum += m.get(i); n++; }
            p0 = sum / n;
        }
        return p0 * (1 + (supplyFactor(s) - 1) * c.scarcityView);
    }

    /** Son 12 ayin ortalama fiyati (arz duzeltmesi olmadan). */
    static double averagePrice(GameState s) {
        java.util.ArrayList<Double> m = s.history.monthlyPrice;
        if (m.isEmpty()) return s.statPrice;
        double sum = 0; int n = 0;
        for (int i = Math.max(0, m.size() - 12); i < m.size(); i++) { sum += m.get(i); n++; }
        return sum / n;
    }

    /** Arz fazlasi/kitligi gecicidir: etkisi ~4 yilda sonumlenir (eski santraller kapanir, yeni yatirim gelir). */
    static final double SUPPLY_DECAY_YEARS = 4;
    /** Cesitlendirme: filodaki pay basina karar puani cezasi (tum filo tek teknolojiyse -0,45). */
    static final double DIVERSIFY = 0.45;

    /**
     * Beklenen kapasite fiyati ($/MW-yil), t yil sonra: bugunku fiyattan uzun donem dengesine (sistem hedef yedege doner;
     * fiyat referans degerin ~%80'i) gecici farkin sonumlenmesiyle. Kitliga duyarli sirket bugunku fiyata daha cok guvenir.
     */
    static double expectedCapacityPrice(GameState s, Company c, double t) { return expectedCapacityPrice(s, c.scarcityView, t); }

    static double expectedCapacityPrice(GameState s, double scarcityView, double t) {
        double longRun = GridBalance.CAPACITY_CONE * 0.8;
        double decay = Math.exp(-t / (SUPPLY_DECAY_YEARS * Math.max(0.5, scarcityView)));
        return longRun + (s.capacityPrice - longRun) * decay;
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    /**
     * Guvenilir kapasite yedegi -> fiyat carpani. Puant talep (son gunlerin en yuksegi, buyume payiyla) ile insaattakiler
     * dahil guvenilir kapasite (termik %90, hidro %60, ruzgar %15, gunes %5) kiyaslanir. ~%15 yedekte 1; yedek
     * buyudukce fiyat beklentisi duser, kitlikta yukselir.
     */
    static double supplyFactor(GameState s) {
        double peak = 0;
        for (int i = 0; i < s.history.count; i++) peak = Math.max(peak, s.history.demand[History.HOURS - 1 - i]);
        if (peak <= 0) return 1;
        peak *= 1.05;
        double firm = 0;
        for (Plant p : s.plants) {
            if (p.legacy && p.retireYear > 0 && p.retireYear <= s.year() + 3) continue; // yakinda kapanacak eski filo
            double k = GridBalance.derate(p.type);
            firm += p.capacityMW() * k;
        }
        double ratio = firm / peak;
        // gozlenen puant marjiyla (kapasite mekanizmasinin olctugu) harmanla: katsayilar bu sistemde fazla temkinli olabilir
        ratio = 0.5 * ratio + 0.5 * (1 + s.capacityMargin);
        return Math.max(0.4, Math.min(1.3, 1 + 1.2 * (1.15 - ratio)));
    }

    /** Sehir cevresinde yenilenebilir icin ulasilabilir en iyi saha verimi (rastgelelik kullanmadan kaba tarama). */
    static double bestSiteCf(GameState s, Equipment e, City city) {
        double best = 0;
        for (int dy = -8; dy <= 8; dy += 2) for (int dx = -8; dx <= 8; dx += 2) {
            int x = city.x + dx, y = city.y + dy;
            if (!s.map.inBounds(x, y) || Math.hypot(dx, dy) < 2 || s.checkPlacement(e.type, x, y) != null) continue;
            best = Math.max(best, s.siteScore(e, x, y));
        }
        return best;
    }

    /**
     * Projenin getiri endeksi = NBD / yatirim, sirketin kendi karbon beklentisiyle. Termik santrallerde calisma orani
     * fiyat-marjinal maliyet farkindan; yenilenebilirde saha veriminden.
     */
    public static double projectIndex(GameState s, Company c, Equipment e, City city) {
        return evaluate(s, c, e, city, -1, -1).index;
    }

    /** Proje degerlendirmesi (oyuncunun kurulum penceresi de ayni hesabi gorur). */
    public static final class Evaluation {
        /** NBD / yatirim. */
        public double index;
        /** Ilk tam yil: uretim (MWh), kapasite faktoru, isletme nakdi (enerji + kapasite - sabit gider), kapasite geliri. */
        public double mwh1, cf1, ebitda1, capacity1, npv;
        /** Ilk 15 yilin ortalama isletme nakdi (banka borc kapasitesi bununla olculur). */
        public double avgEbitda;
    }

    /**
     * x,y >= 0 ise belirli saha (o karenin verimi ve yakit indirimi); aksi halde sehir cevresinde ulasilabilir en iyi saha.
     */
    public static Evaluation evaluate(GameState s, Company c, Equipment e, City city, int sx, int sy) {
        return evaluate(s, c, e, city, sx, sy, c.carbonBelief, c.scarcityView);
    }

    /**
     * Bankanin tarafsiz bakisiyla degerlendirme (kredi boyutlandirmasi): karbon fiyat yolu ilan edildigi gibi,
     * arz durumuna ortalama duyarlilik. Sirketin kendi iyimserligi ya da kotumserligi krediyi etkilemez.
     */
    public static Evaluation evaluateBank(GameState s, Company c, Equipment e, City city, int sx, int sy) {
        return evaluate(s, c, e, city, sx, sy, 1.0, 1.0);
    }

    private static Evaluation evaluate(GameState s, Company c, Equipment e, City city, int sx, int sy, double belief, double scarcity) {
        Evaluation ev = new Evaluation();
        PlantType t = e.type;
        boolean site = sx >= 0 && sy >= 0;
        Plant probe = new Plant();
        probe.type = t; probe.equipment = e; probe.owner = c; probe.x = site ? sx : city.x; probe.y = site ? sy : city.y;
        probe.builtAtHour = s.hour; probe.ageStartHour = s.hour;
        double pAvg = averagePrice(s), sfNow = 1 + (supplyFactor(s) - 1) * scarcity, carbon0 = s.markets.carbonPrice;
        double growth = Balance.CARBON_GROWTH_YEAR * s.markets.carbonGrowthMult * belief;
        double co2 = probe.co2PerMWh(s.hour);
        double mcNoCarbon = s.marginalCost(probe) - carbon0 * co2;
        double cf = t.renewable ? (site ? s.siteScore(e, sx, sy) : bestSiteCf(s, e, city)) : 0;
        if (t.renewable && cf <= 0) { ev.index = Double.NEGATIVE_INFINITY; return ev; }
        if (t == PlantType.HYDRO) cf *= hydroSharing(s, city);
        double capture = captureRate(s, t);
        // ilk yilin model nakdi piyasadaki gercek sonuclarla olceklenir (puant fiyatlari, kisinti, ariza gibi modelin gormedikleri)
        double obs = observedCashPerMw(s, t), calib = 1;
        int years = Math.min(t.lifeYears, 40);
        double capex = e.capex(), fixed = e.fixedOmPerHour() * Balance.HOURS_PER_YEAR, npv = -capex;
        double disc = costOfCapital(s, c);
        double build = e.buildDays() / 365.0;
        double sum15 = 0; int n15 = 0;
        for (int y = 0; y <= years; y++) {
            double tt = y + build;
            double carbon = carbon0 + growth * tt;
            double p0 = pAvg * (1 + (sfNow - 1) * Math.exp(-tt / SUPPLY_DECAY_YEARS));
            double price = p0 + CARBON_PASS_THROUGH * (carbon - carbon0) * Math.min(1, belief + 0.3);
            double mwh, margin;
            if (t.renewable) {
                mwh = e.capacityMW * cf * Balance.HOURS_PER_YEAR;
                margin = price * capture - t.varOm;
            } else {
                double mc = mcNoCarbon + carbon * co2;
                double spread = price - mc;
                double run = t == PlantType.NUCLEAR ? (spread > 0 ? 0.9 : 0.4)
                    : 0.12 + 0.73 / (1 + Math.exp(-spread / Math.max(4, 0.15 * price)));
                mwh = e.capacityMW * run * Balance.HOURS_PER_YEAR;
                // calistigi saatler fiyatin yuksek oldugu saatlere kayar: kucuk bir puant primi
                margin = Math.max(spread, 0) * 0.9 + 0.08 * price * (1 - run);
            }
            double operating = mwh * margin;
            // kapasite mekanizmasi: guvenilir kapasite x beklenen birim fiyat (bugunku fiyatla uzun donem referansin karisimi;
            // sirketin kitlik duyarliligi agirligi belirler)
            double capRev = e.capacityMW * GridBalance.derate(t) * expectedCapacityPrice(s, scarcity, tt);
            if (y == 0) {
                // y=0: yalnizca kalibrasyon (bugunku kosullarda bir yil)
                if (!Double.isNaN(obs) && operating > 0) calib = Math.max(0.75, Math.min(1.25, obs * e.capacityMW / operating));
                continue;
            }
            if (y == 1) {
                ev.mwh1 = mwh; ev.cf1 = mwh / (e.capacityMW * Balance.HOURS_PER_YEAR);
                ev.ebitda1 = operating * calib + capRev - fixed; ev.capacity1 = capRev;
            }
            npv += (operating * calib + capRev - fixed) / Math.pow(1 + disc, tt);
            if (y <= 15) { sum15 += operating * calib + capRev - fixed; n15++; }
        }
        ev.avgEbitda = n15 > 0 ? sum15 / n15 : 0;
        ev.npv = npv; ev.index = npv / capex;
        return ev;
    }

    /** Karar puani: getiri + tarz egilimi (zamanla azalan) + ogrenilmis teknoloji gorusu. */
    static double utility(Company c, PlantType t, double index) {
        return index + prior(c.aiStyle, t) * c.styleStrength * c.priorWeight + c.techView[t.ordinal()];
    }

    /** Hedef sehir icin en cok tercih edilen uc ekipman (getiri esigini gecenler), tercih sirasiyla. */
    public static Equipment[] candidates(GameState s, Company c, City city) {
        if (c.techView == null) init(c, s.seed);
        Equipment[] best = new Equipment[3];
        // borc disiplini: konsolide Net borc / FAVOK yuksekse yeni proje yok (once borc azaltilir)
        // (gelisme asamasindaki, henuz FAVOK'u olusmamis sirkete uygulanmaz: iki yillik isletme gecmisi gerekir)
        if (c.quarters >= 8 && s.finance.leverage(c) > MAX_BUILD_LEVERAGE) return best;
        // portfoy cesitlendirmesi: kendi filosunda payi buyuk teknolojiye yeni yatirim daha az cazip (hava/fiyat riski yogunlasir)
        double[] mix = new double[TYPES.length]; double tot = 0;
        for (Plant p : s.plants) if (p.owner == c) { mix[p.type.ordinal()] += p.capacityMW(); tot += p.capacityMW(); }
        double[] bu = { -1e9, -1e9, -1e9 };
        for (PlantType t : TYPES) {
            Equipment e = bestModel(s, c, t, city);
            if (e == null) continue;
            // nukleer: ozkaynak payi (~%40) nakitte olmali; kalani kredi/ECA ile (sirketin normal finansman kurallari)
            if (t == PlantType.NUCLEAR && c.cash < e.capex() * 0.4) continue;
            double idx = projectIndex(s, c, e, city);
            if (idx < c.hurdle) continue;   // sirket kendi beklentisiyle esigini gecmeyen projeyi yapmaz
            double u = utility(c, t, idx) + s.rng.nextGaussian() * 0.01;
            if (tot > 0) u -= DIVERSIFY * mix[t.ordinal()] / tot;
            for (int k = 0; k < 3; k++) if (u > bu[k]) {
                for (int m = 2; m > k; m--) { bu[m] = bu[m - 1]; best[m] = best[m - 1]; }
                bu[k] = u; best[k] = e; break;
            }
        }
        return best;
    }

    /** Bir tur icin getiri endeksi en yuksek ekipman modeli (yesil kokenli yeniye, digerleri ucuza hafif egilimli). */
    static Equipment bestModel(GameState s, Company c, PlantType t, City city) {
        Equipment best = null; double bi = Double.NEGATIVE_INFINITY;
        Equipment newest = s.catalog.newest(t, s.year()), cheapest = s.catalog.cheapest(t, s.year());
        for (Equipment e : s.catalog.available(t, s.year())) {
            double i = projectIndex(s, c, e, city);
            if (c.aiStyle.equals("green") && e == newest) i += 0.01;
            if (c.aiStyle.equals("fossil") && e == cheapest) i += 0.01;
            if (i > bi) { bi = i; best = e; }
        }
        return best;
    }

    /** Ihale icin: ruzgar ve gaz arasinda karar puani yuksek olan. */
    public static Equipment tenderChoice(GameState s, Company c, City city) {
        if (c.techView == null) init(c, s.seed);
        Equipment w = bestModel(s, c, PlantType.WIND, city), g = bestModel(s, c, PlantType.GAS, city);
        if (w == null || g == null) return w != null ? w : g != null ? g : RivalAI.pick(s, c, PlantType.GAS);
        double uw = utility(c, PlantType.WIND, projectIndex(s, c, w, city)), ug = utility(c, PlantType.GAS, projectIndex(s, c, g, city));
        return uw >= ug ? w : g;
    }

    // ------------------------------------------------------------ yillik ogrenme
    /** Ocak ayinda: beklentileri gunceller, yonelim degistiyse duyurur, zarar eden santralleri gozden gecirir. */
    public static void yearly(GameState s, Company c) {
        if (c.techView == null) init(c, s.seed);
        double lr = c.learnRate;
        // 1) karbon beklentisi: gozlenen fiyata yaklasir; kendi fosil filosu zarar ettiyse ders daha sert
        double fossilLoss = 0, fossilCapex = 0;
        for (Plant p : s.plants) if (p.owner == c && !p.type.renewable && p.type != PlantType.NUCLEAR && fullYear(s, p)) {
            fossilCapex += p.equipment.capex();
            if (p.lastYearProfit < 0) fossilLoss += -p.lastYearProfit;
        }
        double pain = fossilCapex > 0 ? Math.min(1, fossilLoss / fossilCapex * 10) : 0;
        // ogrenme gurultulu: kimi yil yanlis ders cikarilir
        c.carbonBelief = clamp(c.carbonBelief + (1 - c.carbonBelief) * Math.min(1, lr * (1 + 1.5 * pain)) + 0.04 * s.rng.nextGaussian(), 0.25, 1.5);
        // risk istahi zamanla kendi karakterine doner
        c.hurdle += 0.35 * (c.hurdleBase - c.hurdle);
        if (!c.player) surprise(s, c, pain);
        // 2) teknoloji gorusu: piyasadaki santrallerin gecen yilki getirisi (kar / yatirim), ortalamaya gore
        double[] roi = new double[TYPES.length], cap = new double[TYPES.length];
        double tot = 0, totCap = 0;
        for (Plant p : s.plants) {
            if (p.legacy || !fullYear(s, p)) continue;
            double k = p.equipment.capex();
            roi[p.type.ordinal()] += p.lastYearProfit; cap[p.type.ordinal()] += k;
            tot += p.lastYearProfit; totCap += k;
        }
        double mean = totCap > 0 ? tot / totCap : 0;
        for (PlantType t : TYPES) {
            int i = t.ordinal();
            double target = cap[i] > 0 ? Math.max(-0.4, Math.min(0.4, (roi[i] / cap[i] - mean) * 2.5)) : c.techView[i] * 0.8;
            c.techView[i] += lr * (target - c.techView[i]);
        }
        // 3) tarzin agirligi azalir (kimlik tamamen kaybolmaz)
        // kendi fosil santralleri zarar ediyorsa alisilmis tercihlerden daha hizli vazgecilir
        c.priorWeight = Math.max(0.2, c.priorWeight * (1 - 0.35 * lr) * (1 - 0.45 * pain));
        // yonelim: yeni egilim iki yil ust uste surerse duyurulur (gurultuye karsi)
        String o = orientationNow(s, c);
        if (o.equals(c.orientation)) c.pendingOrientation = null;
        else if (o.equals(c.pendingOrientation)) {
            if (!c.player) s.notify("STRATEJİ: " + c.name + " yatırım stratejisini değiştirdi: " + c.orientation + " → " + o + reason(s, c, pain, o));
            c.orientation = o; c.pendingOrientation = null;
        } else c.pendingOrientation = o;
        // 4) portfoy: zarar eden ve beklentisi de olumsuz santraller
        if (!c.player) review(s, c);
    }

    /** Stratejik surprizler (sirket basina yilda ~%9): oyuncunun ezberleyemeyecegi kararlar. */
    static void surprise(GameState s, Company c, double pain) {
        if (s.rng.nextDouble() > 0.09) return;
        int kind = s.rng.nextInt(5);
        switch (kind) {
            case 0 -> { // yonetim degisikligi: karakter yeniden cizilir, karbon beklentisi tazelenir
                c.carbonBelief = clamp(0.95 + 0.2 * s.rng.nextGaussian(), 0.5, 1.4);
                c.hurdleBase = clamp(0.03 * s.rng.nextGaussian(), -0.08, 0.08); c.hurdle = c.hurdleBase;
                c.styleStrength = 0.3 + s.rng.nextDouble(); c.learnRate = 0.15 + 0.35 * s.rng.nextDouble();
                s.notify("STRATEJİ: " + c.name + " yönetimi değişti; yeni yönetim yatırım planını baştan gözden geçiriyor.");
            }
            case 1 -> { // buyume atagi: dusuk getiriyi de kabul eder (pazar payi icin)
                c.hurdle = c.hurdleBase - 0.10;
                s.notify("STRATEJİ: " + c.name + " agresif büyüme planı açıkladı: pazar payı için yatırımları hızlandıracak.");
            }
            case 2 -> { // temkin donemi
                c.hurdle = c.hurdleBase + 0.12;
                s.notify("STRATEJİ: " + c.name + " temkinli döneme girdi: yeni yatırımları erteliyor, borç azaltacak.");
            }
            case 3 -> { // teknoloji bahsi: piyasada geri planda kalmis bir teknolojiye yonelir
                PlantType[] bets = { PlantType.GAS, PlantType.WIND, PlantType.SOLAR, PlantType.HYDRO, PlantType.NUCLEAR, PlantType.COAL };
                PlantType t = bets[s.rng.nextInt(bets.length)];
                if (t == PlantType.COAL && c.carbonBelief > 0.8) t = PlantType.GAS;
                c.techView[t.ordinal()] += 0.15;
                s.notify("STRATEJİ: " + c.name + " " + t.label.toLowerCase(java.util.Locale.ROOT) + " yatırımlarına odaklanacağını açıkladı.");
            }
            default -> { // karbon tartismasi: beklenti bir yone sapar
                double d = (s.rng.nextBoolean() ? 1 : -1) * (0.15 + 0.15 * s.rng.nextDouble());
                c.carbonBelief = clamp(c.carbonBelief + d, 0.25, 1.5);
                s.notify("STRATEJİ: " + c.name + (d > 0 ? " karbon fiyatlarının beklenenden hızlı artacağını öngörüyor." : " karbon fiyatı artışının yavaşlayacağına bahis oynuyor."));
            }
        }
    }

    static boolean fullYear(GameState s, Plant p) {
        return p.operational() && s.hour - p.builtAtHour - (long) p.equipment.buildDays() * 24 >= Balance.HOURS_PER_YEAR;
    }

    /** Duyurudaki gerekce: degisimi en cok neyin surukledigi. */
    private static String reason(GameState s, Company c, double pain, String to) {
        double sf = supplyFactor(s);
        if (to.startsWith("yenilenebilir") && pain > 0.2) return " (fosil santralleri karbon maliyeti yüzünden zarar ediyor)";
        if (to.startsWith("fosil") && sf > 1.05) return " (arz sıkışıyor: esnek kapasite değer kazandı)";
        if (to.startsWith("yenilenebilir") && sf < 0.95) return " (piyasada arz fazlası: yakıt ve karbon maliyeti olmayan üretim öne çıktı)";
        if (to.startsWith("yenilenebilir")) return " (karbon fiyatı beklentisini yükseltti)";
        return " (piyasa beklentileri güncellendi)";
    }

    /** Tablolar icin kisa etiket. */
    public static String shortLabel(String orientation) {
        return orientation.startsWith("yenilenebilir") ? "Yenilenebilir" : orientation.startsWith("fosil") ? "Fosil" : "Dengeli";
    }

    /** Baslangic yonelimi (tarzdan); sonraki yillarda gercek tercihlerden hesaplanir. */
    public static String orientation(Company c) {
        return switch (c.aiStyle) { case "fossil" -> "fosil ağırlıklı"; case "green" -> "yenilenebilir ağırlıklı"; default -> "dengeli"; };
    }

    /** Bugun yatirim yapsa neyi secerdi: en iyi fosil ve en iyi yenilenebilir secenegin karar puani farki (en buyuk sehirde). */
    static String orientationNow(GameState s, Company c) {
        City ref = null;
        for (City ct : s.cities) if (ref == null || ct.population > ref.population) ref = ct;
        if (ref == null) return orientation(c);
        double fossil = -9, green = -9;
        for (PlantType t : TYPES) {
            if (t == PlantType.NUCLEAR) continue;
            Equipment e = bestModel(s, c, t, ref);
            if (e == null) continue;
            double u = utility(c, t, projectIndex(s, c, e, ref));
            if (t.renewable) green = Math.max(green, u); else fossil = Math.max(fossil, u);
        }
        double d = green - fossil;
        return d > 0.12 ? "yenilenebilir ağırlıklı" : d < -0.12 ? "fosil ağırlıklı" : "dengeli";
    }

    /** Zarar eden santral: kalan omurdeki isletme nakdi de negatifse omrunun %60'ini gecmisse kapatilir, degilse satisa cikar. */
    static void review(GameState s, Company c) {
        for (Plant p : new java.util.ArrayList<>(s.plants)) {
            if (p.owner != c || p.legacy || p.tenderId != 0 || !fullYear(s, p) || p.lastYearProfit >= 0) continue;
            if (s.sales.openListingOf(p) != null) continue;
            City city = RivalAI.nearestCity(s, p.x, p.y);
            if (city == null) continue;
            // kalan omur boyunca beklenen nakit (yatirim batik): getiri endeksi * yatirim + yatirim = beklenen NBD'nin isletme kismi
            double fwd = (projectIndex(s, c, p.equipment, city) + 1) * p.equipment.capex();
            double lifeShare = (s.hour - p.ageStartHour) / (double) (p.type.lifeYears * Balance.HOURS_PER_YEAR);
            // yatirim batik maliyettir: kalan omurde isletme nakdi (sabit giderler dahil) hala pozitifse santral tutulur
            if (fwd > 0) continue;
            if (lifeShare > 0.6) {
                s.notify("STRATEJİ: " + c.name + " zarar eden " + p.name() + " santralini kapattı.");
                s.demolishPlant(p, true);
            } else if (s.sales.list(c, p) == null) {
                s.notify("STRATEJİ: " + c.name + " zarar eden " + p.name() + " santralini satışa çıkardı.");
            }
        }
    }
}
