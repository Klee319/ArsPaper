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
 *   <li><b>2つの倍率が 2 の階梯乗で、かつ全段で互いに一致する</b> —— 2026-08-24 の指示。
 *       無印を tier1 とみなして 2 / 4 / 8 / … / 512。
 *       ⚠ 元は「無印5種は倍率キーを一切持たない(=1.0)」を不変条件にしていたが、
 *       無印にも倍率を乗せる指示だったのでここで<b>意図的に緩めている</b>。
 *       ⚠ 2つを同率にするのは「階梯を上げても生成と排出の釣り合いを変えない」ため。
 *       片方だけ上げると、その段だけバッファが詰まる(または空になる)。</li>
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
     * 無印(階梯 tier1)の倍率。2026-08-24 にユーザー指示で<b>転送レート倍率と生成量倍率の
     * 両方</b>を 2 の階梯乗へ張り替えたときの起点で、以降 _ii=4 … _vi=512 と倍々になる。
     */
    private static final double BASE_MULTIPLIER = 2.0;

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
    @DisplayName("無印5種も階梯 tier1 として両方の倍率 2.0 を持つ(2026-08-24 の 2^tier 化)")
    void baseSourcelinksCarryTheTierOneMultipliers() {
        ConfigurationSection items = items();
        for (String key : List.of("transfer-multiplier", "yield-multiplier")) {
            for (String type : TYPES) {
                ConfigurationSection entry = items.getConfigurationSection(type + "_sourcelink");
                assertNotNull(entry);
                assertEquals(BASE_MULTIPLIER, entry.getDouble(key), 1e-9,
                        type + "_sourcelink (無印) の " + key + " が " + BASE_MULTIPLIER + " ではない。"
                                + "無印は階梯 tier1 として 2^1 を持つのが 2026-08-24 の指示"
                                + "(落とすと無印だけ 1.0 に戻り、階梯の最下段が抜ける)");
            }
        }
    }

    @Test
    @DisplayName("生成量倍率は段ごとにちょうど2倍、転送レート倍率は段ごとにちょうど5倍")
    void multipliersFollowTheirOwnLadders() {
        // 2026-08-25 (W-257): 2つの倍率を同率(x2)で伸ばすのをやめた。
        // 同率だと「生成に対して排出が何倍か」が階梯で一切変わらないので、
        // 上位リンクにしても“焼べても入り切らない”比率が永久に残る。
        // ユーザー確定要件は「割合ではなく階梯ごとに転送量を増やす(tier1=100/20tick なら
        // tier2=500/20tick)」＝転送だけ x5 にして、生成は既存の x2 のまま据え置く。
        ConfigurationSection items = items();
        for (String key : List.of("transfer-multiplier", "yield-multiplier")) {
            double step = "transfer-multiplier".equals(key) ? 5.0 : 2.0;
            for (String type : TYPES) {
                double expected = BASE_MULTIPLIER;
                String base = type + "_sourcelink";
                assertEquals(expected, items.getDouble(base + "." + key), 1e-9,
                        base + " (無印) の " + key + " がずれている");
                for (String tier : TIERS) {
                    expected *= step;
                    String id = base + "_" + tier;
                    assertEquals(expected, items.getDouble(id + "." + key), 1e-9,
                            id + " の " + key + " が 1段ごとに x" + step + " の並びからずれている。"
                                    + "1段でも外すと階梯全体の伸びが崩れる");
                }
            }
        }
    }

    @Test
    @DisplayName("転送レート倍率は生成量倍率より速く伸びる(段を上げるほど詰まりが解消される)")
    void transferOutgrowsYieldAtEveryRung() {
        // ※かつては「2つを同率にして、階梯を上げても生成と排出の釣り合いを変えない」ことを
        //   固定していた(2026-08-24)。しかしその釣り合いこそが問題で、無印でも上位でも
        //   「高価値燃料を焼べると入り切らない」比率が変わらなかった(K-16 の残り)。
        //   2026-08-25 (W-257) に転送だけ x5 へ変えたので、不変条件は
        //   「同率」ではなく【転送のほうが速く伸びる】へ置き換えた。
        ConfigurationSection items = items();
        for (String type : TYPES) {
            List<String> ids = new ArrayList<>();
            ids.add(type + "_sourcelink");
            for (String tier : TIERS) {
                ids.add(type + "_sourcelink_" + tier);
            }
            double previousRatio = 0.0;
            for (String id : ids) {
                double transfer = items.getDouble(id + ".transfer-multiplier");
                double yield = items.getDouble(id + ".yield-multiplier");
                assertTrue(transfer >= yield,
                        id + " の転送レート倍率が生成量倍率を下回っている。"
                                + "下回るとその段は生成が排出を追い越して永久にバッファが詰まる");
                double ratio = transfer / yield;
                assertTrue(ratio >= previousRatio,
                        id + " で「排出÷生成」の比が前の段より下がっている。"
                                + "階梯を上げるほど詰まりが解消される並びであること");
                previousRatio = ratio;
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
