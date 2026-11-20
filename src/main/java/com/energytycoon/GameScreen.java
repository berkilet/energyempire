package com.energytycoon;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.math.Vector2;
import com.energytycoon.render.TerrainRenderer.Layer;
import com.energytycoon.render.WorldRenderer;
import com.energytycoon.save.SaveManager;
import com.energytycoon.sim.*;
import com.energytycoon.ui.Fmt;
import com.energytycoon.ui.Hud;
import com.energytycoon.ui.Hud.Tool;

import java.util.ArrayList;
import java.util.List;

public final class GameScreen implements Screen, Hud.Listener {
    /** Oyun saati / gercek saniye. 1x: bir gun 24 sn, 2x: 6 sn, 3x: ~1 sn. */
    private static final double[] HOURS_PER_SEC = { 0, 1, 4, 20 };
    /** Bu hizdan itibaren isik sabit (ogle) tutulur; aksi halde ekran yanip soner. */
    private static final int FREEZE_LIGHT_SPEED = 3;

    private final EnergyGame game;
    private final GameState state;
    private final WorldRenderer wr;
    private final Hud hud;
    private Tool tool = Tool.SELECT;
    private Equipment plantEq;
    private int speed = 1, lastSpeed = 1;
    private double accum;
    private GridNode lineFrom;
    /** Hat araci kademesi (Tab ile OG → YG → ÇYG). */
    private LineTier lineTier = LineTier.YG;
    private int selNode = -1, selLine = -1;
    private boolean rightDown; private float rdx, rdy; private int lastMx, lastMy;
    private final Layer[] layers = Layer.values();
    private long lastAutosaveYear;
    /** Saat sabitlenmisse (dogrulama) >= 0. */
    public float fixedHour = -1;

    public GameScreen(EnergyGame game, GameState state) {
        this.game = game; this.state = state;
        this.wr = new WorldRenderer(state);
        this.hud = new Hud(game.skins, this);
        hud.setState(state);
        wr.setShadowQuality(game.shadowQuality());
        // Ayarlar penceresi: tercihleri kaydeder, golgeyi sahneye hemen uygular.
        hud.attachSettings(new com.energytycoon.ui.SettingsPanel.Model() {
            public int shadowQuality() { return game.shadowQuality(); }
            public void setShadowQuality(int q) { game.setShadowQuality(q); wr.setShadowQuality(q); }
            public boolean dayNight() { return game.dayNight(); }
            public void setDayNight(boolean on) { game.setDayNight(on); }
            public boolean fullscreen() { return game.fullscreen(); }
            public void setFullscreen(boolean on) { game.setFullscreen(on); }
            public int natureDetail() { return game.natureDetail(); }
            public void setNatureDetail(int d) { game.setNatureDetail(d); wr.terrain.setDetail(d); }
        });
        City focus = null;
        for (Plant p : state.plants) if (p.owner.player && !p.lines.isEmpty()) {
            GridNode o = p.lines.get(0).other(p);
            if (o instanceof City c) { focus = c; break; }
        }
        if (focus == null && !state.cities.isEmpty()) focus = state.cities.get(0);
        if (focus != null) wr.iso.target.set(focus.x + 0.5f, 0, focus.y + 0.5f);
        lastAutosaveYear = state.year();
    }

    // ------------------------------------------------------------- dogrulama kancalari
    public void devFocus(int tx, int ty, float ppu) {
        wr.iso.target.set(tx + 0.5f, state.map.topAt(tx, ty) + 0.3f, ty + 0.5f); wr.iso.ppu = wr.iso.ppuTarget = ppu; hud.hideHelp();
        devNoHover = true;
        wr.iso.yaw = wr.iso.yawTarget = 45f;
    }
    public void devSelect(int nodeId) { selNode = nodeId; }
    public long devHour() { return state.hour; }
    public int devTerrainVerts(int detail) { wr.terrain.setDetail(detail); return wr.terrain.vertexCount(); }
    public int devSpeed() { return speed; }
    /** Gercek fare tiklamasi gibi: girdi isleyicisine bas-birak gonderir. */
    public void devClickSpeed(int i) {
        int[] p = hud.devSpeedButtonScreenPos(i);
        Gdx.input.getInputProcessor().touchDown(p[0], p[1], 0, Input.Buttons.LEFT);
        Gdx.input.getInputProcessor().touchUp(p[0], p[1], 0, Input.Buttons.LEFT);
    }
    public void devKey(int key) { Gdx.input.getInputProcessor().keyDown(key); Gdx.input.getInputProcessor().keyUp(key); }
    public void devOpenEquipment(PlantType t) { hud.devOpenEquipment(t); }
    public void devToggle(String w) { hud.toggle(w); }

