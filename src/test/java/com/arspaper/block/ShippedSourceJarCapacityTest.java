package com.arspaper.block;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出荷 {@code sourcejars.yml} の容量そのものを固定する(2026-08-08)。
 *
 * <p>{@link ShippedSourceJarCapacityTest} が見るのは、コードと yml が食い違ったときに
 * <b>「設定が読めたときだけ正しく動く」= 再現しない不具合</b>になる2点:
 *
 * <ol>
 *   <li>{@link SourceJarConfig#FALLBACK_CAPACITY} が出荷 yml の {@code source_jar} の容量と一致する。
 *       ズレていると、設定の読み込みに失敗した鯖／ジャーidが引けない旧ブロックだけが
 *       別容量で動く。2026-08-08 に基本ジャーを 10,000 → 20,000 にしたとき、
 *       定数側を直し忘れると実際にこの状態になった。</li>
 *   <li>階梯の容量が下から上へ<b>狭義単調増加</b>する。基本ジャーの容量は
 *       「TF カタログ儀式の中央値をまかなえるか」というバランス都合で動かす値なので、
 *       上げすぎると上位ジャーを追い越して「上位に替えると容量が減る」逆転になる。</li>
 * </ol>
 */
class ShippedSourceJarCapacityTest {

    /**
     * 階梯の並び。creative_source_jar は容量 -1(無限)なのでここには入れない。
     *
     * <p>⚠ <b>この一覧に段を足し忘れると、その段だけ検査から丸ごと外れる</b>。実際 2026-08-16 に
     * 足した {@code source_vault} は 2026-08-23 まで1件も検査されていなかった
     * ({@code docs/agent-context/common-traps.md} の「許可リスト方式のテストはリスト自体が誤ると
     * 検査ごと無効化される」)。段を足したらここへも足すこと。
     */
    private static final List<String> LADDER = List.of(
            "source_jar", "source_jar_ii", "source_amphora", "source_jar_iii", "source_cistern",
            "source_reservoir", "source_basin", "source_vault", "source_abyssal_urn",
            "source_singularity_jar");

    private static ConfigurationSection jars() {
        File file = new File("src/main/resources/sourcejars.yml");
        assertTrue(file.isFile(), "出荷 sourcejars.yml が見つからない: " + file.getAbsolutePath());
        ConfigurationSection jars = YamlConfiguration.loadConfiguration(file).getConfigurationSection("jars");
        assertNotNull(jars, "sourcejars.yml に jars: が無い");
        return jars;
    }

    private static int capacityOf(ConfigurationSection jars, String id) {
        ConfigurationSection jar = jars.getConfigurationSection(id);
        assertNotNull(jar, "sourcejars.yml に jars." + id + " が無い");
        assertTrue(jar.isInt("capacity"), "jars." + id + ".capacity が整数で書かれていない");
        return jar.getInt("capacity");
    }

    @Test
    @DisplayName("FALLBACK_CAPACITY は出荷 source_jar の容量と一致する")
    void theHardCodedFallbackMatchesTheShippedBaseJarCapacity() {
        assertEquals(capacityOf(jars(), "source_jar"), SourceJarConfig.FALLBACK_CAPACITY,
                "SourceJarConfig.FALLBACK_CAPACITY と sourcejars.yml の source_jar.capacity がズレている。"
                        + " 設定が読めない時だけ容量が変わる = 再現しない不具合になる");
    }

    @Test
    @DisplayName("階梯の容量は下から上へ狭義単調増加する")
    void theJarLadderCapacitiesStrictlyIncrease() {
        ConfigurationSection jars = jars();
        List<Integer> capacities = new ArrayList<>();
        for (String id : LADDER) {
            capacities.add(capacityOf(jars, id));
        }
        for (int i = 1; i < LADDER.size(); i++) {
            assertTrue(capacities.get(i) > capacities.get(i - 1),
                    LADDER.get(i) + "(" + capacities.get(i) + ") が "
                            + LADDER.get(i - 1) + "(" + capacities.get(i - 1) + ") 以下になっている。"
                            + " 上位に替えると容量が減る逆転はプレイヤーから見て意味不明な退化になる");
        }
    }

    @Test
    @DisplayName("階梯は一本道 —— 各段の core-item が前段を指し、飛び級できない")
    void everyJarTierIsChainedToItsPredecessor() {
        ConfigurationSection jars = jars();
        // source_jar だけは作業台レシピ(前段が無い)なので 1 番目から見る。
        for (int i = 1; i < LADDER.size(); i++) {
            String id = LADDER.get(i);
            ConfigurationSection jar = jars.getConfigurationSection(id);
            assertNotNull(jar, "sourcejars.yml に jars." + id + " が無い");
            assertEquals("custom:" + LADDER.get(i - 1), jar.getString("recipe.core-item"),
                    id + " の core-item が前段(" + LADDER.get(i - 1) + ")を指していない。"
                            + " 段を挿し込んだのに次段の core-item を付け替え忘れると、"
                            + " 挿し込んだ段が誰からも参照されない飛び地になる");
            assertEquals("custom:" + id, jar.getString("recipe.result"),
                    id + " のレシピ結果が自分自身になっていない"
                            + "(materials.yml で実際に起きた「自分に戻る破壊レシピ」と同型の事故)");
        }
    }

    @Test
    @DisplayName("クリエイティブジャーだけが無限(-1)で、他は正の有限容量")
    void onlyTheCreativeJarIsInfinite() {
        ConfigurationSection jars = jars();
        assertEquals(-1, capacityOf(jars, "creative_source_jar"),
                "creative_source_jar は無限(-1)であること");
        for (String id : LADDER) {
            assertTrue(capacityOf(jars, id) > 0,
                    id + " の容量が正の有限値でない。0以下だと置いた瞬間に常時満杯のジャーになる");
        }
    }
}
