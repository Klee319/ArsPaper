package com.arspaper.util;

import org.bukkit.Material;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Minecraft公式日本語翻訳データを使ったMaterial名の日本語化。
 * ja_items.properties（リソース内）からMaterial名→日本語名のマッピングを読み込む。
 */
public final class JaTranslations {

    private static final Map<String, String> TRANSLATIONS = new HashMap<>();

    private JaTranslations() {}

    /**
     * プラグイン起動時に呼び出して翻訳データを読み込む。
     */
    public static void load(Logger logger) {
        TRANSLATIONS.clear();
        try (InputStream is = JaTranslations.class.getClassLoader()
                .getResourceAsStream("ja_items.properties")) {
            if (is == null) {
                logger.warning("ja_items.properties not found in resources");
                return;
            }
            Properties props = new Properties();
            props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            for (String key : props.stringPropertyNames()) {
                TRANSLATIONS.put(key, props.getProperty(key));
            }
            logger.info("Loaded " + TRANSLATIONS.size() + " Japanese item translations");
        } catch (Exception e) {
            logger.warning("Failed to load ja_items.properties: " + e.getMessage());
        }
    }

    /**
     * Material名を日本語に変換する。翻訳がない場合はフォーマット済み英語名を返す。
     */
    public static String translate(Material material) {
        String ja = TRANSLATIONS.get(material.name());
        if (ja != null) return ja;
        // フォールバック: DIAMOND_SWORD → Diamond Sword
        String[] words = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }
        return sb.toString();
    }

    /**
     * ポーション効果名を日本語に変換する (W-167, 2026-08-20)。
     *
     * <p>翻訳表のキーは {@code EFFECT_<PotionEffectType 名>}。Material 名と同じ表に同居させるので
     * 接頭辞で衝突を避けている（{@code LUCK} は Material にも Effect にも存在する）。
     * 表に無ければ整形した英語名へ倒す（生の識別子は出さない）。
     */
    public static String translateEffect(org.bukkit.potion.PotionEffectType effect) {
        if (effect == null) return "不明";
        String name = effect.getKey().getKey().toUpperCase();
        String ja = TRANSLATIONS.get("EFFECT_" + name);
        if (ja != null) return ja;
        return titleCase(name);
    }

    private static String titleCase(String upperSnake) {
        String[] words = upperSnake.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }
        return sb.toString();
    }

    /**
     * Material名(文字列)を日本語に変換する。
     */
    public static String translate(String materialName) {
        Material mat = Material.matchMaterial(materialName);
        if (mat != null) return translate(mat);
        String ja = TRANSLATIONS.get(materialName.toUpperCase());
        if (ja != null) return ja;
        return materialName;
    }
}
