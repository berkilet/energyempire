package com.energytycoon.render;

import com.energytycoon.sim.City;
import com.energytycoon.sim.Plant;
import com.energytycoon.world.WorldMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Yalnizca gorsel yol agi (simulasyonu etkilemez). Haritadan ve sehirlerden deterministik kurulur:
 * <ul>
 *   <li>her sehrin cevresinde cevre yolu (3x3 sehir alaninin bir kare disi), buyuk sehirlerde ikinci halka ve
 *       eksenler boyunca ana caddeler (banliyo evleri bu sokaklara bakar);</li>
 *   <li>sehirler arasi yollar: en kisa yayilan agac + her sehirden en yakin iki komsuya, arazi maliyetli A* ile
 *       (duz gitmeyi, mevcut yolu kullanmayi sever; kademe atlamada rampa, dar sularda kopru);</li>
 *   <li>santrallere kisa servis yollari.</li>
 * </ul>
 * Her kare icin komsu baglanti bitleri (N,E,S,W) tutulur; cizim bu maskeye gore Kenney yol karolarini secer.
 */
public final class RoadNetwork {
    public static final int N = 1, E = 2, S = 4, W = 8;
    static final int[] DX = { 0, 1, 0, -1 }, DY = { -1, 0, 1, 0 };
    static final int[] BIT = { N, E, S, W };

    private final WorldMap map;
    private final int size;
    /** Kare basina baglanti maskesi. */
    public final byte[] links;
    /** Sehir sokagi (kaldirimli) mi, kirsal yol mu. */
    public final boolean[] urban;
    /** Su uzerindeki kopru karesi. */
    public final boolean[] bridge;
    /** Yol yuzeyinin taban yuksekligi (kara: arazi ustu, kopru: tabliye). */
    public final float[] deck;

    public RoadNetwork(WorldMap map) {
        this.map = map; this.size = map.size;
        int n = size * size;
        links = new byte[n]; urban = new boolean[n]; bridge = new boolean[n]; deck = new float[n];
    }

    public boolean road(int i) { return links[i] != 0; }

    // ---------------------------------------------------------------- kurulum
    public void build(int[] occ, List<City> cities, List<Plant> plants) {
        Arrays.fill(links, (byte) 0); Arrays.fill(urban, false); Arrays.fill(bridge, false);
        for (int i = 0; i < deck.length; i++) deck[i] = map.top[i];
        boolean[] cityCore = new boolean[links.length];
        for (City c : cities) for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
            if (map.inBounds(c.x + dx, c.y + dy)) cityCore[map.idx(c.x + dx, c.y + dy)] = true;
        // 1) sehir sokaklari
        for (City c : cities) cityStreets(c, occ, cityCore);
        // 2) sehirler arasi baglantilar
        List<int[]> pairs = cityPairs(cities);
        for (int[] p : pairs) connect(cities.get(p[0]), cities.get(p[1]), occ, cityCore);
        // 3) santral servis yollari
        for (Plant p : plants) serviceRoad(p.x, p.y, occ, cityCore);
        // tek basina kalan (baglantisiz) kareler cizilmez; maske tutarliligi
        for (int i = 0; i < links.length; i++) {
            int x = i % size, y = i / size, m = links[i];
            for (int d = 0; d < 4; d++) if ((m & BIT[d]) != 0) {
                int nx = x + DX[d], ny = y + DY[d];
                // santral/sehir karesine giden uc korunur (karsi taraf yol degil)
                if (!map.inBounds(nx, ny)) { m &= ~BIT[d]; continue; }
                int j = map.idx(nx, ny);
                boolean toNode = occ[j] >= 0;
                if (!toNode && (links[j] & BIT[(d + 2) % 4]) == 0) m &= ~BIT[d];
            }
            links[i] = (byte) m;
        }
        pruneFragments(occ);
    }

