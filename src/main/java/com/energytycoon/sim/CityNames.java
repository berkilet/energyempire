package com.energytycoon.sim;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Rastgele sehir adi uretici: on ek + son ek birlesimi (Turkce yer adi kaliplari). Her tohumda farkli,
 * ayni tohumda hep ayni adlar. Son ekler unlu uyumuna bagli olmayan bagimsiz kelimelerdir.
 */
public final class CityNames {
    private CityNames() {}

    static final String[] PREFIX = {
        "Ak", "Kara", "Sarı", "Yeşil", "Demir", "Taş", "Kızıl", "Gök", "Bey", "Kuzey", "Güney", "Yeni", "Eski", "Uzun",
        "Çam", "Elma", "Gül", "Ova", "Dere", "Tepe", "Bağ", "Çınar", "Pınar", "Mercan", "Duman", "Bulut", "Ay", "Gün",
        "Yıldız", "Kum", "Kaya", "Bozkır", "Ilıca", "Kartal", "Doğan", "Ceviz", "Kiraz", "Nar", "Zeytin", "Kavak",
        "Söğüt", "Meşe", "Ardıç", "Lale", "Mavi", "Altın", "Gümüş", "Bakır", "Kireç", "Tuz", "Değirmen", "Köprü",
        "Ulu", "Orta", "Aşağı", "Yukarı", "Büyük", "Küçük", "Serin", "Ilgın", "Akça", "Karaca", "Yalı", "Liman"
    };
    static final String[] SUFFIX = {
        "köy", "ova", "kent", "ırmak", "kale", "su", "yurt", "bahçe", "liman", "pınar", "dere", "tepe", "yayla",
        "ören", "hisar", "şehir", "alan", "çay", "köprü", "bük", "pazar", "yaka", "burun", "göl", "dağ", "bel", "yazı", "taş"
    };
    /** Tek kelimelik, kendi basina sehir adi gibi duran birkac secenek de karisima girer. */
    static final String[] SINGLE = { "Dumanlı", "Elmalı", "Çınarlı", "Karasu", "Bozova", "Akçakoca", "Sarıyurt", "Taşkale" };

    public static List<String> generate(Random r, int n) {
        Set<String> out = new LinkedHashSet<>();
        int guard = 0;
        while (out.size() < n && guard++ < 10_000) {
            String name;
            if (r.nextInt(8) == 0) name = SINGLE[r.nextInt(SINGLE.length)];
            else {
                String p = PREFIX[r.nextInt(PREFIX.length)], s = SUFFIX[r.nextInt(SUFFIX.length)];
                if (p.toLowerCase(java.util.Locale.ROOT).endsWith(s) || s.equals("taş") && p.equals("Taş")) continue;
                name = p + s;
            }
            // ayni on ekle baslayan iki sehir olmasin (haritada karisir)
            String head = name.substring(0, Math.min(4, name.length()));
            if (out.stream().anyMatch(o -> o.startsWith(head))) continue;
            out.add(name);
        }
        return new ArrayList<>(out);
    }
}
