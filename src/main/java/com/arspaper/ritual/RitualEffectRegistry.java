package com.arspaper.ritual;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 儀式エフェクトタイプの登録・検索を管理。
 * effect-type文字列からRitualEffect実装へのマッピング。
 */
public class RitualEffectRegistry {

    private final Map<String, RitualEffect> effects = new LinkedHashMap<>();

    public void register(String effectType, RitualEffect effect) {
        effects.put(effectType, effect);
    }

    public Optional<RitualEffect> get(String effectType) {
        return Optional.ofNullable(effects.get(effectType));
    }
}
