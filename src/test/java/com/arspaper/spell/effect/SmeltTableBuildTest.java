package com.arspaper.spell.effect;

import com.arspaper.spell.effect.SmeltEffect.SmeltRule;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 精錬表を<b>サーバのかまどレシピ登録から組む</b>ことを固定する
 * (2026-08-22 実サーバ報告「精錬魔法が粘土玉に効かない」)。
 *
 * <p><b>何が壊れていたか。</b> 変換表が {@code SMELT_MAP} という<b>手書きのハードコード</b>で、
 * {@code CLAY_BALL -> BRICK} が入っていなかった。手書きなので抜けは粘土玉だけではなく、
 * 石炭/ラピス/レッドストーン/ダイヤ/エメラルドの各鉱石、ネザーの金鉱石、
 * 後から追加された原木(淡いオークなど)、コーラスフルーツ、濡れたスポンジ…と広範囲に及ぶ。
 * <b>1件足しても同じ穴が残り続ける</b>ので、表そのものを登録から引くようにした。
 *
 * <p>ここで検査するのは<b>表の組み立て規則</b>。実レシピの読み出し({@code Bukkit.recipeIterator})は
 * サーバが要るので、このフォークのテスト基盤(MockBukkit なし)では動かせない。
 * だから {@link SmeltEffect#buildTable} は<b>読み出しと分けて</b>あり、ここへ合成のレシピを流す。
 */
class SmeltTableBuildTest {

    /** 「耐久を持つ材質」の判定。実運用は {@code Material#getMaxDurability() > 0}(サーバが要る)。 */
    private static final Predicate<Material> DAMAGEABLE =
            material -> material == Material.IRON_PICKAXE || material == Material.IRON_HELMET;

    private static SmeltRule rule(Material result, Material... inputs) {
        return new SmeltRule(Set.of(inputs), result);
    }

    @Test
    @DisplayName("かまどレシピにある変換はそのまま表に入る(報告された粘土玉→レンガを含む)")
    void furnaceRecipesBecomeSmeltEntries() {
        Map<Material, Material> table = SmeltEffect.buildTable(List.of(
                rule(Material.BRICK, Material.CLAY_BALL),
                rule(Material.CHARCOAL, Material.OAK_LOG, Material.PALE_OAK_LOG),
                rule(Material.COAL, Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE)), DAMAGEABLE);

        assertEquals(Material.BRICK, table.get(Material.CLAY_BALL), "粘土玉が焼けない(報告そのもの)");
        assertEquals(Material.CHARCOAL, table.get(Material.PALE_OAK_LOG),
                "後から増えた原木が拾えていない(手書きの表が腐る典型)");
        assertEquals(Material.COAL, table.get(Material.DEEPSLATE_COAL_ORE));
    }

    @Test
    @DisplayName("道具・防具は取り込まない(足元に落とした装備がナゲットに化ける)")
    void damageableInputsAreNeverSmelted() {
        // バニラには iron_pickaxe -> iron_nugget が実在する。そのまま取り込むと
        // 精錬の掃き取り(半径2)に入った装備が詠唱1回で消える。
        Map<Material, Material> table = SmeltEffect.buildTable(List.of(
                rule(Material.IRON_NUGGET, Material.IRON_PICKAXE, Material.IRON_HELMET),
                rule(Material.IRON_INGOT, Material.RAW_IRON)), DAMAGEABLE);

        assertNull(table.get(Material.IRON_PICKAXE), "落とした道具が精錬対象になっている");
        assertNull(table.get(Material.IRON_HELMET), "落とした防具が精錬対象になっている");
        assertEquals(Material.IRON_INGOT, table.get(Material.RAW_IRON), "前提: 普通の素材は焼けること");
    }

    @Test
    @DisplayName("TF独自の追加(原石ブロック)はバニラより優先で必ず残る")
    void extraSmeltsSurviveAndWin() {
        Map<Material, Material> table = SmeltEffect.buildTable(List.of(
                // 仮にバニラ側が原石ブロックへ別の変換を持っていても、独自分が勝つこと。
                rule(Material.IRON_INGOT, Material.RAW_IRON_BLOCK)), DAMAGEABLE);

        assertEquals(Material.IRON_BLOCK, table.get(Material.RAW_IRON_BLOCK));
        assertEquals(Material.GOLD_BLOCK, table.get(Material.RAW_GOLD_BLOCK));
        assertEquals(Material.COPPER_BLOCK, table.get(Material.RAW_COPPER_BLOCK));
    }

    @Test
    @DisplayName("表は書き換え不可、ただし get(null) では落ちない")
    void tableIsUnmodifiableButNullTolerant() {
        Map<Material, Material> table = SmeltEffect.buildTable(
                List.of(rule(Material.BRICK, Material.CLAY_BALL)), DAMAGEABLE);

        assertFalse(table.isEmpty());
        assertThrowsUnsupported(() -> table.put(Material.DIRT, Material.DIRT));
        // Map.copyOf にすると get(null) が NPE になる。CustomItemConversionPolicy は
        // null を渡す前に弾いているが、同じ罠で右クリックが全部落ちた前例があるので縛る。
        assertNull(table.get(null), "不変Mapにすると get(null) が NPE になる");
    }

    private static void assertThrowsUnsupported(Runnable action) {
        try {
            action.run();
            assertTrue(false, "書き換えられてしまった");
        } catch (UnsupportedOperationException expected) {
            // 期待どおり
        }
    }
}
