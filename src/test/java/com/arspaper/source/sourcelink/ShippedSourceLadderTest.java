package com.arspaper.source.sourcelink;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出荷 {@code sourcelinks.yml} / {@code sourcejars.yml} のソース階梯そのものを固定する(2026-08-03)。
 *
 * <p>{@link SourcelinkConfigTest} はキー1本のパースを見るだけで、「出荷configの階梯が
 * 実際に成立しているか」は誰も見ていなかった。ここで固定するのは次の4点:
 *
 * <ol>
 *   <li><b>2つの倍率が段ごとに単調増加</b> —— 上位が下位より遅い/実入りが少ない、という
 *       気づきにくい逆転を止める。</li>
 *   <li><b>無印5種は転送倍率を書かない</b> —— レートは既定1.0のままにして「階梯を入れたら
 *       無印のレートが変わった」を防ぐ。
 *       ⚠ 2026-08-24 に<b>生成量だけ</b>は無印にも書くようになった(階梯 tier1 = 2.0)。
 *       生成量倍率を 2 の階梯乗へ張り替えるユーザー指示によるもので、
 *       「無印は倍率キーを一切持たない」という元の不変条件はここで意図的に緩めている。</li>
 *   <li><b>レシピの (core-item, pedestal-items) の組が全体で一意</b> —— 重複すると後から
 *       登録した段が {@code findFirst} に負けて<b>永久にクラフト不可</b>になる
 *       ({@code docs/agent-context/common-traps.md})。</li>
 *   <li><b>表示名に数字の段表記(II/III/…)を使わない</b> —— K の要件「数字表記の tier を
 *       固有の名前にする」。id は据え置き(変えるとレシピと設置済みブロックのPDCが切れる)なので、
 *       ここで縛れるのは display-name だけ。</li>
 * </ol>
 */
class ShippedSourceLadderTest {

    private static final List<String> TYPES =
            List.of("volcanic", "mycelial", "alchemical", "vitalic", "botanical");
    /**
     * 段の並び。core-item はこの順で前段を指す必要がある。
     *
     * <p>2026-08-23 (W-189) に 4段 → 8段へ細分化した。id が {@code ii_b} のような枝番なのは、
     * <b>既存 id を詰め直せない</b>ため —— 変えると設置済みブロックの PDC と、
     * 他段の {@code core-item: custom:..._iii} が全部切れる。
     */
    private static final List<String> TIERS =
            List.of("ii", "ii_b", "iii", "iii_b", "iv", "iv_b", "v", "vi");

    /**
     * 無印(階梯 tier1)の生成量倍率。2026-08-24 にユーザー指示で生成量倍率を
     * <b>2 の階梯乗</b>へ張り替えたときの起点で、以降 _ii=4 … _vi=512 と倍々になる。
     */
    private static final double BASE_YIELD = 2.0;

    /** 「ソースジャー II」「炉 III」のような数字の段表記(全角/半角のローマ数字も含む)。 */
    private static final Pattern NUMERIC_TIER =
            Pattern.compile("(?:\\s|^)(?:[IVX]{1,4}|[ⅠⅡⅢⅣⅤ]|[0-9]+)\\s*$");

    private static ConfigurationSection items() {
        File file = new File("src/main/resources/sourcelinks.yml");
        assertTrue(file.isFile(), "出荷 sourcelinks.yml が見つからない: " + file.getAbsolutePath());
        ConfigurationSection items = YamlConfiguration.loadConfiguration(file).getConfigurationSection("items");
        assertNotNull(items, "sourcelinks.yml に items: が無い");
        return items;
    }

    @Test
    @DisplayName("5種すべてに階梯の全段があり、core-item が前段を指す")
    void everyTypeHasTheFullLadderChainedToItsPreviousTier() {
        ConfigurationSection items = items();
        for (String type : TYPES) {
            String base = type + "_sourcelink";
            assertTrue(items.isConfigurationSection(base), base + " (無印) が無い");
            for (int i = 0; i < TIERS.size(); i++) {
                String id = base + "_" + TIERS.get(i);
                ConfigurationSection entry = items.getConfigurationSection(id);
                assertNotNull(entry, id + " が無い(5種 x " + TIERS.size() + "段が揃っている必要がある)");
                String expectedCore = "custom:" + base + (i == 0 ? "" : "_" + TIERS.get(i - 1));
                assertEquals(expectedCore, entry.getString("recipe.core-item"),
                        id + " の core-item は前段でなければならない"
                                + "(飛び級できると階梯の意味が消える)");
                assertEquals("custom:" + id, entry.getString("recipe.result"),
                        id + " のレシピ結果が自分自身になっていない"
                                + "(materials.yml で実際に起きた「自分に戻る破壊レシピ」と同型の事故)");
            }
        }
    }

    @Test
    @DisplayName("転送レート倍率と生成量倍率が段ごとに単調増加する(上位が下位に負けない)")
    void bothMultipliersIncreaseMonotonicallyAlongTheLadder() {
        ConfigurationSection items = items();
        for (String type : TYPES) {
            double prevTransfer = 1.0;
            // 生成量の起点は無印(tier1)。1.0 から始めると「無印より弱い _ii」を見逃す。
            double prevYield = items.getDouble(type + "_sourcelink.yield-multiplier", 1.0);
            for (String tier : TIERS) {
                String id = type + "_sourcelink_" + tier;
                ConfigurationSection entry = items.getConfigurationSection(id);
                assertNotNull(entry, id + " が無い");
                assertTrue(entry.isSet("transfer-multiplier"),
                        id + " に transfer-multiplier が無い(未設定=1.0で無印と同じレートになる)");
                assertTrue(entry.isSet("yield-multiplier"),
                        id + " に yield-multiplier が無い(未設定=1.0で素材効率が無印と同じになる。"
                                + "これが 2026-08-03 に直した「階梯で供給量が増えない」問題そのもの)");
                double transfer = entry.getDouble("transfer-multiplier");
                double yield = entry.getDouble("yield-multiplier");
                assertTrue(transfer > prevTransfer,
                        id + " の transfer-multiplier(" + transfer + ") が前段(" + prevTransfer + ")を超えていない");
                assertTrue(yield > prevYield,
                        id + " の yield-multiplier(" + yield + ") が前段(" + prevYield + ")を超えていない");
                prevTransfer = transfer;
                prevYield = yield;
            }
        }
    }

