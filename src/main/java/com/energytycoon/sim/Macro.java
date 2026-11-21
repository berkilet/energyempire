package com.energytycoon.sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Makro ortam: politika faizi (uc rejim, iki ayda bir toplanti), kuresel olaylar, hisse endeksi ve 12 aylik piyasa beklentisi.
 * Gelecek 12 ayin rejimleri onceden cekilir; piyasa beklentisi bu gercek yola %70 isabetle dayanir (gurultulu tahmin).
 */
public final class Macro {
    public enum Regime {
        EASING("Gevşeme", FinanceBalance.TARGET_EASING), NEUTRAL("Nötr", FinanceBalance.TARGET_NEUTRAL), TIGHTENING("Sıkılaşma", FinanceBalance.TARGET_TIGHTENING);
        public final String label; public final double target;
        Regime(String l, double t) { label = l; target = t; }
    }

    public enum EventType {
        CRISIS("Küresel finans krizi"), INFLATION("Enflasyon şoku"), ENERGY("Enerji krizi"), COUNTRY("Ülke riski şoku"), GREEN_WAVE("Yeşil yatırım dalgası");
        public final String label;
        EventType(String l) { label = l; }
    }

    public static final class Event {
        public EventType type;
        public int monthsLeft;
        public Event() {}
        Event(EventType t, int m) { type = t; monthsLeft = m; }
    }

    private final GameState s;
    public double policyRate = FinanceBalance.POLICY_START;
    public Regime regime = Regime.NEUTRAL;
    /** Onumuzdeki 12 ayin (cekilmis) rejimleri; ilki gelecek ay. */
    public final ArrayDeque<Regime> future = new ArrayDeque<>();
    public final List<Event> events = new ArrayList<>();
    public double equityIndex = 100;
    public int lastCrisisYear = -99, lastInflationYear = -99;
    /** Gaz soku olaganustu artisi uygulandi mi (soku boyunca bir kez). */
    public boolean energyHikeApplied;
    public final List<Double> rateHistory = new ArrayList<>(), indexHistory = new ArrayList<>();
    /** 12 aylik beklenen faiz yolu ve tahminin dayandigi rejim. */
    public double[] expectedPath = new double[12];
    public Regime expectedRegime = Regime.NEUTRAL;
    /** Olcum: olay sayaclari ve gorulen rejimler (rapor/test). */
    public final transient int[] eventCounts = new int[EventType.values().length];
    public final transient int[] regimeMonths = new int[Regime.values().length];

    Macro(GameState s) { this.s = s; }

    void init() {
        future.clear();
        Regime r = regime;
        for (int i = 0; i < 12; i++) { r = nextRegime(r); future.add(r); }
        rateHistory.add(policyRate); indexHistory.add(equityIndex);
        forecast();
    }

    private Regime nextRegime(Regime r) {
        if (s.rng.nextDouble() >= FinanceBalance.REGIME_SWITCH_CHANCE) return r;
        // Dongu egilimi: notrden iki yone, uclardan genelde notre (bazen dogrudan diger uca)
        return switch (r) {
            case NEUTRAL -> s.rng.nextBoolean() ? Regime.EASING : Regime.TIGHTENING;
            case EASING -> s.rng.nextDouble() < 0.75 ? Regime.NEUTRAL : Regime.TIGHTENING;
            case TIGHTENING -> s.rng.nextDouble() < 0.75 ? Regime.NEUTRAL : Regime.EASING;
        };
    }

    public boolean active(EventType t) {
        for (Event e : events) if (e.type == t) return true;
        return false;
    }

    public int monthsLeft(EventType t) {
        int m = 0;
        for (Event e : events) if (e.type == t) m = Math.max(m, e.monthsLeft);
        return m;
    }

    /** Kredi primlerine olay eki (kriz +3, ulke riski +2 puan). */
    public double eventSpread() {
        return (active(EventType.CRISIS) ? FinanceBalance.CRISIS_SPREAD : 0) + (active(EventType.COUNTRY) ? FinanceBalance.COUNTRY_SPREAD : 0);
    }

