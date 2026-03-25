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

    /**
     * AOE拡張を効果側で内部的に処理するかどうか。
     * trueの場合、SpellContextの外部AOE拡張をスキップする。
     */
    default boolean handlesAoeInternally() { return false; }

    /**
     * 軌跡モードで飛行経路上の各ブロックごとに発動すべきかどうか。
     * falseの場合、軌跡モードでの経路上発動をスキップする（召喚系等）。
     */
    default boolean allowsTraceRepeating() { return true; }

    /**
     * ブロックAOE展開モード。
     * FIXED: 常にXZ+Yの固定軸（デフォルト）
     * HIT_FACE_INWARD: ヒット面に沿って展開、垂直は奥行き方向（破壊系）
     * HIT_FACE_OUTWARD: ヒット面に沿って展開、垂直は手前方向（設置系）
     */
    default AoeMode getAoeMode() { return AoeMode.FIXED; }

    enum AoeMode {
        FIXED,            // XZ+Y固定軸
        HIT_FACE_INWARD,  // 奥行き方向（破壊）
        HIT_FACE_OUTWARD  // 手前方向（設置）
    }

    @Override
    default ComponentType getType() {
        return ComponentType.EFFECT;
    }
}
