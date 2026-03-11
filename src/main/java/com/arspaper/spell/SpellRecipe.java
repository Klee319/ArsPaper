package com.arspaper.spell;

import java.util.List;

/**
 * スペルの構成（Componentの不変リスト）。
 * 先頭は必ずFormである必要がある。
 */
public final class SpellRecipe {

    private final String name;
    private final List<SpellComponent> components;

    public SpellRecipe(String name, List<SpellComponent> components) {
        this.name = name;
        this.components = List.copyOf(components);
    }

    public String getName() {
        return name;
    }

    public List<SpellComponent> getComponents() {
        return components;
    }

    /** 合計マナコストを計算 */
    public int getTotalManaCost() {
        return components.stream()
            .mapToInt(SpellComponent::getManaCost)
            .sum();
    }

    /** 先頭のFormを取得 */
    public SpellForm getForm() {
        if (components.isEmpty()) return null;
        SpellComponent first = components.get(0);
        return (first instanceof SpellForm form) ? form : null;
    }

    /** バリデーション: 先頭がFormであること */
    public boolean isValid() {
        return getForm() != null;
    }
}