    public double greenDiscount() { return active(EventType.GREEN_WAVE) ? FinanceBalance.GREEN_DISCOUNT_WAVE : FinanceBalance.GREEN_DISCOUNT; }

    public double ecaPremiumMult() { return active(EventType.COUNTRY) ? 2 : 1; }

    /** Saatlik: gaz soku baslangicinda olaganustu artis (enerji krizi olayi soku boyunca surer). */
    void hourly() {
        boolean shock = s.markets.gasShock();
        if (shock && !energyHikeApplied) {
            energyHikeApplied = true;
            double hike = FinanceBalance.ENERGY_HIKE_MIN + s.rng.nextDouble() * (FinanceBalance.ENERGY_HIKE_MAX - FinanceBalance.ENERGY_HIKE_MIN);
            hike = Math.round(hike / 0.0025) * 0.0025;
            double old = policyRate;
            policyRate = clamp(policyRate + hike);
            events.add(new Event(EventType.ENERGY, 0));
            eventCounts[EventType.ENERGY.ordinal()]++;
            s.notify("FİNANS: Enerji krizi — merkez bankası olağanüstü toplantıda faizi " + pct(old) + "'den " + pct(policyRate) + "'e yükseltti.");
        } else if (!shock && energyHikeApplied) {
            energyHikeApplied = false;
            events.removeIf(e -> e.type == EventType.ENERGY);
            s.notify("FİNANS: Enerji krizi sona erdi.");
        }
    }

    /** Aylik: rejim, olaylar, toplanti, endeks, beklenti. */
    void monthly() {
        int year = (int) s.year();
        // rejim: gelecek kuyrugundan
        regime = future.poll();
        future.add(nextRegime(future.isEmpty() ? regime : future.peekLast()));
        // olaylar: sureler
        for (Iterator<Event> it = events.iterator(); it.hasNext(); ) {
            Event e = it.next();
            if (e.type == EventType.ENERGY) continue;
            if (--e.monthsLeft <= 0) { it.remove(); s.notify("FİNANS: " + e.type.label + " sona erdi."); }
        }
        // yeni olaylar (zorluktan bagimsiz)
        if (!active(EventType.CRISIS) && year != lastCrisisYear && s.rng.nextDouble() < FinanceBalance.CRISIS_CHANCE) {
            lastCrisisYear = year;
            start(EventType.CRISIS, FinanceBalance.CRISIS_MIN, FinanceBalance.CRISIS_MAX);
            equityIndex *= 1 - FinanceBalance.CRISIS_EQUITY_DROP;
        }
        if (!active(EventType.INFLATION) && year != lastInflationYear && s.rng.nextDouble() < FinanceBalance.INFLATION_CHANCE) {
            lastInflationYear = year;
            start(EventType.INFLATION, FinanceBalance.INFLATION_MIN, FinanceBalance.INFLATION_MAX);
        }
        if (!active(EventType.COUNTRY) && s.rng.nextDouble() < FinanceBalance.COUNTRY_CHANCE) start(EventType.COUNTRY, FinanceBalance.COUNTRY_MIN, FinanceBalance.COUNTRY_MAX);
        if (!active(EventType.GREEN_WAVE) && s.rng.nextDouble() < FinanceBalance.GREEN_WAVE_CHANCE) start(EventType.GREEN_WAVE, FinanceBalance.GREEN_WAVE_MIN, FinanceBalance.GREEN_WAVE_MAX);
        // olaylarin zorunlu kildigi rejim (kriz gevsetir, enflasyon sikilastirir; enflasyon baskin). Dogal dongu kuyrugu bozulmaz.
        regimeMonths[effectiveRegime().ordinal()]++;

        // toplanti
        if (s.month() % FinanceBalance.MEETING_MONTHS == 0) {
            double old = policyRate;
            policyRate = meetingStep(policyRate, effectiveRegime().target, active(EventType.INFLATION));
            String verb = policyRate > old + 1e-9 ? "yükseltti" : policyRate < old - 1e-9 ? "düşürdü" : null;
            if (verb != null) s.notify("FİNANS: Merkez bankası faizi " + pct(old) + "'den " + pct(policyRate) + "'e " + verb + ".");
            else s.notify("FİNANS: Merkez bankası faizi " + pct(policyRate) + "'de sabit tuttu (" + effectiveRegime().label + ").");
        }
        // hisse endeksi: dusuk oynaklikli rastgele yuruyus, faiz yuksekken zayif
        double drift = 0.005 - (policyRate - FinanceBalance.TARGET_NEUTRAL) * 0.08;
        equityIndex = Math.max(20, equityIndex * (1 + drift + 0.03 * s.rng.nextGaussian()));
        rateHistory.add(policyRate); indexHistory.add(equityIndex);
        if (rateHistory.size() > 360) { rateHistory.remove(0); indexHistory.remove(0); }
        forecast();
    }

