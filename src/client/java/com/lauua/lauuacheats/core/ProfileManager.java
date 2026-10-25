package com.lauua.lauuacheats.core;

import com.google.gson.*;
import com.lauua.lauuacheats.settings.Setting;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** One atomic JSON file per profile, with size and path validation. */
public final class ProfileManager {
    private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("lauuacheats/profiles");
    private final ModuleManager modules;
    public ProfileManager(ModuleManager modules) { this.modules=modules; }
    private Path path(String name) {
        if (!name.matches("[a-zA-Z0-9_-]{1,40}")) throw new IllegalArgumentException("Invalid profile name");
        return dir.resolve(name + ".json");
    }
    public void save(String name) throws java.io.IOException {
        Files.createDirectories(dir);
        JsonObject root = new JsonObject();
        for (Module m : modules.all()) {
            JsonObject data = new JsonObject(); data.addProperty("enabled", m.enabled());
            for (Setting<?> s : m.settings()) data.add(s.name, s.toJson());
            root.add(m.name, data);
        }
        Path temp = Files.createTempFile(dir, "profile-", ".tmp");
        try {
            Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
            try { Files.move(temp, path(name), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, path(name), StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    public void load(String name) throws java.io.IOException {
        Path file = path(name);
        if (!Files.exists(file)) return;
        if (Files.size(file) > 1_000_000) throw new java.io.IOException("Profile is too large");
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        for (Module m : modules.all()) {
            if (!root.has(m.name) || !root.get(m.name).isJsonObject()) continue;
            JsonObject data = root.getAsJsonObject(m.name);
            for (Setting<?> s : m.settings()) if (data.has(s.name)) {
                try { s.fromJson(data.get(s.name)); } catch (RuntimeException ignored) { /* Ignore one malformed setting. */ }
            }
            if (data.has("enabled")) m.setEnabled(data.get("enabled").getAsBoolean());
        }
    }
}
