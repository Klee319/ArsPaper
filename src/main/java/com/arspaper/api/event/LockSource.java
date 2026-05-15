package com.arspaper.api.event;

/** グリフ/儀式/レシピ ロック (取り消し) のトリガー源。 */
public enum LockSource {
    /** 外部プラグイン (ValhallaMMO等) からAPI呼び出し */
    API,
    /** /ars コマンド */
    COMMAND,
    /** その他 */
    OTHER
}
