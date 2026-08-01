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
        assertTrue(warnings.isEmpty(), "既定値の解決で警告が出てはいけない: " + warnings);
        assertEquals(SourceTransferConfig.defaults(), cfg);
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
}
