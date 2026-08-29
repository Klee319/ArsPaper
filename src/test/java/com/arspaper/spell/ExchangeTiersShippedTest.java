package com.arspaper.spell;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出荷 {@code glyphs.yml} の {@code exchange_tiers} を縛る
 * （2026-08-22 ユーザー報告「交換で菌糸が作れない。前回は作れてたはず」）。
 *
 * <p><b>何が起きていたか。</b> 交換グリフは
 * {@code ExchangeEffect#findNextBlock} が示すとおり「<b>同じ段に並んでいるブロック</b>」の間でしか
 * 循環しない。ところが土系の段は {@code [DIRT, COARSE_DIRT, ROOTED_DIRT, MUD]} だけで、
 * GRASS_BLOCK / PODZOL / MYCELIUM は<b>どの段にも入っていなかった</b>。
 * 菌糸は他に生成経路が無く（TF/Ars のレシピ・儀式・変換のどこにも結果として現れない）、
 * 菌糸ソースリンクの強化には計 19 個要るので、キノコ島から運ぶ以外に手が無い状態だった。
 * 2026-08-22 の指示で 3 種を第 1 段へ同居させた。
 *
 * <p><b>なぜテストで縛るのか。</b> {@code loadExchangeTiers} は
 * {@code Material.matchMaterial} が {@code null} を返した項目を<b>黙って捨てる</b>。
 * つまり綴りを間違えても、段ごと消しても、起動ログに警告 1 行すら出ない ──
 * 「実機で交換したら何も起きない」でしか気づけない。ここが唯一の検出点。
 *
 * <p><b>配備の注意。</b> ArsPaper は自分では既存の yml を更新しない
 * （{@code saveResource(..., false)} ＋ {@code updateResourceFiles} はバージョン文字列が
 * 変わったときしか再展開しない）ので、<b>jar を差し替えても {@code /ars reload} しても
 * 配備先の glyphs.yml は変わらない</b>。届く経路は
 * {@code ops/launch/deploy-config-head.cmd}（全バックエンド停止が要る）か、
 * 止めずに入れる {@code ops/scripts/apply-exchange-dirt-tier.ps1} + {@code /ars reload} の 2 つ。
 */
class ExchangeTiersShippedTest {

    private static List<List<String>> shippedTiers() {
        File file = new File("src/main/resources/glyphs.yml");
        assertTrue(file.isFile(), "出荷 glyphs.yml が見つからない: " + file.getAbsolutePath());
        List<?> raw = YamlConfiguration.loadConfiguration(file).getList("exchange_tiers");
        assertNotNull(raw, "glyphs.yml に exchange_tiers: が無い");

        List<List<String>> tiers = new ArrayList<>();
        for (Object tier : raw) {
            assertTrue(tier instanceof List<?>, "exchange_tiers の要素がリストでない: " + tier);
            List<String> names = new ArrayList<>();
            for (Object material : (List<?>) tier) {
                names.add(String.valueOf(material));
            }
            tiers.add(names);
        }
        assertTrue(tiers.size() > 10, "exchange_tiers の読み込みが空振りしている(" + tiers.size() + "段)");
        return tiers;
    }

    /** {@code current} を含む段を返す（{@code findNextBlock} と同じく最初に見つかった段が勝つ）。 */
    private static List<String> tierContaining(List<List<String>> tiers, String material) {
        for (List<String> tier : tiers) {
            if (tier.contains(material)) {
                return tier;
            }
        }
        return null;
    }

    @Test
    @DisplayName("土から交換で菌糸まで辿り着ける(草・ポドゾル・菌糸が土と同じ段に居る)")
    void myceliumIsReachableFromDirt() {
        List<List<String>> tiers = shippedTiers();
        List<String> dirtTier = tierContaining(tiers, "DIRT");
        assertNotNull(dirtTier, "DIRT を含む段が無い");

        for (String required : List.of("GRASS_BLOCK", "PODZOL", "MYCELIUM")) {
            assertTrue(dirtTier.contains(required),
                    required + " が土の段に居ない。交換は同じ段の中でしか循環しないので、"
                            + "別の段に置くと土からは永久に辿り着けない。実際の段: " + dirtTier);
        }
    }

    @Test
    @DisplayName("段に書いた名前は全部実在する Material(綴り違いは黙って捨てられる)")
    void everyNameResolvesToAMaterial() {
        for (List<String> tier : shippedTiers()) {
            for (String name : tier) {
                assertNotNull(Material.getMaterial(name),
                        name + " は Material に無い。loadExchangeTiers は警告を出さずに捨てるので、"
                                + "そのブロックだけ交換できなくなる");
            }
        }
    }

    @Test
    @DisplayName("同じブロックを2つの段に書かない(後ろの段が丸ごと到達不能になる)")
    void noMaterialAppearsInTwoTiers() {
        Map<String, List<Integer>> seen = new HashMap<>();
        List<List<String>> tiers = shippedTiers();
        for (int index = 0; index < tiers.size(); index++) {
            for (String name : new LinkedHashSet<>(tiers.get(index))) {
                seen.computeIfAbsent(name, key -> new ArrayList<>()).add(index);
            }
        }
        Set<String> duplicated = new LinkedHashSet<>();
        seen.forEach((name, indexes) -> {
            if (indexes.size() > 1) {
                duplicated.add(name + indexes);
            }
        });
        assertEquals(Set.of(), duplicated,
                "findNextBlock は最初に見つけた段で確定するので、2 つ目以降の段は"
                        + "そのブロックからは絶対に選ばれない(＝黙って死んだ設定になる)");
    }
}
