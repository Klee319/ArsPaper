package com.arspaper.api.event;

/** グリフ/儀式/レシピの解放トリガー源。 */
public enum UnlockSource {
    /** プレイヤーが筆記台等でクラフトunlock */
    PLAYER_CRAFT,
    /** 外部プラグイン (ValhallaMMO等) からAPI呼び出し */
    API,
    /** /ars コマンド */
    COMMAND,
    /** その他（プラグイン起動時の自動 unlock 等） */
    OTHER
}
