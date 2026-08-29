package com.arspaper.source;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-257: 網（ドミニオンワンドの経路）へ送信元の階梯倍率を掛ける。
 *
 * <p>直しているのは「上位リンクにしても上位ジャーにしても網の転送が毎秒2.5点で固定」。
 * 網には階梯倍率が1つも掛かっていなかった。
 */
class NetworkTierMultiplierTest {

    @Test
    @DisplayName("ソースリンクの倍率を優先し、無ければジャーの倍率、どちらも無ければ 1.0")
    void resolvePrefersSourcelinkThenJar() {
        assertEquals(10.0, NetworkTierMultiplier.resolve(10.0, 5.0), 1e-9);
        assertEquals(5.0, NetworkTierMultiplier.resolve(null, 5.0), 1e-9);
        assertEquals(1.0, NetworkTierMultiplier.resolve(null, null), 1e-9);
    }

    @Test
    @DisplayName("壊れた倍率（0以下・非有限）は補正なしへ倒す")
    void brokenMultipliersFallBackToNone() {
        assertEquals(1.0, NetworkTierMultiplier.resolve(0.0, null), 1e-9);
        assertEquals(1.0, NetworkTierMultiplier.resolve(-3.0, null), 1e-9);
        assertEquals(1.0, NetworkTierMultiplier.resolve(Double.NaN, null), 1e-9);
        // ソースリンク側が壊れていてもジャー側が生きていればそちらを使う。
        assertEquals(5.0, NetworkTierMultiplier.resolve(Double.NaN, 5.0), 1e-9);
    }

    @Test
    @DisplayName("倍率を掛けても int を溢れさせない")
    void scaleSaturatesInsteadOfOverflowing() {
        assertEquals(1000, NetworkTierMultiplier.scale(100, 10.0));
        assertEquals(Integer.MAX_VALUE, NetworkTierMultiplier.scale(Integer.MAX_VALUE, 2.0));
        // 補正なしは素通し。
        assertEquals(100, NetworkTierMultiplier.scale(100, NetworkTierMultiplier.NONE));
    }

    @Test
    @DisplayName("出荷 sourcejars.yml の階梯は 1段ごとに x5 で並んでいる")
    void shippedJarLadderIsFiveTimesPerStep() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new File("src/main/resources/sourcejars.yml"));

        // 容量の小さい順（=階梯の順）。creative は無限なのでこの並びから外す。
        List<String> ladder = List.of(
                "source_jar", "source_jar_ii", "source_amphora", "source_jar_iii",
                "source_cistern", "source_reservoir", "source_basin", "source_vault",
                "source_abyssal_urn", "source_singularity_jar");

        List<Double> multipliers = new ArrayList<>();
        for (String id : ladder) {
            assertTrue(yaml.isConfigurationSection("jars." + id), "出荷ymlに " + id + " が無い");
            assertTrue(yaml.isSet("jars." + id + ".transfer-multiplier"),
                    id + " に transfer-multiplier が無い（網だけ低速に取り残される）");
            multipliers.add(yaml.getDouble("jars." + id + ".transfer-multiplier"));
        }

        assertEquals(1.0, multipliers.get(0), 1e-9, "最下段は補正なし");
        for (int i = 1; i < multipliers.size(); i++) {
            assertEquals(multipliers.get(i - 1) * 5.0, multipliers.get(i), 1e-6,
                    ladder.get(i) + " は1段前のちょうど5倍であること");
        }
    }

    @Test
    @DisplayName("出荷 sourcelinks.yml の転送倍率は 1段ごとに x5 で並んでいる")
    void shippedSourcelinkLadderMatchesUnifiedSequence() throws Exception {
        // 転送は W-257 の ×5。生成量だけが W-277 の 2,3,6,…,162（このテストは転送を見る）。
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new File("src/main/resources/sourcelinks.yml"));

        List<String> ladder = List.of(
                "volcanic_sourcelink", "volcanic_sourcelink_ii", "volcanic_sourcelink_ii_b",
                "volcanic_sourcelink_iii", "volcanic_sourcelink_iii_b", "volcanic_sourcelink_iv",
                "volcanic_sourcelink_iv_b", "volcanic_sourcelink_v", "volcanic_sourcelink_vi");

        List<Double> multipliers = new ArrayList<>();
        for (String id : ladder) {
            assertTrue(yaml.isSet("items." + id + ".transfer-multiplier"),
                    id + " に transfer-multiplier が無い");
            multipliers.add(yaml.getDouble("items." + id + ".transfer-multiplier"));
        }

        assertEquals(2.0, multipliers.get(0), 1e-9, "無印は 2.0");
        for (int i = 1; i < multipliers.size(); i++) {
            assertEquals(multipliers.get(i - 1) * 5.0, multipliers.get(i), 1e-6,
                    ladder.get(i) + " は1段前のちょうど5倍であること");
        }
    }
}
