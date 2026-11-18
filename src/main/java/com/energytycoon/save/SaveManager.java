package com.energytycoon.save;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.energytycoon.sim.GameState;
import com.energytycoon.sim.SaveData;

public final class SaveManager {
    private SaveManager() {}

    private static FileHandle file(String slot) {
        return Gdx.files.absolute(new java.io.File(com.energytycoon.AppPaths.saveDir(), slot + ".json").getAbsolutePath());
    }

    public static boolean exists(String slot) { return file(slot).exists(); }

    public static boolean save(GameState s, String slot) {
        try {
            Json json = new Json(JsonWriter.OutputType.json);
            json.setIgnoreUnknownFields(true);
            file(slot).writeString(json.toJson(SaveData.from(s)), false, "UTF-8");
            return true;
        } catch (Exception e) {
            Gdx.app.error("save", "kayit hatasi", e);
            return false;
        }
    }

    public static GameState load(String slot) {
        try {
            Json json = new Json();
            json.setIgnoreUnknownFields(true);
            SaveData d = json.fromJson(SaveData.class, file(slot).readString("UTF-8"));
            return d.restore();
        } catch (Exception e) {
            Gdx.app.error("save", "yukleme hatasi", e);
            return null;
        }
    }
}
