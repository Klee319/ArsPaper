package com.arspaper.mana;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * マナシステムの設定値。
 * config.ymlから読み込む。
 *
 * <p>2026-07-25 (config editor T2): {@code default-max} / {@code default-regen-rate} /
 * {@code regen-interval-ticks} / {@code recovery.*}(on-hit/on-attack/idle 系7項目)は
 * {@code config.yml} から削除し、TrinityForge の「プレイヤー基礎ステータス」
 * ({@code combat/base-stats.yml})へ移設した。読み出しは {@link ManaManager}/
 * {@link ManaRecoveryListener} が {@link ManaBaseStats} 経由で都度取得する
 * (TF側の {@code /trinityforge reload} に即追随させるため、この record には焼き込まない)。
 *
 * <p>2026-07-25 (config editor T3): {@code ars-magic.exp-per-cast} / {@code ars-magic.exp-per-mana} も
 * {@code config.yml} から削除し、{@code stats/skill-exp.yml} の {@code ars-magic:} セクションへ統合した
 * (元々 {@link com.arspaper.integration.TrinityForgeBridge#arsMagicExpPerCast} はTFロード時に
 * skill-exp.yml側を優先していたため、こちらのフィールドは実質TF未ロード時のみのフォールバックだった。
 * 重複解消のため定数化し、フォールバック専用としてこの record からは削除した)。
 */
public record ManaConfig(
    int manaPerGlyphUnlock,
    // マナ最大値%上昇の上限（%）
    int maxPercentCap,
    // 要件⑥ source-auto-consume: マナ不足時にインベントリから消費してマナへ変換するアイテム。
    // itemId(Arsカスタムid または TFカタログid) -> 1個あたりのマナ変換量。
    Map<String, Integer> sourceAutoConsumeItems
) {
    public static ManaConfig fromConfig(FileConfiguration config) {
        return new ManaConfig(
            config.getInt("mana.per-glyph-unlock-bonus", 5),
            // 既存挙動を変えない安全デフォルト（上昇上限100%）。
            clampPercent(config.getInt("mana.max-percent-cap", 100)),
            parseSourceAutoConsumeItems(config)
        );
    }

    /**
     * {@code mana.source-auto-consume.items} (itemId -> 1個あたりのマナ変換量) をパースする。
     * 非正値/id空欄のエントリは警告してスキップする（設定ミスで消費0個変換が成立しないよう防ぐ）。
     */
    private static Map<String, Integer> parseSourceAutoConsumeItems(FileConfiguration config) {
        Map<String, Integer> result = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("mana.source-auto-consume.items");
        if (section == null) {
            return result;
        }
        for (String id : section.getKeys(false)) {
            if (id == null || id.isBlank()) {
                Bukkit.getLogger()
                    .warning("[ArsPaper] mana.source-auto-consume.items に空のidが指定されました。スキップします。");
                continue;
            }
            int manaPerItem = section.getInt(id, 0);
            if (manaPerItem <= 0) {
                Bukkit.getLogger().warning(
                    "[ArsPaper] mana.source-auto-consume.items." + id
                        + " のマナ変換量は正の整数である必要があります。スキップします: " + manaPerItem);
                continue;
            }
            result.put(id, manaPerItem);
        }
        return result;
    }

    /** %系設定を 0..1000 にクランプする。 */
    private static int clampPercent(int value) {
        return Math.max(0, Math.min(1000, value));
    }

    /** double設定を 0以上にクランプする。 */
    private static double clampNonNegative(double value) {
        return Math.max(0.0, value);
    }
}
