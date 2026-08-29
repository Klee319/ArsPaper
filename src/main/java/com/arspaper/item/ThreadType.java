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
 *
 * <p><b>2026-08-18(W-102): enum をやめて「組み込み定数 + 実行時登録」の最終クラスにした。</b>
 * 以前はここが enum だったため、<b>「スレッドかどうか」の判定がコンパイル時に閉じていた</b>。
 * {@code ThreadGui#isEffectThread} は PDC の {@code arspaper:thread_item_type} を
 * {@link #fromId} に通して装着可否を決めるので、<b>enum に定数が無い id は
 * 「スレッドではない」と判定される</b> —— 設定エディタからスレッドを足しても、
 * jar を作り直すまで防具に挿せず、品質も乗らなかった
 * (品質側の再刻印は {@code hasEffect()} を条件にしているため、
 * 定数が無い＝品質が常に無視される、という形で同じ根から3つの症状が出ていた)。
 *
 * <p>そこで {@link #BY_ID} を唯一の台帳にし、{@code threads.yml} に書かれた
 * 未知の id は {@link #register} で実行時に足せるようにした。
 * <b>enum ではなくなったが、定数は今までどおり1 id につき1インスタンスなので
 * {@code ==} 比較も従来のまま通る。</b>
 * ただし {@code EnumMap}/{@code EnumSet}/{@code switch} は使えない
 * ({@code ArmorManaListener} の {@code EnumMap} は {@code HashMap} へ置き換え済み)。
 * 永続化は {@link #getId()} の文字列なので、この変更でセーブデータは一切影響を受けない
 * ({@code name()}/{@code ordinal()} は元々どこからも使われていない)。
 */
public final class ThreadType {

    /**
     * id → 定義の唯一の台帳。<b>定数より前に初期化されている必要がある</b> ——
     * 各定数のコンストラクタがここへ自分を登録するため、宣言順を入れ替えると
     * 静的初期化中に NPE で全スレッドが死ぬ。
     */
    private static final java.util.Map<String, ThreadType> BY_ID = new java.util.LinkedHashMap<>();

    // === 空スレッド ===
    public static final ThreadType EMPTY = new ThreadType("empty", "空のスレッド", 300001, NamedTextColor.GRAY,
        0, 0, null, 0, 0, 0, 0, Material.STRING);

    // === マナ系 ===
    // 数値の実体は TF item-stats。ここへ書くと editor に無い値がグレー lore と実行時に二重に乗る。
    public static final ThreadType MANA_REGEN = new ThreadType("mana_regen", "マナ回復速度上昇のスレッド", 300002, NamedTextColor.AQUA,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType MANA_BOOST = new ThreadType("mana_boost", "マナ最大値上昇のスレッド", 300003, NamedTextColor.BLUE,
        0, 0, null, 0, 0, 0, 0, Material.STRING);

    // === ポーション効果系 ===
    public static final ThreadType SPEED = new ThreadType("speed", "迅速のスレッド", 300004, NamedTextColor.WHITE,
        0, 0, PotionEffectType.SPEED, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType JUMP_BOOST = new ThreadType("jump_boost", "跳躍のスレッド", 300005, NamedTextColor.GREEN,
        0, 0, PotionEffectType.JUMP_BOOST, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType NIGHT_VISION = new ThreadType("night_vision", "暗視のスレッド", 300006, NamedTextColor.DARK_AQUA,
        0, 0, PotionEffectType.NIGHT_VISION, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType FIRE_RESISTANCE = new ThreadType("fire_resistance", "耐火のスレッド", 300007, NamedTextColor.RED,
        0, 0, PotionEffectType.FIRE_RESISTANCE, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType DOLPHINS_GRACE = new ThreadType("dolphins_grace", "イルカの好意のスレッド", 300008, NamedTextColor.DARK_AQUA,
        0, 0, PotionEffectType.DOLPHINS_GRACE, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType CONDUIT_POWER = new ThreadType("conduit_power", "コンジットパワーのスレッド", 300009, NamedTextColor.AQUA,
        0, 0, PotionEffectType.CONDUIT_POWER, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType HERO_OF_THE_VILLAGE = new ThreadType("hero_of_the_village", "村の英雄のスレッド", 300010, NamedTextColor.GREEN,
        0, 0, PotionEffectType.HERO_OF_THE_VILLAGE, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType HEALTH_BOOST = new ThreadType("health_boost", "体力増強のスレッド", 300011, NamedTextColor.RED,
        0, 0, PotionEffectType.HEALTH_BOOST, 0, 0, 0, 0, Material.STRING);

    // === マナ回復系 ===
    public static final ThreadType HIT_MANA_RECOVERY = new ThreadType("hit_mana_recovery", "被弾マナ回復のスレッド", 300012, NamedTextColor.GOLD,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType DAMAGE_MANA_RECOVERY = new ThreadType("damage_mana_recovery", "攻撃マナ回復のスレッド", 300013, NamedTextColor.DARK_RED,
        0, 0, null, 0, 0, 0, 0, Material.STRING);

    // === 特殊系 ===
    // costReduction は 0。軽減率の実体は TF item-stats (STRING#300014) が持ち、
    // ArmorManaListener が ThreadManaStatRouting 経由で整数%へ写す。
    // ここに 10 を残すと item-stats の 15% と二重に乗り、さらに percent-point 誤記(15)を
    // ×100 すると 100% キャップ＝消費マナ1 になる。
    public static final ThreadType SPELL_COST_DOWN = new ThreadType("spell_cost_down", "詠唱効率のスレッド", 300014, NamedTextColor.YELLOW,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType FLIGHT = new ThreadType("flight", "飛行のスレッド", 300015, NamedTextColor.WHITE,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType BACKPACK = new ThreadType("backpack", "バックパックのスレッド", 300016, NamedTextColor.DARK_GREEN,
        0, 0, null, 0, 0, 0, 0, Material.STRING);

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
    public static final ThreadType MANA_AMPLIFY = new ThreadType("mana_amplify", "マナ増幅のスレッド", 300017, NamedTextColor.BLUE,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType MANA_CIRCULATE = new ThreadType("mana_circulate", "循環のスレッド", 300018, NamedTextColor.AQUA,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType SOURCE_THRIFT = new ThreadType("source_thrift", "源流節約のスレッド", 300019, NamedTextColor.DARK_AQUA,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType ARTISAN = new ThreadType("artisan", "匠のスレッド", 300020, NamedTextColor.GOLD,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType RITUALIST = new ThreadType("ritualist", "儀式師のスレッド", 300021, NamedTextColor.LIGHT_PURPLE,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType THRIFT = new ThreadType("thrift", "倹約のスレッド", 300022, NamedTextColor.YELLOW,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType SALVAGE = new ThreadType("salvage", "解体のスレッド", 300023, NamedTextColor.GRAY,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType SCHOLAR = new ThreadType("scholar", "選書のスレッド", 300031, NamedTextColor.DARK_PURPLE,
        0, 0, null, 0, 0, 0, 0, Material.STRING);

    // --- 採取・生活系: 入手経路=ルート。効果値は基準の約1.2倍 ---
    public static final ThreadType MINER = new ThreadType("miner", "豊鉱のスレッド", 300024, NamedTextColor.DARK_GRAY,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType ANGLER = new ThreadType("angler", "潮読みのスレッド", 300025, NamedTextColor.BLUE,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType HARVEST = new ThreadType("harvest", "実りのスレッド", 300026, NamedTextColor.YELLOW,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType TIMBER = new ThreadType("timber", "年輪のスレッド", 300027, NamedTextColor.DARK_GREEN,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType DILIGENCE = new ThreadType("diligence", "研鑽のスレッド", 300029, NamedTextColor.GREEN,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType ENDURANCE = new ThreadType("endurance", "持久のスレッド", 300033, NamedTextColor.GOLD,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType GOURMET = new ThreadType("gourmet", "美食のスレッド", 300034, NamedTextColor.RED,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType SLOW_FALLING = new ThreadType("slow_falling", "浮遊のスレッド", 300039, NamedTextColor.WHITE,
        0, 0, PotionEffectType.SLOW_FALLING, 0, 0, 0, 0, Material.STRING);

    // --- 戦闘系: 入手経路=ダンジョン。効果値は基準の約1.5倍 ---
    public static final ThreadType SPOILS = new ThreadType("spoils", "戦利品のスレッド", 300028, NamedTextColor.GOLD,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType EXPERIENCE = new ThreadType("experience", "経験のスレッド", 300030, NamedTextColor.GREEN,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType MENDING_FLESH = new ThreadType("mending_flesh", "治癒のスレッド", 300032, NamedTextColor.RED,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType THORN = new ThreadType("thorn", "棘のスレッド", 300035, NamedTextColor.DARK_RED,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType CONCUSSION = new ThreadType("concussion", "昏倒のスレッド", 300036, NamedTextColor.DARK_AQUA,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType SWIFTCAST = new ThreadType("swiftcast", "速攻のスレッド", 300037, NamedTextColor.YELLOW,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType MARKSMAN = new ThreadType("marksman", "射手のスレッド", 300038, NamedTextColor.DARK_GREEN,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType LUCK = new ThreadType("luck", "幸運のスレッド", 300040, NamedTextColor.GOLD,
        0, 0, PotionEffectType.LUCK, 0, 0, 0, 0, Material.STRING);

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
    public static final ThreadType PERFUMER = new ThreadType("perfumer", "調香のスレッド", 300041, NamedTextColor.LIGHT_PURPLE,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType APIARIST = new ThreadType("apiarist", "養蜂のスレッド", 300042, NamedTextColor.YELLOW,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType HERDER = new ThreadType("herder", "牧人のスレッド", 300043, NamedTextColor.GREEN,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType APPRAISER = new ThreadType("appraiser", "鑑識のスレッド", 300044, NamedTextColor.GOLD,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType EXCAVATION = new ThreadType("excavation", "削岩のスレッド", 300045, NamedTextColor.DARK_GRAY,
        0, 0, null, 0, 0, 0, 0, Material.STRING);

    // ================================================================
    // 【隠密のスレッド】(2026-08-25 W-256 追加・ユーザー依頼「透明化エフェクトのつく
    //   隠密のスレッドの追加」)
    //
    // ⚠ ポーション効果を持つスレッドは【組み込み定数】として書くこと。
    //   threads.yml だけに書くと ThreadType.register() 経由の実行時登録になり、
    //   そちらは「効果の数値を一切持たせない」方針なので potion が enum 側に乗らない。
    //   (ThreadConfig の potion-effect: 上書きは効くが、上書き元が無い状態になる)
    //
    // ⚠ INVISIBILITY は防具スロット限定で走る(ThreadApplicationPolicy#isAmbientOnlyEffect)。
    //   手に持っただけで透明になると PvP が成立しないので、この境界は動かさないこと。
    public static final ThreadType STEALTH = new ThreadType("stealth", "隠密のスレッド", 300080, NamedTextColor.DARK_GRAY,
        0, 0, PotionEffectType.INVISIBILITY, 0, 0, 0, 0, Material.STRING);

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
    public static final ThreadType BETTER_FORTUNE = new ThreadType("better_fortune", "開運のスレッド", 100023, NamedTextColor.RED,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    // catalog.yml の表示名は1文字ずつ虹色だが enum は単色しか持てないので GOLD で代表する。
    public static final ThreadType GACHA = new ThreadType("gacha", "ガチャスレッド", 100024, NamedTextColor.GOLD,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType ROLE_LUCK = new ThreadType("role_luck", "ロール運のスレッド", 100025, NamedTextColor.WHITE,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType ROLE_EFFECIENCY = new ThreadType("role_effeciency", "ロール効率のスレッド", 100026, NamedTextColor.WHITE,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType BLINDNESS = new ThreadType("blindness", "崩命のスレッド", 100027, NamedTextColor.WHITE,
        0, 0, null, 0, 0, 0, 0, Material.STRING);
    public static final ThreadType TRANSLATE = new ThreadType("translate", "流転のスレッド", 100028, NamedTextColor.WHITE,
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

    private ThreadType(String id, String displayName, int customModelData, NamedTextColor color,
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
        // 台帳への登録はここ1箇所。組み込み定数も実行時登録も同じ経路を通る。
        BY_ID.put(id, this);
    }

    /**
     * {@code threads.yml} に書かれていて組み込み定数に無い id を、実行時にスレッドとして登録する
     * (W-102: 「アイテムカタログのスレッドタブで設定したらスレッドとして扱われる」)。
     *
     * <p><b>効果の数値は一切持たせない。</b> 後発スレッドの効果は TrinityForge 側
     * {@code stats/item-stats.yml} の {@code <素材>#<CMD>} が持ち、
     * {@code ArmorManaListener} が {@code TrinityForgeBridge.resolveThreadStats} で引くので、
     * ここで持つべきなのは<b>「スレッドである」という事実と、見た目(表示名/CMD/素材)</b>だけ。
     * マナ/ポーション/飛行の数値を持たせると Ars 側と TF 側で二重に効く。
     *
     * <p>既に同じ id があれば<b>それを返して何もしない</b> —— 組み込み定数を
     * yml から上書きさせない(上書きできると、飛行やバックパックのような
     * 特殊挙動を持つスレッドを設定ミスで無効化できてしまう)。
     *
     * @return 登録済み(または既存)の定義。id が空なら {@code null}
     */
    public static ThreadType register(String id, String displayName, int customModelData,
                                      NamedTextColor color, Material baseMaterial) {
        if (id == null || id.isBlank()) return null;
        ThreadType existing = BY_ID.get(id);
        if (existing != null) return existing;
        return new ThreadType(id,
                displayName == null || displayName.isBlank() ? id : displayName,
                customModelData,
                color == null ? NamedTextColor.WHITE : color,
                0, 0, null, 0, 0, 0, 0,
                baseMaterial == null ? Material.STRING : baseMaterial);
    }

    /**
     * 登録済みの全スレッド(組み込み + 実行時登録)。enum の {@code values()} と同じ用途。
     * 呼び出し側が配列を書き換えても台帳は壊れないようコピーを返す。
     */
    public static ThreadType[] values() {
        return BY_ID.values().toArray(new ThreadType[0]);
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
     * マナ数値は TF item-stats が持つのでここには出さない。
     */
    public List<Component> getEffectLore() {
        List<Component> lore = new ArrayList<>();
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
