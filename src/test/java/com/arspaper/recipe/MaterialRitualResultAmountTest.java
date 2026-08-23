package com.arspaper.recipe;

import com.arspaper.ritual.RitualRecipe;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * materials.yml の儀式レシピが結果個数({@code result-amount})を反映することの回帰テスト。
 *
 * <p>2026-08-23 の実サーバ報告「クラフト儀式で個数を複数に設定しても1つしかクラフトされない
 * (例: ソースの欠片の9個同時クラフト)」の再発防止。
 *
 * <p>儀式のローダーは<b>2本</b>ある —— items.yml 等を読む {@code loadRitualFromSection} と、
 * materials.yml だけを読む {@code materialRitualFrom}。後者は {@code result-amount} を読まずに
 * <b>後方互換コンストラクタ(結果は常に1個)</b>を呼んでいたため、同じキーが片方の config でだけ
 * 効いていた。設定エディタは materials.yml の儀式にも結果個数の入力欄を出すので、
 * <b>入力できるのに警告ひとつ無く捨てられる</b>のが症状だった。
 *
 * <p>この検査は「ソース文字列の固定」ではなく<b>組み立てた {@link RitualRecipe} の値</b>を見る。
 * 実装を差し替えても、個数が反映されている限り通る。
 */
class MaterialRitualResultAmountTest {

    /** プラグイン本体は使わない経路だけを踏むので null で足りる（素材は custom: 指定で Bukkit 非依存）。 */
    private static final UnifiedRecipeLoader LOADER = new UnifiedRecipeLoader(null);

    private static ConfigurationSection parse(String yaml, String path) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (Exception invalid) {
            throw new AssertionError("テスト用 yaml が壊れている", invalid);
        }
        ConfigurationSection section = config.getConfigurationSection(path);
        assertTrue(section != null, "テスト用 yaml に " + path + " が無い");
        return section;
    }

    private static ConfigurationSection material(String... recipeLines) {
        StringBuilder sb = new StringBuilder("source_shard:\n  display_name: \"&bソースの欠片\"\n  recipe:\n");
        for (String line : recipeLines) {
            sb.append("    ").append(line).append('\n');
        }
        return parse(sb.toString(), "source_shard");
    }

    private static RitualRecipe build(String... recipeLines) {
        ConfigurationSection mat = material(recipeLines);
        return LOADER.materialRitualFrom(
                "source_shard", "source_shard", mat.getConfigurationSection("recipe"), mat);
    }

    @Test
    @DisplayName("materials.yml の儀式でも result-amount が結果個数になる(以前は常に1個)")
    void materialRitualHonorsResultAmount() {
        RitualRecipe recipe = build(
                "method: ritual",
                "core-item: custom:amethyst_shard_1x",
                "pedestal-items:",
                "  - custom:compressed_coal x4",
                "source: 900",
                "result-amount: 36");

        assertEquals(36, recipe.resultAmount(),
                "materials.yml の儀式で result-amount が捨てられている");
        // 他の項目まで巻き添えにしていないことも一緒に押さえる。
        assertEquals(900, recipe.sourceRequired());
        assertEquals("source_shard", recipe.resultId());
        assertEquals(4, recipe.pedestalItems().size(), "x4 の展開が壊れている");
    }

    @Test
    @DisplayName("result-amount 未指定なら 1 個(既定は変えない)")
    void defaultsToOneWhenUnset() {
        RitualRecipe recipe = build(
                "method: ritual",
                "core-item: custom:source_shard",
                "source: 100");

        assertEquals(1, recipe.resultAmount());
    }

    @Test
    @DisplayName("0 以下を書いても 1 個へ丸める(空スタックで無言の失敗にしない)")
    void clampsNonPositiveAmount() {
        assertEquals(1, build("method: ritual", "core-item: custom:source_shard",
                "source: 100", "result-amount: 0").resultAmount());
        assertEquals(1, build("method: ritual", "core-item: custom:source_shard",
                "source: 100", "result-amount: -5").resultAmount());
    }

    @Test
    @DisplayName("儀式ローダー2本が同じ1本の読み取りを通る")
    void bothRitualLoadersShareTheSameReader() {
        ConfigurationSection section = parse(
                "recipe:\n  method: ritual\n  result-amount: 12\n", "recipe");

        // materials.yml 側と共通の読み取り。items.yml 側(loadRitualFromSection)もこれを呼ぶ。
        assertEquals(12, UnifiedRecipeLoader.ritualResultAmount(section));
        assertEquals(12, build("method: ritual", "core-item: custom:source_shard",
                "source: 100", "result-amount: 12").resultAmount(),
                "materials.yml 側が共通の読み取りを通っていない");
    }
}
