package com.arspaper.ritual;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 儀式エフェクトのインターフェース。
 * craft以外の儀式タイプ（ワールド効果、スレッド適用等）を実装する。
 */
public interface RitualEffect {

    /**
     * 儀式エフェクトを実行する。
     *
     * @param coreLocation 儀式コアの位置
     * @param player       儀式を発動したプレイヤー
     * @param recipe       マッチした儀式レシピ
     */
    void execute(Location coreLocation, Player player, RitualRecipe recipe);

    /**
     * 素材消費前の事前検証。falseを返すと儀式を中止し素材を消費しない。
     * デフォルトは常にtrue（検証なし）。
     */
    default boolean validate(Location coreLocation, Player player, RitualRecipe recipe) {
        return true;
    }
}
