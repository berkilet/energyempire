package com.energytycoon.render;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.energytycoon.sim.Equipment;

import java.util.ArrayList;
import java.util.List;

/**
 * Ekipmana gore santral yerlesimleri: Kenney Industrial parcalari + prosedurel taban.
 * Koordinatlar tek bir karede (-0.5..0.5), y=0 zemin. Parca, sinir kutusu merkezi (x,z) noktasina,
 * tabani zemine oturacak sekilde yerlestirilir.
 */
public final class Prefabs {
    private Prefabs() {}

    public enum Emit { NONE, SMOKE, STEAM }

    public static final class Part {
        public final String path; public final float x, z, scale, yaw; public final Emit emit;
        Part(String path, float x, float z, float scale, float yaw, Emit emit) {
            this.path = path; this.x = x; this.z = z; this.scale = scale; this.yaw = yaw; this.emit = emit;
        }
        /** Parcanin karedeki yerel donusumu. */
        public Matrix4 local() {
            BoundingBox bb = KenneyModels.get().bounds(path);
            Vector3 c = bb.getCenter(new Vector3());
            return new Matrix4().translate(x, PAD, z).rotate(Vector3.Y, yaw).scale(scale, scale, scale).translate(-c.x, -bb.min.y, -c.z);
        }
        /** Baca agzi (duman/ikaz lambasi) yerel konumu. */
        public Vector3 top() {
            BoundingBox bb = KenneyModels.get().bounds(path);
            return new Vector3(x, PAD + bb.getHeight() * scale, z);
        }
    }

    public static final float PAD = 0.03f;
    private static final String I = "industrial/";

    private static Part p(String name, float x, float z, float s) { return new Part(I + name, x, z, s, 0, Emit.NONE); }
    private static Part p(String name, float x, float z, float s, float yaw) { return new Part(I + name, x, z, s, yaw, Emit.NONE); }
    private static Part smoke(String name, float x, float z, float s) { return new Part(I + name, x, z, s, 0, Emit.SMOKE); }
    private static Part steam(String name, float x, float z, float s) { return new Part(I + name, x, z, s, 0, Emit.STEAM); }

    /** Bu ekipman Kenney parcalariyla mi cizilir? null: tamamen prosedurel. */
    public static List<Part> of(Equipment e) {
        List<Part> l = new ArrayList<>();
        switch (e.id) {
            // ---- komur
            case "fk-sub" -> {
                l.add(p("building-e", -0.06f, 0.18f, 0.36f, 180));
                l.add(smoke("chimney-medium", 0.33f, -0.20f, 0.33f));
            }
            case "fk-sc" -> {
                l.add(p("building-m", -0.13f, 0.16f, 0.35f, 90));
                l.add(smoke("chimney-medium", 0.32f, -0.30f, 0.40f));
                l.add(smoke("chimney-medium", 0.32f, -0.04f, 0.40f));
            }
            case "th-usc" -> {
                l.add(steam("chimney-large", 0.20f, -0.20f, 0.46f));
                l.add(p("building-l", -0.17f, 0.20f, 0.29f, 180));
                l.add(smoke("chimney-medium", 0.30f, 0.20f, 0.50f));
            }
            // ---- dogalgaz
            case "at-oc" -> {
                l.add(p("building-p", -0.08f, 0.12f, 0.40f));
                l.add(smoke("chimney-small", 0.30f, 0.10f, 0.55f));
                l.add(p("detail-tank", -0.12f, -0.27f, 0.40f));
            }
            case "at-cc" -> {
                l.add(p("building-k", -0.18f, 0.20f, 0.36f, 180));
                l.add(p("detail-tank-large", -0.20f, -0.22f, 0.30f));
                l.add(smoke("chimney-medium", 0.24f, -0.06f, 0.36f));
                l.add(p("building-s", 0.22f, 0.30f, 0.20f, 90));
            }
            case "mp-ha" -> {
                l.add(p("building-r", -0.02f, 0.24f, 0.33f, 180));
                l.add(smoke("chimney-medium", -0.24f, -0.14f, 0.36f));
                l.add(smoke("chimney-medium", 0.04f, -0.14f, 0.36f));
                l.add(p("detail-tank-large", 0.30f, -0.26f, 0.24f));
            }
            // ---- nukleer
            case "atr-600" -> {
                l.add(steam("chimney-large", 0.25f, -0.22f, 0.45f));
                l.add(steam("chimney-large", 0.25f, 0.24f, 0.45f));
                l.add(p("building-g", -0.28f, 0.27f, 0.27f, 180));
            }
            case "ps-smr" -> {
                l.add(p("building-t", 0.14f, -0.06f, 0.30f, 90));
                l.add(steam("chimney-small", 0.36f, 0.30f, 0.40f));
            }
            default -> { return null; }
        }
        return l;
    }
}
