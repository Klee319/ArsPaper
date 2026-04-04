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

        // アイテムバインド（状態に依存しない固定テキスト）
        inventory.setItem(SLOT_BIND, createButton(
            Material.STRING,
            Component.text("アイテムバインド", NamedTextColor.AQUA),
            List.of(
                Component.text("クリックでバインド/アンバインド", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("対象: オフハンド/ホットバー9番スロット", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("バインド済みアイテムに再度使用で解除", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            )
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
        player.sendMessage(Component.text("30秒以内に入力してください", NamedTextColor.GRAY));

        final java.util.UUID playerUuid = player.getUniqueId();

        Listener chatListener = new Listener() {
            private void cleanup() {
                HandlerList.unregisterAll(this);
            }

            @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
                if (!event.getPlayer().getUniqueId().equals(playerUuid)) return;
                event.setCancelled(true);
                // Discord連携プラグイン等への漏洩を防止（受信者を空にする）
                event.viewers().clear();

                String message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(event.message());

                cleanup();

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

            @EventHandler
            public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                if (event.getPlayer().getUniqueId().equals(playerUuid)) {
                    cleanup();
                }
            }
        };

        plugin.getServer().getPluginManager().registerEvents(chatListener, plugin);

        // 30秒タイムアウト: リスナーを自動解除
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            HandlerList.unregisterAll(chatListener);
            Player p = plugin.getServer().getPlayer(playerUuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(Component.text("スペル名変更がタイムアウトしました", NamedTextColor.GRAY));
            }
        }, 600L); // 30秒 = 600tick
    }

    private void handleBind(Player player) {
        SpellRecipe recipe = getCurrentRecipe();
        if (recipe == null) {
            player.sendMessage(Component.text("このスロットにスペルが設定されていません", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return;
        }

        ItemStack target = findBindTarget(player);
        if (target == null) {
            // バインド先がない場合、アンバインド対象を探す（既バインド済みアイテム）
            ItemStack unbindTarget = findUnbindTarget(player);
            if (unbindTarget != null) {
                SpellBindListener.unbindSpell(player, unbindTarget);
                render();
                return;
            }
            player.sendMessage(Component.text("バインド可能なアイテムがオフハンド/ホットバー9番スロットにありません", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return;
        }

        SpellBindListener.bindSpell(player, target, spellBookItem, spellSlot, recipe);
        render();
    }

    /**
     * アンバインド対象を検索（既にバインド済みのアイテム）。
     * 検索対象: オフハンド → ホットバースロット8(固定)
     */
    private static ItemStack findUnbindTarget(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isBoundItem(offhand)) return offhand;
        ItemStack slot8 = player.getInventory().getItem(8);
        if (isBoundItem(slot8)) return slot8;
        return null;
    }

    private static boolean isBoundItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
            .has(ItemKeys.BOUND_BOOK_UUID, org.bukkit.persistence.PersistentDataType.STRING);
    }

    /**
     * バインド対象アイテムを検索する。
     * 検索対象: オフハンド → ホットバースロット8(固定)
     * スキップ対象: 空気、魔導書/ワンド、既に他のスペルがバインド済みのアイテム
     */
    private static ItemStack findBindTarget(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isValidBindTarget(offhand)) return offhand;
        ItemStack slot8 = player.getInventory().getItem(8);
        if (isValidBindTarget(slot8)) return slot8;
        return null;
    }

    private static boolean isValidBindTarget(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!item.hasItemMeta()) return true;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        // 魔導書・ワンドはスキップ
        String customId = pdc.get(ItemKeys.CUSTOM_ITEM_ID, org.bukkit.persistence.PersistentDataType.STRING);
        if (customId != null && (customId.contains("spell_book") || customId.contains("wand"))) return false;
        // 既にバインド済みのアイテムはスキップ
        if (pdc.has(ItemKeys.BOUND_BOOK_UUID, org.bukkit.persistence.PersistentDataType.STRING)) return false;
        return true;
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
