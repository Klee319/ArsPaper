package com.arspaper.item;

import com.arspaper.item.ThreadApplicationPolicy.SlotOrigin;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F2「武器・触媒のスレッド枠が機能しない」(2026-07-31)で決めた線引きを固定する。
 *
 * <p>このフォークのテスト基盤は Bukkit ランタイム/MockBukkit/Mockito を持たない
 * ({@link ArmorManaListenerManaBonusGuardTest} の javadoc 参照)ため、判断そのものを
 * {@link ThreadApplicationPolicy} へ切り出し、実行時と同一のコードを直接検証する。
 * 「その policy が実際に配線されていること」は {@link ThreadHandheldWiringTest} が担保する。
 *
 * <p>{@link Material} は素の enum なのでサーバ無しで評価できる。ただし TrinityForge は
 * {@code compileOnly} なのでテスト実行時にクラスが解決できず、
 * {@link ThreadApplicationPolicy#isArmorSlotMaterial} は材質名フォールバック側を通る
 * ── そのフォールバックが正しいことをここで固定する意味もある(TF 未ロードのサーバでも同じ経路)。
 */
class ThreadApplicationPolicyTest {

    @Test
    @DisplayName("数値ステは着用防具・メインハンド・オフハンドすべてで適用する(F2の本体)")
    void numericStatsApplyFromEverySlot() {
        for (SlotOrigin origin : SlotOrigin.values()) {
            assertTrue(ThreadApplicationPolicy.appliesNumericStats(origin),
                    origin + " で数値ステが落ちている。これが落ちると武器・触媒のスレッドが"
                            + "また『枠だけの飾り』に戻る。");
        }
    }

    @Test
    @DisplayName("常時効果(ポーション/飛行)は着用中の防具だけ")
    void ambientEffectsAreArmorOnly() {
        assertTrue(ThreadApplicationPolicy.appliesAmbientEffects(SlotOrigin.WORN_ARMOR));
        // 手持ちで発動させると持ち替え(PlayerItemHeldEvent でも再計算が走る)のたびに
        // 点滅・付け外しが起き、機能ではなく不具合として受け取られる。
        assertFalse(ThreadApplicationPolicy.appliesAmbientEffects(SlotOrigin.MAIN_HAND));
        assertFalse(ThreadApplicationPolicy.appliesAmbientEffects(SlotOrigin.OFF_HAND));
    }

    @Test
    @DisplayName("バックパックだけは防具以外へ装着できない(収納データが取り出せなくなるため)")
    void onlyBackpackIsBlockedOutsideArmor() {
        // ⚠ ThreadType 自体はテストから触れない: 静的初期化で PotionEffectType レジストリを
        // 引くのでサーバ無しでは ExceptionInInitializerError になる。判定本体は boolean 版。
        assertFalse(ThreadApplicationPolicy.isSocketableOutsideArmor(true),
                "/ars backpack は着用防具しか走査しないので、武器へ入れると中身へ辿り着けない");

        // ポーション/飛行/マナ系は装着可。常時効果は出ないが厳選ステと thread-sets のセット効果は
        // 効くので死に枠にはならない(GUI が『常時効果は発動しません』と明示する)。
        assertTrue(ThreadApplicationPolicy.isSocketableOutsideArmor(false));
    }

    @Test
    @DisplayName("常時効果しか持たないスレッドはGUIの注記対象になる")
    void ambientOnlyEffectsAreFlaggedForTheGuiNotice() {
        // (potionEffect, flightThread, backpackThread) の順。SPEED 等 / FLIGHT / BACKPACK が対象。
        assertTrue(ThreadApplicationPolicy.hasAmbientOnlyEffect(true, false, false));
        assertTrue(ThreadApplicationPolicy.hasAmbientOnlyEffect(false, true, false));
        assertTrue(ThreadApplicationPolicy.hasAmbientOnlyEffect(false, false, true));

        // mana_regen / spell_cost_down のような純粋な数値スレッドは注記不要(手持ちでも効く)。
        assertFalse(ThreadApplicationPolicy.hasAmbientOnlyEffect(false, false, false));
        assertFalse(ThreadApplicationPolicy.isAmbientOnlyEffect(null));
    }

    @Test
    @DisplayName("着用スロット判定: 防具4部位とエリトラは true、武器・触媒・ツールは false")
    void armorSlotDetection() {
        assertTrue(ThreadApplicationPolicy.isArmorSlotMaterial(Material.DIAMOND_HELMET));
        assertTrue(ThreadApplicationPolicy.isArmorSlotMaterial(Material.LEATHER_CHESTPLATE));
        assertTrue(ThreadApplicationPolicy.isArmorSlotMaterial(Material.NETHERITE_LEGGINGS));
        assertTrue(ThreadApplicationPolicy.isArmorSlotMaterial(Material.NETHERITE_BOOTS));
        assertTrue(ThreadApplicationPolicy.isArmorSlotMaterial(Material.TURTLE_HELMET));
        assertTrue(ThreadApplicationPolicy.isArmorSlotMaterial(Material.ELYTRA));

        // 触媒(杖)は BLAZE_ROD。ここが true に化けると常時効果が手持ちで走り出す。
        assertFalse(ThreadApplicationPolicy.isArmorSlotMaterial(Material.BLAZE_ROD));
        assertFalse(ThreadApplicationPolicy.isArmorSlotMaterial(Material.NETHERITE_SWORD));
        assertFalse(ThreadApplicationPolicy.isArmorSlotMaterial(Material.NETHERITE_PICKAXE));
        assertFalse(ThreadApplicationPolicy.isArmorSlotMaterial(Material.TRIDENT));
        assertFalse(ThreadApplicationPolicy.isArmorSlotMaterial(Material.MACE));
        assertFalse(ThreadApplicationPolicy.isArmorSlotMaterial(Material.AIR));
        assertFalse(ThreadApplicationPolicy.isArmorSlotMaterial(null));
    }

    @Test
    @DisplayName("ThreadType を触る版は enum のフラグへ委譲するだけ(nullは安全側)")
    void threadTypeOverloadsDelegateToThePrimitiveCores() {
        // ThreadType 自体はサーバ無しでロードできないため、null 経路だけを実行で確認する。
        // enum のフラグ(isBackpackThread/hasPotionEffect/isFlightThread)を実際に読んでいることは
        // ThreadHandheldWiringTest がソース走査で固定する。
        assertTrue(ThreadApplicationPolicy.canSocketOutsideArmor(null),
                "null は装着可否の判断材料にしない(呼び出し側が別途 hasEffect() を見ている)");
        assertFalse(ThreadApplicationPolicy.isAmbientOnlyEffect(null));
    }

    @Test
    @DisplayName("同一スレッドの重複セットは既定で許可される(2026-08-18 ユーザー確定要件)")
    void duplicateSocketingIsAllowedByDefault() {
        assertTrue(ThreadApplicationPolicy.DEFAULT_STACKABLE,
                "既定が重複不可に戻ると thread-sets.yml の6段が『1装備1本 × キャリア5 = 上限5』で"
                        + "また到達不能になる(role_luck / role_effeciency が実際にその状態だった)");

        // 既定の上限は DEFAULT_MAX_STACK。ここまでは挿せて、そこから先は挿せない。
        for (int already = 0; already < ThreadApplicationPolicy.DEFAULT_MAX_STACK; already++) {
            assertTrue(ThreadApplicationPolicy.canSocketAnother(
                            true, ThreadApplicationPolicy.DEFAULT_MAX_STACK, already),
                    already + "本目まで挿せるはず");
        }
        assertFalse(ThreadApplicationPolicy.canSocketAnother(
                        true, ThreadApplicationPolicy.DEFAULT_MAX_STACK,
                        ThreadApplicationPolicy.DEFAULT_MAX_STACK),
                "max に達したら止める(無制限にすると1種へ全枠集中でき、TF の帯目標が壊れる)");

        // 既定の上限 × キャリア5 が、現行 thread-sets.yml の最上位ティア6 を超えていること。
        assertTrue(ThreadApplicationPolicy.DEFAULT_MAX_STACK * 5 >= 6,
                "既定の上限 × キャリア5 が6未満だと role_luck の6段がまた死ぬ");
    }

    @Test
    @DisplayName("stackable: false を明示した種だけは1本まで")
    void explicitNonStackableStillBlocksTheSecondCopy() {
        assertTrue(ThreadApplicationPolicy.canSocketAnother(false, 99, 0));
        assertFalse(ThreadApplicationPolicy.canSocketAnother(false, 99, 1),
                "stackable: false は max を無視して1本で打ち止め");
    }

    @Test
    @DisplayName("材質名フォールバックは EquipmentSlotResolver と同じ接尾辞規則を使う")
    void nameFallbackMatchesTheResolverRules() {
        assertTrue(ThreadApplicationPolicy.isArmorSlotMaterialName("NETHERITE_HELMET"));
        assertTrue(ThreadApplicationPolicy.isArmorSlotMaterialName("TURTLE_HELMET"));
        assertTrue(ThreadApplicationPolicy.isArmorSlotMaterialName("ELYTRA"));
        assertFalse(ThreadApplicationPolicy.isArmorSlotMaterialName("BLAZE_ROD"));
        assertFalse(ThreadApplicationPolicy.isArmorSlotMaterialName(null));
    }

    // isToolMaterial/isToolMaterialName を検証していた2件(依頼#44「斧を除く純粋ツールだけ」)は
    // 2026-08-04 にメソッドごと削除したため撤去した。ThreadGuiOpenListener の見上げジェスチャーは
    // 現在「スレッド枠を持つ装備全般」を対象にしており、素材カテゴリによる絞り込みそのものが
    // 存在しない(ThreadGuiOpenListener/ThreadHandheldWiringTestのjavadoc参照)。
}
