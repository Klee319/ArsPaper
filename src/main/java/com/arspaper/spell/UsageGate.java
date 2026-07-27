package com.arspaper.spell;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 使用ゲート（UNLOCK Model Y）。
 * グリフの入手(scribe)は自由だが、使用には対応するperkが必要という制限を扱う。
 *
 * perk所持の確認は TrinityForge 統合アドオンの PlayerData.heldPerks() で行う。
 * 対応表は usage-gate.yml の glyph-perks セクションから設定駆動で読み込む
 * （ハードコードしない）。加えて、TrinityForgeがskilltreeのdedicated-effectから
 * 算出した glyphキー→perkId集合（{@link com.arspaper.integration.TrinityForgeBridge#tfGlyphGatePerks()}）も
 * union(OR)で参照する（要件⑥: skilltree由来の解放を既存ゲートに合流）。
 * yml/TF双方に対応perkが無い glyph はゲート無し（自由使用）。
 *
 * TrinityForge 未ロード時は安全側に倒し、常に使用可とする。
 */
public final class UsageGate {

    private final JavaPlugin plugin;
    /** アイテム右クリックによる恒久解放グリフ集合（perkゲートとは独立したORルート）。 */
    private final UnlockedGlyphs unlockedGlyphs;
    /** glyphキー → 使用に必要なperk ID（不変・volatileでアトミック差し替え）。 */
    private volatile Map<String, String> glyphPerks = Map.of();

    public UsageGate(JavaPlugin plugin, UnlockedGlyphs unlockedGlyphs) {
        this.plugin = plugin;
        this.unlockedGlyphs = unlockedGlyphs;
        reload();
    }

    /**
     * usage-gate.yml を読み込む（reloadから再実行可能）。
     * ファイルが無ければデフォルトリソースを書き出して初期化する。
     */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "usage-gate.yml");
        if (!file.exists()) {
            plugin.saveResource("usage-gate.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        Map<String, String> newMap = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("glyph-perks");
        if (section != null) {
            for (String glyphKey : section.getKeys(false)) {
                String perk = section.getString(glyphKey);
                if (perk != null && !perk.isBlank()) {
                    newMap.put(glyphKey, perk);
                }
            }
        }
        this.glyphPerks = Map.copyOf(newMap);
    }

    /**
     * 指定glyphの使用権限をプレイヤーが満たすか判定する。
     *
     * - yml(usage-gate.yml)由来perk ∪ TF(skilltree dedicated-effect)由来perk集合が
     *   両方とも空: ゲート無し → true
     * - 上記union集合とプレイヤーのheldPerksが交わる: true
     * - 交わらない場合でもアイテム解放(UnlockedGlyphs)済みなら true
     * - TrinityForge 未ロード等で確認できない場合: 安全側に倒し true
     *
     * @param player   対象プレイヤー
     * @param glyphKey glyphキー（NamespacedKey.getKey() の文字列。例 "fireball"）
     */
    public boolean hasPermission(Player player, String glyphKey) {
        if (isDebugMode(player)) {
            return true;
        }
        String requiredPerk = glyphPerks.get(glyphKey);
        Set<String> tfPerks = com.arspaper.integration.TrinityForgeBridge.tfGlyphGatePerks().get(glyphKey);
        boolean ymlGated = requiredPerk != null && !requiredPerk.isBlank();
        boolean tfGated = tfPerks != null && !tfPerks.isEmpty();
        if (!ymlGated && !tfGated) {
            return true; // ゲート無し（yml/TF双方に対応perk無し。従来通り自由使用）
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
            // perkゲート(yml∪TF)を満たさない場合でも、アイテム解放による恒久解放(OR)を確認する。
            return unlockedGlyphs != null && unlockedGlyphs.contains(player, glyphKey);
        } catch (Throwable t) {
            // TrinityForge 未ロード/参照不可時、または本fork独自PDC参照不可時も
            // 使用を妨げない（LinkageError 等も安全側に倒す）
            return true;
        }
    }

    /** {@code /ars debug} ON 時はグリフ使用ゲートを全通過。 */
    private static boolean isDebugMode(Player player) {
        try {
            com.arspaper.ArsPaper ars = com.arspaper.ArsPaper.getInstance();
            return ars != null && ars.getManaManager() != null && ars.getManaManager().isDebugMode(player);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
