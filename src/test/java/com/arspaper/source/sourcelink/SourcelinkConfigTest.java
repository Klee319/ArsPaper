package com.arspaper.source.sourcelink;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code sourcelinks.yml} の {@code items.<id>.transfer-multiplier} パース(K-16対応、2026-08-02)。
 *
 * <p>いちばん重要なのは <b>「未設定なら1.0 = 挙動不変」</b>と
 * <b>「0以下/非有限値を書いても転送が止まらない」</b>こと。ここが壊れると、上位ソースリンクが
 * 逆に無印より遅くなる、または typo(例: マイナス値)でリンクが完全に詰まる、という
 * 気づきにくい退行になる。
 */
class SourcelinkConfigTest {

    private static ConfigurationSection sectionOf(String body) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(body);
        } catch (org.bukkit.configuration.InvalidConfigurationException broken) {
            throw new AssertionError("テスト用 YAML が壊れている", broken);
        }
        return yaml.getConfigurationSection("entry");
    }

    @Test
    @DisplayName("transfer-multiplier未設定は1.0(無印と同じ挙動)")
    void unsetFallsBackToOne() {
        List<String> warnings = new ArrayList<>();
        ConfigurationSection sec = sectionOf("""
                entry:
                  material: FURNACE
                """);

        double result = SourcelinkConfig.readTransferMultiplier(sec, "volcanic_sourcelink",
                loggerCapturing(warnings));

        assertEquals(1.0, result, 1e-9);
        assertTrue(warnings.isEmpty(), "既定値の解決で警告が出てはいけない: " + warnings);
    }

    @Test
    @DisplayName("正の値はそのまま使われる(II=2.0/III=4.0の実例)")
    void positiveValuesPassThrough() {
        List<String> warnings = new ArrayList<>();
        ConfigurationSection sec = sectionOf("""
                entry:
                  transfer-multiplier: 2.0
                """);

        double result = SourcelinkConfig.readTransferMultiplier(sec, "volcanic_sourcelink_ii",
                loggerCapturing(warnings));

        assertEquals(2.0, result, 1e-9);
        assertTrue(warnings.isEmpty());
    }

    @Test
    @DisplayName("0以下は転送を止めてしまうので1.0へフォールバックし、警告を出す")
    void nonPositiveValueFallsBackWithWarning() {
        List<String> warnings = new ArrayList<>();
        ConfigurationSection sec = sectionOf("""
                entry:
                  transfer-multiplier: 0
                """);

        double result = SourcelinkConfig.readTransferMultiplier(sec, "broken_link",
                loggerCapturing(warnings));

        assertEquals(1.0, result, 1e-9);
        assertEquals(1, warnings.size(), "クランプは黙って行わない: " + warnings);
        assertTrue(warnings.get(0).contains("broken_link"), "どのidが壊れているか名指しすること");
    }

    @Test
    @DisplayName("負の値も1.0へフォールバックする")
    void negativeValueFallsBackWithWarning() {
        List<String> warnings = new ArrayList<>();
        ConfigurationSection sec = sectionOf("""
                entry:
                  transfer-multiplier: -5.0
                """);

        double result = SourcelinkConfig.readTransferMultiplier(sec, "broken_link",
                loggerCapturing(warnings));

        assertEquals(1.0, result, 1e-9);
        assertEquals(1, warnings.size());
    }

    @Test
    @DisplayName("yield-multiplier未設定は1.0(既存の全ソースリンクの生成量が変わらない)")
    void yieldMultiplierUnsetFallsBackToOne() {
        List<String> warnings = new ArrayList<>();
        ConfigurationSection sec = sectionOf("""
                entry:
                  material: FURNACE
                """);

        double result = SourcelinkConfig.readYieldMultiplier(sec, "volcanic_sourcelink",
                loggerCapturing(warnings));

        assertEquals(1.0, result, 1e-9);
        assertTrue(warnings.isEmpty(), "既定値の解決で警告が出てはいけない: " + warnings);
    }

    @Test
    @DisplayName("yield-multiplier は transfer-multiplier と独立に読まれる(片方だけの設定が効く)")
    void yieldAndTransferMultipliersAreIndependent() {
        List<String> warnings = new ArrayList<>();
        ConfigurationSection sec = sectionOf("""
                entry:
                  transfer-multiplier: 4.0
                  yield-multiplier: 1.5
                """);
        Logger logger = loggerCapturing(warnings);

        assertEquals(4.0, SourcelinkConfig.readTransferMultiplier(sec, "x", logger), 1e-9);
        assertEquals(1.5, SourcelinkConfig.readYieldMultiplier(sec, "x", logger), 1e-9);
        assertTrue(warnings.isEmpty());
    }

    @Test
    @DisplayName("yield-multiplierの0以下は1.0へフォールバックし、キー名を名指しで警告する")
    void yieldMultiplierNonPositiveFallsBackWithWarning() {
        List<String> warnings = new ArrayList<>();
        ConfigurationSection sec = sectionOf("""
                entry:
                  yield-multiplier: 0
                """);

        double result = SourcelinkConfig.readYieldMultiplier(sec, "broken_link",
                loggerCapturing(warnings));

        assertEquals(1.0, result, 1e-9);
        assertEquals(1, warnings.size(), "クランプは黙って行わない: " + warnings);
        assertTrue(warnings.get(0).contains("yield-multiplier"),
                "どのキーが壊れているか名指しすること(transfer側と共通実装なので取り違えが起きうる): "
                        + warnings.get(0));
        assertTrue(warnings.get(0).contains("broken_link"), "どのidが壊れているか名指しすること");
    }

    private static Logger loggerCapturing(List<String> warnings) {
        Logger logger = Logger.getLogger("SourcelinkConfigTest");
        logger.setUseParentHandlers(false);
        for (java.util.logging.Handler h : logger.getHandlers()) {
            logger.removeHandler(h);
        }
        logger.addHandler(new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logger;
    }
}
