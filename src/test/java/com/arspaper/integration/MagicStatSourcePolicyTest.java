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
 *   <li>害悪(harm) の基礎ダメージ = {@code glyphs.yml} の {@code base-damage} = 9.0
 *       （2026-08-02: 増幅(Amplify)は固定値加算からSharpness型の乗算方式へ変更したため、
 *       もはや glyphs.yml の base-damage に amplify-bonus を織り込まない。
 *       {@link #applyAmplifyMultiplier} のテストは本ファイル下部を参照）</li>
 *   <li>インフィニティの杖 {@code BLAZE_ROD#400012} の attack-power(fixed) =
 *       TF {@code stats/item-stats.yml} の 10584</li>
 * </ul>
 */
class MagicStatSourcePolicyTest {

    /** 害悪(harm)のグリフ基礎ダメージ({@code glyphs.yml} の {@code base-damage}、増幅を含まない)。 */
    private static final double HARM_BASE_DAMAGE = 9.0;
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
        assertEquals(HARM_BASE_DAMAGE,
                MagicStatSourcePolicy.effectiveBase(HARM_BASE_DAMAGE, 0.0, 1.0), 1e-9);
    }

    @Test
    @DisplayName("魔導書を直接詠唱したときは従来どおり攻撃力0(castItem なし)")
    void spellBookDirectCastKeepsZeroAttackPower() {
        assertEquals(MagicStatSourcePolicy.StatSource.NONE,
                MagicStatSourcePolicy.chooseStatSource(false, false));
        assertEquals(HARM_BASE_DAMAGE,
                MagicStatSourcePolicy.effectiveBase(HARM_BASE_DAMAGE, 0.0, 1.0), 1e-9);
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
    @DisplayName("infinity_cane(10584)で harm を撃つと実効ベースが 9 -> 10593 になる")
    void infinityCaneAddsItsAttackPowerToTheSpellBase() {
        double effectiveBase = MagicStatSourcePolicy.effectiveBase(
                HARM_BASE_DAMAGE, INFINITY_CANE_ATTACK_POWER, 1.0);
        assertEquals(10593.0, effectiveBase, 1e-9);
        // 参考(TF側の後段): コンバットレベル100・防御ゼロ相当の的なら
        // 10593 × (1 + 0.01×100) × base-coefficient 1.0 = 21186。
        assertEquals(21186.0, effectiveBase * COMBAT_LEVEL_100_MULTIPLIER, 1e-9);
    }

    @Test
    @DisplayName("attack-power-scale を 0 にすると杖の攻撃力の加算が消える")
    void zeroScaleRemovesTheAddendEntirely() {
        assertEquals(0.0,
                MagicStatSourcePolicy.scaledAttackPower(INFINITY_CANE_ATTACK_POWER, 0.0), 0.0);
        assertEquals(HARM_BASE_DAMAGE, MagicStatSourcePolicy.effectiveBase(
                HARM_BASE_DAMAGE, INFINITY_CANE_ATTACK_POWER, 0.0), 1e-9);
    }

    @Test
    @DisplayName("attack-power-scale は係数として効く(0.25 なら 1/4)")
    void scaleActsAsAMultiplier() {
        assertEquals(HARM_BASE_DAMAGE + 2646.0, MagicStatSourcePolicy.effectiveBase(
                HARM_BASE_DAMAGE, INFINITY_CANE_ATTACK_POWER, 0.25), 1e-9);
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
                HARM_BASE_DAMAGE, INFINITY_CANE_ATTACK_POWER, 1.0);
        // 「害悪強化 +30%」= TrinityForge#glyphDamageMultiplier が 1.3 を返すケース。
        assertEquals(10593.0 * 1.3, MagicStatSourcePolicy.applyGlyphMultiplier(base, 1.3), 1e-9);
    }

    @Test
    @DisplayName("倍率 1.0 / 不正値は基礎ダメージを変えない(fail-open)")
    void glyphMultiplierIsNoOpForNeutralOrInvalidValues() {
        assertEquals(100.0, MagicStatSourcePolicy.applyGlyphMultiplier(100.0, 1.0), 1e-9);
        assertEquals(100.0, MagicStatSourcePolicy.applyGlyphMultiplier(100.0, 0.0), 1e-9);
        assertEquals(100.0, MagicStatSourcePolicy.applyGlyphMultiplier(100.0, -1.0), 1e-9);
        assertEquals(100.0, MagicStatSourcePolicy.applyGlyphMultiplier(100.0, Double.NaN), 1e-9);
    }

    // --- 2026-08-02: 増幅(Amplify)のダメージ乗算ボーナス ---
    // 「触媒想定の環境で増幅が弱い」報告への対処。旧仕様(グリフ基礎への固定値加算)から
    // Sharpness等ダメージ増加エンチャントと同じ乗算方式(1段+10%)へ変更。

    @Test
    @DisplayName("増幅0段は基礎ダメージを変えない")
    void amplifyMultiplierIsNoOpAtLevelZero() {
        assertEquals(10593.0, MagicStatSourcePolicy.applyAmplifyMultiplier(
                10593.0, 0, MagicStatSourcePolicy.DEFAULT_AMPLIFY_DAMAGE_RATE, 0), 1e-9);
    }

    @Test
    @DisplayName("増幅1段は合成後の基礎ダメージを+10%する")
    void amplifyMultiplierAppliesOneStack() {
        // infinity_cane で harm(9.0)を撃った実効ベース 10593.0 に対して増幅1段(+10%)。
        double base = MagicStatSourcePolicy.effectiveBase(
                HARM_BASE_DAMAGE, INFINITY_CANE_ATTACK_POWER, 1.0);
        assertEquals(10593.0 * 1.10, MagicStatSourcePolicy.applyAmplifyMultiplier(
                base, 1, MagicStatSourcePolicy.DEFAULT_AMPLIFY_DAMAGE_RATE, 0), 1e-9);
    }

    @Test
    @DisplayName("増幅5段は+50%(触媒ビルドでも攻撃力込みの合計へ掛かることの確認)")
    void amplifyMultiplierAppliesFiveStacksToTheCombinedBase() {
        double base = MagicStatSourcePolicy.effectiveBase(
                HARM_BASE_DAMAGE, INFINITY_CANE_ATTACK_POWER, 1.0);
        // 旧仕様なら増幅5段の寄与は 5×3.0=15.0(実効ベース比 0.14%)で無意味だったが、
        // 新仕様は攻撃力込みの合計 10593.0 に対して+50%(=5296.5)効く。
        assertEquals(10593.0 * 1.50, MagicStatSourcePolicy.applyAmplifyMultiplier(
                base, 5, MagicStatSourcePolicy.DEFAULT_AMPLIFY_DAMAGE_RATE, 0), 1e-9);
    }

    @Test
    @DisplayName("段数上限(maxLevel)を超えた増幅はクランプされる(暴走防止)")
    void amplifyMultiplierClampsToMaxLevel() {
        // maxLevel=6(harm等の max-augments.amplify と同じ値)で、level=50 を渡しても+60%止まり。
        assertEquals(100.0 * 1.60, MagicStatSourcePolicy.applyAmplifyMultiplier(
                100.0, 50, MagicStatSourcePolicy.DEFAULT_AMPLIFY_DAMAGE_RATE, 6), 1e-9);
        // maxLevel<=0はクランプなし(仕様上の「無制限」)。
        assertEquals(100.0 * 6.0, MagicStatSourcePolicy.applyAmplifyMultiplier(
                100.0, 50, MagicStatSourcePolicy.DEFAULT_AMPLIFY_DAMAGE_RATE, 0), 1e-9);
    }

    @Test
    @DisplayName("Dampenによる負の段数は乗率を下げる(0未満にはしない)")
    void amplifyMultiplierNeverGoesNegative() {
        // level=-1 → 1 - 0.10 = 0.90倍。
        assertEquals(100.0 * 0.90, MagicStatSourcePolicy.applyAmplifyMultiplier(
                100.0, -1, MagicStatSourcePolicy.DEFAULT_AMPLIFY_DAMAGE_RATE, 0), 1e-9);
        // 極端な負の段数でも乗率は0未満にならない(符号反転で回復に化けさせない)。
        assertEquals(0.0, MagicStatSourcePolicy.applyAmplifyMultiplier(
                100.0, -50, MagicStatSourcePolicy.DEFAULT_AMPLIFY_DAMAGE_RATE, 0), 1e-9);
    }

    @Test
    @DisplayName("乗率が非有限/0のときは基礎ダメージを変えない(fail-open)")
    void amplifyMultiplierIsNoOpForInvalidRate() {
        assertEquals(100.0, MagicStatSourcePolicy.applyAmplifyMultiplier(100.0, 3, 0.0, 0), 1e-9);
        assertEquals(100.0, MagicStatSourcePolicy.applyAmplifyMultiplier(100.0, 3, Double.NaN, 0), 1e-9);
    }
}