    public String devPickTest() {
        StringBuilder sb = new StringBuilder();
        int ok = 0, total = 0;
        for (Plant p : state.plants) {
            com.badlogic.gdx.math.Vector3 v = wr.iso.cam.project(new com.badlogic.gdx.math.Vector3(p.x + 0.5f, wr.topAt(p.x, p.y) + 0.02f, p.y + 0.5f));
            int[] t = wr.pickTile((int) v.x, Gdx.graphics.getHeight() - (int) v.y);
            total++;
            if (t != null && t[0] == p.x && t[1] == p.y) ok++;
            else sb.append(" miss(").append(p.x).append(',').append(p.y).append("->").append(t == null ? "null" : t[0] + "," + t[1]).append(')');
        }
        return "PICKTEST " + ok + "/" + total + sb;
    }

    @Override public void show() {
        InputMultiplexer mux = new InputMultiplexer(hud.stage, new MapInput());
        Gdx.input.setInputProcessor(mux);
    }

    // ------------------------------------------------------------- Hud.Listener
    @Override public void tool(Tool t, Equipment e) {
        tool = t; plantEq = e; lineFrom = null;
        if (t != Tool.SELECT) { selNode = -1; selLine = -1; }
        if (t == Tool.PLANT && e != null) hud.setPlaceInfo(e.fullName() + " seçildi. Haritada bir yere tıklayın.");
    }
    @Override public void speed(int s) { speed = s; if (s > 0) lastSpeed = s; }
    @Override public void layer(Layer l) { wr.terrain.setLayer(l); }
    @Override public void action(String a) {
        if (a.startsWith("upgrade:")) { upgradeSelected(Upgrade.valueOf(a.substring(8))); return; }
        switch (a) {
            case "finance", "market", "companies", "help", "settings" -> hud.toggle(a);
            case "menu" -> hud.toggle("menu");
            case "borrow" -> { if (!state.borrow(state.player)) hud.toast("Kredi limitine ulaştınız."); }
            case "repay" -> { if (!state.repay(state.player)) hud.toast("Geri ödeme için yeterli nakit/borç yok."); }
            case "save" -> hud.toast(SaveManager.save(state, "save") ? "Oyun kaydedildi." : "Kayıt başarısız!");
            case "load" -> { if (SaveManager.exists("save")) { if (!game.continueGame("save")) hud.toast("Yükleme başarısız!"); } else hud.toast("Kayıt bulunamadı."); }
            case "togglePause" -> speed(speed == 0 ? lastSpeed : 0);
            case "main" -> game.toMenu();
            case "exit" -> Gdx.app.exit();
            case "demolish" -> deleteSelected();
            case "removeLine" -> deleteSelected();
            default -> {}
        }
    }

    private void upgradeSelected(Upgrade u) {
        if (!(state.nodeById(selNode) instanceof Plant p) || !p.owner.player) return;
        String err = state.startUpgrade(p, u);
        hud.toast(err != null ? err : u.label + " başladı (" + u.days + " gün" + (u.offline ? ", santral bu sürede kapalı" : "") + ")");
    }

    private void deleteSelected() {
        GridNode n = state.nodeById(selNode);
        if (n instanceof Plant p && p.owner.player) {
            double refund = p.bookValue(state.hour) * Balance.DEMOLISH_REFUND;
            state.demolishPlant(p); selNode = -1;
            hud.toast("Santral yıkıldı. İade: " + Fmt.money(refund));
        } else if (selLine >= 0) {
            for (PowerLine l : state.lines) if (l.id == selLine && l.owner != null && l.owner.player) { state.removeLine(l, true); hud.toast("Hat kaldırıldı."); break; }
            selLine = -1;
        }
    }