    @Test
    @DisplayName("無印5種は転送倍率を書かず、生成倍率だけ 2.0 を持つ(2026-08-24 の 2^tier 化)")
    void baseSourcelinksCarryOnlyTheYieldMultiplier() {
        ConfigurationSection items = items();
        for (String type : TYPES) {
            ConfigurationSection entry = items.getConfigurationSection(type + "_sourcelink");
            assertNotNull(entry);
            assertTrue(!entry.isSet("transfer-multiplier"),
                    type + "_sourcelink (無印) には transfer-multiplier を書かない"
                            + "(既定1.0を明示すると『無印のレートを触った』差分に見えてしまう。"
                            + "レート側は 2026-08-24 の生成量改定でも据え置き)");
            assertEquals(BASE_YIELD, entry.getDouble("yield-multiplier"), 1e-9,
                    type + "_sourcelink (無印) の yield-multiplier が " + BASE_YIELD + " ではない。"
                            + "無印は階梯 tier1 として 2^1 を持つのが 2026-08-24 の指示"
                            + "(この値を落とすと無印だけ 1.0 に戻り、階梯の最下段が抜ける)");
        }
    }

    @Test
    @DisplayName("生成量倍率が段ごとにちょうど2倍(=2^tier)になっている")
    void yieldMultiplierDoublesAtEveryRung() {
        ConfigurationSection items = items();
        for (String type : TYPES) {
            double expected = BASE_YIELD;
            String base = type + "_sourcelink";
            assertEquals(expected, items.getDouble(base + ".yield-multiplier"), 1e-9,
                    base + " (無印) の生成量倍率がずれている");
            for (String tier : TIERS) {
                expected *= 2.0;
                String id = base + "_" + tier;
                assertEquals(expected, items.getDouble(id + ".yield-multiplier"), 1e-9,
                        id + " の生成量倍率が 2^tier からずれている。"
                                + "1段でも外すと階梯全体の伸びが崩れる(上限は _vi の 512.0)");
            }
        }
    }

    @Test
    @DisplayName("(core-item, pedestal-items) の組が全ソースリンクで一意(後発が永久クラフト不可にならない)")
    void everyRecipeSignatureIsUnique() {
        ConfigurationSection items = items();
        Map<String, String> seen = new HashMap<>();
        List<String> collisions = new ArrayList<>();
        for (String id : items.getKeys(false)) {
            ConfigurationSection entry = items.getConfigurationSection(id);
            if (entry == null || !entry.isConfigurationSection("recipe")) continue;
            List<String> pedestals = new ArrayList<>(entry.getStringList("recipe.pedestal-items"));
            pedestals.sort(String::compareTo);
            String signature = entry.getString("recipe.core-item") + " | " + pedestals;
            String previous = seen.putIfAbsent(signature, id);
            if (previous != null) {
                collisions.add(previous + " <-> " + id + " : " + signature);
            }
        }
        assertTrue(collisions.isEmpty(),
                "レシピの組が重複している(先に登録された方だけが作れて、もう片方は永久に作れない): "
                        + collisions);
    }

    @Test
    @DisplayName("表示名は全ソースリンク/ソースジャーで一意、かつ数字の段表記を含まない")
    void displayNamesAreUniqueAndFreeOfNumericTierNotation() {
        List<String> numeric = new ArrayList<>();
        java.util.Set<String> names = new LinkedHashSet<>();
        List<String> duplicates = new ArrayList<>();

        record Target(String file, String section) {}
        for (Target target : List.of(new Target("sourcelinks.yml", "items"),
                new Target("sourcejars.yml", "jars"))) {
            File file = new File("src/main/resources/" + target.file());
            assertTrue(file.isFile(), "出荷configが見つからない: " + file.getAbsolutePath());
            ConfigurationSection section =
                    YamlConfiguration.loadConfiguration(file).getConfigurationSection(target.section());
            assertNotNull(section, target.file() + " に " + target.section() + ": が無い");
            for (String id : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null) continue;
                String raw = entry.getString("display-name", "");
                assertTrue(!raw.isBlank(), target.file() + " " + id + " に display-name が無い");
                // 色記号を落としてから判定する(&4&l の後ろの文字が段表記かどうかを見たい)。
                String plain = raw.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "").trim();
                if (NUMERIC_TIER.matcher(plain).find()) {
                    numeric.add(target.file() + " " + id + " -> " + plain);
                }
                if (!names.add(plain)) {
                    duplicates.add(target.file() + " " + id + " -> " + plain);
                }
            }
        }
        assertTrue(numeric.isEmpty(),
                "数字の段表記が残っている(K要件: 数字tierは固有名にする。id は据え置きでよい): " + numeric);
        assertTrue(duplicates.isEmpty(),
                "表示名が重複している(GUI/レシピ名で見分けが付かなくなる): " + duplicates);
    }
}
