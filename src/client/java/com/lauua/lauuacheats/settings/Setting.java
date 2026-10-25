package com.lauua.lauuacheats.settings;

import com.google.gson.*;
import java.util.*;

/** Typed JSON settings. All values are copied at the serialization boundary. */
public abstract class Setting<T> {
    public final String name;
    public final T defaultValue;
    protected T value;
    protected Setting(String name, T initial) { this.name = name; defaultValue = initial; value = initial; }
    public T get() { return value; }
    public void set(T value) { this.value = Objects.requireNonNull(value); }
    public void reset() { set(defaultValue); }
    public abstract JsonElement toJson();
    public abstract void fromJson(JsonElement json);

    public static final class BooleanSetting extends Setting<Boolean> {
        public BooleanSetting(String name, boolean initial) { super(name, initial); }
        public JsonElement toJson() { return new JsonPrimitive(value); }
        public void fromJson(JsonElement j) { set(j.getAsBoolean()); }
    }
    public static final class IntSlider extends Setting<Integer> {
        public final int min, max;
        public IntSlider(String name, int initial, int min, int max) { super(name, initial); this.min=min; this.max=max; set(initial); }
        @Override public void set(Integer v) { super.set(Math.max(min, Math.min(max, v))); }
        public JsonElement toJson() { return new JsonPrimitive(value); }
        public void fromJson(JsonElement j) { set(j.getAsInt()); }
    }
    public static final class DoubleSlider extends Setting<Double> {
        public final double min, max;
        public DoubleSlider(String name, double initial, double min, double max) { super(name, initial); this.min=min; this.max=max; set(initial); }
        @Override public void set(Double v) { super.set(Double.isFinite(v) ? Math.max(min, Math.min(max, v)) : defaultValue); }
        public JsonElement toJson() { return new JsonPrimitive(value); }
        public void fromJson(JsonElement j) { set(j.getAsDouble()); }
    }
    public static final class EnumSetting<E extends Enum<E>> extends Setting<E> {
        private final Class<E> type;
        public EnumSetting(String name, E initial, Class<E> type) { super(name, initial); this.type=type; }
        public JsonElement toJson() { return new JsonPrimitive(value.name()); }
        public void fromJson(JsonElement j) { set(Enum.valueOf(type, j.getAsString())); }
    }
    public static class StringSetting extends Setting<String> {
        public StringSetting(String name, String initial) { super(name, initial); }
        public JsonElement toJson() { return new JsonPrimitive(value); }
        public void fromJson(JsonElement j) { set(j.getAsString()); }
    }
    /** Packed 0xAARRGGBB. */
    public static final class ColorSetting extends Setting<Integer> {
        public ColorSetting(String name, int initial) { super(name, initial); }
        public JsonElement toJson() { return new JsonPrimitive(value); }
        public void fromJson(JsonElement j) { set(j.getAsInt()); }
    }
    /** GLFW key code, -1 means unbound. */
    public static final class KeybindSetting extends Setting<Integer> {
        public KeybindSetting(String name, int initial) { super(name, initial); }
        public JsonElement toJson() { return new JsonPrimitive(value); }
        public void fromJson(JsonElement j) { set(j.getAsInt()); }
    }
    private static abstract class IdList extends Setting<List<String>> {
        IdList(String name, List<String> initial) { super(name, List.copyOf(initial)); }
        @Override public void set(List<String> v) {
            if (v.size() > 256 || v.stream().anyMatch(s -> !s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")))
                throw new IllegalArgumentException("Expected at most 256 namespaced identifiers");
            super.set(List.copyOf(v));
        }
        public JsonElement toJson() { JsonArray array = new JsonArray(); value.forEach(array::add); return array; }
        public void fromJson(JsonElement j) { List<String> ids = new ArrayList<>(); j.getAsJsonArray().forEach(e -> ids.add(e.getAsString())); set(ids); }
    }
    public static final class BlockList extends IdList { public BlockList(String n, List<String> v) { super(n,v); } }
    public static final class ItemList extends IdList { public ItemList(String n, List<String> v) { super(n,v); } }
}
