package com.arspaper.mana;

/**
 * Bukkitに依存しないマナバー表示ポリシー。
 *
 * <p>魔導書は {@code BaseCustomItem} と {@code SpellBook#createItemStack} が書き込む
 * カスタムアイテムID・ティア・個体UUIDの組で識別する。バインド品は
 * {@code SpellBindListener} が書き込む参照先の本UUIDとスロット番号の組が揃っている場合だけ
 * 有効とする。
 */
public final class ManaBarVisibilityPolicy {

    private ManaBarVisibilityPolicy() {
    }

    public static boolean isRelevantItem(String customItemId, Integer bookTier,
                                         String spellBookUuid, String boundBookUuid,
                                         Integer boundSpellSlot) {
        boolean spellBook = customItemId != null
                && !customItemId.isBlank()
                && bookTier != null
                && bookTier > 0
                && spellBookUuid != null
                && !spellBookUuid.isBlank();
        boolean boundSpell = boundBookUuid != null
                && !boundBookUuid.isBlank()
                && boundSpellSlot != null
                && boundSpellSlot >= 0;
        return spellBook || boundSpell;
    }

    public static boolean shouldShow(boolean mainHandRelevant, boolean offHandRelevant) {
        return mainHandRelevant || offHandRelevant;
    }
}
