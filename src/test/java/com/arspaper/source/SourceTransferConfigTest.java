package com.arspaper.source;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code sourcelinks.yml} の {@code transfer:} 節のパースを固定する。
 *
 * <p>いちばん重要なのは <b>「設定を書かなければ挙動が変わらない」</b>こと。
 * config化のついでに既定値がズレると、バランス変更として意図していないのに
 * 回収時間が何倍にもなる/半分になる、という気づけない事故になる。
 */
class SourceTransferConfigTest {

    private static SourceTransferConfig parse(String body, List<String> warnings) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(body);
        } catch (org.bukkit.configuration.InvalidConfigurationException broken) {
            throw new AssertionError("テスト用 YAML が壊れている", broken);
        }
        return SourceTransferConfig.parse(yaml.getConfigurationSection("transfer"), warnings::add);
    }

    @Test
    @DisplayName("transfer: 節が無ければ config 化前のハードコード値と厳密に一致する")
    void absentSectionKeepsLegacyBehaviour() {
        List<String> warnings = new ArrayList<>();
        SourceTransferConfig cfg = parse("other: 1", warnings);

        assertEquals(100, cfg.sourcelinkIntervalTicks());
        assertEquals(50, cfg.sourcelinkMaxPerTransfer());
        assertEquals(10, cfg.vitalicDetectionRadius());
        assertEquals(10, cfg.botanicalDetectionRadius());
        assertEquals(40, cfg.networkIntervalTicks());
        assertEquals(100, cfg.networkMaxPerTransfer());
        assertEquals(30, cfg.networkMaxLinkRange());
        assertEquals(Integer.MAX_VALUE, cfg.sourcelinkBufferCap());
        assertEquals(5, cfg.infinityCoreRadius());
        assertEquals(2.0, cfg.infinityCoreTransferMultiplier(), 1e-9);
        assertEquals(2.0, cfg.infinityCoreBufferMultiplier(), 1e-9);
        assertTrue(warnings.isEmpty(), "既定値の解決で警告が出てはいけない: " + warnings);
        assertEquals(SourceTransferConfig.defaults(), cfg);
    }

    @Test
    @DisplayName("経路パーティクルの既定値は保守的な値で固定する(毎秒の粒子数の見積り付き)")
    void particleDefaultsStayConservative() {
        SourceTransferConfig cfg = SourceTransferConfig.defaults();

        assertTrue(cfg.pathParticlesEnabled(), "既定はON(可視化しないと経路が追えない)");
        assertEquals(20, cfg.pathParticleIntervalTicks());
        assertEquals(1.0, cfg.pathParticleSpacing(), 1e-9);
        assertEquals(48, cfg.pathParticleViewDistance());
        assertEquals(16, cfg.pathParticleMaxPaths());

        // 1経路30mでの毎秒粒子数 = (30 ÷ spacing + 1 端点 + 2 終端マーカー) × max-paths × (20 ÷ interval)
        int dots = SourcePathVisualPolicy.dotCount(30.0, cfg.pathParticleSpacing());
        int perSecond = (dots + 2) * cfg.pathParticleMaxPaths() * (20 / cfg.pathParticleIntervalTicks());
        assertEquals(528, perSecond, "ワンド保持者1人あたりの定常負荷");
        assertTrue(perSecond <= 1000,
                "初版の既定(10tick / 0.5m / 64経路)は毎秒約8,064粒子だった。"
                        + "ワンドは設置作業中ずっと持つ道具なので、ここは1,000粒子/秒を超えさせない: " + perSecond);
    }

    @Test
    @DisplayName("出荷 sourcelinks.yml の値は既定値と厳密に一致する")
    void shippedYamlMatchesDefaults() throws Exception {
        // 既定値だけ下げても、出荷ymlが古い値を明示していれば新規サーバには効かない。
        // 「Java の既定」と「出荷 yml」の2本を必ず同時に動かすためのガード。
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new java.io.File("src/main/resources/sourcelinks.yml"));

        List<String> warnings = new ArrayList<>();
        SourceTransferConfig cfg =
                SourceTransferConfig.parse(yaml.getConfigurationSection("transfer"), warnings::add);

        assertEquals(SourceTransferConfig.defaults(), cfg,
                "出荷 sourcelinks.yml の transfer: が SourceTransferConfig の既定値とズレている");
        assertTrue(warnings.isEmpty(), "出荷ymlがクランプ対象の値を書いている: " + warnings);
    }

    @Test
    @DisplayName("部分的に書いても、書かなかったキーは既定値のまま")
    void partialSectionKeepsOtherDefaults() {
        SourceTransferConfig cfg = parse("""
                transfer:
                  sourcelink:
                    max-per-transfer: 500
                """, new ArrayList<>());

        assertEquals(500, cfg.sourcelinkMaxPerTransfer());
        assertEquals(100, cfg.sourcelinkIntervalTicks());
        assertEquals(30, cfg.networkMaxLinkRange());
    }

    @Test
    @DisplayName("転送速度と転送範囲を両方設定できる")
    void ratesAndRangesAreConfigurable() {
        SourceTransferConfig cfg = parse("""
                transfer:
                  sourcelink:
                    interval-ticks: 20
                    max-per-transfer: 2000
                    detection-radius:
                      vitalic: 4
                      botanical: 6
                  network:
                    interval-ticks: 10
                    max-per-transfer: 5000
                    max-link-range: 16
                """, new ArrayList<>());

        assertEquals(20, cfg.sourcelinkIntervalTicks());
        assertEquals(2000, cfg.sourcelinkMaxPerTransfer());
        assertEquals(4, cfg.vitalicDetectionRadius());
        assertEquals(6, cfg.botanicalDetectionRadius());
        assertEquals(10, cfg.networkIntervalTicks());
        assertEquals(5000, cfg.networkMaxPerTransfer());
        assertEquals(16, cfg.networkMaxLinkRange());
    }

    @Test
    @DisplayName("周期0はタイマーを壊すので1へクランプし、理由を警告する")
    void zeroIntervalIsClampedWithWarning() {
        List<String> warnings = new ArrayList<>();
        SourceTransferConfig cfg = parse("""
                transfer:
                  sourcelink:
                    interval-ticks: 0
                  network:
                    interval-ticks: -5
                """, warnings);

        assertEquals(1, cfg.sourcelinkIntervalTicks());
        assertEquals(1, cfg.networkIntervalTicks());
        assertEquals(2, warnings.size(), "クランプは黙って行わない: " + warnings);
    }

    @Test
    @DisplayName("検知半径0は「そのイベント蓄積を止める」として通す")
    void zeroDetectionRadiusIsAllowed() {
        List<String> warnings = new ArrayList<>();
        SourceTransferConfig cfg = parse("""
                transfer:
                  sourcelink:
                    detection-radius:
                      vitalic: 0
                      botanical: 0
                """, warnings);

        assertEquals(0, cfg.vitalicDetectionRadius());
        assertEquals(0, cfg.botanicalDetectionRadius());
        assertTrue(warnings.isEmpty());
    }

    @Test
    @DisplayName("パーティクル設定はOFFにでき、間隔は下限でクランプされる")
    void particleSettingsAreConfigurable() {
        List<String> warnings = new ArrayList<>();
        SourceTransferConfig cfg = parse("""
                transfer:
                  network:
                    path-particles:
                      enabled: false
                      interval-ticks: 0
                      spacing: 0.0
                      view-distance: 999
                      max-paths: 8
                """, warnings);

        assertFalse(cfg.pathParticlesEnabled());
        assertEquals(1, cfg.pathParticleIntervalTicks());
        assertEquals(0.1, cfg.pathParticleSpacing(), 1e-9);
        assertEquals(256, cfg.pathParticleViewDistance());
        assertEquals(8, cfg.pathParticleMaxPaths());
        // クランプしたのは interval-ticks / spacing / view-distance の3件。max-paths=8 は範囲内。
        assertEquals(3, warnings.size(), "3件クランプしたはず: " + warnings);
    }

    // ---- バッファのオーバーフロー ----

    @Test
    @DisplayName("バッファ加算は int オーバーフローせず上限でクランプされる")
    void bufferAdditionNeverOverflows() {
        // 実バグ: ソース機関(3000万)を焼べ続けると約2.1億で負値に化けて全損していた。
        int cap = Integer.MAX_VALUE;
        assertEquals(Integer.MAX_VALUE,
                SourceTransferConfig.clampBuffer(Integer.MAX_VALUE - 10, 30_000_000, cap));
        assertEquals(Integer.MAX_VALUE,
                SourceTransferConfig.clampBuffer(2_000_000_000, 2_000_000_000, cap));
    }

    @Test
    @DisplayName("buffer-cap を下げるとその値で頭打ちになる")
    void bufferAdditionRespectsConfiguredCap() {
        assertEquals(1000, SourceTransferConfig.clampBuffer(900, 500, 1000));
        assertEquals(1000, SourceTransferConfig.clampBuffer(1000, 1, 1000));
    }

    @Test
    @DisplayName("上限に達していない通常の加算はそのまま通す")
    void bufferAdditionIsTransparentBelowCap() {
        assertEquals(1500, SourceTransferConfig.clampBuffer(1000, 500, Integer.MAX_VALUE));
        assertEquals(30_000_000, SourceTransferConfig.clampBuffer(0, 30_000_000, Integer.MAX_VALUE));
    }

    // ---- infinity_source_core (柱6) ----

    @Test
    @DisplayName("infinity-core: 節を書けば半径・倍率を上書きできる")
    void infinityCoreSectionIsConfigurable() {
        SourceTransferConfig cfg = parse("""
                transfer:
                  infinity-core:
                    radius: 8
                    transfer-multiplier: 3.5
                    buffer-multiplier: 1.5
                """, new ArrayList<>());

        assertEquals(8, cfg.infinityCoreRadius());
        assertEquals(3.5, cfg.infinityCoreTransferMultiplier(), 1e-9);
        assertEquals(1.5, cfg.infinityCoreBufferMultiplier(), 1e-9);
    }

    @Test
    @DisplayName("infinity-core.radius=0は「補正を無効化する」として通す(警告なし)")
    void infinityCoreRadiusZeroIsAllowed() {
        List<String> warnings = new ArrayList<>();
        SourceTransferConfig cfg = parse("""
                transfer:
                  infinity-core:
                    radius: 0
                """, warnings);

        assertEquals(0, cfg.infinityCoreRadius());
        assertTrue(warnings.isEmpty());
    }

    @Test
    @DisplayName("infinity-core の倍率に極端な値を書くとクランプされ警告が出る")
    void infinityCoreMultiplierOutOfRangeIsClamped() {
        List<String> warnings = new ArrayList<>();
        SourceTransferConfig cfg = parse("""
                transfer:
                  infinity-core:
                    transfer-multiplier: -5.0
                    buffer-multiplier: 999999.0
                """, warnings);

        assertEquals(0.0, cfg.infinityCoreTransferMultiplier(), 1e-9);
        assertEquals(1000.0, cfg.infinityCoreBufferMultiplier(), 1e-9);
        assertEquals(2, warnings.size(), "2件クランプしたはず: " + warnings);
    }
}
