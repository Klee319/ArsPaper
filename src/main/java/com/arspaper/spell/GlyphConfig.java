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
        return localizeMatName(mat);
    }

    private String localizeMatName(Material mat) {
        return switch (mat) {
            // 鉱石・宝石
            case DIAMOND -> "ダイヤモンド";
            case DIAMOND_BLOCK -> "ダイヤモンドブロック";
            case DIAMOND_PICKAXE -> "ダイヤモンドのツルハシ";
            case DIAMOND_AXE -> "ダイヤモンドの斧";
            case EMERALD -> "エメラルド";
            case GOLD_INGOT -> "金インゴット";
            case IRON_INGOT -> "鉄インゴット";
            case IRON_BLOCK -> "鉄ブロック";
            case IRON_PICKAXE -> "鉄のツルハシ";
            case IRON_SWORD -> "鉄の剣";
            case IRON_HOE -> "鉄のクワ";
            case IRON_BOOTS -> "鉄のブーツ";
            case IRON_CHESTPLATE -> "鉄のチェストプレート";
            case NETHERITE_INGOT -> "ネザライトインゴット";
            case NETHERITE_PICKAXE -> "ネザライトのツルハシ";
            case AMETHYST_SHARD -> "アメジストの欠片";
            case LAPIS_LAZULI -> "ラピスラズリ";
            case REDSTONE -> "レッドストーン";
            case REDSTONE_BLOCK -> "レッドストーンブロック";
            case QUARTZ -> "ネザークォーツ";
            case QUARTZ_BLOCK -> "クォーツブロック";
            case NETHER_STAR -> "ネザースター";
            case NETHER_BRICK -> "ネザーレンガ";
            // 素材
            case GLOWSTONE_DUST -> "グロウストーンダスト";
            case GUNPOWDER -> "火薬";
            case BLAZE_POWDER -> "ブレイズパウダー";
            case BLAZE_ROD -> "ブレイズロッド";
            case BREEZE_ROD -> "ブリーズロッド";
            case BONE_MEAL -> "骨粉";
            case BONE_BLOCK -> "骨ブロック";
            case SUGAR -> "砂糖";
            case FEATHER -> "羽";
            case STRING -> "糸";
            case SLIME_BALL -> "スライムボール";
            case ARROW -> "矢";
            case SPECTRAL_ARROW -> "光の矢";
            case LEATHER -> "革";
            case RABBIT_FOOT -> "ウサギの足";
            case PHANTOM_MEMBRANE -> "ファントムの皮膜";
            case ECHO_SHARD -> "エコーの欠片";
            case PRISMARINE_SHARD -> "プリズマリンの欠片";
            case HEART_OF_THE_SEA -> "海の心";
            case NAUTILUS_SHELL -> "オウムガイの殻";
            case SHULKER_SHELL -> "シュルカーの殻";
            case SCULK_SENSOR -> "スカルクセンサー";
            case MAGMA_CREAM -> "マグマクリーム";
            case GHAST_TEAR -> "ガストの涙";
            case SPIDER_EYE -> "クモの目";
            case FERMENTED_SPIDER_EYE -> "発酵したクモの目";
            case DRAGON_BREATH -> "ドラゴンブレス";
            // 食料・植物
            case GOLDEN_APPLE -> "金リンゴ";
            case GOLDEN_CARROT -> "金ニンジン";
            case GLISTERING_MELON_SLICE -> "きらめくスイカ";
            case WHEAT -> "小麦";
            case WHEAT_SEEDS -> "小麦の種";
            case CARROT -> "ニンジン";
            case OAK_LOG -> "オークの原木";
            // ブロック・道具
            case COBWEB -> "クモの巣";
            case PACKED_ICE -> "氷塊";
            case BLUE_ICE -> "青氷";
            case SNOWBALL -> "雪玉";
            case TNT -> "TNT";
            case TORCH -> "松明";
            case LANTERN -> "ランタン";
            case SOUL_LANTERN -> "ソウルランタン";
            case CLOCK -> "時計";
            case COMPASS -> "コンパス";
            case SAND -> "砂";
            case ANVIL -> "金床";
            case GRAVEL -> "砂利";
            case GLASS -> "ガラス";
            case GLASS_BOTTLE -> "ガラス瓶";
            case OBSIDIAN -> "黒曜石";
            case SOUL_SAND -> "ソウルサンド";
            case HONEY_BLOCK -> "蜂蜜ブロック";
            case LADDER -> "はしご";
            case END_CRYSTAL -> "エンドクリスタル";
            case ELYTRA -> "エリトラ";
            case TOTEM_OF_UNDYING -> "不死のトーテム";
            // 機能ブロック
            case PISTON -> "ピストン";
            case STICKY_PISTON -> "粘着ピストン";
            case HOPPER -> "ホッパー";
            case CHEST -> "チェスト";
            case FURNACE -> "かまど";
            case COAL_BLOCK -> "石炭ブロック";
            case CRAFTING_TABLE -> "作業台";
            case BREWING_STAND -> "醸造台";
            case DISPENSER -> "ディスペンサー";
            case DROPPER -> "ドロッパー";
            case LEVER -> "レバー";
            case LIGHTNING_ROD -> "避雷針";
            case BOOKSHELF -> "本棚";
            case BOOK -> "本";
            case NAME_TAG -> "名札";
            case SADDLE -> "鞍";
            case SHIELD -> "盾";
            case ARMOR_STAND -> "防具立て";
            case FISHING_ROD -> "釣り竿";
            case SHEARS -> "ハサミ";
            case FLINT_AND_STEEL -> "火打石と打ち金";
            case FLINT -> "火打石";
            case SPONGE -> "スポンジ";
            case WATER_BUCKET -> "水バケツ";
            case MILK_BUCKET -> "牛乳バケツ";
            // 操作系
            case OAK_BUTTON -> "オークのボタン";
            case OAK_PRESSURE_PLATE -> "オークの感圧板";
            case FIRE_CHARGE -> "ファイヤーチャージ";
            case FIREWORK_ROCKET -> "花火";
            case ENDER_EYE -> "エンダーアイ";
            case COBBLESTONE -> "丸石";
            case STONE -> "石";
            case LODESTONE -> "ロードストーン";
            // 染料
            case RED_DYE -> "赤色の染料";
            case BLUE_DYE -> "青色の染料";
            // モブドロップ
            case BONE -> "骨";
            case WITHER_SKELETON_SKULL -> "ウィザースケルトンの頭蓋骨";
            case WITHER_ROSE -> "ウィザーローズ";
            case CHORUS_FRUIT -> "コーラスフルーツ";
            case PUFFERFISH -> "フグ";
            case SUNFLOWER -> "ヒマワリ";
            case MYCELIUM -> "菌糸ブロック";
            case NETHER_WART -> "ネザーウォート";
            case NETHERRACK -> "ネザーラック";
            case MAGMA_BLOCK -> "マグマブロック";
            case CRYING_OBSIDIAN -> "泣く黒曜石";
            case AMETHYST_BLOCK -> "アメジストブロック";
            case BARREL -> "樽";
            case SMOKER -> "燻製器";
            case IRON_BARS -> "鉄格子";
            case IRON_AXE -> "鉄の斧";
            case IRON_SHOVEL -> "鉄のシャベル";
            case STICK -> "棒";
            case BRICK -> "レンガ";
            case LEATHER_HELMET -> "革のヘルメット";
            case LEATHER_CHESTPLATE -> "革のチェストプレート";
            case LEATHER_LEGGINGS -> "革のレギンス";
            case LEATHER_BOOTS -> "革のブーツ";
            case ENDER_PEARL -> "エンダーパール";
            case LAPIS_BLOCK -> "ラピスラズリブロック";
            // LAPIS_LAZULI_BLOCKはLAPIS_BLOCKで統合
            case EMERALD_BLOCK -> "エメラルドブロック";
            case GOLD_BLOCK -> "金ブロック";
            case NETHERITE_BLOCK -> "ネザライトブロック";
            case ENDER_CHEST -> "エンダーチェスト";
            case BEACON -> "ビーコン";
            case SEA_LANTERN -> "シーランタン";
            case CONDUIT -> "コンジット";
            case ICE -> "氷";
            case SNOW_BLOCK -> "雪ブロック";
            case SLIME_BLOCK -> "スライムブロック";
            case GOLDEN_PICKAXE -> "金のツルハシ";
            case GOLDEN_AXE -> "金の斧";
            case GOLDEN_SWORD -> "金の剣";
            case GOLDEN_HOE -> "金のクワ";
            case LIGHT_WEIGHTED_PRESSURE_PLATE -> "金の感圧板";
            case STONE_PICKAXE -> "石のツルハシ";
            case STONE_AXE -> "石の斧";
            case STONE_SWORD -> "石の剣";
            case STONE_HOE -> "石のクワ";
            case STONE_BUTTON -> "石のボタン";
            case STONE_PRESSURE_PLATE -> "石の感圧板";
            case HEAVY_WEIGHTED_PRESSURE_PLATE -> "鉄の感圧板";
            case POWDER_SNOW_BUCKET -> "粉雪バケツ";
            case WIND_CHARGE -> "風の玉";
            case CROSSBOW -> "クロスボウ";
            case OAK_LEAVES -> "オークの葉";
            case WHITE_WOOL -> "白色の羊毛";
            case WHITE_DYE -> "白色の染料";
            case YELLOW_DYE -> "黄色の染料";
            case GREEN_DYE -> "緑色の染料";
            case ROTTEN_FLESH -> "腐った肉";
            case NETHERITE_SWORD -> "ネザライトの剣";
            case NETHERITE_CHESTPLATE -> "ネザライトのチェストプレート";
            case RECOVERY_COMPASS -> "リカバリーコンパス";
            case NETHER_BRICKS -> "ネザーレンガ";
            case CRACKED_STONE_BRICKS -> "ひび割れた石レンガ";
            case DIORITE_WALL -> "閃緑岩の壁";
            case GRANITE_WALL -> "花崗岩の壁";
            case ANDESITE_WALL -> "安山岩の壁";
            case JUKEBOX -> "ジュークボックス";
            case NOTE_BLOCK -> "音符ブロック";
            case TARGET -> "ターゲットブロック";
            case GRASS_BLOCK -> "草ブロック";
            case MOSS_BLOCK -> "苔ブロック";
            case PODZOL -> "ポドゾル";
            case BLAST_FURNACE -> "溶鉱炉";
            case REPEATER -> "リピーター";
            case COMPARATOR -> "コンパレーター";
            case CARVED_PUMPKIN -> "くり抜かれたカボチャ";
            case PAPER -> "紙";
            case BUCKET -> "バケツ";
            case OMINOUS_BOTTLE -> "不吉な瓶";
            case ENCHANTED_GOLDEN_APPLE -> "エンチャントされた金リンゴ";
            default -> {
                // Material名からヒューリスティックに日本語化
                String name = mat.name().toLowerCase().replace('_', ' ');
                yield name.substring(0, 1).toUpperCase() + name.substring(1);
            }
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
        "delay"
    );

    /**
     * Augment互換性マップ（ソースコード定義）。
     * glyphs.ymlのaugmentsフィールドは参照しない（プログラムの仕様で決まるため）。
     * configで変更しても効果のコードが対応しない増強は無意味なのでハードコード。
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
        Map.entry("break",           Set.of("amplify", "dampen", "aoe", "extract", "fortune")),
        Map.entry("harm",            Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down", "linger")),
        Map.entry("ignite",          Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down", "linger")),
        Map.entry("freeze",          Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down", "linger")),
        Map.entry("knockback",       Set.of("amplify", "dampen", "aoe_radius", "linger")),
        Map.entry("pull",            Set.of("amplify", "dampen", "aoe_radius", "linger")),
        Map.entry("gravity",         Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down", "linger")),
        Map.entry("light",           Set.of("aoe", "extend_time", "duration_down")),
        Map.entry("harvest",         Set.of("amplify", "dampen", "aoe_radius", "fortune", "extract")),
        Map.entry("cut",             Set.of("aoe")),
        Map.entry("interact",        Set.of("aoe", "extend_time", "duration_down")),
        Map.entry("pickup",          Set.of("aoe_radius")),
        Map.entry("rotate",          Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down")),
        Map.entry("fell",            Set.of("aoe_radius")),
        Map.entry("phantom_block",   Set.of("amplify", "dampen", "aoe", "extend_time", "duration_down")),
        Map.entry("place_block",     Set.of("aoe")),
        // toss は廃止
        Map.entry("launch",          Set.of("amplify", "dampen", "aoe_radius")),
        Map.entry("leap",            Set.of("amplify", "dampen")),
        Map.entry("bounce",          Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down", "linger")),
        Map.entry("snare",           Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down", "linger")),
        Map.entry("evaporate",       Set.of("aoe_radius")),
        Map.entry("dispel",          Set.of("amplify", "dampen", "aoe_radius")),
        Map.entry("rune",            Set.of("extend_time", "duration_down", "linger")),
        Map.entry("summon_steed",    Set.of("amplify", "dampen", "extend_time", "duration_down")),
        Map.entry("summon_wolves",   Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down")),
        Map.entry("wololo",          Set.of("amplify", "dampen", "aoe_radius", "randomize")),
        Map.entry("bubble",          Set.of("aoe_radius", "extend_time", "duration_down")),
        Map.entry("prestidigitation", Set.of("randomize")),
        // === Effects - Tier 2 ===
        Map.entry("heal",            Set.of("amplify", "dampen", "aoe_radius", "linger")),
        Map.entry("grow",            Set.of("amplify", "dampen", "aoe_radius")),
        Map.entry("explosion",       Set.of("aoe_radius", "extract")),
        Map.entry("exchange",        Set.of("amplify", "dampen", "aoe")),
        Map.entry("smelt",           Set.of("aoe_radius")),
        Map.entry("crush",           Set.of("aoe", "fortune")),
        Map.entry("crush_wave",      Set.of("amplify", "dampen", "aoe_radius", "linger")),
        Map.entry("scorch",          Set.of("amplify", "dampen", "aoe_radius", "linger")),
        Map.entry("cold_snap",       Set.of("amplify", "dampen", "aoe_radius", "linger")),
        Map.entry("windshear",       Set.of("amplify", "dampen", "aoe_radius", "linger")),
        Map.entry("conjure_water",   Set.of("aoe", "extend_time", "duration_down")),
        Map.entry("slowfall",        Set.of("aoe_radius", "extend_time", "duration_down", "linger")),
        Map.entry("invisibility",    Set.of("extend_time", "duration_down")),
        Map.entry("infuse",          Set.of()),
        Map.entry("craft",           Set.of("aoe_radius")),
        Map.entry("animate",         Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down")),
        Map.entry("firework",        Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down", "randomize")),
        Map.entry("name",            Set.of()),
        Map.entry("wind_burst",      Set.of("amplify", "dampen", "aoe_radius")),
        Map.entry("speed_boost",     Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down", "randomize", "linger")),
        Map.entry("levitate",        Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down", "linger")),
        // === Effects - Tier 3 ===
        Map.entry("blink",           Set.of("extend_reach")),
        Map.entry("lightning",       Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down", "linger")),
        Map.entry("wither",          Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down", "linger")),
        Map.entry("hex",             Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down", "linger")),
        Map.entry("glide",           Set.of("extend_time", "duration_down")),
        Map.entry("shield",          Set.of("amplify", "dampen", "extend_time", "duration_down", "linger")),
        Map.entry("summon_undead",   Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down")),
        Map.entry("summon_vex",      Set.of("amplify", "dampen", "aoe_radius", "extend_time", "duration_down")),
        Map.entry("summon_decoy",    Set.of("amplify", "dampen", "extend_time", "duration_down")),
        Map.entry("intangible",      Set.of("aoe", "extend_time", "duration_down")),
        Map.entry("rewind",          Set.of("extend_time", "duration_down")),
        Map.entry("fangs",           Set.of("amplify", "dampen", "aoe_radius")),
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
