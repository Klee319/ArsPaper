package com.arspaper.item;

import com.arspaper.ArsPaper;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.concurrent.ThreadLocalRandom;

/**
 * selection.yml を読み込み、厳選の品質(0-5)抽選分布を供給する設定駆動ユーティリティ。
 *
 * <p>ステ値の振れ幅は TrinityForge 側のテーブルが rollSeed + 品質から live 導出する。
 * ここでは「どの品質を引くか」の分布のみを管理し、ステ値そのものは扱わない。
 *
 * <p>分布が未設定（空セクション）の場合は抽選を行わず、呼び出し側のフォールバック品質を返す。
 * 数値はバランス調整フェーズで selection.yml に投入する想定（初期状態は全コメントアウト）。
 */
public final class SelectionConfig {

    /** 品質の下限・上限（TrinityForge ItemData の仕様に一致させる） */
    public static final int MIN_QUALITY = 0;
    public static final int MAX_QUALITY = 5;

    private static volatile SelectionConfig instance;

    /** quality(0..5) ごとの抽選ウェイト。合計0なら分布なし。 */
    private final int[] weights;
    private final int totalWeight;

    private SelectionConfig(int[] weights) {
        this.weights = weights;
        int sum = 0;
        for (int w : weights) {
            sum += Math.max(0, w);
        }
        this.totalWeight = sum;
    }

    /** 遅延ロードされたシングルトンを返す。初回アクセス時に selection.yml を初期化・読込みする。 */
    public static SelectionConfig get() {
        SelectionConfig local = instance;
        if (local != null) {
            return local;
        }
        synchronized (SelectionConfig.class) {
            if (instance == null) {
                instance = load();
            }
            return instance;
        }
    }

    /** 設定の再読込み（/ars reload 等から呼べるよう公開）。 */
    public static void reload() {
        synchronized (SelectionConfig.class) {
            instance = load();
        }
    }

    private static SelectionConfig load() {
        int[] weights = new int[MAX_QUALITY + 1];
        ArsPaper plugin = ArsPaper.getInstance();
        if (plugin == null) {
            // プラグイン未初期化（テスト等）: 分布なしで返す。
            return new SelectionConfig(weights);
        }

        File file = new File(plugin.getDataFolder(), "selection.yml");
        if (!file.exists()) {
            plugin.saveResource("selection.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection dist = config.getConfigurationSection("quality-distribution");
        if (dist == null) {
            return new SelectionConfig(weights);
        }
        for (int q = MIN_QUALITY; q <= MAX_QUALITY; q++) {
            weights[q] = Math.max(0, dist.getInt(String.valueOf(q), 0));
        }
        return new SelectionConfig(weights);
    }

    /**
     * 分布から品質を抽選する。分布が未設定なら {@code fallback} をクランプして返す。
     *
     * @param fallback 分布なし時に使う既定品質
     * @return 0-5 の品質
     */
    public int rollQuality(int fallback) {
        if (totalWeight <= 0) {
            return clamp(fallback);
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cursor = 0;
        for (int q = MIN_QUALITY; q <= MAX_QUALITY; q++) {
            cursor += weights[q];
            if (roll < cursor) {
                return q;
            }
        }
        return clamp(fallback);
    }

    /** 品質を 0-5 にクランプする。 */
    public static int clamp(int quality) {
        return Math.max(MIN_QUALITY, Math.min(MAX_QUALITY, quality));
    }
}
