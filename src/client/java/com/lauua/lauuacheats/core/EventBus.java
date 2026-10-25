package com.lauua.lauuacheats.core;

import java.util.*;
import java.util.function.Consumer;

/** Synchronous client-thread bus; listeners can unregister via the returned handle. */
public final class EventBus {
    private final Map<Class<?>, List<Consumer<?>>> listeners = new HashMap<>();
    public <T> AutoCloseable listen(Class<T> type, Consumer<T> listener) {
        listeners.computeIfAbsent(type, k -> new ArrayList<>()).add(listener);
        return () -> listeners.getOrDefault(type, List.of()).remove(listener);
    }
    @SuppressWarnings("unchecked")
    public <T> void post(T event) {
        for (Consumer<?> listener : List.copyOf(listeners.getOrDefault(event.getClass(), List.of())))
            ((Consumer<T>) listener).accept(event);
    }
    public record Tick() { }
    public record WorldChanged() { }
}
