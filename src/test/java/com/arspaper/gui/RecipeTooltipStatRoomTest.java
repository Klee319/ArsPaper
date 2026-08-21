package com.arspaper.gui;

import com.arspaper.integration.TrinityForgeStatPreview;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * レシピ詳細のツールチップで、最低ステの行数を<b>残りの余白から決める</b>ことを固定する
 * (2026-08-22 ユーザー報告「まだloreの長さに余裕があるのに省略されてしまい、
 * スレッド枠やマナ回復量などのステータスが移っていない」)。
 *
 * <p><b>何が壊れていたか。</b> 上限が {@code MAX_LINES = 8} の固定値だった。
 * ツールチップの高さを決めるのは lore 全体なのに、lore が2〜3行しかないアイテムでも
 * 8行で頭打ちになる。表示順は {@code lore.yml} の category → order で固定なので、
 * <b>毎回まったく同じステだけがこぼれる</b> ── 実際に報告された
 * 魔術師の守護ヘルメット(表示対象12件)では `thread-slots` / `mana-bonus` / `mana-regen` /
 * `move-speed` の4件が常に「…ほか」に落ちていた。
 *
 * <p>ここで検査するのは行数の算数だけ。表示の中身({@code fixed + random.min} を引くこと)は
 * {@link RecipeBrowserCategorySortTest} 側が見ている。
 */
class RecipeTooltipStatRoomTest {

    /** 報告のあった魔術師の守護ヘルメットで表示対象になるステの件数。 */
    private static final int MAGE_HELMET_DISPLAYED_STATS = 12;
    /** 壊れていたときの固定上限。 */
    private static final int OLD_FIXED_CAP = 8;

    @Test
    @DisplayName("lore が短いレシピでは、旧固定上限の8行より多く出せる")
    void shortLoreLeavesRoomForMoreThanTheOldFixedCap() {
        // 完成品スロットの典型: lore はまだ空で、あとに「空行 + クリック案内」の2行が付く。
        int room = RecipeBrowserGui.statLineRoom(0, 2);

        assertTrue(room > OLD_FIXED_CAP,
                "余白があるのに8行で頭打ちのまま(room=" + room + ")");
        assertTrue(room >= MAGE_HELMET_DISPLAYED_STATS,
                "報告のあった防具(表示対象" + MAGE_HELMET_DISPLAYED_STATS + "件)が全部出せない(room=" + room + ")");
    }

    @Test
    @DisplayName("lore が伸びるとステ行の枠はそのぶん減る(ツールチップの総行数が予算を超えない)")
    void longerLoreShrinksTheStatRoom() {
        int shortLore = RecipeBrowserGui.statLineRoom(0, 0);
        int longLore = RecipeBrowserGui.statLineRoom(10, 0);

        assertEquals(shortLore - 10, longLore, "lore 1行につきステ枠が1行減ること");
        // 総行数 = lore に出ない行 + lore + 空行 + 見出し + ステ行。予算を超えないこと。
        assertTrue(10 + 2 + longLore <= shortLore + 2, "予算を超えて積める計算になっている");
    }

    @Test
    @DisplayName("lore が予算を食い尽くしても、見出しだけの空ブロックにはしない")
    void veryLongLoreStillShowsAFewStats() {
        int room = RecipeBrowserGui.statLineRoom(100, 4);

        assertTrue(room < 0, "前提: この lore 長では計算上マイナスになること");
        assertTrue(TrinityForgeStatPreview.lineLimit(room) > 0,
                "0行に切り詰めると『見出し + …ほか』だけの意味不明なブロックになる");
    }
}
