package com.arspaper.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * アイテム参照 → 数値（ソースポイント等）のテーブル。
 * YAML の materials マップで {@code COAL: 5} / {@code "custom:source_gem": 10} を受け付ける。
 */
public final class ItemCostTable {

    private final Map<ItemCostRef, Integer> values;

    private ItemCostTable(Map<ItemCostRef, Integer> values) {
        this.values = Map.copyOf(values);
    }

    public static ItemCostTable empty() {
        return new ItemCostTable(Map.of());
    }

    public static ItemCostTable fromMaterials(Map<Material, Integer> materials) {
        Map<ItemCostRef, Integer> map = new LinkedHashMap<>();
        if (materials != null) {
            for (Map.Entry<Material, Integer> e : materials.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && e.getValue() > 0) {
                    map.put(ItemCostRef.ofMaterial(e.getKey()), e.getValue());
                }
            }
        }
        return new ItemCostTable(map);
    }

    public static ItemCostTable parseSection(ConfigurationSection section, Logger logger, String path) {
        if (section == null) {
            return empty();
        }
        Map<ItemCostRef, Integer> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            int value = section.getInt(key, 0);
            if (value <= 0) {
                continue;
            }
            try {
                map.put(ItemCostRef.parse(key), value);
            } catch (IllegalArgumentException ex) {
                if (logger != null) {
                    logger.warning(path + ": invalid item id '" + key + "': " + ex.getMessage());
                }
            }
        }
        return new ItemCostTable(map);
    }

    public int valueOf(ItemStack stack) {
        return ItemCostRef.fromStack(stack)
                .map(ref -> values.getOrDefault(ref, 0))
                .orElse(0);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public int size() {
        return values.size();
    }

    public Map<ItemCostRef, Integer> asMap() {
        return Collections.unmodifiableMap(values);
    }
}