    // ------------------------------------------------------------- girdi
    private final class MapInput extends InputAdapter {
        @Override public boolean touchDown(int x, int y, int p, int button) {
            if (button == Input.Buttons.RIGHT || button == Input.Buttons.MIDDLE) {
                rightDown = true; rdx = x; rdy = y; lastMx = x; lastMy = y; return true;
            }
            if (button == Input.Buttons.LEFT) { click(x, y); return true; }
            return false;
        }
        @Override public boolean touchDragged(int x, int y, int p) {
            if (rightDown) { wr.iso.drag(x - lastMx, y - lastMy); lastMx = x; lastMy = y; return true; }
            return false;
        }
        @Override public boolean touchUp(int x, int y, int p, int button) {
            if (rightDown && (button == Input.Buttons.RIGHT || button == Input.Buttons.MIDDLE)) {
                rightDown = false;
                if (Math.abs(x - rdx) < 5 && Math.abs(y - rdy) < 5 && button == Input.Buttons.RIGHT) cancelTool();
                return true;
            }
            return false;
        }
        @Override public boolean scrolled(float ax, float ay) { wr.iso.zoom(ay > 0 ? 0.9f : 1.1f); return true; }
        @Override public boolean keyDown(int key) {
            switch (key) {
                case Input.Keys.SPACE -> speed(speed == 0 ? lastSpeed : 0);
                case Input.Keys.NUM_1 -> speed(1);
                case Input.Keys.NUM_2 -> speed(2);
                case Input.Keys.NUM_3 -> speed(3);
                case Input.Keys.Q -> wr.iso.rotate(-1);
                case Input.Keys.E -> wr.iso.rotate(1);
                case Input.Keys.F -> hud.toggle("finance");
                case Input.Keys.M -> hud.toggle("market");
                case Input.Keys.C -> hud.toggle("companies");
                case Input.Keys.H -> hud.toggle("help");
                case Input.Keys.TAB -> { lineTier = lineTier.next(); if (tool == Tool.LINE) hud.toast("Hat kademesi: " + lineTier.label); }
                case Input.Keys.L -> layer(layers[(wr.terrain.layer().ordinal() + 1) % layers.length]);
                case Input.Keys.F5 -> action("save");
                case Input.Keys.F9 -> action("load");
                case Input.Keys.ESCAPE -> {
                    if (tool != Tool.SELECT) cancelTool();
                    else if (hud.anyWindowOpen()) hud.closeWindows();
                    else hud.toggle("menu");
                }
                default -> { return false; }
            }
            return true;
        }
    }

    private void cancelTool() { tool(Tool.SELECT, null); selNode = -1; selLine = -1; }

    private void click(int sx, int sy) {
        int[] t = wr.pickTile(sx, sy);
        if (t == null) { selNode = -1; selLine = -1; return; }
        int x = t[0], y = t[1];
        switch (tool) {
            case SELECT -> {
                GridNode n = state.nodeAt(x, y);
                if (n != null) { selNode = n.id; selLine = -1; }
                else {
                    PowerLine l = wr.pickLine(x + 0.5f, y + 0.5f);
                    if (l != null) { selLine = l.id; selNode = -1; } else { selNode = -1; selLine = -1; }
                }
            }
            case PLANT -> {
                if (plantEq == null) return;
                String why = state.checkPlacement(plantEq.type, x, y);
                if (why == null) why = state.checkEquipment(plantEq);
                if (why != null) { hud.toast(why); return; }
                if (state.player.cash < plantEq.capex()) { hud.toast("Yetersiz nakit (" + Fmt.money(plantEq.capex()) + " gerekli). Finans'tan borçlanın."); return; }
                Plant p = state.buildPlant(state.player, plantEq, x, y);
                if (p != null) {
                    hud.toast(plantEq.fullName() + " inşaatı başladı (" + p.buildHoursLeft / 24 + " gün). Şimdi bir hat çekin!");
                    selNode = p.id;
                }
            }
            case LINE -> {
                GridNode n = state.nodeAt(x, y);
                if (n == null) { hud.toast("Hat için bir santrale veya şehre tıklayın."); return; }
                if (lineFrom == null) { lineFrom = n; return; }
                String why = state.checkLine(lineFrom, n, lineTier);
                if (why != null) { hud.toast(why); return; }
                double cost = state.lineCost(lineFrom, n, lineTier);
                if (state.player.cash < cost) { hud.toast("Yetersiz nakit (" + Fmt.money(cost) + " gerekli)."); return; }
                state.buildLine(state.player, lineFrom, n, lineTier);
                hud.toast(lineTier.shortLabel + " hat kuruldu: " + lineFrom.name() + " - " + n.name() + " (" + Fmt.money(cost) + ")");
                lineFrom = null;
            }
            case DEMOLISH -> {
                GridNode n = state.nodeAt(x, y);
                if (n instanceof Plant p) {
                    if (!p.owner.player) { hud.toast("Rakip tesisini yıkamazsınız."); return; }
                    double refund = p.bookValue(state.hour) * Balance.DEMOLISH_REFUND;
                    state.demolishPlant(p);
                    hud.toast("Santral yıkıldı. İade: " + Fmt.money(refund));
                } else {
                    PowerLine l = wr.pickLine(x + 0.5f, y + 0.5f);
                    if (l != null && l.owner != null && l.owner.player) { state.removeLine(l, true); hud.toast("Hat kaldırıldı."); }
                    else if (l != null) hud.toast("Bu hat size ait değil.");
                }
            }
        }
    }

