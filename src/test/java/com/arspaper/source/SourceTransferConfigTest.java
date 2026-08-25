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
    @DisplayName("網の割合排出は既定0.25で、隣接供給と同じ値に揃っている")
    void networkDrainRatioDefaultsToTheSameShareAsAdjacentSupply() {
        // 2026-08-25: 割合排出(2026-08-24)は隣接供給にしか効いておらず、網だけ定額100 ÷ 40tick
        // ＝毎秒2.5点に取り残されていた(報告「ソースリンクの転送速度がまだ100ずつ」)。
        // 網は階梯倍率もコア倍率も掛からないので、ここが定額のままだと上位リンクにしても速くならない。
        SourceTransferConfig cfg = SourceTransferConfig.defaults();

        assertEquals(0.25, cfg.networkDrainRatio(), 1e-9);
        assertEquals(cfg.sourcelinkDrainRatio(), cfg.networkDrainRatio(), 1e-9,
                "隣接に置くか網で繋ぐかで「溜まった量の何割が動くか」が変わってはいけない");

        // 定額と割合の大きい方。小口(残量400未満)は定額が勝つので少量の速度は変わらない。
        assertEquals(100, SourceDrainPolicy.allowance(
                cfg.networkMaxPerTransfer(), 100, cfg.networkDrainRatio()));
        assertEquals(7_500_000, SourceDrainPolicy.allowance(
                cfg.networkMaxPerTransfer(), 30_000_000, cfg.networkDrainRatio()));
    }

    @Test
    @DisplayName("網の drain-ratio は yml から上書きでき、範囲外は警告付きでクランプする")
    void networkDrainRatioIsConfigurableAndClamped() {
        List<String> warnings = new ArrayList<>();
        SourceTransferConfig cfg = parse("""
                transfer:
                  network:
                    drain-ratio: 0.5
                """, warnings);
        assertEquals(0.5, cfg.networkDrainRatio(), 1e-9);
        assertTrue(warnings.isEmpty(), "範囲内の値で警告が出てはいけない: " + warnings);

        List<String> tooBig = new ArrayList<>();
        SourceTransferConfig clamped = parse("""
                transfer:
                  network:
                    drain-ratio: 5.0
                """, tooBig);
        assertEquals(1.0, clamped.networkDrainRatio(), 1e-9, "1.0(全量)より大きくはできない");
        assertFalse(tooBig.isEmpty(), "クランプしたら理由を通知する必要がある");
    }

    @Test
    @DisplayName("経路パーティクルの既定値は「細い線を近くだけ」で固定する(毎秒の粒子数の見積り付き)")
    void particleDefaultsStayConservative() {
        SourceTransferConfig cfg = SourceTransferConfig.defaults();

        assertTrue(cfg.pathParticlesEnabled(), "既定はON(可視化しないと経路が追えない)");
        assertEquals(10, cfg.pathParticleIntervalTicks());
        assertEquals(0.5, cfg.pathParticleSpacing(), 1e-9);
        assertEquals(0.45, cfg.pathParticleDotSize(), 1e-9);
        assertEquals(24, cfg.pathParticleViewDistance());
        assertEquals(8, cfg.pathParticleMaxPaths());

        // ⚠ DUST の寿命は約8〜40tick。描き直し周期がこれを超えると
        //    「描いた線が次の描画までに消える」= 点滅になる。20tick の既定がまさにそれだった。
        assertTrue(cfg.pathParticleIntervalTicks() <= 10,
                "描き直しは10tick以下に保つ(超えると点滅する): " + cfg.pathParticleIntervalTicks());
        // ⚠ 粒の大きさ1.0はバニラのレッドストーン粒と同じで、線に使うと太い玉の飛び石になる。
        assertTrue(cfg.pathParticleDotSize() < 1.0,
                "線に使う粒はバニラ既定(1.0)より小さくする: " + cfg.pathParticleDotSize());

        // 1経路30mでの毎秒粒子数 = (30 ÷ spacing + 1 端点 + 2 終端マーカー) × max-paths × (20 ÷ interval)
        int dots = SourcePathVisualPolicy.dotCount(30.0, cfg.pathParticleSpacing());
        int perSecond = (dots + 2) * cfg.pathParticleMaxPaths() * (20 / cfg.pathParticleIntervalTicks());
        assertEquals(1008, perSecond, "ワンド保持者1人あたりの定常負荷");
        assertTrue(perSecond <= 1200,
                "初版の既定(10tick / 0.5m / 64経路 / 48m)は毎秒約8,064粒子だった。"
                        + "ワンドは設置作業中ずっと持つ道具なので、密度を上げるなら"
                        + "範囲(view-distance)と本数(max-paths)を必ず下げて相殺する: " + perSecond);
    }

    @Test
    @DisplayName("割合排出の既定は 0.25（定額だけだと高価値燃料ほど不利になる）")
    void drainRatioDefaultBoundsWorstCaseDrainTime() {
        SourceTransferConfig cfg = SourceTransferConfig.defaults();
        assertEquals(0.25, cfg.sourcelinkDrainRatio(), 1e-9);

        // 割合排出があると、どれだけ溜まっていても指数的に減衰する。
        // ソース機関1個(3,000万)でも「定額のみなら約35日」→「割合込みなら数分」。
        long buffer = 30_000_000L;
        int cycles = 0;
        while (buffer > 0 && cycles < 10_000) {
            long drain = Math.max(50L, (long) Math.ceil(buffer * cfg.sourcelinkDrainRatio()));
            buffer -= Math.min(buffer, drain);
            cycles++;
        }
        assertEquals(0L, buffer, "吐き切れずに残ってはいけない");
        long seconds = (long) cycles * cfg.sourcelinkIntervalTicks() / 20L;
        assertTrue(seconds <= 900,
                "3,000万でも15分以内に吐き切ること(定額のみなら約35日かかっていた): " + seconds + "秒");
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

    @Test
    @DisplayName("粒の大きさは設定でき、0 は描画が壊れるので下限へクランプする")
    void dotSizeIsConfigurableAndNeverZero() {
        List<String> warnings = new ArrayList<>();
        SourceTransferConfig cfg = parse("""
                transfer:
                  network:
                    path-particles:
                      dot-size: 0.8
                """, warnings);
        assertEquals(0.8, cfg.pathParticleDotSize(), 1e-9);
        assertTrue(warnings.isEmpty());

        List<String> clamped = new ArrayList<>();
        SourceTransferConfig zero = parse("""
                transfer:
                  network:
                    path-particles:
                      dot-size: 0.0
                """, clamped);
        assertTrue(zero.pathParticleDotSize() > 0.0,
                "0 は「見えない」ではなくクライアント側の描画が壊れる値");
        assertEquals(1, clamped.size(), "クランプは黙って行わない: " + clamped);
    }

    @Test
    @DisplayName("割合排出は 0〜1 の外を書くとクランプされ、0 は「従来どおりの定額」として通る")
    void drainRatioIsClampedToUnitRange() {
        List<String> warnings = new ArrayList<>();
        SourceTransferConfig off = parse("""
                transfer:
                  sourcelink:
                    drain-ratio: 0.0
                """, warnings);
        assertEquals(0.0, off.sourcelinkDrainRatio(), 1e-9);
        assertTrue(warnings.isEmpty(), "0 は「割合排出を止める」意味なので警告しない: " + warnings);

        List<String> clamped = new ArrayList<>();
        SourceTransferConfig over = parse("""
                transfer:
                  sourcelink:
                    drain-ratio: 3.0
                """, clamped);
        assertEquals(1.0, over.sourcelinkDrainRatio(), 1e-9);
        assertEquals(1, clamped.size(), "クランプは黙って行わない: " + clamped);
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
