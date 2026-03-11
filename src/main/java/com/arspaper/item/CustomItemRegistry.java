package com.arspaper.item;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * カスタムアイテムの登録と検索を管理するレジストリ。
 */
public class CustomItemRegistry {

    private final Map<String, BaseCustomItem> items = new LinkedHashMap<>();

    public void register(BaseCustomItem item) {
        items.put(item.getItemId(), item);
    }

    public Optional<BaseCustomItem> get(String itemId) {
        return Optional.ofNullable(items.get(itemId));
    }

    public Collection<BaseCustomItem> getAll() {
        return Collections.unmodifiableCollection(items.values());
    }

    public boolean has(String itemId) {
        return items.containsKey(itemId);
    }
}