    // ------------------------------------------------------------- dongu
    /** Dogrulama goruntulerinde fare vurgusunu gizle. */
    private boolean devNoHover;

    private void updateHover() {
        if (devNoHover) { wr.hoverX = wr.hoverY = -1; wr.ghostEquipment = null; wr.lineFrom = null; return; }
        int mx = Gdx.input.getX(), my = Gdx.input.getY();
        Vector2 sc = hud.stage.screenToStageCoordinates(new Vector2(mx, my));
        boolean overUi = hud.stage.hit(sc.x, sc.y, true) != null;
        wr.hoverX = wr.hoverY = -1; wr.ghostEquipment = null; wr.lineFrom = null; wr.lineHover = null;
        if (overUi || rightDown || state.gameOver) return;
        int[] t = wr.pickTile(mx, my);
        if (t == null) return;
        wr.hoverX = t[0]; wr.hoverY = t[1];
        GridNode n = state.nodeAt(t[0], t[1]);
        int i = state.map.idx(t[0], t[1]);
        switch (tool) {
            case PLANT -> {
                if (plantEq == null) return;
                String why = state.checkPlacement(plantEq.type, t[0], t[1]);
                if (why == null) why = state.checkEquipment(plantEq);
                boolean afford = state.player.cash >= plantEq.capex();
                wr.ghostEquipment = plantEq; wr.ghostValid = why == null && afford;
                hud.setPlaceInfo(plantEq.fullName() + "  " + Fmt.money(plantEq.capex()) + "  ·  " + siteInfo(plantEq, t[0], t[1])
                    + (why != null ? "   ✗ " + why : !afford ? "   ✗ Yetersiz nakit" : ""));
            }
            case LINE -> {
                wr.lineFrom = lineFrom; wr.lineHover = n;
                if (lineFrom != null && n != null) {
                    String why = state.checkLine(lineFrom, n, lineTier);
                    double cost = state.lineCost(lineFrom, n, lineTier);
                    double len = state.lineLength(lineFrom, n);
                    wr.lineValid = why == null && state.player.cash >= cost;
                    hud.setPlaceInfo(lineTier.shortLabel + " hat: " + Fmt.f1(len) + " kare  ·  " + Fmt.money(cost) + "  ·  " + Fmt.mw(lineTier.capacityMW)
                        + "  ·  " + lineTier.buildDays(len) + " gün  ·  kayıp %" + Fmt.f1(len * lineTier.lossPerTile * 100) + (why != null ? "   ✗ " + why : ""));
                } else {
                    wr.lineValid = n != null;
                    hud.setPlaceInfo(lineTier.label + " · " + (lineFrom == null ? "Hat başlangıcı için bir santrale/şehre tıklayın" : "Hat bitişi için bir santrale/şehre tıklayın") + " · Tab: kademe");
                }
            }
            case DEMOLISH -> hud.setPlaceInfo(n instanceof Plant p && p.owner.player ? "Yık: " + p.name() + " (iade " + Fmt.money(p.bookValue(state.hour) * Balance.DEMOLISH_REFUND) + ")" : "Yıkmak için kendi santralinize veya hatlarınıza tıklayın");
            default -> {
                if (n != null) hud.setPlaceInfo(n instanceof Plant p ? p.name() + " · " + p.equipment.fullName() + " · " + p.owner.name : n.name());
                else hud.setPlaceInfo(String.format("Rüzgar %s  ·  Güneş %s  ·  Su %s  ·  Düşü %s  ·  Kömür %s  ·  Gaz %s",
                    Fmt.pct(state.map.wind[i]), Fmt.pct(state.map.sun[i]), Fmt.pct(state.map.hydro[i]),
                    Fmt.pct(state.map.head(t[0], t[1])), Fmt.pct(state.map.coal[i]), Fmt.pct(state.map.gas[i])));
            }
        }
    }

