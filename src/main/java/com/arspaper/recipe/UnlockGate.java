package com.arspaper.recipe;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 解放ゲート（UNLOCK Model）。
 *
 * <p>TrinityForge 統合アドオンの perk 所持状況に応じて、
 * クラフトレシピ・儀式レシピの解放を制御する。さらに修繕儀式の
 * 有効/無効と追加 Source コストもここで一元管理する。
 *
 * <p>対応表は unlock-gate.yml から設定駆動で読み込み、perk 対応や
 * コストはハードコードしない。加えて、TrinityForgeがskilltreeのdedicated-effectから
 * 算出した レシピID/儀式ID→perkId集合（{@link com.arspaper.integration.TrinityForgeBridge#tfRecipeGatePerks()}
 * / {@link com.arspaper.integration.TrinityForgeBridge#tfRitualGatePerks()}）も union(OR)で参照する
 * （要件⑥: skilltree由来の解放を既存ゲートに合流）。yml/TF双方に対応perkが無いレシピ/儀式は
 * ゲート無し（解放=従来通り使用可）。
 *
 * <p>perk 所持判定は {@code com.trinityforge.pdc.PlayerData.heldPerks()} で行う。
 * TrinityForge 未ロード時は安全側に倒し、常に解放（使用可）とする。
 */
public final class UnlockGate {

    private final JavaPlugin plugin;

    /** レシピID → 必要 perk ID（不変・volatile でアトミック差し替え）。 */
    private volatile Map<String, String> recipePerks = Map.of();
    /** 儀式レシピID → 必要 perk ID（不変・volatile でアトミック差し替え）。 */
    private volatile Map<String, String> ritualPerks = Map.of();
    /** 修繕儀式が許可されているか（デフォルト true）。 */
    private volatile boolean repairEnabled = true;
    /** 修繕儀式に上乗せする追加 Source コスト（デフォルト 0、負値は 0 にクランプ）。 */
    private volatile int repairExtraSourceCost = 0;

    public UnlockGate(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * unlock-gate.yml を読み込む（reload から再実行可能）。
     * ファイルが無ければデフォルトリソースを書き出して初期化する。
     */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "unlock-gate.yml");
        if (!file.exists()) {
            plugin.saveResource("unlock-gate.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        this.recipePerks = readPerkMap(config.getConfigurationSection("recipe-perks"));
        this.ritualPerks = readPerkMap(config.getConfigurationSection("ritual-perks"));

        ConfigurationSection repair = config.getConfigurationSection("repair-ritual");
        if (repair != null) {
            this.repairEnabled = repair.getBoolean("enabled", true);
            this.repairExtraSourceCost = Math.max(0, repair.getInt("extra-source-cost", 0));
        } else {
            this.repairEnabled = true;
            this.repairExtraSourceCost = 0;
        }
    }

    /** ConfigurationSection を「キー → perk ID」の不変マップへ変換する。 */
    private Map<String, String> readPerkMap(ConfigurationSection section) {
        if (section == null) return Map.of();
        Map<String, String> map = new HashMap<>();
        for (String key : section.getKeys(false)) {
            String perk = section.getString(key);
            if (perk != null && !perk.isBlank()) {
                map.put(key, perk);
            }
        }
        return Map.copyOf(map);
    }

    /**
     * 指定レシピのクラフト解放をプレイヤーが満たすか判定する。
     *
     * @param player   対象プレイヤー
     * @param recipeId レシピID（NamespacedKey のキー部分）
     */
    public boolean hasRecipePermission(Player player, String recipeId) {
        return hasPermission(player, recipePerks.get(recipeId),
            com.arspaper.integration.TrinityForgeBridge.tfRecipeGatePerks().get(recipeId));
    }

    /**
     * レシピが perk ゲートの対象かどうかをプレイヤー非依存で判定する。
     *
     * <p>Crafterブロック等、クラフト主体のプレイヤーを特定できない自動化経路で使用する。
     * ゲート対象（yml の recipe-perks または TF の skilltree由来マップのいずれかに定義あり）なら
     * true を返し、呼び出し側は fail-closed（キャンセル）で扱う。
     *
     * @param recipeId レシピID（NamespacedKey のキー部分）
     */
    public boolean isRecipeGated(String recipeId) {
        String requiredPerk = recipePerks.get(recipeId);
        if (requiredPerk != null && !requiredPerk.isBlank()) {
            return true;
        }
        Set<String> tfPerks = com.arspaper.integration.TrinityForgeBridge.tfRecipeGatePerks().get(recipeId);
        return tfPerks != null && !tfPerks.isEmpty();
    }

    /**
     * 指定儀式レシピの解放をプレイヤーが満たすか判定する。
     *
     * @param player   対象プレイヤー
     * @param ritualId 儀式レシピID（RitualRecipe.id()）
     */
    public boolean hasRitualPermission(Player player, String ritualId) {
        return hasPermission(player, ritualPerks.get(ritualId),
            com.arspaper.integration.TrinityForgeBridge.tfRitualGatePerks().get(ritualId));
    }

    /** 修繕儀式が許可されているか。 */
    public boolean isRepairEnabled() {
        return repairEnabled;
    }

    /** 修繕儀式に上乗せする追加 Source コスト（0 以上）。 */
    public int repairExtraSourceCost() {
        return repairExtraSourceCost;
    }

    /**
     * perk 所持判定の共通ロジック（yml由来perk ∪ TF(skilltree)由来perk集合のunion判定）。
     *
     * - yml由来 requiredPerk・TF由来 tfPerks が両方とも未定義（null/空）: ゲート無し → true
     * - 上記union集合とプレイヤーのheldPerksが交わる: true / 交わらない: false
     * - TrinityForge 未ロード等で確認できない場合: 安全側に倒し true
     */
    private boolean hasPermission(Player player, String requiredPerk, Set<String> tfPerks) {
        if (isDebugMode(player)) {
            return true;
        }
        boolean ymlGated = requiredPerk != null && !requiredPerk.isBlank();
        boolean tfGated = tfPerks != null && !tfPerks.isEmpty();
        if (!ymlGated && !tfGated) {
            return true; // ゲート無し（yml/TF双方に対応perk無し。従来通り解放）
        }
        try {
            java.util.List<String> held = com.trinityforge.pdc.PlayerData.of(player).heldPerks();
            if (ymlGated && held.contains(requiredPerk)) {
                return true;
            }
            if (tfGated) {
                for (String perk : tfPerks) {
                    if (held.contains(perk)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Throwable t) {
            // TrinityForge 未ロード/参照不可時はゲートを無効化（解放=従来通り使用可。LinkageError 等も安全側に倒す）
            return true;
        }
    }

    /** {@code /ars debug} ON 時はパーク解放ゲートを全通過。 */
    private static boolean isDebugMode(Player player) {
        try {
            com.arspaper.ArsPaper ars = com.arspaper.ArsPaper.getInstance();
            return ars != null && ars.getManaManager() != null && ars.getManaManager().isDebugMode(player);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
