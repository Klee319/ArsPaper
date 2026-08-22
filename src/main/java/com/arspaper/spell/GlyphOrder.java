package com.arspaper.spell;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * グリフの<b>正典の並び順</b>。3画面すべてがここを通る。
 *
 * <p><b>なぜ要るか。</b> 2026-08-22 のユーザー報告
 * 「グリフレシピ、グリフ設定、グリフ解放で順番もそろえてほしい」。それまで3画面が別々に並べていた:
 *
 * <ul>
 *   <li>グリフ解放（筆記台 {@code ScribingTableGui}）: 種類 → ティア</li>
 *   <li>グリフレシピ（{@code GlyphBrowserGui}）: 種類 → ティア → <b>ID のアルファベット順</b></li>
 *   <li>グリフ設定（{@code SpellCraftingGui}）: ティアのみ → 超増強をベースの直後へ</li>
 * </ul>
 *
 * <p><b>採用した並びは「種類 → 登録順」。ティアでは並べない。</b>
 * {@code ArsPaper#registerComponents} の登録順は<b>手で意味づけされたグループ</b>になっている ——
 * 効果は攻撃系／移動系／生存系／ブロック系／ユーティリティ系、増強は
 * 増幅⇔減衰・延長⇔短縮・延伸⇔収縮・加速⇔減速…という<b>対のペア</b>。
 * ティアで並べ替えるとこのグループが完全に崩れる（延伸=T2 と 収縮=T1 が離れる、
 * 害悪の隣に収穫が来る）。「ぱっと見で分からない」という報告に対して、
 * 意味のまとまりを保つ方が効く。ティアは lore に出ているので情報は失われない。
 *
 * <p>超増強（{@code super_*}）だけは例外で、<b>対応するベースの直後</b>へ寄せる
 * （グリフ設定が元々やっていた挙動。登録順でも末尾15個にまとまってしまうため）。
 */
public final class GlyphOrder {

    private GlyphOrder() {
    }

    /** 超増強のIDに付く接頭辞。 */
    private static final String SUPER_PREFIX = "super_";

    /**
     * 全グリフを正典順に並べる（種類 → 登録順、超増強はベースの直後）。
     *
     * @param glyphs {@code SpellRegistry#getAll()}。<b>登録順であること</b>
     *               （{@code LinkedHashMap} なので保たれる）
     */
    public static List<SpellComponent> canonical(Collection<SpellComponent> glyphs) {
        List<SpellComponent> out = new ArrayList<>();
        for (SpellComponent.ComponentType type : SpellComponent.ComponentType.values()) {
            out.addAll(canonical(glyphs, type));
        }
        return List.copyOf(out);
    }

    /** 1種類ぶんだけ正典順で返す（グリフ設定のタブ用）。 */
    public static List<SpellComponent> canonical(Collection<SpellComponent> glyphs,
                                                 SpellComponent.ComponentType type) {
        List<SpellComponent> ofType = new ArrayList<>();
        for (SpellComponent glyph : glyphs) {
            if (glyph != null && glyph.getType() == type) {
                ofType.add(glyph);
            }
        }
        return List.copyOf(superAfterBase(ofType, c -> c.getId().getKey()));
    }

    /**
     * 並べ替えの本体。Bukkit ランタイム無しで固定できるよう、{@link SpellComponent} から切り離してある
     * （このフォークのテスト基盤は MockBukkit/Mockito を持たない）。
     *
     * <p>入力の順序（＝登録順）を保ったまま、{@code super_<key>} を対応する {@code <key>} の直後へ移す。
     * ベースが存在しない超増強は、元の位置を保ったまま末尾へ回す。
     */
    static <T> List<T> superAfterBase(List<T> glyphs, Function<T, String> keyOf) {
        Set<String> keys = new HashSet<>();
        for (T glyph : glyphs) {
            keys.add(keyOf.apply(glyph));
        }

        List<T> out = new ArrayList<>(glyphs.size());
        Set<String> placed = new LinkedHashSet<>();
        for (T glyph : glyphs) {
            String key = keyOf.apply(glyph);
            if (placed.contains(key)) {
                continue;
            }
            // 超増強は「ベースが同じリストに居るなら」その直後まで待つ。
            if (key.startsWith(SUPER_PREFIX) && keys.contains(key.substring(SUPER_PREFIX.length()))) {
                continue;
            }
            out.add(glyph);
            placed.add(key);

            String superKey = SUPER_PREFIX + key;
            for (T candidate : glyphs) {
                if (superKey.equals(keyOf.apply(candidate)) && placed.add(superKey)) {
                    out.add(candidate);
                    break;
                }
            }
        }
        // ベースが見つからなかった超増強と、同一IDの重複登録の取りこぼし。
        for (T glyph : glyphs) {
            if (placed.add(keyOf.apply(glyph))) {
                out.add(glyph);
            }
        }
        return out;
    }

    /**
     * ティア順の比較子は<b>意図的に用意していない</b>。
     * 画面ごとに別の並びを足すと、また3画面がずれる（この class が生まれた理由そのもの）。
     * 並びを変えたいときは {@link #canonical} を1箇所直すこと。
     */
    static <T> List<T> byRegistrationOrder(List<T> glyphs, ToIntFunction<T> typeOrdinal,
                                           Function<T, String> keyOf) {
        List<T> out = new ArrayList<>(glyphs.size());
        int types = SpellComponent.ComponentType.values().length;
        for (int ordinal = 0; ordinal < types; ordinal++) {
            List<T> ofType = new ArrayList<>();
            for (T glyph : glyphs) {
                if (typeOrdinal.applyAsInt(glyph) == ordinal) {
                    ofType.add(glyph);
                }
            }
            out.addAll(superAfterBase(ofType, keyOf));
        }
        return out;
    }
}