    /** Gecerli rejim: enflasyon soku sikilastirma, kriz gevseme zorunlu kilar; yoksa dogal dongu. */
    public Regime effectiveRegime() { return regimeAt(regime, 0); }

    /** k ay sonraki etkin rejim (olay suresi bitene kadar olayin zorladigi rejim). */
    Regime regimeAt(Regime natural, int k) {
        if (monthsLeft(EventType.INFLATION) > k) return Regime.TIGHTENING;
        if (monthsLeft(EventType.CRISIS) > k) return Regime.EASING;
        return natural;
    }

    private void start(EventType t, int min, int max) {
        int m = min + s.rng.nextInt(max - min + 1);
        events.add(new Event(t, m));
        eventCounts[t.ordinal()]++;
        s.notify("FİNANS: " + t.label + " başladı (tahmini " + m + " ay).");
    }

    /** Toplanti karari: hedefe uzaklik buyudukce adim buyur (0,25 / 0,50 / 0,75); enflasyon sokunda en az 0,50. */
    public static double meetingStep(double rate, double target, boolean inflation) {
        double diff = target - rate, ad = Math.abs(diff);
        if (ad < 0.00125) return rate;
        double step = ad >= 0.02 ? 0.0075 : ad >= 0.01 ? 0.005 : 0.0025;
        if (inflation && diff > 0) step = Math.max(step, FinanceBalance.INFLATION_MIN_STEP);
        step = Math.min(step, Math.max(0.0025, Math.round(ad / 0.0025) * 0.0025));
        return clamp(rate + Math.signum(diff) * step);
    }

    static double clamp(double r) { return Math.max(FinanceBalance.POLICY_MIN, Math.min(FinanceBalance.POLICY_MAX, r)); }

    /**
     * Piyasa beklentisi: %70 olasilikla gelecek 12 ayin gercek (onceden cekilmis) rejim yolu, %30 olasilikla yanlis bir
     * rejim tahmini; bu rejim yolu toplanti kurallariyla faiz yoluna cevrilir. (Enerji krizi olaganustu artislari ongorulemez.)
     */
    void forecast() {
        boolean accurate = s.rng.nextDouble() < FinanceBalance.FORECAST_ACCURACY;
        Regime[] path = new Regime[12];
        int i = 0;
        for (Regime r : future) { if (i < 12) path[i] = regimeAt(r, i + 1); i++; }
        for (; i < 12; i++) path[i] = path[i - 1] == null ? regime : path[i - 1];
        Regime truth6 = path[5];
        if (accurate) expectedRegime = truth6;
        else {
            Regime[] o = Regime.values(); Regime alt;
            do { alt = o[s.rng.nextInt(o.length)]; } while (alt == truth6);
            expectedRegime = alt;
            for (int k = 2; k < 12; k++) path[k] = alt;
        }
        double r = policyRate;
        int m = s.month();
        for (int k = 0; k < 12; k++) {
            int mm = (m + 1 + k) % 12;
            if (mm % FinanceBalance.MEETING_MONTHS == 0) r = meetingStep(r, path[k].target, monthsLeft(EventType.INFLATION) > k);
            expectedPath[k] = r;
        }
    }

    /** Beklenti dususte mi (12 ay sonra bugunden en az 0,5 puan dusuk). */
    public boolean expectingCuts() { return expectedPath[11] < policyRate - 0.005; }

    public static String pct(double r) { return String.format(Locale.ROOT, "%%%.2f", r * 100).replace('.', ','); }
}
