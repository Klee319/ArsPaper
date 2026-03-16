package com.arspaper.block.impl;

import com.arspaper.ArsPaper;
import com.arspaper.block.CustomBlock;
import com.arspaper.item.ItemKeys;
import com.arspaper.ritual.RitualIngredient;
import com.arspaper.ritual.RitualManager;
import com.arspaper.util.ItemFrameHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/**
 * Ritual Core - 儀式を発動するための中心ブロック。
 * Lodestoneベースのカスタムブロック。
 * 中央アイテムを設置可能。スニーク+右クリックで儀式発動。
 * 素手で右クリックでアイテム設置/回収。
 */
public class RitualCore extends CustomBlock {

    /** コアに載っているアイテムのMaterial名 */
    private static final NamespacedKey CORE_ITEM_KEY = new NamespacedKey("arspaper", "core_item");

    /** コアに載っているカスタムアイテムのID */
    private static final NamespacedKey CORE_CUSTOM_ID_KEY = new NamespacedKey("arspaper", "core_custom_id");

    /** コアに載っているスペルブックのスペルデータ（昇格時にデータ保持するため） */
    private static final NamespacedKey CORE_SPELL_SLOTS_KEY = new NamespacedKey("arspaper", "core_spell_slots");
    private static final NamespacedKey CORE_SPELL_SLOT_KEY = new NamespacedKey("arspaper", "core_spell_slot");

    public RitualCore(JavaPlugin plugin) {
        super(plugin, "ritual_core");
    }

