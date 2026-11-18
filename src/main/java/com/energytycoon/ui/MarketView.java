package com.energytycoon.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.energytycoon.sim.GameState;
import com.energytycoon.sim.Markets;
import com.energytycoon.sim.PlantType;
import com.energytycoon.sim.Supplier;

import java.util.ArrayList;
import java.util.List;

/** Piyasa penceresi: fiyat ve sistem kartlari, grafikler, tedarikci fiyat endeksi tablosu. */
public final class MarketView {
    public final Table root = new Table();
    private final Skin skin;
    private GameState st;

    private final Label price, coal, gas, uranium, carbon, gasSub, wind, cloud, renew, served;
    private final Table supTable = new Table();
    private final List<Label[]> supRows = new ArrayList<>();
    public final List<LineChart> charts = new ArrayList<>();

    public MarketView(Skins skins) {
        this.skin = skins.skin;
        root.defaults().left();
        price = kpi(); coal = kpi(); gas = kpi(); uranium = kpi(); carbon = kpi();
        wind = kpi(); cloud = kpi(); renew = kpi(); served = kpi();
        gasSub = new Label("", skin, "dim");

        Table r1 = new Table();
        r1.add(UiKit.card(skin, "Elektrik fiyatı (ort.)", price, new Label("$/MWh, şu an", skin, "dim"))).width(150).padRight(6);
        r1.add(UiKit.card(skin, "Kömür", coal, new Label("$/MWh ısıl", skin, "dim"))).width(112).padRight(6);
        r1.add(UiKit.card(skin, "Doğalgaz", gas, gasSub)).width(112).padRight(6);
        r1.add(UiKit.card(skin, "Uranyum", uranium, new Label("$/MWh ısıl", skin, "dim"))).width(112).padRight(6);
        r1.add(UiKit.card(skin, "Karbon vergisi", carbon, new Label("$/ton CO₂", skin, "dim"))).width(130);
        root.add(r1).padBottom(6).row();
        Table r2 = new Table();
        r2.add(UiKit.card(skin, "Rüzgar", wind, null)).width(150).padRight(6);
        r2.add(UiKit.card(skin, "Bulut örtüsü", cloud, null)).width(112).padRight(6);
        r2.add(UiKit.card(skin, "Yenilenebilir pay", renew, null)).width(236).padRight(6);
        r2.add(UiKit.card(skin, "Karşılanan talep", served, new Label("geçen ay", skin, "dim"))).width(130);
        root.add(r2).padBottom(10).row();

        LineChart p1 = new LineChart("Elektrik fiyatı", LineChart.Unit.PRICE, skins.small, 470, 130, true)
            .add("Fiyat", Skins.WARN, () -> st.history.price).xLabels("96 saat önce", "Şimdi")
            .empty("Fiyat verisi birikiyor…");
        LineChart p2 = new LineChart("Talep ve üretim", LineChart.Unit.MW, skins.small, 470, 130, true)
            .add("Talep", Skins.BAD, () -> st.history.demand).add("Toplam üretim", Skins.ACCENT, () -> st.history.supply)
            .add("Sizin", Color.WHITE, () -> st.history.playerMW).xLabels("96 saat önce", "Şimdi");
        LineChart p3 = new LineChart("Aylık ortalama fiyat", LineChart.Unit.PRICE, skins.small, 470, 110, true)
            .add("Fiyat", new Color(0.5f, 0.8f, 1f, 1), () -> arr(st.history.monthlyPrice))
            .empty("En az iki ay kapandığında oluşur.");
        charts.add(p1); charts.add(p2); charts.add(p3);
        Table chartsCol = new Table();
        chartsCol.add(p1).size(470, 130).padBottom(6).row();
        chartsCol.add(p2).size(470, 130).padBottom(6).row();
        chartsCol.add(p3).size(470, 110);

        Table sup = new Table(skin);
        sup.setBackground("card");
        sup.pad(8);
        sup.defaults().left();
        sup.add(UiKit.section(skin, "TEDARİKÇİ FİYATLARI")).padBottom(2).row();
        Label hint = new Label("Liste fiyatına göre; ekipman maliyetini etkiler.", skin, "dim");
        hint.setWrap(true);
        sup.add(hint).width(170).padBottom(6).row();
        sup.add(supTable).top();
        Table bottom = new Table();
        bottom.add(chartsCol).top().padRight(8);
        bottom.add(sup).top().width(190).growY();
        root.add(bottom);
    }

    private Label kpi() { return new Label("", skin, "kpi"); }

    private static double[] arr(List<Double> l) {
        double[] a = new double[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    public void update(GameState s) {
        this.st = s;
        Markets mk = s.markets;
        price.setText("$" + Fmt.f1(s.statPrice));
        coal.setText(Fmt.f1(mk.fuelPrice(PlantType.Fuel.COAL)));
        gas.setText(Fmt.f1(mk.fuelPrice(PlantType.Fuel.GAS)));
        gas.setStyle(skin.get(mk.gasShock() ? "kpiBad" : "kpi", Label.LabelStyle.class));
        gasSub.setText(mk.gasShock() ? "⚠ arz şoku!" : "$/MWh ısıl");
        gasSub.setStyle(skin.get(mk.gasShock() ? "bad" : "dim", Label.LabelStyle.class));
        uranium.setText(Fmt.f1(mk.fuelPrice(PlantType.Fuel.URANIUM)));
        carbon.setText("$" + Fmt.f1(mk.carbonPrice));
        wind.setText(Fmt.pct(mk.wind));
        cloud.setText(Fmt.pct(1 - mk.cloud));
        renew.setText(Fmt.pct(s.statRenewShare));
        served.setText(Fmt.pct(s.lastServedRatio));
        served.setStyle(skin.get(s.lastServedRatio >= 0.97 ? "kpiGood" : s.lastServedRatio >= 0.9 ? "kpi" : "kpiBad", Label.LabelStyle.class));

        if (supRows.isEmpty()) {
            for (Supplier sp : s.catalog.allSuppliers()) {
                Label n = new Label(sp.name, skin, "small"), v = new Label("", skin, "small");
                v.setAlignment(Align.right);
                supTable.add(n).width(116).left(); supTable.add(v).width(56).right().row();
                supRows.add(new Label[] { n, v });
            }
        }
        int k = 0;
        for (Supplier sp : s.catalog.allSuppliers()) {
            Label v = supRows.get(k++)[1];
            double d = sp.priceIndex - 1;
            if (Math.abs(d) < 0.02) { v.setText("liste"); v.setColor(Skins.DIM); }
            else { v.setText((d > 0 ? "▲ +" : "▼ ") + Math.round(d * 100) + "%"); v.setColor(d > 0 ? Skins.BAD : Skins.ACCENT); }
        }
    }
}
