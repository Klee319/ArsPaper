package com.arspaper.spell;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * グリフ配置（呪文編集）画面で「今の構成では置けないグリフ」を<b>灰色の染料</b>へ潰すこと。
 *
 * <p><b>何が壊れていたか。</b> 2026-09-05 のユーザー報告
 * 「グリフ配置のバリデーションが GUI でなされていない。シナジーのないグリフは
 * 灰色の染料のようなものに自動で置き換わったはず」。
 * 検査自体は動いていた（置けないグリフはクリックしても入らない）が、
 * <b>2026-08-22 のアイコン刷新で「置けない」の絵が消えていた</b> ——
 * 名前の色（灰）と lore の赤字しか手掛かりが無く、1 ページ 25 個の一覧では
 * 1 個ずつカーソルを当てるまで分からない。つまり「無いのは検査ではなく表示」。
 *
 * <p>ここで固定するのは 2 点。
 * <ol>
 *   <li><b>絵の優先順</b> —— 鍵（パーク未所持） &gt; 石炭（未解放） &gt; 灰色の染料（置けない）
 *       &gt; 個別アイコン。遠い直し方から順に見せる（スキルツリー → 筆記台 → その場で選び直し）。</li>
 *   <li><b>灰色にするのは「そのグリフ固有の理由」だけ</b> —— 「まだ形態を選んでいない」
 *       「満杯」まで潰すと一覧が一色になり、どれを解放済みかが絵から消える。
 *       それは 2026-08-22 に全グリフへ個別アイコンを付けて解放状態が読めなくなったのと
 *       同じ壊れ方（＝この修正が再発させてはいけないもの）。</li>
 * </ol>
 */
class GlyphIncompatibleIconTest {

    private static final String KEY = "harm";
    private static final SpellComponent.ComponentType TYPE = SpellComponent.ComponentType.EFFECT;

    @Test
    @DisplayName("置けないグリフは灰色の染料になる（個別アイコンのままにしない）")
    void unplaceableGlyphCollapsesToGrayDye() {
        Material individual = GlyphIcons.resolveForState(KEY, TYPE, null, true, true, true);
        assertEquals(Material.IRON_SWORD, individual, "置けるなら個別アイコンのまま");

        Material unplaceable = GlyphIcons.resolveForState(KEY, TYPE, null, true, true, false);
        assertEquals(GlyphIcons.INCOMPATIBLE_ICON, unplaceable);
        assertEquals(Material.GRAY_DYE, GlyphIcons.INCOMPATIBLE_ICON,
                "ユーザーが覚えている絵は灰色の染料（2026-08-22 以前のこの画面の仕様）");
        assertNotEquals(individual, unplaceable);
    }

    @Test
    @DisplayName("優先順は 鍵 > 石炭 > 灰色の染料（直し方の遠い方から見せる）")
    void perkAndUnlockGatesOutrankIncompatibility() {
        // パーク未所持: 置けない状態でも鍵のまま（灰色で上書きするとスキルツリーへの導線が消える）
        assertEquals(GlyphIcons.PERK_LOCKED_ICON,
                GlyphIcons.resolveForState(KEY, TYPE, null, true, false, false));
        assertEquals(GlyphIcons.PERK_LOCKED_ICON,
                GlyphIcons.resolveForState(KEY, TYPE, null, false, false, false));

        // 未解放: 置けない状態でも石炭のまま（筆記台へ行けという指示が先）
        assertEquals(GlyphIcons.LOCKED_ICON,
                GlyphIcons.resolveForState(KEY, TYPE, null, false, true, false));
    }

    @Test
    @DisplayName("灰色の染料は他のグリフのアイコンと衝突しない")
    void grayDyeIsNotAlsoSomeGlyphsOwnIcon() {
        assertNotEquals(true, GlyphIcons.defaults().containsValue(GlyphIcons.INCOMPATIBLE_ICON),
                "個別アイコンと同じ材質だと、そのグリフが常に『置けない』ように見える");
    }

    @Test
    @DisplayName("yml の icon: 上書きよりも『置けない』が優先される")
    void iconOverrideDoesNotEscapeTheGrayOut() {
        assertEquals(GlyphIcons.INCOMPATIBLE_ICON,
                GlyphIcons.resolveForState(KEY, TYPE, "DIAMOND", true, true, false),
                "1個だけ yml で絵を上書きしてあると、そこだけ置けるように見えてしまう");
    }

    @Test
    @DisplayName("灰色に潰すのはグリフ固有の理由だけ（タブ全体に当たる理由では潰さない）")
    void onlyGlyphSpecificReasonsGrayOut() {
        Set<GlyphBlockReason> graysOut = EnumSet.of(
                GlyphBlockReason.TIER_TOO_HIGH,
                GlyphBlockReason.DUPLICATE_EFFECT,
                GlyphBlockReason.FORM_INCOMPATIBLE,
                GlyphBlockReason.AUGMENT_INCOMPATIBLE,
                GlyphBlockReason.AUGMENT_LIMIT);

        for (GlyphBlockReason reason : GlyphBlockReason.values()) {
            assertEquals(graysOut.contains(reason), reason.glyphSpecific(),
                    reason + " の分類が仕様と違う。"
                            + "『形態をまだ選んでいない』『満杯』『先頭スロットが埋まっている』は"
                            + "そのタブの全グリフに同時に当てはまるので潰さない ——"
                            + "潰すと一覧が一色になり、どれを解放済みかが絵から消える");
        }

        assertEquals(false, GlyphBlockReason.PERK_MISSING.glyphSpecific(),
                "パーク未所持は鍵が担当する。灰色で上書きするとスキルツリーへの導線が消える");
    }
}
