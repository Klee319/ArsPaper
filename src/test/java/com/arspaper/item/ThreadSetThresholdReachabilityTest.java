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
 *       {@code threads.yml} の {@code max}(= 装備1点あたりの上限。{@code stackable}/{@code max}
 *       未指定なら {@link ThreadApplicationPolicy#DEFAULT_MAX_STACK}。2026-08-18 に既定が
 *       「重複不可(=1本)」から「重複可」へ反転した)
 *       × キャリア数で頭打ちになる。キャリアは<b>着用防具4部位 + メインハンド + オフハンド</b>だが、
 *       オフハンドは TrinityForge {@code item-stats.yml} の {@code offhand-stats-apply: true} の品だけで、
 *       出荷 yml には該当が0件 → <b>実キャリア数は 5</b>。
 *       上限を超えるしきい値を書くと、そのティアは物理的に発動しない。
 *       (設計書 §3-A-5 の「2/4 段 → 3/6 段」は旧既定での上限 5 を見落としていた。
 *       2026-08-18 に重複セットを既定で許可したので上限は 2 × 5 = 10 になり、
 *       {@code role_luck} / {@code role_effeciency} の6段は到達可能な形へ戻してある。)</li>
 *   <li><b>死に値</b>。セット効果は「同種を N 枠捧げる」対価なので、最終ティアまでの累計が
 *       そのキーの<b>スレッド1本の抽選最小値</b>(TrinityForge {@code item-stats.yml} のスレッド項目
 *       (CMD帯 300000-300099、items.<MATERIAL#CMD>.random.<key>.min)が持つ値のうち、全スレッド中の
 *       最小値。2026-08-03 に、共有プール {@code random-roll-pools.thread} 方式からスレッド40件の
 *       個別定義方式へ移行した際、比較対象も「全スレッド中の最小値」へ読み替えた)にすら届かないなら、
 *       枠を1つ厳選スレッドに使ったほうが強い = セット効果を狙う理由が消える。
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

    /** スレッド用に予約された CustomModelData 帯の下限(含む)。 */
    private static final int THREAD_CMD_MIN = 300000;
    /** スレッド用に予約された CustomModelData 帯の上限(含まない)。 */
    private static final int THREAD_CMD_MAX = 300100;

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

    /**
     * 1装備あたりのスレッド枠数(安全側=厳しめの値)。
     * 出荷 TrinityForge {@code item-stats.yml} の {@code thread-slots} は 1が52件 / 2が82件 /
     * 3が52件 / 4が4件で、防具は帯ごとに 2/3/4 枠(TF 側 {@code ShippedThreadBandIndependenceTest}
     * の {@code BAND_SLOTS} = 8/12/16 ÷ 4部位)。ここでは最頻値かつ最低帯の値である 2 を使う
     * ── 大きく取ると「到達可能」と誤判定してしまうため。
     */
    private static final int SLOTS_PER_ITEM = 2;

    /**
     * スレッド種別 → 1装備あたりの装着上限。
     *
     * <p>2026-08-18: 既定が {@link ThreadApplicationPolicy#DEFAULT_STACKABLE}(=重複可)へ
     * 反転したので、未記載は「1個まで」ではなく {@link ThreadApplicationPolicy#DEFAULT_MAX_STACK}
     * になる。さらに1装備に挿せるのはスレッド枠の数までなので {@link #SLOTS_PER_ITEM} で押さえる。
     */
    private static Map<String, Integer> perItemLimits() {
        ConfigurationSection threads = section(load("threads.yml"), "threads", "threads.yml の threads:");
        Map<String, Integer> limits = new LinkedHashMap<>();
        for (String id : threads.getKeys(false)) {
            ConfigurationSection entry = threads.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            if (!entry.getBoolean("stackable", ThreadApplicationPolicy.DEFAULT_STACKABLE)) {
                limits.put(id, 1);
            } else {
                int max = entry.getInt("max", ThreadApplicationPolicy.DEFAULT_MAX_STACK);
                limits.put(id, Math.min(max, SLOTS_PER_ITEM));
            }
        }
        return limits;
    }

    /**
     * ステキー → 全スレッド中の抽選最小値(そのキーを {@code random:} に持つスレッド全体での最小)。
     *
     * <p>2026-08-03: スレッド厳選は「共有プール1本({@code random-roll-pools.thread.main-stats})」
     * 方式から「スレッド40件それぞれが {@code item-stats.yml} に個別の {@code per-quality}/
     * {@code random} を持つ」方式へ移行した(依頼:「専用GUI/専用configを作るな、武器と同じ
     * item-stats仕様でスレッドも個別にステータス定義しろ」)。共有の「主ステ抽選テーブル」という
     * 概念が無くなったため、この比較は「そのステキーを持つ全スレッド項目の {@code random.min} の
     * うち最小値」で代用する(死に値判定を安全側=厳しめに倒す: 最も緩いスレッドの最小値と比べる)。
     * このフォークのリソースには厳選定義がもう存在しないため、TF 本体の出荷 yml を
     * リポジトリ相対パスで直接読む。
     */
    private static Map<String, Double> mainStatMinimums() {
        File file = Path.of("../../../TrinityForge/src/main/resources/stats/item-stats.yml").toFile();
        assertTrue(file.isFile(), "TrinityForge 本体の item-stats.yml が見つからない: " + file.getAbsolutePath());
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection items = config.getConfigurationSection("items");
        assertNotNull(items, "item-stats.yml の items: が見つからない");
        Map<String, Double> minimums = new LinkedHashMap<>();
        for (String key : items.getKeys(false)) {
            if (!isThreadItemKey(key)) {
                continue;
            }
            ConfigurationSection random = items.getConfigurationSection(key + ".random");
            if (random == null) {
                continue;
            }
            for (String stat : random.getKeys(false)) {
                if (!random.contains(stat + ".min")) {
                    continue;
                }
                double min = random.getDouble(stat + ".min");
                minimums.merge(stat, min, Math::min);
            }
        }
        assertFalse(minimums.isEmpty(),
                "item-stats.yml にスレッド項目(CMD " + THREAD_CMD_MIN + "-" + (THREAD_CMD_MAX - 1)
                        + ")の random: 定義が1件も見つからない");
        return minimums;
    }

    /** {@code MATERIAL#CMD} キーがスレッド用 CustomModelData 帯かどうか。 */
    private static boolean isThreadItemKey(String key) {
        int hash = key.lastIndexOf('#');
        if (hash < 0) {
            return false;
        }
        try {
            int cmd = Integer.parseInt(key.substring(hash + 1).trim());
            return cmd >= THREAD_CMD_MIN && cmd < THREAD_CMD_MAX;
        } catch (NumberFormatException notNumeric) {
            return false;
        }
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
    @DisplayName("最終ティアまでの累計は、そのキーの抽選最小値(全スレッド中の最小)以上ある(死に値の禁止)")
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
            // 2026-08-18: 負の累計は「意図した代償」で、死に値ではない。
            // translate(mana-bonus -100 と引き換えに mana-regen +10)や
            // blindness(max-health -10 と引き換えに attack-power +250)のような
            // ハイリスク・ハイリターン型のセットがあるため、抽選最小値との比較を
            // そのまま当てると**設計どおりの代償を不具合として報告してしまう**。
            // 代わりに「代償があるなら見返りもあること」を縛る(純粋な下方修正セットは通さない)。
            boolean hasDrawback = cumulative.values().stream().anyMatch(v -> v < 0);
            if (hasDrawback) {
                assertTrue(cumulative.values().stream().anyMatch(v -> v > 0),
                        threadId + " は負の効果しか持たない(代償だけで見返りが無いセットは成立しない): "
                                + cumulative);
            }
            cumulative.forEach((stat, total) -> {
                if (total <= 0) {
                    return; // 意図した代償(上の hasDrawback で見返りの有無を担保している)。
                }
                Double rollMin = mainMinimums.get(stat);
                if (rollMin == null) {
                    return; // 厳選の抽選候補に無いキー(比較対象が無い)は対象外。
                }
                assertTrue(total >= rollMin,
                        threadId + " の " + stat + " 累計 " + total + " は抽選1本の最小値(全スレッド中最小) "
                                + rollMin + " 未満 = 死に値(枠を厳選スレッドに使ったほうが強い)");
            });
        }
    }

    /**
     * TrinityForge の戦闘ステ語彙(攻撃系・防御系・最大体力)。
     * TF 側の {@code StatVocabulary} を参照できない(フォークは TF を compileOnly でしか見ない
     * うえ、テストは yml だけを読む)ので、ここに書き写している。増やしたときは
     * TF の {@code ShippedThreadItemStatsTest#nonCombatThreadsCarryNoCombatStats} と対で直すこと。
     */
    private static final java.util.Set<String> COMBAT_STATS = java.util.Set.of(
            "attack-power", "flat-bonus-damage", "percent-bonus-damage", "crit-chance", "crit-damage",
            "penetration", "damage-modifier", "fixed-damage", "bleed-chance", "bleed-damage",
            "bleed-damage-rate",
            "phys-resistance", "magic-resistance", "flat-defense", "phys-flat-defense",
            "magic-flat-defense", "damage-reduction", "dodge-chance", "armor-defense-rate",
            "armor-strength",
            "max-health");

    /**
     * 戦闘そのものが正体のスレッド = 戦闘ステを持ってよい側。
     * 既存6種(修復の肉/棘/昏倒/速攻/射手/盲目)＋ 2026-08-21 に新設した戦闘系24種。
     */
    private static final java.util.Set<String> COMBAT_THREADS = java.util.Set.of(
            "mending_flesh", "thorn", "concussion", "swiftcast", "marksman", "blindness",
            "swordsman", "berserker", "lancer", "reaper", "crusher", "assassin", "duelist",
            "titan", "greatsword", "longshot", "tidecaller", "earthshaker", "bolter", "spellblade",
            "onslaught", "precision", "execution", "piercer", "hemorrhage", "bulwark", "aegis",
            "ironhide", "evasion", "resilience");

    @Test
    @DisplayName("A-5: 非戦闘系スレッドのセット効果には戦闘ステが1件も無い(2026-08-21 の住み分け)")
    void nonCombatSetsCarryNoCombatStats() {
        ConfigurationSection sets = threadSets();

        // 2026-08-21 のユーザー指示:
        //   「非戦闘系効果のスレッド(常時効果系含む)から戦闘関連ステータスの効果を削除」
        // それまでは 移動速度・暗視・耐火・村の英雄・体力増強といった常時効果系のスレッドが
        // セット効果で会心率や耐性を配っていた。常時効果を目当てに着けた枠が
        // 「実は戦闘用の枠でもある」状態で、戦闘系スレッドを新設しても住み分けができない。
        //
        // ここで固定するのは個々の数値ではなく【住み分け】。数値を書き写すと
        // バランス調整のたびにテストを書き換えることになり、守れるものが残らない。
        java.util.List<String> offenders = new java.util.ArrayList<>();
        for (String threadId : sets.getKeys(false)) {
            if (COMBAT_THREADS.contains(threadId)) {
                continue;
            }
            ConfigurationSection thresholds = sets.getConfigurationSection(threadId + ".thresholds");
            if (thresholds == null) {
                continue;
            }
            for (String countKey : thresholds.getKeys(false)) {
                ConfigurationSection stats = thresholds.getConfigurationSection(countKey);
                if (stats == null) {
                    continue;
                }
                for (String stat : stats.getKeys(false)) {
                    if (COMBAT_STATS.contains(stat)) {
                        offenders.add(threadId + ".thresholds." + countKey + "." + stat);
                    }
                }
            }
        }
        assertEquals(java.util.List.of(), offenders,
                "非戦闘系スレッドのセット効果に戦闘ステが残っている(常時効果の枠が戦闘枠を兼ねてしまう)");
    }

    @Test
    @DisplayName("A-5b: 戦闘系スレッドのセット効果は空になっていない(剥がしすぎの検出)")
    void combatSetsAreNotEmptied() {
        ConfigurationSection sets = threadSets();

        // 上の住み分けテストは「非戦闘系に戦闘ステが無いこと」しか見ないので、
        // 全部消しても緑になる。剥がす側の走査が広がりすぎたときに気づけるよう、
        // 戦闘系スレッドがセット効果を持っていることを対で縛る。
        //
        // ここで「戦闘ステ(COMBAT_STATS)を持つこと」まで要求してはいけない。既存6種のうち
        // 修復の肉(health-regen-bonus) / 棘(reflect-percent) / 昏倒(stun-chance) /
        // 速攻(cooldown-reduction) / 射手(ammo-save-chance) は、戦闘用でありながら
        // 【攻撃力・耐性の語彙に属さない専用ステ】がそのスレッドの正体なので、
        // 語彙で縛ると「正しいのに落ちる」検査になる。
        java.util.List<String> empty = new java.util.ArrayList<>();
        for (String threadId : COMBAT_THREADS) {
            ConfigurationSection thresholds = sets.getConfigurationSection(threadId + ".thresholds");
            if (thresholds == null || thresholds.getKeys(false).isEmpty()) {
                empty.add(threadId + ": セット効果が無い");
                continue;
            }
            boolean hasAnyStat = false;
            for (String countKey : thresholds.getKeys(false)) {
                ConfigurationSection stats = thresholds.getConfigurationSection(countKey);
                hasAnyStat |= stats != null && !stats.getKeys(false).isEmpty();
            }
            if (!hasAnyStat) {
                empty.add(threadId + ": しきい値はあるがステが1件も無い");
            }
        }
        assertEquals(java.util.List.of(), empty,
                "戦闘系スレッドのセット効果が空 = 非戦闘系から剥がす走査が広がりすぎている");
    }

    @Test
    @DisplayName("A-5c: 攻撃力のセット効果は乗算モードで書く(固定値は低帯だけ極端に強くなる)")
    void attackPowerSetsUseMultiplyMode() {
        ConfigurationSection sets = threadSets();

        // 2026-08-21 のユーザー指示で乗算モード({ mode: multiply, value: X })を追加した。
        // attack-power は帯(進行度)で桁が変わる実数ステなので、固定値で配ると
        // Lv20 帯の最強武器(攻撃力 700 前後)をスレッド1本が上書きしてしまい、
        // TF 側 ShippedThreadBandIndependenceTest が固定している
        // 「スレッド1本ぶんのダメージ倍率は帯に依らず一定」を壊す。
        java.util.List<String> flat = new java.util.ArrayList<>();
        for (String threadId : sets.getKeys(false)) {
            ConfigurationSection thresholds = sets.getConfigurationSection(threadId + ".thresholds");
            if (thresholds == null) {
                continue;
            }
            for (String countKey : thresholds.getKeys(false)) {
                ConfigurationSection stats = thresholds.getConfigurationSection(countKey);
                if (stats == null || !stats.contains("attack-power")) {
                    continue;
                }
                ConfigurationSection mode = stats.getConfigurationSection("attack-power");
                if (mode == null || !"multiply".equals(mode.getString("mode"))) {
                    flat.add(threadId + ".thresholds." + countKey + ".attack-power");
                }
            }
        }
        assertEquals(java.util.List.of(), flat,
                "attack-power のセット効果が固定値のまま(低帯だけ極端に強くなる)。"
                        + "{ mode: multiply, value: 0.05 } のように割合で書くこと");
    }

    @Test
    @DisplayName("A-5d: 旧しきい値(枠合計9の時代の段)が残っていない")
    void oldThresholdTiersAreGone() {
        ConfigurationSection sets = threadSets();
        assertFalse(sets.contains("mana_regen.thresholds.6"), "mana_regen が旧 6 段のまま");
        assertFalse(sets.contains("spell_cost_down.thresholds.2"), "spell_cost_down が旧 2 段のまま");
        assertFalse(sets.contains("spell_cost_down.thresholds.4"), "spell_cost_down が旧 4 段のまま");
    }
}