    /** Sehre ya da santrale baglanmayan kisa kopuk parcalar (arazi yuzunden bolunmus halka artiklari) silinir. */
    private void pruneFragments(int[] occ) {
        boolean[] seen = new boolean[links.length];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        List<Integer> members = new ArrayList<>();
        for (int s = 0; s < links.length; s++) {
            if (links[s] == 0 || seen[s]) continue;
            members.clear();
            boolean anchored = false;
            seen[s] = true; q.add(s);
            while (!q.isEmpty()) {
                int i = q.poll(), x = i % size, y = i / size;
                members.add(i);
                for (int d = 0; d < 4; d++) {
                    if ((links[i] & BIT[d]) == 0) continue;
                    int j = map.idx(x + DX[d], y + DY[d]);
                    if (occ[j] >= 0) { anchored = true; continue; }
                    if (!seen[j]) { seen[j] = true; q.add(j); }
                }
            }
            if (!anchored && members.size() < 10) for (int i : members) links[i] = 0;
        }
    }

    private boolean landOk(int i, int[] occ, boolean[] cityCore) {
        return !map.water[i] && occ[i] < 0 && !cityCore[i];
    }

    private boolean stepOk(int a, int b) {
        return Math.abs(deck[a] - deck[b]) <= WorldMap.LEVEL_H + 0.01f;
    }

    private void link(int x, int y, int d) {
        int nx = x + DX[d], ny = y + DY[d];
        links[map.idx(x, y)] |= BIT[d];
        if (map.inBounds(nx, ny)) links[map.idx(nx, ny)] |= BIT[(d + 2) % 4];
    }

    /** Sehir: Chebyshev 2'de cevre yolu, buyuklerde 4'te ikinci halka, eksenlerde caddeler. */
    private void cityStreets(City c, int[] occ, boolean[] cityCore) {
        int rad = suburbRadius(c);
        List<Integer> rings = new ArrayList<>();
        rings.add(2);
        if (rad >= 5) rings.add(4);
        for (int r : rings) {
            // halkayi saat yonunde dolas; ardisik iki gecerli kare baglanir
            int[] px = new int[8 * r], py = new int[8 * r];
            int k = 0;
            for (int t = -r; t < r; t++) { px[k] = c.x + t; py[k++] = c.y - r; }
            for (int t = -r; t < r; t++) { px[k] = c.x + r; py[k++] = c.y + t; }
            for (int t = r; t > -r; t--) { px[k] = c.x + t; py[k++] = c.y + r; }
            for (int t = r; t > -r; t--) { px[k] = c.x - r; py[k++] = c.y + t; }
            for (int q = 0; q < k; q++) {
                int q2 = (q + 1) % k;
                tryLink(px[q], py[q], px[q2], py[q2], occ, cityCore, true);
            }
        }
        // eksen caddeleri: sehir kenarindan (1) disari, banliyo sinirinin bir otesine kadar
        for (int d = 0; d < 4; d++) {
            for (int s = 1; s <= rad; s++) {
                int ax = c.x + DX[d] * s, ay = c.y + DY[d] * s, bx = ax + DX[d], by = ay + DY[d];
                if (s == 1) {
                    // sehir alanindan cevre yoluna giris: yalnizca cevre yolu karesine sehre bakan uc
                    if (map.inBounds(bx, by) && landOk(map.idx(bx, by), occ, cityCore)) {
                        links[map.idx(bx, by)] |= BIT[(d + 2) % 4];
                        urban[map.idx(bx, by)] = true;
                    }
                    continue;
                }
                tryLink(ax, ay, bx, by, occ, cityCore, true);
            }
        }
    }

    public static int suburbRadius(City c) { return 3 + c.tier() / 2; }

    /** Bu Chebyshev uzakligina kadar apartman dokusu (orta katli bloklar); disinda mustakil evler. */
    public static int apartmentRadius(City c) { return c.tier() >= 3 ? 2 + (c.tier() - 1) / 2 : 2; }

    private boolean tryLink(int ax, int ay, int bx, int by, int[] occ, boolean[] cityCore, boolean isUrban) {
        if (!map.inBounds(ax, ay) || !map.inBounds(bx, by)) return false;
        int a = map.idx(ax, ay), b = map.idx(bx, by);
        if (!landOk(a, occ, cityCore) || !landOk(b, occ, cityCore) || !stepOk(a, b)) return false;
        int d = bx > ax ? 1 : bx < ax ? 3 : by > ay ? 2 : 0;
        link(ax, ay, d);
        if (isUrban) { urban[a] = true; urban[b] = true; }
        return true;
    }

