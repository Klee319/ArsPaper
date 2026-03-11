package com.arspaper.item.impl;

import com.arspaper.ArsPaper;
import com.arspaper.gui.SpellCraftingGui;
import com.arspaper.item.BaseCustomItem;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.SpellBookTier;
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
 * スペルブック。ティアに応じてスロット数・使用可能グリフティアが変わる。
 * 右クリックで選択中のスペルを発動。
 * スニーク+右クリックでスロット切り替え。
 * スニーク+左クリックでスペルクラフティングGUI。
 */
public class SpellBook extends BaseCustomItem {

    private final SpellRegistry spellRegistry;
    private final SpellBookTier bookTier;

    public SpellBook(JavaPlugin plugin, SpellRegistry spellRegistry, SpellBookTier bookTier) {
        super(plugin, bookTier.getItemId());
        this.spellRegistry = spellRegistry;
        this.bookTier = bookTier;
    }

    @Override
    public Material getBaseMaterial() {
        return Material.BOOK;
    }

    @Override
    public Component getDisplayName() {
        return Component.text(bookTier.getDisplayName() + "スペルブック", bookTier.getColor())
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return bookTier.getCustomModelData();
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                ItemKeys.BOOK_TIER, PersistentDataType.INTEGER, bookTier.getTier()
            );
            meta.getPersistentDataContainer().set(
                ItemKeys.SPELL_SLOT, PersistentDataType.INTEGER, 0
            );
            meta.lore(List.of(
                Component.text("ティア: " + bookTier.getDisplayName(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("スロット数: " + bookTier.getMaxSlots(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("最大グリフティア: " + bookTier.getMaxGlyphTier(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("右クリックで発動", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("スニーク+右クリックでスロット切替", NamedTextColor.DARK_GRAY)
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
            switchSlot(player, item);
        } else {
            castCurrentSpell(player, item);
        }
    }

    @Override
    public void onLeftClick(PlayerInteractEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        if (player.isSneaking()) {
            int slot = item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(ItemKeys.SPELL_SLOT, PersistentDataType.INTEGER, 0);
            int tier = item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(ItemKeys.BOOK_TIER, PersistentDataType.INTEGER, 1);
            SpellCraftingGui gui = new SpellCraftingGui(
                ArsPaper.getInstance(), player, item, slot,
                SpellBookTier.fromTier(tier).getMaxGlyphTier()
            );
            gui.open();
        }
    }

    private void castCurrentSpell(Player player, ItemStack item) {
        String slotsJson = item.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.SPELL_SLOTS, PersistentDataType.STRING);
        if (slotsJson == null) {
            player.sendMessage(Component.text("スペルが未設定です！スニーク+左クリックで作成してください", NamedTextColor.YELLOW));
            return;
        }

        int slot = item.getItemMeta().getPersistentDataContainer()
            .getOrDefault(ItemKeys.SPELL_SLOT, PersistentDataType.INTEGER, 0);

        List<SpellRecipe> slots = SpellSerializer.deserializeSlots(slotsJson, spellRegistry);
        if (slot >= slots.size() || slots.get(slot) == null) {
            player.sendMessage(Component.text("空のスペルスロットです！", NamedTextColor.YELLOW));
            return;
        }

        SpellRecipe recipe = slots.get(slot);
        SpellCaster caster = new SpellCaster(ArsPaper.getInstance().getManaManager());
        caster.cast(player, recipe);
    }

    private void switchSlot(Player player, ItemStack item) {
        int tier = item.getItemMeta().getPersistentDataContainer()
            .getOrDefault(ItemKeys.BOOK_TIER, PersistentDataType.INTEGER, 1);
        int maxSlots = SpellBookTier.fromTier(tier).getMaxSlots();

        int current = item.getItemMeta().getPersistentDataContainer()
            .getOrDefault(ItemKeys.SPELL_SLOT, PersistentDataType.INTEGER, 0);
        int next = (current + 1) % maxSlots;

        item.editMeta(meta ->
            meta.getPersistentDataContainer().set(
                ItemKeys.SPELL_SLOT, PersistentDataType.INTEGER, next
            )
        );

        String slotName = getSlotSpellName(item, next);
        player.sendActionBar(
            Component.text("スロット " + (next + 1) + "/" + maxSlots + ": " + slotName, NamedTextColor.AQUA)
        );
    }

    private String getSlotSpellName(ItemStack item, int slot) {
        String slotsJson = item.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.SPELL_SLOTS, PersistentDataType.STRING);
        if (slotsJson == null) return "空";

        List<SpellRecipe> slots = SpellSerializer.deserializeSlots(slotsJson, spellRegistry);
        if (slot >= slots.size() || slots.get(slot) == null) return "Empty";
        return slots.get(slot).getName();
    }

    public SpellBookTier getBookTier() {
        return bookTier;
    }
}
