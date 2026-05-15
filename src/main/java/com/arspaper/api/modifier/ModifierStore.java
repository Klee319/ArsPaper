package com.arspaper.api.modifier;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤー×NamespacedKey×ModifierType → value のModifier集約ストア。
 * 永続化はArsPlayerDataStoreが担当する。
 */
public final class ModifierStore {

    private final Map<UUID, EnumMapPerType> data = new ConcurrentHashMap<>();

    private static final class EnumMapPerType {
        private final Map<ModifierType, Map<NamespacedKey, Double>> byType =
            new java.util.EnumMap<>(ModifierType.class);
    }

    public synchronized Double put(UUID uuid, NamespacedKey key, ModifierType type, double value) {
        EnumMapPerType ep = data.computeIfAbsent(uuid, k -> new EnumMapPerType());
        Map<NamespacedKey, Double> inner = ep.byType.computeIfAbsent(type, t -> new LinkedHashMap<>());
        return inner.put(key, value);
    }

    public synchronized Double remove(UUID uuid, NamespacedKey key, ModifierType type) {
        EnumMapPerType ep = data.get(uuid);
        if (ep == null) return null;
        Map<NamespacedKey, Double> inner = ep.byType.get(type);
        if (inner == null) return null;
        Double removed = inner.remove(key);
        if (inner.isEmpty()) ep.byType.remove(type);
        return removed;
    }

    public synchronized Double get(UUID uuid, NamespacedKey key, ModifierType type) {
        EnumMapPerType ep = data.get(uuid);
        if (ep == null) return null;
        Map<NamespacedKey, Double> inner = ep.byType.get(type);
        if (inner == null) return null;
        return inner.get(key);
    }

    public synchronized Map<NamespacedKey, Double> list(UUID uuid, ModifierType type) {
        EnumMapPerType ep = data.get(uuid);
        if (ep == null) return Collections.emptyMap();
        Map<NamespacedKey, Double> inner = ep.byType.get(type);
        if (inner == null) return Collections.emptyMap();
        return new LinkedHashMap<>(inner);
    }

    public synchronized double sum(UUID uuid, ModifierType type) {
        EnumMapPerType ep = data.get(uuid);
        if (ep == null) return 0.0;
        Map<NamespacedKey, Double> inner = ep.byType.get(type);
        if (inner == null) return 0.0;
        double total = 0.0;
        for (Double v : inner.values()) total += v;
        return total;
    }

    public synchronized void clear(UUID uuid) {
        data.remove(uuid);
    }

    public synchronized java.util.List<ArsModifier> snapshot(UUID uuid) {
        EnumMapPerType ep = data.get(uuid);
        if (ep == null) return java.util.List.of();
        java.util.List<ArsModifier> out = new java.util.ArrayList<>();
        for (var typeEntry : ep.byType.entrySet()) {
            for (var keyEntry : typeEntry.getValue().entrySet()) {
                out.add(new ArsModifier(keyEntry.getKey(), typeEntry.getKey(), keyEntry.getValue()));
            }
        }
        return out;
    }

    public synchronized void restore(UUID uuid, java.util.List<ArsModifier> mods) {
        clear(uuid);
        for (ArsModifier m : mods) {
            put(uuid, m.key(), m.type(), m.value());
        }
    }

    public double sumFor(Player player, ModifierType type) {
        return sum(player.getUniqueId(), type);
    }
}
