package com.energytycoon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Turkce yerel ayarda "WIND".toLowerCase() = "wınd" olur ve kaynak adlari (ikon, logo) bulunamaz.
 * Kaynak kodda yerel ayara bagli harf donusumu kalmamali: toLowerCase/toUpperCase her zaman Locale ile.
 */
class LocaleSafetyTest {
    @Test
    void noLocaleSensitiveCaseConversion() throws IOException {
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Paths.get("src/main/java"))) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                List<String> lines = Files.readAllLines(p);
                for (int i = 0; i < lines.size(); i++)
                    if (lines.get(i).contains(".toLowerCase()") || lines.get(i).contains(".toUpperCase()")) bad.add(p + ":" + (i + 1));
            }
        }
        assertTrue(bad.isEmpty(), "Locale'siz harf donusumu: " + bad);
    }

    @Test
    void testsRunUnderTurkishLocale() {
        assertEquals("wınd", "WIND".toLowerCase(), "testler Turkce yerel ayarla calisir (build.gradle)");
        assertEquals("wind", "WIND".toLowerCase(java.util.Locale.ROOT));
    }
}