    private String siteInfo(Equipment e, int x, int y) {
        return switch (e.type) {
            case SOLAR, WIND, HYDRO -> "Beklenen kapasite faktörü %" + Math.round(state.siteScore(e, x, y) * 100);
            case COAL, GAS -> "Yakıt indirimi " + Fmt.pct(1 - nearFuel(e, x, y));
            case NUCLEAR -> "Soğutma suyu " + Fmt.pct(state.map.hydro[state.map.idx(x, y)]);
        };
    }

    private double nearFuel(Equipment e, int x, int y) {
        Plant tmp = new Plant(); tmp.type = e.type; tmp.equipment = e; tmp.x = x; tmp.y = y;
        return state.fuelMult(tmp);
    }

    private String selectionText() {
        GridNode n = state.nodeById(selNode);
        if (n instanceof Plant p) {
            String status = !p.operational() ? String.format("İnşaat: %d gün kaldı", p.buildHoursLeft / 24)
                          : p.outageHoursLeft > 0 ? "ARIZALI (" + p.outageHoursLeft / 24 + " gün)"
                          : p.upgrading() ? p.pendingUpgrade.label + ": " + p.upgradeHoursLeft / 24 + " gün" : "Çalışıyor";
            Equipment e = p.equipment;
            StringBuilder sb = new StringBuilder();
            sb.append(p.customName != null ? p.customName + " (" + p.type.label + ")" : p.type.label).append("  —  ").append(p.owner.name).append('\n')
              .append(e.supplier.name).append(" ").append(e.model).append(" (").append(e.tech).append(")\n")
              .append("Durum: ").append(status).append('\n')
              .append("Üretim: ").append(Fmt.mw(p.outputMW)).append(" / ").append(Fmt.mw(p.capacityMW())).append('\n');
            if (p.operational()) sb.append("Şu an uygun kapasite: ").append(Fmt.pct(state.capacityFactorNow(p))).append('\n');
            if (!p.type.renewable) sb.append("Verim: ").append(Fmt.pct(p.efficiency(state.hour)))
                .append("  ·  CO₂ ").append(Fmt.f2(p.co2PerMWh(state.hour))).append(" t/MWh\n");
            sb.append("Marjinal maliyet: $").append(Fmt.f1(state.marginalCost(p))).append("/MWh\n")
              .append("Performans (yaşlanma): ").append(Fmt.pct(p.degradation(state.hour))).append('\n')
              .append("Garanti: ").append(p.underWarranty(state.hour)
                    ? Math.max(0, Math.round(e.supplier.warrantyYears - p.ageYears(state.hour))) + " yıl kaldı" : "bitti").append('\n')
              .append("Hatlar: ").append(p.lines.size()).append(p.lines.isEmpty() ? "  (BAĞLI DEĞİL!)" : "  yük " + Fmt.pct(p.utilization())).append('\n')
              .append("Geçen ay kâr: ").append(Fmt.money(p.lastMonthProfit)).append("  ·  bu ay ").append(Fmt.money(p.monthProfit)).append('\n')
              .append("Yaş: ").append(Fmt.f1(p.ageYears(state.hour))).append(" yıl  ·  defter ").append(Fmt.money(p.bookValue(state.hour)));
            if (!p.upgrades.isEmpty()) {
                sb.append("\nYükseltmeler: ");
                for (Upgrade u : p.upgrades) sb.append(u.label).append(", ");
                sb.setLength(sb.length() - 2);
            }
            return sb.toString();
        }
        if (n instanceof City c) {
            GameState.Component comp = state.componentOf(c);
            return c.name + " (şehir)\nNüfus: " + Fmt.pop(c.population) + "\nTalep: " + Fmt.mw(c.demandMW) + "  (tepe " + Fmt.mw(c.peakMW()) + ")\n"
                + "Karşılanan: " + Fmt.mw(c.servedMW) + "\nMemnuniyet: " + Fmt.pct(c.satisfaction)
                + "\nBağlı hat: " + c.lines.size() + (c.lines.isEmpty() ? "  (ŞEBEKEYE BAĞLI DEĞİL!)" : "")
                + "\nBölge fiyatı: $" + Fmt.f1(comp == null ? 0 : comp.price) + "/MWh";
        }
        if (selLine >= 0) for (PowerLine l : state.lines) if (l.id == selLine) {
            return "İletim hattı · " + l.tier.label + "\n" + l.a.name() + " ↔ " + l.b.name() + "\nSahibi: " + l.ownerName()
                + "\nDurum: " + l.status.label
                + "\nUzunluk: " + Fmt.f1(l.lengthTiles) + " kare\nKapasite: " + Fmt.mw(l.capacityMW()) + "\nYük: " + Fmt.pct(l.utilization())
                + "\nKayıp: %" + Fmt.f1(l.lossFraction() * 100) + "  ·  doluluk (geçen yıl) " + l.saturatedHoursLastYear + " saat";
        }
        return null;
    }

