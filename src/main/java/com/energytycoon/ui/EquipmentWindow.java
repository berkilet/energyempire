package com.energytycoon.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.energytycoon.sim.*;

import java.util.function.Consumer;

/** Santral turu icin tedarikci/model secim penceresi. */
public final class EquipmentWindow extends Window {
    private final Skin skin;
    private final Table rows = new Table();
    private final Label note;
    private Consumer<Equipment> onPick;

    public EquipmentWindow(Skin skin) {
        super("Ekipman Seçimi", skin);
        this.skin = skin;
        setMovable(true);
        // Cerceve ve kapatma dugmesi Skins.decorateWindow ile (Hud olusturur); burada yalnizca icerik.
        note = new Label("", skin, "small");
        note.setWrap(true);
        add(rows).growX().row();
        add(note).width(880).padTop(8).left();
        setVisible(false);
    }

    public void show(GameState s, PlantType t, Equipment current, Consumer<Equipment> onPick) {
        this.onPick = onPick;
        getTitleLabel().setText(t.label + " — tedarikçi ve model seçin");
        rows.clear();
        String[] heads = { "Tedarikçi", "Model / teknoloji", "Güç", "Fiyat", "Performans", "Yaşlanma", "Garanti", "Güvenilirlik", "İnşaat" };
        float[] widths = { 120, 220, 64, 82, 170, 70, 60, 92, 56 };
        Table h = new Table();
        for (int i = 0; i < heads.length; i++) h.add(new Label(heads[i], skin, "dim")).width(widths[i]).left().padLeft(4);
        rows.add(h).left().padBottom(4).row();
        for (Equipment e : s.catalog.forType(t)) {
            boolean locked = e.availableYear > s.year();
            boolean afford = s.player.cash >= e.capex();
            Table r = new Table(skin);
            r.setBackground(locked ? "rowLocked" : e == current ? "rowSel" : "row");
            r.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
            String[] cells = {
                e.supplier.name + "\n" + e.supplier.origin,
                e.model + "\n" + e.tech,
                Fmt.mw(e.capacityMW),
                Fmt.money(e.capex()) + "\n" + priceTrend(e.supplier),
                performance(e),
                Fmt.f1(e.degradationPerYear * 100) + "%/yıl",
                e.supplier.warrantyYears + " yıl",
                e.supplier.stars(),
                e.buildDays() + " gün"
            };
            for (int i = 0; i < cells.length; i++) {
                Label l = new Label(cells[i], skin, "small");
                if (locked) l.setColor(0.55f, 0.58f, 0.62f, 1);
                else if (i == 3 && !afford) l.setColor(1f, 0.55f, 0.5f, 1);
                if (i == 7) l.setColor(locked ? Color.GRAY : new Color(1f, 0.85f, 0.3f, 1));
                r.add(l).width(widths[i]).left().pad(4, 4, 4, 0);
            }
            if (locked) {
                r.row();
                Label lk = new Label("Piyasaya çıkış: " + e.availableYear, skin, "dim");
                r.add(lk).colspan(cells.length).left().padLeft(4).padBottom(3);
            }
            final String bg = locked ? "rowLocked" : e == current ? "rowSel" : "row";
            r.addListener(new ClickListener() {
                @Override public void enter(InputEvent ev, float x, float y, int p, Actor from) {
                    if (!locked) r.setBackground("rowHover");
                    note.setText(e.fullName() + " — " + e.note + "  Tedarikçi itibarı arıza sıklığını ve teslim gecikmesi riskini belirler"
                        + " (gecikme olasılığı %" + Math.round(e.supplier.delayChance() * 100) + "). Garanti süresince arıza onarımı ücretsizdir.");
                }
                @Override public void exit(InputEvent ev, float x, float y, int p, Actor to) { r.setBackground(bg); }
                @Override public void clicked(InputEvent ev, float x, float y) {
                    if (locked) return;
                    setVisible(false);
                    if (EquipmentWindow.this.onPick != null) EquipmentWindow.this.onPick.accept(e);
                }
            });
            rows.add(r).growX().padBottom(2).row();
        }
        note.setText("Bir modelin üzerine gelerek açıklamasını görün, tıklayarak seçin. Fiyatlar tedarikçi fiyat endeksiyle aylık dalgalanır.");
        pack();
        if (getStage() != null) setPosition((getStage().getWidth() - getWidth()) / 2, (getStage().getHeight() - getHeight()) / 2);
        setVisible(true);
        toFront();
    }

    static String priceTrend(Supplier s) {
        double d = s.priceIndex - 1;
        if (Math.abs(d) < 0.02) return "liste fiyatı";
        return (d > 0 ? "▲ +" : "▼ ") + Math.round(d * 100) + "%";
    }

    static String performance(Equipment e) {
        return switch (e.type) {
            case SOLAR -> "Üretim ×" + Fmt.f2(e.outputMult) + "\nSıcakta kayıp −%" + Math.round(e.paramA * 100);
            case WIND -> "Zayıf saha CF %" + Math.round(GameState.expectedWindCf(e, 0.7) * 100)
                       + "\nGüçlü saha CF %" + Math.round(GameState.expectedWindCf(e, 1.3) * 100);
            case HYDRO -> "Uygun düşü: " + (e.paramA < 0.3 ? "alçak (ova)" : e.paramA < 0.7 ? "orta" : "yüksek (dağ)");
            default -> "Verim %" + Math.round(e.efficiency * 100) + "\nCO₂ " + Fmt.f2(e.co2PerMWh()) + " t/MWh";
        };
    }
}
