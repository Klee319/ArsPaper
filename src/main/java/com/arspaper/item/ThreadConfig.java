package com.arspaper.item;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * threads.ymlからスレッド効果量・重複設定・最大積載量を読み込む。
 * ThreadTypeのデフォルト値を上書きする。
 */
public class ThreadConfig {

    /**
     * threads.yml の {@code potion-effect:} に指定できる有益効果18種の許可リスト（id小文字 →
     * バニラ PotionEffectType）。有害効果・即時系(instant_health等、毎tick付け直すと無限回復になる)は
     * 意図的に含まない。ここに無い id が書かれた場合は {@link #load()} が警告ログを出し
     * 既定値(ThreadType enum の値)へフォールバックする(起動は止めない)。
     */
    public static final Map<String, PotionEffectType> ALLOWED_POTION_EFFECTS = buildAllowedPotionEffects();

    private static Map<String, PotionEffectType> buildAllowedPotionEffects() {
        Map<String, PotionEffectType> map = new LinkedHashMap<>();
        map.put("speed", PotionEffectType.SPEED);
        map.put("haste", PotionEffectType.HASTE);
        map.put("strength", PotionEffectType.STRENGTH);
        map.put("jump_boost", PotionEffectType.JUMP_BOOST);
        map.put("regeneration", PotionEffectType.REGENERATION);
        map.put("resistance", PotionEffectType.RESISTANCE);
        map.put("fire_resistance", PotionEffectType.FIRE_RESISTANCE);
        map.put("water_breathing", PotionEffectType.WATER_BREATHING);
        map.put("invisibility", PotionEffectType.INVISIBILITY);
        map.put("night_vision", PotionEffectType.NIGHT_VISION);
        map.put("health_boost", PotionEffectType.HEALTH_BOOST);
        map.put("absorption", PotionEffectType.ABSORPTION);
        map.put("saturation", PotionEffectType.SATURATION);
        map.put("luck", PotionEffectType.LUCK);
        map.put("slow_falling", PotionEffectType.SLOW_FALLING);
        map.put("conduit_power", PotionEffectType.CONDUIT_POWER);
        map.put("dolphins_grace", PotionEffectType.DOLPHINS_GRACE);
        map.put("hero_of_the_village", PotionEffectType.HERO_OF_THE_VILLAGE);
        return Collections.unmodifiableMap(map);
    }

    /** バニラの日本語名(実機の /effect 表示に合わせる)。lore組み立て({@link #getEffectLore}）専用。 */
    private static final Map<String, String> POTION_JA_NAMES = buildPotionJaNames();

    private static Map<String, String> buildPotionJaNames() {
        Map<String, String> map = new HashMap<>();
        map.put("speed", "移動速度上昇");
        map.put("haste", "採掘速度上昇");
        map.put("strength", "攻撃力上昇");
        map.put("jump_boost", "跳躍力上昇");
        map.put("regeneration", "再生能力");
        map.put("resistance", "耐性");
        map.put("fire_resistance", "火炎耐性");
        map.put("water_breathing", "水中呼吸");
        map.put("invisibility", "透明化");
        map.put("night_vision", "暗視");
        map.put("health_boost", "体力増強");
        map.put("absorption", "衝撃吸収");
        map.put("saturation", "満腹度回復");
        map.put("luck", "幸運");
        map.put("slow_falling", "落下速度低下");
        map.put("conduit_power", "コンジットパワー");
        map.put("dolphins_grace", "イルカの好意");
        map.put("hero_of_the_village", "村の英雄");
        return Collections.unmodifiableMap(map);
    }

