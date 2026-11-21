package com.energytycoon.sim;

import com.energytycoon.world.WorldMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Butun simulasyon durumu ve saatlik tick. LibGDX'ten bagimsiz. */
public final class GameState {
    public static final String[] CITY_NAMES = {
        "Akköy", "Bozova", "Çınarlı", "Demirpark", "Elmalı", "Gülbahçe", "Kuzeyliman", "Yeşilırmak", "Taşkale", "Ovakent",
        "Karasu", "Mercanköy", "Sarıyurt", "Dumanlı"
    };
    static final String[][] RIVALS = {
        { "Anadolu Enerji", "e74c3c", "fossil" },
        { "Yeşil Vadi Güç", "3498db", "green" },
        { "Delta Elektrik", "f39c12", "balanced" },
    };

    public final GameSettings settings;
    public final Scenario scenario;
    public final long seed;
    public final WorldMap map;
    public final Random rng;
    public final Markets markets = new Markets();
    public final EquipmentCatalog catalog = new EquipmentCatalog();

    public final List<Company> companies = new ArrayList<>();
    public Company player;
    /** Tarafsiz kamu sirketi (eski filo); yoksa null. */
    public Company neutral;
    public final List<City> cities = new ArrayList<>();
    public final List<Plant> plants = new ArrayList<>();
    public final List<PowerLine> lines = new ArrayList<>();
    public final List<Substation> substations = new ArrayList<>();
    /** Ulusal Sebeke Isletmesi (sirket degil). */
    public final GridOperator operator = new GridOperator(this);
    /** Acil kapasite ihaleleri. */
    public final TenderBook tenders = new TenderBook(this);
    /** Varlik satis ilanlari. */
    public final SaleBook sales = new SaleBook(this);
    /** Makro ortam: politika faizi, rejimler, kuresel olaylar. */
    public final Macro macro = new Macro(this);
    /** Finans motoru: not, bankalar, krediler, tahsilat. */
    public final Finance finance = new Finance(this);
    public final Capital capital = new Capital(this);
    /** Birlesme-satin alma ve rekabet otoritesi kayitlari. */
    public DealBook deals = new DealBook();
    public final Competition competition = new Competition(this);
    public final Mna mna = new Mna(this);
    /** Mesaj -> tiklaninca yapilacak eylem (ör. "tender:12"). */
    public final Map<String, String> messageActions = new HashMap<>();
    /** Test/dogrulama: kendi hatlari insaat beklemeden aktif olur (kayda yazilmaz). */
    public boolean instantLines;
    /** Gozlem sayaclari (rapor/test): rakiplerin kurdugu TM, yaptigi hat yukseltmesi, yukseltme kayitlari. */
    public transient int statRivalTms, statRivalUpgrades, statRivalParallel;
    /** Olcum: oyuncuyu da rakip YZ ile oynat (yalnizca harness). */
    public transient boolean aiPlayer;
    /** Olcum: sirket basina kurulan baglanti sayisi {kendi hatti, UŞİ basvurusu}. */
    public final transient Map<Company, int[]> statConnections = new HashMap<>();
    public final transient List<long[]> statUpgradeLog = new ArrayList<>(); // {saat, hatId, ikinciYol(0/1), sureSaat}
    public final Map<Integer, GridNode> nodeById = new HashMap<>();
    /** tile -> node id (-1 yok). Sehir 3x3 kaplar. */
    public final int[] occ;
    public int nextId = 1;
    /** Hazir kurulumlarda (senaryo/rakip baslangici) teslim gecikmesi uygulanmaz. */
    boolean suppressDelays;

    public long hour;
    public boolean gameOver, won;
    public String gameOverReason = "";

    // Gozlem istatistikleri (son saat)
    public double statDemand, statServed, statPrice = 40, statPlayerMW, statRivalMW, statCo2Hour;
    public double statRenewShare;
    // Aylik sistem istatistikleri
    public double monthDemandMWh, monthServedMWh, lastServedRatio = 1;
    public final History history = new History();
    public final ArrayDeque<String> messages = new ArrayDeque<>();

    // Topoloji onbellegi
    private boolean topoDirty = true;
    final List<Component> components = new ArrayList<>();

    public static final class Component {
        public final List<City> cities = new ArrayList<>();
        public final List<Plant> plants = new ArrayList<>();
        public final List<ConnectionPoint> points = new ArrayList<>();
        public double price = 40;
        public double nominalMW, peakMW;
    }

    public GameState(long seed) { this(defaultSettings(seed)); }

    public GameState(GameSettings settings) {
        this.settings = settings.effective();
        this.scenario = this.settings.scenario();
        this.seed = this.settings.seed;
        this.rng = new Random(seed ^ 0x5DEECE66DL);
        this.map = new WorldMap(this.settings.size, seed, this.settings.preset);
        this.occ = new int[map.size * map.size];
        java.util.Arrays.fill(occ, -1);
    }

    private static GameSettings defaultSettings(long seed) {
        GameSettings g = new GameSettings();
        g.seed = seed;
        return g;
    }

    public static GameState newGame(long seed, String companyName) {
        GameSettings g = defaultSettings(seed);
        g.companyName = companyName;
        return newGame(g);
    }

    public static GameState newGame(GameSettings settings) {
        GameState s = new GameState(settings);
        Difficulty d = s.settings.difficulty;
        s.player = new Company(s.settings.companyName, true, 0x2ecc71, Balance.START_CASH * d.cashMult);
        s.companies.add(s.player);
        // Esit baslangic: tum ozel sirketler ayni nakitle baslar; zorluk rakiplerin kaynagini degil davranisini belirler.
        for (int i = 0; i < s.settings.rivals; i++) {
            Company r = new Company(RIVALS[i][0], false, Integer.parseInt(RIVALS[i][1], 16), Balance.START_CASH * d.cashMult);
            r.aiStyle = RIVALS[i][2];
            s.companies.add(r);
        }
        s.hour = 8;
        s.placeCities();
        if (s.settings.rivalsStartWithFleet) s.seedRivals();
        s.scenario.setup.accept(s);
        if (s.settings.legacyFleet) s.placeLegacyFleet(s.scenario.legacyCoverage);
        s.macro.init();
        // Her ozel sirket Kiyibank'ta not bazli isletme kredisi limitiyle baslar (otomatik tahsilatta kullanilir).
        for (Company c : s.companies) if (!c.neutral) s.finance.openRevolverFree(c, Bank.KIYI);
        s.updateValuations();
        s.notify("Hoş geldiniz! Senaryo: " + s.scenario.title);
        return s;
    }