    @Override
    public Material getBlockMaterial() {
        // Lodestoneは1.20.5以降TileState非対応のためPDCが使えない
        // ENCHANTING_TABLEはTileState対応で儀式の見た目に最適
        return Material.ENCHANTING_TABLE;
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
                Component.text("スニーク+右クリックで儀式を発動", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("右クリックでアイテムを設置/回収", NamedTextColor.DARK_GRAY)
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
    public void onBlockPlaced(Player player, Block block, TileState tileState) {
        ItemFrameHelper.spawnHeadDisplay(block.getLocation(), null);
    }

    @Override
    public void onBlockBroken(Player player, Block block, TileState tileState) {
        // コアに載っているアイテムをドロップ
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        String materialName = pdc.get(CORE_ITEM_KEY, PersistentDataType.STRING);
        String customId = pdc.get(CORE_CUSTOM_ID_KEY, PersistentDataType.STRING);
        if (materialName != null && !materialName.isEmpty()) {
            ItemStack storedItem = resolveStoredItemWithSpellData(materialName, customId, tileState);
            if (storedItem != null) {
                block.getWorld().dropItemNaturally(block.getLocation(), storedItem);
            }
        }
        ItemFrameHelper.removeHeadDisplay(block.getLocation());
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        if (player.isSneaking()) {
            // スニーク+右クリック → 儀式発動
            RitualManager ritualManager = ArsPaper.getInstance().getRitualManager();
            if (ritualManager != null) {
                ritualManager.tryPerformRitual(player, block.getLocation());
            }
            return;
        }

        // 通常右クリック → アイテム設置/回収
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        String currentItem = pdc.get(CORE_ITEM_KEY, PersistentDataType.STRING);
        String currentCustomId = pdc.get(CORE_CUSTOM_ID_KEY, PersistentDataType.STRING);
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (currentItem != null && !currentItem.isEmpty()) {
            // コアにアイテムがある → 回収（スペルデータ付き）
            ItemStack returnItem = resolveStoredItemWithSpellData(currentItem, currentCustomId, tileState);
            if (returnItem != null) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(returnItem);
                if (!overflow.isEmpty()) {
                    overflow.values().forEach(item ->
                        player.getWorld().dropItemNaturally(player.getLocation(), item));
                }
            }
            pdc.remove(CORE_ITEM_KEY);
            pdc.remove(CORE_CUSTOM_ID_KEY);
            tileState.update();
            ItemFrameHelper.updateHeadDisplay(block.getLocation(), null);
            player.sendMessage(Component.text("コアからアイテムを回収しました", NamedTextColor.YELLOW));
        } else if (!handItem.isEmpty() && handItem.getType() != Material.AIR) {
            // 手にアイテムがある → コアに設置
            String materialName = handItem.getType().name();
            pdc.set(CORE_ITEM_KEY, PersistentDataType.STRING, materialName);

            String customId = handItem.getItemMeta() != null
                ? handItem.getItemMeta().getPersistentDataContainer()
                    .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING)
                : null;
            if (customId != null) {
                pdc.set(CORE_CUSTOM_ID_KEY, PersistentDataType.STRING, customId);
            } else {
                pdc.remove(CORE_CUSTOM_ID_KEY);
            }

            // スペルブック/ワンドの場合: スペルデータも保存（昇格時にデータ保持するため）
            if (customId != null && (customId.startsWith("spell_book_") || customId.startsWith("wand_"))) {
                PersistentDataContainer itemPdc = handItem.getItemMeta().getPersistentDataContainer();
                String spellSlots = itemPdc.get(ItemKeys.SPELL_SLOTS, PersistentDataType.STRING);
                Integer spellSlot = itemPdc.get(ItemKeys.SPELL_SLOT, PersistentDataType.INTEGER);
                if (spellSlots != null) {
                    pdc.set(CORE_SPELL_SLOTS_KEY, PersistentDataType.STRING, spellSlots);
                }
                if (spellSlot != null) {
                    pdc.set(CORE_SPELL_SLOT_KEY, PersistentDataType.INTEGER, spellSlot);
                }
            } else {
                pdc.remove(CORE_SPELL_SLOTS_KEY);
                pdc.remove(CORE_SPELL_SLOT_KEY);
            }
            tileState.update();

            Material displayMaterial = handItem.getType(); // 消費前にMaterialを保存
            handItem.setAmount(handItem.getAmount() - 1);
            ItemFrameHelper.updateHeadDisplay(block.getLocation(), new ItemStack(displayMaterial));

            String displayName = customId != null ? "custom:" + customId : materialName;
            player.sendMessage(Component.text(displayName + " をコアに設置しました",
                NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("コアは空です。アイテムを持って右クリックで設置",
                NamedTextColor.GRAY));
        }
    }

    /**
     * 保存されたアイテム情報からItemStackを復元する。
     */
    private static ItemStack resolveStoredItem(String materialName, String customId) {
        if (customId != null && !customId.isEmpty()) {
            return ArsPaper.getInstance().getItemRegistry()
                .get(customId)
                .map(item -> item.createItemStack())
                .orElseGet(() -> {
                    Material mat = Material.matchMaterial(materialName);
                    return mat != null ? new ItemStack(mat, 1) : null;
                });
        }
        Material mat = Material.matchMaterial(materialName);
        return mat != null ? new ItemStack(mat, 1) : null;
    }

    /**
     * 保存されたアイテムにスペルデータを復元する（スペルブック回収時）。
     */
    private static ItemStack resolveStoredItemWithSpellData(String materialName, String customId, TileState tileState) {
        ItemStack item = resolveStoredItem(materialName, customId);
        if (item != null && customId != null && (customId.startsWith("spell_book_") || customId.startsWith("wand_"))) {
            PersistentDataContainer corePdc = tileState.getPersistentDataContainer();
            String spellSlots = corePdc.get(CORE_SPELL_SLOTS_KEY, PersistentDataType.STRING);
            Integer spellSlot = corePdc.get(CORE_SPELL_SLOT_KEY, PersistentDataType.INTEGER);
            if (spellSlots != null || spellSlot != null) {
                item.editMeta(meta -> {
                    PersistentDataContainer itemPdc = meta.getPersistentDataContainer();
                    if (spellSlots != null) {
                        itemPdc.set(ItemKeys.SPELL_SLOTS, PersistentDataType.STRING, spellSlots);
                    }
                    if (spellSlot != null) {
                        itemPdc.set(ItemKeys.SPELL_SLOT, PersistentDataType.INTEGER, spellSlot);
                    }
                });
            }
        }
        return item;
    }

    /**
     * コアに載っている素材をRitualIngredientとして取得。
     */
    public static RitualIngredient getCoreIngredient(TileState tileState) {
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        String customId = pdc.get(CORE_CUSTOM_ID_KEY, PersistentDataType.STRING);
        if (customId != null && !customId.isEmpty()) {
            return RitualIngredient.ofCustom(customId);
        }
        String matName = pdc.get(CORE_ITEM_KEY, PersistentDataType.STRING);
        if (matName == null || matName.isEmpty()) return null;
        Material mat = Material.matchMaterial(matName);
        return mat != null ? RitualIngredient.ofMaterial(mat) : null;
    }

    /**
     * コアの素材をクリア（儀式完了時）。
     */
    public static void clearCoreItem(TileState tileState) {
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        pdc.remove(CORE_ITEM_KEY);
        pdc.remove(CORE_CUSTOM_ID_KEY);
        pdc.remove(CORE_SPELL_SLOTS_KEY);
        pdc.remove(CORE_SPELL_SLOT_KEY);
        tileState.update();
        ItemFrameHelper.updateHeadDisplay(tileState.getLocation(), null);
    }

    /**
     * コアに保存されたスペルデータを取得する（儀式昇格用）。
     */
    public static String getStoredSpellSlots(TileState tileState) {
        return tileState.getPersistentDataContainer().get(CORE_SPELL_SLOTS_KEY, PersistentDataType.STRING);
    }

    public static Integer getStoredSpellSlot(TileState tileState) {
        return tileState.getPersistentDataContainer().get(CORE_SPELL_SLOT_KEY, PersistentDataType.INTEGER);
    }
}
