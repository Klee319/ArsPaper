package com.arspaper.item.impl;

import com.arspaper.ArsPaper;
import com.arspaper.gui.SpellCraftingGui;
import com.arspaper.item.BaseCustomItem;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.WandTier;
import com.arspaper.spell.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * スペルワンド。1スロットの簡易スペルキャスター。
 * 右クリックで発動、スニーク+右クリックでスペル設定GUI。
 */
public class SpellWand extends BaseCustomItem {

    private final SpellRegistry spellRegistry;
    private final WandTier wandTier;

    public SpellWand(JavaPlugin plugin, SpellRegistry spellRegistry, WandTier wandTier) {
        super(plugin, wandTier.getItemId());
        this.spellRegistry = spellRegistry;
        this.wandTier = wandTier;
    }

    @Override
    public Material getBaseMaterial() {
        return Material.BLAZE_ROD;
    }

    @Override
    public Component getDisplayName() {
        return Component.text(wandTier.getDisplayName() + "スペルワンド", wandTier.getColor())
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return wandTier.getCustomModelData();
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                ItemKeys.WAND_TIER, PersistentDataType.INTEGER, wandTier.getTier()
            );
            meta.getPersistentDataContainer().set(
                ItemKeys.SPELL_SLOT, PersistentDataType.INTEGER, 0
            );
            meta.lore(List.of(
                Component.text("ティア: " + wandTier.getDisplayName(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("スロット数: 1", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("最大グリフティア: " + wandTier.getMaxGlyphTier(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("右クリックで発動", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("スニーク+右クリックでスペル設定", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ));
        });
        return item;
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        if (player.isSneaking()) {
            int tier = item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(ItemKeys.WAND_TIER, PersistentDataType.INTEGER, 1);
            SpellCraftingGui gui = new SpellCraftingGui(
                ArsPaper.getInstance(), player, item, 0,
                WandTier.fromTier(tier).getMaxGlyphTier()
            );
            gui.open();
        } else {
            castSpell(player, item);
        }
    }

    private void castSpell(Player player, ItemStack item) {
        String slotsJson = item.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.SPELL_SLOTS, PersistentDataType.STRING);
        if (slotsJson == null) {
            player.sendMessage(Component.text("スペルが未設定です！スニーク+右クリックで設定してください", NamedTextColor.YELLOW));
            return;
        }

        List<SpellRecipe> slots = SpellSerializer.deserializeSlots(slotsJson, spellRegistry);
        if (slots.isEmpty() || slots.get(0) == null) {
            player.sendMessage(Component.text("スペルが未設定です！", NamedTextColor.YELLOW));
            return;
        }

        ArsPaper.getInstance().getSpellCaster().cast(player, slots.get(0));
    }

    public WandTier getWandTier() {
        return wandTier;
    }
}
