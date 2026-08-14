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
 */
public record ManaConfig(
    int manaPerGlyphUnlock,
    // マナ最大値%上昇の上限（%）
    int maxPercentCap,
    // 要件⑥ source-auto-consume: マナ不足時にインベントリから消費してマナへ変換するアイテム。
    // itemId(Arsカスタムid または TFカタログid) -> 1個あたりのマナ変換量。
    Map<String, Integer> sourceAutoConsumeItems,
    // 2026-08-14 追加: 自動消費のクールタイム(秒)。0以下でCT無し(従来挙動)。
    // スキルツリー ars_smithing.yml A-2「ソースベリー活用」の説明文は当初から「100マナ/10CT」と
    // 書いてあったが、CT判定は一度も実装されておらずマナ不足のたびに無制限に変換できていた。
    int sourceAutoConsumeCooldownSeconds
) {
    /** CT未設定時の既定値(秒)。ノード説明「100マナ/10CT」の 10 をそのまま秒として採る。 */
    public static final int DEFAULT_SOURCE_AUTO_CONSUME_COOLDOWN_SECONDS = 10;

    public static ManaConfig fromConfig(FileConfiguration config) {
        return new ManaConfig(
            config.getInt("mana.per-glyph-unlock-bonus", 5),
            // 既存挙動を変えない安全デフォルト（上昇上限100%）。
            clampPercent(config.getInt("mana.max-percent-cap", 100)),
            parseSourceAutoConsumeItems(config),
            // 負値は0(CT無し)として扱う。上限は設けない(運用で長いCTを置きたい場合がある)。
            Math.max(0, config.getInt("mana.source-auto-consume.cooldown-seconds",
                DEFAULT_SOURCE_AUTO_CONSUME_COOLDOWN_SECONDS))
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
}
