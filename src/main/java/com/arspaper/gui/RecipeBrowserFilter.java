package com.arspaper.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * レシピ一覧の並べ替え・絞り込み・名前検索 (2026-07-27 レシピGUI改修)。
 *
 * <p>GUI描画を一切持たない純粋関数の集まりで、{@link RecipeBrowserGui} はここへ委譲するだけ。
 * 仕様(ユーザー確定):
 * <ul>
 *   <li>名前検索はワイルドカード({@code *} / {@code ?})。ワイルドカードを含まない入力は部分一致。</li>
 *   <li>種別順 = ステータス(item-stats)に設定された使用スキル種別、未設定なら結果アイテムの Material。</li>
 *   <li>「素材数順」ではなく<b>使用可能レベル順</b>(use-level)。</li>
 *   <li>解放済み / 未解放 / すべて は<b>ソートとは別のボタン</b>で切り替える。</li>
 * </ul>
 */
final class RecipeBrowserFilter {

    /** 正規表現の暴発を防ぐための入力長上限(これを超える検索語は先頭だけ使う)。 */
    private static final int MAX_PATTERN_LENGTH = 128;

    private RecipeBrowserFilter() {
    }

    /** 並べ替えモード。ボタン1つで {@link #next()} 順に巡回する。 */
    enum SortMode {
        DEFAULT("既定(登録順)"),
        NAME("名前順"),
        KIND("種別順(スキル→素材)"),
        LEVEL("使用可能レベル順");

        private final String label;

        SortMode(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    /** 解放状態の絞り込み。ソートとは独立したボタンで巡回する。 */
    enum FilterMode {
        ALL("すべて"),
        UNLOCKED("解放済みのみ"),
        LOCKED("未解放のみ");

        private final String label;

        FilterMode(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        FilterMode next() {
            FilterMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    /**
     * 絞り込み → 並べ替えを適用した新しいリストを返す(引数のリストは変更しない)。
     *
     * @param unlocked レシピが解放済みかを返す述語。{@link FilterMode#ALL} のときは呼ばれない。
     * @param search   ワイルドカード検索語。null/空なら検索なし。
     */
    static List<RecipeEntry> arrange(List<RecipeEntry> source, SortMode sort, FilterMode filter,
                                     String search, Predicate<RecipeEntry> unlocked) {
        List<RecipeEntry> result = new ArrayList<>();
        Pattern pattern = compileGlob(search);
        for (RecipeEntry entry : source) {
            if (entry == null) continue;
            if (pattern != null && !pattern.matcher(entry.sortName()).matches()) continue;
            if (filter != FilterMode.ALL) {
                boolean open = unlocked == null || unlocked.test(entry);
                if (filter == FilterMode.UNLOCKED && !open) continue;
                if (filter == FilterMode.LOCKED && open) continue;
            }
            result.add(entry);
        }
        Comparator<RecipeEntry> comparator = comparatorFor(sort);
        if (comparator != null) {
            result.sort(comparator);
        }
        return result;
    }

    private static Comparator<RecipeEntry> comparatorFor(SortMode sort) {
        Comparator<RecipeEntry> byName =
                Comparator.comparing(e -> e.sortName().toLowerCase(Locale.ROOT));
        return switch (sort) {
            case DEFAULT -> null; // 収集順のまま(儀式 → 作業台 → TFカタログ)
            case NAME -> byName;
            // 同じ種別/レベル内は名前順で安定させる(ページをめくるたび順序が変わらないように)
            case KIND -> Comparator.<RecipeEntry, String>comparing(
                    e -> e.sortKind().toLowerCase(Locale.ROOT)).thenComparing(byName);
            case LEVEL -> Comparator.<RecipeEntry, Integer>comparing(e -> e.sortLevel)
                    .thenComparing(byName);
        };
    }

    /**
     * ワイルドカード検索語を {@link Pattern} へ変換する。
     * {@code *} / {@code ?} を含まない入力は「部分一致」として扱う(利用者が毎回 {@code *foo*} と
     * 書かなくて済むように — ユーザー確定仕様「名前検索はワイルドカードで」の実用形)。
     *
     * @return 検索なしのときは null
     */
    static Pattern compileGlob(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > MAX_PATTERN_LENGTH) {
            trimmed = trimmed.substring(0, MAX_PATTERN_LENGTH);
        }
        if (trimmed.indexOf('*') < 0 && trimmed.indexOf('?') < 0) {
            trimmed = "*" + trimmed + "*";
        }
        StringBuilder regex = new StringBuilder(trimmed.length() * 2);
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '*' || c == '?') {
                if (literal.length() > 0) {
                    regex.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                regex.append(c == '*' ? ".*" : ".");
            } else {
                literal.append(c);
            }
        }
        if (literal.length() > 0) {
            regex.append(Pattern.quote(literal.toString()));
        }
        return Pattern.compile(regex.toString(),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
    }
}
