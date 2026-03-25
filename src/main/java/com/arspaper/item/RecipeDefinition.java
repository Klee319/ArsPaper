package com.arspaper.item;

import java.util.List;
import java.util.Map;

/**
 * armors.ymlで定義されたレシピのデータクラス。
 */
public record RecipeDefinition(
    String type,                    // "shaped" or "shapeless"
    List<String> shape,             // shaped用: ["ABA", "A A"] 等
    Map<String, String> ingredients // シンボル → 素材 ("L" → "LEATHER", "G" → "custom:source_gem")
) {}
