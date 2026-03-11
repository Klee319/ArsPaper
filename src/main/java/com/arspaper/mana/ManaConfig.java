package com.arspaper.mana;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * マナシステムの設定値。
 * config.ymlから読み込む。
 */
public record ManaConfig(
    int defaultMaxMana,
    int defaultRegenRate,
    int regenIntervalTicks,
    int manaPerGlyphUnlock
) {
    public static ManaConfig fromConfig(FileConfiguration config) {
        return new ManaConfig(
            config.getInt("mana.default-max", 100),
            config.getInt("mana.default-regen-rate", 2),
            config.getInt("mana.regen-interval-ticks", 20),
            config.getInt("mana.per-glyph-unlock-bonus", 5)
        );
    }
}
