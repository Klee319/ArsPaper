package com.arspaper.spell;

import org.bukkit.NamespacedKey;

/**
 * 全グリフ（Form/Effect/Augment）の共通インターフェース。
 */
public interface SpellComponent {

    /** グリフの一意ID */
    NamespacedKey getId();

    /** 表示名 */
    String getDisplayName();

    /** グリフの種別 */
    ComponentType getType();

    /** マナコスト */
    int getManaCost();

    /** ティア (1=Novice, 2=Apprentice, 3=Archmage) */
    int getTier();

    enum ComponentType {
        FORM,
        EFFECT,
        AUGMENT
    }
}
