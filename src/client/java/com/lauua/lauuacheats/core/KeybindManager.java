package com.lauua.lauuacheats.core;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Edge-triggered polling; no toggles while a key is held or a text box is open. */
public final class KeybindManager {
    private final Set<Integer> down = new HashSet<>();
    private final Map<Integer, List<Runnable>> actions = new HashMap<>();
    public void bind(int glfwKey, Runnable action) { actions.computeIfAbsent(glfwKey, k -> new ArrayList<>()).add(action); }
    public boolean held(int key) { return down.contains(key); }
    public void tick(Minecraft client, ModuleManager modules) {
        if (client.getWindow() == null) return;
        Set<Integer> keys = new HashSet<>(actions.keySet());
        modules.all().forEach(m -> { if (m.keybind.get() >= 0) keys.add(m.keybind.get()); });
        for (int key : keys) {
            boolean pressed = GLFW.glfwGetKey(client.getWindow().getWindow(), key) == GLFW.GLFW_PRESS;
            if (pressed && down.add(key) && client.screen == null) {
                actions.getOrDefault(key, List.of()).forEach(Runnable::run);
                modules.all().stream().filter(m -> m.keybind.get() == key).forEach(Module::toggle);
            }
            if (!pressed) down.remove(key);
        }
    }
}
