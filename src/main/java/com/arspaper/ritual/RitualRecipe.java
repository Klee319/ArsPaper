package com.arspaper.ritual;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 儀式レシピの定義。
 * Ritual Coreを中心に、周囲のPedestalに素材を配置して発動。
 *
 * @param id              レシピ識別子
 * @param name            表示名
 * @param coreItem        コアに置くべき素材（null = コアアイテム不要）
 * @param pedestalItems   Pedestalに置くべき素材リスト（順不同）
 * @param sourceRequired  必要なSource量
 * @param resultId        カスタムアイテムの場合のID（nullならバニラ）
 * @param resultMaterial  バニラアイテムの場合のMaterial（nullならカスタム）
 * @param effectType      儀式タイプ（"craft"=通常, "world_effect"=ワールド効果, "thread"=スレッド適用）
 * @param effectParams    エフェクトパラメータ（effect-typeごとの追加パラメータ）
 * @param resultAmount    結果アイテムの数量（1以上）
 */
public record RitualRecipe(
    String id,
    String name,
    RitualIngredient coreItem,
    List<RitualIngredient> pedestalItems,
    int sourceRequired,
    String resultId,
    Material resultMaterial,
    String effectType,
    Map<String, String> effectParams,
    int resultAmount
) {

    /**
     * 後方互換コンストラクタ（resultAmount=1）。
     */
    public RitualRecipe(String id, String name, RitualIngredient coreItem,
                        List<RitualIngredient> pedestalItems, int sourceRequired,
                        String resultId, Material resultMaterial,
                        String effectType, Map<String, String> effectParams) {
        this(id, name, coreItem, pedestalItems, sourceRequired, resultId, resultMaterial, effectType, effectParams, 1);
    }

    /**
     * 後方互換コンストラクタ（effectType/effectParamsなし）。
     */
    public RitualRecipe(String id, String name, RitualIngredient coreItem,
                        List<RitualIngredient> pedestalItems, int sourceRequired,
                        String resultId, Material resultMaterial) {
        this(id, name, coreItem, pedestalItems, sourceRequired, resultId, resultMaterial, "craft", Map.of(), 1);
    }

    /**
     * Pedestalの素材とコアアイテムが一致するか（順不同）チェック。
     */
    public boolean matches(RitualIngredient providedCoreItem, List<RitualIngredient> pedestalIngredients) {
        // コアアイテムの一致チェック
        if (coreItem != null) {
            if (providedCoreItem == null || !coreItem.equals(providedCoreItem)) return false;
        }

        if (pedestalIngredients.size() != pedestalItems.size()) return false;

        Map<RitualIngredient, Integer> required = toFrequencyMap(pedestalItems);
        Map<RitualIngredient, Integer> provided = toFrequencyMap(pedestalIngredients);
        return required.equals(provided);
    }

    private static Map<RitualIngredient, Integer> toFrequencyMap(List<RitualIngredient> ingredients) {
        Map<RitualIngredient, Integer> map = new HashMap<>();
        for (RitualIngredient ing : ingredients) {
            map.merge(ing, 1, Integer::sum);
        }
        return map;
    }

    public boolean isCustomResult() {
        return resultId != null;
    }

    public boolean isCraftType() {
        return "craft".equals(effectType);
    }
}