    private static final String[] ROMAN_NUMERALS =
        {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private static String romanNumeral(int level) {
        if (level >= 1 && level < ROMAN_NUMERALS.length) {
            return ROMAN_NUMERALS[level];
        }
        return String.valueOf(level);
    }

    private final JavaPlugin plugin;
    private final Map<String, Boolean> stackable = new HashMap<>();
    private final Map<String, Integer> maxStack = new HashMap<>();
    private final Map<String, Integer> regenBonus = new HashMap<>();
    private final Map<String, Integer> manaBonus = new HashMap<>();
    private final Map<String, Integer> hitRecovery = new HashMap<>();
    private final Map<String, Integer> damageRecovery = new HashMap<>();
    private final Map<String, Integer> costReduction = new HashMap<>();
    private final Map<String, Integer> manaMaxPercent = new HashMap<>();
    private final Map<String, Integer> regenPercent = new HashMap<>();
    private final Map<String, Integer> backpackSlots = new HashMap<>();
    /** threads.yml の {@code potion-effect:}(明示指定)。"none" はここに入れず {@link #potionNone} へ。 */
    private final Map<String, PotionEffectType> potionEffectOverride = new HashMap<>();
    /** threads.yml で {@code potion-effect: none} と明示されたスレッド(効果なしの明示)。 */
    private final Map<String, Boolean> potionNone = new HashMap<>();
    /** threads.yml の {@code potion-level:}(1以上、未設定時は呼び出し側で1にフォールバック)。 */
    private final Map<String, Integer> potionLevel = new HashMap<>();
    /** threads.yml の {@code flight:}(明示指定)。未設定なら ThreadType enum の既定値を使う。 */
    private final Map<String, Boolean> flightOverride = new HashMap<>();
    /**
     * threads.yml の {@code lore:}(汎用説明行)。ThreadConfig が数値として解釈するキーを1つも
     * 持たないスレッド ── 効果の実体が thread-sets.yml のセット効果側にあるスレッド ── は、
     * ここを書かないと【説明文が1行も出ない】。種類ごとに Java の switch を足す代わりに、
     * yml の行をそのまま lore へ流す汎用経路を1本だけ用意する(2026-08-02 スレッド16→40種)。
     */
    private final Map<String, List<String>> extraLore = new HashMap<>();

    public ThreadConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        stackable.clear();
        maxStack.clear();
        regenBonus.clear();
        manaBonus.clear();
        hitRecovery.clear();
        damageRecovery.clear();
        costReduction.clear();
        manaMaxPercent.clear();
        regenPercent.clear();
        backpackSlots.clear();
        potionEffectOverride.clear();
        potionNone.clear();
        potionLevel.clear();
        flightOverride.clear();
        extraLore.clear();
        load();
    }

