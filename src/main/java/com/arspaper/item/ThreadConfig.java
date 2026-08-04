package com.arspaper.item;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * threads.ymlからスレッド効果量・重複設定・最大積載量を読み込む。
 * ThreadTypeのデフォルト値を上書きする。
 */
public class ThreadConfig {

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
        if (type.hasPotionEffect()) {
            String effectName = switch (type.getId()) {
                case "speed" -> "移動速度上昇";
                case "jump_boost" -> "跳躍力上昇";
                case "night_vision" -> "暗視";
                case "fire_resistance" -> "火炎耐性";
                case "dolphins_grace" -> "イルカの好意";
                case "conduit_power" -> "コンジットパワー";
                case "hero_of_the_village" -> "村の英雄";
                case "health_boost" -> "体力増強";
                case "slow_falling" -> "落下速度低下";
                case "luck" -> "幸運";
                default -> "ポーション効果";
            };
            lore.add(loreText(effectName + " (装備中常時)"));
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
        if (type.isFlightThread()) {
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
