package com.energytycoon;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public final class Main {
    public static void main(String[] args) {
        try {
            if (restartOnMacFirstThread()) return;
            if (!AppPaths.writable()) {
                fail("Oyun klasörüne yazılamıyor:\n" + AppPaths.appDir()
                    + "\n\nKlasörü yazma izniniz olan bir yere taşıyın (ör. Masaüstü veya Belgeler; Program Files değil).", null);
                return;
            }
            AppPaths.redirectSystemWrites();
            try {
                launch(4);
            } catch (Throwable first) {
                // Bazi ekran kartlari 4x kenar yumusatmali pencere acamaz: bir kez yumusatmasiz dene.
                String m = String.valueOf(first.getMessage()).toLowerCase();
                if (m.contains("window") || m.contains("opengl") || m.contains("pixel format")) launch(0);
                else throw first;
            }
        } catch (Throwable t) {
            fail("Oyun beklenmeyen bir hatayla kapandı.", t);
        }
    }

    private static void launch(int msaa) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        // Ayarlar varsayilan olarak ~/.prefs altina yazilir; oyunun kendi klasorune yonlendir.
        cfg.setPreferencesConfig(AppPaths.prefsDir().getAbsolutePath() + "/", com.badlogic.gdx.Files.FileType.Absolute);
        // Ses kullanilmiyor: OpenAL yerel kutuphanesi hic yuklenmez/cikarilmaz.
        cfg.disableAudio(true);
        cfg.setTitle("Enerji İmparatorluğu");
        cfg.setWindowedMode(1360, 800);
        cfg.setWindowSizeLimits(960, 600, -1, -1);
        cfg.useVsync(true);
        cfg.setForegroundFPS(120);
        cfg.setBackBufferConfig(8, 8, 8, 8, 24, 0, msaa);
        new Lwjgl3Application(new EnergyGame(), cfg);
    }

    /** Hatayi veri/hata.log dosyasina yazar ve kullaniciya Turkce bir pencere gosterir. */
    static void fail(String message, Throwable t) {
        String logPath = null;
        if (t != null) {
            try {
                java.io.File log = new java.io.File(AppPaths.dataDir(), "hata.log");
                try (java.io.PrintWriter w = new java.io.PrintWriter(new java.io.FileWriter(log, java.nio.charset.StandardCharsets.UTF_8, true))) {
                    w.println("==== " + java.time.LocalDateTime.now() + " | Java " + System.getProperty("java.version")
                        + " | " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
                    t.printStackTrace(w);
                }
                logPath = log.getAbsolutePath();
            } catch (Throwable ignored) { }
            t.printStackTrace();
        }
        String full = message + (t != null ? "\n\nHata: " + t : "") + (logPath != null ? "\n\nAyrıntılar: " + logPath : "");
        System.err.println(full);
        try {
            if (!java.awt.GraphicsEnvironment.isHeadless())
                javax.swing.JOptionPane.showMessageDialog(null, full, "Enerji İmparatorluğu", javax.swing.JOptionPane.ERROR_MESSAGE);
        } catch (Throwable ignored) { }
    }

    /** macOS GLFW ana thread'de olmali: gerekirse -XstartOnFirstThread ile yeniden baslat. */
    private static boolean restartOnMacFirstThread() throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) return false;
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        if ("1".equals(System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + pid))) return false;
        String jar = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + "/bin/java");
        cmd.add("-XstartOnFirstThread");
        cmd.add("-XX:-UsePerfData");
        cmd.add("-cp"); cmd.add(jar);
        cmd.add(Main.class.getName());
        new ProcessBuilder(cmd).inheritIO().start().waitFor();
        return true;
    }
}
