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

    private String localizeMatName(Material mat) {
        return switch (mat) {
            case LAPIS_LAZULI -> "ラピスラズリ";
            case REDSTONE -> "レッドストーン";
            case GOLDEN_APPLE -> "金リンゴ";
            case BONE_MEAL -> "骨粉";
            case GLOWSTONE_DUST -> "グロウストーンダスト";
            case SUGAR -> "砂糖";
            case SLIME_BALL -> "スライムボール";
            case BLAZE_POWDER -> "ブレイズパウダー";
            case COBWEB -> "クモの巣";
            case ENDER_PEARL -> "エンダーパール";
            case PACKED_ICE -> "氷塊";
            case DIAMOND -> "ダイヤモンド";
            case NETHER_STAR -> "ネザースター";
            case TNT -> "TNT";
            case SHULKER_SHELL -> "シュルカーの殻";
            case FIREWORK_ROCKET -> "花火";
            case WITHER_SKELETON_SKULL -> "ウィザースケルトンの頭蓋骨";
            case TORCH -> "松明";
            case FEATHER -> "羽";
            case GUNPOWDER -> "火薬";
            case ARROW -> "矢";
            case PRISMARINE_SHARD -> "プリズマリンの欠片";
            default -> mat.name();
        };
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
        "delay", "reset"
    );

    /**
     * Augment互換性マップ（ソースコード定義）。
     * glyphs.ymlのaugmentsフィールドは参照しない（プログラムの仕様で決まるため）。
     * configで変更しても効果のコードが対応しない増強は無意味なのでハードコード。
     */
    private static final Map<String, Set<String>> AUGMENT_COMPAT = Map.ofEntries(
        // === Forms ===
        Map.entry("projectile", Set.of("accelerate", "decelerate", "pierce", "split")),
        Map.entry("touch",      Set.of()),
        Map.entry("self",       Set.of()),
        Map.entry("underfoot",  Set.of()),
        Map.entry("orbit",      Set.of("amplify", "dampen", "split", "extend_time", "duration_down")),
        Map.entry("wall",       Set.of("accelerate", "decelerate", "split")),
        Map.entry("beam",       Set.of("accelerate", "decelerate", "pierce", "split", "aoe", "trail")),
        // === Effects - Tier 1 ===
        Map.entry("break",           Set.of("amplify", "dampen", "aoe", "extract", "fortune")),
        Map.entry("harm",            Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down")),
        Map.entry("ignite",          Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down")),
        Map.entry("freeze",          Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down")),
        Map.entry("knockback",       Set.of("amplify", "dampen", "aoe")),
        Map.entry("pull",            Set.of("amplify", "dampen", "aoe")),
        Map.entry("gravity",         Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down")),
        Map.entry("light",           Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down", "wall")),
        Map.entry("harvest",         Set.of("amplify", "dampen", "aoe", "fortune", "extract")),
        Map.entry("cut",             Set.of("amplify", "dampen", "aoe", "fortune", "extract")),
        Map.entry("interact",        Set.of("aoe", "extend_time", "duration_down")),
        Map.entry("pickup",          Set.of("aoe_radius")),
        Map.entry("rotate",          Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down")),
        Map.entry("fell",            Set.of("aoe_radius", "fortune", "extract")),
        // redstone_signal は削除済み
        Map.entry("phantom_block",   Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down", "wall")),
        Map.entry("place_block",     Set.of("aoe", "wall")),
        Map.entry("toss",            Set.of("amplify", "dampen", "aoe")),
        Map.entry("launch",          Set.of("amplify", "dampen")),
        Map.entry("leap",            Set.of("amplify", "dampen")),
        Map.entry("bounce",          Set.of("amplify", "dampen", "extend_time", "duration_down")),
        Map.entry("snare",           Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down")),
        Map.entry("evaporate",       Set.of("aoe")),
        Map.entry("dispel",          Set.of("amplify", "dampen", "aoe")),
        Map.entry("reset",           Set.of()),
        Map.entry("rune",            Set.of("extend_time", "duration_down", "linger", "wall")),
        Map.entry("summon_steed",    Set.of("amplify", "dampen", "extend_time", "duration_down")),
        Map.entry("summon_wolves",   Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down")),
        Map.entry("wololo",          Set.of("amplify", "dampen", "aoe", "randomize")),
        Map.entry("bubble",          Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down")),
        Map.entry("prestidigitation", Set.of("randomize")),
        // === Effects - Tier 2 ===
        Map.entry("heal",            Set.of("amplify", "dampen", "aoe")),
        Map.entry("grow",            Set.of("amplify", "dampen", "aoe")),
        Map.entry("explosion",       Set.of("amplify", "dampen", "aoe_radius", "extract")),
        Map.entry("exchange",        Set.of("amplify", "dampen", "aoe")),
        Map.entry("smelt",           Set.of("aoe")),
        Map.entry("crush",           Set.of("amplify", "dampen", "aoe", "fortune")),
        Map.entry("cold_snap",       Set.of("amplify", "dampen", "aoe")),
        Map.entry("flare",           Set.of("amplify", "dampen", "aoe")),
        Map.entry("windshear",       Set.of("amplify", "dampen", "aoe")),
        Map.entry("conjure_water",   Set.of("aoe", "extend_time", "duration_down", "wall")),
        Map.entry("slowfall",        Set.of("aoe", "extend_time", "duration_down")),
        Map.entry("invisibility",    Set.of("extend_time", "duration_down")),
        Map.entry("infuse",          Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down")),
        Map.entry("craft",           Set.of("aoe")),
        Map.entry("animate",         Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down")),
        Map.entry("firework",        Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down", "randomize")),
        Map.entry("name",            Set.of()),
        Map.entry("wind_burst",      Set.of("amplify", "dampen", "aoe_radius")),
        Map.entry("speed_boost",     Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down", "randomize")),
        Map.entry("levitate",        Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down")),
        // === Effects - Tier 3 ===
        Map.entry("blink",           Set.of("amplify", "dampen")),
        Map.entry("lightning",       Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down")),
        Map.entry("wither",          Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down")),
        Map.entry("hex",             Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down")),
        Map.entry("glide",           Set.of("amplify", "dampen", "extend_time", "duration_down")),
        Map.entry("shield",          Set.of("amplify", "dampen", "extend_time", "duration_down")),
        Map.entry("summon_undead",   Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down")),
        Map.entry("summon_vex",      Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down")),
        Map.entry("summon_decoy",    Set.of("amplify", "dampen", "extend_time", "duration_down")),
        Map.entry("intangible",      Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down")),
        Map.entry("rewind",          Set.of("extend_time", "duration_down")),
        Map.entry("fangs",           Set.of("amplify", "dampen", "aoe_radius")),
        Map.entry("advanced_break",  Set.of("aoe", "extract"))
    );

    public boolean isAugmentCompatible(String glyphKey, String augmentKey) {
        // 汎用Augmentは常に互換
        if (UNIVERSAL_AUGMENTS.contains(augmentKey)) return true;

        Set<String> compatible = AUGMENT_COMPAT.get(glyphKey);
        if (compatible == null) return false;

        // aoe_vertical は aoe と同じ互換性を持つ
        if ("aoe_vertical".equals(augmentKey)) {
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
        "wall", "linger", "trail", "randomize", "extract"
    );

    public int getMaxAugmentStack(String glyphKey, String augmentKey) {
        // トグル系増強は常に1個まで
        if (SINGLE_STACK_AUGMENTS.contains(augmentKey)) return 1;

        GlyphData data = glyphData.get(glyphKey);
        if (data == null || data.maxAugments == null) return Integer.MAX_VALUE;
        return data.maxAugments.getOrDefault(augmentKey, Integer.MAX_VALUE);
    }

    private record GlyphData(int tier, int manaCost, int unlockLevel, Map<Material, Integer> unlockMaterials,
                              List<String> compatibleEffects,
                              Map<String, Double> params, Map<String, Integer> maxAugments) {}
}
