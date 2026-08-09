package com.nyarutoru.nekoplugin.core.settings;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** A typed, validated setting contract independent of its persistence backend. */
public final class SettingDescriptor<T> {
    private final String key;
    private final String displayName;
    private final SettingType type;
    private final T defaultValue;
    private final ApplySemantics applySemantics;
    private final Function<String, T> parser;
    private final Function<T, String> formatter;
    private final Consumer<T> applyHook;

    private SettingDescriptor(String key, String displayName, SettingType type, T defaultValue,
                              ApplySemantics applySemantics, Function<String, T> parser,
                              Function<T, String> formatter, Consumer<T> applyHook) {
        if (!key.matches("[a-z0-9._-]+")) throw new IllegalArgumentException("Invalid setting key: " + key);
        this.key = key;
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.type = Objects.requireNonNull(type, "type");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.applySemantics = Objects.requireNonNull(applySemantics, "applySemantics");
        this.parser = parser;
        this.formatter = formatter;
        this.applyHook = applyHook;
    }

    public static SettingDescriptor<Boolean> bool(String key, String name, boolean defaultValue,
                                                   ApplySemantics semantics, Consumer<Boolean> hook) {
        return descriptor(key, name, SettingType.BOOLEAN, defaultValue, semantics, text -> {
            if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("on")) return true;
            if (text.equalsIgnoreCase("false") || text.equalsIgnoreCase("off")) return false;
            throw new IllegalArgumentException("Expected true/false or on/off");
        }, Object::toString, hook);
    }

    public static SettingDescriptor<Integer> integer(String key, String name, int defaultValue, int min, int max,
                                                      ApplySemantics semantics, Consumer<Integer> hook) {
        return descriptor(key, name, SettingType.INTEGER, defaultValue, semantics,
                text -> bounded(Integer.parseInt(text.trim()), min, max), Object::toString, hook);
    }

    public static SettingDescriptor<Long> longValue(String key, String name, long defaultValue, long min, long max,
                                                     ApplySemantics semantics, Consumer<Long> hook) {
        return descriptor(key, name, SettingType.LONG, defaultValue, semantics,
                text -> bounded(Long.parseLong(text.trim()), min, max), Object::toString, hook);
    }

    public static SettingDescriptor<Double> doubleValue(String key, String name, double defaultValue,
                                                         double min, double max, ApplySemantics semantics,
                                                         Consumer<Double> hook) {
        if (!Double.isFinite(defaultValue) || defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException("Default outside bounds");
        }
        return descriptor(key, name, SettingType.DOUBLE, defaultValue, semantics, text -> {
            double value = Double.parseDouble(text.trim());
            if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Value outside " + min + ".." + max);
            return value;
        }, Object::toString, hook);
    }

    public static <E extends Enum<E>> SettingDescriptor<E> enumValue(String key, String name, Class<E> enumClass,
                                                                      E defaultValue, ApplySemantics semantics,
                                                                      Consumer<E> hook) {
        return descriptor(key, name, SettingType.ENUM, defaultValue, semantics,
                text -> Enum.valueOf(enumClass, text.trim().toUpperCase(Locale.ROOT)), Enum::name, hook);
    }

    public static SettingDescriptor<String> string(String key, String name, String defaultValue,
                                                    ApplySemantics semantics, Consumer<String> hook) {
        return descriptor(key, name, SettingType.STRING, defaultValue, semantics, text -> text, text -> text, hook);
    }

    public static SettingDescriptor<List<Material>> materials(String key, String name, List<Material> defaultValue,
                                                               ApplySemantics semantics, Consumer<List<Material>> hook) {
        return list(key, name, SettingType.MATERIAL_LIST, defaultValue, semantics, Material::valueOf, hook);
    }

    public static SettingDescriptor<List<EntityType>> entities(String key, String name, List<EntityType> defaultValue,
                                                                ApplySemantics semantics, Consumer<List<EntityType>> hook) {
        return list(key, name, SettingType.ENTITY_LIST, defaultValue, semantics, EntityType::valueOf, hook);
    }

    public static SettingDescriptor<List<Sound>> sounds(String key, String name, List<Sound> defaultValue,
                                                         ApplySemantics semantics, Consumer<List<Sound>> hook) {
        return list(key, name, SettingType.SOUND_LIST, defaultValue, semantics, text -> {
            NamespacedKey soundKey = NamespacedKey.fromString(text.toLowerCase(Locale.ROOT));
            Sound sound = soundKey == null ? null : Registry.SOUNDS.get(soundKey);
            if (sound == null) throw new IllegalArgumentException("Unknown sound: " + text);
            return sound;
        }, hook);
    }

    private static <T> SettingDescriptor<T> descriptor(String key, String name, SettingType type, T defaultValue,
                                                        ApplySemantics semantics, Function<String, T> parser,
                                                        Function<T, String> formatter, Consumer<T> hook) {
        return new SettingDescriptor<>(key, name, type, defaultValue, semantics, parser, formatter,
                hook == null ? ignored -> { } : hook);
    }

    private static <E> SettingDescriptor<List<E>> list(String key, String name, SettingType type, List<E> defaults,
                                                        ApplySemantics semantics, Function<String, E> elementParser,
                                                        Consumer<List<E>> hook) {
        List<E> immutableDefaults = List.copyOf(defaults);
        return descriptor(key, name, type, immutableDefaults, semantics, text -> {
            if (text.isBlank()) return List.of();
            List<E> values = new ArrayList<>();
            for (String token : text.split(",")) values.add(elementParser.apply(token.trim().toUpperCase(Locale.ROOT)));
            return List.copyOf(values);
        }, values -> String.join(",", values.stream().map(Object::toString).toList()), hook);
    }

    private static int bounded(int value, int min, int max) {
        if (value < min || value > max) throw new IllegalArgumentException("Value outside " + min + ".." + max);
        return value;
    }

    private static long bounded(long value, long min, long max) {
        if (value < min || value > max) throw new IllegalArgumentException("Value outside " + min + ".." + max);
        return value;
    }

    public T parse(String text) { return parser.apply(text); }
    public String format(T value) { return formatter.apply(value); }
    public void apply(T value) { applyHook.accept(value); }
    public String key() { return key; }
    public String displayName() { return displayName; }
    public SettingType type() { return type; }
    public T defaultValue() { return defaultValue; }
    public ApplySemantics applySemantics() { return applySemantics; }
}
