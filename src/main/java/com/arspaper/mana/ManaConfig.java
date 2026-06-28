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
    int manaPerGlyphUnlock,
    // マナ最大値%上昇の上限（%）
    int maxPercentCap,
    // 被弾時マナ回復（最大値に対する%＋固定値）
    int onHitPercent,
    int onHitFlat,
    // 攻撃時マナ回復（最大値に対する%＋固定値）
    int onAttackPercent,
    int onAttackFlat,
    // 非発動（idle）判定秒数と、idle時の回復ボーナス（最大値に対する%＋固定値）
    int idleSeconds,
    int idleBonusPercent,
    int idleBonusFlat
) {
    public static ManaConfig fromConfig(FileConfiguration config) {
        return new ManaConfig(
            config.getInt("mana.default-max", 100),
            config.getInt("mana.default-regen-rate", 2),
            config.getInt("mana.regen-interval-ticks", 20),
            config.getInt("mana.per-glyph-unlock-bonus", 5),
            // 既存挙動を変えない安全デフォルト（上昇上限100%、回復系は0=無効）。
            // %系は 0..1000、flat系は 0以上、idle-seconds は 0以上にクランプする。
            clampPercent(config.getInt("mana.max-percent-cap", 100)),
            clampPercent(config.getInt("mana.recovery.on-hit-percent", 0)),
            clampFlat(config.getInt("mana.recovery.on-hit-flat", 0)),
            clampPercent(config.getInt("mana.recovery.on-attack-percent", 0)),
            clampFlat(config.getInt("mana.recovery.on-attack-flat", 0)),
            clampFlat(config.getInt("mana.recovery.idle-seconds", 5)),
            clampPercent(config.getInt("mana.recovery.idle-bonus-percent", 0)),
            clampFlat(config.getInt("mana.recovery.idle-bonus-flat", 0))
        );
    }

    /** %系設定を 0..1000 にクランプする。 */
    private static int clampPercent(int value) {
        return Math.max(0, Math.min(1000, value));
    }

    /** flat系・秒数設定を 0以上にクランプする。 */
    private static int clampFlat(int value) {
        return Math.max(0, value);
    }
}
