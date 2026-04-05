package com.arspaper.world;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * ワールド別設定を管理する。
 * - スペルBAN（効果グリフの使用禁止）
 * - マナ補正（最大マナ/回復量の固定値・補正値）
 *
 * ban.yml: サーバ全体のデフォルトBAN
 * world_settings.yml: ワールド別のBAN + マナ補正
 */
public class WorldSettingsManager {

    private final JavaPlugin plugin;
    private final File banFile;
    private final File settingsFile;

    /** サーバ全体のデフォルトBAN（ban.ymlから読み込み） */
    private final Set<String> globalBannedSpells = new HashSet<>();

    /** ワールド別BAN */
    private final Map<String, Set<String>> worldBannedSpells = new HashMap<>();

    /** ワールド別マナ設定 */
    private final Map<String, WorldManaSettings> worldManaSettings = new HashMap<>();

    public WorldSettingsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.banFile = new File(plugin.getDataFolder(), "ban.yml");
        this.settingsFile = new File(plugin.getDataFolder(), "world_settings.yml");
        load();
    }

    public void load() {
        globalBannedSpells.clear();
        worldBannedSpells.clear();
        worldManaSettings.clear();

        // ban.yml: サーバ全体デフォルトBAN
        if (!banFile.exists()) {
            plugin.saveResource("ban.yml", false);
        }
        YamlConfiguration banConfig = YamlConfiguration.loadConfiguration(banFile);
        List<String> defaultBans = banConfig.getStringList("banned-spells");
        globalBannedSpells.addAll(defaultBans);

        // world_settings.yml: ワールド別設定
        if (!settingsFile.exists()) {
            saveSettings();
            return;
        }
        YamlConfiguration settings = YamlConfiguration.loadConfiguration(settingsFile);

        ConfigurationSection worldsSection = settings.getConfigurationSection("worlds");
        if (worldsSection == null) return;

        for (String worldName : worldsSection.getKeys(false)) {
            ConfigurationSection ws = worldsSection.getConfigurationSection(worldName);
            if (ws == null) continue;

            // BAN設定
            List<String> bans = ws.getStringList("banned-spells");
            if (!bans.isEmpty()) {
                worldBannedSpells.put(worldName, new HashSet<>(bans));
            }

            // マナ設定
            ConfigurationSection mana = ws.getConfigurationSection("mana");
            if (mana != null) {
                WorldManaSettings manaSettings = new WorldManaSettings(
                    mana.getInt("max-bonus", 0),
                    mana.getInt("regen-bonus", 0),
                    mana.getInt("fix-max", -1),
                    mana.getInt("fix-regen", -1)
                );
                worldManaSettings.put(worldName, manaSettings);
            }
        }
    }

    public void saveSettings() {
        YamlConfiguration settings = new YamlConfiguration();
        settings.options().header("""
            ArsPaper ワールド別設定
            worlds.<world-name>.banned-spells: BANされたスペルのリスト
            worlds.<world-name>.mana.max-bonus: 最大マナ補正値
            worlds.<world-name>.mana.regen-bonus: 回復量補正値
            worlds.<world-name>.mana.fix-max: 固定最大マナ(-1で無効)
            worlds.<world-name>.mana.fix-regen: 固定回復量(-1で無効)""");

        for (String worldName : getAllConfiguredWorlds()) {
            String prefix = "worlds." + worldName;

            Set<String> bans = worldBannedSpells.getOrDefault(worldName, Set.of());
            settings.set(prefix + ".banned-spells", new ArrayList<>(bans));

            WorldManaSettings mana = worldManaSettings.getOrDefault(worldName, WorldManaSettings.EMPTY);
            settings.set(prefix + ".mana.max-bonus", mana.maxBonus());
            settings.set(prefix + ".mana.regen-bonus", mana.regenBonus());
            settings.set(prefix + ".mana.fix-max", mana.fixMax());
            settings.set(prefix + ".mana.fix-regen", mana.fixRegen());
        }

        try {
            settings.save(settingsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save world_settings.yml: " + e.getMessage());
        }
    }

    private Set<String> getAllConfiguredWorlds() {
        Set<String> worlds = new HashSet<>();
        worlds.addAll(worldBannedSpells.keySet());
        worlds.addAll(worldManaSettings.keySet());
        return worlds;
    }

    // ===== スペルBAN =====

    /**
     * 指定ワールドでスペルがBANされているか判定。
     * グローバルBAN + ワールド別BANの両方をチェック。
     */
    public boolean isSpellBanned(String worldName, String spellKey) {
        if (globalBannedSpells.contains(spellKey)) return true;
        Set<String> worldBans = worldBannedSpells.get(worldName);
        return worldBans != null && worldBans.contains(spellKey);
    }

    /**
     * 指定ワールドのBAN済みスペルセットを取得（グローバル含む）。
     */
    public Set<String> getBannedSpells(String worldName) {
        Set<String> result = new HashSet<>(globalBannedSpells);
        Set<String> worldBans = worldBannedSpells.get(worldName);
        if (worldBans != null) result.addAll(worldBans);
        return Collections.unmodifiableSet(result);
    }

    /**
     * ワールド固有のBAN済みスペルセットを取得（グローバル除外）。
     */
    public Set<String> getWorldOnlyBannedSpells(String worldName) {
        Set<String> worldBans = worldBannedSpells.get(worldName);
        return worldBans != null ? Collections.unmodifiableSet(worldBans) : Set.of();
    }

    /**
     * ワールドのBAN設定をトグルする。
     * @return トグル後の状態（true=BAN, false=解除）
     */
    /**
     * ワールドのBAN設定をトグルする。保存は呼び出し元で行う。
     * @return トグル後の状態（true=BAN, false=解除）
     */
    public boolean toggleWorldBan(String worldName, String spellKey) {
        Set<String> bans = worldBannedSpells.computeIfAbsent(worldName, k -> new HashSet<>());
        boolean nowBanned;
        if (bans.contains(spellKey)) {
            bans.remove(spellKey);
            nowBanned = false;
        } else {
            bans.add(spellKey);
            nowBanned = true;
        }
        return nowBanned;
    }

    /**
     * グローバルBAN済みスペルセットを取得。
     */
    public Set<String> getGlobalBannedSpells() {
        return Collections.unmodifiableSet(globalBannedSpells);
    }

    // ===== ワールドマナ設定 =====

    /**
     * 指定ワールドのマナ設定を取得。
     */
    public WorldManaSettings getWorldMana(String worldName) {
        return worldManaSettings.getOrDefault(worldName, WorldManaSettings.EMPTY);
    }

    public void setWorldManaMaxBonus(String worldName, int value) {
        WorldManaSettings current = getWorldMana(worldName);
        worldManaSettings.put(worldName, new WorldManaSettings(
            value, current.regenBonus(), current.fixMax(), current.fixRegen()));
        saveSettings();
    }

    public void setWorldManaRegenBonus(String worldName, int value) {
        WorldManaSettings current = getWorldMana(worldName);
        worldManaSettings.put(worldName, new WorldManaSettings(
            current.maxBonus(), value, current.fixMax(), current.fixRegen()));
        saveSettings();
    }

    public void setWorldManaFixMax(String worldName, int value) {
        WorldManaSettings current = getWorldMana(worldName);
        worldManaSettings.put(worldName, new WorldManaSettings(
            current.maxBonus(), current.regenBonus(), value, current.fixRegen()));
        saveSettings();
    }

    public void setWorldManaFixRegen(String worldName, int value) {
        WorldManaSettings current = getWorldMana(worldName);
        worldManaSettings.put(worldName, new WorldManaSettings(
            current.maxBonus(), current.regenBonus(), current.fixMax(), value));
        saveSettings();
    }

    /**
     * ワールド別マナ設定レコード。
     * fixMax/fixRegen が -1 の場合は無効（通常計算を使用）。
     */
    public record WorldManaSettings(int maxBonus, int regenBonus, int fixMax, int fixRegen) {
        public static final WorldManaSettings EMPTY = new WorldManaSettings(0, 0, -1, -1);

        public boolean hasFixedMax() { return fixMax >= 0; }
        public boolean hasFixedRegen() { return fixRegen >= 0; }
    }
}
