package com.arspaper.block;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * カスタムブロックの登録と検索を管理するレジストリ。
 */
public class CustomBlockRegistry {

    private final Map<String, CustomBlock> blocks = new LinkedHashMap<>();

    public void register(CustomBlock block) {
        blocks.put(block.getItemId(), block);
    }

    public Optional<CustomBlock> get(String blockId) {
        return Optional.ofNullable(blocks.get(blockId));
    }

    public Collection<CustomBlock> getAll() {
        return Collections.unmodifiableCollection(blocks.values());
    }

    public boolean has(String blockId) {
        return blocks.containsKey(blockId);
    }
}
