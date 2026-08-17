package com.arspaper.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * 防具スレッドのタイプ。
 * スレッドアイテムおよび防具PDCのスロットデータとして使用。
 * 効果量と重複設定はconfig.ymlのthreadsセクションで上書き可能。
 */
public enum ThreadType {

    // === 空スレッド ===
    EMPTY("empty", "空のスレッド", 300001, NamedTextColor.GRAY,
        0, 0, null, 0, 0, 0, 0, Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE),

    // === マナ系 ===
    MANA_REGEN("mana_regen", "マナ回復速度上昇のスレッド", 300002, NamedTextColor.AQUA,
        1, 0, null, 0, 0, 0, 0, Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE),
    MANA_BOOST("mana_boost", "マナ最大値上昇のスレッド", 300003, NamedTextColor.BLUE,
        0, 20, null, 0, 0, 0, 0, Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE),

    // === ポーション効果系 ===
    SPEED("speed", "迅速のスレッド", 300004, NamedTextColor.WHITE,
        0, 0, PotionEffectType.SPEED, 0, 0, 0, 0, Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE),
    JUMP_BOOST("jump_boost", "跳躍のスレッド", 300005, NamedTextColor.GREEN,
        0, 0, PotionEffectType.JUMP_BOOST, 0, 0, 0, 0, Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE),
    NIGHT_VISION("night_vision", "暗視のスレッド", 300006, NamedTextColor.DARK_AQUA,
        0, 0, PotionEffectType.NIGHT_VISION, 0, 0, 0, 0, Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE),
    FIRE_RESISTANCE("fire_resistance", "耐火のスレッド", 300007, NamedTextColor.RED,
        0, 0, PotionEffectType.FIRE_RESISTANCE, 0, 0, 0, 0, Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
    DOLPHINS_GRACE("dolphins_grace", "イルカの好意のスレッド", 300008, NamedTextColor.DARK_AQUA,
        0, 0, PotionEffectType.DOLPHINS_GRACE, 0, 0, 0, 0, Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE),
    CONDUIT_POWER("conduit_power", "コンジットパワーのスレッド", 300009, NamedTextColor.AQUA,
        0, 0, PotionEffectType.CONDUIT_POWER, 0, 0, 0, 0, Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE),
    HERO_OF_THE_VILLAGE("hero_of_the_village", "村の英雄のスレッド", 300010, NamedTextColor.GREEN,
        0, 0, PotionEffectType.HERO_OF_THE_VILLAGE, 0, 0, 0, 0, Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE),
    HEALTH_BOOST("health_boost", "体力増強のスレッド", 300011, NamedTextColor.RED,
        0, 0, PotionEffectType.HEALTH_BOOST, 0, 0, 0, 0, Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE),

    // === マナ回復系 ===
    HIT_MANA_RECOVERY("hit_mana_recovery", "被弾マナ回復のスレッド", 300012, NamedTextColor.GOLD,
        0, 0, null, 0, 0, 3, 0, Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE),
    DAMAGE_MANA_RECOVERY("damage_mana_recovery", "攻撃マナ回復のスレッド", 300013, NamedTextColor.DARK_RED,
        0, 0, null, 0, 0, 0, 2, Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE),

    // === 特殊系 ===
    SPELL_COST_DOWN("spell_cost_down", "詠唱効率のスレッド", 300014, NamedTextColor.YELLOW,
        0, 0, null, 0, 10, 0, 0, Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE),
    FLIGHT("flight", "飛行のスレッド", 300015, NamedTextColor.WHITE,
        0, 0, null, 0, 0, 0, 0, Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE),
    BACKPACK("backpack", "バックパックのスレッド", 300016, NamedTextColor.DARK_GREEN,
        0, 0, null, 0, 0, 0, 0, Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE),

    // ================================================================
    // 2026-08-02 スレッド 16 -> 40 種への拡張 (CMD 300017-300040)。
    //
    // ■ 以下 24 種の効果はどこに書いてあるか
    //   - mana_amplify / mana_circulate ... threads.yml の mana-max-percent / regen-percent
    //     (ThreadConfig と ManaManager が既に配線済みだったのに出荷 yml に1件も無かった遊休レバー)。
    //   - slow_falling / luck ............. ここの potionEffect(装備中常時のバニラポーション効果)。
    //   - 残り 20 種 ...................... thread-sets.yml の【しきい値1段目=1】。
    //
    // ■ なぜ item-stats.yml (MATERIAL#CMD) に単体ステを書かないのか【重要】
    //   ArmorManaListener はソケット済みスレッドについて TF item-stats.yml を読むが、同じエントリを
    //   TF の PlayerStatAggregator#aggregate が【材質フィルタ無しで】メインハンド寄与としても読む。
    //   つまりそこへ書くと「装備に挿さず手に持つだけで効果が乗る」穴が開く。thread-sets.yml の
    //   thresholds 1段目なら【ソケット済みしか数えない】ので手持ちでは発動しない。
    //
    // ■ id の禁止事項
    //   - "hit" を含む id を作らない: ThreadConfig の recovery 振り分けが key.contains("hit") の
    //     文字列判定なので、hit を含まない id に recovery: を書くと無言で攻撃時マナ回復に化ける。
    //   - water_breathing / spell_power を再利用しない: fromId が明示的に null を返す後方互換分岐を
    //     持っており、古い PDC が無言で復活する。
    // ================================================================

    // --- 制作(儀式・生産)系: 入手経路=制作。効果値は設計書の基準どおり ---
    MANA_AMPLIFY("mana_amplify", "マナ増幅のスレッド", 300017, NamedTextColor.BLUE,
        0, 0, null, 0, 0, 0, 0, Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE),
    MANA_CIRCULATE("mana_circulate", "循環のスレッド", 300018, NamedTextColor.AQUA,
        0, 0, null, 0, 0, 0, 0, Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE),
    SOURCE_THRIFT("source_thrift", "源流節約のスレッド", 300019, NamedTextColor.DARK_AQUA,
        0, 0, null, 0, 0, 0, 0, Material.FLOW_POTTERY_SHERD),
    ARTISAN("artisan", "匠のスレッド", 300020, NamedTextColor.GOLD,
        0, 0, null, 0, 0, 0, 0, Material.ARMS_UP_POTTERY_SHERD),
    RITUALIST("ritualist", "儀式師のスレッド", 300021, NamedTextColor.LIGHT_PURPLE,
        0, 0, null, 0, 0, 0, 0, Material.BREWER_POTTERY_SHERD),
    THRIFT("thrift", "倹約のスレッド", 300022, NamedTextColor.YELLOW,
        0, 0, null, 0, 0, 0, 0, Material.PLENTY_POTTERY_SHERD),
    SALVAGE("salvage", "解体のスレッド", 300023, NamedTextColor.GRAY,
        0, 0, null, 0, 0, 0, 0, Material.SCRAPE_POTTERY_SHERD),
    SCHOLAR("scholar", "選書のスレッド", 300031, NamedTextColor.DARK_PURPLE,
        0, 0, null, 0, 0, 0, 0, Material.BURN_POTTERY_SHERD),

    // --- 採取・生活系: 入手経路=ルート。効果値は基準の約1.2倍 ---
    MINER("miner", "豊鉱のスレッド", 300024, NamedTextColor.DARK_GRAY,
        0, 0, null, 0, 0, 0, 0, Material.MINER_POTTERY_SHERD),
    ANGLER("angler", "潮読みのスレッド", 300025, NamedTextColor.BLUE,
        0, 0, null, 0, 0, 0, 0, Material.ANGLER_POTTERY_SHERD),
    HARVEST("harvest", "実りのスレッド", 300026, NamedTextColor.YELLOW,
        0, 0, null, 0, 0, 0, 0, Material.SHEAF_POTTERY_SHERD),
    TIMBER("timber", "年輪のスレッド", 300027, NamedTextColor.DARK_GREEN,
        0, 0, null, 0, 0, 0, 0, Material.SNORT_POTTERY_SHERD),
    DILIGENCE("diligence", "研鑽のスレッド", 300029, NamedTextColor.GREEN,
        0, 0, null, 0, 0, 0, 0, Material.FRIEND_POTTERY_SHERD),
    ENDURANCE("endurance", "持久のスレッド", 300033, NamedTextColor.GOLD,
        0, 0, null, 0, 0, 0, 0, Material.SHELTER_POTTERY_SHERD),
    GOURMET("gourmet", "美食のスレッド", 300034, NamedTextColor.RED,
        0, 0, null, 0, 0, 0, 0, Material.HEARTBREAK_POTTERY_SHERD),
    SLOW_FALLING("slow_falling", "浮遊のスレッド", 300039, NamedTextColor.WHITE,
        0, 0, PotionEffectType.SLOW_FALLING, 0, 0, 0, 0, Material.GUSTER_POTTERY_SHERD),

    // --- 戦闘系: 入手経路=ダンジョン。効果値は基準の約1.5倍 ---
    SPOILS("spoils", "戦利品のスレッド", 300028, NamedTextColor.GOLD,
        0, 0, null, 0, 0, 0, 0, Material.SKULL_POTTERY_SHERD),
    EXPERIENCE("experience", "経験のスレッド", 300030, NamedTextColor.GREEN,
        0, 0, null, 0, 0, 0, 0, Material.HOWL_POTTERY_SHERD),
    MENDING_FLESH("mending_flesh", "治癒のスレッド", 300032, NamedTextColor.RED,
        0, 0, null, 0, 0, 0, 0, Material.HEART_POTTERY_SHERD),
    THORN("thorn", "棘のスレッド", 300035, NamedTextColor.DARK_RED,
        0, 0, null, 0, 0, 0, 0, Material.DANGER_POTTERY_SHERD),
    CONCUSSION("concussion", "昏倒のスレッド", 300036, NamedTextColor.DARK_AQUA,
        0, 0, null, 0, 0, 0, 0, Material.BLADE_POTTERY_SHERD),
    SWIFTCAST("swiftcast", "速攻のスレッド", 300037, NamedTextColor.YELLOW,
        0, 0, null, 0, 0, 0, 0, Material.MOURNER_POTTERY_SHERD),
    MARKSMAN("marksman", "射手のスレッド", 300038, NamedTextColor.DARK_GREEN,
        0, 0, null, 0, 0, 0, 0, Material.ARCHER_POTTERY_SHERD),
    LUCK("luck", "幸運のスレッド", 300040, NamedTextColor.GOLD,
        0, 0, PotionEffectType.LUCK, 0, 0, 0, 0, Material.EXPLORER_POTTERY_SHERD),

    // ================================================================
    // 2026-08-03 追加5種 (CMD 300041-300045)。
    //
    // ■ 上の40種と違う点は「儀式レシピを持たない」ことだけ
    //   items/catalog.yml のエントリに recipe: を書かず、TF の gacha.yml の景品としてしか出ない。
    //   そのぶん厳選(item-stats.yml の per-quality + random + grant-chances)の主ステを
    //   生活・採取・戦利品の軸に寄せ、「引き当てた甲斐がある」性格にしてある。
    //
    // ■ baseMaterial に旗の模様(BANNER_PATTERN)と鍛冶型を使った理由
    //   既存40種で防具トリム18種と陶器の欠片22種を使い切っており、空きは
    //   PRIZE の欠片1種だけだった(HOST は防具トリムにしか無く、HOST_POTTERY_SHERD は
    //   Paper の Material に存在しない)。同じ材質を使い回しても機能上は安全
    //   (スレッドの同一性は PDC の thread_type で、材質+CMD では判定していない)が、
    //   見た目が既存スレッドと完全に同じになるので避けた。旗の模様は「紋様」で
    //   スレッドの語感にも合う。
    //
    // ■ id の禁止事項は上の40種と同じ
    //   "hit" を含む id を作らない(ThreadConfig の recovery 振り分けが文字列判定)。
    //   perfumer / apiarist / herder / appraiser / excavation はいずれも該当しない。
    // ================================================================
    PERFUMER("perfumer", "調香のスレッド", 300041, NamedTextColor.LIGHT_PURPLE,
        0, 0, null, 0, 0, 0, 0, Material.FLOWER_BANNER_PATTERN),
    APIARIST("apiarist", "養蜂のスレッド", 300042, NamedTextColor.YELLOW,
        0, 0, null, 0, 0, 0, 0, Material.FIELD_MASONED_BANNER_PATTERN),
    HERDER("herder", "牧人のスレッド", 300043, NamedTextColor.GREEN,
        0, 0, null, 0, 0, 0, 0, Material.PIGLIN_BANNER_PATTERN),
    APPRAISER("appraiser", "鑑識のスレッド", 300044, NamedTextColor.GOLD,
        0, 0, null, 0, 0, 0, 0, Material.PRIZE_POTTERY_SHERD),
    EXCAVATION("excavation", "削岩のスレッド", 300045, NamedTextColor.DARK_GRAY,
        0, 0, null, 0, 0, 0, 0, Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE),

    // ================================================================
    // 【TFステ専用スレッド 6種】(2026-08-18 追加)
    //
    // ■ なぜ後から足したのか
    //   この6種は TrinityForge の catalog.yml に CMD 100023〜100028 で前から存在し、
    //   Ars の loot-tables.yml(4ダンジョン)と TF の gacha.yml から実際に配られていたが、
    //   **ThreadType にも threads.yml にも定義が無かった**。
    //   ThreadGui#isEffectThread は arspaper:thread_item_type PDC → ThreadType.fromId で
    //   装着可否を決めるので、この6種は「ドロップするのに防具へ永久に挿せない」状態だった
    //   (装着できない=効果は装着時にしか乗らないので、実質死んでいた)。
    //   ログにも何も出ないので、ShippedCatalogExternalSourceDriftTest が赤いことでしか
    //   気づけなかった。
    //
    // ■ 効果の実体はここには無い
    //   上の45種と違い、効果は TF 側 stats/item-stats.yml の STRING#1000xx が持つ
    //   (loot-luck / gacha-rate-bonus / craft-roll-up-bonus / craft-roll-down-reduction /
    //   move-speed / hidden-saturation-bonus)。ArmorManaListener が
    //   TrinityForgeBridge.resolveThreadStats(baseMaterial, cmd, ...) で引くため、
    //   **baseMaterial と customModelData が item-stats のキーと一致していることが唯一の要件**。
    //   マナ/ポーション/飛行の数値は全部0で正しい。
    //   数値キーを持たないので getEffectLore() は0行になる ──
    //   threads.yml 側に必ず lore: を書くこと(thread-sets 系スレッドと同じ扱い)。
    //
    // ■ baseMaterial が STRING で揃っているのは意図的
    //   catalog.yml / item-stats.yml / resourcepack の cmd-registry.json が既に
    //   STRING#1000xx で登録済みなので、見た目を変えないためにそのまま合わせる。
    //   CMD が別なのでモデルは6種それぞれ別に解決される。
    //
    // ■ id の禁止事項は上の45種と同じ
    //   "hit" を含む id を作らない(ThreadConfig の recovery 振り分けが文字列判定)。
    //   better_fortune / gacha / role_luck / role_effeciency / blindness / translate は
    //   いずれも該当しない。role_effeciency の綴りは catalog.yml 側の既存IDに合わせている
    //   (typo だが配布済みなので直せない)。
    // ================================================================
    BETTER_FORTUNE("better_fortune", "開運のスレッド", 100023, NamedTextColor.RED,
        0, 0, null, 0, 0, 0, 0, Material.STRING),
    // catalog.yml の表示名は1文字ずつ虹色だが enum は単色しか持てないので GOLD で代表する。
    GACHA("gacha", "ガチャスレッド", 100024, NamedTextColor.GOLD,
        0, 0, null, 0, 0, 0, 0, Material.STRING),
    ROLE_LUCK("role_luck", "ロール運のスレッド", 100025, NamedTextColor.WHITE,
        0, 0, null, 0, 0, 0, 0, Material.STRING),
    ROLE_EFFECIENCY("role_effeciency", "ロール効率のスレッド", 100026, NamedTextColor.WHITE,
        0, 0, null, 0, 0, 0, 0, Material.STRING),
    BLINDNESS("blindness", "崩命のスレッド", 100027, NamedTextColor.WHITE,
        0, 0, null, 0, 0, 0, 0, Material.STRING),
    TRANSLATE("translate", "流転のスレッド", 100028, NamedTextColor.WHITE,
        0, 0, null, 0, 0, 0, 0, Material.STRING);

    private final String id;
    private final String displayName;
    private final int customModelData;
    private final NamedTextColor color;
    private final int regenBonus;
    private final int manaBonus;
    private final PotionEffectType potionEffect;
    private final int potionAmplifier;
    private final int costReductionPercent;
    private final int hitManaRecovery;
    private final int damageManaRecovery;
    private final Material baseMaterial;

    ThreadType(String id, String displayName, int customModelData, NamedTextColor color,
               int regenBonus, int manaBonus,
               PotionEffectType potionEffect, int potionAmplifier,
               int costReductionPercent,
               int hitManaRecovery, int damageManaRecovery,
               Material baseMaterial) {
        this.id = id;
        this.displayName = displayName;
        this.customModelData = customModelData;
        this.color = color;
        this.regenBonus = regenBonus;
        this.manaBonus = manaBonus;
        this.potionEffect = potionEffect;
        this.potionAmplifier = potionAmplifier;
        this.costReductionPercent = costReductionPercent;
        this.hitManaRecovery = hitManaRecovery;
        this.damageManaRecovery = damageManaRecovery;
        this.baseMaterial = baseMaterial;
    }

    public Material getBaseMaterial() { return baseMaterial; }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getRegenBonus() { return regenBonus; }
    public int getManaBonus() { return manaBonus; }
    public int getCustomModelData() { return customModelData; }
    public NamedTextColor getColor() { return color; }
    public PotionEffectType getPotionEffect() { return potionEffect; }
    public int getPotionAmplifier() { return potionAmplifier; }
    public int getCostReductionPercent() { return costReductionPercent; }
    public int getHitManaRecovery() { return hitManaRecovery; }
    public int getDamageManaRecovery() { return damageManaRecovery; }

    /** @deprecated spell_power は削除済み。互換性のため0を返す */
    @Deprecated
    public int getSpellPowerPercent() { return 0; }

    /** 効果を持つスレッドか（空でない） */
    public boolean hasEffect() { return this != EMPTY; }

    /** ポーション効果を付与するスレッドか */
    public boolean hasPotionEffect() { return potionEffect != null; }

    /** 飛行スレッドか */
    public boolean isFlightThread() { return this == FLIGHT; }

    /** バックパックスレッドか */
    public boolean isBackpackThread() { return this == BACKPACK; }

    /**
     * このスレッドの効果説明をComponent Loreとして返す。
     */
    public List<Component> getEffectLore() {
        List<Component> lore = new ArrayList<>();
        if (regenBonus > 0) {
            lore.add(loreText("マナ回復速度 +" + regenBonus + "/tick", NamedTextColor.AQUA));
        }
        if (manaBonus > 0) {
            lore.add(loreText("マナ最大値 +" + manaBonus, NamedTextColor.BLUE));
        }
        if (potionEffect != null) {
            String effectName = switch (id) {
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
            lore.add(loreText(effectName + " (装備中常時)", NamedTextColor.GREEN));
        }
        if (hitManaRecovery > 0) {
            lore.add(loreText("被弾時マナ回復 +" + hitManaRecovery, NamedTextColor.GOLD));
        }
        if (damageManaRecovery > 0) {
            lore.add(loreText("攻撃時マナ回復 +" + damageManaRecovery, NamedTextColor.DARK_RED));
        }
        if (costReductionPercent > 0) {
            lore.add(loreText("マナコスト -" + costReductionPercent + "%", NamedTextColor.YELLOW));
        }
        if (this == FLIGHT) {
            lore.add(loreText("エリトラ飛行 (装備中常時)", NamedTextColor.WHITE));
        }
        if (this == BACKPACK) {
            lore.add(loreText("追加インベントリ 27スロット", NamedTextColor.DARK_GREEN));
        }
        return lore;
    }

    private static Component loreText(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public static ThreadType fromId(String id) {
        if (id == null) return null;
        for (ThreadType t : values()) {
            if (t.id.equals(id)) return t;
        }
        // 後方互換: 削除されたスレッドは無視
        if ("water_breathing".equals(id) || "spell_power".equals(id)) return null;
        return null;
    }
}
