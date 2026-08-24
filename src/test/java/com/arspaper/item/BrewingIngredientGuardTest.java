package com.arspaper.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 醸造台へカスタム素材を入れられること（2026-08-24 実サーバ報告
 * 「醸造台にクリスタルリンゴを入れようとしてもインベントリから動かない」の回帰）。
 *
 * <h2>何が起きていたか</h2>
 * {@code CustomItemListener} は W-132 で「materials.yml 素材を消費・変換する装置へ入れさせない」
 * ガードを入れ、その集合に<b>醸造台を含めた</b>。根拠は「醸造台は材料スロットが Material しか
 * 見ずに飲み込む」だったが、これは<b>誤り</b> —— TF の {@code BrewPotionMixRegistrar} は
 * Paper の {@code PotionMix} を述語({@code createPredicateChoice})で登録しており、
 * 素材スロットの受け入れ判定はその述語を見るので、PDC 付きのカスタム素材をちゃんと見分ける。
 *
 * <p>結果、TF の出荷 config が宣言している醸造素材 8 件は<b>全部 materials.yml 素材</b>
 * だったため、<b>カスタム素材を使う醸造レシピが 1 件残らず成立しなかった</b>。
 *
 * <p>さらに、ガードがクリックしたスロットを一切見ていなかったので、
 * <b>醸造台を開いている間はプレイヤー側インベントリのカスタム素材を掴むことすらできなかった</b>
 * （{@code InventoryClickEvent#getInventory()} はクリック位置に関係なく常に上段を返す）。
 */
class BrewingIngredientGuardTest {

    @Test
    void brewingStandStaysBlockedByDefault() {
        // 穴は「TF が醸造素材と宣言した id」に限る。装置ごと素通しにしてはいけない
        // （素通しにすると圧縮素材が醸造台の素材枠で消える経路が開く）。
        assertTrue(CustomItemListener.CONSUMING_MACHINES.contains("BREWING"),
                "醸造台を CONSUMING_MACHINES から外すと、宣言していない素材まで入れられる");
    }

    @Test
    void brewingHasItsOwnExemption() {
        assertTrue(CustomItemListener.BREWING_MACHINES.contains("BREWING"),
                "醸造台の穴が無いと brew-unlocks の PotionMix が一度も発火できない");
        assertFalse(CustomItemListener.SMELTING_MACHINES.contains("BREWING"),
                "醸造の穴を精錬の穴に相乗りさせない（compressed-smelting の id が醸造台へ入る）");
        assertFalse(CustomItemListener.BREWING_MACHINES.contains("FURNACE"),
                "精錬側を醸造の穴に相乗りさせない（brew-unlocks の素材がかまどで焼ける）");
    }

    @Test
    void machineSlotClicksAreJudged() {
        // 醸造台は 5 スロット（ビン3 + 素材1 + 燃料1）。
        assertTrue(CustomItemListener.clickCanReachMachine(3, 5, false),
                "素材スロットへ直接置くクリックは判定対象");
        assertTrue(CustomItemListener.clickCanReachMachine(0, 5, false),
                "ビン枠へ直接置くクリックは判定対象");
    }

    @Test
    void quickMoveFromPlayerInventoryIsJudged() {
        assertTrue(CustomItemListener.clickCanReachMachine(30, 5, true),
                "シフトクリックはプレイヤー側スロットからでも中身が装置へ飛ぶ");
    }

    @Test
    void plainClicksInsideThePlayerInventoryAreNotJudged() {
        assertFalse(CustomItemListener.clickCanReachMachine(30, 5, false),
                "装置を開いている間、手持ちのカスタム素材が掴めなくなる（報告の症状そのもの）");
        assertFalse(CustomItemListener.clickCanReachMachine(-999, 5, false),
                "画面外クリック（rawSlot < 0）は装置に触れない");
    }
}
