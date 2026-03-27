package com.arspaper.spell;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

import java.io.File;
import java.util.*;
import java.util.Set;

/**
 * glyphs.yml からグリフの設定（ティア、マナコスト、解放コスト）を読み込む。
 */
public class GlyphConfig {

    private volatile Map<String, GlyphData> glyphData = new HashMap<>();
    private final Logger logger;
    private final JavaPlugin plugin;

    public GlyphConfig(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
        this.plugin = plugin;
        load();
    }

    /**
     * glyphs.ymlを再読み込みする。同一インスタンスを再利用するため、
     * 既存のSpellComponentが保持する参照はそのまま有効。
     */
    public void reload() {
        load();
        logger.info("Reloaded glyphs.yml (" + glyphData.size() + " glyphs)");
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), "glyphs.yml");
        if (!file.exists()) {
            plugin.saveResource("glyphs.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection glyphs = config.getConfigurationSection("glyphs");
        if (glyphs == null) return;

        // 新マップに構築してからアトミックに差し替え
        Map<String, GlyphData> newData = new HashMap<>();

        for (String key : glyphs.getKeys(false)) {
            ConfigurationSection section = glyphs.getConfigurationSection(key);
            if (section == null) continue;

            int tier = section.getInt("tier", 1);
            int manaCost = section.getInt("mana-cost", 10);

            int unlockLevel = 5;
            Map<Material, Integer> unlockMaterials = new LinkedHashMap<>();

            ConfigurationSection unlockSection = section.getConfigurationSection("unlock-cost");
            if (unlockSection != null) {
                unlockLevel = unlockSection.getInt("level", 5);
                ConfigurationSection matsSection = unlockSection.getConfigurationSection("materials");
                if (matsSection != null) {
                    for (String matKey : matsSection.getKeys(false)) {
                        Material mat = Material.matchMaterial(matKey);
                        if (mat != null) {
                            unlockMaterials.put(mat, matsSection.getInt(matKey, 1));
                        }
                    }
                }
            }

            // augments互換性はソースコード定義(AUGMENT_COMPAT)のため、ymlからは読まない
            List<String> compatibleEffects = section.getStringList("effects");

            Map<String, Double> params = new LinkedHashMap<>();
            ConfigurationSection paramsSection = section.getConfigurationSection("params");
            if (paramsSection != null) {
                for (String paramKey : paramsSection.getKeys(false)) {
                    params.put(paramKey, paramsSection.getDouble(paramKey));
                }
            }

            // 増強の最大スタック数
            Map<String, Integer> maxAugments = new LinkedHashMap<>();
            ConfigurationSection maxAugSection = section.getConfigurationSection("max-augments");
            if (maxAugSection != null) {
                for (String augKey : maxAugSection.getKeys(false)) {
                    maxAugments.put(augKey, maxAugSection.getInt(augKey));
                }
            }

            newData.put(key, new GlyphData(tier, manaCost, unlockLevel, unlockMaterials, compatibleEffects, params, maxAugments));
        }

        // アトミックに差し替え（volatile書き込み）
        this.glyphData = newData;

        // 交換・粉砕マッピング
        loadExchangeTiers(config);
        loadCrushMap(config);
    }

    public int getTier(String glyphKey) {
        GlyphData data = glyphData.get(glyphKey);
        return data != null ? data.tier : 1;
    }

    public int getManaCost(String glyphKey) {
        GlyphData data = glyphData.get(glyphKey);
        return data != null ? data.manaCost : 10;
    }

    public int getUnlockLevel(String glyphKey) {
        GlyphData data = glyphData.get(glyphKey);
        return data != null ? data.unlockLevel : 5;
    }

    public Map<Material, Integer> getUnlockMaterials(String glyphKey) {
        GlyphData data = glyphData.get(glyphKey);
        return data != null ? Collections.unmodifiableMap(data.unlockMaterials) : Map.of(Material.LAPIS_LAZULI, 1);
    }

    public String getUnlockCostDescription(String glyphKey) {
        int level = getUnlockLevel(glyphKey);
        Map<Material, Integer> mats = getUnlockMaterials(glyphKey);
        StringBuilder sb = new StringBuilder();
        sb.append(level).append("レベル");
        for (Map.Entry<Material, Integer> entry : mats.entrySet()) {
            sb.append(" + ").append(localizeMatName(entry.getKey())).append(entry.getValue()).append("個");
        }
        return sb.toString();
    }

    /** 素材名の日本語化（公開メソッド） */
    public String localizeMatNamePublic(Material mat) {
        return com.arspaper.util.JaTranslations.translate(mat);
    }

    private String localizeMatName(Material mat) {
        return com.arspaper.util.JaTranslations.translate(mat);
    }

    /**
     * グリフ固有の数値パラメータを取得する。
     * glyphs.yml の params セクションから読み込まれた値を返す。
     */
    public double getParam(String glyphKey, String paramName, double defaultValue) {
        GlyphData data = glyphData.get(glyphKey);
        if (data == null || data.params == null) return defaultValue;
        return data.params.getOrDefault(paramName, defaultValue);
    }

    /** どのEffect/Formにも付けられる汎用Augment */
    private static final Set<String> UNIVERSAL_AUGMENTS = Set.of(
        "delay"
    );

    /**
     * Augment互換性マップ（ソースコード定義）。
     * glyphs.ymlのaugmentsフィールドは参照しない（プログラムの仕様で決まるため）。
     * configで変更しても効果のコードが対応しない増強は無意味なのでハードコード。
     */
    /**
     * Augment互換性マップ（ソースコード定義）。
     *
     * dampen(減衰)はamplifyLevel を負にするが、Math.max(0, amplifyLevel) やブール分岐で
     * 0にクランプされる効果では単体で搭載しても変化がない（増幅の相殺にしかならない）。
     * そのような効果からはdampenを除外し、実際にデフォルト状態から減衰できる効果のみに搭載可能とする。
     */
    private static final Map<String, Set<String>> AUGMENT_COMPAT = Map.ofEntries(
        // === Forms ===
        Map.entry("projectile", Set.of("accelerate", "decelerate", "pierce", "split", "rapid_fire", "trace", "propagate", "extend_reach")),
        Map.entry("touch",      Set.of("rapid_fire", "propagate")),
        Map.entry("self",       Set.of("rapid_fire")),
        Map.entry("underfoot",  Set.of("rapid_fire")),
        Map.entry("orbit",      Set.of("accelerate", "decelerate", "split", "extend_time", "duration_down", "rapid_fire", "aoe_radius")),
        // wall は未登録（廃止済み）
        // beam: pierceは内蔵（INT_MAX貫通）のため互換リストに含めない
        Map.entry("beam",       Set.of("accelerate", "decelerate", "split", "aoe_radius", "rapid_fire", "trace", "extend_reach")),
        // === Effects - Tier 1 ===
        // aoe = 範囲[水平]/[垂直]（方向性あり: 破壊/設置系）
        // aoe_radius = 半径増加（単純な範囲拡大: それ以外）
        Map.entry("break",           Set.of("amplify", "dampen", "aoe", "extract", "fortune")),            // dampen: ユーティリティモード切替
        Map.entry("harm",            Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down", "linger", "propagate")), // dampen: ダメージ減少
        Map.entry("ignite",          Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down", "linger", "propagate")), // dampen: ダメージ減少（最低0.5）
        Map.entry("freeze",          Set.of("amplify", "aoe_radius", "extend_time", "duration_down", "linger", "propagate")),           // dampen除外: amplifier Math.max(0)
        Map.entry("knockback",       Set.of("amplify", "dampen", "aoe_radius", "linger", "propagate")),    // dampen: ノックバック力減少
        Map.entry("pull",            Set.of("amplify", "dampen", "aoe_radius", "linger", "propagate")),    // dampen: 引き寄せ力減少
        Map.entry("gravity",         Set.of("amplify", "aoe_radius", "extend_time", "duration_down", "linger", "propagate")),           // dampen除外: amplifier Math.max(0)
        Map.entry("light",           Set.of("aoe", "extend_time", "duration_down")),
        Map.entry("harvest",         Set.of("amplify", "aoe_radius", "fortune", "extract")),               // dampen除外: amplify Math.max(0)
        Map.entry("cut",             Set.of("aoe")),
        Map.entry("interact",        Set.of("aoe", "extend_time", "duration_down")),
        Map.entry("pickup",          Set.of("aoe_radius")),
        Map.entry("rotate",          Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down")), // dampen: 逆回転
        Map.entry("fell",            Set.of("aoe_radius")),
        Map.entry("phantom_block",   Set.of("amplify", "aoe", "extend_time", "duration_down")),            // dampen除外: ブール分岐(amp<=0)
        Map.entry("place_block",     Set.of("aoe")),
        Map.entry("launch",          Set.of("amplify", "dampen", "aoe_radius")),                            // dampen: 射出力減少
        Map.entry("leap",            Set.of("amplify", "dampen")),                                          // dampen: 跳躍力減少
        Map.entry("bounce",          Set.of("amplify", "aoe_radius", "extend_time", "duration_down", "linger", "propagate")),           // dampen除外: Math.max(0)
        Map.entry("snare",           Set.of("amplify", "aoe_radius", "extend_time", "duration_down", "linger", "propagate")),           // dampen除外: amplifyLevel未使用
        Map.entry("evaporate",       Set.of("aoe_radius")),
        Map.entry("dispel",          Set.of("amplify", "aoe_radius", "propagate")),                         // dampen除外: ブール分岐(amp>0)
        Map.entry("rune",            Set.of("extend_time", "duration_down", "linger")),
        Map.entry("summon_steed",    Set.of("amplify", "extend_time", "duration_down")),                    // dampen除外: Math.max(0)
        Map.entry("summon_wolves",   Set.of("amplify", "aoe_radius", "extend_time", "duration_down")),     // dampen除外: Math.max(0)
        Map.entry("wololo",          Set.of("amplify", "aoe_radius", "randomize")),                         // dampen除外: Math.max(0)
        Map.entry("bubble",          Set.of("aoe_radius", "extend_time", "duration_down")),
        Map.entry("prestidigitation", Set.of("randomize")),
        // === Effects - Tier 2 ===
        Map.entry("heal",            Set.of("amplify", "dampen", "aoe_radius", "linger", "propagate")),    // dampen: 回復量減少
        Map.entry("grow",            Set.of("amplify", "aoe_radius")),                                       // dampen除外: 成長量0は無意味
        Map.entry("explosion",       Set.of("aoe_radius", "extract")),
        Map.entry("exchange",        Set.of("amplify", "aoe")),                                             // dampen除外: Math.max(0)
        Map.entry("smelt",           Set.of("aoe_radius")),
        Map.entry("crush",           Set.of("aoe", "fortune")),
        Map.entry("crush_wave",      Set.of("amplify", "dampen", "aoe_radius", "linger", "propagate")),    // dampen: ダメージ減少
        Map.entry("scorch",          Set.of("amplify", "dampen", "aoe_radius", "linger", "propagate")),    // dampen: ダメージ減少
        Map.entry("cold_snap",       Set.of("amplify", "aoe_radius", "linger", "propagate")),               // dampen除外: Math.max(0)
        Map.entry("windshear",       Set.of("amplify", "dampen", "aoe_radius", "linger", "propagate")),    // dampen: ダメージ減少
        Map.entry("conjure_water",   Set.of("aoe", "extend_time", "duration_down")),
        Map.entry("slowfall",        Set.of("aoe_radius", "extend_time", "duration_down", "linger")),
        Map.entry("invisibility",    Set.of("extend_time", "duration_down")),
        Map.entry("infuse",          Set.of()),
        Map.entry("craft",           Set.of("aoe_radius")),
        Map.entry("animate",         Set.of("amplify", "aoe_radius", "extend_time", "duration_down")),     // dampen除外: Math.max(0)
        Map.entry("firework",        Set.of("amplify", "aoe_radius", "extend_time", "duration_down", "randomize")), // dampen除外: Math.max(0)
        Map.entry("name",            Set.of()),
        Map.entry("wind_burst",      Set.of("amplify", "dampen", "aoe_radius")),                            // dampen: 吹き飛ばし力減少
        Map.entry("speed_boost",     Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down", "randomize", "linger", "propagate")), // dampen: ブースト力減少（最低0.3）
        Map.entry("levitate",        Set.of("amplify", "aoe_radius", "extend_time", "duration_down", "linger", "propagate")),             // dampen除外: Math.max(0)
        // === Effects - Tier 3 ===
        Map.entry("blink",           Set.of("extend_reach")),
        Map.entry("lightning",       Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down", "linger", "propagate")),    // dampen: ダメージ減少（0以下で演出雷）
        Map.entry("wither",          Set.of("amplify", "aoe_radius", "extend_time", "duration_down", "linger", "propagate")),             // dampen除外: Math.max(0)
        Map.entry("hex",             Set.of("amplify", "aoe_radius", "extend_time", "duration_down", "linger", "propagate")),             // dampen除外: Math.max(0)
        Map.entry("glide",           Set.of("extend_time", "duration_down")),
        Map.entry("shield",          Set.of("amplify", "extend_time", "duration_down", "linger")),          // dampen除外: Math.max(0)
        Map.entry("summon_undead",   Set.of("amplify", "aoe_radius", "extend_time", "duration_down")),     // dampen除外: Math.max(0)
        Map.entry("summon_vex",      Set.of("amplify", "aoe_radius", "extend_time", "duration_down")),     // dampen除外: Math.max(0)
        Map.entry("summon_decoy",    Set.of("amplify", "extend_time", "duration_down")),                    // dampen除外: Math.max(0)
        Map.entry("intangible",      Set.of("aoe", "extend_time", "duration_down")),
        Map.entry("rewind",          Set.of("extend_time", "duration_down")),
        Map.entry("fangs",           Set.of("amplify", "aoe_radius")),                                      // dampen除外: Math.max(0)
        Map.entry("advanced_break",  Set.of("aoe", "extract", "fortune"))
    );

    public boolean isAugmentCompatible(String glyphKey, String augmentKey) {
        // 汎用Augmentは常に互換
        if (UNIVERSAL_AUGMENTS.contains(augmentKey)) return true;

        Set<String> compatible = AUGMENT_COMPAT.get(glyphKey);
        if (compatible == null) return false;

        // aoe_vertical(奥行き) と aoe_height(上下) は aoe(幅) と同じ互換性を持つ
        if ("aoe_vertical".equals(augmentKey) || "aoe_height".equals(augmentKey)) {
            return compatible.contains("aoe");
        }

        return compatible.contains(augmentKey);
    }

    /**
     * 指定Formに対して、指定Effectが互換性があるかチェック。
     * effectsリストが空（未定義）の場合は全Effect互換。
     */
    public boolean isEffectCompatibleWithForm(String formKey, String effectKey) {
        // 接触形態は投擲と互換性なし（近距離でアイテム射出は不適切）
        if ("touch".equals(formKey) && "toss".equals(effectKey)) return false;

        GlyphData data = glyphData.get(formKey);
        if (data == null) return true;
        List<String> compatible = data.compatibleEffects;
        // effectsリストが空 → 全Effect互換（制限なし）
        return compatible.isEmpty() || compatible.contains(effectKey);
    }

    /**
     * 指定Formの互換Effectリストを返す。空=全互換。
     */
    public List<String> getCompatibleEffects(String formKey) {
        GlyphData data = glyphData.get(formKey);
        return data != null ? data.compatibleEffects : List.of();
    }

    /**
     * 指定Effect/Formに対する指定Augmentの最大スタック数を返す。
     * 未設定の場合はInteger.MAX_VALUE（無制限）。
     */
    /** 1個までしか積めない増強（トグル系） */
    private static final Set<String> SINGLE_STACK_AUGMENTS = Set.of(
        "linger", "randomize", "extract", "trace"
    );

    public int getMaxAugmentStack(String glyphKey, String augmentKey) {
        // トグル系増強は常に1個まで
        if (SINGLE_STACK_AUGMENTS.contains(augmentKey)) return 1;

        // 連射: 照射は内蔵2個あるので追加最大2個、その他は最大4個
        if ("rapid_fire".equals(augmentKey)) {
            return "beam".equals(glyphKey) ? 2 : 4;
        }

        GlyphData data = glyphData.get(glyphKey);
        if (data == null || data.maxAugments == null) return Integer.MAX_VALUE;
        return data.maxAugments.getOrDefault(augmentKey, Integer.MAX_VALUE);
    }

    private record GlyphData(int tier, int manaCost, int unlockLevel, Map<Material, Integer> unlockMaterials,
                              List<String> compatibleEffects,
                              Map<String, Double> params, Map<String, Integer> maxAugments) {}

    // ============================================================
    // 交換グリフ ブロックティアマッピング
    // ============================================================
    private volatile List<List<Material>> exchangeTiers = List.of();

    public List<List<Material>> getExchangeTiers() {
        return exchangeTiers;
    }

    // ============================================================
    // 粉砕グリフ 変換マッピング
    // ============================================================
    private volatile Map<Material, Material> crushMap = Map.of();

    public Map<Material, Material> getCrushMap() {
        return crushMap;
    }

    private void loadExchangeTiers(YamlConfiguration config) {
        List<List<Material>> tiers = new ArrayList<>();
        List<?> rawTiers = config.getList("exchange_tiers");
        if (rawTiers != null) {
            for (Object tierObj : rawTiers) {
                if (tierObj instanceof List<?> tierList) {
                    List<Material> materials = new ArrayList<>();
                    for (Object matObj : tierList) {
                        Material mat = Material.matchMaterial(String.valueOf(matObj));
                        if (mat != null) materials.add(mat);
                    }
                    if (!materials.isEmpty()) tiers.add(List.copyOf(materials));
                }
            }
        }
        this.exchangeTiers = List.copyOf(tiers);
        logger.info("Loaded " + tiers.size() + " exchange tiers");
    }

    private void loadCrushMap(YamlConfiguration config) {
        Map<Material, Material> map = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("crush_map");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material from = Material.matchMaterial(key);
                Material to = Material.matchMaterial(section.getString(key, ""));
                if (from != null && to != null) {
                    map.put(from, to);
                }
            }
        }
        this.crushMap = Map.copyOf(map);
        logger.info("Loaded " + map.size() + " crush mappings");
    }
}
