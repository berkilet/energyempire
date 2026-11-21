package com.energytycoon.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.energytycoon.sim.City;
import com.energytycoon.sim.Company;
import com.energytycoon.sim.GameState;
import com.energytycoon.sim.Plant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Sirketler ve sehirler: sabit genislikli sutunlu tablolar; oyuncu satiri vurgulu. */
public final class CompaniesView {
    public final Table root = new Table();
    private final Skin skin;
    private boolean built;

    private static final String[] C_HEAD = { "Şirket", "Piyasa değeri", "Nakit", "Borç", "Santral", "Kurulu güç", "Üretim/ay", "Pay", "Kâr (geçen ay)", "" };
    private static final float[] C_W = { 170, 96, 84, 70, 60, 84, 84, 60, 104, 100 };
    private static final int PROFIT = 8, TAG = 9;
    private static final String[] T_HEAD = { "Şehir", "Nüfus", "Tepe talep", "Şu an", "Memnuniyet", "", "Hat" };
    private static final float[] T_W = { 140, 80, 90, 80, 130, 50, 50 };

    private final List<Label[]> compRows = new ArrayList<>();
    private final List<Company> rowOrder = new ArrayList<>();
    private final List<Label[]> cityRows = new ArrayList<>();
    private final List<UiKit.Meter> cityMeters = new ArrayList<>();

    public CompaniesView(Skins skins) {
        this.skin = skins.skin;
        root.defaults().left();
    }

    private Label head(String t, int align) {
        Label l = new Label(t, skin, "dim");
        l.setAlignment(align);
        return l;
    }

    private void build(GameState s) {
        root.clear();
        root.add(UiKit.section(skin, "ŞİRKETLER")).padBottom(4).row();
        Table ct = new Table();
        for (int i = 0; i < C_HEAD.length; i++) ct.add(head(C_HEAD[i], i == 0 ? Align.left : Align.right)).width(C_W[i]).padBottom(4);
        ct.row();
        // Ozel sirketler once; tarafsiz kamu sirketi siralamanin disinda, ayri baslik altinda.
        rowOrder.clear();
        for (Company c : s.companies) if (!c.neutral) rowOrder.add(c);
        for (Company c : s.companies) if (c.neutral) rowOrder.add(c);
        for (Company c : rowOrder) {
            if (c.neutral) {
                Label sep = new Label("Kamu (tarafsız, rakip değil; kapanış takvimiyle devreden çıkar)", skin, "dim");
                ct.add(sep).colspan(C_HEAD.length).left().padTop(6).padBottom(2).row();
            }
            Table row = new Table(skin);
            if (c.player) row.setBackground("card");
            Label[] cells = new Label[C_HEAD.length];
            Table nameCell = new Table();
            Image dot = new Image(skin.newDrawable("white", new Color((c.colorRgb << 8) | 0xFF)));
            nameCell.add(dot).size(10, 10).padRight(6);
            cells[0] = new Label(c.name, skin, c.player ? "title" : "default");
            cells[0].setWrap(true); // uzun sirket adi kirpilmaz, satir kayar
            nameCell.add(cells[0]).left().width(C_W[0] - 16);
            row.add(nameCell).width(C_W[0]).left();
            for (int i = 1; i < C_HEAD.length; i++) {
                cells[i] = new Label("", skin, i == TAG ? "warn" : "value");
                row.add(UiKit.col(new Table(), cells[i], C_W[i], i == TAG ? Align.left : Align.right).getTable()).width(C_W[i]).padLeft(i == TAG ? 8 : 0);
            }
            ct.add(row).colspan(C_HEAD.length).padBottom(2).row();
            compRows.add(cells);
        }
        root.add(ct).padBottom(14).row();

        root.add(UiKit.section(skin, "ŞEHİRLER")).padBottom(4).row();
        Table tt = new Table();
        for (int i = 0; i < T_HEAD.length; i++) {
            Cell<Label> hc = tt.add(head(T_HEAD[i], i == 0 || i == 4 ? Align.left : Align.right)).width(T_W[i]).padBottom(4);
            if (i == 4) hc.width(T_W[4] - 30).padLeft(20).padRight(10);
        }
        tt.row();
        for (City c : s.cities) {
            Label[] cells = new Label[T_HEAD.length];
            cells[0] = new Label(c.name, skin);
            tt.add(cells[0]).width(T_W[0]).left();
            for (int i = 1; i < T_HEAD.length; i++) {
                if (i == 4) {
                    UiKit.Meter m = new UiKit.Meter(skin, T_W[4] - 30, 8);
                    cityMeters.add(m);
                    tt.add(m).width(T_W[4] - 30).height(8).left().padLeft(20).padRight(10);
                    continue;
                }
                cells[i] = new Label("", skin, "value");
                cells[i].setAlignment(Align.right);
                tt.add(cells[i]).width(T_W[i]);
            }
            tt.row().padTop(3);
            cityRows.add(cells);
        }
        root.add(tt);
        built = true;
    }

    public void update(GameState s) {
        if (!built) build(s);
        double totalGen = 0;
        for (Company c : s.companies) totalGen += c.lastMwhGenerated;
        for (int k = 0; k < rowOrder.size() && k < compRows.size(); k++) {
            Company c = rowOrder.get(k);
            Label[] r = compRows.get(k);
            double cap = 0; int n = 0;
            for (Plant p : s.plants) if (p.owner == c && p.operational()) { cap += p.capacityMW(); n++; }
            r[0].setText(c.name + (c.eq.merged ? "  (birleşti)" : c.bankrupt ? "  (iflas)" : c.eq.parent >= 0 ? "  (bağlı ortaklık)" : ""));
            r[1].setText(Fmt.money(c.valuation));
            UiKit.setMoney(skin, r[2], c.cash, true);
            r[3].setText(c.debt() > 0 ? Fmt.money(c.debt()) : "—");
            r[4].setText(String.valueOf(n));
            r[5].setText(Fmt.mw(cap));
            r[6].setText(c.lastMwhGenerated > 0 ? String.format(Locale.ROOT, "%.0f GWh", c.lastMwhGenerated / 1000) : "—");
            r[7].setText(totalGen > 0 ? "%" + Math.round(100 * c.lastMwhGenerated / totalGen) : "—");
            boolean anyMonth = !c.netHistory.isEmpty();
            if (anyMonth) UiKit.setMoney(skin, r[PROFIT], c.lastNet, true); else r[PROFIT].setText("—");
            // Kamu: yalnizca zarardayken "Gorev zarari" etiketi
            r[TAG].setText(c.neutral && anyMonth && c.lastNet < 0 ? "Görev zararı" : "");
        }
        for (int k = 0; k < s.cities.size() && k < cityRows.size(); k++) {
            City c = s.cities.get(k);
            Label[] r = cityRows.get(k);
            r[1].setText(Fmt.pop(c.population));
            r[2].setText(Fmt.mw(c.peakMW()));
            r[3].setText(Fmt.mw(c.servedMW) );
            float sat = (float) c.satisfaction;
            cityMeters.get(k).set(sat, sat > 0.95f ? Skins.ACCENT : sat > 0.8f ? Skins.WARN : Skins.BAD);
            r[5].setText("%" + Math.round(sat * 100));
            r[6].setText(c.lines.isEmpty() ? "yok" : String.valueOf(c.lines.size()));
            r[6].setStyle(skin.get(c.lines.isEmpty() ? "valueBad" : "value", Label.LabelStyle.class));
        }
    }
}
