package com.energytycoon.harness;
import com.energytycoon.sim.*;
import java.util.*;
public final class MultiEca {
    public static void main(String[] a) {
        for (long seed = 1; seed <= 3; seed++) {
            GameState s = GameState.newGame(SimHarness.reference(seed));
            Company p = s.player; p.cash = 3e9;
            String[] ids = { "nw-45", "nw-45", "hx-topcon", "mp-ha", "nw-45" };
            List<Plant> built = new ArrayList<>();
            for (int k = 0; k < ids.length; k++) {
                Equipment e = s.catalog.get(ids[k]);
                City c = s.cities.get(k % s.cities.size());
                int[] xy = RivalAI.findSite(s, e, c, 2, 7);
                Plant pl = xy == null ? null : s.finance.buildWithEca(p, e, xy[0], xy[1], Bank.KIYI, 6);
                if (pl != null) { built.add(pl); s.buildLine(p, pl, c, LineTier.YG); }
            }
            StringBuilder sb = new StringBuilder("tohum " + seed + ": " + built.size() + " ECA santrali\n");
            for (int y = 1; y <= 4; y++) {
                for (int h = 0; h < Balance.HOURS_PER_YEAR; h++) { s.tick(); s.messages.clear(); }
                sb.append("  yil " + y + ":");
                for (Plant pl : built) {
                    Loan l = p.loans.stream().filter(x -> x.linkedPlant == pl.id && x.type == Loan.Type.ECA).findFirst().orElse(null);
                    sb.append(String.format(Locale.ROOT, "  %s DSCR=%s%s", pl.equipment.id, l == null || Double.isNaN(l.lastDscr) ? "-" : String.format(Locale.ROOT, "%.2f", l.lastDscr),
                        l == null ? "" : l.status == Loan.Status.DEFAULTED ? "(el kondu)" : l.dscrLowQuarters > 0 ? "(" + l.dscrLowQuarters + " dusuk)" : ""));
                }
                sb.append(String.format(Locale.ROOT, "  | firma ND/FAVOK %.1f%n", s.finance.leverage(p)));
            }
            System.out.print(sb);
        }
    }
}
