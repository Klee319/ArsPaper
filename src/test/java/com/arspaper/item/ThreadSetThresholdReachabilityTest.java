package com.arspaper.item;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code thread-sets.yml} のしきい値と数値が「書いても永久に意味を持たない」状態にならないことを固定する。
 *
 * <p>縛っている失敗は2種類ある(どちらも例外もログも出ないので、実機で気づけない):
 * <ol>
 *   <li><b>到達不能なしきい値</b>。同種スレッドの合計個数は
 *       {@code threads.yml} の {@code max}(= 装備1点あたりの上限。{@code stackable} 未指定なら1)
 *       × キャリア数で頭打ちになる。キャリアは<b>着用防具4部位 + メインハンド + オフハンド</b>だが、
 *       オフハンドは TrinityForge {@code item-stats.yml} の {@code offhand-stats-apply: true} の品だけで、
 *       出荷 yml には該当が0件 → <b>実キャリア数は 5</b>。
 *       上限を超えるしきい値を書くと、そのティアは物理的に発動しない。
 *       (設計書 §3-A-5 の「2/4 段 → 3/6 段」はこの上限 5 を見落としていた。)</li>
 *   <li><b>死に値</b>。セット効果は「同種を N 枠捧げる」対価なので、最終ティアまでの累計が
 *       そのキーの<b>主ステ抽選1本の最小値</b>({@code thread-rolls.yml} の {@code main-stats.min})に
 *       すら届かないなら、枠を1つ厳選スレッドに使ったほうが強い = セット効果を狙う理由が消える。
 *       {@code hero_of_the_village} の {@code attack-power +1.0/+2.0}(主ステ最小 200 の 1/100)が
 *       この状態だった。</li>
 * </ol>
 */
class ThreadSetThresholdReachabilityTest {

    /**
     * セット効果の個数を供給できる装備スロット数。
     * {@code ArmorManaListener#recalculateArmorBonus} が走査するのは防具4部位 + メインハンド +
     * オフハンドで、{@code ThreadApplicationPolicy#appliesNumericStats} は全スロットを true にする
     * (常時ポーション効果だけが防具限定)。オフハンドは {@code offhand-stats-apply: true} の品が
     * 出荷 item-stats.yml に0件なので数えない。
     */
    private static final int CARRIER_SLOTS = 5;

    private static YamlConfiguration load(String relative) {
        File file = Path.of("src/main/resources/" + relative).toFile();
        assertTrue(file.isFile(), "出荷 yml が見つからない: " + file.getAbsolutePath());
        return YamlConfiguration.loadConfiguration(file);
    }

    private static ConfigurationSection section(YamlConfiguration config, String key, String what) {
        ConfigurationSection out = config.getConfigurationSection(key);
        assertNotNull(out, what + " が見つからない");
        return out;
    }

    /** スレッド種別 → 1装備あたりの装着上限({@code stackable} 未指定 = 1個まで)。 */
    private static Map<String, Integer> perItemLimits() {
        ConfigurationSection threads = section(load("threads.yml"), "threads", "threads.yml の threads:");
        Map<String, Integer> limits = new LinkedHashMap<>();
        for (String id : threads.getKeys(false)) {
            ConfigurationSection entry = threads.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            if (!entry.getBoolean("stackable", false)) {
                limits.put(id, 1);
            } else {
                // max 未指定 = 無制限。その場合はキャリア数で頭打ちになるので上限扱いにする。
                limits.put(id, entry.getInt("max", CARRIER_SLOTS));
            }
        }
        return limits;
    }

    /** ステキー → 主ステ抽選の最小値({@code thread-rolls.yml})。 */
    private static Map<String, Double> mainStatMinimums() {
        ConfigurationSection mains = section(load("thread-rolls.yml"), "main-stats",
                "thread-rolls.yml の main-stats:");
        Map<String, Double> minimums = new LinkedHashMap<>();
        for (String key : mains.getKeys(false)) {
            ConfigurationSection entry = mains.getConfigurationSection(key);
            if (entry != null) {
                minimums.put(key, entry.getDouble("min"));
            }
        }
        return minimums;
    }

    private static ConfigurationSection threadSets() {
        return section(load("thread-sets.yml"), "thread-sets", "thread-sets.yml の thread-sets:");
    }

