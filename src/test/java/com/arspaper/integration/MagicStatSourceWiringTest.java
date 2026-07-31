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
 * D6 / G5 / G10 の<b>配線</b>が外れていないことを固定する（2026-07-31）。
 *
 * <p>{@link MagicStatSourcePolicyTest} は判断と算術（純粋関数）を検証するが、それだけでは
 * 「policy は正しいのに呼び出し側が繋がっていない」状態を検出できない。まさにそれが G5
 * （{@code glyph-damage-multiplier-bonus} は公開APIがあるのに呼び出し元がゼロで、lore に出るのに
 * 効かなかった）と D6（castItem を運ぶ経路が無く杖の攻撃力が落ちていた）の正体だった。
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
    @DisplayName("SpellContext は castItem を bridge の両経路(通常ダメージ/防御無視)へ渡す")
    void spellContextForwardsCastItemToBridge() throws Exception {
        String context = read("spell/SpellContext.java");
        assertTrue(context.contains("magicalFinalDamage(casterUuid, target, spellBase, catalyst, castItem, glyphId)"),
                "dealSpellDamage は castItem と glyphId を bridge へ渡す必要がある");
        assertTrue(context.contains("magicAttackPowerAddend(catalyst, castItem)"),
                "defenseIgnoringDamage(日輪/月輪) も castItem を渡す必要がある");
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
        assertTrue(bridge.contains("resolveMagicAttackStats(casterUuid, statSource)"),
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
