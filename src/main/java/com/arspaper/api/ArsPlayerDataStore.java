package com.arspaper.api;

import com.arspaper.api.modifier.ArsModifier;
import com.arspaper.api.modifier.ModifierStore;
import com.arspaper.api.modifier.ModifierType;
import com.arspaper.mana.ManaKeys;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * プレイヤー単位の unlock 状態 + modifier をyml永続化するストア。
 *
 * 保存先: plugins/ArsPaper/playerdata/&lt;uuid&gt;.yml
 *
 * - unlocked-rituals / unlocked-recipes は本クラスでローカル管理
 *   (unlocked-glyphs は既存 ManaKeys.UNLOCKED_GLYPHS と一元化のため
 *    既存PDC側を権威データとして参照)
 * - modifier は ModifierStore のサブスナップショットを保存/復元
 *
 * config.yml の api.modifier-persistence が false の場合、
 * modifier セクションは保存しない（起動時クリア相当）。
 */
public final class ArsPlayerDataStore {

    private final JavaPlugin plugin;
    private final ModifierStore modifierStore;
    private final boolean persistModifiers;

    private final File dataDir;
    private final Map<UUID, Set<String>> unlockedRituals = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> unlockedRecipes = new ConcurrentHashMap<>();

    public ArsPlayerDataStore(JavaPlugin plugin, ModifierStore store, boolean persistModifiers) {
        this.plugin = plugin;
        this.modifierStore = store;
        this.persistModifiers = persistModifiers;
        this.dataDir = new File(plugin.getDataFolder(), "playerdata");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create playerdata directory: " + dataDir);
        }
    }

    public boolean isPersistModifiers() { return persistModifiers; }

    public Set<String> getUnlockedRituals(UUID uuid) {
        return Collections.unmodifiableSet(unlockedRituals.getOrDefault(uuid, Set.of()));
    }

    public Set<String> getUnlockedRecipes(UUID uuid) {
        return Collections.unmodifiableSet(unlockedRecipes.getOrDefault(uuid, Set.of()));
    }

    public boolean isRitualUnlocked(UUID uuid, String ritualId) {
        Set<String> set = unlockedRituals.get(uuid);
        return set != null && set.contains(ritualId);
    }

    public boolean isRecipeUnlocked(UUID uuid, String recipeId) {
        Set<String> set = unlockedRecipes.get(uuid);
        return set != null && set.contains(recipeId);
    }

    public boolean unlockRitual(UUID uuid, String ritualId) {
        Set<String> set = unlockedRituals.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        return set.add(ritualId);
    }

    public boolean lockRitual(UUID uuid, String ritualId) {
        Set<String> set = unlockedRituals.get(uuid);
        return set != null && set.remove(ritualId);
    }

    public boolean unlockRecipe(UUID uuid, String recipeId) {
        Set<String> set = unlockedRecipes.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        return set.add(recipeId);
    }

    public boolean lockRecipe(UUID uuid, String recipeId) {
        Set<String> set = unlockedRecipes.get(uuid);
        return set != null && set.remove(recipeId);
    }

    /** ファイルから unlock 状態 + (有効な場合) modifier を読み込む。 */
    public void load(Player player) {
        File file = playerFile(player.getUniqueId());
        if (!file.exists()) return;
        loadInternal(player.getUniqueId(), file, player);
    }

    public void load(UUID uuid) {
        File file = playerFile(uuid);
        if (!file.exists()) return;
        loadInternal(uuid, file, plugin.getServer().getPlayer(uuid));
    }

    private void loadInternal(UUID uuid, File file, Player onlinePlayer) {
        YamlConfiguration cfg;
        try {
            cfg = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load playerdata: " + file, e);
            return;
        }

        Set<String> rituals = new HashSet<>(cfg.getStringList("unlocked-rituals"));
        Set<String> recipes = new HashSet<>(cfg.getStringList("unlocked-recipes"));
        unlockedRituals.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).addAll(rituals);
        unlockedRecipes.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).addAll(recipes);

        // 仕様§10.1: unlocked-glyphs を yml に保存しておく
        // PDC ManaKeys.UNLOCKED_GLYPHS が権威データ。yml に entry があり PDC が空なら復元する。
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            List<String> ymlGlyphs = cfg.getStringList("unlocked-glyphs");
            if (!ymlGlyphs.isEmpty()) {
                PersistentDataContainer pdc = onlinePlayer.getPersistentDataContainer();
                String existing = pdc.get(ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING);
                Set<String> pdcSet = new HashSet<>();
                if (existing != null) {
                    try {
                        JsonParser.parseString(existing).getAsJsonArray()
                            .forEach(el -> pdcSet.add(el.getAsString()));
                    } catch (Exception ignored) {}
                }
                if (pdcSet.isEmpty()) {
                    // PDC 側が空 → yml から復元
                    JsonArray arr = new JsonArray();
                    ymlGlyphs.forEach(arr::add);
                    pdc.set(ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING, arr.toString());
                }
            }
        }

        if (persistModifiers) {
            List<ArsModifier> mods = new ArrayList<>();
            List<Map<?, ?>> rawMods = cfg.getMapList("modifiers");
            for (Map<?, ?> raw : rawMods) {
                Object keyObj = raw.get("key");
                Object typeObj = raw.get("type");
                Object valObj = raw.get("value");
                if (keyObj == null || typeObj == null || valObj == null) continue;
                NamespacedKey key = NamespacedKey.fromString(keyObj.toString());
                if (key == null) continue;
                ModifierType type;
                try {
                    type = ModifierType.valueOf(typeObj.toString());
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Unknown ModifierType in " + file + ": " + typeObj);
                    continue;
                }
                double value;
                try {
                    value = Double.parseDouble(valObj.toString());
                } catch (NumberFormatException ex) {
                    continue;
                }
                mods.add(new ArsModifier(key, type, value));
            }
            modifierStore.restore(uuid, mods);
        }
    }

    /** ファイルに unlock 状態 + (有効な場合) modifier を書き出す。 */
    public void save(Player player) {
        save(player.getUniqueId());
    }

    public void save(UUID uuid) {
        File file = playerFile(uuid);
        YamlConfiguration cfg = new YamlConfiguration();

        Set<String> rituals = unlockedRituals.get(uuid);
        Set<String> recipes = unlockedRecipes.get(uuid);
        if (rituals != null) cfg.set("unlocked-rituals", new ArrayList<>(rituals));
        if (recipes != null) cfg.set("unlocked-recipes", new ArrayList<>(recipes));

        // 仕様§10.1: PDC の unlocked-glyphs を yml にミラーする (運用ツールが yml を読めるように)
        Player online = plugin.getServer().getPlayer(uuid);
        if (online != null && online.isOnline()) {
            String json = online.getPersistentDataContainer()
                .get(ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING);
            if (json != null) {
                List<String> ymlGlyphs = new ArrayList<>();
                try {
                    JsonParser.parseString(json).getAsJsonArray()
                        .forEach(el -> ymlGlyphs.add(el.getAsString()));
                } catch (Exception e) {
                    plugin.getLogger().warning(
                        "Failed to mirror unlocked-glyphs to yml for " + uuid + ": " + e.getMessage());
                }
                if (!ymlGlyphs.isEmpty()) {
                    cfg.set("unlocked-glyphs", ymlGlyphs);
                }
            }
        }

        if (persistModifiers) {
            List<ArsModifier> mods = modifierStore.snapshot(uuid);
            List<Map<String, Object>> rawMods = new ArrayList<>();
            for (ArsModifier m : mods) {
                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put("key", m.key().toString());
                raw.put("type", m.type().name());
                raw.put("value", m.value());
                rawMods.add(raw);
            }
            cfg.set("modifiers", rawMods);
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save playerdata: " + file, e);
        }
    }

    /** 全オンラインプレイヤーのデータを保存する。 */
    public void saveAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            save(p);
        }
    }

    /** プレイヤーログアウト時のキャッシュクリア。 */
    public void unload(UUID uuid) {
        save(uuid);
        unlockedRituals.remove(uuid);
        unlockedRecipes.remove(uuid);
        if (!persistModifiers) {
            modifierStore.clear(uuid);
        }
    }

    private File playerFile(UUID uuid) {
        return new File(dataDir, uuid + ".yml");
    }
}
