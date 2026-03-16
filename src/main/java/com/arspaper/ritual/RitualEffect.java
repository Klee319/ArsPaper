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
}
