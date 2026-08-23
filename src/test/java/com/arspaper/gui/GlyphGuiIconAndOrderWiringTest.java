package com.arspaper.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * グリフを並べる3画面が<b>同じ1本の定義</b>を通ることを固定する
 * （2026-08-22 ユーザー報告「グリフレシピ、グリフ設定、グリフ解放で順番もそろえてほしい」
 * 「どれがどの魔法かぱっと見で分からない」）。
 *
 * <p><b>何が壊れていたか。</b> 3画面がそれぞれ自前で並べ、自前でアイコンを決めていた:
 *
 * <ul>
 *   <li>{@code ScribingTableGui}（グリフ解放）: 種類 → ティア / 未解放は石炭</li>
 *   <li>{@code GlyphBrowserGui}（グリフレシピ）: 種類 → ティア → <b>ID順</b> / 未解放は石炭</li>
 *   <li>{@code SpellCraftingGui}（グリフ設定）: ティアのみ / 未解放はバリア・使用不可は灰色染料</li>
 * </ul>
 *
 * <p>3箇所に散らばった switch とソートは、片方だけ直しても<b>実機で並べて見るまで
 * 食い違いに気づけない</b>（画面を切り替えて初めて分かる）。単体テストでは各画面が
 * 「正しく描けている」ようにしか見えないので、<b>1本を通っているか</b>を直接縛る。
 *
 * <p><b>2026-08-22 の追記。</b> 「全部に個別アイコン」まで振り切ったら
 * 「解放したのか解放してないのか直感的にわからなくなった」という逆向きの報告が来た。
 *
 * <p><b>2026-08-23 の確定仕様（画面ごとに違う）。</b> 潰し方は 3 画面で揃えない ──
 * 画面ごとに「見に来ている目的」が違うため:
 *
 * <ul>
 *   <li>{@code ScribingTableGui}（グリフ解放）: 未解放=石炭 / 他=個別アイコン</li>
 *   <li>{@code GlyphBrowserGui}（グリフレシピ）: <b>全部が個別アイコン</b>
 *       ── 未解放こそが主役の画面なので潰すと素材を引く手掛かりが消える</li>
 *   <li>{@code SpellCraftingGui}（グリフ配置／呪文編集）:
 *       <b>パーク未所持=鍵</b> / 未解放=石炭 / 他=個別アイコン
 *       ── 直し方が違う（スキルツリー / 筆記台）ので絵を分ける</li>
 * </ul>
 *
 * この割り当ては {@link #eachGuiPassesExactlyTheStatesItsSpecCallsFor} が固定する。
 * <b>画面側に材質を直接書いてはいけない</b>のは変わらない ── 決めるのは {@code GlyphIcons} 1箇所で、
 * 画面ごとに書くと 3 画面でずれる。
 *
 * <p>ソース文字列で縛るのは筋が悪いが、GUI の描画は {@code Player}/{@code Inventory} が要り
 * このフォークのテスト基盤（MockBukkit なし）では動かせない。並び自体の正しさは
 * {@code GlyphOrderTest}、アイコンの網羅は {@code GlyphIconCoverageTest} が別途見ている。
 */
class GlyphGuiIconAndOrderWiringTest {

    /** グリフを並べる3画面。 */
    private static final List<String> GLYPH_GUIS = List.of(
            "ScribingTableGui",   // グリフ解放（筆記台）
            "GlyphBrowserGui",    // グリフレシピ（解放素材）
            "SpellCraftingGui");  // グリフ設定（呪文編集）

    private static String source(String simpleName) {
        Path path = Path.of("src/main/java/com/arspaper/gui/" + simpleName + ".java");
        assertTrue(Files.isRegularFile(path), "ソースが見つからない: " + path.toAbsolutePath());
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("3画面ともアイコンは GlyphIcons を通す(画面ごとの switch を持たない)")
    void allGuisResolveIconsThroughGlyphIcons() {
        for (String gui : GLYPH_GUIS) {
            String src = source(gui);
            assertTrue(src.contains("GlyphIcons.iconFor("),
                    gui + " が GlyphIcons を通っていない");
            assertFalse(src.contains("case FORM -> Material.DIAMOND")
                            || src.contains("case FORM -> unlocked ? Material.DIAMOND"),
                    gui + " に種類ごとのアイコン switch が残っている(3画面でずれる)");
            assertFalse(src.contains("Material.COAL"),
                    gui + " が石炭を直接書いている。未解放の材質を決めるのは GlyphIcons.LOCKED_ICON の1箇所"
                            + "(画面ごとに書くと3画面でずれる)");
            assertFalse(src.contains("Material.TRIAL_KEY"),
                    gui + " が鍵を直接書いている。パーク未所持の材質を決めるのは"
                            + " GlyphIcons.PERK_LOCKED_ICON の1箇所");
        }
    }

    /**
     * {@code GlyphIcons.iconFor(...)} 呼び出しの最大引数個数。
     *
     * <p>2引数=解放状態で潰さない / 3引数=未解放を潰す / 4引数=パーク未所持も潰す。
     * 文字列の部分一致だと {@code plugin.getGlyphConfig()} の内側の括弧に引っかかるので、
     * 括弧の対応を数えて判定する。
     */
    private static int maxIconForArity(String src) {
        final String call = "GlyphIcons.iconFor(";
        int max = 0;
        for (int at = src.indexOf(call); at >= 0; at = src.indexOf(call, at + 1)) {
            int depth = 1;
            int args = 1;
            for (int i = at + call.length(); i < src.length() && depth > 0; i++) {
                char c = src.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                } else if (c == ',' && depth == 1) {
                    args++;
                }
            }
            max = Math.max(max, args);
        }
        return max;
    }

    @Test
    @DisplayName("画面ごとに潰す状態が違う(解放=未解放だけ / レシピ=潰さない / 配置=パークも)")
    void eachGuiPassesExactlyTheStatesItsSpecCallsFor() {
        assertEquals(3, maxIconForArity(source("ScribingTableGui")),
                "グリフ解放(筆記台)は解放状態を渡して未解放を石炭へ潰すこと。"
                        + "全部に個別アイコンを付けると『解放済みかどうかが絵から消える』"
                        + "(2026-08-22 報告: 1個ずつカーソルを当てないと分からない)");

        assertEquals(2, maxIconForArity(source("GlyphBrowserGui")),
                "グリフレシピ(解放素材)は潰さず全部を個別アイコンにすること(2026-08-23 指示)。"
                        + "未解放こそが主役の画面なので、1種類の絵に潰すと素材表を引く手掛かりが消える");

        assertEquals(4, maxIconForArity(source("SpellCraftingGui")),
                "グリフ配置(呪文編集)はパークゲートも渡すこと(2026-08-23 指示)。"
                        + "未解放(筆記台へ行け)とパーク未所持(スキルツリーへ行け)は直し方が違うので、"
                        + "同じ石炭に潰すと次にどこへ行けばよいか分からない");
    }

    @Test
    @DisplayName("3画面とも並びは GlyphOrder を通す(画面ごとのソートを持たない)")
    void allGuisSortThroughGlyphOrder() {
        for (String gui : GLYPH_GUIS) {
            String src = source(gui);
            assertTrue(src.contains("GlyphOrder.canonical("),
                    gui + " が GlyphOrder を通っていない");
            assertFalse(src.contains("thenComparingInt(SpellComponent::getTier)"),
                    gui + " が独自にティア順へ並べ替えている");
            assertFalse(src.contains("getSpellRegistry().getByType("),
                    gui + " が getByType を使っている(ティア順に潰されて対のペアが割れる)");
        }
    }
}
