package com.arspaper.spell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>ユーザー要望（2026-08-19）「精錬をブロックに撃つとき、対応増強グリフを半径増加から
 * 範囲（各種）に変え、増強グリフの設定範囲が精錬されるようにしてほしい」の回帰ガード。</b>
 *
 * <p><b>なぜ半径増加では1ブロックも広がらなかったのか。</b>
 * 「半径増加」({@code aoe_radius}) が動かすのは {@code SpellContext#aoeRadiusLevel} で、これを読むのは
 * <ul>
 *   <li>エンティティAOE展開（{@code resolveGroupsOnEntity}）と</li>
 *   <li>{@code handlesAoeInternally() == true} のエフェクト（爆発・召喚数など）</li>
 * </ul>
 * だけ。<b>ブロックAOE展開（{@code resolveGroupsOnBlock}）は
 * {@code aoeLevel}/{@code aoeHeightLevel}/{@code aoeVerticalLevel} の3軸しか見ない</b>。
 * 精錬は {@code handlesAoeInternally()} が false のままなので、
 * 半径増加を何個積んでも<b>ブロックは狙った1個しか精錬されなかった</b>
 * （＝グリフは装着できるのに効果が無い、という無言死）。
 *
 * <p>互換表 {@code AUGMENT_COMPAT} は yml ではなく<b>ソースコード側の定数</b>
 * （{@code GlyphConfig} の「augments互換性はソースコード定義のため、ymlからは読まない」参照）。
 * つまり {@code glyphs.yml} の {@code max-augments} をいくら直しても互換性は変わらない ——
 * 両方を直さないと直らないので、ここで両方を固定する。
 */
class SmeltAreaAugmentCompatTest {

    private static final Path GLYPHS_YML =
            Path.of("src", "main", "resources", "glyphs.yml");

    @Test
    @DisplayName("精錬には範囲(幅/高さ/法線)が付く —— ブロックAOE展開が読む3軸はこれだけ")
    void smeltAcceptsTheThreeAreaAugments() {
        assertTrue(GlyphConfig.augmentCompatible("smelt", "aoe"),
                "範囲[幅]が付かない。ブロックAOE展開は aoe/aoe_height/aoe_vertical しか見ないので、"
                        + "これが無いと精錬は永久に1ブロックのまま");
        assertTrue(GlyphConfig.augmentCompatible("smelt", "aoe_height"), "範囲[高さ]が付かない");
        assertTrue(GlyphConfig.augmentCompatible("smelt", "aoe_vertical"), "範囲[法線]が付かない");
        assertTrue(GlyphConfig.augmentCompatible("smelt", "super_aoe"), "超増強版の範囲[幅]が付かない");
    }

    @Test
    @DisplayName("精錬から半径増加は外れている（ブロックには一切効かない軸なので誤解を残さない）")
    void smeltNoLongerAcceptsTheRadiusAugment() {
        assertFalse(GlyphConfig.augmentCompatible("smelt", "aoe_radius"),
                "半径増加が付いたままだと『装着できるのに範囲が広がらない』状態が残る");
    }

    /**
     * 3軸とも {@code max-augments} に明示しておくこと。未記載の軸は
     * {@code getMaxAugmentStack} が {@code Integer.MAX_VALUE} を返すため
     * <b>上限なし（実質、展開ループ側の 10 が上限）</b>になり、
     * 高さや法線だけを極端に伸ばせてしまう（破壊グリフが現にそうなっている）。
     */
    @Test
    @DisplayName("glyphs.yml の精錬は3軸とも上限を明示している（未記載＝上限なしの穴を塞ぐ）")
    void shippedGlyphsYmlCapsAllThreeAxes() throws IOException {
        String smeltBlock = smeltSection();
        assertTrue(smeltBlock.contains("aoe:"), "glyphs.yml の smelt に aoe の上限が無い: " + smeltBlock);
        assertTrue(smeltBlock.contains("aoe_height:"),
                "glyphs.yml の smelt に aoe_height の上限が無い（未記載は上限なしになる）: " + smeltBlock);
        assertTrue(smeltBlock.contains("aoe_vertical:"),
                "glyphs.yml の smelt に aoe_vertical の上限が無い（未記載は上限なしになる）: " + smeltBlock);
        assertFalse(smeltBlock.contains("aoe_radius:"),
                "glyphs.yml の smelt に半径増加が残っている（互換表から外したので死に設定）: " + smeltBlock);
    }

    /** 出荷 glyphs.yml の {@code glyphs.smelt:} ブロックだけを切り出す。 */
    private static String smeltSection() throws IOException {
        String yaml = Files.readString(GLYPHS_YML);
        int start = yaml.indexOf("\n  smelt:");
        assertTrue(start >= 0, "glyphs.yml に smelt グリフが無い");
        int next = yaml.indexOf("\n\n", start + 1);
        return next < 0 ? yaml.substring(start) : yaml.substring(start, next);
    }

    /**
     * 展開の向き。既定の {@code FIXED} は法線方向の符号が {@code +1}（手前＝設置系の向き）なので、
     * 壁を狙って範囲[法線]を積むと<b>壁の中ではなく自分側の空気が対象になる</b>。
     * 精錬は破壊と同じ「狙った面から内側へ効く」操作。
     *
     * <p>{@code SmeltEffect} は {@code NamespacedKey(plugin, ...)} を構築するので Bukkit 抜きでは
     * 実体化できない（このフォークのテストには MockBukkit も Mockito も無い）。
     * 兄弟の {@code SpellBreakMarkerCoverageTest} と同じくソース走査で固定する ——
     * 定数名が変われば本体側がコンパイルエラーになるので、名前の空振りにはならない。
     */
    @Test
    @DisplayName("精錬のAOE展開は破壊と同じくヒット面から内側へ向かう")
    void smeltExpandsInwardLikeBreak() throws IOException {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "arspaper", "spell", "effect", "SmeltEffect.java"));
        assertTrue(source.contains("AoeMode.HIT_FACE_INWARD"),
                "法線方向が手前向き(既定のFIXED)だと、壁を狙ったときに壁の中ではなく"
                        + "自分側の空気を精錬しに行く");
    }

    /**
     * 範囲対応で {@code applyToBlock} は範囲内の全ブロック分呼ばれるようになった。
     * その先頭で毎回ドロップアイテムを掃くので、<b>同じアイテムが何十回も精錬対象になる</b>。
     * {@code SMELT_MAP} には {@code COBBLESTONE → STONE → SMOOTH_STONE} の2段連鎖があるため、
     * 1回の詠唱で落ちている丸石が滑らかな石まで進んでしまう。
     */
    @Test
    @DisplayName("ドロップアイテムの多重精錬ガードが残っている（丸石→石→滑らかな石の連鎖を1回で止める）")
    void droppedItemsAreSmeltedAtMostOncePerTick() throws IOException {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "arspaper", "spell", "effect", "SmeltEffect.java"));
        assertTrue(source.contains("smeltedThisTick"),
                "多重精錬ガードが消えている。範囲展開は1詠唱でブロック数だけ掃き取りを走らせるので、"
                        + "ガードが無いと丸石が1回の詠唱で滑らかな石になる");
    }
}