    @Test
    @DisplayName("しきい値は『1装備あたりの上限 × キャリア5』を超えない(超えたティアは物理的に発動しない)")
    void thresholdsAreReachable() {
        Map<String, Integer> limits = perItemLimits();
        ConfigurationSection sets = threadSets();
        int checked = 0;
        for (String threadId : sets.getKeys(false)) {
            ConfigurationSection thresholds = sets.getConfigurationSection(threadId + ".thresholds");
            if (thresholds == null) {
                continue;
            }
            Integer perItem = limits.get(threadId);
            assertNotNull(perItem, "thread-sets.yml の '" + threadId + "' が threads.yml に無い");
            int ceiling = perItem * CARRIER_SLOTS;
            for (String countKey : thresholds.getKeys(false)) {
                int count = Integer.parseInt(countKey);
                assertTrue(count <= ceiling,
                        threadId + " の " + count + " 段は到達不能"
                                + "(1装備あたり " + perItem + " 個 × キャリア " + CARRIER_SLOTS
                                + " = 最大 " + ceiling + " 個)");
                checked++;
            }
        }
        assertTrue(checked > 0, "しきい値が1件も無い(セット効果が全部空になっている)");
    }

    @Test
    @DisplayName("最終ティアまでの累計は、そのキーの主ステ抽選1本分(最小値)以上ある(死に値の禁止)")
    void topTierIsWorthMoreThanOneRoll() {
        Map<String, Double> mainMinimums = mainStatMinimums();
        ConfigurationSection sets = threadSets();
        for (String threadId : sets.getKeys(false)) {
            ConfigurationSection thresholds = sets.getConfigurationSection(threadId + ".thresholds");
            if (thresholds == null) {
                continue;
            }
            // 累積しきい値式: しきい値 <= N の全ティアを合算する(ThreadSetConfig#cumulativeBonus)。
            Map<String, Double> cumulative = new LinkedHashMap<>();
            for (String countKey : thresholds.getKeys(false)) {
                ConfigurationSection stats = thresholds.getConfigurationSection(countKey);
                if (stats == null) {
                    continue;
                }
                for (String stat : stats.getKeys(false)) {
                    cumulative.merge(stat, stats.getDouble(stat), Double::sum);
                }
            }
            cumulative.forEach((stat, total) -> {
                Double rollMin = mainMinimums.get(stat);
                if (rollMin == null) {
                    return; // 厳選の抽選候補に無いキー(比較対象が無い)は対象外。
                }
                assertTrue(total >= rollMin,
                        threadId + " の " + stat + " 累計 " + total + " は主ステ抽選1本の最小値 "
                                + rollMin + " 未満 = 死に値(枠を厳選スレッドに使ったほうが強い)");
            });
        }
    }

    @Test
    @DisplayName("A-5: 死に値だった3種の値と、引き上げ後のしきい値を固定する")
    void repairedSetsKeepTheirNewValues() {
        ConfigurationSection sets = threadSets();

        // 1点1個の系統 = 上限5。3/5 段(=5キャリア全部に載せて初めて最終段)。
        assertEquals(400.0, sets.getDouble("hero_of_the_village.thresholds.3.attack-power"));
        assertEquals(900.0, sets.getDouble("hero_of_the_village.thresholds.5.attack-power"));
        assertEquals(8.0, sets.getDouble("night_vision.thresholds.3.flat-bonus-damage"));
        assertEquals(18.0, sets.getDouble("night_vision.thresholds.5.flat-bonus-damage"));
        assertEquals(12.0, sets.getDouble("conduit_power.thresholds.3.magic-flat-defense"));
        assertEquals(26.0, sets.getDouble("conduit_power.thresholds.5.magic-flat-defense"));
        assertEquals(0.02, sets.getDouble("conduit_power.thresholds.5.damage-reduction"));

        // 設計書が挙げていないが同じ死に値だった2件。
        assertEquals(80.0, sets.getDouble("damage_mana_recovery.thresholds.8.bleed-damage"));
        assertEquals(12.0, sets.getDouble("health_boost.thresholds.4.phys-flat-defense"));
        assertEquals(12.0, sets.getDouble("health_boost.thresholds.8.magic-flat-defense"));

        // 旧しきい値(枠合計9の時代の値)が残っていないこと。
        assertFalse(sets.contains("mana_regen.thresholds.3"), "mana_regen が旧 3 段のまま");
        assertFalse(sets.contains("mana_regen.thresholds.6"), "mana_regen が旧 6 段のまま");
        assertFalse(sets.contains("spell_cost_down.thresholds.2"), "spell_cost_down が旧 2 段のまま");
        assertFalse(sets.contains("spell_cost_down.thresholds.4"), "spell_cost_down が旧 4 段のまま");
    }
}
