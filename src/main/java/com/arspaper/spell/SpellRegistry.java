package com.arspaper.spell;

import org.bukkit.NamespacedKey;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 全グリフ（SpellComponent）の登録と検索を管理。
 */
public class SpellRegistry {

    private final Map<String, SpellComponent> components = new LinkedHashMap<>();

    public void register(SpellComponent component) {
        components.put(component.getId().toString(), component);
    }

    public SpellComponent get(NamespacedKey key) {
        return components.get(key.toString());
    }

    public SpellComponent get(String keyString) {
        return components.get(keyString);
    }

    public Collection<SpellComponent> getAll() {
        return Collections.unmodifiableCollection(components.values());
    }

    public List<SpellComponent> getByType(SpellComponent.ComponentType type) {
        return components.values().stream()
            .filter(c -> c.getType() == type)
            .collect(Collectors.toUnmodifiableList());
    }

    public List<SpellForm> getForms() {
        return components.values().stream()
            .filter(c -> c instanceof SpellForm)
            .map(c -> (SpellForm) c)
            .collect(Collectors.toUnmodifiableList());
    }

    public List<SpellEffect> getEffects() {
        return components.values().stream()
            .filter(c -> c instanceof SpellEffect)
            .map(c -> (SpellEffect) c)
            .collect(Collectors.toUnmodifiableList());
    }

    public List<SpellAugment> getAugments() {
        return components.values().stream()
            .filter(c -> c instanceof SpellAugment)
            .map(c -> (SpellAugment) c)
            .collect(Collectors.toUnmodifiableList());
    }
}