    /** En kisa yayilan agac (Kruskal) + her sehirden en yakin iki komsuya ek baglanti. */
    private List<int[]> cityPairs(List<City> cities) {
        int n = cities.size();
        List<int[]> all = new ArrayList<>();
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) all.add(new int[] { i, j, dist2(cities.get(i), cities.get(j)) });
        all.sort((p, q) -> Integer.compare(p[2], q[2]));
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        List<int[]> out = new ArrayList<>();
        boolean[][] used = new boolean[n][n];
        for (int[] e : all) {
            int ra = find(parent, e[0]), rb = find(parent, e[1]);
            if (ra != rb) { parent[ra] = rb; out.add(e); used[e[0]][e[1]] = used[e[1]][e[0]] = true; }
        }
        for (int i = 0; i < n; i++) {
            int added = 0;
            for (int[] e : all) {
                if (added >= 2) break;
                if (e[0] != i && e[1] != i) continue;
                added++;
                if (!used[e[0]][e[1]] && e[2] <= 26 * 26) { out.add(e); used[e[0]][e[1]] = used[e[1]][e[0]] = true; }
            }
        }
        out.sort((p, q) -> Integer.compare(p[2], q[2]));
        return out;
    }

    private static int find(int[] p, int i) { while (p[i] != i) i = p[i] = p[p[i]]; return i; }
    private static int dist2(City a, City b) { int dx = a.x - b.x, dy = a.y - b.y; return dx * dx + dy * dy; }

    // ---------------------------------------------------------------- A*
    private static final float STEP = 1f, REUSE = 0.35f, TURN = 0.8f, CLIMB = 2.5f, WATER = 3.5f;
    /** Bir kopruyle gecilebilecek en genis su (kare). */
    private static final int MAX_SPAN = 5;

    /** Iki sehrin cevre yollarini baglar. */
    private void connect(City a, City b, int[] occ, boolean[] cityCore) {
        boolean[] goal = new boolean[links.length];
        List<Integer> starts = new ArrayList<>();
        boolean any = false;
        for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
            if (Math.max(Math.abs(dx), Math.abs(dy)) != 2) continue;
            if (map.inBounds(a.x + dx, a.y + dy)) { int i = map.idx(a.x + dx, a.y + dy); if (links[i] != 0) starts.add(i); }
            if (map.inBounds(b.x + dx, b.y + dy)) { int i = map.idx(b.x + dx, b.y + dy); if (links[i] != 0) { goal[i] = true; any = true; } }
        }
        if (starts.isEmpty() || !any) return;
        int[] path = astar(starts, goal, b.x, b.y, occ, cityCore, 0);
        if (path != null) commit(path);
    }

    /** Santralden en yakin yola en fazla 7 karelik servis yolu (BFS ile hedef, A* ile duzgun rota). */
    private void serviceRoad(int px, int py, int[] occ, boolean[] cityCore) {
        // santral karesine bitisik, yola en yakin kara karesinden baslanir
        int bestStart = -1, bestDir = -1;
        int[] dist = new int[links.length];
        Arrays.fill(dist, -1);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int d = 0; d < 4; d++) {
            int x = px + DX[d], y = py + DY[d];
            if (!map.inBounds(x, y)) continue;
            int i = map.idx(x, y);
            if (!landOk(i, occ, cityCore) || Math.abs(map.top[i] - map.top[map.idx(px, py)]) > 0.01f) continue;
            if (links[i] != 0) { links[i] |= BIT[(d + 2) % 4]; return; }
            dist[i] = 0; q.add(i);
        }
        int target = -1;
        while (!q.isEmpty() && target < 0) {
            int i = q.poll();
            if (dist[i] >= 7) continue;
            int x = i % size, y = i / size;
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d], ny = y + DY[d];
                if (!map.inBounds(nx, ny)) continue;
                int j = map.idx(nx, ny);
                if (dist[j] >= 0 || !landOk(j, occ, cityCore) || !stepOk(i, j)) continue;
                dist[j] = dist[i] + 1;
                if (links[j] != 0) { target = j; break; }
                q.add(j);
            }
        }
        if (target < 0) return;
        // hedeften geri: hangi bitisik kareden baslanacagini A* secsin
        List<Integer> starts = new ArrayList<>();
        for (int d = 0; d < 4; d++) {
            int x = px + DX[d], y = py + DY[d];
            if (map.inBounds(x, y) && dist[map.idx(x, y)] == 0) starts.add(map.idx(x, y));
        }
        boolean[] goal = new boolean[links.length];
        goal[target] = true;
        int[] path = astar(starts, goal, target % size, target / size, occ, cityCore, 12);
        if (path == null || path.length == 0) return;
        commit(path);
        int s = path[0], sx = s % size, sy = s / size;
        for (int d = 0; d < 4; d++) if (sx + DX[d] == px && sy + DY[d] == py) { bestStart = s; bestDir = d; }
        if (bestStart >= 0) links[bestStart] |= BIT[bestDir];
    }

    /**
     * Cok kaynakli A*; durum = (kare, gelis yonu). Kisitlar: su karesinde donulmez (kopru duz), kademe farki en fazla 1,
     * donulen karenin komsulari ondan yuksek olamaz (rampa yalnizca duz karede). maxLen > 0 ise yol uzunlugu siniri.
     */
    private int[] astar(List<Integer> starts, boolean[] goal, int gx, int gy, int[] occ, boolean[] cityCore, int maxLen) {
        int n = links.length;
        float[] g = new float[n * 4];
        int[] from = new int[n * 4];
        short[] len = new short[n * 4];
        Arrays.fill(g, Float.MAX_VALUE);
        Arrays.fill(from, -1);
        Heap open = new Heap(n * 4);
        for (int s : starts) for (int d = 0; d < 4; d++) {
            int st = s * 4 + d;
            g[st] = 0; from[st] = -2;
            open.push(st, heur(s, gx, gy));
        }
        int found = -1;
        while (open.size > 0) {
            int st = open.pop();
            int i = st >> 2, din = st & 3;
            if (goal[i] && from[st] != -2) { found = st; break; }
            if (maxLen > 0 && len[st] >= maxLen) continue;
            int x = i % size, y = i / size;
            int prev = from[st] >= 0 ? from[st] >> 2 : -1;
            for (int d = 0; d < 4; d++) {
                if (d == (din + 2) % 4 && prev >= 0) continue;
                int nx = x + DX[d], ny = y + DY[d];
                if (!map.inBounds(nx, ny)) continue;
                int j = map.idx(nx, ny);
                boolean turn = prev >= 0 && d != din;
                float hi = map.top[i];
                float cost;
                int span = 0;
                if (map.water[j]) {
                    // kopru: suyun karsi kiyisina duz atlama (en fazla MAX_SPAN su karesi); iki kiyi en fazla bir kademe farkli
                    if (turn) continue;
                    int k = j;
                    while (span < MAX_SPAN && map.water[k] && occ[k] < 0) {
                        span++;
                        int kx = x + DX[d] * (span + 1), ky = y + DY[d] * (span + 1);
                        if (!map.inBounds(kx, ky)) { k = -1; break; }
                        k = map.idx(kx, ky);
                    }
                    if (k < 0 || map.water[k] || !landOk(k, occ, cityCore)) continue;
                    if (Math.abs(map.top[k] - hi) > WorldMap.LEVEL_H + 0.01f) continue;
                    j = k;
                    cost = STEP * (span + 1) + WATER * span;
                } else {
                    if (!landOk(j, occ, cityCore)) continue;
                    float hj = map.top[j];
                    if (Math.abs(hi - hj) > WorldMap.LEVEL_H + 0.01f) continue;
                    // rampa yalnizca duz karede: donulen karenin komsulari ondan yuksek olamaz
                    if (turn && (hj > hi + 0.01f || (prev >= 0 && map.top[prev] > hi + 0.01f))) continue;
                    cost = links[j] != 0 ? REUSE : STEP;
                    if (Math.abs(hi - hj) > 0.01f) cost += CLIMB;
                    // kiyiya ve diger yollara cok yakin paralel gitmekten kac (dogal gorunum)
                    if (links[j] == 0) cost += crowd(j) * 0.25f + parallel(j, d) * 1.2f;
                }
                if (turn) cost += TURN;
                int ns = j * 4 + d;
                float ng = g[st] + cost;
                if (ng < g[ns]) {
                    g[ns] = ng; from[ns] = st; len[ns] = (short) (len[st] + 1 + span);
                    open.push(ns, ng + heur(j, gx, gy));
                }
            }
        }
        if (found < 0) return null;
        ArrayList<Integer> rev = new ArrayList<>();
        for (int st = found; st >= 0; st = from[st]) rev.add(st >> 2);
        ArrayList<Integer> seq = new ArrayList<>();
        for (int k = rev.size() - 1; k >= 0; k--) {
            int t = rev.get(k);
            if (!seq.isEmpty()) {
                // kopru atlamasinin ara (su) kareleri
                int p = seq.get(seq.size() - 1), px = p % size, py = p / size, tx = t % size, ty = t / size;
                int sx = Integer.signum(tx - px), sy = Integer.signum(ty - py);
                for (int cx = px + sx, cy = py + sy; cx != tx || cy != ty; cx += sx, cy += sy) seq.add(map.idx(cx, cy));
            }
            seq.add(t);
        }
        int[] out = new int[seq.size()];
        for (int k = 0; k < out.length; k++) out[k] = seq.get(k);
        return out;
    }

    private int crowd(int j) {
        int x = j % size, y = j / size, c = 0;
        for (int d = 0; d < 4; d++) {
            int nx = x + DX[d], ny = y + DY[d];
            if (map.inBounds(nx, ny) && map.water[map.idx(nx, ny)]) c++;
        }
        return c;
    }

    /** Yeni yol karesinin yanlarinda (gidis yonune dik) zaten yol varsa: paralel ikinci yol yerine mevcut yola katilmasi icin ceza. */
    private int parallel(int j, int d) {
        int x = j % size, y = j / size, c = 0;
        for (int side : new int[] { (d + 1) % 4, (d + 3) % 4 }) {
            int nx = x + DX[side], ny = y + DY[side];
            if (map.inBounds(nx, ny) && links[map.idx(nx, ny)] != 0) c++;
        }
        return c;
    }

    private float heur(int i, int gx, int gy) {
        int x = i % size, y = i / size;
        return Math.max(0, Math.abs(x - gx) + Math.abs(y - gy) - 2) * REUSE;
    }

    private void commit(int[] path) {
        for (int k = 0; k + 1 < path.length; k++) {
            int a = path[k], b = path[k + 1];
            int ax = a % size, ay = a / size, bx = b % size, by = b / size;
            int d = bx > ax ? 1 : bx < ax ? 3 : by > ay ? 2 : 0;
            link(ax, ay, d);
        }
        // kopruler: tabliye iki kiyinin yuksek olani
        for (int k = 0; k < path.length; k++) {
            if (!map.water[path[k]]) continue;
            int s = k; while (k < path.length && map.water[path[k]]) k++;
            float dk = Math.max(map.top[path[s - 1]], map.top[path[k]]);
            for (int t = s; t < k; t++) { bridge[path[t]] = true; deck[path[t]] = dk; }
        }
    }

    /** Basit ikili yigin (float anahtar). */
    private static final class Heap {
        int[] item; float[] key; int size;
        Heap(int cap) { item = new int[Math.max(16, cap / 4)]; key = new float[item.length]; }
        void push(int it, float k) {
            if (size == item.length) { item = Arrays.copyOf(item, size * 2); key = Arrays.copyOf(key, size * 2); }
            int i = size++;
            while (i > 0) {
                int p = (i - 1) >> 1;
                if (key[p] <= k) break;
                item[i] = item[p]; key[i] = key[p]; i = p;
            }
            item[i] = it; key[i] = k;
        }
        int pop() {
            int top = item[0];
            int it = item[--size]; float k = key[size];
            int i = 0;
            while (true) {
                int l = 2 * i + 1;
                if (l >= size) break;
                int r = l + 1, c = r < size && key[r] < key[l] ? r : l;
                if (key[c] >= k) break;
                item[i] = item[c]; key[i] = key[c]; i = c;
            }
            if (size > 0) { item[i] = it; key[i] = k; }
            return top;
        }
    }
}
