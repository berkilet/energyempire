package com.energytycoon.harness;

import com.energytycoon.sim.*;
import java.util.Locale;

/** Teknoloji basina getiri endeksi (NBD/yatirim): kapasite odemesi dahil ve haric, iki karbon beklentisiyle. */
public final class TechEconomics {
    public static void main(String[] a) {
        GameState s = GameState.newGame(SimHarness.reference(1));
        for (int h = 0; h < 2 * Balance.HOURS_PER_YEAR; h++) { s.tick(); s.messages.clear(); }
        Company c = s.companies.get(1);
        City city = s.cities.get(0);
        System.out.printf(Locale.ROOT, "yil %d, fiyat ort %.1f, karbon %.0f, kapasite fiyati %.0fk$/MW-yil, marj %.2f%n", s.year(), s.history.monthlyPrice.get(s.history.monthlyPrice.size() - 1), s.markets.carbonPrice, s.capacityPrice / 1e3, s.capacityMargin);
        System.out.printf(Locale.ROOT, "sermaye maliyeti %.3f (politika %.3f, not %s), gozlenen nakit/MW: gaz %.0f komur %.0f%n", RivalStrategy.costOfCapital(s, c), s.macro.policyRate, s.finance.rating(c), RivalStrategy.observedCashPerMw(s, PlantType.GAS), RivalStrategy.observedCashPerMw(s, PlantType.COAL));
        for (double belief : new double[] { 1.0, 0.4 }) {
            c.carbonBelief = belief; c.scarcityView = 1; c.hurdle = 0;
            System.out.printf(Locale.ROOT, "karbon beklentisi %.1f:%n", belief);
            for (PlantType t : PlantType.values()) {
                Equipment best = null; double bi = -9;
                for (Equipment e : s.catalog.available(t, s.year())) { double i = RivalStrategy.projectIndex(s, c, e, city); if (i > bi) { bi = i; best = e; } }
                if (best == null) continue;
                // kapasite gelirinin bugunku degeri / yatirim (ayni varsayimlarla)
                double disc = RivalStrategy.costOfCapital(s, c);
                int years = Math.min(t.lifeYears, 40);
                double ann = 0; double build = best.buildDays() / 365.0;
                for (int y = 1; y <= years; y++) ann += 1 / Math.pow(1 + disc, y + build);
                double capPv = 0;
                for (int y = 1; y <= years; y++) { double tt = y + build; double lr = GridBalance.CAPACITY_CONE * 0.8;
                    capPv += best.capacityMW * GridBalance.derate(t) * (lr + (s.capacityPrice - lr) * Math.exp(-tt / 4)) / Math.pow(1 + disc, tt); }
                capPv /= best.capex();
                System.out.printf(Locale.ROOT, "  %-8s %-26s kapasite dahil %+.2f | kapasite haric %+.2f | kapasitenin katkisi %+.2f%n", t, best.fullName(), bi, bi - capPv, capPv);
            }
        }
    }
}
