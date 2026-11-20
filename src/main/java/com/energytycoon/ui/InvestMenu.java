package com.energytycoon.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Yatirim menusu: veri tabanli akordeon. Kategori -> (alt grup) -> satir. Bos kategori (gorunur satiri olmayan)
 * gosterilmez. Satir: ikon, ad, alt satir (fiyat), tiklaninca eylem.
 */
public final class InvestMenu {

    public static final class Item {
        final String icon, title;
        final Supplier<String> sub;
        final BooleanSupplier visible, affordable, active;
        final Runnable onClick;
        ImageTextButton button;

        public Item(String icon, String title, Supplier<String> sub, BooleanSupplier visible, BooleanSupplier affordable,
                    BooleanSupplier active, Runnable onClick) {
            this.icon = icon; this.title = title; this.sub = sub; this.visible = visible; this.affordable = affordable;
            this.active = active; this.onClick = onClick;
        }
    }

    public static final class Group {
        final String title; // null: baslıksız
        final List<Item> items = new ArrayList<>();
        public Group(String title) { this.title = title; }
        public Group add(Item i) { items.add(i); return this; }
    }

    public static final class Category {
        final String title;
        final List<Group> groups = new ArrayList<>();
        boolean open;
        public Category(String title, boolean open) { this.title = title; this.open = open; }
        public Category add(Group g) { groups.add(g); return this; }
        boolean hasVisible() {
            for (Group g : groups) for (Item i : g.items) if (i.visible.getAsBoolean()) return true;
            return false;
        }
    }

    public final Table root;
    private final Skin skin;
    private final Table body = new Table();
    private final List<Category> categories = new ArrayList<>();
    private final TextButton pin;
    private String layoutKey = "";

    public InvestMenu(Skins skins) {
        this.skin = skins.skin;
        Table inner = new Table(skin);
        Table head = new Table();
        Label hint = new Label("Tür seçin → model seçin → haritaya tıklayın", skin, "dim");
        hint.setWrap(true);
        head.add(hint).width(BODY_W - 74).left();
        pin = new TextButton("İğnele", skin, "small");
        pin.setProgrammaticChangeEvents(false);
        TextTooltip tt = new TextTooltip("İğnelenince seçim yapınca menü kapanmaz", skin);
        tt.setInstant(false);
        pin.addListener(tt);
        head.add(pin).width(64).height(24).right();
        inner.add(head).width(BODY_W).padBottom(4).row();
        inner.add(body).width(BODY_W).left().top();
        root = skins.framedPanel(new Label("YATIRIM", skin, "title"), inner);
        root.setTouchable(Touchable.enabled);
    }

    static final float BODY_W = 268;

    public boolean pinned() { return pin.isChecked(); }

    public InvestMenu add(Category c) { categories.add(c); return this; }

    public void open(String categoryTitle) {
        for (Category c : categories) c.open = c.title.equals(categoryTitle);
        layoutKey = "";
    }

    /** Gorunurluk degisirse yeniden kurar, metin/renkleri her karede gunceller. */
    public void update() {
        StringBuilder key = new StringBuilder();
        for (Category c : categories) {
            key.append(c.title).append(c.open).append(c.hasVisible());
            for (Group g : c.groups) for (Item i : g.items) key.append(i.visible.getAsBoolean() ? '1' : '0');
        }
        if (!key.toString().equals(layoutKey)) { layoutKey = key.toString(); rebuild(); }
        for (Category c : categories) {
            if (!c.open) continue;
            for (Group g : c.groups) for (Item i : g.items) {
                if (i.button == null) continue;
                String txt = i.title + "\n" + i.sub.get();
                if (!i.button.getText().toString().equals(txt)) i.button.setText(txt);
                i.button.getLabel().setColor(i.affordable.getAsBoolean() ? Color.WHITE : new Color(1f, 0.55f, 0.5f, 1));
                i.button.setChecked(i.active.getAsBoolean());
            }
        }
    }

    private void rebuild() {
        body.clear();
        body.defaults().left();
        for (Category c : categories) {
            if (!c.hasVisible()) continue;
            TextButton h = new TextButton((c.open ? "−  " : "+  ") + c.title, skin, "default");
            h.getLabel().setAlignment(Align.left);
            h.getLabelCell().padLeft(10);
            h.addListener(new ClickListener() {
                @Override public void clicked(InputEvent e, float x, float y) {
                    boolean was = c.open;
                    for (Category o : categories) o.open = false;
                    c.open = !was;
                    layoutKey = "";
                }
            });
            body.add(h).width(BODY_W).height(32).padBottom(3).row();
            if (!c.open) continue;
            for (Group g : c.groups) {
                boolean any = false;
                for (Item i : g.items) any |= i.visible.getAsBoolean();
                if (!any) continue;
                if (g.title != null) body.add(new Label(g.title.toUpperCase(java.util.Locale.forLanguageTag("tr")), skin, "dim")).padLeft(6).padTop(2).row();
                for (Item i : g.items) {
                    if (!i.visible.getAsBoolean()) { i.button = null; continue; }
                    ImageTextButton.ImageTextButtonStyle st = new ImageTextButton.ImageTextButtonStyle(skin.get("small", TextButton.TextButtonStyle.class));
                    st.imageUp = skin.getDrawable(i.icon);
                    ImageTextButton b = new ImageTextButton(i.title, st);
                    b.getImageCell().size(28, 30).padLeft(4);
                    b.getLabel().setAlignment(Align.left);
                    b.getLabel().setWrap(true);
                    b.getLabelCell().pad(2, 6, 2, 6).width(BODY_W - 56);
                    b.setProgrammaticChangeEvents(false);
                    b.addListener(new ChangeListener() {
                        @Override public void changed(ChangeEvent e, Actor a) { i.onClick.run(); }
                    });
                    i.button = b;
                    body.add(b).width(BODY_W).minHeight(42).padBottom(2).row();
                }
            }
        }
        root.pack();
    }
}
