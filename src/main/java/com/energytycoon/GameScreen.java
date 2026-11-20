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
    /** Trafo merkezi araci seviyesi (1 = TM154, 2 = TM380). */
    private int tmLevel = 1;
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
    public void devShowInvest(String category) { hud.hideHelp(); hud.devShowInvest(category); }
    public void devLineTool(LineTier t) { hud.hideHelp(); lineTier = t; tool(Tool.LINE, null); }
    public void devNoHover(boolean b) { devNoHover = b; }
    public void devHideHelp() { hud.hideHelp(); }
    /** Dogrulama: oyuncu santrallerinin en cok toplandigi yere TM kurar, sehre hat ceker, secer ve odaklar. */
    public void devBuildSubstation() {
        hud.hideHelp();
        int bx = -1, by = -1, bn = -1;
        for (Plant p : state.plants) if (p.owner.player)
            for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) {
                int x = p.x + dx, y = p.y + dy;
                if (state.checkSubstation(x, y) != null) continue;
                int n = 0;
                for (Plant q : state.plants) if (q.owner.player && Math.hypot(q.x - x, q.y - y) <= GridBalance.TM_RADIUS) n++;
                if (n > bn) { bn = n; bx = x; by = y; }
            }
        if (bx < 0) return;
        state.player.cash += 100e6;
        Substation t = state.buildSubstation(state.player, 1, bx, by);
        t.buildHoursLeft = 0;
        City best = null; double bd = 1e9;
        for (City c : state.cities) { double d = Math.hypot(c.x - bx, c.y - by); if (d < bd) { bd = d; best = c; } }
        state.buildLine(state.player, t, best, LineTier.YG);
        state.markTopologyDirty();
        state.tick();
        selNode = t.id;
        wr.iso.target.set(bx + 0.5f, 0, by + 0.5f);
    }

    public void devSubstationTool(int level) { hud.hideHelp(); action("substation:" + level); }
    public String devUiState() {
        return "arac=" + tool + " kademe=" + lineTier.shortLabel + " yatirim=" + (hud.investOpen() ? "acik" : "kapali")
            + " pencere=" + (hud.anyWindowOpen() ? "acik" : "kapali") + " katman=" + wr.terrain.layer().label;
    }
    public void devAction(String a) { action(a); }

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
        if (t == Tool.PLANT && e != null) hud.setPlaceInfo("Haritada bir yere tıklayın.");
    }
    @Override public void speed(int s) { speed = s; if (s > 0) lastSpeed = s; }
    @Override public void layer(Layer l) { wr.terrain.setLayer(l); }
    @Override public void action(String a) {
        if (a.startsWith("upgrade:")) { upgradeSelected(Upgrade.valueOf(a.substring(8))); return; }
        if (a.startsWith("line:")) { lineTier = LineTier.valueOf(a.substring(5)); tool(Tool.LINE, null); return; }
        if (a.startsWith("substation:")) { tmLevel = Integer.parseInt(a.substring(11)); hud.setTmLevel(tmLevel); tool(Tool.SUBSTATION, null); return; }
        if (a.startsWith("tm:")) {
            String[] f = a.split(":");
            if (state.nodeById(Integer.parseInt(f[1])) instanceof Plant p && p.owner.player) {
                int id = Integer.parseInt(f[2]);
                state.setPreferredSubstation(p, id);
                hud.toast(id == 0 ? p.name() + ": otomatik bağlantı" : p.name() + " → " + state.nodeById(id).name() + " bağlandı");
            }
            return;
        }
        if (a.startsWith("tmup:") || a.startsWith("tmfee:") || a.equals("tmaccess")) { substationAction(a); return; }
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

    private void substationAction(String a) {
        if (!(state.nodeById(selNode) instanceof Substation t) || !t.owner.player) return;
        if (a.startsWith("tmup:")) {
            int kind = Integer.parseInt(a.substring(5));
            String err = state.upgradeSubstation(t, kind);
            hud.toast(err != null ? err : (kind == 1 ? "+" + (int) GridBalance.TM_PLUS_MW + " MW trafo" : "TM380 yükseltmesi") + " başladı; TM çalışmaya devam ediyor");
        } else if (a.startsWith("tmfee:")) {
            double d = Double.parseDouble(a.substring(6));
            t.accessFeePerMWh = Math.max(GridBalance.ACCESS_FEE_MIN, Math.min(GridBalance.ACCESS_FEE_MAX, t.accessFeePerMWh + d));
        } else {
            t.openAccess = !t.openAccess;
            state.markTopologyDirty();
            hud.toast(t.name() + (t.openAccess ? ": üçüncü taraf erişimine açık" : ": erişime kapatıldı (bağlı rakip santraller ayrılır)"));
        }
    }

    private void deleteSelected() {
        GridNode n = state.nodeById(selNode);
        if (n instanceof Substation t && t.owner.player) {
            double refund = t.bookValue(state.hour) * Balance.DEMOLISH_REFUND;
            state.demolishSubstation(t, true); selNode = -1;
            hud.toast("Trafo merkezi yıkıldı (bağlı hatlar kaldırıldı). İade: " + Fmt.money(refund));
            return;
        }
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
                case Input.Keys.TAB -> lineTier = lineTier.next();
                case Input.Keys.L -> layer(layers[(wr.terrain.layer().ordinal() + 1) % layers.length]);
                case Input.Keys.F5 -> action("save");
                case Input.Keys.F9 -> action("load");
                case Input.Keys.B -> hud.toggleInvest();
                case Input.Keys.FORWARD_DEL -> { hud.hideInvest(); tool(Tool.DEMOLISH, null); }
                case Input.Keys.ESCAPE -> {
                    // Esc: once araci iptal eder, sonra yatirim menusunu kapatir, sonra pencereleri/menuyu.
                    if (tool != Tool.SELECT) cancelTool();
                    else if (hud.investOpen()) hud.hideInvest();
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
                String why = state.checkLine(state.player, lineFrom, n, lineTier);
                if (why != null) { hud.toast(why); return; }
                double cost = state.lineCost(lineFrom, n, lineTier);
                if (state.player.cash < cost) { hud.toast("Yetersiz nakit (" + Fmt.money(cost) + " gerekli)."); return; }
                state.buildLine(state.player, lineFrom, n, lineTier);
                hud.toast(lineTier.shortLabel + " hat kuruldu: " + lineFrom.name() + " - " + n.name() + " (" + Fmt.money(cost) + ")");
                lineFrom = null;
            }
            case SUBSTATION -> {
                String why = state.checkSubstation(x, y);
                if (why != null) { hud.toast(why); return; }
                double cost = state.substationCost(tmLevel);
                if (state.player.cash < cost) { hud.toast("Yetersiz nakit (" + Fmt.money(cost) + " gerekli)."); return; }
                Substation sub = state.buildSubstation(state.player, tmLevel, x, y);
                if (sub != null) {
                    hud.toast(sub.levelLabel() + " inşaatı başladı (" + sub.buildHoursLeft / 24 + " gün). Şehre hat çekin; yarıçaptaki santralleriniz otomatik bağlanır.");
                    selNode = sub.id;
                }
            }
            case DEMOLISH -> {
                GridNode n = state.nodeAt(x, y);
                if (n instanceof Substation sub) {
                    if (!sub.owner.player) { hud.toast("Başkasının trafo merkezini yıkamazsınız."); return; }
                    selNode = sub.id; deleteSelected(); return;
                }
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
        if (devNoHover) { wr.radiusX = -1; wr.hoverX = wr.hoverY = -1; wr.ghostEquipment = null; wr.lineFrom = null; return; }
        int mx = Gdx.input.getX(), my = Gdx.input.getY();
        Vector2 sc = hud.stage.screenToStageCoordinates(new Vector2(mx, my));
        boolean overUi = hud.stage.hit(sc.x, sc.y, true) != null;
        wr.hoverX = wr.hoverY = -1; wr.ghostEquipment = null; wr.lineFrom = null; wr.lineHover = null; wr.radiusX = -1;
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
                hud.setPlaceInfo(siteInfo(plantEq, t[0], t[1])
                    + (why != null ? "   ✗ " + why : !afford ? "   ✗ Yetersiz nakit" : ""));
            }
            case LINE -> {
                wr.lineFrom = lineFrom; wr.lineHover = n;
                if (lineFrom != null && n != null) {
                    String why = state.checkLine(state.player, lineFrom, n, lineTier);
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
            case SUBSTATION -> {
                String why = state.checkSubstation(t[0], t[1]);
                boolean afford = state.player.cash >= state.substationCost(tmLevel);
                wr.radiusX = t[0]; wr.radiusY = t[1]; wr.radius = (float) GridBalance.TM_RADIUS; wr.radiusValid = why == null && afford;
                int n2 = 0; double mw = 0;
                for (Plant p : state.plants) if (p.owner.player && Math.hypot(p.x - t[0], p.y - t[1]) <= GridBalance.TM_RADIUS + 1e-6) { n2++; mw += p.capacityMW(); }
                hud.setPlaceInfo("Yarıçaptaki santralleriniz: " + n2 + (n2 > 0 ? " (" + Fmt.mw(mw) + ")" : "")
                    + (why != null ? "   ✗ " + why : !afford ? "   ✗ Yetersiz nakit" : ""));
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
              .append(connectionLine(p)).append('\n');
            String why = whyIdle(p);
            if (why != null) sb.append("Neden üretmiyor: ").append(why).append('\n');
            sb
              .append("Geçen ay kâr: ").append(Fmt.money(p.lastMonthProfit)).append("  ·  bu ay ").append(Fmt.money(p.monthProfit)).append('\n')
              .append("Yaş: ").append(Fmt.f1(p.ageYears(state.hour))).append(" yıl  ·  defter ").append(Fmt.money(p.bookValue(state.hour)));
            if (!p.upgrades.isEmpty()) {
                sb.append("\nYükseltmeler: ");
                for (Upgrade u : p.upgrades) sb.append(u.label).append(", ");
                sb.setLength(sb.length() - 2);
            }
            return sb.toString();
        }
        if (n instanceof Substation t) return substationText(t);
        if (n instanceof City c) {
            GameState.Component comp = state.componentOf(c);
            return c.name + " (şehir)\nNüfus: " + Fmt.pop(c.population) + "\nTalep: " + Fmt.mw(c.demandMW) + "  (tepe " + Fmt.mw(c.peakMW()) + ")\n"
                + "Karşılanan: " + Fmt.mw(c.servedMW) + "\nMemnuniyet: " + Fmt.pct(c.satisfaction)
                + "\nBağlı hat: " + c.lines.size() + (c.lines.isEmpty() ? "  (ŞEBEKEYE BAĞLI DEĞİL!)" : c.lines.size() == 1 ? "  (tek hat)" : "  (yedekli)")
                + "  ·  kapasite " + Fmt.mw(c.lineCapacity())
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

    /** "Bağlantı: Küme (3 santral, 450 MW) → Bozova · ihracat 250 MW · %100 dolu · kısıntı 120 MW" */
    private String connectionLine(Plant p) {
        state.components();
        ConnectionPoint cp = p.cp;
        if (cp == null) return "Bağlantı: —";
        StringBuilder sb = new StringBuilder("Bağlantı: ").append(cp.kindLabel());
        if (cp.tm != null && cp.tm.owner != p.owner) sb.append(" · ").append(cp.tm.owner.name).append(" TM'si, erişim $").append(Fmt.f1(cp.tm.accessFeePerMWh)).append("/MWh");
        City near = null; double best = Double.MAX_VALUE;
        GameState.Component comp = state.componentOf(p);
        if (comp != null) for (City c : comp.cities) { double d = Math.hypot(c.x - p.x, c.y - p.y); if (d < best) { best = d; near = c; } }
        sb.append(" → ").append(near != null ? near.name : "şehre bağlı değil");
        double cap = cp.exportCapacityMW();
        sb.append("\n  ihracat ").append(Fmt.mw(cp.exportedMWThisHour)).append(" / ").append(Fmt.mw(cap))
          .append(" · %").append(Math.round(cp.utilization() * 100)).append(" dolu · kayıp %").append(Fmt.f1(cp.lossFraction * 100));
        if (p.curtailedMW > 0.05) sb.append(" · kısıntı ").append(Fmt.mw(p.curtailedMW));
        return sb.toString();
    }

    private String whyIdle(Plant p) {
        if (!p.operational()) return "İnşaat sürüyor (" + p.buildHoursLeft / 24 + " gün)";
        if (p.outageHoursLeft > 0) return "Arıza (" + Math.max(1, p.outageHoursLeft / 24) + " gün)";
        if (p.upgrading() && !p.online()) return "Yükseltme (" + p.upgradeHoursLeft / 24 + " gün)";
        if (p.outputMW > 0.05) return null;
        return p.idleReason;
    }

    private String substationText(Substation t) {
        state.components();
        StringBuilder sb = new StringBuilder();
        sb.append(t.levelLabel()).append(" trafo merkezi #").append(t.id).append("  —  ").append(t.owner.name).append('\n');
        if (!t.operational()) sb.append("Durum: İnşaat (").append(t.buildHoursLeft / 24).append(" gün kaldı)\n");
        else if (t.pendingUpgrade != 0) sb.append("Durum: Çalışıyor · yükseltme ").append(t.upgradeHoursLeft / 24).append(" gün\n");
        else sb.append("Durum: Çalışıyor\n");
        sb.append("Trafo: ").append(Fmt.mw(t.transformerMW)).append("  ·  yarıçap ").append((int) t.radius).append('\n');
        if (t.cp != null) {
            ConnectionPoint cp = t.cp;
            sb.append("Üye santral: ").append(cp.members.size()).append(" (").append(Fmt.mw(cp.installedMW())).append(")\n")
              .append("Hat kapasitesi: ").append(Fmt.mw(cp.lineCapacityMW())).append("  ·  ihracat sınırı ").append(Fmt.mw(cp.exportCapacityMW())).append('\n')
              .append("İhracat: ").append(Fmt.mw(cp.exportedMWThisHour)).append("  ·  %").append(Math.round(cp.utilization() * 100)).append(" dolu\n");
            int third = 0;
            for (Plant m : cp.members) if (m.owner != t.owner) third++;
            sb.append("Üçüncü taraf santral: ").append(third).append('\n');
        }
        sb.append("Erişim: ").append(t.openAccess ? "açık" : "kapalı").append("  ·  bedel $").append(Fmt.f1(t.accessFeePerMWh)).append("/MWh\n")
          .append("Erişim geliri: geçen ay ").append(Fmt.money(t.lastAccessIncome)).append("  ·  bu ay ").append(Fmt.money(t.accessIncomeMonth)).append('\n')
          .append("Defter değeri: ").append(Fmt.money(t.bookValue(state.hour)));
        return sb.toString();
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
            for (Substation t : state.substationsFor(p)) {
                if (p.cp != null && p.cp.tm == t) continue;
                String who = t.owner == p.owner ? "kendi TM'niz" : t.owner.name + ", erişim $" + Fmt.f1(t.accessFeePerMWh) + "/MWh";
                a.add(new String[] { "→ Bağlan: " + t.name() + " (" + who + ")", "tm:" + p.id + ":" + t.id, "1" });
            }
            if (p.preferredTm != 0) a.add(new String[] { "→ Otomatik bağlantıya dön", "tm:" + p.id + ":0", "1" });
            a.add(new String[] { "Yık (iade %" + (int) (Balance.DEMOLISH_REFUND * 100) + ")", "demolish", "1" });
        } else if (n instanceof Substation t && t.owner.player) {
            boolean busy = !t.operational() || t.pendingUpgrade != 0;
            a.add(new String[] { "▲ +" + (int) GridBalance.TM_PLUS_MW + " MW trafo — " + Fmt.money(GridBalance.TM_PLUS_COST) + ", " + GridBalance.TM_PLUS_DAYS + " gün",
                "tmup:1", !busy && state.player.cash >= GridBalance.TM_PLUS_COST ? "1" : "0" });
            if (t.level < 2) a.add(new String[] { "▲ TM154 → TM380 — " + Fmt.money(GridBalance.TM380_UPGRADE_COST) + ", " + GridBalance.TM380_UPGRADE_DAYS + " gün\nÇYG bağlanabilir, trafo " + Fmt.mw(GridBalance.TM380_MW),
                "tmup:2", !busy && state.player.cash >= GridBalance.TM380_UPGRADE_COST ? "1" : "0" });
            a.add(new String[] { t.openAccess ? "Erişimi kapat (rakipler bağlanamaz)" : "Erişimi aç (rakipler bedelle bağlanır)", "tmaccess", "1" });
            a.add(new String[] { "Erişim bedeli −$1/MWh", "tmfee:-1", t.accessFeePerMWh > GridBalance.ACCESS_FEE_MIN ? "1" : "0" });
            a.add(new String[] { "Erişim bedeli +$1/MWh", "tmfee:1", t.accessFeePerMWh < GridBalance.ACCESS_FEE_MAX ? "1" : "0" });
            a.add(new String[] { "Yık (iade %" + (int) (Balance.DEMOLISH_REFUND * 100) + ", bağlı hatlar kalkar)", "demolish", "1" });
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
        if (wr.radiusX < 0 && state.nodeById(selNode) instanceof Substation st) { wr.radiusX = st.x; wr.radiusY = st.y; wr.radius = (float) st.radius; wr.radiusValid = true; }
        if (selNode >= 0 && state.nodeById(selNode) == null) selNode = -1;
        wr.selectedNodeId = selNode; wr.selectedLineId = selLine;
        String sel = selectionText();
        hud.setSelection(sel == null ? "Haritada bir santrale, şehre veya hatta tıklayın." : sel, selectionActions());

        wr.render(dt, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        hud.update(state, tool, plantEq, speed, wr.terrain.layer(), lineTier);
        hud.render(dt);
    }

    @Override public void resize(int w, int h) { hud.resize(w, h); }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}
    @Override public void dispose() { hud.dispose(); wr.dispose(); }
}