    private List<String[]> selectionActions() {
        List<String[]> a = new ArrayList<>();
        GridNode n = state.nodeById(selNode);
        if (n instanceof Plant p && p.owner.player) {
            for (Upgrade u : Upgrade.values()) {
                if (u.type != p.type) continue;
                if (p.upgrades.contains(u)) { a.add(new String[] { "✓ " + u.label, "none", "0" }); continue; }
                boolean ok = p.canUpgrade(u) && state.player.cash >= u.cost(p);
                a.add(new String[] { "▲ " + u.label + " — " + Fmt.money(u.cost(p)) + ", " + u.days + " gün\n" + u.desc, "upgrade:" + u.name(), ok ? "1" : "0" });
            }
            a.add(new String[] { "Yık (iade %" + (int) (Balance.DEMOLISH_REFUND * 100) + ")", "demolish", "1" });
        } else if (selLine >= 0) {
            for (PowerLine l : state.lines) if (l.id == selLine && l.owner != null && l.owner.player) a.add(new String[] { "Hattı kaldır", "removeLine", "1" });
        }
        return a;
    }

    @Override public void render(float dt) {
        dt = Math.min(dt, 0.1f);
        float k = 16f / (wr.iso.ppu / 34f) * dt;
        float rx = 0, fw = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) rx -= k;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) rx += k;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) fw += k;
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) fw -= k;
        if (rx != 0 || fw != 0) wr.iso.pan(rx, fw);

        if (!state.gameOver && speed > 0) {
            accum += dt * HOURS_PER_SEC[speed];
            int n = 0;
            while (accum >= 1 && n++ < 400) { state.tick(); accum -= 1; }
            if (accum > 1) accum = 1;
        }
        if (fixedHour >= 0) wr.hourFloat = fixedHour;
        else if (!game.dayNight() || speed >= FREEZE_LIGHT_SPEED) wr.hourFloat = 13f;
        else wr.hourFloat = state.hourOfDay() + (float) Math.min(accum, 0.999);
        String m;
        while ((m = state.messages.poll()) != null) hud.toast(m);
        if (state.year() != lastAutosaveYear) { lastAutosaveYear = state.year(); SaveManager.save(state, "autosave"); }

        hud.setPlaceInfo("");
        updateHover();
        if (selNode >= 0 && state.nodeById(selNode) == null) selNode = -1;
        wr.selectedNodeId = selNode; wr.selectedLineId = selLine;
        String sel = selectionText();
        hud.setSelection(sel == null ? "Haritada bir santrale, şehre veya hatta tıklayın." : sel, selectionActions());

        wr.render(dt, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        hud.update(state, tool, plantEq, speed, wr.terrain.layer());
        hud.render(dt);
    }

    @Override public void resize(int w, int h) { hud.resize(w, h); }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}
    @Override public void dispose() { hud.dispose(); wr.dispose(); }
}
