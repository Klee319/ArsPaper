package com.arspaper.item.impl;

import com.arspaper.ArsPaper;
import com.arspaper.gui.SpellCraftingGui;
import com.arspaper.integration.TrinityForgeBridge;
import com.arspaper.item.BaseCustomItem;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.SpellBookTierData;
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
import java.util.UUID;

/**
 * スペルブック。ティアに応じてスロット数・使用可能グリフティアが変わる。
 * 右クリックで選択中のスペルを発動。
 * スニーク+右クリックでスロット切り替え。
 * スニーク+左クリックでスペルクラフティングGUI。
 */
public class SpellBook extends BaseCustomItem {

    private final SpellRegistry spellRegistry;
    private final SpellBookTierData bookTier;

    public SpellBook(JavaPlugin plugin, SpellRegistry spellRegistry, SpellBookTierData bookTier) {
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
     * ティアに応じた魔導書名を生成する。
     *
     * 旧実装はNOVICE単色/APPRENTICE多色グラデーション+装飾記号(✦)/ARCHMAGE金グラデーション+太字+装飾記号(✧✦)を
     * ティアごとにswitch文でハードコードしていた。config駆動化に伴い、spellbooks.ymlの
     * display-name / name-color から生成する単色表示に統一した（1文字ずつの色替えグラデーションと
     * 装飾記号はconfigで表現しきれないため単色化。表示自体は崩れず、ティアごとの色分けは維持される）。
     */
    private static Component buildTieredBookName(SpellBookTierData tier) {
        return Component.text(tier.getDisplayName(), tier.getNameColor())
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return bookTier.getCustomModelData();
    }

    /** 触媒(完成品): 儀式クラフト時に品質(rollSeed + quality)を刻印する。 */
    @Override
    public boolean isQualityStamped() { return true; }

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
            // 要件⑥ ars-tier: skilltree由来のperkで使用可能グリフtier上限を加算する。
            // TF未ロード時はtfArsTierUnlockBonusが0を返し、従来のmaxGlyphTierのまま(fail-open)。
            SpellCraftingGui gui = new SpellCraftingGui(
                ArsPaper.getInstance(), player, item, slot,
                ArsPaper.getInstance().getSpellBookConfig().byTier(tier).getMaxGlyphTier()
                    + TrinityForgeBridge.tfArsTierUnlockBonus(player),
                ArsPaper.getInstance().getSpellBookConfig().byTier(tier).getMaxGlyphs()
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
        String authoritative = trinityForgeOwner(item);
        if (authoritative != null) {
            // TF が所有者を刻印している(儀式品・SOULBOUND 等)。こちらが唯一の正なので、
            // 先に誰が右クリックしたかに関係なく Ars 側の台帳をそれへ揃える(W-100)。
            if (!authoritative.equals(owner)) {
                item.editMeta(meta ->
                    meta.getPersistentDataContainer().set(
                        ItemKeys.SPELL_BOOK_OWNER, PersistentDataType.STRING, authoritative
                    )
                );
            }
            return authoritative;
        }
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
     * TrinityForge が刻印した所有者(文字列化した UUID)。無ければ {@code null}。
     *
     * <p>W-100(実サーバ報告「儀式で作成した魔導書が所有者名の人が使えない」)の対応点。
     * <b>所有者台帳が2本ある</b>のが真因だった —— アイテムの lore に出る「所有者:」は
     * TF の {@code ItemData#owner()} だが、装着/GUI の可否は Ars 独自の
     * {@code arspaper:spell_book_owner}(=<b>最初に右クリックした人</b>)で決めていた。
     * 儀式品を別の人が先に一度右クリックすると2本がずれ、
     * <b>lore に自分の名前が出ているのに所有者ではないと言われる</b>状態になる。
     * TF 側の刻印があるときはそちらを正とする。TF が居ない構成では null が返り、
     * 従来どおり「初回使用者が所有者」で動く。
     */
    private static String trinityForgeOwner(ItemStack item) {
        java.util.UUID tfOwner = com.arspaper.integration.TrinityForgeBridge.tfOwnerId(item);
        return tfOwner == null ? null : tfOwner.toString();
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
        boolean sharedSpell = com.arspaper.enchant.ArsEnchantments.hasShareEnchant(item);
        // 触媒＝詠唱に使ったスペルブック ItemStack（会心/貫通を魔法ダメージへ連携）。
        // 2026-07-31 D6: castItem は明示的に null。魔導書を直接右クリックして詠唱する経路では
        // 「魔導書自身のステータスを魔法へ持ち込まない」現行仕様を厳密に維持する
        // （魔導書は item-stats.yml に一切エントリが無く攻撃力0だが、将来ステが付いても乗らないことを保証する）。
        ArsPaper.getInstance().getSpellCaster().cast(player, recipe, sharedSpell, item, null);
    }

    private void switchSlot(Player player, ItemStack item) {
        int tier = item.getItemMeta().getPersistentDataContainer()
            .getOrDefault(ItemKeys.BOOK_TIER, PersistentDataType.INTEGER, 1);
        // 要件⑥ glyph-slot-plus: skilltree由来のperkでスペルスロット数を加算する(floor/0クランプ済み)。
        // TF未ロード時はtfGlyphSlotBonusが0を返し、従来のmaxSlotsのまま(fail-open)。
        int maxSlots = ArsPaper.getInstance().getSpellBookConfig().byTier(tier).getMaxSlots()
            + TrinityForgeBridge.tfGlyphSlotBonus(player);

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

    public SpellBookTierData getBookTier() {
        return bookTier;
    }
}
