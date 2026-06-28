package com.arspaper.spell;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 使用ゲート（UNLOCK Model Y）。
 * グリフの入手(scribe)は自由だが、使用には対応するperkが必要という制限を扱う。
 *
 * perk所持の確認は TrinityForge 統合アドオンの PlayerData.heldPerks() で行う。
 * 対応表は usage-gate.yml の glyph-perks セクションから設定駆動で読み込む
 * （ハードコードしない）。未定義の glyph はゲート無し（自由使用）。
 *
 * TrinityForge 未ロード時は安全側に倒し、常に使用可とする。
 */
public final class UsageGate {

    private final JavaPlugin plugin;
    /** glyphキー → 使用に必要なperk ID（不変・volatileでアトミック差し替え）。 */
    private volatile Map<String, String> glyphPerks = Map.of();

    public UsageGate(JavaPlugin plugin) {
        this.plugin = plugin;
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
     * - 対応perkが未定義のglyph: ゲート無し → true
     * - perk所持: true / 未所持: false
     * - TrinityForge 未ロード等で確認できない場合: 安全側に倒し true
     *
     * @param player   対象プレイヤー
     * @param glyphKey glyphキー（NamespacedKey.getKey() の文字列。例 "fireball"）
     */
    public boolean hasPermission(Player player, String glyphKey) {
        String requiredPerk = glyphPerks.get(glyphKey);
        if (requiredPerk == null || requiredPerk.isBlank()) {
            return true; // ゲート無し（自由使用）
        }
        try {
            return com.trinityforge.pdc.PlayerData.of(player)
                .heldPerks()
                .contains(requiredPerk);
        } catch (Throwable t) {
            // TrinityForge 未ロード/参照不可時は使用を妨げない（LinkageError 等も安全側に倒す）
            return true;
        }
    }
}
