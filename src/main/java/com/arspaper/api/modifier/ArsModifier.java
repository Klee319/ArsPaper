package com.arspaper.api.modifier;

import org.bukkit.NamespacedKey;

/**
 * key×type→value の3つ組Modifier。
 * key は呼び出し元プラグインが任意に決める識別子（例: "valhallammo:mana_pool_1"）。
 */
public record ArsModifier(NamespacedKey key, ModifierType type, double value) {
}
