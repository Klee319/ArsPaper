package com.arspaper.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/**
 * PDC読み書きユーティリティ。
 * ItemStackは不変パターンで扱い、新しいItemStackを返す。
 */
public final class PdcHelper {

    private PdcHelper() {}

    public static <T, Z> Optional<Z> getFromItem(ItemStack item, NamespacedKey key, PersistentDataType<T, Z> type) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(key, type)) return Optional.empty();
        return Optional.ofNullable(pdc.get(key, type));
    }

    public static <T, Z> ItemStack setOnItem(ItemStack item, NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        ItemStack result = item.clone();
        result.editMeta(meta ->
            meta.getPersistentDataContainer().set(key, type, value)
        );
        return result;
    }

    public static boolean hasOnItem(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key);
    }

    public static Optional<String> getCustomItemId(ItemStack item) {
        NamespacedKey key = new NamespacedKey("arspaper", "custom_item_id");
        return getFromItem(item, key, PersistentDataType.STRING);
    }
}
