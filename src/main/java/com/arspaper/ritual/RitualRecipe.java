package com.arspaper.ritual;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 儀式レシピの定義。
 * Ritual Coreを中心に、周囲のPedestalに素材を配置して発動。
 *
 * @param id        レシピ識別子
 * @param name      表示名
 * @param pedestalItems Pedestalに置くべき素材リスト（順不同）
 * @param sourceRequired 必要なSource量
 * @param result    結果アイテム（カスタムアイテムIDまたはバニラMaterial）
 * @param resultId  カスタムアイテムの場合のID（nullならバニラ）
 */
public record RitualRecipe(
    String id,
    String name,
    List<Material> pedestalItems,
    int sourceRequired,
    String resultId,
    Material resultMaterial
) {

    /**
     * Pedestalの素材が一致するか（順不同）チェック。
     */
    public boolean matches(List<Material> pedestalMaterials) {
        if (pedestalMaterials.size() != pedestalItems.size()) return false;

        // 頻度マップで比較（同じ素材が異なる個数のケースに対応）
        Map<Material, Integer> required = toFrequencyMap(pedestalItems);
        Map<Material, Integer> provided = toFrequencyMap(pedestalMaterials);
        return required.equals(provided);
    }

    private static Map<Material, Integer> toFrequencyMap(List<Material> materials) {
        Map<Material, Integer> map = new HashMap<>();
        for (Material mat : materials) {
            map.merge(mat, 1, Integer::sum);
        }
        return map;
    }

    public boolean isCustomResult() {
        return resultId != null;
    }
}
