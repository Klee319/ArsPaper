package com.arspaper.ritual;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * U1/N6: 儀式が消費した素材を TrinityForge の素材表と同じ語彙で渡していることの回帰テスト。
 *
 * <p>儀式EXPは長らく定額（{@code ars-smithing.exp-per-craft}）で、
 * 「ネザライト級を溶かす儀式」と「石を並べる儀式」が同じEXPだった。
 * 素材ごとにするには、まず<b>消費素材を config と同じトークンで渡す</b>必要がある。
 */
class RitualMaterialTokenTest {

    private static List<String> tokensOf(RitualIngredient core, RitualIngredient... pedestals) {
        List<String> tokens = new ArrayList<>();
        RitualManager.addMaterialToken(tokens, core);
        for (RitualIngredient ingredient : pedestals) {
            RitualManager.addMaterialToken(tokens, ingredient);
        }
        return tokens;
    }

    @Test
    @DisplayName("カスタム素材は custom:<id>、バニラ素材は Material 名")
    void tokensUseTheConfigVocabulary() {
        assertEquals(List.of("custom:source_gem", "IRON_INGOT"),
                tokensOf(RitualIngredient.ofCustom("source_gem"),
                        new RitualIngredient("IRON_INGOT", false)));
    }

    @Test
    @DisplayName("同じ素材を複数台座に置いたら個数ぶん要素が並ぶ(合計が個数に比例する)")
    void duplicatesAreKeptAsSeparateElements() {
        assertEquals(List.of("custom:source_gem", "custom:source_gem", "custom:source_gem"),
                tokensOf(RitualIngredient.ofCustom("source_gem"),
                        RitualIngredient.ofCustom("source_gem"),
                        RitualIngredient.ofCustom("source_gem")));
    }

    @Test
    @DisplayName("コア無し/空IDは黙って落とす(空トークンで表を引かない)")
    void blankIngredientsAreDropped() {
        assertEquals(List.of("IRON_INGOT"),
                tokensOf(null, new RitualIngredient("IRON_INGOT", false),
                        new RitualIngredient("  ", true),
                        new RitualIngredient(null, false)));
    }

    @Test
    @DisplayName("儀式完了時に素材トークンを TrinityForge へ渡している")
    void ritualCompletionForwardsTheTokens() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/arspaper/ritual/RitualManager.java"));
        assertTrue(source.contains("consumedMaterialTokens(recipe)"),
                "消費素材を集めていない");
        assertTrue(source.contains("finalizeCatalogRitualResult(result, player, consumedTokens)"),
                "TFカタログ儀式が定額のまま(素材を渡していない)");
        // 旧実装にも finalizeArsSmithingResult( はあったので、「呼んでいるか」では何も守れない。
        // 素材トークンを渡しているか(=consumedTokens が引数に入っているか)を見る。
        // 改行位置に依存しないよう連続空白を1つに潰してから照合する。
        String flattened = source.replaceAll("\\s+", " ");
        assertTrue(flattened.contains("finalizeArsSmithingResult( result, player, consumedTokens)")
                        || flattened.contains("finalizeArsSmithingResult(result, player, consumedTokens)"),
                "Ars カスタム儀式が定額のまま(素材トークンを渡していない)");
    }
}
