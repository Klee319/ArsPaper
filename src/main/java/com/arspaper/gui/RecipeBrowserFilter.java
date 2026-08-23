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
        // N4: 「防具/素材/武器/ツール/その他」の5分類。KIND は使用スキル文字列を並べるだけで
        // 未設定分が Material 名で散らばるため、「防具だけ見たい」には使えなかった。
        CATEGORY("分類順(防具/素材/武器/ツール/その他)"),
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
        RITUAL("儀式レシピ"),
        // 2026-08-19 W-123 実サーバ報告「日の出の儀式やスレッド枠付与の儀式まで儀式レシピに
        // 混ざっている」。これらは【アイテムを作らない儀式】で、探す動機も探し方も
        // 「何が作れるか」とは別物なので、儀式レシピから外して独立の絞り込みにする。
        RITUAL_EFFECT("儀式エフェクト"),
        // 2026-08-20 W-167 ユーザー要望「カスタムで追加したポーションのレシピ(幸運など)が
        // 掲載されるようにしてほしい。作業台・儀式のようにソートカテゴリに醸造を追加する」。
        BREWING("醸造レシピ");

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
                // 醸造を足したので「儀式でない＝作業台」は成り立たなくなった。
                // ここを直し忘れると醸造レシピが作業台カテゴリにも二重に出る。
                case WORKBENCH -> !entry.isRitual && !entry.isBrewing;
                case RITUAL -> entry.isRitual && !isEffectRitual(entry);
                case RITUAL_EFFECT -> entry.isRitual && isEffectRitual(entry);
                case BREWING -> entry.isBrewing;
            };
        }
    }

    /**
     * 「アイテムを作らない儀式」か。
     *
     * <p>判定は {@code RitualRecipe#isCraft()}(= {@code "craft".equals(effectType)})の裏返しで、
     * <b>この GUI が独自に線を引いているのではなく、儀式の実行側が既に使っている区分をそのまま
     * 借りている</b>。ここで独自の一覧(sunrise / thread_slot_expand / …)を持つと、
     * 新しい effect-type を足したときに絞り込みだけが無言で取りこぼす。
     *
     * <p>対象は 日の出 / 月の出 / 天候 / 飛行 / 修復 / 召喚 / エンチャント本化 / スレッド付与 /
     * スレッド枠拡張 など、{@code effect-type} が {@code craft} 以外の全部。
     * {@code effectType} が null の儀式(旧経路で作られた行)は、結果アイテムを持つ従来の儀式として扱う。
     */
    static boolean isEffectRitual(RecipeEntry entry) {
        return entry.effectType != null && !"craft".equals(entry.effectType);
    }

    /**
     * 圧縮の中間段階と解凍を隠した既定の絞り込み。
     *
     * @see #arrange(List, SortMode, KindMode, String, boolean)
     */
    static List<RecipeEntry> arrange(List<RecipeEntry> source, SortMode sort, KindMode kind,
                                     String search) {
        return arrange(source, sort, kind, search, false);
    }

    /**
     * 絞り込み → 並べ替えを適用した新しいリストを返す(引数のリストは変更しない)。
     *
     * @param kind   表示するレシピ種別(作業台/儀式/儀式エフェクト/すべて)。
     * @param search ワイルドカード検索語。null/空なら検索なし。
     * @param showCompressionDetails 圧縮の中間段階と解凍レシピも出すか(既定 false)。
     */
    static List<RecipeEntry> arrange(List<RecipeEntry> source, SortMode sort, KindMode kind,
                                     String search, boolean showCompressionDetails) {
        return arrange(source, sort, kind, search, showCompressionDetails, null);
    }

    /**
     * ピン止め(お気に入り)での絞り込みまで含めた版 (2026-08-23)。
     *
     * @param pinnedOnly null なら絞り込みなし。非 null なら<b>id がこの集合にあるものだけ</b>を残す。
     */
    static List<RecipeEntry> arrange(List<RecipeEntry> source, SortMode sort, KindMode kind,
                                     String search, boolean showCompressionDetails,
                                     java.util.Set<String> pinnedOnly) {
        List<RecipeEntry> result = new ArrayList<>();
        Pattern pattern = compileGlob(search);
        // 「その連鎖の最大段」は検索語や種別で変わってはいけない(検索するたびに出る段が
        // 変わると、同じアイテムが有ったり無かったりするように見える)。母集団全体で1度だけ決める。
        Map<String, Integer> topStages = showCompressionDetails ? Map.of() : topCompressionStages(source);
        for (RecipeEntry entry : source) {
            if (entry == null) continue;
            if (pinnedOnly != null && (entry.id == null || !pinnedOnly.contains(entry.id))) continue;
            if (pattern != null && !pattern.matcher(entry.sortName()).matches()) continue;
            if (kind != null && !kind.accepts(entry)) continue;
            // ピンを明示した行は圧縮の間引きを通さない。自分でピン止めしたのに
            // 「中間段だから」で消えると、お気に入り一覧が無言で歯抜けになる。
            if (pinnedOnly == null && !showCompressionDetails
                    && isHiddenCompressionStep(entry, topStages)) continue;
            result.add(entry);
        }
        Comparator<RecipeEntry> comparator = comparatorFor(sort);
        if (comparator != null) {
            result.sort(comparator);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 圧縮レシピの間引き (2026-08-19 W-122 / W-99)
    //
    // 圧縮素材は materials.yml だけで 171 件・62 連鎖あり、1連鎖が最大5段ある。
    // 全部並べるとレシピ一覧が圧縮素材で埋まって他が探せない。
    // 既定では【各連鎖の最大段だけ】を出し、中間段と解凍(reversible の裏レシピ)は隠す。
    //
    // 判定はレシピキー(RecipeEntry#id)から行う。キーは Ars が materials.yml のエントリ id を
    // そのまま使い(`stone_3x`)、逆レシピは `_decompress` を足す(RecipeManager)。
    // TF カタログ由来は `catalog_` 接頭辞が付くのでそこだけ落とす。
    // ★ 表示名で判定しない: 「81倍圧縮石」のような表示名は yml の自由記述で、
    //   倍率の書き方が揺れた瞬間に間引きが無言で効かなくなる。
    // ------------------------------------------------------------------

    /** TrinityForge カタログレシピのキー接頭辞({@code CatalogRecipeRegistrar} と対)。 */
    private static final String CATALOG_KEY_PREFIX = "catalog_";
    /** {@code reversible: true} が自動登録する解凍レシピのキー接尾辞({@code RecipeManager})。 */
    private static final String DECOMPRESS_SUFFIX = "_decompress";
    /** {@code <base>_<段>x} 形式の圧縮アイテム id。 */
    private static final Pattern COMPRESSION_ID = Pattern.compile("^(.+)_(\\d{1,2})x$");

    /** 解凍(圧縮を戻す)レシピか。 */
    static boolean isDecompression(RecipeEntry entry) {
        return entry != null && entry.id != null && entry.id.endsWith(DECOMPRESS_SUFFIX);
    }

    /** 圧縮連鎖の基底 id。圧縮レシピでなければ null。解凍レシピも同じ連鎖として扱う。 */
    static String compressionBase(RecipeEntry entry) {
        var m = compressionMatcher(entry);
        return m == null ? null : m.group(1);
    }

    /** 圧縮連鎖の段数(1 始まり)。圧縮レシピでなければ 0。 */
    static int compressionStage(RecipeEntry entry) {
        var m = compressionMatcher(entry);
        return m == null ? 0 : Integer.parseInt(m.group(2));
    }

    private static java.util.regex.Matcher compressionMatcher(RecipeEntry entry) {
        if (entry == null || entry.id == null) return null;
        String id = entry.id;
        if (id.startsWith(CATALOG_KEY_PREFIX)) id = id.substring(CATALOG_KEY_PREFIX.length());
        if (id.endsWith(DECOMPRESS_SUFFIX)) id = id.substring(0, id.length() - DECOMPRESS_SUFFIX.length());
        var m = COMPRESSION_ID.matcher(id);
        return m.matches() ? m : null;
    }

    /** 基底 id -> その連鎖に実在する最大段。 */
    private static Map<String, Integer> topCompressionStages(List<RecipeEntry> source) {
        Map<String, Integer> top = new java.util.HashMap<>();
        for (RecipeEntry entry : source) {
            String base = compressionBase(entry);
            if (base == null) continue;
            top.merge(base, compressionStage(entry), Math::max);
        }
        return top;
    }

    /** 既定表示で隠すレシピか(中間段の圧縮 / すべての解凍)。 */
    private static boolean isHiddenCompressionStep(RecipeEntry entry, Map<String, Integer> topStages) {
        String base = compressionBase(entry);
        if (base == null) return false;
        if (isDecompression(entry)) return true;
        Integer top = topStages.get(base);
        return top != null && compressionStage(entry) < top;
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
            // 分類は enum の宣言順(防具→素材→武器→ツール→その他)で並べる
            case CATEGORY -> Comparator.<RecipeEntry, Integer>comparing(
                    e -> e.sortCategory == null ? RecipeCategory.OTHER.ordinal()
                            : e.sortCategory.ordinal()).thenComparing(byName);
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
