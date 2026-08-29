package com.arspaper.recipe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-08-25: {@code items.yml} の {@code items:} に最後まで残っていた2件
 * (trident / エンチャントされた金リンゴ) を TrinityForge 側
 * {@code progression/crafting-features.yml} の {@code added-recipes:} へ移設した回帰テスト。
 *
 * <p>移設理由は enchant_book_* と同じ ── どちらもバニラアイテムを結果に持ち
 * カタログ識別を持たないため、設定エディタの items.yml 用画面(ritual_effects: しか描画しない)
 * から永久に編集できなかった。
 *
 * <p>ここで固定するのは1点だけ: <b>items.yml 側に trident / エンチャントされた金リンゴ が
 * 残っていないこと</b>。両方に定義が残っていると({@code UnifiedRecipeLoader} が
 * items: を先に読み、TF 側の added-recipes は独立した Bukkit レシピ登録なので)
 * 同じ結果アイテムに対して <b>ArsPaper 側の作業台/儀式レシピと TF 側のバニラレシピが両方
 * 生きた状態</b>になる。ArsPaper 側の儀式レシピ(エンチャントされた金リンゴ)は
 * mana:3000 を消費する別経路として残り続け、「TF 側だけ直したのに Ars 側の抜け道が
 * 生きている」という無言の二重登録になる。
 *
 * <p>TF 側で実際に読めているかどうかは
 * {@code com.trinityforge.listeners.ShippedAddedRecipesArsMigrationTest} が固定する
 * (フォークと TF 本体はビルドが分かれているため、ここからは直接検証できない)。
 */
class ItemsYmlVanillaRecipesMigratedToTfTest {

    /** items.yml の items: にもう置かないキー(このテストが復活を検知する)。 */
    private static final Set<String> MUST_NOT_BE_PRESENT = Set.of("trident", "エンチャントされた金リンゴ");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadResource(String name) throws Exception {
        try (InputStream in = ItemsYmlVanillaRecipesMigratedToTfTest.class.getClassLoader()
                .getResourceAsStream(name)) {
            assertNotNull(in, name + " が resources に無い");
            return (Map<String, Object>) new Yaml().load(in);
        }
    }

    @Test
    @DisplayName("items.yml の items: に trident / エンチャントされた金リンゴ が残っていない"
            + "(TF側 added-recipes と ArsPaper 側レシピの二重登録防止)")
    @SuppressWarnings("unchecked")
    void itemsYmlNoLongerHoldsMigratedVanillaRecipes() throws Exception {
        Map<String, Object> root = loadResource("items.yml");
        Object itemsRaw = root.get("items");
        // items: {} (空マップ) を維持している前提。null に戻す(キーごと削除する)と
        // EnchantBookDisplayOverrideTest 等「items: がマップとして存在する」前提の
        // 既存テストが道連れで壊れるので、そちらを壊さず不在だけを確認する。
        assertTrue(itemsRaw instanceof Map, "items: セクションがマップでない(空マップ {} を維持すること)");
        Map<String, Object> items = (Map<String, Object>) itemsRaw;

        for (String forbidden : MUST_NOT_BE_PRESENT) {
            assertFalse(items.containsKey(forbidden),
                    "items.yml の items: に '" + forbidden + "' が復活している。"
                    + "TrinityForge/src/main/resources/progression/crafting-features.yml の"
                    + " added-recipes: と二重登録になり、ArsPaper 側の抜け道(儀式/作業台)が"
                    + "生き残ったまま TF 側だけ直したことになる");
        }
    }
}
