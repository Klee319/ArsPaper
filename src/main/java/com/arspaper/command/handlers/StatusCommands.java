package com.arspaper.command.handlers;

import com.arspaper.ArsPaper;
import com.arspaper.gui.BackpackGui;
import com.arspaper.mana.ManaKeys;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * /ars status / mana / debug / fixmana / backpack 系サブコマンドの実装。
 */
public final class StatusCommands {

    private StatusCommands() {}

    public static int executeStatus(ArsPaper plugin, Player player) {
        var pdc = player.getPersistentDataContainer();
        var config = plugin.getManaManager().getConfig();

        // === マナ上限 ===
        // 基礎値3項目は 2026-08-16 に ArsPaper config.yml(mana.default-*)へ戻ったので config から引く。
        int baseMana = config.defaultMax();
        int glyphBonus = pdc.getOrDefault(ManaKeys.GLYPH_MANA_BONUS, PersistentDataType.INTEGER, 0);
        int armorManaBonus = pdc.getOrDefault(ManaKeys.ARMOR_MANA_BONUS, PersistentDataType.INTEGER, 0);
        int threadManaBonus = pdc.getOrDefault(ManaKeys.THREAD_MANA_BONUS, PersistentDataType.INTEGER, 0);
        int enchantManaBonus = pdc.getOrDefault(ManaKeys.ENCHANT_MANA_BONUS, PersistentDataType.INTEGER, 0);
        // === ワールド補正 ===
        var wsm = plugin.getWorldSettingsManager();
        var worldMana = (wsm != null)
            ? wsm.getWorldMana(player.getWorld().getName())
            : com.arspaper.world.WorldSettingsManager.WorldManaSettings.EMPTY;
        int worldManaBonus = worldMana.maxBonus();
        int worldRegenBonus = worldMana.regenBonus();

        int totalMana = worldMana.hasFixedMax()
            ? worldMana.fixMax()
            : baseMana + glyphBonus + armorManaBonus + threadManaBonus + enchantManaBonus + worldManaBonus;
        int currentMana = plugin.getManaManager().getCurrentMana(player);

        // === マナ回復 ===
        int baseRegen = pdc.getOrDefault(ManaKeys.REGEN_RATE, PersistentDataType.INTEGER, config.defaultRegenRate());
        int threadRegenBonus = pdc.getOrDefault(ManaKeys.THREAD_REGEN_BONUS, PersistentDataType.INTEGER, 0);
        int enchantRegenBonus = pdc.getOrDefault(ManaKeys.ENCHANT_REGEN_BONUS, PersistentDataType.INTEGER, 0);
        int armorRegenBonus = pdc.getOrDefault(ManaKeys.ARMOR_REGEN_BONUS, PersistentDataType.INTEGER, 0);
        int totalRegen = worldMana.hasFixedRegen()
            ? worldMana.fixRegen()
            : baseRegen + threadRegenBonus + enchantRegenBonus + armorRegenBonus + worldRegenBonus;

        // === スレッド特殊ボーナス ===
        int costReduction = pdc.getOrDefault(ManaKeys.THREAD_COST_REDUCTION, PersistentDataType.INTEGER, 0);

        // === 表示 ===
        player.sendMessage(Component.text("═══ ArsPaper ステータス ═══", NamedTextColor.GOLD));

        // マナ
        player.sendMessage(Component.text("マナ: ", NamedTextColor.AQUA)
            .append(Component.text(currentMana + " / " + totalMana, NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  基本: " + baseMana, NamedTextColor.GRAY));
        if (glyphBonus > 0)
            player.sendMessage(Component.text("  グリフ: +" + glyphBonus, NamedTextColor.GRAY));
        if (armorManaBonus > 0)
            player.sendMessage(Component.text("  防具: +" + armorManaBonus, NamedTextColor.GRAY));
        if (threadManaBonus > 0)
            player.sendMessage(Component.text("  スレッド: +" + threadManaBonus, NamedTextColor.GRAY));
        if (enchantManaBonus > 0)
            player.sendMessage(Component.text("  エンチャント: +" + enchantManaBonus, NamedTextColor.GRAY));
        if (worldMana.hasFixedMax())
            player.sendMessage(Component.text("  ワールド固定: " + worldMana.fixMax(), NamedTextColor.YELLOW));
        else if (worldManaBonus != 0)
            player.sendMessage(Component.text("  ワールド: " + (worldManaBonus >= 0 ? "+" : "") + worldManaBonus, NamedTextColor.GRAY));

        // 回復
        // config の設定値ではなく「今スケジュールされている周期」を使う。
        // regen-interval-ticks は /ars reload では張り替わらないので、config 値を出すと
        // 再起動していないのに変わったように見える(表示だけが嘘をつく)。
        double regenInterval = plugin.getManaManager().getActiveRegenIntervalTicks() / 20.0;
        double regenPerSec = totalRegen / regenInterval;
        player.sendMessage(Component.text("回復: ", NamedTextColor.GREEN)
            .append(Component.text(totalRegen + "/tick (" + String.format("%.1f", regenPerSec) + "/秒)", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  基本: " + baseRegen, NamedTextColor.GRAY));
        if (armorRegenBonus > 0)
            player.sendMessage(Component.text("  防具: +" + armorRegenBonus, NamedTextColor.GRAY));
        if (threadRegenBonus > 0)
            player.sendMessage(Component.text("  スレッド: +" + threadRegenBonus, NamedTextColor.GRAY));
        if (enchantRegenBonus > 0)
            player.sendMessage(Component.text("  エンチャント: +" + enchantRegenBonus, NamedTextColor.GRAY));
        if (worldMana.hasFixedRegen())
            player.sendMessage(Component.text("  ワールド固定: " + worldMana.fixRegen(), NamedTextColor.YELLOW));
        else if (worldRegenBonus != 0)
            player.sendMessage(Component.text("  ワールド: " + (worldRegenBonus >= 0 ? "+" : "") + worldRegenBonus, NamedTextColor.GRAY));

        // スレッド特殊
        if (costReduction > 0) {
            player.sendMessage(Component.text("特殊: ", NamedTextColor.LIGHT_PURPLE));
            player.sendMessage(Component.text("  コスト削減: -" + costReduction + "%", NamedTextColor.GRAY));
        }

        return 1;
    }

    public static int executeManaNotifyToggle(Player player) {
        var pdc = player.getPersistentDataContainer();
        int current = pdc.getOrDefault(ManaKeys.MANA_NOTIFY_OFF, PersistentDataType.INTEGER, 0);
        if (current == 0) {
            pdc.set(ManaKeys.MANA_NOTIFY_OFF, PersistentDataType.INTEGER, 1);
            player.sendMessage(Component.text("マナ不足の通知を無効にしました", NamedTextColor.YELLOW));
        } else {
            pdc.remove(ManaKeys.MANA_NOTIFY_OFF);
            player.sendMessage(Component.text("マナ不足の通知を有効にしました", NamedTextColor.GREEN));
        }
        return 1;
    }

    public static int executeManaInfo(ArsPaper plugin, Player player) {
        int current = plugin.getManaManager().getCurrentMana(player);
        int max = plugin.getManaManager().getMaxMana(player);
        player.sendMessage(Component.text("マナ: " + current + " / " + max, NamedTextColor.AQUA));
        return 1;
    }

    public static int executeDebug(ArsPaper plugin, Player player) {
        return executeDebug(plugin, player, null);
    }

    /**
     * @param mode {@code null}=トグル, {@code true}=ON, {@code false}=OFF
     */
    public static int executeDebug(ArsPaper plugin, Player player, Boolean mode) {
        boolean enabled;
        if (mode == null) {
            enabled = plugin.getManaManager().toggleInfiniteMana(player);
        } else {
            plugin.getManaManager().setInfiniteMana(player, mode);
            enabled = mode;
        }
        if (enabled) {
            player.sendMessage(Component.text(
                    "デバッグモード: ON（マナ無限・パーク解放ゲート全通過）", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("デバッグモード: OFF", NamedTextColor.YELLOW));
        }
        return 1;
    }

    public static int executeBackpack(CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(Component.text("プレイヤーのみ使用可能", NamedTextColor.RED));
            return 0;
        }

        java.util.List<org.bukkit.inventory.ItemStack> pieces = BackpackGui.wornBackpacks(player);
        if (pieces.isEmpty()) {
            player.sendMessage(Component.text("バックパックスレッドが装備されていません", NamedTextColor.RED));
            return 0;
        }
        if (pieces.size() == 1) {
            BackpackGui.open(player, pieces.get(0));
        } else {
            BackpackGui.openSelector(player, pieces);
        }
        return 1;
    }

    /**
     * グリフ解放数からマナボーナスを再計算し、不正な値を修正する。
     */
    public static int executeFixMana(ArsPaper plugin, CommandSender sender, Player target) {
        // 現在の解放済みグリフ数を取得
        String json = target.getPersistentDataContainer()
            .get(ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING);
        int glyphCount = 0;
        if (json != null) {
            try {
                JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
                glyphCount = arr.size();
            } catch (Exception ignored) {}
        }

        int perGlyphBonus = plugin.getConfig().getInt("mana.per-glyph-unlock-bonus", 5);
        int correctBonus = glyphCount * perGlyphBonus;
        int currentBonus = target.getPersistentDataContainer()
            .getOrDefault(ManaKeys.GLYPH_MANA_BONUS, PersistentDataType.INTEGER, 0);

        target.getPersistentDataContainer().set(
            ManaKeys.GLYPH_MANA_BONUS, PersistentDataType.INTEGER, correctBonus);

        // 現在マナが新上限を超えている場合はクランプ
        int newMax = plugin.getManaManager().getMaxMana(target);
        int currentMana = plugin.getManaManager().getCurrentMana(target);
        if (currentMana > newMax) {
            plugin.getManaManager().setCurrentMana(target, newMax);
        }

        int diff = currentBonus - correctBonus;
        sender.sendMessage(Component.text(
            target.getName() + " のマナボーナスを修正: " + currentBonus + " → " + correctBonus
                + " (グリフ" + glyphCount + "個 × " + perGlyphBonus + ")"
                + (diff > 0 ? " §c(-" + diff + " 修正)" : " §a(正常)"),
            NamedTextColor.GREEN));
        return 1;
    }
}
