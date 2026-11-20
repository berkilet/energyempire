package com.energytycoon.sim;

/** Hat yasam dongusu. PLANNED/QUEUED: isletmeci basvurusu; BUILDING: insaat; UPGRADING: kademe yukseltme (devre disi). */
public enum LineStatus {
    PLANNED("Planlama"), QUEUED("İnşaat kuyruğu"), BUILDING("İnşaatta"), ACTIVE("Aktif"), UPGRADING("Yükseltmede");
    public final String label;
    LineStatus(String l) { label = l; }
}
