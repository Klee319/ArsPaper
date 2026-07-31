package com.arspaper.loot;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code loot-tables.yml} のパースと、ルートテーブルの当たり判定を固定する。
 *
 * <p>当たり判定を試験で縛る理由: 本番の資源サーバには構造物生成のデータパックを入れる予定で、
 * そのチェストは {@code minecraft} 以外の namespace のルートテーブルを使う。
 * 「パスの最後の要素だけ見る」実装に戻ると<b>データパックのチェストに何も入らないまま
 * 誰も気づかない</b>（例外もログも出ない）ので、3通りの書き方をここで固定する。
 */
class LootTableConfigTest {

    private static YamlConfiguration yaml(String body) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(body);
        } catch (org.bukkit.configuration.InvalidConfigurationException broken) {
            throw new AssertionError("テスト用 YAML が壊れている", broken);
        }
        return config;
    }

    private static Map<String, LootTableConfig.Pool> parse(String body, List<String> warnings) {
        return LootTableConfig.parsePools(yaml(body).getConfigurationSection("pools"), warnings::add);
    }

    @Test
    @DisplayName("バニラ指定はパスの最後の要素で当たる")
    void bareLeafMatchesVanillaTable() {
        var pools = parse("""
                pools:
                  p:
                    tables: [simple_dungeon]
                    entries:
                      - item: DIAMOND
                        chance: 1.0
                """, new ArrayList<>());
        LootTableConfig.Pool pool = pools.get("p");
        assertTrue(pool.matches("minecraft:chests/simple_dungeon"));
        assertFalse(pool.matches("minecraft:chests/village/village_armorer"));
    }

    @Test
    @DisplayName("namespace 付きの完全一致は他 namespace の同名テーブルに当たらない")
    void fullKeyMatchIsNamespaceScoped() {
        var pools = parse("""
                pools:
                  p:
                    tables: ["minecraft:chests/ancient_city"]
                    entries:
                      - item: DIAMOND
                """, new ArrayList<>());
        LootTableConfig.Pool pool = pools.get("p");
        assertTrue(pool.matches("minecraft:chests/ancient_city"));
        // 別 namespace が同じ名前のテーブルを持っていても、完全一致指定なら当たってはいけない。
        assertFalse(pool.matches("dungeons_and_taverns:chests/ancient_city"));
    }

    @Test
    @DisplayName("namespace ワイルドカードはデータパックのテーブル全部に当たる")
    void namespaceWildcardMatchesEverythingInThatNamespace() {
        var pools = parse("""
                pools:
                  p:
                    tables: ["dungeons_and_taverns:*"]
                    entries:
                      - item: DIAMOND
                """, new ArrayList<>());
        LootTableConfig.Pool pool = pools.get("p");
        assertTrue(pool.matches("dungeons_and_taverns:chests/toxic_lair/chest"));
        assertTrue(pool.matches("dungeons_and_taverns:whatever"));
        assertFalse(pool.matches("minecraft:chests/simple_dungeon"));
    }

    @Test
    @DisplayName("大文字で書いても当たる（yml の表記揺れを許す）")
    void matchingIsCaseInsensitive() {
        var pools = parse("""
                pools:
                  p:
                    tables: [Simple_Dungeon, "Dungeons_And_Taverns:*"]
                    entries:
                      - item: DIAMOND
                """, new ArrayList<>());
        LootTableConfig.Pool pool = pools.get("p");
        assertTrue(pool.matches("MINECRAFT:chests/SIMPLE_DUNGEON"));
        assertTrue(pool.matches("dungeons_and_taverns:chests/x"));
    }

    @Test
    @DisplayName("rolls は 1-16 に丸められる（桁を間違えてもチェストが埋まらない）")
    void rollsAreClamped() {
        var pools = parse("""
                pools:
                  zero:
                    tables: [a]
                    rolls: 0
                    entries: [{item: DIAMOND}]
                  huge:
                    tables: [a]
                    rolls: 9999
                    entries: [{item: DIAMOND}]
                """, new ArrayList<>());
        assertEquals(1, pools.get("zero").rolls());
        assertEquals(16, pools.get("huge").rolls());
    }

    @Test
    @DisplayName("chance と個数は正規化される")
    void entryValuesAreNormalized() {
        var pools = parse("""
                pools:
                  p:
                    tables: [a]
                    entries:
                      - item: DIAMOND
                        chance: 5.0
                        min: 0
                        max: -3
                """, new ArrayList<>());
        LootTableConfig.Entry entry = pools.get("p").entries().get(0);
        assertEquals(1.0, entry.chance());
        assertEquals(1, entry.min());
        // max < min は min まで引き上げる（nextInt(min, max+1) が例外になるのを防ぐ）。
        assertEquals(1, entry.max());
    }

    @Test
    @DisplayName("enchant-book は item 無しで通り、item 型は item 必須")
    void enchantBookNeedsNoItem() {
        List<String> warnings = new ArrayList<>();
        var pools = parse("""
                pools:
                  p:
                    tables: [a]
                    entries:
                      - type: enchant-book
                        chance: 0.05
                      - chance: 0.05
                      - type: mystery
                        item: DIAMOND
                """, warnings);
        assertEquals(1, pools.get("p").entries().size());
        assertTrue(pools.get("p").entries().get(0).enchantBook());
        assertEquals(2, warnings.size(), "item 無しの item 型と未知 type の2件が警告される: " + warnings);
    }

    @Test
    @DisplayName("tables: が空のプールは警告される（書いたのに永久に出ない状態を検知する）")
    void emptyTablesWarns() {
        List<String> warnings = new ArrayList<>();
        parse("""
                pools:
                  p:
                    entries: [{item: DIAMOND}]
                """, warnings);
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("tables"), warnings.get(0));
    }

    @Test
    @DisplayName("出荷 yml が警告ゼロでパースでき、全プールに対象テーブルと候補がある")
    void shippedYamlParsesCleanly() {
        File shipped = new File("src/main/resources/" + LootTableConfig.FILE_NAME);
        assertTrue(shipped.isFile(), "出荷 yml が見つからない: " + shipped.getAbsolutePath());
        YamlConfiguration config = YamlConfiguration.loadConfiguration(shipped);
        List<String> warnings = new ArrayList<>();
        Map<String, LootTableConfig.Pool> pools =
                LootTableConfig.parsePools(config.getConfigurationSection("pools"), warnings::add);
        assertTrue(warnings.isEmpty(), "出荷 yml が警告を出している: " + warnings);
        assertFalse(pools.isEmpty(), "出荷 yml にプールが1つも無い");
        for (LootTableConfig.Pool pool : pools.values()) {
            assertFalse(pool.tables().isEmpty(), pool.id() + " に tables: が無い");
            assertFalse(pool.entries().isEmpty(), pool.id() + " に entries: が無い");
        }
        // 移行前のハードコード挙動（対象15件にエンチャント本とエンチャント金リンゴ）が
        // 残っていることを確認する。ここが落ちたら既存プレイヤーの入手経路を消している。
        LootTableConfig.Pool legacy = pools.get("vanilla_structure_extras");
        assertTrue(legacy != null && legacy.tables().size() == 15,
                "従来の対象15件プールが失われている");
        assertTrue(legacy.entries().stream().anyMatch(LootTableConfig.Entry::enchantBook),
                "カスタムエンチャント本の候補が失われている");
        assertTrue(legacy.entries().stream().anyMatch(e -> e.item().equals("ENCHANTED_GOLDEN_APPLE")),
                "エンチャント金リンゴの候補が失われている");
    }
}
