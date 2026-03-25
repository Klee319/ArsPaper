package com.arspaper.item;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
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
    private final Map<String, Integer> backpackSlots = new HashMap<>();

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
        backpackSlots.clear();
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
            if (section.contains("slots")) {
                backpackSlots.put(key, section.getInt("slots"));
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

    /** バックパックスロット数 */
    public int getBackpackSlots(ThreadType type) {
        return backpackSlots.getOrDefault(type.getId(), 27);
    }

    /**
     * ThreadConfigの値を反映したloreを生成する。
     * ThreadType.getEffectLore()はEnum定数値を使うため、YAMLオーバーライドが反映されない。
     */
    public java.util.List<net.kyori.adventure.text.Component> getEffectLore(ThreadType type) {
        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();

        int regen = getRegenBonus(type);
        if (regen > 0) {
            lore.add(loreText("マナ回復速度 +" + regen + "/tick", net.kyori.adventure.text.format.NamedTextColor.AQUA));
        }
        int mana = getManaBonus(type);
        if (mana > 0) {
            lore.add(loreText("マナ最大値 +" + mana, net.kyori.adventure.text.format.NamedTextColor.BLUE));
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
                default -> "ポーション効果";
            };
            lore.add(loreText(effectName + " (装備中常時)", net.kyori.adventure.text.format.NamedTextColor.GREEN));
        }
        int hit = getHitManaRecovery(type);
        if (hit > 0) {
            lore.add(loreText("被弾時マナ回復 +" + hit, net.kyori.adventure.text.format.NamedTextColor.GOLD));
        }
        int dmg = getDamageManaRecovery(type);
        if (dmg > 0) {
            lore.add(loreText("攻撃時マナ回復 +" + dmg, net.kyori.adventure.text.format.NamedTextColor.DARK_RED));
        }
        int cost = getCostReduction(type);
        if (cost > 0) {
            lore.add(loreText("マナコスト -" + cost + "%", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        }
        if (type.isFlightThread()) {
            lore.add(loreText("エリトラ飛行 (装備中常時)", net.kyori.adventure.text.format.NamedTextColor.WHITE));
        }
        if (type.isBackpackThread()) {
            int slots = getBackpackSlots(type);
            lore.add(loreText("追加インベントリ " + slots + "スロット", net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN));
        }
        return lore;
    }

    private static net.kyori.adventure.text.Component loreText(String text, net.kyori.adventure.text.format.NamedTextColor color) {
        return net.kyori.adventure.text.Component.text(text, color)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }
}
