package com.lauua.lauuacheats.core;

import com.lauua.lauuacheats.settings.Setting;
import java.util.*;

public abstract class Module {
    public enum Category { COMBAT, WORLD, MOVEMENT, RENDER, HUD, MISC }
    public enum Tier { A, B, C, D }
    public final String name, description, note;
    public final Category category;
    public final Tier tier;
    public final boolean defaultEnabled;
    public final Setting.KeybindSetting keybind;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;
    protected Module(String name, String description, Category category, int key, boolean defaultEnabled, Tier tier, String note) {
        this.name=name; this.description=description; this.category=category;
        this.defaultEnabled=defaultEnabled; this.tier=tier; this.note=note;
        this.keybind=add(new Setting.KeybindSetting("Keybind", key));
    }
    public final <T extends Setting<?>> T add(T setting) { settings.add(setting); return setting; }
    public final List<Setting<?>> settings() { return Collections.unmodifiableList(settings); }
    public final boolean enabled() { return enabled; }
    public final void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled=enabled;
        if (enabled) onEnable(); else onDisable();
    }
    public final void toggle() { setEnabled(!enabled); }
    protected void onEnable() { }
    protected void onDisable() { }
    public void tick() { }
    public void worldChanged() { }
}
