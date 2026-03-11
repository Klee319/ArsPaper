package com.arspaper.spell;

import org.bukkit.entity.Player;

/**
 * スペルの形態（発動方法）を定義するインターフェース。
 * Projectile, Touch, Self, Underfoot など。
 */
public interface SpellForm extends SpellComponent {

    /**
     * スペルを発動する。ヒット対象が確定したらcontext内のEffectチェーンを実行する。
     *
     * @param caster  術者
     * @param context スペル実行コンテキスト
     */
    void cast(Player caster, SpellContext context);

    @Override
    default ComponentType getType() {
        return ComponentType.FORM;
    }
}
