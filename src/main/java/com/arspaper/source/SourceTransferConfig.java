package com.arspaper.source;

import org.bukkit.configuration.ConfigurationSection;

import java.util.function.Consumer;

/**
 * ソース転送まわりの調整値({@code sourcelinks.yml} の {@code transfer:} 節)。
 *
 * <p>2026-08-01 に定数から設定へ出した。<b>既定値は当時のハードコード値と厳密に一致する</b>ので、
 * 設定を書かなければ挙動は一切変わらない(config化だけでバランスが動かないこと)。
 *
 * <p>2系統ある転送経路の両方をここで扱う:
 * <ul>
 *   <li><b>sourcelink</b>: ソースリンク → <b>隣接</b>ソースジャーへの供給
 *       ({@link com.arspaper.source.sourcelink.Sourcelink#supplyAdjacent})。
 *       転送速度 = {@code max-per-transfer} ÷ {@code interval-ticks}。
 *       検知半径 = バイタリック(mob討伐) / ボタニカル(作物成長)がイベントを拾う範囲。</li>
 *   <li><b>network</b>: ドミニオンワンドで結んだソースリレー網({@link SourceNetwork})。
 *       転送速度 = {@code max-per-transfer} ÷ {@code interval-ticks}、
 *       転送範囲 = {@code max-link-range}(1本のリンクを張れる最大距離)。</li>
 * </ul>
 */
public record SourceTransferConfig(
        int sourcelinkIntervalTicks,
        int sourcelinkMaxPerTransfer,
        int sourcelinkBufferCap,
        int vitalicDetectionRadius,
        int botanicalDetectionRadius,
        int networkIntervalTicks,
        int networkMaxPerTransfer,
        int networkMaxLinkRange,
        boolean pathParticlesEnabled,
        int pathParticleIntervalTicks,
        double pathParticleSpacing,
        int pathParticleViewDistance,
        int pathParticleMaxPaths) {

    // --- 既定値 = 2026-08-01 以前のハードコード値(挙動不変) ---
    public static final int DEFAULT_SOURCELINK_INTERVAL_TICKS = 100;
    public static final int DEFAULT_SOURCELINK_MAX_PER_TRANSFER = 50;
    /** バッファ上限。既定は int の上限＝「事実上無制限だがオーバーフローはしない」。 */
    public static final int DEFAULT_SOURCELINK_BUFFER_CAP = Integer.MAX_VALUE;
    public static final int DEFAULT_VITALIC_DETECTION_RADIUS = 10;
    public static final int DEFAULT_BOTANICAL_DETECTION_RADIUS = 10;
    public static final int DEFAULT_NETWORK_INTERVAL_TICKS = 40;
    public static final int DEFAULT_NETWORK_MAX_PER_TRANSFER = 100;
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
    public static final boolean DEFAULT_PATH_PARTICLES_ENABLED = true;
    public static final int DEFAULT_PATH_PARTICLE_INTERVAL_TICKS = 20;
    public static final double DEFAULT_PATH_PARTICLE_SPACING = 1.0;
    public static final int DEFAULT_PATH_PARTICLE_VIEW_DISTANCE = 48;
    public static final int DEFAULT_PATH_PARTICLE_MAX_PATHS = 16;

    /** 検知半径の上限。これ以上はイベントごとの全リンク走査が実用にならない。 */
    private static final int MAX_DETECTION_RADIUS = 256;
    /** リンク距離の上限。ワールド跨ぎは元から不可、チャンクロード判定の現実的な上限に合わせる。 */
    private static final int MAX_LINK_RANGE = 256;
    /** パーティクル間隔の下限(m)。これ未満だと1経路あたりの粒子数が爆発する。 */
    private static final double MIN_PARTICLE_SPACING = 0.1;

    public static SourceTransferConfig defaults() {
        return new SourceTransferConfig(
                DEFAULT_SOURCELINK_INTERVAL_TICKS,
                DEFAULT_SOURCELINK_MAX_PER_TRANSFER,
                DEFAULT_SOURCELINK_BUFFER_CAP,
                DEFAULT_VITALIC_DETECTION_RADIUS,
                DEFAULT_BOTANICAL_DETECTION_RADIUS,
                DEFAULT_NETWORK_INTERVAL_TICKS,
                DEFAULT_NETWORK_MAX_PER_TRANSFER,
                DEFAULT_NETWORK_MAX_LINK_RANGE,
                DEFAULT_PATH_PARTICLES_ENABLED,
                DEFAULT_PATH_PARTICLE_INTERVAL_TICKS,
                DEFAULT_PATH_PARTICLE_SPACING,
                DEFAULT_PATH_PARTICLE_VIEW_DISTANCE,
                DEFAULT_PATH_PARTICLE_MAX_PATHS);
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

        return new SourceTransferConfig(
                clampInt(link, "interval-ticks", DEFAULT_SOURCELINK_INTERVAL_TICKS,
                        1, 72000, "transfer.sourcelink.interval-ticks", warn),
                clampInt(link, "max-per-transfer", DEFAULT_SOURCELINK_MAX_PER_TRANSFER,
                        1, Integer.MAX_VALUE, "transfer.sourcelink.max-per-transfer", warn),
                clampInt(link, "buffer-cap", DEFAULT_SOURCELINK_BUFFER_CAP,
                        1, Integer.MAX_VALUE, "transfer.sourcelink.buffer-cap", warn),
                clampInt(detect, "vitalic", DEFAULT_VITALIC_DETECTION_RADIUS,
                        0, MAX_DETECTION_RADIUS, "transfer.sourcelink.detection-radius.vitalic", warn),
                clampInt(detect, "botanical", DEFAULT_BOTANICAL_DETECTION_RADIUS,
                        0, MAX_DETECTION_RADIUS, "transfer.sourcelink.detection-radius.botanical", warn),
                clampInt(net, "interval-ticks", DEFAULT_NETWORK_INTERVAL_TICKS,
                        1, 72000, "transfer.network.interval-ticks", warn),
                clampInt(net, "max-per-transfer", DEFAULT_NETWORK_MAX_PER_TRANSFER,
                        1, Integer.MAX_VALUE, "transfer.network.max-per-transfer", warn),
                clampInt(net, "max-link-range", DEFAULT_NETWORK_MAX_LINK_RANGE,
                        1, MAX_LINK_RANGE, "transfer.network.max-link-range", warn),
                fx == null ? DEFAULT_PATH_PARTICLES_ENABLED
                        : fx.getBoolean("enabled", DEFAULT_PATH_PARTICLES_ENABLED),
                clampInt(fx, "interval-ticks", DEFAULT_PATH_PARTICLE_INTERVAL_TICKS,
                        1, 1200, "transfer.network.path-particles.interval-ticks", warn),
                clampDouble(fx, "spacing", DEFAULT_PATH_PARTICLE_SPACING,
                        MIN_PARTICLE_SPACING, 16.0, "transfer.network.path-particles.spacing", warn),
                clampInt(fx, "view-distance", DEFAULT_PATH_PARTICLE_VIEW_DISTANCE,
                        1, 256, "transfer.network.path-particles.view-distance", warn),
                clampInt(fx, "max-paths", DEFAULT_PATH_PARTICLE_MAX_PATHS,
                        1, 4096, "transfer.network.path-particles.max-paths", warn));
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
