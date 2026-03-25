package com.arspaper.item.impl;

import com.arspaper.item.BaseCustomItem;
import com.arspaper.item.ItemKeys;
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
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                ItemKeys.THREAD_ITEM_TYPE, PersistentDataType.STRING, threadType.getId()
            );

            List<Component> lore = new ArrayList<>();
            if (threadType.hasEffect()) {
                lore.addAll(com.arspaper.ArsPaper.getInstance().getThreadConfig().getEffectLore(threadType));
            } else {
                lore.add(Component.text("儀式で効果付きスレッドに変換できます", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.text("防具のスレッドスロットにセット可能", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return item;
    }

    public ThreadType getThreadType() {
        return threadType;
    }
}
