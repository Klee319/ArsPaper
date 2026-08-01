package com.arspaper.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * レシピ一覧GUI({@link RecipeBrowserGui})の1行分のデータ。
 *
 * <p>2026-07-27 のレシピGUI改修(素材⇔レシピ相互ジャンプ / ソート / 絞り込み / 名前検索)で
 * {@code RecipeBrowserGui} の private inner class から切り出した。切り出したのは
 * {@link RecipeBrowserFilter} が並べ替え・絞り込みを純粋関数として扱えるようにするため
 * (GUI描画とリスト加工を同じクラスに同居させると800行を大きく超え、テストもできない)。
 *
 * <p>可変フィールドのままなのは、{@code RecipeBrowserGui#collectAllRecipes()} が
 * 儀式/作業台/TFカタログの3経路で段階的に組み立てているため。組み立て後は読み取り専用として扱う。
 */
final class RecipeEntry {

    String id;
    String displayName;
    boolean isRitual;
    Material icon = Material.PAPER;
    ItemStack iconItem = null;
    String resultCustomId = null; // カスタムアイテム結果ID
    String effectType = null;     // 儀式エフェクトタイプ
    Map<String, String> effectParams = null; // エフェクトパラメータ
    String threadId = null;       // スレッドID
    // 儀式用
    String coreItem;
    List<String> ingredients = List.of();
    int source;
    // 作業台用
    List<String> shape = List.of();
    Map<String, String> ingredientMap = Map.of();
    int amount = 1;

    /**
     * このレシピが「作るもの」を素材トークンと同じ語彙で表したもの
     * ({@code custom:<id>} または Material 名)。素材クリックで生産レシピへ飛ぶための突き合わせキー。
     * 解決できないときは null(ジャンプ対象にならないだけで、表示は従来どおり)。
     */
    String resultToken;

    /** 並べ替え用にキャッシュした使用スキル種別(未設定なら空文字)。 */
    String sortSkill = "";
    /** 並べ替え用にキャッシュした使用可能レベル(未設定なら0)。 */
    int sortLevel = 0;
    /**
     * 並べ替え用にキャッシュした5分類(防具/素材/武器/ツール/その他)。
     * 「他のレシピの素材か」を見る必要があるので、全レシピを集め終わってからまとめて決まる
     * ({@code RecipeBrowserGui#applyCategories})。未確定の間は「その他」。
     */
    RecipeCategory sortCategory = RecipeCategory.OTHER;

    /**
     * このレシピが消費する素材トークンを重複なしで返す。
     * 作業台レシピは {@code ingredientMap} の値、儀式レシピはコア + 台座素材。
     */
    Set<String> ingredientTokens() {
        Set<String> tokens = new LinkedHashSet<>();
        if (isRitual) {
            addToken(tokens, coreItem);
            for (String ing : ingredients) addToken(tokens, ing);
        } else {
            for (String ing : ingredientMap.values()) addToken(tokens, ing);
        }
        return tokens;
    }

    private static void addToken(Set<String> tokens, String token) {
        if (token != null && !token.isBlank()) {
            tokens.add(token);
        }
    }

    /**
     * 検索・ソート用に、並べ替えキーとして安定した表示名を返す。
     * {@code displayName} には儀式の「×N」接尾辞が付くことがあるので、そこだけ落とす。
     */
    String sortName() {
        if (displayName == null) return "";
        int idx = displayName.lastIndexOf(" ×");
        return idx > 0 ? displayName.substring(0, idx) : displayName;
    }

    /** 種別順のキー。使用スキルがあればそれ、無ければ結果アイテムの Material 名。 */
    String sortKind() {
        if (sortSkill != null && !sortSkill.isBlank()) {
            return sortSkill;
        }
        return icon == null ? "" : icon.name();
    }
}
