package com.arspaper.block.impl;

import com.arspaper.ArsPaper;
import com.arspaper.block.BlockKeys;
import com.arspaper.block.CustomBlock;
import com.arspaper.ritual.RitualManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Ritual Core - 儀式を発動するための中心ブロック。
 * ビーコンベースのカスタムブロック。
 * 周囲のPedestalに素材を配置し、右クリックで儀式を実行。
 */
public class RitualCore extends CustomBlock {

    public RitualCore(JavaPlugin plugin) {
        super(plugin, "ritual_core");
    }

    @Override
    public Material getBlockMaterial() {
        return Material.LODESTONE;
    }

    @Override
    public Component getDisplayName() {
        return Component.text("儀式の核", NamedTextColor.DARK_RED)
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return 300001;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta ->
            meta.lore(List.of(
                Component.text("台座と共に設置して儀式を行う", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("右クリックで儀式を発動", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ))
        );
        return item;
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        ItemStack head = new ItemStack(Material.PAPER);
        head.editMeta(meta -> meta.setCustomModelData(300001));
        return head;
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        RitualManager ritualManager = ArsPaper.getInstance().getRitualManager();
        if (ritualManager != null) {
            ritualManager.tryPerformRitual(player, block.getLocation());
        }
    }
}
