package com.arspaper.loot;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LootTableListener} の純関数部（既存戦利品の増量とデータパック本の判定）を固定する。
 *
 * <p>このフォークのテスト環境はサーバを起こさない（paper-api をクラスパスに置いただけ）ので
 * {@code LootGenerateEvent} は直接叩けない。増量と除去の<b>算数と判定</b>はここに切り出してある。
 *
 * <p>ここが壊れたときの症状は「チェストの中身が思ったより多い/少ない」だけで、例外もログも
 * 出ない。yml の倍率を疑って何度も配備し直すことになるので、期待値の性質を試験で縛る。
 */
class LootTableListenerTest {

    private static LootTableConfig.Pool pool(String id, double multiplier) {
        return new LootTableConfig.Pool(id, List.of("a"), 1, multiplier, List.of());
    }

    @Test
    @DisplayName("倍率の整数部は確定で増える")
    void wholePartAlwaysApplies() {
        // 2.0 倍は乱数に関係なく 2 倍。roll の値を変えても揺れてはいけない。
        assertEquals(6, LootTableListener.scaledAmount(3, 2.0, 64, 0.0));
        assertEquals(6, LootTableListener.scaledAmount(3, 2.0, 64, 0.999));
    }

    @Test
    @DisplayName("端数はその確率で +1 する（四捨五入にしない）")
    void fractionalPartIsProbabilistic() {
        // 1 個 × 1.5 = 1.5 → 50% で 2 個。四捨五入だと常に 2 個になり実効 2 倍に化ける。
        assertEquals(2, LootTableListener.scaledAmount(1, 1.5, 64, 0.49));
        assertEquals(1, LootTableListener.scaledAmount(1, 1.5, 64, 0.50));
        assertEquals(1, LootTableListener.scaledAmount(1, 1.5, 64, 0.99));
        // 3 × 1.4 = 4.2 → 整数部 4 確定、20% でだけ 5。
        assertEquals(5, LootTableListener.scaledAmount(3, 1.4, 64, 0.19));
        assertEquals(4, LootTableListener.scaledAmount(3, 1.4, 64, 0.21));
    }

    @Test
    @DisplayName("倍率 1.0 と 0 個・負の個数でも 1 個以上を返す")
    void degenerateInputsStayValid() {
        assertEquals(3, LootTableListener.scaledAmount(3, 1.0, 64, 0.5));
        // ItemStack#setAmount(0) はスタックを消すので、増量処理で戦利品を消してはいけない。
        assertEquals(1, LootTableListener.scaledAmount(0, 2.0, 64, 0.5));
        assertEquals(1, LootTableListener.scaledAmount(-5, 2.0, 64, 0.5));
    }

    @Test
    @DisplayName("スタック上限を超えない（枠が有限なので溢れた分は消えるだけ）")
    void resultIsCappedAtStackSize() {
        assertEquals(64, LootTableListener.scaledAmount(50, 2.0, 64, 0.5));
        // エンダーパールなど 16 スタックのものは 16 で止まる。
        assertEquals(16, LootTableListener.scaledAmount(10, 2.0, 16, 0.5));
        // 上限 1 のもの（防具など）は増えない。
        assertEquals(1, LootTableListener.scaledAmount(1, 2.0, 1, 0.0));
    }

    @Test
    @DisplayName("複数プールが当たったら倍率は掛け合わせず最大値を採る")
    void overlappingPoolsUseTheMaxNotTheProduct() {
        // 掛け合わせると、プールを1つ足しただけで既存の全チェストが黙って 3.0 倍になる。
        assertEquals(2.0, LootTableListener.maxQuantityMultiplier(
                List.of(pool("a", 1.5), pool("b", 2.0))));
        assertEquals(1.0, LootTableListener.maxQuantityMultiplier(List.of()));
        assertEquals(1.0, LootTableListener.maxQuantityMultiplier(List.of(pool("a", 1.0))));
    }

    @Test
    @DisplayName("minecraft 以外の名前空間のエンチャントを持つ本だけを落とす")
    void onlyNonVanillaEnchantsAreTreatedAsDatapackBooks() {
        // DnT は nova_structures:* で 32 種のエンチャントを足す。名前を列挙する方式にすると
        // データパック更新で増えた分がすり抜けるので、名前空間で判定していることを固定する。
        assertTrue(LootTableListener.hasNonVanillaEnchant(
                List.of(new NamespacedKey("nova_structures", "unknown_future_enchant"))));
        // バニラだけの本（修繕を含む）はここでは落とさない。修繕は TF の removed-vanilla-items の担当。
        assertFalse(LootTableListener.hasNonVanillaEnchant(
                List.of(NamespacedKey.minecraft("mending"), NamespacedKey.minecraft("sharpness"))));
        assertFalse(LootTableListener.hasNonVanillaEnchant(List.of()));
    }

    @Test
    @DisplayName("バニラと混在していてもデータパック側が1つあれば落とす")
    void mixedBookIsStillDropped() {
        assertTrue(LootTableListener.hasNonVanillaEnchant(List.of(
                NamespacedKey.minecraft("sharpness"),
                new NamespacedKey("nova_structures", "custom"))));
    }
}
