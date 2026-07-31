package com.arspaper.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D6「魔法ダメージに杖の攻撃力が乗らない」の再発防止（2026-07-31）。
 *
 * <p>このフォークのテスト基盤は Bukkit ランタイム / MockBukkit / Mockito を持たない
 * （{@link SourceAutoConsumeTest} の javadoc 参照）ため、{@code ItemStack} と TrinityForge 実体を
 * 要する {@code TrinityForgeBridge} そのものは動かせない。判断と算術を切り出した
 * {@link MagicStatSourcePolicy}（実行時と同一のコード）を対象にし、
 * 「その policy が実際に配線されていること」は {@link MagicStatSourceWiringTest} が担保する。
 *
 * <p>実測値の根拠:
 * <ul>
 *   <li>害悪(harm) 最大増幅のグリフ基礎ダメージ = {@code glyphs.yml} の
 *       {@code base-damage 9.0 + amplify 6 × amplify-bonus 3.0} = 27.0</li>
 *   <li>インフィニティの杖 {@code BLAZE_ROD#400012} の attack-power(fixed) =
 *       TF {@code stats/item-stats.yml} の 10584</li>
 * </ul>
 */
class MagicStatSourcePolicyTest {

    /** 害悪(harm)最大増幅のグリフ基礎ダメージ: 9.0 + 6 × 3.0。 */
    private static final double HARM_MAX_SPELL_BASE = 27.0;
    /** インフィニティの杖 BLAZE_ROD#400012 の attack-power(fixed)。 */
    private static final double INFINITY_CANE_ATTACK_POWER = 10584.0;
    /** combat/damage.yml level-scaling.per-level 0.01 × コンバットレベル100。 */
    private static final double COMBAT_LEVEL_100_MULTIPLIER = 2.0;

    // --- use-skill ゲート ---

    @Test
    @DisplayName("use-skill: ARS_MAGIC を持つアイテムだけがステ供給元として認められる")
    void onlyArsMagicUseSkillIsAcceptedAsStatSource() {
        assertTrue(MagicStatSourcePolicy.isMagicUseSkill("ARS_MAGIC"));
        // item-stats.yml は手書きなので大文字小文字・前後空白の揺れを吸収する。
        assertTrue(MagicStatSourcePolicy.isMagicUseSkill("ars_magic"));
        assertTrue(MagicStatSourcePolicy.isMagicUseSkill("  ARS_MAGIC  "));

        // 近接/採取スキルの装備は魔法のステ源にしない(剣にバインドして近接ステで魔法を撃つ抜け道の封鎖)。
        assertFalse(MagicStatSourcePolicy.isMagicUseSkill("LIGHT_WEAPONS"));
        assertFalse(MagicStatSourcePolicy.isMagicUseSkill("HEAVY_WEAPONS"));
        assertFalse(MagicStatSourcePolicy.isMagicUseSkill("MINING"));
        assertFalse(MagicStatSourcePolicy.isMagicUseSkill(""));
        assertFalse(MagicStatSourcePolicy.isMagicUseSkill(null));
    }

    // --- ステ供給元の選択 ---

    @Test
    @DisplayName("未登録CMDの杖を手に持ってバインド詠唱: ステ源は castItem(杖)")
    void unregisteredWandBecomesStatSource() {
        // infinity_cane(BLAZE_ROD#400012) は spellbooks.yml の catalysts: に載っていないので
        // 触媒引数は魔導書になる(catalystRegistered=false)。それでも杖がステ源になること。
        assertEquals(MagicStatSourcePolicy.StatSource.CAST_ITEM,
                MagicStatSourcePolicy.chooseStatSource(true, false));
    }

    @Test
    @DisplayName("剣にバインドしても近接ステは魔法に乗らない(ステ源なし)")
    void swordBoundSpellHasNoStatSource() {
        // 剣の use-skill は LIGHT_WEAPONS なので castItemAccepted=false。触媒も未登録(魔導書)。
        assertEquals(MagicStatSourcePolicy.StatSource.NONE,
                MagicStatSourcePolicy.chooseStatSource(false, false));
        // 攻撃力の加算値も 0(剣の attack-power は一切引かれない)。
        assertEquals(HARM_MAX_SPELL_BASE,
                MagicStatSourcePolicy.effectiveBase(HARM_MAX_SPELL_BASE, 0.0, 1.0), 1e-9);
    }

    @Test
    @DisplayName("魔導書を直接詠唱したときは従来どおり攻撃力0(castItem なし)")
    void spellBookDirectCastKeepsZeroAttackPower() {
        assertEquals(MagicStatSourcePolicy.StatSource.NONE,
                MagicStatSourcePolicy.chooseStatSource(false, false));
        assertEquals(HARM_MAX_SPELL_BASE,
                MagicStatSourcePolicy.effectiveBase(HARM_MAX_SPELL_BASE, 0.0, 1.0), 1e-9);
    }

    @Test
    @DisplayName("catalysts.yml 登録済み触媒は castItem が無くても従来どおりステ源になる")
    void registeredCatalystRemainsStatSourceWithoutCastItem() {
        assertEquals(MagicStatSourcePolicy.StatSource.CATALYST,
                MagicStatSourcePolicy.chooseStatSource(false, true));
    }

    @Test
    @DisplayName("castItem が認められる場合は登録済み触媒より castItem を優先する")
    void castItemWinsOverRegisteredCatalyst() {
        // バインド詠唱で手持ちが登録済み触媒のときは catalystArg == castItem(同一 ItemStack)なので
        // どちらを選んでも同じ品になる。優先順位を固定して将来の食い違いを防ぐ。
        assertEquals(MagicStatSourcePolicy.StatSource.CAST_ITEM,
                MagicStatSourcePolicy.chooseStatSource(true, true));
    }

    // --- 基礎ダメージ合成 ---

    @Test
    @DisplayName("infinity_cane(10584)で harm を撃つと実効ベースが 27 -> 10611 になる")
    void infinityCaneAddsItsAttackPowerToTheSpellBase() {
        double effectiveBase = MagicStatSourcePolicy.effectiveBase(
                HARM_MAX_SPELL_BASE, INFINITY_CANE_ATTACK_POWER, 1.0);
        assertEquals(10611.0, effectiveBase, 1e-9);
        // 参考(TF側の後段): コンバットレベル100・防御ゼロ相当の的なら
        // 10611 × (1 + 0.01×100) × base-coefficient 1.0 = 21222。
        // 修正前は 27 × 2 = 54(報告値「50程度」)だったので、杖の攻撃力が乗ったことが数値で分かる。
        assertEquals(21222.0, effectiveBase * COMBAT_LEVEL_100_MULTIPLIER, 1e-9);
    }

    @Test
    @DisplayName("attack-power-scale を 0 にすると杖の攻撃力の加算が消える")
    void zeroScaleRemovesTheAddendEntirely() {
        assertEquals(0.0,
                MagicStatSourcePolicy.scaledAttackPower(INFINITY_CANE_ATTACK_POWER, 0.0), 0.0);
        assertEquals(HARM_MAX_SPELL_BASE, MagicStatSourcePolicy.effectiveBase(
                HARM_MAX_SPELL_BASE, INFINITY_CANE_ATTACK_POWER, 0.0), 1e-9);
    }

    @Test
    @DisplayName("attack-power-scale は係数として効く(0.25 なら 1/4)")
    void scaleActsAsAMultiplier() {
        assertEquals(HARM_MAX_SPELL_BASE + 2646.0, MagicStatSourcePolicy.effectiveBase(
                HARM_MAX_SPELL_BASE, INFINITY_CANE_ATTACK_POWER, 0.25), 1e-9);
    }

    @Test
    @DisplayName("scale は TF スキーマと同じ [0,10] にクランプされ、非有限値は既定へ落ちる")
    void scaleIsClampedLikeTheJavaSchema() {
        assertEquals(0.0, MagicStatSourcePolicy.clampAttackPowerScale(-5.0), 0.0);
        assertEquals(10.0, MagicStatSourcePolicy.clampAttackPowerScale(999.0), 0.0);
        assertEquals(1.0, MagicStatSourcePolicy.clampAttackPowerScale(Double.NaN), 0.0);
        assertEquals(MagicStatSourcePolicy.DEFAULT_ATTACK_POWER_SCALE,
                MagicStatSourcePolicy.clampAttackPowerScale(Double.POSITIVE_INFINITY), 0.0);
    }

    @Test
    @DisplayName("負の attack-power は 0 扱い(回復側へ反転させない)")
    void negativeAttackPowerIsTreatedAsZero() {
        assertEquals(0.0, MagicStatSourcePolicy.scaledAttackPower(-100.0, 1.0), 0.0);
        assertEquals(0.0, MagicStatSourcePolicy.scaledAttackPower(Double.NaN, 1.0), 0.0);
    }

    // --- G5: グリフ別ダメージ倍率 ---

    @Test
    @DisplayName("glyph_damage_multiplier_bonus は合成後の基礎ダメージへ掛かる")
    void glyphMultiplierAppliesToTheCombinedBase() {
        double base = MagicStatSourcePolicy.effectiveBase(
                HARM_MAX_SPELL_BASE, INFINITY_CANE_ATTACK_POWER, 1.0);
        // 「害悪強化 +30%」= TrinityForge#glyphDamageMultiplier が 1.3 を返すケース。
        assertEquals(10611.0 * 1.3, MagicStatSourcePolicy.applyGlyphMultiplier(base, 1.3), 1e-9);
    }

    @Test
    @DisplayName("倍率 1.0 / 不正値は基礎ダメージを変えない(fail-open)")
    void glyphMultiplierIsNoOpForNeutralOrInvalidValues() {
        assertEquals(100.0, MagicStatSourcePolicy.applyGlyphMultiplier(100.0, 1.0), 1e-9);
        assertEquals(100.0, MagicStatSourcePolicy.applyGlyphMultiplier(100.0, 0.0), 1e-9);
        assertEquals(100.0, MagicStatSourcePolicy.applyGlyphMultiplier(100.0, -1.0), 1e-9);
        assertEquals(100.0, MagicStatSourcePolicy.applyGlyphMultiplier(100.0, Double.NaN), 1e-9);
    }
}
