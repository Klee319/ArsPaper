package com.arspaper.item;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * materials.ymlの1素材分の定義データ。
 */
public record MaterialConfig(
    String id,
    String displayName,
    String nameColor,
    Material baseMaterial,
    int customModelData,
    List<String> lore,
    // エンチャント光沢(キラキラ)を付与するか。既定 true（従来どおり）
    boolean enchantGlow,
    // 儀式レシピ（null=レシピなし）
    String coreItem,
    List<String> pedestalItems,
    int source
) {}