    /** 後方互換: FileConfigurationを受け取るreload（何もしない、reload()を使用） */
    public void reload(org.bukkit.configuration.file.FileConfiguration ignored) {
        reload();
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), "threads.yml");
        if (!file.exists()) {
            plugin.saveResource("threads.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection threads = config.getConfigurationSection("threads");
        if (threads == null) return;

        for (String key : threads.getKeys(false)) {
            ConfigurationSection section = threads.getConfigurationSection(key);
            if (section == null) continue;

            // stackable設定（未記載 = false: 重複不可）
            stackable.put(key, section.getBoolean("stackable", false));

            // max設定（stackable: trueの場合のみ有効、未設定 = 無制限）
            if (section.contains("max")) {
                maxStack.put(key, section.getInt("max"));
            }

            if (section.contains("regen-bonus")) {
                regenBonus.put(key, section.getInt("regen-bonus"));
            }
            if (section.contains("mana-bonus")) {
                manaBonus.put(key, section.getInt("mana-bonus"));
            }
            if (section.contains("recovery")) {
                if (key.contains("hit")) {
                    hitRecovery.put(key, section.getInt("recovery"));
                } else {
                    damageRecovery.put(key, section.getInt("recovery"));
                }
            }
            if (section.contains("cost-reduction")) {
                costReduction.put(key, section.getInt("cost-reduction"));
            }
            if (section.contains("mana-max-percent")) {
                manaMaxPercent.put(key, section.getInt("mana-max-percent"));
            }
            if (section.contains("regen-percent")) {
                regenPercent.put(key, section.getInt("regen-percent"));
            }
            if (section.contains("slots")) {
                backpackSlots.put(key, section.getInt("slots"));
            }
            // potion-effect: バニラ PotionEffectType 名を小文字にしたid。"none" は明示的な無効化、
            // 未知/有害/即時系のidは警告ログを出してenum既定値へフォールバックする(起動は止めない)。
            if (section.contains("potion-effect")) {
                String rawId = section.getString("potion-effect");
                if (rawId != null && !rawId.isBlank()) {
                    String normalized = rawId.trim().toLowerCase(Locale.ROOT);
                    if ("none".equals(normalized)) {
                        potionNone.put(key, true);
                    } else {
                        PotionEffectType resolved = ALLOWED_POTION_EFFECTS.get(normalized);
                        if (resolved != null) {
                            potionEffectOverride.put(key, resolved);
                        } else {
                            plugin.getLogger().warning(
                                "threads.yml: スレッド '" + key + "' の potion-effect '" + rawId
                                    + "' は許可された有益効果18種に無いため、既定値へフォールバックします。");
                        }
                    }
                }
            }
            // potion-level: 1以上の整数。1未満は1に丸める。省略時は呼び出し側で1を返す。
            if (section.contains("potion-level")) {
                potionLevel.put(key, Math.max(1, section.getInt("potion-level")));
            }
            // flight: 明示指定があれば ThreadType enum の isFlightThread() を上書きする。
            if (section.contains("flight")) {
                flightOverride.put(key, section.getBoolean("flight"));
            }
            // lore: は「文字列のリスト」でも「1行の文字列」でも書ける(1行しか要らない側で
            // わざわざ - を書かせないため)。空行だけの記述は捨てる。
            if (section.contains("lore")) {
                List<String> lines = section.getStringList("lore");
                if (lines.isEmpty()) {
                    String single = section.getString("lore");
                    if (single != null && !single.isBlank()) {
                        lines = List.of(single);
                    }
                }
                lines = lines.stream().filter(line -> line != null && !line.isBlank()).toList();
                if (!lines.isEmpty()) {
                    extraLore.put(key, lines);
                }
            }
        }
    }

    /** 同じ防具にスタック可能かどうか */
    public boolean isStackable(String threadId) {
        return stackable.getOrDefault(threadId, false);
    }

    /** 1つの防具にセットできる最大数（未設定 = Integer.MAX_VALUE） */
    public int getMaxStack(String threadId) {
        return maxStack.getOrDefault(threadId, Integer.MAX_VALUE);
    }

    /** マナリジェンボーナス */
    public int getRegenBonus(ThreadType type) {
        return regenBonus.getOrDefault(type.getId(), type.getRegenBonus());
    }

    /** マナボーナス */
    public int getManaBonus(ThreadType type) {
        return manaBonus.getOrDefault(type.getId(), type.getManaBonus());
    }

    /** 被弾マナ回復 */
    public int getHitManaRecovery(ThreadType type) {
        return hitRecovery.getOrDefault(type.getId(), type.getHitManaRecovery());
    }

    /** 攻撃マナ回復 */
    public int getDamageManaRecovery(ThreadType type) {
        return damageRecovery.getOrDefault(type.getId(), type.getDamageManaRecovery());
    }

    /** マナコスト削減% */
    public int getCostReduction(ThreadType type) {
        return costReduction.getOrDefault(type.getId(), type.getCostReductionPercent());
    }

    /** マナ最大値%上昇（threads.yml mana-max-percent, 未設定=0） */
    public int getManaMaxPercent(ThreadType type) {
        return manaMaxPercent.getOrDefault(type.getId(), 0);
    }

    /** マナ回復速度%上昇（threads.yml regen-percent, 未設定=0） */
    public int getRegenPercent(ThreadType type) {
        return regenPercent.getOrDefault(type.getId(), 0);
    }

    /** バックパックスロット数 */
    public int getBackpackSlots(ThreadType type) {
        return backpackSlots.getOrDefault(type.getId(), 27);
    }

    /**
     * このスレッドが装備中常時付与するポーション効果の型。
     * config({@code potion-effect:})優先、config未記載なら {@link ThreadType} enum の既定値、
     * {@code potion-effect: none} が明示されていれば {@code null}(効果なし)を返す。
     */
    public PotionEffectType getPotionEffect(ThreadType type) {
        String key = type.getId();
        if (potionNone.getOrDefault(key, false)) {
            return null;
        }
        PotionEffectType override = potionEffectOverride.get(key);
        if (override != null) {
            return override;
        }
        return type.getPotionEffect();
    }

    /**
     * このスレッドのポーション効果レベル(1以上)。内部amplifierは {@code level - 1}。
     * threads.yml の {@code potion-level:} が無ければ 1(効果I)。
     */
    public int getPotionLevel(ThreadType type) {
        Integer level = potionLevel.get(type.getId());
        if (level == null) {
            return 1;
        }
        return Math.max(1, level);
    }

    /**
     * このスレッドが装備中常時エリトラ飛行を許可するか。
     * threads.yml の {@code flight:} が明示されていればそれを使い、無ければ
     * {@link ThreadType#isFlightThread()}(=enumで FLIGHT 一択)を使う。
     */
    public boolean isFlightThread(ThreadType type) {
        Boolean override = flightOverride.get(type.getId());
        if (override != null) {
            return override;
        }
        return type.isFlightThread();
    }

    /**
     * {@link ArmorManaListener#updatePotionEffects} が走査すべき「付与されうる全ポーション型」。
     * ThreadType enum の既定値(全40+種)と、threads.yml の {@code potion-effect:} で参照されている
     * 型の和集合を返す。config で新しい型を選べる以上、この集合は動的に決まる必要がある
     * ── 固定配列(旧 THREAD_POTION_TYPES)のままだと config で新しい型を指定しても
     * 【付けても効かず、外しても剥がれない】(どちらも無言)。
     */
    public Set<PotionEffectType> allPotionTypes() {
        Set<PotionEffectType> types = new LinkedHashSet<>();
        for (ThreadType candidate : ThreadType.values()) {
            PotionEffectType enumDefault = candidate.getPotionEffect();
            if (enumDefault != null) {
                types.add(enumDefault);
            }
        }
        types.addAll(potionEffectOverride.values());
        return Collections.unmodifiableSet(types);
    }

    /** threads.yml の {@code lore:}(未記載なら空リスト)。 */
    public List<String> getExtraLore(ThreadType type) {
        return extraLore.getOrDefault(type.getId(), List.of());
    }

    /**
     * ThreadConfigの値を反映したloreを生成する。
     * ThreadType.getEffectLore()はEnum定数値を使うため、YAMLオーバーライドが反映されない。
     */
    public java.util.List<net.kyori.adventure.text.Component> getEffectLore(ThreadType type) {
        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();

        int regen = getRegenBonus(type);
        if (regen > 0) {
            lore.add(loreText("マナ回復速度 +" + regen + "/tick"));
        }
        int mana = getManaBonus(type);
        if (mana > 0) {
            lore.add(loreText("マナ最大値 +" + mana));
        }
        // mana-max-percent / regen-percent は ThreadConfig も ManaManager も配線済みなのに
        // lore を1行も出していなかった(＝出荷 threads.yml に1件も無かったので露見しなかった)。
        // 割合版スレッドを入れる以上、ここを書かないと「効いているのに説明が無い」ままになる。
        int manaPercent = getManaMaxPercent(type);
        if (manaPercent > 0) {
            lore.add(loreText("マナ最大値 +" + manaPercent + "%"));
        }
        int regenPct = getRegenPercent(type);
        if (regenPct > 0) {
            lore.add(loreText("マナ回復速度 +" + regenPct + "%"));
        }
        // config優先(getPotionEffect)で解決する: potion-effect: none 明示ならここは何も出さず、
        // potion-effect: <id> 明示ならその型で、どちらも無ければ ThreadType enum の既定値で出す。
        // レベル2以上はローマ数字を添える(例: 移動速度上昇 II (装備中常時))。
        PotionEffectType resolvedPotion = getPotionEffect(type);
        if (resolvedPotion != null) {
            String effectName = POTION_JA_NAMES.getOrDefault(
                resolvedPotion.getKey().getKey(), "ポーション効果");
            int level = getPotionLevel(type);
            String levelSuffix = level >= 2 ? " " + romanNumeral(level) : "";
            lore.add(loreText(effectName + levelSuffix + " (装備中常時)"));
        }
        int hit = getHitManaRecovery(type);
        if (hit > 0) {
            lore.add(loreText("被弾時マナ回復 +" + hit));
        }
        int dmg = getDamageManaRecovery(type);
        if (dmg > 0) {
            lore.add(loreText("攻撃時マナ回復 +" + dmg));
        }
        int cost = getCostReduction(type);
        if (cost > 0) {
            lore.add(loreText("マナコスト -" + cost + "%"));
        }
        if (isFlightThread(type)) {
            lore.add(loreText("エリトラ飛行 (装備中常時)"));
        }
        if (type.isBackpackThread()) {
            int slots = getBackpackSlots(type);
            lore.add(loreText("追加インベントリ " + slots + "スロット"));
        }
        // 汎用フォールバック: 効果の実体が thread-sets.yml 側にあるスレッドは上のどの分岐にも
        // 引っかからないので、ここで threads.yml の lore: をそのまま出す。これが無いと
        // 「説明文が1行も無いスレッド」になり、種類を増やすたびに Java の switch を足す羽目になる。
        for (String line : getExtraLore(type)) {
            lore.add(loreText(line));
        }
        return lore;
    }

    /**
     * スレッドの効果説明1行。
     *
     * <p><b>色は灰色で固定する(2026-08-04 依頼#46)</b>: 以前は効果の種類ごとに
     * AQUA/BLUE/GREEN/GOLD/DARK_RED/YELLOW を振っていたため、1本のスレッドの lore の中で色が
     * バラバラに並び、しかも TF 装備の lore(灰色テンプレート {@code stats/lore.yml}
     * {@code layout.line-template})とも体裁が揃っていなかった。説明文は TF 側と同じ灰色に寄せ、
     * <b>色で意味を伝えるのはスレッド名(種別色)と数値(TF 側の正負色)だけ</b>にする。
     */
    private static net.kyori.adventure.text.Component loreText(String text) {
        return net.kyori.adventure.text.Component.text(
                text, net.kyori.adventure.text.format.NamedTextColor.GRAY)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }
}
