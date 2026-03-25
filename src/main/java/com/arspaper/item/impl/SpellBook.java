package com.arspaper.item.impl;

import com.arspaper.ArsPaper;
import com.arspaper.gui.SpellCraftingGui;
import com.arspaper.item.BaseCustomItem;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.SpellBookTier;
import com.arspaper.spell.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

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
        return buildTieredBookName(bookTier);
    }

    /**
     * ティアに応じた華やかな魔導書名を生成する。
     * Novice: シンプルな薄紫
     * Apprentice: 装飾付き濃紫グラデーション
     * Archmage: 金→黄グラデーション + ボールド + 装飾シンボル
     */
    private static Component buildTieredBookName(SpellBookTier tier) {
        return switch (tier) {
            case NOVICE -> Component.text("見習いの魔法書", TextColor.color(0xD4A0FF))
                .decoration(TextDecoration.ITALIC, false);

            case APPRENTICE -> Component.text("✦ ", TextColor.color(0x9966CC))
                .append(Component.text("魔術師", TextColor.color(0xAA55DD)))
                .append(Component.text("の", TextColor.color(0x9944CC)))
                .append(Component.text("魔術書", TextColor.color(0x8833BB)))
                .append(Component.text(" ✦", TextColor.color(0x9966CC)))
                .decoration(TextDecoration.ITALIC, false);

            case ARCHMAGE -> Component.text("✧✦ ", TextColor.color(0xFFD700))
                .append(Component.text("大", TextColor.color(0xFFD700)))
                .append(Component.text("魔", TextColor.color(0xFFCC00)))
                .append(Component.text("導", TextColor.color(0xFFC200)))
                .append(Component.text("士", TextColor.color(0xFFB800)))
                .append(Component.text("の", TextColor.color(0xFFAE00)))
                .append(Component.text("魔", TextColor.color(0xFFA400)))
                .append(Component.text("導", TextColor.color(0xFF9A00)))
                .append(Component.text("書", TextColor.color(0xFF9000)))
                .append(Component.text(" ✦✧", TextColor.color(0xFFD700)))
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true);
        };
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
            // 新規作成時にUUIDを付与
            meta.getPersistentDataContainer().set(
                ItemKeys.SPELL_BOOK_UUID, PersistentDataType.STRING, UUID.randomUUID().toString()
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

    /**
     * スペルブックのUUIDを取得する。存在しない場合は生成して設定する。
     */
    public static String getOrCreateUUID(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        String uuid = item.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.SPELL_BOOK_UUID, PersistentDataType.STRING);
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            String finalUuid = uuid;
            item.editMeta(meta ->
                meta.getPersistentDataContainer().set(
                    ItemKeys.SPELL_BOOK_UUID, PersistentDataType.STRING, finalUuid
                )
            );
        }
        return uuid;
    }

    @Override
    public void onRightClick(PlayerInteractEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        // UUID未設定の既存ブックに対してUUIDを付与
        getOrCreateUUID(item);
        // 初回使用時に所有者を設定
        getOrCreateOwner(item, player);

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
            // 所有者チェック: 所有者以外はGUIを開けない
            if (!isOwner(item, player)) {
                player.sendMessage(Component.text("この魔導書の所有者ではありません", NamedTextColor.RED));
                return;
            }

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

    /**
     * スペルブックの所有者を取得または初回設定する。
     * 所有者が未設定の場合、操作したプレイヤーを所有者として記録する。
     */
    public static String getOrCreateOwner(ItemStack item, Player player) {
        if (!item.hasItemMeta()) return null;
        String owner = item.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.SPELL_BOOK_OWNER, PersistentDataType.STRING);
        if (owner == null) {
            owner = player.getUniqueId().toString();
            String finalOwner = owner;
            item.editMeta(meta ->
                meta.getPersistentDataContainer().set(
                    ItemKeys.SPELL_BOOK_OWNER, PersistentDataType.STRING, finalOwner
                )
            );
        }
        return owner;
    }

    /**
     * プレイヤーがこのスペルブックの所有者かどうか判定する。
     */
    public static boolean isOwner(ItemStack item, Player player) {
        if (!item.hasItemMeta()) return false;
        String owner = item.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.SPELL_BOOK_OWNER, PersistentDataType.STRING);
        // 所有者未設定の場合は誰でもOK（後方互換）
        if (owner == null) return true;
        return owner.equals(player.getUniqueId().toString());
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
        ArsPaper.getInstance().getSpellCaster().cast(player, recipe);
    }

    private void switchSlot(Player player, ItemStack item) {
        int tier = item.getItemMeta().getPersistentDataContainer()
            .getOrDefault(ItemKeys.BOOK_TIER, PersistentDataType.INTEGER, 1);
        int maxSlots = SpellBookTier.fromTier(tier).getMaxSlots();

        int current = item.getItemMeta().getPersistentDataContainer()
            .getOrDefault(ItemKeys.SPELL_SLOT, PersistentDataType.INTEGER, 0);

        // 通常: 次のスロットへ（前ロールはスニーク+ドロップキーで別途処理）
        int next = (current + 1) % maxSlots;

        item.editMeta(meta ->
            meta.getPersistentDataContainer().set(
                ItemKeys.SPELL_SLOT, PersistentDataType.INTEGER, next
            )
        );

        String slotName = getSlotSpellName(item, next);
        player.sendActionBar(
            Component.text("§d" + slotName + " §7(スロット" + (next + 1) + ")")
        );
    }

    public String getSlotSpellName(ItemStack item, int slot) {
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
