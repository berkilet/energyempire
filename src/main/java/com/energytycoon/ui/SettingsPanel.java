package com.energytycoon.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

/** Ana menu ve oyun ici menu tarafindan paylasilan ayarlar icerigi. */
public final class SettingsPanel {
    private SettingsPanel() {}

    /** Ayarlarin okunup yazildigi yer (EnergyGame tercihleri; oyun icinde ayrica sahneye uygulanir). */
    public interface Model {
        int shadowQuality();
        void setShadowQuality(int q);
        boolean dayNight();
        void setDayNight(boolean on);
        boolean fullscreen();
        void setFullscreen(boolean on);
        int natureDetail();
        void setNatureDetail(int d);
    }

    private static final String[] SHADOW = { "Kapalı", "Orta", "Yüksek" };
    private static final String[] NATURE = { "Az", "Normal", "Yoğun" };

    public static Table build(Skin skin, Model m) {
        Table t = new Table();
        t.defaults().left();

        t.add(new Label("GRAFİK", skin, "dim")).padBottom(4).row();
        t.add(new Label("Gölge kalitesi", skin)).padBottom(4).row();
        Table shadows = new Table();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        group.setMinCheckCount(0);
        for (int i = 0; i < 3; i++) {
            final int q = i;
            TextButton b = new TextButton(SHADOW[i], skin, "small");
            b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) {
                if (((TextButton) a).isChecked()) m.setShadowQuality(q);
            } });
            group.add(b);
            shadows.add(b).width(96).height(30).padRight(4);
        }
        group.setMinCheckCount(1);
        group.getButtons().get(Math.max(0, Math.min(2, m.shadowQuality()))).setChecked(true);
        t.add(shadows).padBottom(4).row();
        t.add(new Label("Zayıf ekran kartlarında \"Kapalı\" en akıcı sonucu verir.", skin, "dim")).padBottom(10).row();
        t.add(new Label("Bitki örtüsü (ağaç, çalı, çim, kaya)", skin)).padBottom(4).row();
        t.add(choice(skin, NATURE, m.natureDetail(), m::setNatureDetail)).padBottom(12).row();

        t.add(toggle(skin, "Gün/gece döngüsü", m.dayNight(), m::setDayNight)).padBottom(2).row();
        t.add(new Label("Kapalıyken hep gündüz ışığı kullanılır.", skin, "dim")).padBottom(10).row();
        t.add(toggle(skin, "Tam ekran", m.fullscreen(), m::setFullscreen)).padBottom(2).row();
        return t;
    }

    private static Table choice(Skin skin, String[] labels, int current, java.util.function.IntConsumer set) {
        Table row = new Table();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        group.setMinCheckCount(0);
        for (int i = 0; i < labels.length; i++) {
            final int q = i;
            TextButton b = new TextButton(labels[i], skin, "small");
            b.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) {
                if (((TextButton) a).isChecked()) set.accept(q);
            } });
            group.add(b);
            row.add(b).width(96).height(30).padRight(4);
        }
        group.setMinCheckCount(1);
        group.getButtons().get(Math.max(0, Math.min(labels.length - 1, current))).setChecked(true);
        return row;
    }

    private static CheckBox toggle(Skin skin, String text, boolean on, java.util.function.Consumer<Boolean> set) {
        CheckBox c = new CheckBox("  " + text, skin, "toggle");
        c.setChecked(on);
        c.addListener(new ChangeListener() { public void changed(ChangeEvent e, Actor a) { set.accept(c.isChecked()); } });
        return c;
    }
}
