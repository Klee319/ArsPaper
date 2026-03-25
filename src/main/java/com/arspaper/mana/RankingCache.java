package com.arspaper.mana;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * オフラインプレイヤーを含むランキングデータのキャッシュ。
 * プレイヤーのログアウト時・定期フラッシュ時にJSONファイルへ保存する。
 */
public class RankingCache {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File cacheFile;
    private final Logger logger;
    private final Map<String, PlayerRankData> cache = new ConcurrentHashMap<>();

    public record PlayerRankData(String name, int glyphCount, long manaConsumed) {}

    public RankingCache(File dataFolder, Logger logger) {
        this.cacheFile = new File(dataFolder, "ranking_cache.json");
        this.logger = logger;
        load();
    }

    /**
     * オンラインプレイヤーのデータでキャッシュを更新する。
     */
    public void updatePlayer(Player player, long totalManaConsumed) {
        String json = player.getPersistentDataContainer()
            .get(ManaKeys.UNLOCKED_GLYPHS, PersistentDataType.STRING);
        int glyphCount = 0;
        if (json != null) {
            try {
                JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
                glyphCount = arr.size();
            } catch (Exception ignored) {}
        }

        cache.put(player.getUniqueId().toString(),
            new PlayerRankData(player.getName(), glyphCount, totalManaConsumed));
    }

    /**
     * キャッシュ全体を取得する。
     */
    public Map<String, PlayerRankData> getAll() {
        return Map.copyOf(cache);
    }

    /**
     * ファイルから読み込み。
     */
    private void load() {
        if (!cacheFile.exists()) return;
        try {
            String content = Files.readString(cacheFile.toPath());
            Type type = new TypeToken<Map<String, PlayerRankData>>() {}.getType();
            Map<String, PlayerRankData> loaded = GSON.fromJson(content, type);
            if (loaded != null) {
                cache.putAll(loaded);
            }
        } catch (Exception e) {
            // 読み込み失敗は無視（次回保存時に復旧）
        }
    }

    /**
     * ファイルに保存する。
     */
    public void save() {
        try {
            cacheFile.getParentFile().mkdirs();
            Files.writeString(cacheFile.toPath(), GSON.toJson(cache));
        } catch (IOException e) {
            logger.warning("ランキングキャッシュの保存に失敗: " + e.getMessage());
        }
    }
}
