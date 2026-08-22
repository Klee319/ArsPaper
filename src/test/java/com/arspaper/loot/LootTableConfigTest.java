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

    /** 出荷 yml を読んでプールにする。複数のテストから使う。 */
    private static Map<String, LootTableConfig.Pool> shippedPools(List<String> warnings) {
        File shipped = new File("src/main/resources/" + LootTableConfig.FILE_NAME);
        assertTrue(shipped.isFile(), "出荷 yml が見つからない: " + shipped.getAbsolutePath());
        YamlConfiguration config = YamlConfiguration.loadConfiguration(shipped);
        return LootTableConfig.parsePools(config.getConfigurationSection("pools"), warnings::add);
    }

    /** 出荷 yml のどこかに、その custom:<ID>（またはバニラ Material 名）の候補があるか。 */
    private static boolean offersSomewhere(Map<String, LootTableConfig.Pool> pools, String item) {
        return pools.values().stream()
                .flatMap(p -> p.entries().stream())
                .anyMatch(e -> e.item().equalsIgnoreCase(item));
    }

    @Test
    @DisplayName("出荷 yml が警告ゼロでパースでき、10ティア全部に対象テーブルと候補と倍率がある")
    void shippedYamlParsesCleanly() {
        List<String> warnings = new ArrayList<>();
        Map<String, LootTableConfig.Pool> pools = shippedPools(warnings);
        assertTrue(warnings.isEmpty(), "出荷 yml が警告を出している: " + warnings);
        // 2026-08-21: 5 → 10 ティア(ユーザー指示「それぞれのダンジョンを漁る意味を作るため
        // 10 ティアくらいに細分化」)。生成元は tmp/worldgen/gen_loot_yml.py。
        for (String id : List.of("t10_structures", "t9_structures", "t8_structures",
                "t7_structures", "t6_structures", "t5_structures", "t4_structures",
                "t3_structures", "t2_structures", "t1_structures")) {
            LootTableConfig.Pool pool = pools.get(id);
            assertTrue(pool != null, "ティアプール " + id + " が無い");
            assertFalse(pool.tables().isEmpty(), id + " に tables: が無い");
            assertFalse(pool.entries().isEmpty(), id + " に entries: が無い");
            // 「既存戦利品も少し盛る」がこのファイルの半分の目的なので、倍率 1.0 は設定漏れ。
            assertTrue(pool.scalesQuantity(), id + " の quantity-multiplier が 1.0（＝既存戦利品を盛っていない）");
            assertTrue(pool.quantityMultiplier() <= 2.0,
                    id + " の quantity-multiplier が 2.0 を超えている: " + pool.quantityMultiplier());
        }
    }

    @Test
    @DisplayName("データパックの実名前空間 nova_structures が対象に入っている")
    void shippedYamlTargetsTheRealDatapackNamespace() {
        Map<String, LootTableConfig.Pool> pools = shippedPools(new ArrayList<>());
        // 旧版は実在しない `dungeons_and_taverns:` を対象にしていて、1度も発火していなかった。
        // 例外もログも出ないので、当たり判定そのものをここで固定する。
        // 案1 に入れるデータパック 6 種すべてから、実在する表 ID を1件ずつ当てる。
        // 1 つの名前空間がまるごと抜けても yml は正常にパースできてしまうので、ここで縛る。
        for (String key : List.of(
                "nova_structures:chests/mansion_overhaul/mansion_overhaul_generic",
                "incendium:castle/barrel/blacksmith",
                "structory:ruin/swamp/loot",
                "structory_towers:end_tower",
                "terralith:spire/treasure",
                "kaisyn:outpost/common/food")) {
            assertTrue(pools.values().stream().anyMatch(p -> p.matches(key)),
                    "データパックの表 " + key + " に当たるプールが無い");
        }
        assertTrue(pools.values().stream().anyMatch(p -> p.matches("minecraft:chests/ancient_city")),
                "バニラのチェストに当たるプールが無い");
    }

    @Test
    @DisplayName("旧ハードコード15表と旧2品の入手経路が残っている")
    void shippedYamlKeepsLegacyAcquisitionPaths() {
        Map<String, LootTableConfig.Pool> pools = shippedPools(new ArrayList<>());
        // 移行前は「この15表にエンチャント本とエンチャント金リンゴ」をJavaで直書きしていた。
        // ティア制に組み替えたときに黙って落ちると、既存プレイヤーの入手経路が1本消える。
        for (String leaf : List.of("abandoned_mineshaft", "desert_pyramid", "jungle_temple",
                "simple_dungeon", "stronghold_corridor", "stronghold_crossing", "stronghold_library",
                "woodland_mansion", "end_city_treasure", "bastion_treasure", "bastion_other",
                "bastion_hoglin_stable", "bastion_bridge", "ancient_city", "buried_treasure")) {
            assertTrue(pools.values().stream().anyMatch(p -> p.matches("minecraft:chests/" + leaf)),
                    "旧版が対象にしていた " + leaf + " がどのプールにも入っていない");
        }
        assertTrue(pools.values().stream().flatMap(p -> p.entries().stream())
                        .anyMatch(LootTableConfig.Entry::enchantBook),
                "ArsPaper 自前のエンチャント本の候補が失われている");
        assertTrue(offersSomewhere(pools, "ENCHANTED_GOLDEN_APPLE"),
                "エンチャント金リンゴの候補が失われている");
    }

    @Test
    @DisplayName("ユーザー指定の報酬5分類がすべて配られている")
    void shippedYamlOffersEveryRewardClass() {
        Map<String, LootTableConfig.Pool> pools = shippedPools(new ArrayList<>());
        // 2026-08-16 の指示: 現実の芯 / 品質系スレッド / カスタムモブ素材 / レシピの無い鍵 /
        // 極低確率のリセット系。どれか1分類まるごと落ちても yml は正常にパースできてしまうので、
        // 代表IDを1件ずつ固定する。
        assertTrue(offersSomewhere(pools, "custom:reality_thread_core"), "現実の芯が配られていない");
        assertTrue(offersSomewhere(pools, "custom:thread_artisan"), "品質系スレッドが配られていない");
        assertTrue(offersSomewhere(pools, "custom:ravager_hide"), "カスタムモブ素材が配られていない");
        assertTrue(offersSomewhere(pools, "custom:key_bridge"), "レシピの無い鍵が配られていない");
        assertTrue(offersSomewhere(pools, "custom:skill_tree_reset"), "リセット系スクロールが配られていない");
    }

    @Test
    @DisplayName("除外すると決めたものは出荷 yml のどこにも無い")
    void shippedYamlExcludesForbiddenRewards() {
        Map<String, LootTableConfig.Pool> pools = shippedPools(new ArrayList<>());
        // 深淵素材2種はダンジョン主の独占素材（ユーザー判断で構造物からは出さない）。
        assertFalse(offersSomewhere(pools, "custom:abyssal_ingot"), "深淵の合金が構造物から出ている");
        assertFalse(offersSomewhere(pools, "custom:binder_fragment"), "束縛者の欠片が構造物から出ている");
        // 村・壺/発掘は無限湧き or 大量にあるので対象外。
        // ⚠ 2026-08-23 (W-187) にトライアルチャンバーはこの一覧から外れた。
        //    「一部チェストが空」という報告の真因がこの除外だったので、
        //    ユーザー判断で「バニラの中身の豪華さでティアを決めて割り当てる」へ変更した。
        for (String key : List.of("minecraft:chests/village/village_armorer",
                "minecraft:archaeology/desert_pyramid")) {
            assertFalse(pools.values().stream().anyMatch(p -> p.matches(key)),
                    "対象外にしたはずの " + key + " に当たるプールがある");
        }
    }

    @Test
    @DisplayName("トライアルチャンバーはチェスト・ヴォールト・スポナーの3経路とも当たる")
    void shippedYamlCoversTrialChambers() {
        Map<String, LootTableConfig.Pool> pools = shippedPools(new ArrayList<>());
        // 2026-08-23 (W-187): 試練の間は戦利品の出口が3つに割れていて、しかも
        // ヴォールトとスポナーは LootGenerateEvent を発火しない(BlockDispenseLootEvent 側)。
        // yml から1本でも落ちると「宝物庫だけ空」という形で部分的に壊れるので、
        // 3経路それぞれの代表表を固定する。
        // ヴォールトが引く表は chests/trial_chambers/reward(通常) と ..._ominous(不吉)。
        // 不吉版は構造物 piece から参照されないので、生成側で ID 名指しして拾っている。
        for (String key : List.of(
                "minecraft:chests/trial_chambers/reward",
                "minecraft:chests/trial_chambers/reward_ominous",
                "minecraft:chests/trial_chambers/supply",
                "minecraft:spawners/trial_chamber/consumables",
                "minecraft:spawners/ominous/trial_chamber/consumables")) {
            assertTrue(pools.values().stream().anyMatch(p -> p.matches(key)),
                    "試練の間の " + key + " がどのプールにも入っていない");
        }
    }

    @Test
    @DisplayName("quantity-multiplier は 1.0-3.0 に丸められる")
    void quantityMultiplierIsClamped() {
        var pools = parse("""
                pools:
                  low:
                    tables: [a]
                    quantity-multiplier: 0.1
                    entries: [{item: DIAMOND}]
                  huge:
                    tables: [a]
                    quantity-multiplier: 999.0
                    entries: [{item: DIAMOND}]
                  ok:
                    tables: [a]
                    quantity-multiplier: 1.5
                    entries: [{item: DIAMOND}]
                """, new ArrayList<>());
        // 1.0 未満は「減らす」になってしまうので 1.0 へ。桁を間違えた大きな値も 3.0 で止める。
        assertEquals(1.0, pools.get("low").quantityMultiplier());
        assertFalse(pools.get("low").scalesQuantity());
        assertEquals(3.0, pools.get("huge").quantityMultiplier());
        assertEquals(1.5, pools.get("ok").quantityMultiplier());
        assertTrue(pools.get("ok").scalesQuantity());
    }

    @Test
    @DisplayName("entries も quantity-multiplier も無いプールは警告される")
    void poolWithNeitherEntriesNorMultiplierWarns() {
        List<String> warnings = new ArrayList<>();
        parse("""
                pools:
                  p:
                    tables: [a]
                """, warnings);
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("quantity-multiplier"), warnings.get(0));
    }

    @Test
    @DisplayName("quantity-multiplier だけのプールは entries が無くても警告されない")
    void multiplierOnlyPoolIsValid() {
        List<String> warnings = new ArrayList<>();
        var pools = parse("""
                pools:
                  p:
                    tables: [a]
                    quantity-multiplier: 2.0
                """, warnings);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertTrue(pools.get("p").entries().isEmpty());
        assertTrue(pools.get("p").scalesQuantity());
    }
}
