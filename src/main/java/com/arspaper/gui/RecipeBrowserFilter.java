package com.arspaper.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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

    /**
     * コンパイル済みパターンの上限付きキャッシュ。検索語はプレイヤーの自由入力なので、
     * 無制限に貯めると悪意ある入力でヒープを膨らませられる。
     */
    private static final int MAX_CACHE = 256;
    private static final Map<String, Pattern> CACHE = new ConcurrentHashMap<>();

    private RecipeBrowserFilter() {
    }

    /**
     * 並べ替えモード。ボタン1つで {@link #next()} 順に巡回する。
     *
     * <p><b>宣言順がそのまま巡回順であり、先頭が初期値</b>(2026-07-30 ユーザー確定
     * 「ソート順の規定を名前にし、登録順は一番下に」)。登録順は最後に置いてある。
     */
    enum SortMode {
        NAME("名前順"),
        KIND("種別順(スキル→素材)"),
        LEVEL("使用可能レベル順"),
        DEFAULT("登録順");

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

    /**
     * レシピ種別の絞り込み。ソートとは独立したボタンで巡回する。
     *
     * <p>2026-07-30 ユーザー確定で、従来の「解放済み/未解放」ボタンをこれに置換した。
     * 解放状態は各レシピのアイコン(施錠表示)で分かる一方、作業台と儀式は同じ一覧に
     * 混在していて探しづらかったため。
     */
    enum KindMode {
        ALL("すべて"),
        WORKBENCH("作業台レシピ"),
        RITUAL("儀式レシピ");

        private final String label;

        KindMode(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        KindMode next() {
            KindMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        /** このモードが対象とするレシピか。{@link #ALL} は常に true。 */
        boolean accepts(RecipeEntry entry) {
            return switch (this) {
                case ALL -> true;
                case WORKBENCH -> !entry.isRitual;
                case RITUAL -> entry.isRitual;
            };
        }
    }

    /**
     * 絞り込み → 並べ替えを適用した新しいリストを返す(引数のリストは変更しない)。
     *
     * @param kind   表示するレシピ種別(作業台/儀式/すべて)。
     * @param search ワイルドカード検索語。null/空なら検索なし。
     */
    static List<RecipeEntry> arrange(List<RecipeEntry> source, SortMode sort, KindMode kind,
                                     String search) {
        List<RecipeEntry> result = new ArrayList<>();
        Pattern pattern = compileGlob(search);
        for (RecipeEntry entry : source) {
            if (entry == null) continue;
            if (pattern != null && !pattern.matcher(entry.sortName()).matches()) continue;
            if (kind != null && !kind.accepts(entry)) continue;
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
     * <p><b>TrinityForge 側の {@code com.trinityforge.progression.GlobMatcher} と意図的に同じ実装を
     * 持っている。</b>このGUIは TrinityForge が入っていなくても動く必要があるため TF のクラスを
     * 直接は呼べない。片方だけ直すと同じ検索語で図鑑とレシピ一覧の結果が食い違うので、
     * 仕様(部分一致への昇格・大文字小文字無視・長さ上限・壊れたパターンの扱い)を変えるときは
     * <b>必ず両方そろえること</b>。
     *
     * @return 検索なし、または解釈できないパターンのときは null(＝絞り込みなし)
     */
    static Pattern compileGlob(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > MAX_PATTERN_LENGTH) {
            trimmed = trimmed.substring(0, MAX_PATTERN_LENGTH);
        }
        String key = trimmed.toLowerCase(Locale.ROOT);
        Pattern cached = CACHE.get(key);
        if (cached != null) return cached;
        Pattern built = buildGlob(key);
        if (built != null && CACHE.size() < MAX_CACHE) {
            CACHE.put(key, built);
        }
        return built;
    }

    private static Pattern buildGlob(String glob) {
        String effective = (glob.indexOf('*') < 0 && glob.indexOf('?') < 0) ? "*" + glob + "*" : glob;
        StringBuilder regex = new StringBuilder(effective.length() * 2);
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < effective.length(); i++) {
            char c = effective.charAt(i);
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
        try {
            return Pattern.compile(regex.toString(),
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
        } catch (PatternSyntaxException ex) {
            // 壊れたパターンで全件消すより絞り込み無しに倒す(TF GlobMatcher と同じ判断)。
            return null;
        }
    }
}
