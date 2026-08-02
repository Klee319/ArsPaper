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
            String[] ymlFiles = {"config.yml", "glyphs.yml", "items.yml", "materials.yml", "threads.yml",
                    "ban.yml", "spellbooks.yml", "sourcelinks.yml", "sourcejars.yml", "functional-items.yml"};
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

        // エンチャント定数リロード
        com.arspaper.enchant.ArsEnchantments.loadConfig(plugin.getConfig());

        // glyphs.yml リロード（グリフのティア・コスト・係数）
        plugin.reloadGlyphConfig();

        // functional-items.yml リロード（機能アイテムの表示名上書き）
        plugin.reloadFunctionalItemConfig();

        // materials.yml を先にリロード（レシピ解決に必要）
        plugin.reloadMaterialConfig();

        // spellbooks.yml リロード（魔導書ティアのスロット数・グリフ上限・PDC対応表）
        plugin.reloadSpellBookConfig();

        // spellbooks.yml catalysts: リロード（触媒のステ/マナ減/CT定義 + TrinityForge動的登録の再反映）
        plugin.reloadCatalystConfig();

        // 統合レシピリロード（items.yml, materials.yml, threads.yml）
        com.arspaper.recipe.UnifiedRecipeLoader loader = new com.arspaper.recipe.UnifiedRecipeLoader(plugin);
        loader.loadAll();
        plugin.getRecipeManager().unloadRecipes();
        plugin.getRecipeManager().registerWorkbenchRecipes(loader.getWorkbenchRecipes());
        plugin.getRitualRecipeRegistry().registerRecipes(loader.getRitualRecipes());
        // 上のmaterialConfig/spellBookConfig/catalystConfigリロードでitemRegistryの内容が変わりうるため、
        // TrinityForgeのExternalItemRegistry(customレシピのper-slot識別台帳)を再反映する。
        // 必ずrepushCatalogRituals/refreshCatalogRecipesより前 — でないと古い識別情報のままレシピ結果が再構築される。
        com.arspaper.integration.TrinityForgeBridge.registerExternalItems(plugin.getItemRegistry().getAll());
        // TF catalog.yml ritual recipes are registered via CatalogRitualBridge; re-push after Ars clears the registry.
        com.arspaper.integration.TrinityForgeBridge.repushCatalogRituals();
        // TF catalog workbench recipes: rebuild so Ars-owned results stay Ars-built after item re-registration.
        com.arspaper.integration.TrinityForgeBridge.refreshCatalogRecipes();

        // スレッド設定リロード
        plugin.getThreadConfig().reload(plugin.getConfig());

        // スレッド・セット効果設定リロード(thread-sets.yml)
        // スレッド個体差(rollSeed/quality)と厳選ステの中身は TrinityForge の
        // stats/item-stats.yml(スレッド40件それぞれの個別 fixed/per-quality/random 定義)側にあるので、
        // TF自身の /trinityforge reload でリロードされる(こちら側での個別reloadは不要)。
        if (plugin.getThreadSetConfig() != null) {
            plugin.getThreadSetConfig().reload();
        }

        // マナ設定リロード（regenIntervalの変更はサーバ再起動が必要）
        com.arspaper.mana.ManaConfig newManaConfig = com.arspaper.mana.ManaConfig.fromConfig(plugin.getConfig());
        plugin.getManaManager().reloadConfig(newManaConfig);

        // ソースリンク設定リロード
        plugin.reloadSourcelinkConfig();
        plugin.reloadSourceJarConfig();

        // ルートチェスト設定リロード
        plugin.reloadLootConfig();

        // ワールド別設定リロード
        plugin.getWorldSettingsManager().load();

        // 設定変更を装備中の全オンラインプレイヤーへ即時反映
        // (マナ集計 + スレッド戦闘ステ/セット効果PDC を再計算。未反映だと再装備/再ログインまで旧値のまま)
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            com.arspaper.item.ArmorManaListener.recalculateArmorBonus(online);
        }

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
