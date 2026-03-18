package com.arspaper.gui;

import com.arspaper.ArsPaper;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.SpellBookTier;
import com.arspaper.item.impl.SpellBook;
import com.arspaper.spell.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * スペル設定GUI。
 * スペル名変更、アイテムバインド、設定初期化を提供する。
 */
public class SpellSettingsGui extends BaseGui {

    private static final int SLOT_RENAME = 11;
    private static final int SLOT_BIND = 13;
    private static final int SLOT_RESET = 15;
    private static final int SLOT_BACK = 22;

    private final ArsPaper plugin;
    private final ItemStack spellBookItem;
    private final int spellSlot;

    public SpellSettingsGui(Player player, ItemStack spellBookItem, int spellSlot, ArsPaper plugin) {
        super(player, 3, Component.text("スペル設定 - スロット" + (spellSlot + 1), NamedTextColor.DARK_PURPLE));
        this.plugin = plugin;
        this.spellBookItem = spellBookItem;
        this.spellSlot = spellSlot;
    }

    @Override
    public void render() {
        inventory.clear();
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        // スペル名変更
        inventory.setItem(SLOT_RENAME, createButton(
            Material.PAPER,
            Component.text("スペル名変更", NamedTextColor.YELLOW),
            List.of(
                Component.text("クリックしてチャットで入力", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            )
        ));

        // アイテムバインド
        SpellRecipe recipe = getCurrentRecipe();
        ItemStack offhand = viewer.getInventory().getItemInOffHand();
        boolean offhandHasBind = offhand != null && offhand.hasItemMeta()
            && offhand.getItemMeta().getPersistentDataContainer()
                .has(ItemKeys.BOUND_BOOK_UUID, PersistentDataType.STRING);

        List<Component> bindLore = new ArrayList<>();
        if (recipe == null) {
            bindLore.add(Component.text("スペルが未設定です", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        } else if (offhand == null || offhand.getType().isAir()) {
            bindLore.add(Component.text("オフハンドにアイテムを持ってください", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        } else if (offhandHasBind) {
            bindLore.add(Component.text("クリックでバインド解除", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        } else {
            bindLore.add(Component.text("クリックでオフハンドにバインド", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        }

        inventory.setItem(SLOT_BIND, createButton(
            Material.STRING,
            Component.text("アイテムバインド", NamedTextColor.AQUA),
            bindLore
        ));

        // 設定の初期化
        inventory.setItem(SLOT_RESET, createButton(
            Material.TNT,
            Component.text("設定の初期化", NamedTextColor.RED),
            List.of(
                Component.text("スペル名をデフォルトに戻す", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            )
        ));

        // 戻るボタン
        inventory.setItem(SLOT_BACK, createButton(
            Material.ARROW,
            Component.text("戻る", NamedTextColor.WHITE)
        ));
    }

    @Override
    public boolean onClick(int slot, Player clicker, InventoryClickEvent event) {
        switch (slot) {
            case SLOT_RENAME -> {
                handleRename(clicker);
                return true;
            }
            case SLOT_BIND -> {
                handleBind(clicker);
                return true;
            }
            case SLOT_RESET -> {
                handleReset(clicker);
                return true;
            }
            case SLOT_BACK -> {
                openCraftingGui(clicker);
                return true;
            }
        }
        return false;
    }

    private void handleRename(Player player) {
        player.closeInventory();
        player.sendMessage(Component.text("チャットにスペル名を入力してください (キャンセル: 'cancel')", NamedTextColor.YELLOW));

        Listener chatListener = new Listener() {
            @EventHandler
            public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
                if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
                event.setCancelled(true);

                String message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(event.message());

                HandlerList.unregisterAll(this);

                if (message.equalsIgnoreCase("cancel")) {
                    player.sendMessage(Component.text("名前変更をキャンセルしました", NamedTextColor.GRAY));
                    return;
                }

                // メインスレッドでPDC操作を実行
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    setSpellName(message);
                    player.sendMessage(Component.text(
                        "スペル名を \"" + message + "\" に変更しました", NamedTextColor.GREEN));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
                });
            }
        };

        plugin.getServer().getPluginManager().registerEvents(chatListener, plugin);
    }

    private void handleBind(Player player) {
        SpellRecipe recipe = getCurrentRecipe();
        if (recipe == null) {
            player.sendMessage(Component.text("このスロットにスペルが設定されていません", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return;
        }

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand == null || offhand.getType().isAir()) {
            player.sendMessage(Component.text("オフハンドにバインド先アイテムを持ってください", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return;
        }

        // 既にバインド済みなら解除
        boolean hasBind = offhand.hasItemMeta()
            && offhand.getItemMeta().getPersistentDataContainer()
                .has(ItemKeys.BOUND_BOOK_UUID, PersistentDataType.STRING);
        if (hasBind) {
            SpellBindListener.unbindSpell(player, offhand);
        } else {
            SpellBindListener.bindSpell(player, offhand, spellBookItem, spellSlot, recipe);
        }
        render();
    }

    private void handleReset(Player player) {
        String defaultName = "スペル" + (spellSlot + 1);
        setSpellName(defaultName);
        player.sendMessage(Component.text("設定を初期化しました", NamedTextColor.YELLOW));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 0.3f, 1.0f);
        render();
    }

    private void openCraftingGui(Player player) {
        int tier = spellBookItem.getItemMeta().getPersistentDataContainer()
            .getOrDefault(ItemKeys.BOOK_TIER, PersistentDataType.INTEGER, 1);
        SpellCraftingGui gui = new SpellCraftingGui(
            plugin, player, spellBookItem, spellSlot,
            SpellBookTier.fromTier(tier).getMaxGlyphTier()
        );
        gui.open();
    }

    private SpellRecipe getCurrentRecipe() {
        if (!spellBookItem.hasItemMeta()) return null;
        String slotsJson = spellBookItem.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.SPELL_SLOTS, PersistentDataType.STRING);
        if (slotsJson == null) return null;

        List<SpellRecipe> slots = SpellSerializer.deserializeSlots(slotsJson, plugin.getSpellRegistry());
        if (spellSlot >= slots.size()) return null;
        return slots.get(spellSlot);
    }

    private void setSpellName(String newName) {
        if (!spellBookItem.hasItemMeta()) return;
        String slotsJson = spellBookItem.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.SPELL_SLOTS, PersistentDataType.STRING);
        if (slotsJson == null) return;

        SpellRegistry registry = plugin.getSpellRegistry();
        List<SpellRecipe> slots = new ArrayList<>(SpellSerializer.deserializeSlots(slotsJson, registry));
        if (spellSlot >= slots.size() || slots.get(spellSlot) == null) return;

        SpellRecipe old = slots.get(spellSlot);
        SpellRecipe renamed = new SpellRecipe(newName, old.getComponents());
        slots.set(spellSlot, renamed);

        String newJson = SpellSerializer.serializeSlots(slots);
        spellBookItem.editMeta(meta ->
            meta.getPersistentDataContainer().set(
                ItemKeys.SPELL_SLOTS, PersistentDataType.STRING, newJson
            )
        );
    }

    // ESCで閉じても特別な処理は不要（状態破壊なし）
    @Override
    public void onClose(Player player) {
        // No-op: ESCで閉じても安全
    }
}
