package com.arspaper.source;

import org.bukkit.configuration.ConfigurationSection;

import java.util.function.Consumer;

/**
 * ソース転送まわりの調整値({@code sourcelinks.yml} の {@code transfer:} 節)。
 *
 * <p>2026-08-01 に定数から設定へ出した。このとき<b>既定値は当時のハードコード値と厳密に一致</b>させて
 * あり、config化だけではバランスが動かなかった。
 *
 * <p>⚠ 2026-08-24 に既定値を意図的に動かした2箇所がある(挙動不変ではない):
 * {@code sourcelink.drain-ratio}(新規・割合排出)と {@code network.path-particles.*}(見やすさ)。
 * どちらも理由は各定数のコメントに書いてある。
 * <b>配備先の yml は {@code saveResource(..., false)} なので jar を差し替えても更新されない</b> ――
 * 既定値を動かしただけでは実サーバに届かない({@code ops/scripts/} の patch スクリプトで
 * 配備先を書き換えてから {@code /ars reload})。
 *
 * <p>2系統ある転送経路の両方をここで扱う:
 * <ul>
 *   <li><b>sourcelink</b>: ソースリンク → <b>隣接</b>ソースジャーへの供給
 *       ({@link com.arspaper.source.sourcelink.Sourcelink#supplyAdjacent})。
 *       転送速度 = <b>max({@code max-per-transfer}, バッファ × {@code drain-ratio})</b> ÷ {@code interval-ticks}。
 *       検知半径 = バイタリック(mob討伐) / ボタニカル(作物成長)がイベントを拾う範囲。</li>
 *   <li><b>network</b>: ドミニオンワンドで結んだソースリレー網({@link SourceNetwork})。
 *       転送速度 = <b>max({@code max-per-transfer}, 残量 × {@code drain-ratio})</b> ÷ {@code interval-ticks}、
 *       転送範囲 = {@code max-link-range}(1本のリンクを張れる最大距離)。
 *       <b>割合ぶんは 2026-08-25 に追加</b>(それまで網だけ定額のままだった)。</li>
 * </ul>
 */
