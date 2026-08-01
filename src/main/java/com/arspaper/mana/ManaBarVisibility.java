package com.arspaper.mana;

import com.arspaper.item.ItemKeys;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * マナバーを出すかどうかの判定(2026-07-28 ユーザー要望
 * 「魔導書や魔法がバインドされたアイテムを持った時のみマナバーが出るように変更」)。
 *
 * <p>従来は常時表示だったため、魔法を一切使わないプレイヤーの画面上部もボスバーで
 * 常に埋まっていた(EliteMobs のボスバー等と競合する)。
 *
 * <p>判定対象は<strong>メインハンドとオフハンドのみ</strong>。インベントリ全体を見ると
 * 「持ち歩いているだけで出る」ことになり、要望(=持った時のみ)と食い違う。
 */
public final class ManaBarVisibility {

    private ManaBarVisibility() {
    }

    /**
     * 魔導書(スペルブック)か、魔法がバインドされたアイテムを手に持っているか。
     *
     * <p>判定キーは PDC のみ — Material 推論はしない。魔導書は正規カスタムアイテムID、
     * バインド品は {@link ItemKeys#BOUND_BOOK_UUID} と
     * {@link ItemKeys#BOUND_SPELL_SLOT} の組で判定する。
     */
    public static boolean holdsMagicItem(org.bukkit.entity.Player player) {
        if (player == null) {
            return false;
        }
        return ManaBarVisibilityPolicy.shouldShow(
                isMagicItem(player.getInventory().getItemInMainHand()),
                isMagicItem(player.getInventory().getItemInOffHand())
        );
    }

    /** 魔導書 or 魔法バインド済みアイテムなら true。 */
    public static boolean isMagicItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        return ManaBarVisibilityPolicy.isRelevantItem(
                pdc.get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING),
                pdc.get(ItemKeys.BOOK_TIER, PersistentDataType.INTEGER),
                pdc.get(ItemKeys.SPELL_BOOK_UUID, PersistentDataType.STRING),
                pdc.get(ItemKeys.BOUND_BOOK_UUID, PersistentDataType.STRING),
                pdc.get(ItemKeys.BOUND_SPELL_SLOT, PersistentDataType.INTEGER)
        );
    }
}