    // ------------------------------------------------------------ olusturma
    private void placeCities() {
        int want = settings.cities;
        int tries = 0;
        List<String> names = new ArrayList<>(List.of(CITY_NAMES));
        java.util.Collections.shuffle(names, rng);   // rastgele sayi akisi (harita yerlesimi) eski tohumlarla ayni kalsin
        names = CityNames.generate(new java.util.Random(settings.seed * 0x9E3779B97F4A7C15L + 17), want);
        double spacing = map.size / Math.sqrt(want) * 0.85;
        int margin = Math.max(4, map.size / 12);
        int maxLevel = Math.max(9, settings.preset.relief - 4);
        while (cities.size() < want && tries++ < 8000) {
            int x = margin + rng.nextInt(map.size - 2 * margin), y = margin + rng.nextInt(map.size - 2 * margin);
            int lv = map.level[map.idx(x, y)];
            if (!allLand(x, y, 2) || lv > maxLevel) continue;
            double minD = spacing * (tries < 3000 ? 1.0 : tries < 6000 ? 0.75 : 0.55);
            boolean ok = true;
            for (City c : cities) if (Math.hypot(c.x - x, c.y - y) < minD) { ok = false; break; }
            if (!ok) continue;
            map.flatten(x, y, 1);
            City c = new City();
            c.id = nextId++; c.x = x; c.y = y; c.name = names.get(cities.size() % names.size());
            c.population = 260_000 + rng.nextInt(650_000);
            c.satisfaction = 0.9;
            cities.add(c); nodeById.put(c.id, c);
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) occ[map.idx(x + dx, y + dy)] = c.id;
        }
        topoDirty = true;
    }

    private boolean allLand(int cx, int cy, int r) {
        for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
            if (!map.inBounds(cx + dx, cy + dy) || map.water[map.idx(cx + dx, cy + dy)]) return false;
            if (occ[map.idx(cx + dx, cy + dy)] >= 0) return false;
        }
        return true;
    }

    private void seedRivals() {
        int ci = 0;
        for (Company r : companies) {
            if (r.player || cities.isEmpty()) continue;
            City c = cities.get(ci++ % cities.size());
            // Eski davranis (secenek): bedava, hemen calisan santral + hat.
            switch (r.aiStyle) {
                case "fossil" -> placeLegacy(r, catalog.defaultFor(PlantType.COAL), c, 0);
                case "green" -> { placeLegacy(r, catalog.defaultFor(PlantType.WIND), c, 0); placeLegacy(r, catalog.defaultFor(PlantType.GAS), c, 0); }
                default -> { placeLegacy(r, catalog.defaultFor(PlantType.GAS), c, 0); placeLegacy(r, catalog.defaultFor(PlantType.SOLAR), c, 0); }
            }
        }
    }

    // ------------------------------------------------------------ kamu eski filosu
    /**
     * Gunluk/mevsimlik talep egrisinin en yuksek carpani: sehrin gercek tepe talebi = peakMW() * bu deger
     * (en yuksek saat x en yuksek mevsim; dispatchHour ile ayni formul, gurultu haric).
     */
    public static double maxDemandMult() {
        double m = 0;
        for (double v : HOUR_CURVE) m = Math.max(m, v);
        return m * (1.07 + 0.05);
    }

    public static final String[] MONTH_NAMES = { "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık" };
    /** "Mart'ta" gibi bulunma hali. */
    static final String[] MONTH_LOC = { "Ocak'ta", "Şubat'ta", "Mart'ta", "Nisan'da", "Mayıs'ta", "Haziran'da", "Temmuz'da", "Ağustos'ta", "Eylül'de", "Ekim'de", "Kasım'da", "Aralık'ta" };

    /**
     * Odeme yapmadan, hemen calisir durumda santral + sehre hat kurar (hat sahibi ayni sirket).
     * Kullanim: kamu eski filosu, senaryo baslangic filolari, "rakipler kurulu santrallerle baslasin" secenegi.
     * Konum bulunamazsa veya ekipman henuz piyasada degilse null.
     */
    public Plant placeLegacy(Company owner, Equipment eq, City city, int ageYears) {
        return placeLegacy(owner, eq, city, ageYears, 4, 16);
    }

    public Plant placeLegacy(Company owner, Equipment eq, City city, int ageYears, double minDist, double maxDist) {
        if (checkEquipment(eq) != null) return null;
        int[] site = RivalAI.findSite(this, eq, city, minDist, maxDist);
        if (site == null) return null;
        suppressDelays = true;
        Plant p = createPlant(owner, eq, site[0], site[1]);
        suppressDelays = false;
        p.buildHoursLeft = 0;
        p.builtAtHour = hour - (long) ageYears * Balance.HOURS_PER_YEAR;
        p.ageStartHour = p.builtAtHour;
        // Kamu santralleri isletmeci (UŞİ) hattiyla baglanir (owner == null).
        Company lineOwner = owner.neutral ? null : owner;
        LineTier tier = owner.neutral ? LineTier.YG : tierFor(p.capacityMW(), lineLength(p, city));
        if (checkLine(p, city, tier) == null) createLine(lineOwner, p, city, lineCost(p, city, tier), tier);
        if (!owner.neutral && p.capacityMW() > tier.capacityMW && checkLine(p, city, tier) == null) createLine(lineOwner, p, city, lineCost(p, city, tier), tier);
        return p;
    }

    /**
     * Her sehrin yakinina, gercek tepe talebinin coverage kati kadar eski komur/gaz kapasitesi kurar
     * (sehre zaten bagli santrallerin kapasitesi dusulur). Kapanislar LEGACY_RETIRE_FROM..TO arasina esit dagitilir.
     */
    void placeLegacyFleet(double coverage) {
        if (coverage <= 0 || cities.isEmpty()) return;
        neutral = new Company(GridBalance.NEUTRAL_NAME, false, GridBalance.NEUTRAL_COLOR, 0);
        neutral.neutral = true;
        companies.add(neutral);
        Equipment coal = catalog.get(GridBalance.LEGACY_COAL_ID), gas = catalog.get(GridBalance.LEGACY_GAS_ID);
        List<Plant> fleet = new ArrayList<>();
        for (City c : cities) {
            double need = c.peakMW() * maxDemandMult() * coverage;
            for (PowerLine l : c.lines) if (l.other(c) instanceof Plant p) need -= p.capacityMW();
            int unit = 0, termik = 0, dg = 0, guard = 0;
            while (need > 1 && guard++ < 20) {
                // Buyuk acik varsa komur, kalan kucuk acik icin gaz; sira esitse donusumlu.
                boolean useCoal = need >= coal.capacityMW * 0.75 && (unit % 2 == 0 || need >= coal.capacityMW + gas.capacityMW);
                Equipment e = useCoal ? coal : gas;
                int age = GridBalance.LEGACY_AGE_MIN + rng.nextInt(GridBalance.LEGACY_AGE_MAX - GridBalance.LEGACY_AGE_MIN + 1);
                Plant p = placeLegacy(neutral, e, c, age, GridBalance.LEGACY_MIN_DIST, GridBalance.LEGACY_MAX_DIST);
                if (p == null) p = placeLegacy(neutral, e, c, age, GridBalance.LEGACY_MIN_DIST, GridBalance.LEGACY_MAX_DIST + 6);
                if (p == null) break;
                p.legacy = true;
                int k = useCoal ? ++termik : ++dg;
                p.customName = c.name + (useCoal ? " Termik" : " Doğalgaz") + (k > 1 ? " " + k : "");
                need -= p.capacityMW();
                fleet.add(p);
                unit++;
            }
        }
        // Kapanis tarihleri: karisik sirayla, aralik boyunca esit aralikli.
        java.util.Collections.shuffle(fleet, rng);
        if (scenario.legacyCoalRetireTo > 0) {
            // Komurden Cikis: komur LEGACY_RETIRE_FROM..legacyCoalRetireTo, gaz normal takvim.
            List<Plant> coalP = new ArrayList<>(), gasP = new ArrayList<>();
            for (Plant p : fleet) (p.type == PlantType.COAL ? coalP : gasP).add(p);
            spreadRetirements(coalP, GridBalance.LEGACY_RETIRE_FROM, scenario.legacyCoalRetireTo);
            spreadRetirements(gasP, GridBalance.LEGACY_RETIRE_FROM, GridBalance.LEGACY_RETIRE_TO);
        } else spreadRetirements(fleet, GridBalance.LEGACY_RETIRE_FROM, GridBalance.LEGACY_RETIRE_TO);
        topoDirty = true;
    }

    private static void spreadRetirements(List<Plant> fleet, int fromYear, int toYear) {
        int from = (fromYear - Balance.START_YEAR) * 12;
        int span = (toYear - fromYear + 1) * 12 - 1;
        for (int i = 0; i < fleet.size(); i++) {
            int off = fleet.size() == 1 ? span / 2 : (int) Math.round(i * span / (double) (fleet.size() - 1));
            int abs = from + off;
            fleet.get(i).retireYear = Balance.START_YEAR + abs / 12;
            fleet.get(i).retireMonth = abs % 12;
        }
    }

    /** Ay basinda: kapanisa LEGACY_NOTICE_MONTHS kalan santraller icin haber, suresi dolanlari iadesiz kapat. */
    private void retireLegacy() {
        int now = (int) (hour / Balance.HOURS_PER_MONTH);
        for (Plant p : new ArrayList<>(plants)) {
            if (p.retireYear <= 0) continue;
            int at = (p.retireYear - Balance.START_YEAR) * 12 + p.retireMonth;
            if (at - now == GridBalance.LEGACY_NOTICE_MONTHS)
                notify("Kamu: " + p.name() + " " + p.retireYear + " " + MONTH_LOC[p.retireMonth] + " kapanacak");
            if (at <= now) {
                notify("Kamu: " + p.name() + " kapatıldı (" + (int) p.capacityMW() + " MW)");
                demolishPlant(p, false);
            }
        }
    }

    // ------------------------------------------------------------ sorgular
    public GridNode nodeAt(int x, int y) {
        if (!map.inBounds(x, y)) return null;
        int id = occ[map.idx(x, y)];
        return id < 0 ? null : nodeById.get(id);
    }
    public GridNode nodeById(int id) { return nodeById.get(id); }

    public long year() { return Balance.START_YEAR + hour / Balance.HOURS_PER_YEAR; }
    public int month() { return (int) ((hour / Balance.HOURS_PER_MONTH) % 12); }
    public int dayOfMonth() { return (int) ((hour / Balance.HOURS_PER_DAY) % Balance.DAYS_PER_MONTH) + 1; }
    public int hourOfDay() { return (int) (hour % 24); }
    public int dayIndex() { return (int) (hour / 24); }

    public double assetValue(Company c) {
        double v = 0;
        for (Plant p : plants) if (p.owner == c) v += p.bookValue(hour);
        for (PowerLine l : lines) if (l.owner == c && l.status != LineStatus.PLANNED && l.status != LineStatus.QUEUED) v += l.bookValue(hour);
        for (Substation t : substations) if (t.owner == c) v += t.bookValue(hour);
        return v;
    }

    /** Gosterge faiz: Kiyibank degisken ticari kredi. */
    public double interestRate(Company c) { return macro.policyRate + finance.commercialSpread(c, Bank.KIYI, false, false); }
    public double creditLimit(Company c) { return finance.creditLimit(c); }

    public void notify(String msg) {
        messages.add(msg);
        while (messages.size() > 200) messages.poll();
    }

    /** Tiklanabilir bildirim: arayuz mesaja tiklaninca action'i calistirir. */
    public void notify(String msg, String action) {
        notify(msg);
        messageActions.put(msg, action);
        if (messageActions.size() > 100) messageActions.clear();
    }

    public int countPlants(Company c, PlantType t) {
        int n = 0;
        for (Plant p : plants) if (p.owner == c && p.type == t) n++;
        return n;
    }

    public double lastPlayerMarketShare() {
        double tot = 0;
        for (Company c : companies) tot += c.lastMwhGenerated;
        return tot > 0 ? player.lastMwhGenerated / tot : 0;
    }

    // ------------------------------------------------------------ insaat
    /** Null donerse yer uygun, aksi halde sebep. */
    public String checkPlacement(PlantType t, int x, int y) {
        if (!map.inBounds(x, y)) return "Harita dışı";
        if (map.isWater(x, y)) return "Suya inşa edilemez";
        if (occ[map.idx(x, y)] >= 0) return "Alan dolu";
        if (t == PlantType.HYDRO && map.hydro[map.idx(x, y)] < 0.18) return "Hidro için su kenarı gerekli";
        if (t == PlantType.NUCLEAR && map.hydro[map.idx(x, y)] < 0.10) return "Nükleer için soğutma suyu gerekli";
        if (t == PlantType.NUCLEAR) {
            for (City c : cities) if (Math.hypot(c.x - x, c.y - y) < 5) return "Şehre çok yakın (min 5)";
        }
        return null;
    }

    public String checkEquipment(Equipment e) {
        if (e.availableYear > year()) return e.fullName() + " " + e.availableYear + " yılında piyasaya çıkacak";
        return null;
    }

    public Plant buildPlant(Company owner, PlantType t, int x, int y) {
        return buildPlant(owner, catalog.defaultFor(t), x, y);
    }

    public Plant buildPlant(Company owner, Equipment eq, int x, int y) {
        if (checkPlacement(eq.type, x, y) != null || checkEquipment(eq) != null) return null;
        double cost = eq.capex();
        // Ayni kural herkese: nakdi yetmeyen sirket insa edemez.
        if (owner.cash < cost - 1) return null;
        owner.cash -= cost; owner.capexSpent += cost;
        return createPlant(owner, eq, x, y);
    }

    /** Odeme kontrolu yapmadan santral nesnesini olusturur (buildPlant ve placeLegacy ortak yolu). */
    private Plant createPlant(Company owner, Equipment eq, int x, int y) {
        Plant p = new Plant();
        p.id = nextId++; p.type = eq.type; p.equipment = eq; p.x = x; p.y = y; p.owner = owner;
        p.buildHoursLeft = eq.buildDays() * 24;
        if (!suppressDelays && rng.nextDouble() < eq.supplier.delayChance()) {
            int extra = Math.min(90 * 24, (int) (p.buildHoursLeft * (0.15 + rng.nextDouble() * 0.3)));
            p.buildHoursLeft += extra;
            if (owner.player) notify("TEDARİK: " + eq.supplier.name + " teslimatı " + extra / 24 + " gün gecikecek");
        }
        p.builtAtHour = hour + p.buildHoursLeft;
        p.ageStartHour = p.builtAtHour;
        plants.add(p); nodeById.put(p.id, p); occ[map.idx(x, y)] = p.id;
        topoDirty = true;
        return p;
    }

    /** Null donerse basladi, aksi halde sebep. */
    public String startUpgrade(Plant p, Upgrade u) {
        if (!p.canUpgrade(u)) return "Bu yükseltme şu an yapılamaz";
        double cost = u.cost(p);
        if (p.owner.cash < cost) return "Yetersiz nakit (" + Math.round(cost / 1e6) + "M $ gerekli)";
        p.owner.cash -= cost; p.owner.capexSpent += cost;
        p.pendingUpgrade = u; p.upgradeHoursLeft = u.days * 24;
        return null;
    }

    private void finishUpgrade(Plant p) {
        Upgrade u = p.pendingUpgrade;
        p.upgrades.add(u);
        if (u == Upgrade.REPOWER || u == Upgrade.HYDRO_RETROFIT || u == Upgrade.LIFE_EXT) p.ageStartHour = hour;
        if (u == Upgrade.REPOWER || u == Upgrade.LIFE_EXT) p.builtAtHour = Math.max(p.builtAtHour, hour - Balance.HOURS_PER_YEAR * 5L);
        p.pendingUpgrade = null; p.upgradeHoursLeft = 0;
        if (p.owner.player) notify(p.type.label + ": " + u.label + " tamamlandı");
        topoDirty = true;
    }

    public double lineLength(GridNode a, GridNode b) { return Math.hypot(a.x - b.x, a.y - b.y); }

    public double lineCost(GridNode a, GridNode b) { return lineCost(a, b, LineTier.YG); }

    /** Santral gucunu tasiyabilen en ucuz kademe (uzunluk sinirina uyan). */
    public static LineTier tierFor(double mw, double length) {
        // ÇYG yalnizca CYG_MIN_PLANT_MW ustu santrale; arada kalan santral YG ile (gerekirse ikinci hatla) baglanir.
        for (LineTier t : LineTier.values()) {
            if (t == LineTier.CYG && mw < GridBalance.CYG_MIN_PLANT_MW) continue;
            if (t.capacityMW >= mw && length <= t.maxLength) return t;
        }
        return mw >= GridBalance.CYG_MIN_PLANT_MW && length <= LineTier.CYG.maxLength ? LineTier.CYG : LineTier.YG;
    }

    /** Guzergah ozeti: gecilen karelerin su/yuksek arazi orani, ortalama maliyet carpani ve icinden gecilen sehir. */
    public static final class Route {
        public double waterFrac, highFrac, mult = 1;
        public City throughCity;
    }

    /** Duz hat guzergahindaki kareler (yarim kare adimla ornekleme; tekrarsiz). */
    public Route route(GridNode a, GridNode b) {
        Route r = new Route();
        double len = lineLength(a, b);
        int steps = Math.max(1, (int) Math.ceil(len * 2));
        int n = 0, w = 0, hi = 0, last = -1;
        double sum = 0;
        for (int i = 0; i <= steps; i++) {
            int x = (int) Math.floor(a.x + 0.5 + (b.x - a.x) * i / (double) steps);
            int y = (int) Math.floor(a.y + 0.5 + (b.y - a.y) * i / (double) steps);
            if (!map.inBounds(x, y)) continue;
            int idx = map.idx(x, y);
            if (idx == last) continue;
            last = idx;
            if (occ[idx] >= 0 && r.throughCity == null && nodeById.get(occ[idx]) instanceof City c && c != a && c != b) r.throughCity = c;
            n++;
            double m = 1;
            if (map.water[idx]) { w++; m = GridBalance.ROUTE_WATER_MULT; }
            else if (map.level[idx] >= GridBalance.ROUTE_HIGH_LEVEL) { hi++; m = GridBalance.ROUTE_HIGH_MULT; }
            sum += m;
        }
        if (n > 0) { r.waterFrac = w / (double) n; r.highFrac = hi / (double) n; r.mult = sum / n; }
        return r;
    }

    /** Indirimsiz guzergah maliyeti (uzunluk x kare maliyeti x guzergah carpani). */
    public double routeCost(GridNode a, GridNode b, LineTier tier) {
        return lineLength(a, b) * tier.costPerTile * route(a, b).mult;
    }

    /** Ayni iki uc arasinda (herhangi durumda) hat var mi. */
    public boolean hasParallel(GridNode a, GridNode b) {
        for (PowerLine l : a.lines) if (l.other(a) == b) return true;
        return false;
    }

    public double lineCost(GridNode a, GridNode b, LineTier tier) {
        double c = routeCost(a, b, tier);
        return hasParallel(a, b) ? c * (1 - GridBalance.PARALLEL_DISCOUNT) : c;
    }

    public String checkLine(GridNode a, GridNode b) { return checkLine(a, b, LineTier.YG); }

    public String checkLine(GridNode a, GridNode b, LineTier tier) {
        if (a == null || b == null) return "Bağlantı noktası seçin";
        if (a == b) return "Aynı nokta";
        if (a instanceof City && b instanceof City) return "Şehirleri doğrudan bağlayamazsınız";
        if (lineLength(a, b) > tier.maxLength) return tier.shortLabel + " hat çok uzun (en fazla " + (int) tier.maxLength + " kare)";
        int dup = 0;
        for (PowerLine l : a.lines) if (l.other(a) == b) dup++;
        if (dup >= 3) return "Bu iki nokta arası en fazla 3 hat";
        City through = route(a, b).throughCity;
        if (through != null) return "Hat şehir içinden geçemez (" + through.name + ")";
        if (tier == LineTier.CYG && (!cygEndpoint(a) || !cygEndpoint(b)))
            return "ÇYG yalnızca TM380, şehir veya " + (int) GridBalance.CYG_MIN_PLANT_MW + " MW üstü tekil santrale bağlanır";
        return null;
    }

    /** Sahip bilgisiyle: rakip trafo merkezine yalnizca erisime aciksa hat cekilebilir. */
    public String checkLine(Company owner, GridNode a, GridNode b, LineTier tier) {
        String why = checkLine(a, b, tier);
        if (why != null) return why;
        for (GridNode n : new GridNode[] { a, b })
            if (n instanceof Substation t && t.owner != owner && !t.openAccess) return t.name() + " üçüncü taraf erişimine kapalı";
        return null;
    }

    private boolean cygEndpoint(GridNode n) {
        if (n instanceof City) return true;
        if (n instanceof Substation t) return t.level >= 2;
        if (n instanceof Plant p) return p.capacityMW() >= GridBalance.CYG_MIN_PLANT_MW && !inBara(p);
        return false;
    }

    /** Ayni sirketin bitisik (Chebyshev <= BARA_DISTANCE) baska bir santrali var mi. */
    public boolean inBara(Plant p) {
        int r = GridBalance.BARA_DISTANCE;
        for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
            if (dx == 0 && dy == 0) continue;
            if (nodeAt(p.x + dx, p.y + dy) instanceof Plant o && o.owner == p.owner) return true;
        }
        return false;
    }

    public PowerLine buildLine(Company owner, GridNode a, GridNode b) { return buildLine(owner, a, b, LineTier.YG); }

    public PowerLine buildLine(Company owner, GridNode a, GridNode b, LineTier tier) {
        if (checkLine(owner, a, b, tier) != null) return null;
        double cost = lineCost(a, b, tier);
        if (owner.cash < cost) return null;
        owner.cash -= cost; owner.capexSpent += cost;
        PowerLine l = createLine(owner, a, b, cost, tier);
        statConnections.computeIfAbsent(owner, k -> new int[2])[0]++;
        if (instantLines) return l;
        // Kendi hatti: pesin odenir, insaat suresi (taban + kare basina); %10 olasilikla %10-30 gecikme.
        l.status = LineStatus.BUILDING;
        l.buildHoursLeft = tier.buildDays(l.lengthTiles) * 24;
        if (!suppressDelays && rng.nextDouble() < GridBalance.LINE_DELAY_CHANCE) {
            int extra = (int) Math.round(l.buildHoursLeft * (GridBalance.LINE_DELAY_MIN + rng.nextDouble() * (GridBalance.LINE_DELAY_MAX - GridBalance.LINE_DELAY_MIN)));
            l.buildHoursLeft += extra;
            if (owner.player) notify("Hat inşaatı " + Math.max(1, extra / 24) + " gün gecikecek (" + GridOperator.label(l) + ")");
        }
        l.builtAtHour = hour + l.buildHoursLeft;
        return l;
    }

    /** Kademe yukseltme bedeli: (yeni - eski indirimsiz maliyet) x UPGRADE_COST_FACTOR. */
    public double upgradeCost(PowerLine l) {
        LineTier to = l.tier.up();
        if (to == null) return 0;
        return (routeCost(l.a, l.b, to) - routeCost(l.a, l.b, l.tier)) * GridBalance.UPGRADE_COST_FACTOR;
    }

    public int upgradeHours(PowerLine l) {
        LineTier to = l.tier.up();
        return to == null ? 0 : (int) Math.round(to.buildDays(l.lengthTiles) * GridBalance.UPGRADE_TIME_MULT) * 24;
    }

    /** Null: yukseltme yapilabilir; aksi halde sebep. */
    public String checkUpgrade(Company c, PowerLine l) {
        LineTier to = l.tier.up();
        if (to == null) return "Zaten en üst kademe";
        if (l.owner != c) return "Yalnızca kendi hattınızı yükseltebilirsiniz";
        if (l.status != LineStatus.ACTIVE) return "Hat aktif değil";
        if (l.lengthTiles > to.maxLength) return to.shortLabel + " için hat çok uzun";
        if (to == LineTier.CYG && (!cygEndpoint(l.a) || !cygEndpoint(l.b))) return "ÇYG uçları uygun değil (TM380, şehir veya 400 MW+ tekil santral)";
        if (c.cash < upgradeCost(l)) return "Yetersiz nakit";
        return null;
    }

    /** Kademe yukseltmesini baslatir: hat sure boyunca kapali (UPGRADING). */
    public String upgradeLine(Company c, PowerLine l) {
        String why = checkUpgrade(c, l);
        if (why != null) return why;
        double cost = upgradeCost(l);
        c.cash -= cost; c.capexSpent += cost;
        l.upgradeTo = l.tier.up(); l.upgradeCost = cost;
        l.status = LineStatus.UPGRADING;
        l.buildHoursLeft = upgradeHours(l);
        topoDirty = true;
        return null;
    }

    private void finishLineUpgrade(PowerLine l) {
        l.tier = l.upgradeTo; l.buildCost += l.upgradeCost;
        l.upgradeTo = null; l.upgradeCost = 0;
        l.status = LineStatus.ACTIVE; l.buildHoursLeft = 0;
        topoDirty = true;
        if (affectsPlayer(l)) notify("Hat yükseltmesi tamamlandı: " + GridOperator.label(l) + " (" + Math.round(l.capacityMW()) + " MW)");
    }

    /** Hat oyuncuyu ilgilendiriyor mu: sahibi/basvurani oyuncu ya da bir ucu oyuncunun santrali/TM'si. */
    public boolean affectsPlayer(PowerLine l) {
        if (l.owner != null && l.owner.player || l.requester != null && l.requester.player) return true;
        for (GridNode n : new GridNode[] { l.a, l.b })
            if (n instanceof Plant p && p.owner.player || n instanceof Substation t && t.playerOwned()) return true;
        return false;
    }

    /** Saatlik hat ariza zari ve sayaclari. Onarim bedeli sahibine (UŞİ ucretsiz). */
    private void lineOutages() {
        for (int i = 0; i < lines.size(); i++) {
            PowerLine l = lines.get(i);
            if (l.outageHoursLeft > 0) {
                if (--l.outageHoursLeft == 0) {
                    topoDirty = true;
                    if (affectsPlayer(l)) notify("Hat arızası giderildi: " + GridOperator.label(l));
                }
                continue;
            }
            if (l.status != LineStatus.ACTIVE) continue;
            if (rng.nextDouble() >= l.tier.outagePerTileHour * l.lengthTiles) continue;
            l.outageHoursLeft = GridBalance.LINE_OUTAGE_MIN_H + rng.nextInt(GridBalance.LINE_OUTAGE_MAX_H - GridBalance.LINE_OUTAGE_MIN_H + 1);
            topoDirty = true;
            double repair = l.owner == null ? 0 : l.buildCost * GridBalance.LINE_REPAIR_COST;
            if (l.owner != null) { l.owner.cash -= repair; l.owner.omCost += repair; }
            if (affectsPlayer(l)) notify("HAT ARIZASI: " + GridOperator.label(l) + " " + l.outageHoursLeft + " saat devre dışı"
                + (repair > 0 ? " · onarım " + Math.round(repair / 1e3) + "K $" : " · onarım UŞİ'de"));
        }
    }

    /** Insaati/planlamasi biten hatti devreye alir; iki ayri sehirli sebeke parcasi birlesirse duyurur. */
    void activateLine(PowerLine l) {
        Component ca = componentOf(l.a), cb = componentOf(l.b);
        boolean merge = ca != null && cb != null && ca != cb && !ca.cities.isEmpty() && !cb.cities.isEmpty();
        l.status = LineStatus.ACTIVE; l.buildHoursLeft = 0; l.builtAtHour = hour;
        topoDirty = true;
        boolean mine = (l.owner != null && l.owner.player) || (l.requester != null && l.requester.player);
        if (mine || l.operatorPlan) notify("Hat devreye girdi: " + GridOperator.label(l));
        if (merge) notify(ca.cities.get(0).name + " ve " + cb.cities.get(0).name + " piyasaları birleşti");
    }

    PowerLine createLine(Company owner, GridNode a, GridNode b, double cost, LineTier tier) {
        PowerLine l = new PowerLine();
        l.id = nextId++; l.a = a; l.b = b; l.owner = owner; l.buildCost = cost; l.lengthTiles = lineLength(a, b);
        l.tier = tier; l.status = LineStatus.ACTIVE; l.builtAtHour = hour;
        lines.add(l); a.lines.add(l); b.lines.add(l);
        topoDirty = true;
        return l;
    }

    public void demolishPlant(Plant p) { demolishPlant(p, true); }

    public void demolishPlant(Plant p, boolean refund) {
        if (!plants.contains(p)) return;
        for (PowerLine l : new ArrayList<>(p.lines)) removeLine(l, false);
        if (refund) p.owner.cash += p.bookValue(hour) * Balance.DEMOLISH_REFUND;
        plants.remove(p); nodeById.remove(p.id); occ[map.idx(p.x, p.y)] = -1;
        topoDirty = true;
    }

    public void removeLine(PowerLine l, boolean refund) {
        if (!lines.remove(l)) return;
        l.a.lines.remove(l); l.b.lines.remove(l);
        if (refund && l.owner != null) l.owner.cash += l.bookValue(hour) * Balance.DEMOLISH_REFUND;
        topoDirty = true;
    }

    // ------------------------------------------------------------ trafo merkezi
    public double substationCost(int level) { return level >= 2 ? GridBalance.TM380_COST : GridBalance.TM154_COST; }
    public int substationDays(int level) { return level >= 2 ? GridBalance.TM380_DAYS : GridBalance.TM154_DAYS; }

    public String checkSubstation(int x, int y) {
        if (!map.inBounds(x, y)) return "Harita dışı";
        if (map.isWater(x, y)) return "Suya kurulamaz";
        if (occ[map.idx(x, y)] >= 0) return "Alan dolu";
        return null;
    }

    public Substation buildSubstation(Company owner, int level, int x, int y) {
        if (checkSubstation(x, y) != null) return null;
        double cost = substationCost(level);
        if (owner.cash < cost) return null;
        owner.cash -= cost; owner.capexSpent += cost;
        Substation t = new Substation();
        t.id = nextId++; t.x = x; t.y = y; t.owner = owner; t.level = level;
        t.transformerMW = level >= 2 ? GridBalance.TM380_MW : GridBalance.TM154_MW;
        t.buildHoursLeft = substationDays(level) * 24; t.buildCost = cost; t.builtAtHour = hour + t.buildHoursLeft;
        substations.add(t); nodeById.put(t.id, t); occ[map.idx(x, y)] = t.id;
        topoDirty = true;
        return t;
    }

    /** kind 1 = +600 MW trafo, 2 = TM154 -> TM380. Null: basladi; aksi halde sebep. TM calismaya devam eder. */
    public String upgradeSubstation(Substation t, int kind) {
        if (!t.operational()) return "Trafo merkezi henüz inşa halinde";
        if (t.pendingUpgrade != 0) return "Devam eden bir yükseltme var";
        if (kind == 2 && t.level >= 2) return "Zaten TM380";
        double cost = kind == 1 ? GridBalance.TM_PLUS_COST : GridBalance.TM380_UPGRADE_COST;
        if (t.owner == null) return "UŞİ'nin trafo merkezi";
        if (t.owner.cash < cost) return "Yetersiz nakit (" + Math.round(cost / 1e6) + "M $ gerekli)";
        t.owner.cash -= cost; t.owner.capexSpent += cost; t.buildCost += cost;
        t.pendingUpgrade = kind;
        t.upgradeHoursLeft = (kind == 1 ? GridBalance.TM_PLUS_DAYS : GridBalance.TM380_UPGRADE_DAYS) * 24;
        return null;
    }

    private void finishSubstationUpgrade(Substation t) {
        if (t.pendingUpgrade == 1) t.transformerMW += GridBalance.TM_PLUS_MW;
        else { t.level = 2; t.transformerMW = Math.max(t.transformerMW, GridBalance.TM380_MW); }
        if (t.playerOwned()) notify(t.name() + ": yükseltme tamamlandı (" + Math.round(t.transformerMW) + " MW)");
        t.pendingUpgrade = 0; t.upgradeHoursLeft = 0;
        topoDirty = true;
    }

    public void demolishSubstation(Substation t, boolean refund) {
        if (!substations.remove(t)) return;
        for (PowerLine l : new ArrayList<>(t.lines)) removeLine(l, refund);
        for (Plant p : plants) if (p.preferredTm == t.id) p.preferredTm = 0;
        if (refund && t.owner != null) t.owner.cash += t.bookValue(hour) * Balance.DEMOLISH_REFUND;
        nodeById.remove(t.id); occ[map.idx(t.x, t.y)] = -1;
        topoDirty = true;
    }

    /** Santralin baglanabilecegi trafo merkezleri: yaricap icinde, calisan; kendi TM'si ya da erisime acik. */
    public List<Substation> substationsFor(Plant p) {
        List<Substation> r = new ArrayList<>();
        for (Substation t : substations)
            if (t.operational() && t.inRange(p.x, p.y) && (t.owner == p.owner || t.openAccess)) r.add(t);
        return r;
    }

    /** Santrali bir TM'ye aktarir (0: otomatik). */
    public void setPreferredSubstation(Plant p, int tmId) { p.preferredTm = tmId; topoDirty = true; }

    /** Santralin bu topolojide uyesi olacagi TM (yoksa null -> bara/tek). */
    private Substation substationOf(Plant p) {
        if (p.preferredTm != 0 && nodeById.get(p.preferredTm) instanceof Substation t
            && t.operational() && t.inRange(p.x, p.y) && (t.owner == p.owner || t.openAccess)) return t;
        Substation best = null; double bd = Double.MAX_VALUE;
        for (Substation t : substations) {
            if (t.owner != p.owner || !t.operational() || !t.inRange(p.x, p.y)) continue;
            double d = Math.hypot(t.x - p.x, t.y - p.y);
            if (d < bd) { bd = d; best = t; }
        }
        return best;
    }

    /** Kisayol: Kiyibank'tan 5 yillik, 3 ayda bir taksitli degisken ticari kredi (LOAN_STEP). */
    public boolean borrow(Company c) {
        return finance.signCommercial(c, Bank.KIYI, FinanceBalance.LOAN_STEP, 5, 3, false, null) != null;
    }

    // ------------------------------------------------------------ topoloji
    /** Tum baglanti noktalari (topoloji degisince yeniden kurulur). */
    final List<ConnectionPoint> points = new ArrayList<>();

    public List<ConnectionPoint> connectionPoints() { components(); return points; }

    /** Topoloji degisti (hat durumu/arizasi, insaat bitisi vb.). */
    public void markTopologyDirty() { topoDirty = true; }

    private void rebuildComponents() {
        components.clear();
        buildConnectionPoints();
        List<GridNode> nodes = new ArrayList<>(cities.size() + plants.size() + substations.size());
        nodes.addAll(cities); nodes.addAll(plants);
        for (Substation t : substations) if (t.operational()) nodes.add(t);
        Map<GridNode, Component> comp = new HashMap<>();
        for (GridNode start : nodes) {
            if (comp.containsKey(start)) continue;
            Component c = new Component();
            ArrayDeque<GridNode> q = new ArrayDeque<>();
            q.add(start); comp.put(start, c);
            while (!q.isEmpty()) {
                GridNode n = q.poll();
                if (n instanceof City ct) c.cities.add(ct); else if (n instanceof Plant pl) c.plants.add(pl);
                else if (n instanceof Substation t && t.cp != null && !c.points.contains(t.cp)) c.points.add(t.cp);
                // Yalnizca ACTIVE ve arizada olmayan hatlar sebekeyi olusturur.
                for (PowerLine l : n.lines) {
                    if (!passable(l)) continue;
                    GridNode o = l.other(n);
                    if (!comp.containsKey(o)) { comp.put(o, c); q.add(o); }
                }
                // Ayni baglanti noktasinin uyeleri (bara/TM) kayipsiz bagli sayilir.
                ConnectionPoint cp = cpOf(n);
                if (cp != null) for (GridNode o : cpNodes(cp)) if (!comp.containsKey(o)) { comp.put(o, c); q.add(o); }
            }
            for (Plant p : c.plants) if (p.cp != null && !c.points.contains(p.cp)) c.points.add(p.cp);
            components.add(c);
        }
        computeLosses();
        topoDirty = false;
        refreshComponentStats();
    }

    /** Hat sebekede guc tasiyabilir mi (aktif, arizasiz, uclari calisir durumda). */
    private static boolean passable(PowerLine l) {
        if (!l.usable()) return false;
        if (l.a instanceof Substation t && !t.operational()) return false;
        if (l.b instanceof Substation t && !t.operational()) return false;
        return true;
    }

    public static ConnectionPoint cpOf(GridNode n) {
        return n instanceof Plant p ? p.cp : n instanceof Substation t ? t.cp : null;
    }

    private static List<GridNode> cpNodes(ConnectionPoint cp) {
        List<GridNode> r = new ArrayList<>(cp.members.size() + 1);
        r.addAll(cp.members);
        if (cp.tm != null) r.add(cp.tm);
        return r;
    }

    /**
     * Baglanti noktalari: once calisan trafo merkezleri (yaricaptaki kendi santralleri + aktarilanlar),
     * kalan santraller ayni sirketin bitisik santralleriyle "bara" kumesi (union-find), yalniz kalan tek basina.
     * Bir santral yalnizca bir noktaya uyedir; insaattaki santral uye olur ama uretmez.
     */
    private void buildConnectionPoints() {
        points.clear();
        for (Substation t : substations) {
            t.cp = null;
            if (!t.operational()) continue;
            ConnectionPoint cp = new ConnectionPoint();
            cp.tm = t; cp.transformerMW = t.transformerMW;
            t.cp = cp;
            points.add(cp);
        }
        List<Plant> free = new ArrayList<>();
        for (Plant p : plants) {
            p.cp = null;
            Substation t = substationOf(p);
            if (t != null) { t.cp.members.add(p); p.cp = t.cp; } else free.add(p);
        }
        // bara: union-find (komsuluk occ ile bulunur)
        Map<Plant, Plant> parent = new HashMap<>();
        for (Plant p : free) parent.put(p, p);
        int r = GridBalance.BARA_DISTANCE;
        for (Plant p : free) for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
            if ((dx == 0 && dy == 0) || !(nodeAt(p.x + dx, p.y + dy) instanceof Plant o)) continue;
            if (o.owner != p.owner || !parent.containsKey(o)) continue;
            Plant ra = find(parent, p), rb = find(parent, o);
            if (ra != rb) { if (ra.id < rb.id) parent.put(rb, ra); else parent.put(ra, rb); }
        }
        Map<Plant, ConnectionPoint> byRoot = new HashMap<>();
        for (Plant p : free) {
            ConnectionPoint cp = byRoot.computeIfAbsent(find(parent, p), k -> { ConnectionPoint n = new ConnectionPoint(); points.add(n); return n; });
            cp.members.add(p); p.cp = cp;
        }
        for (ConnectionPoint cp : points) collectExportLines(cp);
    }

    private static Plant find(Map<Plant, Plant> parent, Plant p) {
        Plant q = p;
        while (parent.get(q) != q) q = parent.get(q);
        while (parent.get(p) != q) { Plant n = parent.get(p); parent.put(p, q); p = n; }
        return q;
    }

    static void collectExportLines(ConnectionPoint cp) {
        cp.exportLines.clear();
        for (GridNode m : cpNodes(cp))
            for (PowerLine l : m.lines)
                if (!cp.contains(l.other(m)) && !cp.exportLines.contains(l)) cp.exportLines.add(l);
    }

    /**
     * Her baglanti noktasinin en yakin sehre kaybi: tum sehirlerden baslayan tek bir Dijkstra (agirlik =
     * uzunluk x kademe kayip orani), yalnizca kullanilabilir hatlar uzerinden; en fazla MAX_PATH_LOSS.
     * Bilincli basitlestirme: zincirin ortasindaki hatlarin kapasitesi tek tek kontrol edilmez; gercek akis
     * hesabi ve bolgesel fiyat ileriki bir surume birakilmistir.
     */
    private void computeLosses() {
        Map<GridNode, Double> dist = new HashMap<>();
        java.util.PriorityQueue<Object[]> pq = new java.util.PriorityQueue<>(Comparator.comparingDouble(o -> (Double) o[1]));
        for (City c : cities) { dist.put(c, 0.0); pq.add(new Object[] { c, 0.0 }); }
        while (!pq.isEmpty()) {
            Object[] top = pq.poll();
            GridNode n = (GridNode) top[0];
            double d = (Double) top[1];
            if (d > dist.getOrDefault(n, Double.MAX_VALUE)) continue;
            for (PowerLine l : n.lines) {
                if (!passable(l)) continue;
                GridNode o = l.other(n);
                double nd = d + l.lossFraction();
                if (nd < dist.getOrDefault(o, Double.MAX_VALUE)) { dist.put(o, nd); pq.add(new Object[] { o, nd }); }
            }
            ConnectionPoint cp = cpOf(n);
            if (cp != null) for (GridNode o : cpNodes(cp))
                if (d < dist.getOrDefault(o, Double.MAX_VALUE)) { dist.put(o, d); pq.add(new Object[] { o, d }); }
        }
        for (ConnectionPoint cp : points) {
            double best = Double.MAX_VALUE;
            for (GridNode m : cpNodes(cp)) best = Math.min(best, dist.getOrDefault(m, Double.MAX_VALUE));
            cp.lossFraction = Math.min(GridBalance.MAX_PATH_LOSS, best);
        }
    }

    private void refreshComponentStats() {
        for (Component c : components) {
            c.peakMW = 0; c.nominalMW = 0;
            for (City ct : c.cities) c.peakMW += ct.peakMW();
            for (Plant p : c.plants) if (p.operational()) c.nominalMW += p.capacityMW() * nominalCf(p.type);
        }
    }

    private static double nominalCf(PlantType t) {
        return switch (t) { case SOLAR -> 0.25; case WIND -> 0.33; case HYDRO -> 0.5; default -> 0.9; };
    }

    public List<Component> components() {
        if (topoDirty) rebuildComponents();
        return components;
    }

    public Component componentOf(GridNode n) {
        for (Component c : components()) {
            if (n instanceof City ct ? c.cities.contains(ct) : n instanceof Substation t ? t.cp != null && c.points.contains(t.cp) : c.plants.contains(n)) return c;
        }
        return null;
    }

    // ------------------------------------------------------------ yardimcilar
    /** Yakit yatagina yakinlik indirimi. */
    public double fuelMult(Plant p) {
        double dep = p.type.fuel == PlantType.Fuel.COAL ? nearbyDeposit(map.coal, p.x, p.y)
                   : p.type.fuel == PlantType.Fuel.GAS ? nearbyDeposit(map.gas, p.x, p.y) : 0;
        return 1.0 - 0.28 * dep;
    }

    private double nearbyDeposit(float[] layer, int cx, int cy) {
        double best = 0;
        for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
            int x = cx + dx, y = cy + dy;
            if (map.inBounds(x, y)) best = Math.max(best, layer[map.idx(x, y)] * (1 - 0.2 * Math.max(Math.abs(dx), Math.abs(dy))));
        }
        return Math.min(1, best);
    }

    public double fuelCostPerMWh(Plant p) {
        if (p.type.fuel == PlantType.Fuel.NONE) return 0;
        double legacy = p.legacy ? GridBalance.LEGACY_FUEL_MULT : 1.0;
        return markets.fuelPrice(p.type.fuel) * fuelMult(p) * p.fuelPriceMult() * legacy / p.efficiency(hour);
    }

    public double marginalCost(Plant p) {
        return fuelCostPerMWh(p) + p.type.varOm + markets.carbonPrice * p.co2PerMWh(hour);
    }

    /** Ekipman ve sahaya bagli anlik kapasite faktoru (0..1+). */
    public double capacityFactorNow(Plant p) {
        int h = hourOfDay(), m = month();
        int i = map.idx(p.x, p.y);
        Equipment e = p.equipment;
        switch (p.type) {
            case SOLAR: {
                double winter = (1 + Math.cos(2 * Math.PI * m / 12.0)) / 2;
                double rise = 6.5 + winter * 1.5, set = 17.5 - winter * 1.5;
                if (h + 0.5 <= rise || h + 0.5 >= set) return 0;
                double sunH = Math.sin(Math.PI * (h + 0.5 - rise) / (set - rise));
                if (p.upgrades.contains(Upgrade.TRACKER)) sunH = Math.pow(sunH, 0.6);
                double season = 0.62 + 0.38 * (1 - winter);
                double heat = 1 - e.paramA * Math.max(0, (map.sun[i] - 0.8) / 0.2) * (0.5 + 0.5 * (1 - winter));
                return Math.min(1.2, sunH * season * markets.cloud * map.sun[i] * 1.15 * heat * p.renewableMult(hour) / 1.07);
            }
            case WIND: {
                double speed = markets.wind * map.wind[i];
                double x = (speed - e.paramA) / Math.max(0.1, e.paramB - e.paramA);
                if (x <= 0) return 0;
                return Math.min(1, Math.pow(Math.min(1, x), 1.2) * p.renewableMult(hour));
            }
            case HYDRO: {
                double s = 1 + 0.4 * Math.sin(2 * Math.PI * (m - 1) / 12.0);
                double match = 1.12 - 0.45 * Math.abs(e.paramA - map.head(p.x, p.y));
                return Math.max(0.08, Math.min(0.98, 0.5 * (0.6 + 0.8 * map.hydro[i]) * s * match * p.renewableMult(hour)));
            }
            default:
                return 1.0;
        }
    }

    /** Arayuz icin: sahanin bu ekipman icin ortalama uretim beklentisi (0..1). */
    public double siteScore(Equipment e, int x, int y) {
        int i = map.idx(x, y);
        switch (e.type) {
            case SOLAR: {
                double heat = 1 - e.paramA * Math.max(0, (map.sun[i] - 0.8) / 0.2) * 0.75;
                return 0.21 * map.sun[i] / 0.85 * heat * e.outputMult;
            }
            case WIND:
                return expectedWindCf(e, map.wind[i]);
            case HYDRO: {
                double match = 1.12 - 0.45 * Math.abs(e.paramA - map.head(x, y));
                return Math.min(0.98, 0.5 * (0.6 + 0.8 * map.hydro[i]) * match);
            }
            default: return 0.9;
        }
    }

    /** Tipik ruzgar dagilimi altinda beklenen kapasite faktoru. */
    public static double expectedWindCf(Equipment e, double siteWind) {
        double sum = 0, norm = 0;
        for (int k = 0; k < 20; k++) {
            double w = 0.03 + k * 0.045;
            double weight = Math.exp(-Math.pow((w - 0.36) / 0.2, 2));
            double xx = (w * siteWind - e.paramA) / Math.max(0.1, e.paramB - e.paramA);
            sum += weight * (xx <= 0 ? 0 : Math.pow(Math.min(1, xx), 1.2));
            norm += weight;
        }
        return sum / norm;
    }

    // ------------------------------------------------------------ tick
    public void tick() {
        if (gameOver) return;
        int hod = hourOfDay();
        if (hod == 0) for (Plant p : plants) if (p.operational()) {
            // yeni gun: halka tamponda bir sonraki gunu sifirla
            if (p.daysTracked > 0 || p.dailyMWh[p.dayIdx] > 0) { p.dayIdx = (p.dayIdx + 1) % 30; p.dailyMWh[p.dayIdx] = 0; p.dailyCurtailMWh[p.dayIdx] = 0; }
            p.daysTracked = Math.min(30, p.daysTracked + 1);
        }
        markets.hourly(rng);
        macro.hourly();
        if (hod == 0) {
            markets.daily(rng, (int) (hour / Balance.HOURS_PER_YEAR)); tenders.daily(); sales.daily();
            capital.daily(); competition.daily(); mna.daily();
            for (int ci = 0; ci < companies.size(); ci++) { Company c = companies.get(ci); finance.daily(c); finance.dueWarnings(c); }
        }

        boolean changed = false;
        for (Plant p : plants) {
            if (p.buildHoursLeft > 0) {
                if (--p.buildHoursLeft == 0) {
                    changed = true;
                    if (p.owner.player) notify(p.type.label + " devreye girdi (" + (int) p.capacityMW() + " MW, " + p.equipment.fullName() + ")");
                }
                continue;
            }
            if (p.upgrading() && --p.upgradeHoursLeft <= 0) { finishUpgrade(p); changed = true; }
            if (p.outageHoursLeft > 0) {
                if (--p.outageHoursLeft == 0 && p.owner.player) notify(p.type.label + " arızası giderildi");
            } else if (p.online()) {
                double base = p.type.renewable ? Balance.OUTAGE_CHANCE_PER_HOUR * 0.3 : Balance.OUTAGE_CHANCE_PER_HOUR;
                double age = p.wearYears(hour) / p.type.lifeYears;
                double chance = base * p.equipment.supplier.outageMult() * (1 + age * 0.8) * (p.upgrades.contains(Upgrade.LIFE_EXT) ? 0.6 : 1);
                if (rng.nextDouble() < chance) {
                    p.outageHoursLeft = p.type == PlantType.NUCLEAR ? 200 + rng.nextInt(200) : 24 + rng.nextInt(72);
                    double repair = p.underWarranty(hour) ? 0 : p.equipment.listCapex() * (p.type.renewable ? 0.006 : 0.012);
                    p.owner.cash -= repair; p.owner.omCost += repair; p.monthProfit -= repair;
                    if (p.capacityMW() >= FinanceBalance.NEWS_OUTAGE_MW) capital.news(p.owner, FinanceBalance.NEWS_OUTAGE, "büyük arıza (" + p.type.label + ", " + Math.round(p.capacityMW()) + " MW)");
                    if (p.owner.player) notify("ARIZA: " + p.type.label + " devre dışı. "
                        + (repair > 0 ? "Onarım " + Math.round(repair / 1e6 * 10) / 10.0 + "M $" : "Garanti kapsamında (" + p.equipment.supplier.name + ")"));
                }
            }
        }
        for (Substation t : substations) {
            if (t.buildHoursLeft > 0) {
                if (--t.buildHoursLeft == 0) {
                    changed = true; t.builtAtHour = hour;
                    if (t.playerOwned()) notify(t.name() + " devreye girdi (" + Math.round(t.transformerMW) + " MW trafo)");
                }
            } else if (t.pendingUpgrade != 0 && --t.upgradeHoursLeft <= 0) finishSubstationUpgrade(t);
        }
        if (changed) topoDirty = true;
        for (int i = 0; i < lines.size(); i++) {
            PowerLine l = lines.get(i);
            if (l.status == LineStatus.BUILDING && --l.buildHoursLeft <= 0) activateLine(l);
            else if (l.status == LineStatus.UPGRADING && --l.buildHoursLeft <= 0) finishLineUpgrade(l);
        }
        lineOutages();
        operator.hourly();

        dispatchHour();

        for (PowerLine l : lines) {
            if (l.owner == null || l.status != LineStatus.ACTIVE && l.status != LineStatus.UPGRADING) continue;
            double om = l.omPerHour();
            l.owner.cash -= om; l.owner.omCost += om;
        }
        for (City c : cities) c.satisfaction += 0.04 * ((c.demandMW <= 0 ? 1 : c.servedMW / c.demandMW) - c.satisfaction);

        hour++;
        if (hour % Balance.HOURS_PER_MONTH == 0) monthly();
    }

    private static final double[] HOUR_CURVE = {
        0.62, 0.58, 0.55, 0.54, 0.56, 0.63, 0.76, 0.90, 0.95, 0.93, 0.92, 0.93,
        0.94, 0.93, 0.92, 0.93, 0.97, 1.04, 1.10, 1.07, 1.00, 0.90, 0.78, 0.68 };

    private void dispatchHour() {
        int hod = hourOfDay();
        int day = dayIndex();
        double season = 1.07 + 0.05 * Math.cos(2 * Math.PI * month() / 12.0);
        double weekend = (day % 7 >= 5) ? 0.93 : 1.0;
        double dmult = HOUR_CURVE[hod] * season * weekend * markets.demandNoise;
        for (City c : cities) { c.demandMW = c.peakMW() * dmult; c.servedMW = 0; c.flowMW = 0; }
        for (Plant p : plants) { p.outputMW = 0; p.flowMW = 0; p.availableMW = 0; p.curtailedMW = 0; p.idleReason = null; }

        double totDemand = 0, totServed = 0, priceWeighted = 0, priceDemand = 0, playerMW = 0, rivalMW = 0, renew = 0, co2 = 0;
        double fineMult = settings.difficulty.fineMult;

        tenders.resetHour();
        for (Component comp : components()) {
            double demand = 0, rawDemand = 0;
            for (City ct : comp.cities) {
                demand += Math.min(ct.demandMW, ct.lineCapacity());
                rawDemand += ct.demandMW;
            }
            totDemand += rawDemand;

            List<Plant> cand = new ArrayList<>(comp.plants.size());
            for (Plant p : comp.plants) {
                if (!p.online()) continue;
                // Hat siniri artik santral bazinda degil, baglanti noktasinin kalan ihracat kapasitesiyle uygulanir.
                double avail = p.capacityMW() * capacityFactorNow(p);
                p.availableMW = avail;
                if (avail > 0.01) cand.add(p); else p.idleReason = "Kaynak yok (rüzgar/güneş/su yetersiz)";
            }
            for (ConnectionPoint cp : comp.points) { cp.remainingMW = cp.exportCapacityMW(); cp.exportedMWThisHour = 0; }
            if (cand.isEmpty() || demand <= 0) {
                String why = comp.cities.isEmpty() ? "Şehre bağlı değil (hat yok)" : "Talep yok";
                for (Plant p : cand) p.idleReason = why;
                comp.price = demand > 0 ? Balance.SCARCITY_PRICE : Math.max(Balance.MIN_PRICE, comp.price * 0.98);
                if (demand > 0) fineOwners(comp, demand * fineMult);
                continue;
            }
            // Teklif = marjinal maliyet (fosile 1,04); liyakat sirasi teklif / (1 - kayip): sehre yakin santral one gecer.
            final Map<Plant, Double> mc = new HashMap<>(cand.size() * 2), merit = new HashMap<>(cand.size() * 2);
            for (Plant p : cand) {
                double bid = marginalCost(p) * (p.type.renewable ? 1.0 : 1.04);
                mc.put(p, bid);
                merit.put(p, bid / (1 - p.cp.lossFraction));
            }
            cand.sort(Comparator.<Plant>comparingDouble(pl -> merit.get(pl)).thenComparingInt(pl -> pl.id));

            // remaining: sehirlere teslim edilmesi gereken (net) MW. Santral cikisi kendi yolunun kaybiyla azalir.
            double remaining = demand, price = Balance.MIN_PRICE;
            for (Plant p : cand) {
                if (remaining <= 1e-6) break;
                ConnectionPoint cp = p.cp;
                double keep = 1 - cp.lossFraction;
                double want = Math.min(p.availableMW, remaining / keep);
                double out = Math.min(want, cp.remainingMW);
                if (want - out > 1e-6) p.curtailedMW = want - out;
                if (out <= 1e-9) { p.idleReason = cp.exportCapacityMW() <= 0 ? "Bağlantı hattı yok" : "Bağlantı dolu"; continue; }
                p.outputMW = out;
                cp.remainingMW -= out;
                cp.exportedMWThisHour += out;
                remaining -= out * keep;
                price = Math.max(Balance.MIN_PRICE, mc.get(p));
            }
            for (Plant p : cand) if (p.outputMW <= 0 && p.idleReason == null) p.idleReason = "Liyakat sırası dışında (talep daha ucuz santrallerle karşılandı)";
            double delivered = demand - Math.max(0, remaining);
            double ratio = demand > 0 ? Math.min(1, delivered / demand) : 1;
            boolean shortage = remaining > 1e-3;

            for (City ct : comp.cities) {
                double d = Math.min(ct.demandMW, ct.lineCapacity());
                ct.servedMW = d * ratio; ct.flowMW = ct.servedMW;
            }
            double unserved = rawDemand;
            for (City ct : comp.cities) unserved -= ct.servedMW;
            double unservedFrac = rawDemand > 0 ? Math.max(0, unserved) / rawDemand : 0;
            comp.price = shortage ? Math.min(Balance.SCARCITY_PRICE, Math.max(price, 40) * (1 + 2.5 * unservedFrac)) : price;
            totServed += rawDemand - unserved;
            priceWeighted += comp.price * rawDemand; priceDemand += rawDemand;

            for (Plant p : cand) {
                double out = p.outputMW;
                if (out <= 0) continue;
                p.flowMW = out;
                // hakim durumdaki grubun santrali: fiyat tavani (ortalama uretim maliyeti + %15)
                double rev = tenders.revenue(p, out, p.priceCap > 0 ? Math.min(comp.price, p.priceCap) : comp.price);
                double fuelC = out * fuelCostPerMWh(p);
                double var = out * p.type.varOm;
                double pco2 = p.co2PerMWh(hour);
                double carb = out * pco2 * markets.carbonPrice;
                Company o = p.owner;
                o.cash += rev - fuelC - var - carb;
                o.revenue += rev; o.fuelCost += fuelC; o.omCost += var; o.carbonCost += carb;
                o.mwhGenerated += out; o.co2Tons += out * pco2;
                if (p.type.renewable) { o.renewMwh += out; renew += out; }
                p.monthProfit += rev - fuelC - var - carb;
                p.monthMWh += out; p.monthCost += fuelC + var + carb;
                // Ucuncu taraf TM: teslim edilen MWh basina erisim bedeli TM sahibine.
                Substation tm = p.cp.tm;
                if (tm != null && tm.owner != o && tm.accessFeePerMWh > 0) {
                    double fee = out * (1 - p.cp.lossFraction) * tm.accessFeePerMWh;
                    o.cash -= fee; o.accessCost += fee; p.monthProfit -= fee;
                    if (tm.owner != null) { tm.owner.cash += fee; tm.owner.accessIncome += fee; } // UŞİ TM'si: bedel UŞİ'ye
                    tm.accessIncomeMonth += fee;
                }
                p.lifetimeMWh += out;
                co2 += out * pco2;
                if (o.player) playerMW += out; else rivalMW += out;
            }
            if (unserved > 1e-3) fineOwners(comp, unserved * fineMult);
        }
        for (City c : cities) { c.monthDemandMWh += c.demandMW; c.monthServedMWh += c.servedMW; }
        // Doluluk: hattin dolulugu iki ucunun buyugu; esigi asan her saat sayilir.
        for (PowerLine l : lines) if (l.usable() && l.utilization() > GridBalance.SATURATION_THRESHOLD) l.saturatedHoursThisYear++;
        for (ConnectionPoint cp : points) if (cp.utilization() > GridBalance.SATURATION_THRESHOLD) for (Plant m : cp.members) m.cpFullHoursMonth++;
        // sabit O&M
        for (Plant p : plants) {
            if (!p.operational()) continue;
            p.dailyMWh[p.dayIdx] += p.outputMW; p.dailyCurtailMWh[p.dayIdx] += p.curtailedMW;
            double fx = p.fixedOmPerHour();
            p.owner.cash -= fx; p.owner.omCost += fx; p.monthProfit -= fx; p.monthCost += fx;
        }
        statDemand = totDemand; statServed = totServed;
        monthDemandMWh += totDemand; monthServedMWh += totServed;
        statPrice = priceDemand > 0 ? priceWeighted / priceDemand : statPrice;
        statPlayerMW = playerMW; statRivalMW = rivalMW; statCo2Hour = co2;
        double gen = playerMW + rivalMW;
        statRenewShare = gen > 0 ? renew / gen : 0;
        history.push(statPrice, totDemand, gen, playerMW);
    }

    private void fineOwners(Component comp, double unservedMWh) {
        double cap = 0;
        for (Plant p : comp.plants) if (p.operational()) cap += p.capacityMW();
        if (cap <= 0) return;
        double fine = unservedMWh * Balance.BLACKOUT_FINE;
        for (Plant p : comp.plants) {
            if (!p.operational()) continue;
            double share = p.capacityMW() / cap;
            p.owner.cash -= fine * share; p.owner.fines += fine * share;
        }
    }

    // ------------------------------------------------------------ aylik
    private void monthly() {
        refreshComponentStats();
        macro.monthly();
        operator.monthly();
        for (City c : cities) { c.lastServedRatio = c.monthDemandMWh > 0 ? c.monthServedMWh / c.monthDemandMWh : 1; c.monthDemandMWh = c.monthServedMWh = 0; }
        tenders.monthly();
        if (month() == 0) for (PowerLine l : lines) { l.saturatedHoursLastYear = l.saturatedHoursThisYear; l.saturatedHoursThisYear = 0; }
        for (Supplier s : catalog.allSuppliers()) s.monthly(rng);
        lastServedRatio = monthDemandMWh > 0 ? monthServedMWh / monthDemandMWh : 1;
        monthDemandMWh = 0; monthServedMWh = 0;
        for (Company c : companies) {
            // Faiz: kredilerin aylik tahakkuku (nakit cikisi taksit gununde); eksi nakit cezasi. Kamu disarida.
            finance.monthlyAccrual(c);
            c.qEbitdaAcc += c.monthEbitda(); c.qRenewAcc += c.renewMwh; c.qGenAcc += c.mwhGenerated;

            double net = c.monthNetSoFar();
            c.lastRevenue = c.revenue; c.lastFuel = c.fuelCost; c.lastOm = c.omCost; c.lastCarbon = c.carbonCost;
            c.lastInterest = c.interest; c.lastFines = c.fines; c.lastNet = net; c.lastFees = c.fees; c.fees = 0;
            c.lastDebtService = c.debtServicePaid; c.debtServicePaid = 0; c.lastCapex = c.capexSpent;
            c.lastAccessIncome = c.accessIncome; c.lastAccessCost = c.accessCost; c.accessIncome = c.accessCost = 0;
            c.lastConnectionCharge = c.connectionCharge; c.connectionCharge = 0;
            c.lastDividendIncome = c.dividendIncome; c.dividendIncome = 0; c.lastAffiliateIncome = c.affiliateIncome; c.affiliateIncome = 0;
            c.lastMwhGenerated = c.mwhGenerated; c.lastCo2 = c.co2Tons; c.lastRenewMwh = c.renewMwh;
            c.revenue = c.fuelCost = c.omCost = c.carbonCost = c.interest = c.fines = c.capexSpent = 0;
            c.mwhGenerated = 0; c.co2Tons = 0; c.renewMwh = 0;
            c.netHistory.addLast(net);
            c.eq.qNetAcc += net;
            while (c.netHistory.size() > 12) c.netHistory.pollFirst();
        }
        for (Plant p : plants) {
            p.gen12[p.genIdx] = p.monthMWh; p.genIdx = (p.genIdx + 1) % 12;
            p.lastMonthMWh = p.monthMWh; p.lastMonthCost = p.monthCost; p.monthMWh = 0; p.monthCost = 0;
            p.lastMonthProfit = p.monthProfit; p.yearProfit += p.monthProfit; p.qProfitAcc += p.monthProfit; p.monthProfit = 0;
            if (month() % 3 == 0) { p.qProfitLast = p.qProfitAcc; p.qProfitAcc = 0; }
            if (month() == 0) p.yearProfit = 0; // biten ay Aralik: yeni yil
            p.cpFullMonths = p.cpFullHoursMonth > Balance.HOURS_PER_MONTH * GridBalance.CP_FULL_MONTH_SHARE ? p.cpFullMonths + 1 : 0;
            p.cpFullHoursMonth = 0;
        }
        for (Substation t : substations) { t.lastAccessIncome = t.accessIncomeMonth; t.accessIncomeMonth = 0; }

        for (City c : cities) {
            double g = c.satisfaction > 0.97 ? 0.0035 : c.satisfaction > 0.85 ? 0.0008 : c.satisfaction > 0.6 ? -0.002 : -0.006;
            c.population = Math.max(60_000, c.population * (1 + g + 0.0004 * (rng.nextDouble() - 0.3)));
        }
        updateValuations();
        for (Company c : companies) {
            c.valuationHistory.add(c.valuation); c.cashHistory.add(c.cash);
            if (c.valuationHistory.size() > 240) { c.valuationHistory.remove(0); c.cashHistory.remove(0); }
        }
        history.monthly(statPrice);

        retireLegacy();
        // Ceyrek sonu: kredi notu, taahhutler. Iflas yalnizca finans akisiyla (gecikme -> temerrut -> yapilandirma/iflas).
        if (month() % 3 == 0) for (int ci = 0; ci < companies.size(); ci++) { finance.quarterly(companies.get(ci)); capital.quarterly(companies.get(ci)); }
        competition.monthly();
        mna.monthly();
        if (player.cash < 0 && !player.bankrupt) notify("FİNANS: Nakit eksi! İşletme kredisi limiti dolu; 60 gün içinde düzelmezse temerrüt süreci başlar.", "finance");
        evaluateScenario();
        for (Company c : companies) if ((!c.player || aiPlayer) && !c.neutral && !c.bankrupt) RivalAI.monthly(this, c);
        if ((hour / Balance.HOURS_PER_MONTH) % 12 == 0) notify("Yeni yıl: " + year());
        for (Equipment e : catalog.equipment.values())
            if (e.availableYear == year() && month() == 0) notify("YENİ TEKNOLOJİ: " + e.fullName() + " (" + e.tech + ") piyasada!");
    }

    private void evaluateScenario() {
        if (gameOver || won) return;
        if (scenario.allMet(this) && hour >= Balance.HOURS_PER_MONTH * 2) {
            won = true;
            notify("TEBRİKLER! \"" + scenario.title + "\" senaryosunun tüm hedeflerine ulaştınız. Oyun devam ediyor.");
            return;
        }
        if (scenario.deadlineYear > 0 && year() > scenario.deadlineYear) {
            gameOver = true;
            gameOverReason = "Süre doldu: " + scenario.deadlineYear + " sonuna kadar \"" + scenario.title + "\" hedeflerine ulaşılamadı.";
        }
    }

    /** Sirket degerleri ve hisse fiyatlari (Capital: temel deger, halka acikta piyasa fiyati). */
    public void updateValuations() { capital.monthly(); }

    /** Oyuncunun bu sirkette payi var mi. */
    public boolean playerHolds(Company c) { return c != player && c.eq.held(companies.indexOf(player)) > 0; }

    public int totalPlantsOf(Company c) {
        int n = 0;
        for (Plant p : plants) if (p.owner == c) n++;
        return n;
    }
}
