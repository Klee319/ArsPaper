package com.arspaper.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D6 / G5 / G10 / 増幅ダメージ乗算(2026-08-02) の<b>配線</b>が外れていないことを固定する。
 *
 * <p>{@link MagicStatSourcePolicyTest} は判断と算術（純粋関数）を検証するが、それだけでは
 * 「policy は正しいのに呼び出し側が繋がっていない」状態を検出できない。まさにそれが G5
 * （{@code glyph-damage-multiplier-bonus} は公開APIがあるのに呼び出し元がゼロで、lore に出るのに
 * 効かなかった）と D6（castItem を運ぶ経路が無く杖の攻撃力が落ちていた）の正体だった。
 * 増幅ダメージ乗算も同型のリスク（ダメージ系エフェクトが旧仕様の固定値加算を消し忘れると
 * 乗算ボーナスと二重計上になる）を持つため、同じ配線検査で押さえる。
 *
 * <p>このフォークは Bukkit ランタイムを持たないので、{@code LegacyCastExperienceRemovalTest} と
 * 同じソーステキスト検査でこの種の「無言の断線」を止める。
 */
class MagicStatSourceWiringTest {

    private static final String SRC = "src/main/java/com/arspaper/";

    /** {@code dealSpellDamage} を呼ぶダメージ系エフェクト。ここに載っている限りグリフIDを渡す義務がある。 */
    private static final List<String> DAMAGE_EFFECTS = List.of(
            "ColdSnapEffect", "CrushWaveEffect", "FlareEffect", "HarmEffect", "HealEffect",
            "HeavyImpactEffect", "LightningEffect", "ScorchEffect", "SonicBoomEffect",
            "WindshearEffect");

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(SRC + relative));
    }

    // --- D6: castItem を運ぶ経路 ---

    @Test
    @DisplayName("SpellBindListener は触媒引数とは別に castItem(手持ちの実アイテム)を渡す")
    void bindListenerPassesCastItem() throws Exception {
        String listener = read("spell/SpellBindListener.java");
        assertTrue(listener.contains("ItemStack castItem = item;"),
                "SpellBindListener は右クリックした実アイテムを castItem として取り出す必要がある");
        assertTrue(listener.contains(".cast(player, recipe, sharedSpell, catalystArg, castItem)"),
                "SpellBindListener は cast(..., catalystArg, castItem) を呼ぶ必要がある"
                        + "(castItem を落とすと杖の attack-power が魔導書に化けて消える = D6 の再発)");
    }

    @Test
    @DisplayName("SpellCaster は castItem を SpellContext まで運ぶ")
    void spellCasterForwardsCastItemToContext() throws Exception {
        String caster = read("spell/SpellCaster.java");
        assertTrue(caster.contains("new SpellContext(caster, recipe, catalyst, castItem)"),
                "SpellCaster は castItem を SpellContext へ渡す必要がある");
    }

    @Test
    @DisplayName("SpellContext は castItem を通常ダメージ経路へ渡す")
    void spellContextForwardsCastItemToBridge() throws Exception {
        String context = read("spell/SpellContext.java");
        assertTrue(context.contains(
                        "magicalFinalDamage(casterUuid, target, spellBase, catalyst, castItem, glyphId, amplifyForDamage)"),
                "dealSpellDamage は castItem・glyphId に加えて増幅段数(amplifyForDamage)も"
                        + " bridge へ渡す必要がある(渡さないと増幅グリフの乗算ボーナスが無言で0のままになる)");
    }

    @Test
    @DisplayName("防御無視ダメージ(日輪/月輪)には attack-power を合成しない")
    void defenseIgnoringDamageCarriesNoAttackPower() throws Exception {
        // 2026-07-31 F4 指摘1(a): defenseIgnoringDamage は setHealth で直接HPを削る経路で、
        // EntityDamageEvent すら発火しない=守備力・耐性・回避・不死のトーテム・盾・TF の
        // PvpDamagePolicy のいずれも通らない。ここへ attack-power(Lv100帯の杖で10000超)を足すと
        // 軽減不能の即死ボタンになる。「魔法へ攻撃力100%加算」はあくまで通常ダメージ経路の仕様。
        assertFalse(read("spell/SpellContext.java").contains("magicAttackPowerAddend"),
                "defenseIgnoringDamage は attack-power を合成してはならない(D6 の対象外経路)");
        assertFalse(read("integration/TrinityForgeBridge.java").contains("public static double magicAttackPowerAddend"),
                "防御無視ダメージ専用の attack-power 加算入口は撤去済みである必要がある"
                        + "(残すと同じ即死バグへ配線し直される)");
    }

    @Test
    @DisplayName("魔導書の直接詠唱は castItem を明示的に null にする(魔導書のステを魔法へ持ち込まない)")
    void spellBookDirectCastPassesNullCastItem() throws Exception {
        assertTrue(read("item/impl/SpellBook.java")
                        .contains(".cast(player, recipe, sharedSpell, item, null)"),
                "SpellBook は castItem=null を明示して現行挙動(攻撃力0)を維持する必要がある");
    }

    @Test
    @DisplayName("bridge は魔法ダメージ・攻撃ステ・魔法出血のすべてに同じ statSource を渡す")
    void bridgeUsesOneStatSourceForAllThreePaths() throws Exception {
        String bridge = read("integration/TrinityForgeBridge.java");
        assertTrue(bridge.contains("ItemStack statSource = resolveMagicStatSource(catalyst, castItem);"),
                "magicalFinalDamage は statSource を1回だけ解決する必要がある");
        // 引数リスト全体ではなく「同じ statSource を渡していること」だけを見る。ここを閉じ括弧まで
        // 固定していたため、2026-08-08 にエンチャント補正(enchantBonuses)を足しただけで
        // 不変条件は保たれているのに赤くなった。守りたいのは引数の個数ではなく供給元の一致。
        assertTrue(bridge.contains("resolveMagicAttackStats(casterUuid, statSource"),
                "攻撃ステ(会心/貫通)も同じ statSource から引く必要がある");
        assertTrue(bridge.contains("notifyMagicBleed(casterUuid, victim, statSource,"),
                "魔法出血が読む集約と魔法ダメージが読む集約を一致させる既存要件"
                        + "(片方だけ statSource を変えると崩れる)");
        assertTrue(bridge.contains("MagicStatSourcePolicy.effectiveBase("),
                "実効ベースの合成は MagicStatSourcePolicy 経由(テスト対象と同一コード)である必要がある");
        // 旧実装(触媒だけを見る加算)が残っていないこと。
        assertFalse(bridge.contains("catalystAttackPowerAddend("),
                "触媒引数だけを見る旧 attack-power 加算は撤去済みである必要がある");
    }

    @Test
    @DisplayName("bridge は magical.attack-power-scale を TF config から読む")
    void bridgeReadsAttackPowerScaleFromConfig() throws Exception {
        assertTrue(read("integration/TrinityForgeBridge.java")
                        .contains("combatDamage().magicalAttackPowerScale()"),
                "係数はハードコードせず combat/damage.yml から読む必要がある");
    }

    // --- G5: glyph_damage_multiplier_bonus の配線 ---

    @Test
    @DisplayName("bridge は TF 公開API glyphDamageMultiplier を実際に呼ぶ")
    void bridgeCallsGlyphDamageMultiplierApi() throws Exception {
        String bridge = read("integration/TrinityForgeBridge.java");
        assertTrue(bridge.contains("tf.glyphDamageMultiplier(caster, glyphId)"),
                "glyph_damage_multiplier_bonus は TF 側 API 経由で解決する必要がある"
                        + "(対象グリフの一覧をフォークへ複製しない)");
        assertTrue(bridge.contains("MagicStatSourcePolicy.applyGlyphMultiplier("),
                "倍率の適用点も policy 経由に揃える必要がある");
    }

    @Test
    @DisplayName("ダメージ系エフェクトはすべて dealSpellDamage へ自分のグリフIDを渡す")
    void allDamageEffectsPassTheirGlyphId() throws Exception {
        Pattern call = Pattern.compile("dealSpellDamage\\(([^;]*?)\\);", Pattern.DOTALL);
        List<String> offenders = new ArrayList<>();
        for (String effect : DAMAGE_EFFECTS) {
            String src = read("spell/effect/" + effect + ".java");
            Matcher m = call.matcher(src);
            int calls = 0;
            while (m.find()) {
                calls++;
                if (!m.group(1).contains("id.getKey()") && !m.group(1).contains("getId().getKey()")) {
                    offenders.add(effect + ": " + m.group(1).trim());
                }
            }
            assertTrue(calls > 0, effect + " に dealSpellDamage の呼び出しが見つからない"
                    + "(リネーム/削除したなら DAMAGE_EFFECTS も更新すること)");
        }
        assertTrue(offenders.isEmpty(),
                "グリフIDを渡していない dealSpellDamage 呼び出しがある。"
                        + "stats/glyph-damage-boost.yml にそのグリフを追加しても倍率が無言で乗らない: "
                        + offenders);
    }

    // --- 2026-08-02: 増幅(Amplify)の乗算ボーナス配線 ---

    /** dealSpellDamage 側の乗算ボーナスに委ねた(=ローカルの固定値加算を撤去した)ダメージ系エフェクトと、
     *  撤去済みであるべき glyphs.yml 由来のパラメータキー。HealEffect は対アンデッド分岐で
     *  amount へ既に増幅を織り込む設計を維持しているため対象外(applyAmplifyDamageMultiplier=false)。 */
    private static final java.util.Map<String, String> AMPLIFY_MULTIPLIER_EFFECTS = java.util.Map.ofEntries(
            java.util.Map.entry("HarmEffect", "amplify-bonus"),
            java.util.Map.entry("ColdSnapEffect", "amplify-bonus"),
            java.util.Map.entry("CrushWaveEffect", "amplify-bonus"),
            java.util.Map.entry("FlareEffect", "amplify-bonus"),
            java.util.Map.entry("ScorchEffect", "amplify-bonus"),
            java.util.Map.entry("WindshearEffect", "amplify-bonus"),
            java.util.Map.entry("LightningEffect", "amplify-bonus"),
            java.util.Map.entry("HeavyImpactEffect", "amplify-damage-bonus"),
            java.util.Map.entry("SonicBoomEffect", "amplify-damage-bonus"));

    @Test
    @DisplayName("bridge は増幅の乗算ボーナスを MagicStatSourcePolicy 経由で適用する")
    void bridgeAppliesAmplifyMultiplierViaPolicy() throws Exception {
        String bridge = read("integration/TrinityForgeBridge.java");
        assertTrue(bridge.contains("MagicStatSourcePolicy.applyAmplifyMultiplier("),
                "増幅の乗算ボーナスの適用点も policy 経由に揃える必要がある"
                        + "(直書きすると純関数テストと実装がズレても検出できない)");
        assertTrue(bridge.contains("amplifyDamageRatePerStack()") && bridge.contains("maxAmplifyDamageLevel()"),
                "乗率/上限は glyphs.yml の amplify.params から読む必要がある(決め打ち禁止)");
    }

    @Test
    @DisplayName("ダメージ系エフェクトはローカルの増幅固定値加算を持たない(二重計上防止)")
    void damageEffectsNoLongerBakeAmplifyLocally() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (var entry : AMPLIFY_MULTIPLIER_EFFECTS.entrySet()) {
            String src = read("spell/effect/" + entry.getKey() + ".java");
            if (src.contains(entry.getValue())) {
                offenders.add(entry.getKey() + " はまだ config.getParam(..., \"" + entry.getValue() + "\", ...) "
                        + "を参照している(dealSpellDamage側の乗算ボーナスと二重計上になる)");
            }
            if (src.contains("getAmplifyLevel() *") || src.contains("* context.getAmplifyLevel()")) {
                offenders.add(entry.getKey() + " はまだ getAmplifyLevel() をダメージ式へ直接掛けている");
            }
        }
        assertTrue(offenders.isEmpty(), String.join("; ", offenders));
    }

    // --- 2026-08-23: 進捗「触媒を振るう」(catalyst_cast)の判定 ---

    @Test
    @DisplayName("catalyst_cast は catalysts.yml の登録有無ではなくステ供給元の判定で数える")
    void catalystCastCounterUsesStatSourcePredicate() throws Exception {
        String bridge = read("integration/TrinityForgeBridge.java");
        assertTrue(bridge.contains("public static boolean isCatalystCast(ItemStack catalyst, ItemStack castItem)"),
                "「触媒から唱えたか」の判定は bridge の公開入口として1本だけ持つ必要がある");
        assertTrue(bridge.contains("return resolveMagicStatSource(catalyst, castItem) != null;"),
                "判定はステ供給元の解決と同一でなければならない。別の述語を書くと"
                        + "杖を1本足すたびに2か所を直すことになり、また片方が腐る");

        String caster = read("spell/SpellCaster.java");
        assertTrue(caster.contains("TrinityForgeBridge.isCatalystCast(catalyst, castItem)"),
                "SpellCaster は catalyst_cast の加算判定を bridge へ委ねる必要がある");
        assertFalse(caster.contains("recordCastCounters(caster, recipe, catalystData != null"),
                "catalystData != null へ戻してはならない ── TFカタログの杖10本は"
                        + " spellbooks.yml の catalysts: に1本も載っていないので、"
                        + "杖で何回撃っても catalyst_cast が0のままになる(2026-08-23 のバグ)");
    }

    @Test
    @DisplayName("魔導書の直接詠唱は catalyst_cast に数えない")
    void spellBookDirectCastIsNotCountedAsCatalystCast() throws Exception {
        // 直接詠唱は castItem=null(上の spellBookDirectCastPassesNullCastItem が固定)なので、
        // resolveMagicStatSource は catalyst(=魔導書)が catalysts.yml 登録品のときしか
        // 非 null を返さない。つまり魔導書ぶんが混ざらないことは、その解決規則が
        // castItem==null で catalysts.yml 登録だけを見ることに依存している。
        String bridge = read("integration/TrinityForgeBridge.java");
        assertTrue(bridge.contains("boolean castItemAccepted = castItem != null"),
                "castItem が null の経路でステ供給元が拾われてはならない"
                        + "(拾うと魔導書の素の右クリック詠唱まで catalyst_cast に混ざる)");
    }

    // --- G10: 杖の use-level-requirement / use-skill をバインド詠唱でも強制する ---

    @Test
    @DisplayName("SpellCaster は castItem 自身の使用条件(use-skill/use-level)も検査する")
    void spellCasterEnforcesUseRequirementOnCastItem() throws Exception {
        String caster = read("spell/SpellCaster.java");
        assertTrue(caster.contains("useRequirementDenial(caster, castItem)"),
                "SpellBindListener が PlayerInteractEvent をキャンセルするため TF の"
                        + " UseRequirementListener は走らない。詠唱時に castItem を検査しないと"
                        + " 低レベルでも infinity_cane(要Lv100)で撃てる");
        assertTrue(caster.contains("castItem != null && castItem != catalyst"),
                "catalyst と同一インスタンスのときは二重に拒否メッセージを出さないこと");
    }
}
