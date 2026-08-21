package com.arspaper.item;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * セット効果の<b>乗算レイヤ</b>を固定する(2026-08-22 W-186)。
 *
 * <p>TrinityForge の合成規則は「レイヤ内は Σ(v-1) を足し、レイヤ同士は掛ける」。
 * それまでスレッドのセット効果の乗算は全部 {@code addon} という専用レイヤ1本に入っていたので、
 * <b>装備側にも同じステの倍率があると掛け算で二重に乗っていた</b>(攻撃力%が該当)。
 * {@code layer:} で TF {@code stats/lore.yml} の {@code multiplier-layers} を名指しすると、
 * 装備側の同じレイヤの中で足し算になる。
 *
 * <p>ここで縛るのは2つ:
 * <ol>
 *   <li>{@code layer:} の解釈(指定すればそのレイヤ・省略すれば {@code DEFAULT_LAYER})</li>
 *   <li><b>出荷 yml の全乗算ステが、実在するレイヤを、基準ステが一致する形で名指ししていること。</b>
 *       書き忘れても例外もログも出ずに「専用レイヤ扱い＝二重に乗る」へ落ちるので、
 *       実機で気づけない</li>
 * </ol>
 */
class ThreadSetMultiplierLayerTest {

    private static ConfigurationSection tier(String yaml) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("tier:\n" + yaml);
        ConfigurationSection section = config.getConfigurationSection("tier");
        assertNotNull(section, "テスト用 YAML の組み立てに失敗した");
        return section;
    }

    /** ステキーの表記ゆれ(大文字・アンダースコア)を吸収する。 */
    private static String norm(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    @Test
    @DisplayName("layer: を書いた乗算はそのレイヤへ、書かない乗算は既定レイヤへ入る")
    void layerKeyRoutesMultipliers() throws Exception {
        Map<String, Double> stats = new LinkedHashMap<>();
        Map<String, Map<String, Double>> multipliers = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        ThreadSetConfig.readTier(tier(""
                + "  crit-chance: 0.03\n"
                + "  attack-power: { mode: multiply, value: 0.10, layer: layer_1 }\n"
                + "  penetration: { mode: multiply, value: 0.05 }\n"), stats, multipliers, warnings::add);

        assertEquals(Map.of("crit-chance", 0.03), stats, "加算ステが乗算側へ混ざっている");
        assertEquals(Map.of(
                        "layer_1", Map.of("attack-power", 0.10),
                        ThreadSetConfig.DEFAULT_LAYER, Map.of("penetration", 0.05)),
                multipliers,
                "layer: 未指定は DEFAULT_LAYER へ、指定ありはそのレイヤへ振り分ける契約");
        assertEquals(List.of(), warnings, "正常な記述で警告が出ている");
    }

    @Test
    @DisplayName("layer: が空文字/空白のときは既定レイヤへ落ちる(空レイヤIDを作らない)")
    void blankLayerFallsBackToDefault() throws Exception {
        Map<String, Map<String, Double>> multipliers = new LinkedHashMap<>();
        ThreadSetConfig.readTier(tier("  attack-power: { mode: multiply, value: 0.10, layer: '   ' }\n"),
                new LinkedHashMap<>(), multipliers, w -> { });
        assertEquals(Map.of(ThreadSetConfig.DEFAULT_LAYER, Map.of("attack-power", 0.10)), multipliers);
    }

    @Test
    @DisplayName("加算モードの layer: は無視される(乗算側へ漏れない)")
    void layerOnAdditiveStatIsIgnored() throws Exception {
        Map<String, Double> stats = new LinkedHashMap<>();
        Map<String, Map<String, Double>> multipliers = new LinkedHashMap<>();
        ThreadSetConfig.readTier(tier("  crit-chance: { value: 0.03, layer: layer_1 }\n"),
                stats, multipliers, w -> { });
        assertEquals(Map.of("crit-chance", 0.03), stats);
        assertTrue(multipliers.isEmpty(), "mode: multiply が無いのに乗算として扱われている");
    }

    @Test
    @DisplayName("壊れたステ1件はスキップして警告し、同じ段の残りは生き残る")
    void malformedStatIsSkippedWithWarning() throws Exception {
        Map<String, Double> stats = new LinkedHashMap<>();
        Map<String, Map<String, Double>> multipliers = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        ThreadSetConfig.readTier(tier(""
                + "  broken: あいうえお\n"
                + "  crit-chance: 0.03\n"), stats, multipliers, warnings::add);
        assertEquals(Map.of("crit-chance", 0.03), stats, "1件の記述ミスで段ごと落としてはいけない");
        assertEquals(1, warnings.size(), "捨てた理由を出していない: " + warnings);
        assertTrue(warnings.get(0).contains("broken"), "警告にステ名が無い: " + warnings.get(0));
    }

    @Test
    @DisplayName("出荷 thread-sets.yml の乗算は全て、基準ステが一致する実在レイヤを名指ししている")
    void shippedMultipliersDeclareAMatchingLayer() {
        // TF の乗算レイヤ定義 (id -> 基準ステ)。
        File loreFile = Path.of("../../../TrinityForge/src/main/resources/stats/lore.yml").toFile();
        assertTrue(loreFile.isFile(), "TrinityForge の lore.yml が見つからない: " + loreFile.getAbsolutePath());
        Map<String, String> layerStat = new LinkedHashMap<>();
        for (Map<?, ?> entry : YamlConfiguration.loadConfiguration(loreFile)
                .getMapList("multiplier-layers")) {
            Object id = entry.get("id");
            if (id != null) {
                layerStat.put(String.valueOf(id), norm(String.valueOf(entry.get("stat"))));
            }
        }
        assertTrue(layerStat.containsKey("layer_1"),
                "lore.yml の multiplier-layers が読めていない: " + layerStat);

        File setsFile = Path.of("src/main/resources/thread-sets.yml").toFile();
        assertTrue(setsFile.isFile(), "出荷 thread-sets.yml が見つからない: " + setsFile.getAbsolutePath());
        ConfigurationSection root = YamlConfiguration.loadConfiguration(setsFile)
                .getConfigurationSection("thread-sets");
        assertNotNull(root, "thread-sets: が見つからない");

        List<String> offenders = new ArrayList<>();
        int checked = 0;
        for (String threadId : root.getKeys(false)) {
            ConfigurationSection thresholds = root.getConfigurationSection(threadId + ".thresholds");
            if (thresholds == null) {
                continue;
            }
            for (String count : thresholds.getKeys(false)) {
                ConfigurationSection statSection = thresholds.getConfigurationSection(count);
                if (statSection == null) {
                    continue;
                }
                for (String stat : statSection.getKeys(false)) {
                    ConfigurationSection mode = statSection.getConfigurationSection(stat);
                    if (mode == null || !"multiply".equalsIgnoreCase(mode.getString("mode", ""))) {
                        continue;
                    }
                    checked++;
                    String where = threadId + "/" + count + "/" + stat;
                    String layer = mode.getString("layer", "");
                    if (layer == null || layer.trim().isEmpty()) {
                        offenders.add(where + ": layer: が無い(専用レイヤ扱いになり装備側と掛け算で二重に乗る)");
                        continue;
                    }
                    if (!layerStat.containsKey(layer)) {
                        offenders.add(where + ": layer: '" + layer + "' が lore.yml に実在しない");
                        continue;
                    }
                    if (!layerStat.get(layer).equals(norm(stat))) {
                        offenders.add(where + ": layer: '" + layer + "' の基準ステは '"
                                + layerStat.get(layer) + "' でこのステと違う(TF 側で無言で捨てられる)");
                    }
                }
            }
        }
        assertTrue(checked > 0, "出荷 yml に乗算ステが1件も見つからない(走査が壊れている)");
        assertEquals(List.of(), offenders,
                "乗算レイヤの指定が不正: " + String.join(" / ", offenders));
    }
}
