package com.lauua.lauuacheats.core;

import java.util.*;

public final class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    public void register(Module module) {
        if (find(module.name).isPresent()) throw new IllegalArgumentException("Duplicate module " + module.name);
        modules.add(module);
        if (module.defaultEnabled && module.tier != Module.Tier.D) module.setEnabled(true);
    }
    public List<Module> all() { return Collections.unmodifiableList(modules); }
    public Optional<Module> find(String name) { return modules.stream().filter(m -> m.name.equalsIgnoreCase(name)).findFirst(); }
    public void tick() { for (Module m : List.copyOf(modules)) if (m.enabled()) m.tick(); }
    public void worldChanged() { for (Module m : modules) m.worldChanged(); }
}
