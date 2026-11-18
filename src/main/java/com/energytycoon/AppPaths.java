package com.energytycoon;

import java.io.File;

/**
 * Tasinabilir mod: oyun yalnizca kendi klasorundeki "veri" alt klasorune yazar.
 * Kayitlar, ayarlar ve calisma aninda cikarilan yerel kutuphaneler (dll/so/dylib) buraya gider.
 */
public final class AppPaths {
    private AppPaths() {}

    private static File root;

    /** JAR'in bulundugu klasor; IDE/gradle'dan calisirken calisma dizini. */
    public static synchronized File appDir() {
        if (root != null) return root;
        File dir = null;
        try {
            File src = new File(AppPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (src.isFile()) dir = src.getParentFile();
        } catch (Exception ignored) { }
        if (dir == null) dir = new File(System.getProperty("user.dir"));
        root = dir.getAbsoluteFile();
        return root;
    }

    public static File dataDir() { return sub("veri"); }

    /** Oyun klasorune yazilabiliyor mu? (Program Files gibi korumali yerlerde hayir.) */
    public static boolean writable() {
        try {
            File probe = new File(dataDir(), ".yazma-testi");
            if (!probe.exists() && !probe.createNewFile()) return false;
            return probe.delete();
        } catch (Exception e) {
            return false;
        }
    }
    public static File saveDir() { return sub("veri/kayitlar"); }
    public static File prefsDir() { return sub("veri/ayarlar"); }
    public static File nativesDir() { return sub("veri/kutuphaneler"); }
    public static File tempDir() { return sub("veri/gecici"); }

    private static File sub(String rel) {
        File f = new File(appDir(), rel);
        f.mkdirs();
        return f;
    }

    /**
     * Herhangi bir kutuphane yuklenmeden ONCE cagrilmali.
     * LibGDX natives'i java.io.tmpdir altina, LWJGL ise SharedLibraryExtractPath'e cikarir.
     */
    public static void redirectSystemWrites() {
        System.setProperty("java.io.tmpdir", tempDir().getAbsolutePath());
        System.setProperty("org.lwjgl.system.SharedLibraryExtractPath", nativesDir().getAbsolutePath());
        System.setProperty("org.lwjgl.system.SharedLibraryExtractDirectory", "lwjgl");
    }
}
