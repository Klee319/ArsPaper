package com.arspaper.spell;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/**
 * スペルの効果を定義するインターフェース。
 * Break, Harm, Heal, Grow, Light, Speed など。
 */
public interface SpellEffect extends SpellComponent {

    /**
     * エンティティに対する効果を適用。
     */
    void applyToEntity(SpellContext context, LivingEntity target);

    /**
     * ブロック位置に対する効果を適用。
     */
    void applyToBlock(SpellContext context, Location blockLocation);

    @Override
    default ComponentType getType() {
        return ComponentType.EFFECT;
    }
}
