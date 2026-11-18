package com.energytycoon.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** Gercek izometrik acili ortografik kamera (yaw 45 + 90k, pitch 35.264). */
public final class IsoCamera {
    public final OrthographicCamera cam = new OrthographicCamera();
    public float yaw = 45f, yawTarget = 45f;
    public final float pitch = 35.264f;
    public float ppu = 34f, ppuTarget = 34f;
    public final Vector3 target = new Vector3();
    private final float mapSize;

    public IsoCamera(float mapSize) {
        this.mapSize = mapSize;
        target.set(mapSize / 2f, 0f, mapSize / 2f);
        cam.near = 1f; cam.far = 600f;
    }

    public void rotate(int dir) { yawTarget += 90f * dir; }
    public void zoom(float factor) { ppuTarget = MathUtils.clamp(ppuTarget * factor, 12f, 120f); }

    private float rad(float d) { return d * MathUtils.degreesToRadians; }
    private float fx() { return -MathUtils.sin(rad(yaw)); }
    private float fz() { return -MathUtils.cos(rad(yaw)); }
    private float rx() { return MathUtils.cos(rad(yaw)); }
    private float rz() { return -MathUtils.sin(rad(yaw)); }

    /** Klavye pani: sag/ileri birimleri dunya biriminde. */
    public void pan(float right, float forward) {
        target.x += rx() * right + fx() * forward;
        target.z += rz() * right + fz() * forward;
        clampTarget();
    }

    /** Fare surukleme (piksel). */
    public void drag(float dxPx, float dyPx) {
        float s = MathUtils.sin(rad(pitch));
        pan(-dxPx / ppu, dyPx / (ppu * s));
    }

    private void clampTarget() {
        target.x = MathUtils.clamp(target.x, 0, mapSize);
        target.z = MathUtils.clamp(target.z, 0, mapSize);
    }

    public void update(float delta, int w, int h) {
        yaw += (yawTarget - yaw) * Math.min(1f, delta * 9f);
        ppu += (ppuTarget - ppu) * Math.min(1f, delta * 12f);
        float p = rad(pitch), y = rad(yaw);
        float d = 300f;
        cam.position.set(target.x + MathUtils.cos(p) * MathUtils.sin(y) * d,
                         target.y + MathUtils.sin(p) * d,
                         target.z + MathUtils.cos(p) * MathUtils.cos(y) * d);
        cam.up.set(0, 1, 0);
        cam.lookAt(target);
        cam.viewportWidth = w / ppu;
        cam.viewportHeight = h / ppu;
        cam.update();
    }
}
