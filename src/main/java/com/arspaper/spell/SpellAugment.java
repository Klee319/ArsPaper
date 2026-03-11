package com.arspaper.spell;

/**
 * 直前のForm/Effectを修飾するAugment。
 * Amplify, AOE, ExtendTime, Pierce など。
 */
public interface SpellAugment extends SpellComponent {

    /**
     * Augmentの効果をコンテキストに適用する。
     * 例: AmplifyならダメージやAOE半径などの倍率を変更。
     */
    void modify(SpellContext context);

    @Override
    default ComponentType getType() {
        return ComponentType.AUGMENT;
    }
}
