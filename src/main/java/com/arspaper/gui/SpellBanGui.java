package com.arspaper.gui;

import com.arspaper.ArsPaper;
import com.arspaper.spell.SpellComponent;
import com.arspaper.spell.SpellEffect;
import com.arspaper.world.WorldSettingsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ワールド別スペルBAN設定GUI。
 * 効果グリフの一覧をページネーション付きで表示し、クリックでBAN/解除をトグル。
 */
public class SpellBanGui extends BaseGui {

    private static final int ROWS = 6;
    private static final int ITEMS_PER_PAGE = 36; // 行2-5 × 9列 (slot 9-44)

    private final ArsPaper plugin;
    private final String worldName;
    private final List<SpellEffect> allEffects;
    private int page = 0;

    public SpellBanGui(Player viewer, ArsPaper plugin, String worldName) {
        super(viewer, ROWS, Component.text("スペルBAN設定: " + worldName, NamedTextColor.DARK_RED));
        this.plugin = plugin;
        this.worldName = worldName;
        this.allEffects = new ArrayList<>(plugin.getSpellRegistry().getEffects());
    }

    @Override
    public void render() {
        inventory.clear();
        fillRow(0, Material.RED_STAINED_GLASS_PANE);
        fillRow(5, Material.RED_STAINED_GLASS_PANE);

        WorldSettingsManager wsm = plugin.getWorldSettingsManager();
        Set<String> bannedAll = wsm.getBannedSpells(worldName);
        Set<String> globalBans = wsm.getGlobalBannedSpells();

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, allEffects.size());

        for (int i = start; i < end; i++) {
            SpellEffect effect = allEffects.get(i);
            String key = effect.getId().toString();
            boolean isGlobalBan = globalBans.contains(key);
            boolean isBanned = bannedAll.contains(key);

            int slot = 9 + (i - start);
            inventory.setItem(slot, createEffectButton(effect, isBanned, isGlobalBan));
        }

        // ページ情報
        int totalPages = Math.max(1, (allEffects.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        inventory.setItem(4, createButton(Material.PAPER,
            Component.text("ページ " + (page + 1) + "/" + totalPages, NamedTextColor.WHITE)));

        // ナビゲーション
        if (page > 0) {
            inventory.setItem(45, createButton(Material.ARROW,
                Component.text("前のページ", NamedTextColor.YELLOW)));
        }
        if (page < totalPages - 1) {
            inventory.setItem(53, createButton(Material.ARROW,
                Component.text("次のページ", NamedTextColor.YELLOW)));
        }

        // 閉じるボタン
        inventory.setItem(49, createButton(Material.BARRIER,
            Component.text("閉じる", NamedTextColor.RED)));
    }

    private ItemStack createEffectButton(SpellEffect effect, boolean isBanned, boolean isGlobalBan) {
        Material material = isBanned ? Material.RED_CONCRETE : Material.LIME_CONCRETE;
        NamedTextColor nameColor = isBanned ? NamedTextColor.RED : NamedTextColor.GREEN;

        String statusText;
        if (isGlobalBan) {
            statusText = "サーバ全体BAN (ban.yml)";
            material = Material.GRAY_CONCRETE;
            nameColor = NamedTextColor.DARK_GRAY;
        } else if (isBanned) {
            statusText = "BAN中 (クリックで解除)";
        } else {
            statusText = "利用可能 (クリックでBAN)";
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(effect.getDescription(), NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("ティア: " + effect.getTier(), NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("マナコスト: " + effect.getManaCost(), NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text(statusText, isBanned ? NamedTextColor.RED : NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));

        if (isGlobalBan) {
            lore.add(Component.text("(ban.ymlから変更してください)", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }

        return createButton(material,
            Component.text(effect.getDisplayName(), nameColor),
            lore);
    }

    @Override
    public boolean onClick(int slot, Player clicker, InventoryClickEvent event) {
        // 前のページ
        if (slot == 45 && page > 0) {
            page--;
            render();
            return true;
        }
        // 次のページ
        if (slot == 53) {
            int totalPages = Math.max(1, (allEffects.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
            if (page < totalPages - 1) {
                page++;
                render();
            }
            return true;
        }
        // 閉じる
        if (slot == 49) {
            clicker.closeInventory();
            return true;
        }
        // 効果グリフクリック
        if (slot >= 9 && slot <= 44) {
            int index = page * ITEMS_PER_PAGE + (slot - 9);
            if (index >= allEffects.size()) return true;

            SpellEffect effect = allEffects.get(index);
            String key = effect.getId().toString();

            // グローバルBANはGUIから変更不可
            if (plugin.getWorldSettingsManager().getGlobalBannedSpells().contains(key)) {
                clicker.sendMessage(Component.text(
                    effect.getDisplayName() + " はサーバ全体でBANされています (ban.ymlで管理)", NamedTextColor.RED));
                return true;
            }

            boolean nowBanned = plugin.getWorldSettingsManager().toggleWorldBan(worldName, key);
            if (nowBanned) {
                clicker.sendMessage(Component.text(
                    effect.getDisplayName() + " を " + worldName + " でBANしました", NamedTextColor.RED));
            } else {
                clicker.sendMessage(Component.text(
                    effect.getDisplayName() + " の " + worldName + " でのBANを解除しました", NamedTextColor.GREEN));
            }
            render();
            return true;
        }
        return false;
    }
}
