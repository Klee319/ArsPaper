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
    // 儀式レシピ（null=レシピなし）
    String coreItem,
    List<String> pedestalItems,
    int source
) {}