public record SourceTransferConfig(
        int sourcelinkIntervalTicks,
        int sourcelinkMaxPerTransfer,
        int sourcelinkBufferCap,
        double sourcelinkDrainRatio,
        int vitalicDetectionRadius,
        int botanicalDetectionRadius,
        int networkIntervalTicks,
        int networkMaxPerTransfer,
        double networkDrainRatio,
        int networkMaxLinkRange,
        boolean pathParticlesEnabled,
        int pathParticleIntervalTicks,
        double pathParticleSpacing,
        double pathParticleDotSize,
        int pathParticleViewDistance,
        int pathParticleMaxPaths,
        int infinityCoreRadius,
        double infinityCoreTransferMultiplier,
        double infinityCoreBufferMultiplier) {

    // --- 既定値 = 2026-08-01 以前のハードコード値(挙動不変) ---
    public static final int DEFAULT_SOURCELINK_INTERVAL_TICKS = 100;
    public static final int DEFAULT_SOURCELINK_MAX_PER_TRANSFER = 50;
    /** バッファ上限。既定は int の上限＝「事実上無制限だがオーバーフローはしない」。 */
    public static final int DEFAULT_SOURCELINK_BUFFER_CAP = Integer.MAX_VALUE;
    /**
     * 1周期にバッファの何割まで排出できるか(定額 {@code max-per-transfer} との大きい方を採る)。
     *
     * <p>⚠ 2026-08-24 追加。これが無かったため排出は<b>完全な定額</b>で、
     * 「1点あたり 100tick ÷ 50 = 2tick = 0.1秒」が燃料の価値に関係なく固定だった。
     * 階梯の {@code transfer-multiplier} と {@code yield-multiplier} は同じ倍率で伸びるので
     * <b>階梯を上げてもこの比率は一切変わらない</b> ―― 結果、高価値燃料ほど不利になり、
     * 圧縮薪1個(2,187)で約3分40秒、ソースの欠片(4,500)で7分30秒、
     * ソース機関1個(3,000万)で約35日という「生成量に見合わない転送速度」になっていた。
     * 割合排出を足すと、どれだけ溜まっていても指数的に減衰して数分で吐き切る。
     *
     * <p>既定 0.25 の根拠(定額50・周期100tick での実測。単位は秒):
     * <pre>
     *   投入量       定額のみ    0.10     0.25
     *   100(溶岩)         10       10       10   ← 小口は定額が勝つので変わらない
     *   2,187(圧縮薪)    220      120       65
     *   4,500(欠片)      450      155       75
     *   140,000(1スタック) 14,000    320      135
     *   30,000,000(機関) 3,000,000  575      230
     * </pre>
     * 0.10 だと単品の改善が2倍弱にとどまる(圧縮薪1個で2分)ので 0.25 を採る。
     * 上げても損失は生まれない —— ジャーの空きを超えた分はバッファへ戻るので、
     * 「ジャーの容量が実質的な上限」という元設計は保たれる。
     */
    public static final double DEFAULT_SOURCELINK_DRAIN_RATIO = 0.25;
    public static final int DEFAULT_VITALIC_DETECTION_RADIUS = 10;
    public static final int DEFAULT_BOTANICAL_DETECTION_RADIUS = 10;
    public static final int DEFAULT_NETWORK_INTERVAL_TICKS = 40;
    public static final int DEFAULT_NETWORK_MAX_PER_TRANSFER = 100;
    /**
     * 網の1リンク・1周期で送れる量のうち「残量に対する割合」ぶん(定額 {@code max-per-transfer}
     * との大きい方を採る)。判定は隣接供給と同じ {@link SourceDrainPolicy}。
     *
     * <p>⚠ 2026-08-25 追加。{@code sourcelink.drain-ratio}(2026-08-24)は<b>隣接供給の経路にしか
     * 効いていなかった</b>ため、ドミニオンワンドで結んだ網の転送だけ定額 100 ÷ 40tick
     * ＝毎秒2.5点に取り残されていた（ユーザー報告「ソースリンクの転送速度がまだ100ずつ」）。
     * 網は階梯倍率もコア倍率も掛からないので、<b>上位リンクにしても上位ジャーにしても永久に
     * 毎秒2.5点</b>で、ソース機関1個(3,000万)を運ぶのに約7日(60万秒)かかる計算だった。
     *
     * <p>同じ 0.25 を既定にするのは、隣接供給と網で「溜まった量の何割が1周期で動くか」を
     * 揃えるため(片方だけ速いと、ジャーを隣に置くか網で繋ぐかで速度が桁違いになる)。
     * 別のキーにしてあるのは周期が違う(隣接100tick / 網40tick)ため ——
     * 同じ割合でも網のほうが2.5倍速いので、後から別々に絞れる必要がある。
     *
     * <p>⚠ <b>配備先の yml にこのキーが無くてもこの既定値で動く</b>。ArsPaper の yml は
     * {@code saveResource(..., false)} なので jar を差し替えても配備先には現れないが、
     * {@code clampDouble} が未定義キーを既定値で埋めるので配備は jar だけで足りる。
     */
    public static final double DEFAULT_NETWORK_DRAIN_RATIO = 0.25;
    public static final int DEFAULT_NETWORK_MAX_LINK_RANGE = 30;

    // --- 経路パーティクル(2026-08-01 新規。既定ON) ---
    //
    // ⚠ 2026-08-01(同日 round2): 初版の既定値(10tick / 0.5m / 64経路)はワンド保持者1人あたり
    //    毎秒約8,000粒子パケットを送っていた(1経路30m ÷ 0.5m = 61粒子 + 終端2 → 63、
    //    × 64経路 = 4,032、× 毎秒2回 = 約8,064)。ワンドは設置作業中ずっと持つ道具なので
    //    「一瞬だけ重い」ではなく定常負荷になる。下記の保守的な既定へ引き下げた:
    //      1経路30m ÷ 1.0m = 31粒子 + 終端2 → 33、× 16経路 = 528、× 毎秒1回 = 約528粒子/秒。
    //    ≒ 1/15。可視性は「1ブロックに1粒子」で十分保てる。
    //    濃く出したい鯖は sourcelinks.yml で従来値(10 / 0.5 / 64)へ戻せる。
    //
    // ⚠ 2026-08-24(見にくさの修正): 上の「1ブロックに1粒子・1秒ごとに描き直し」は
    //    負荷こそ軽いが<b>線として読めない</b>という別の失敗をしていた。原因は3つ重なっている:
    //      ① 間隔1.0m + 粒の大きさ0.8〜1.2 → 大きい玉が飛び石で並ぶだけで線に見えない
    //      ② 描き直し20tick に対し DUST の寿命は約8〜40tick(乱数) → 次の描画までに
    //         半分近くが消えるので<b>点滅する</b>
    //      ③ view-distance 48m × 最大16経路 × 隣接供給の全描画 → 周囲一帯が点だらけになり
    //         「今どれを繋いだのか」が埋もれる
    //    そこで「細く・詰めて・切れない線を、近くの数本だけ」へ振り直した:
    //      間隔0.5m / 粒0.45 / 10tick / 24m / 8経路。
    //    粒子数は 1経路30m なら 61+2=63、× 8経路 × 毎秒2回 ≒ 1,000粒子/秒
    //    (直前の既定は約528、初版は約8,064)。密度を2倍にしても範囲と本数を半分にしたので
    //    合計は初版の1/8に収まる。
    public static final boolean DEFAULT_PATH_PARTICLES_ENABLED = true;
    public static final int DEFAULT_PATH_PARTICLE_INTERVAL_TICKS = 10;
    public static final double DEFAULT_PATH_PARTICLE_SPACING = 0.5;
    /**
     * 経路の粒(DUST)の大きさ。1.0 がバニラのレッドストーン粒と同じで、線に使うと太すぎる。
     * 終端マーカーと隣接供給はこの値から一定倍率で導く({@code SourceNetworkParticleTask})。
     */
    public static final double DEFAULT_PATH_PARTICLE_DOT_SIZE = 0.45;
    public static final int DEFAULT_PATH_PARTICLE_VIEW_DISTANCE = 24;
    public static final int DEFAULT_PATH_PARTICLE_MAX_PATHS = 8;

    // --- infinity_source_core を置いて機能させる(2026-08-02 新規。2026-08-01 確定仕様 柱6) ---
    //
    // 設置された infinity_source_core からこの半径内のソースリンクへ補正が乗る。
    // マルチブロックのパターン判定はしない(半径判定だけで成立させる)。コアは消費されず、
    // 設置したまま使い回せる(InfinityCoreTracker が設置位置を追跡する)。
    public static final int DEFAULT_INFINITY_CORE_RADIUS = 5;
    public static final double DEFAULT_INFINITY_CORE_TRANSFER_MULTIPLIER = 2.0;
    public static final double DEFAULT_INFINITY_CORE_BUFFER_MULTIPLIER = 2.0;

    /** 検知半径の上限。これ以上はイベントごとの全リンク走査が実用にならない。 */
    private static final int MAX_DETECTION_RADIUS = 256;
    /** リンク距離の上限。ワールド跨ぎは元から不可、チャンクロード判定の現実的な上限に合わせる。 */
    private static final int MAX_LINK_RANGE = 256;
    /** パーティクル間隔の下限(m)。これ未満だと1経路あたりの粒子数が爆発する。 */
    private static final double MIN_PARTICLE_SPACING = 0.1;
    /**
     * 粒の大きさの下限/上限。0 は「見えない」ではなく<b>クライアント側で描画が壊れる</b>
     * (レッドストーン粒の scale は 0 を想定していない)ので 0 を許さない。
     */
    private static final double MIN_PARTICLE_DOT_SIZE = 0.05;
    private static final double MAX_PARTICLE_DOT_SIZE = 4.0;
    /** infinity-core 倍率の上限。桁間違い(例: 2.0のつもりで20)を早期に警告するための保守的な上限。 */
    private static final double MAX_INFINITY_CORE_MULTIPLIER = 1000.0;

    public static SourceTransferConfig defaults() {
        return new SourceTransferConfig(
                DEFAULT_SOURCELINK_INTERVAL_TICKS,
                DEFAULT_SOURCELINK_MAX_PER_TRANSFER,
                DEFAULT_SOURCELINK_BUFFER_CAP,
                DEFAULT_SOURCELINK_DRAIN_RATIO,
                DEFAULT_VITALIC_DETECTION_RADIUS,
                DEFAULT_BOTANICAL_DETECTION_RADIUS,
                DEFAULT_NETWORK_INTERVAL_TICKS,
                DEFAULT_NETWORK_MAX_PER_TRANSFER,
                DEFAULT_NETWORK_DRAIN_RATIO,
                DEFAULT_NETWORK_MAX_LINK_RANGE,
                DEFAULT_PATH_PARTICLES_ENABLED,
                DEFAULT_PATH_PARTICLE_INTERVAL_TICKS,
                DEFAULT_PATH_PARTICLE_SPACING,
                DEFAULT_PATH_PARTICLE_DOT_SIZE,
                DEFAULT_PATH_PARTICLE_VIEW_DISTANCE,
                DEFAULT_PATH_PARTICLE_MAX_PATHS,
                DEFAULT_INFINITY_CORE_RADIUS,
                DEFAULT_INFINITY_CORE_TRANSFER_MULTIPLIER,
                DEFAULT_INFINITY_CORE_BUFFER_MULTIPLIER);
    }

    /**
     * {@code transfer:} 節をパースする。{@code root} が null なら全て既定値。
     * 範囲外の値は既定挙動を壊さない側へクランプし、{@code warn} で理由を通知する。
     */
    public static SourceTransferConfig parse(ConfigurationSection root, Consumer<String> warn) {
        if (root == null) {
            return defaults();
        }
        ConfigurationSection link = root.getConfigurationSection("sourcelink");
        ConfigurationSection detect = link == null ? null : link.getConfigurationSection("detection-radius");
        ConfigurationSection net = root.getConfigurationSection("network");
        ConfigurationSection fx = net == null ? null : net.getConfigurationSection("path-particles");
        ConfigurationSection infinityCore = root.getConfigurationSection("infinity-core");

        return new SourceTransferConfig(
                clampInt(link, "interval-ticks", DEFAULT_SOURCELINK_INTERVAL_TICKS,
                        1, 72000, "transfer.sourcelink.interval-ticks", warn),
                clampInt(link, "max-per-transfer", DEFAULT_SOURCELINK_MAX_PER_TRANSFER,
                        1, Integer.MAX_VALUE, "transfer.sourcelink.max-per-transfer", warn),
                clampInt(link, "buffer-cap", DEFAULT_SOURCELINK_BUFFER_CAP,
                        1, Integer.MAX_VALUE, "transfer.sourcelink.buffer-cap", warn),
                clampDouble(link, "drain-ratio", DEFAULT_SOURCELINK_DRAIN_RATIO,
                        0.0, 1.0, "transfer.sourcelink.drain-ratio", warn),
                clampInt(detect, "vitalic", DEFAULT_VITALIC_DETECTION_RADIUS,
                        0, MAX_DETECTION_RADIUS, "transfer.sourcelink.detection-radius.vitalic", warn),
                clampInt(detect, "botanical", DEFAULT_BOTANICAL_DETECTION_RADIUS,
                        0, MAX_DETECTION_RADIUS, "transfer.sourcelink.detection-radius.botanical", warn),
                clampInt(net, "interval-ticks", DEFAULT_NETWORK_INTERVAL_TICKS,
                        1, 72000, "transfer.network.interval-ticks", warn),
                clampInt(net, "max-per-transfer", DEFAULT_NETWORK_MAX_PER_TRANSFER,
                        1, Integer.MAX_VALUE, "transfer.network.max-per-transfer", warn),
                clampDouble(net, "drain-ratio", DEFAULT_NETWORK_DRAIN_RATIO,
                        0.0, 1.0, "transfer.network.drain-ratio", warn),
                clampInt(net, "max-link-range", DEFAULT_NETWORK_MAX_LINK_RANGE,
                        1, MAX_LINK_RANGE, "transfer.network.max-link-range", warn),
                fx == null ? DEFAULT_PATH_PARTICLES_ENABLED
                        : fx.getBoolean("enabled", DEFAULT_PATH_PARTICLES_ENABLED),
                clampInt(fx, "interval-ticks", DEFAULT_PATH_PARTICLE_INTERVAL_TICKS,
                        1, 1200, "transfer.network.path-particles.interval-ticks", warn),
                clampDouble(fx, "spacing", DEFAULT_PATH_PARTICLE_SPACING,
                        MIN_PARTICLE_SPACING, 16.0, "transfer.network.path-particles.spacing", warn),
                clampDouble(fx, "dot-size", DEFAULT_PATH_PARTICLE_DOT_SIZE,
                        MIN_PARTICLE_DOT_SIZE, MAX_PARTICLE_DOT_SIZE,
                        "transfer.network.path-particles.dot-size", warn),
                clampInt(fx, "view-distance", DEFAULT_PATH_PARTICLE_VIEW_DISTANCE,
                        1, 256, "transfer.network.path-particles.view-distance", warn),
                clampInt(fx, "max-paths", DEFAULT_PATH_PARTICLE_MAX_PATHS,
                        1, 4096, "transfer.network.path-particles.max-paths", warn),
                clampInt(infinityCore, "radius", DEFAULT_INFINITY_CORE_RADIUS,
                        0, MAX_DETECTION_RADIUS, "transfer.infinity-core.radius", warn),
                clampDouble(infinityCore, "transfer-multiplier", DEFAULT_INFINITY_CORE_TRANSFER_MULTIPLIER,
                        0.0, MAX_INFINITY_CORE_MULTIPLIER, "transfer.infinity-core.transfer-multiplier", warn),
                clampDouble(infinityCore, "buffer-multiplier", DEFAULT_INFINITY_CORE_BUFFER_MULTIPLIER,
                        0.0, MAX_INFINITY_CORE_MULTIPLIER, "transfer.infinity-core.buffer-multiplier", warn));
    }

    private static int clampInt(ConfigurationSection sec, String key, int fallback,
                                int min, int max, String path, Consumer<String> warn) {
        if (sec == null || !sec.isSet(key)) {
            return fallback;
        }
        int raw = sec.getInt(key, fallback);
        int clamped = Math.max(min, Math.min(max, raw));
        if (clamped != raw) {
            warn.accept("sourcelinks.yml " + path + ": " + raw + " は範囲外(" + min + ".." + max
                    + ")のため " + clamped + " として扱います");
        }
        return clamped;
    }

    private static double clampDouble(ConfigurationSection sec, String key, double fallback,
                                      double min, double max, String path, Consumer<String> warn) {
        if (sec == null || !sec.isSet(key)) {
            return fallback;
        }
        double raw = sec.getDouble(key, fallback);
        if (!Double.isFinite(raw)) {
            warn.accept("sourcelinks.yml " + path + ": 数値ではないため既定値 " + fallback + " を使います");
            return fallback;
        }
        double clamped = Math.max(min, Math.min(max, raw));
        if (clamped != raw) {
            warn.accept("sourcelinks.yml " + path + ": " + raw + " は範囲外(" + min + ".." + max
                    + ")のため " + clamped + " として扱います");
        }
        return clamped;
    }

    /**
     * バッファへの加算結果を上限クランプ付きで求める(オーバーフロー安全)。
     *
     * <p>⚠ 元実装は {@code getBuffer(tile) + amount} を int で行っていたため、
     * 高価値燃料(例 {@code custom:source_engine} = 3000万)を焼べ続けると約2.1億で
     * <b>int オーバーフローして負値になり、蓄積が無言で全損</b>していた。
     * long で計算してから {@code cap} でクランプする。
     */
    public static int clampBuffer(int current, int amount, int cap) {
        long sum = (long) current + (long) amount;
        if (sum < 0L) {
            return 0;
        }
        return (int) Math.min(sum, (long) cap);
    }
}
