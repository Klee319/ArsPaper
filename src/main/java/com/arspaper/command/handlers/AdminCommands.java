package com.arspaper.command.handlers;

import com.arspaper.ArsPaper;
import com.arspaper.block.BlockKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

/**
 * /ars cleanup / reload / pvp 系の管理サブコマンドの実装。
 */
public final class AdminCommands {

    private AdminCommands() {}

    public static int executeCleanup(Player player) {
        int removed = 0;
        for (ArmorStand stand : player.getWorld().getEntitiesByClass(ArmorStand.class)) {
            if (stand.getPersistentDataContainer().has(BlockKeys.DISPLAY_MARKER)) {
                stand.remove();
                removed++;
            }
        }
        player.sendMessage(Component.text(
            "ArsPaper表示用ArmorStandを" + removed + "体除去しました", NamedTextColor.GREEN));
        return 1;
    }

    public static int executeReload(ArsPaper plugin, CommandSender sender, boolean resetDefaults) {
        long start = System.currentTimeMillis();

        // reset: 設定ファイルをデフォルトに上書き復元
        if (resetDefaults) {
            String[] ymlFiles = {"config.yml", "glyphs.yml", "items.yml", "materials.yml", "armors.yml", "threads.yml", "ban.yml"};
            for (String yml : ymlFiles) {
                plugin.saveResource(yml, true); // true = 上書き
            }
            sender.sendMessage(Component.text("全設定ファイルをデフォルトにリセットしました", NamedTextColor.YELLOW));
        }

        // config.yml リロード
        plugin.reloadConfig();

        // form別クールダウン / 使用ゲート リロード
        plugin.getSpellCaster().reloadFormCooldowns();
        plugin.getSpellCaster().reloadUsageGate();

        // 解放ゲート（レシピ/儀式 perk + 修繕儀式コスト）リロード
        plugin.getUnlockGate().reload();

        // 厳選（selection）設定リロード
        com.arspaper.item.SelectionConfig.reload();

        // エンチャント定数リロード
        com.arspaper.enchant.ArsEnchantments.loadConfig(plugin.getConfig());

        // glyphs.yml リロード（グリフのティア・コスト・係数）
        plugin.reloadGlyphConfig();

        // materials.yml / armors.yml を先にリロード（レシピ解決に必要）
        plugin.reloadMaterialConfig();
        plugin.reloadArmorConfig();

        // 統合レシピリロード（items.yml, materials.yml, threads.yml）
        com.arspaper.recipe.UnifiedRecipeLoader loader = new com.arspaper.recipe.UnifiedRecipeLoader(plugin);
        loader.loadAll();
        plugin.getRecipeManager().unloadRecipes();
        plugin.getRecipeManager().registerWorkbenchRecipes(loader.getWorkbenchRecipes());
        plugin.getRitualRecipeRegistry().registerRecipes(loader.getRitualRecipes());

        // 防具レシピ再登録（armorConfigリロード済み）
        plugin.getRecipeManager().registerArmorRecipes(plugin.getArmorConfigManager());

        // スレッド設定リロード
        plugin.getThreadConfig().reload(plugin.getConfig());

        // マナ設定リロード（regenIntervalの変更はサーバ再起動が必要）
        com.arspaper.mana.ManaConfig newManaConfig = com.arspaper.mana.ManaConfig.fromConfig(plugin.getConfig());
        plugin.getManaManager().reloadConfig(newManaConfig);

        // ソースリンク設定リロード
        plugin.reloadSourcelinkConfig();

        // ルートチェスト設定リロード
        plugin.reloadLootConfig();

        // ワールド別設定リロード
        plugin.getWorldSettingsManager().load();

        long elapsed = System.currentTimeMillis() - start;
        sender.sendMessage(Component.text(
            "ArsPaper設定をリロードしました (" + elapsed + "ms)", NamedTextColor.GREEN));
        return 1;
    }

    public static int executePvpToggle(ArsPaper plugin, CommandSender sender, String state) {
        boolean enabled;
        if ("on".equalsIgnoreCase(state)) {
            enabled = true;
        } else if ("off".equalsIgnoreCase(state)) {
            enabled = false;
        } else {
            sender.sendMessage(Component.text("使い方: /ars pvp <on|off>", NamedTextColor.RED));
            return 0;
        }
        plugin.getConfig().set("pvp.enabled", enabled);
        plugin.saveConfig();
        sender.sendMessage(Component.text(
            "スペルPvP: " + (enabled ? "ON" : "OFF"),
            enabled ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        return 1;
    }
}
