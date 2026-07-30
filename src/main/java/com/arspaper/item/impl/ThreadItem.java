package com.arspaper.item.impl;

import com.arspaper.item.BaseCustomItem;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.ThreadRoll;
import com.arspaper.item.ThreadRollConfig;
import com.arspaper.item.ThreadType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * スレッドアイテム。防具のスレッドスロットにセットして使う。
 * 空スレッド（EMPTY）は儀式で型付きスレッドに変換する中間素材。
 */
public class ThreadItem extends BaseCustomItem {

    /** 厳選用の乱数。個体差だけに使うのでセキュアである必要はない。 */
    private static final java.util.Random RANDOM = new java.util.Random();

    private final ThreadType threadType;

    public ThreadItem(JavaPlugin plugin, ThreadType threadType) {
        super(plugin, "thread_" + threadType.getId());
        this.threadType = threadType;
    }

    @Override
    public Material getBaseMaterial() {
        return threadType.getBaseMaterial();
    }

    @Override
    public Component getDisplayName() {
        return Component.text(threadType.getDisplayName(), threadType.getColor())
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return threadType.getCustomModelData();
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        // 厳選(個体差)は「効果付きスレッドを1個作った瞬間」に決まる。EMPTY は儀式で型付きへ変換する
        // 中間素材なので厳選しない(変換後の ThreadItem 生成時に改めて抽選される)。
        ThreadRoll roll = threadType.hasEffect() ? rollForNewItem() : null;
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                ItemKeys.THREAD_ITEM_TYPE, PersistentDataType.STRING, threadType.getId()
            );
            ThreadRoll.write(meta.getPersistentDataContainer(), roll);

            List<Component> lore = new ArrayList<>();
            if (threadType.hasEffect()) {
                lore.addAll(com.arspaper.ArsPaper.getInstance().getThreadConfig().getEffectLore(threadType));
            } else {
                lore.add(Component.text("儀式で効果付きスレッドに変換できます", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            }
            lore.addAll(rollLore(roll));
            lore.add(Component.text("防具のスレッドスロットにセット可能", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return item;
    }

    /** 厳選を1回引く。設定が無効/未ロードなら null（＝個体差なしの従来挙動）。 */
    private static ThreadRoll rollForNewItem() {
        com.arspaper.ArsPaper ars = com.arspaper.ArsPaper.getInstance();
        if (ars == null || ars.getThreadRollConfig() == null) {
            return null;
        }
        return ars.getThreadRollConfig().roll(RANDOM).orElse(null);
    }

    /**
     * 厳選結果の lore 行。レア度のラベル/色は thread-rolls.yml 側が正なので毎回引き直す
     * （アイテムに焼き込むのは ID と数値だけ ── 表示だけは後から設定で変えられるようにしている）。
     */
    public static List<Component> rollLore(ThreadRoll roll) {
        if (roll == null) {
            return List.of();
        }
        com.arspaper.ArsPaper ars = com.arspaper.ArsPaper.getInstance();
        ThreadRollConfig config = ars == null ? null : ars.getThreadRollConfig();
        if (config == null) {
            return roll.lore(NamedTextColor.GRAY, roll.rarityId(), java.util.Set.of());
        }
        return config.rarity(roll.rarityId())
                .map(rarity -> roll.lore(rarity.color(), rarity.label(), config.percentKeys()))
                .orElseGet(() -> roll.lore(NamedTextColor.GRAY, roll.rarityId(), config.percentKeys()));
    }

    public ThreadType getThreadType() {
        return threadType;
    }
}
