package com.arspaper.integration;

import com.arspaper.ArsPaper;
import com.arspaper.item.BaseCustomItem;
import com.arspaper.ritual.CatalogRitualRegistrar;
import com.trinityforge.TrinityForge;
import com.trinityforge.integration.ars.ArsProgressionBridge;
import com.trinityforge.combat.AddonCombatStats;
import com.trinityforge.combat.AttackStats;
import com.trinityforge.combat.CombatHitResult;
import com.trinityforge.combat.CritFlash;
import com.trinityforge.combat.EnchantmentStatBridge;
import com.trinityforge.combat.PlayerStatAggregator;
import com.trinityforge.combat.SymmetricCombatService;
import com.trinityforge.combat.WeaponAttackStatResolver;
import com.trinityforge.stats.ExternalItemRegistry;
import com.trinityforge.stats.MaterialTier;
import com.trinityforge.stats.StatKeys;
import com.trinityforge.stats.CraftQualityService;
import com.trinityforge.stats.CraftRollMods;
import com.trinityforge.pdc.BindType;
import com.trinityforge.pdc.ItemData;
import com.trinityforge.pdc.PdcKeys;
import com.trinityforge.progression.OwnerBindPolicy;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * TrinityForge 統合アドオンへの委譲窓口。
 *
 * <p>魔法ダメージは TrinityForge の対称ダメージパイプライン
 * ({@link SymmetricCombatService#magicalFinalDamage}) に供給し、返ってきた最終ダメージを
 * <b>MAGIC ダメージソース</b>で適用する。MAGIC を使う理由:
 * <ul>
 *   <li>TrinityForge の {@code CombatListener} は cause=ENTITY_ATTACK/SWEEP のみを物理として
 *       再計算する。{@code LivingEntity#damage(double, Entity)} は ENTITY_ATTACK を発火させるため、
 *       そのまま使うと魔法が物理パイプラインで上書きされてしまう。MAGIC cause なら横取りされない。</li>
 *   <li>パイプラインは既に魔法守備力・魔法耐性・防御率(armor)を計算済み。バニラ MAGIC はバニラ armor を
 *       無視するため、armor の二重軽減を避けられる。</li>
 * </ul>
 *
 * <p>全メソッドはメインスレッド前提（{@code magicalFinalDamage} と Bukkit ダメージ適用が同期）。
 */
public final class TrinityForgeBridge {

    private static final String PLUGIN_NAME = "TrinityForge";
    private static volatile SymmetricCombatService cachedService;
    // combatService() をキャッシュした時点の TrinityForge プラグインインスタンス。
    // TF単独のホットリロード(ArsPaper#reset()を経由しない)でプラグインインスタンスが差し替わった、
    // または無効化された場合にキャッシュの陳腐化を検出するための識別子。
    private static volatile org.bukkit.plugin.Plugin cachedPlugin;
    private static volatile boolean unavailableLogged = false;

    private TrinityForgeBridge() {
    }

    /**
     * TrinityForge の対称戦闘サービスを取得する。未ロード時は {@code null}。
     *
     * <p>キャッシュ済みインスタンス取得時に、キャッシュ時点のプラグインインスタンスと
     * 現在 {@link Bukkit#getPluginManager()} が返すインスタンスの同一性を照合する。
     * 不一致（TF単独ホットリロードでインスタンスが差し替わった等）または現在のプラグインが
     * 無効化/未ロードの場合はキャッシュを破棄して再解決する。
     */
    public static SymmetricCombatService combatService() {
        try {
            SymmetricCombatService service = cachedService;
            org.bukkit.plugin.Plugin cached = cachedPlugin;
            org.bukkit.plugin.Plugin current = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
            if (service != null && cached != null && cached == current && current.isEnabled()) {
                return service;
            }
            if (current == null || !current.isEnabled()) {
                cachedService = null;
                cachedPlugin = null;
                return null;
            }
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                cachedService = null;
                cachedPlugin = null;
                return null;
            }
            service = tf.combatService();
            cachedService = service;
            cachedPlugin = current;
            return service;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isAvailable() {
        return combatService() != null;
    }

    /**
     * キャッシュした {@link SymmetricCombatService} と未ロード警告フラグを破棄する。
     *
     * <p>TrinityForge のホットリロード後はサービスインスタンスが差し替わるため、
     * キャッシュを無効化して次回 {@link #combatService()} で再解決させる。
     * ArsPaper の onEnable から呼ぶことで、再有効化のたびに最新サービスへ追従する。
     */
    public static void reset() {
        cachedService = null;
        cachedPlugin = null;
        unavailableLogged = false;
    }

    /**
     * スペル基礎ダメージを対称パイプラインへ供給し、最終魔法ダメージを返す（触媒なし）。
     *
     * <p>儀式・タレット等の非プレイヤー詠唱など触媒 ItemStack が特定できない経路向け。
     * 触媒由来の会心/貫通は乗らず、{@link AttackStats#plain(0)} 相当で計算する。
     *
     * @param casterUuid 詠唱者UUID
     * @param victim     被弾エンティティ（{@code PersistentDataHolder}）
     * @param spellBase  スペル基礎ダメージ（Ars攻撃力 + 増減グリフを内包済み）
     * @return 8stepパイプライン後の最終ダメージ。サービス未ロード時は {@code spellBase} をそのまま返す
     */
    public static double magicalFinalDamage(UUID casterUuid, LivingEntity victim, double spellBase) {
        return magicalFinalDamage(casterUuid, victim, spellBase, null);
    }

    /**
     * 旧シグネチャ（触媒のみ）。{@code castItem}(詠唱に使った実アイテム)を知らない呼び出し元向けに温存する。
     * 挙動は {@code castItem == null} の場合と同一で、ステ供給元は「catalysts.yml 登録済みの触媒」だけ。
     */
    public static double magicalFinalDamage(UUID casterUuid, LivingEntity victim, double spellBase,
                                            ItemStack catalyst) {
        return magicalFinalDamage(casterUuid, victim, spellBase, catalyst, null, null);
    }

    /**
     * グリフID無しの5引数版。{@code glyph_damage_multiplier_bonus}(グリフ別ダメージ倍率)は掛からない。
     */
    public static double magicalFinalDamage(UUID casterUuid, LivingEntity victim, double spellBase,
                                            ItemStack catalyst, ItemStack castItem) {
        return magicalFinalDamage(casterUuid, victim, spellBase, catalyst, castItem, null);
    }

    /**
     * グリフID付きの6引数版。増幅(Amplify)の乗算ボーナスは掛からない
     * （{@code amplifyLevel=0} で {@link #magicalFinalDamage(UUID, LivingEntity, double, ItemStack,
     * ItemStack, String, int)} へ委譲）。現状の呼び出し元は
     * {@code SpellContext#dealSpellDamage} 経由の7引数版のみで、本メソッドは旧シグネチャの
     * 呼び出し元向けに温存している。
     */
    public static double magicalFinalDamage(UUID casterUuid, LivingEntity victim, double spellBase,
                                            ItemStack catalyst, ItemStack castItem, String glyphId) {
        return magicalFinalDamage(casterUuid, victim, spellBase, catalyst, castItem, glyphId, 0);
    }

    /**
     * スペル基礎ダメージを対称パイプラインへ供給し、杖/触媒の攻撃ステを乗せた最終魔法ダメージを返す。
     *
     * <p>ステ供給元（杖 or 登録済み触媒）の選択は {@link #resolveMagicStatSource} が担う。
     * その会心・貫通等は {@link WeaponAttackStatResolver#forItem} 系で {@link AttackStats} に導出し、
     * 対称パイプラインへ供給する。会心/貫通はTF側という層分離を維持するため、{@code spellBase} には
     * 攻撃力(attack-power)以外のステを二重計上しない（TF側 AttackStats が別レイヤーで加味する）。
     *
     * <p>フォールバック（挙動不変の安全策）:
     * <ul>
     *   <li>TF未ロード（{@link #combatService()}==null）→ {@code spellBase} を素通し（fail-open）。</li>
     *   <li>ステ供給元が無い / 取得失敗 / resolver未初期化 → {@link AttackStats#plain(0)} 相当で計算。</li>
     * </ul>
     *
     * @param casterUuid   詠唱者UUID
     * @param victim       被弾エンティティ（{@code PersistentDataHolder}）
     * @param spellBase    スペル基礎ダメージ（Ars攻撃力を内包済み。2026-08-02以降、増減グリフは
     *                     {@code amplifyLevel} 側に分離した——{@code applyAmplifyDamageMultiplier=false}
     *                     で呼ばれた経路(例: {@code HealEffect})だけは従来どおり内包済み）
     * @param catalyst     詠唱に使った触媒 ItemStack（{@code catalysts.yml} 登録品、または非触媒バインド
     *                     詠唱では魔導書本体）。特定不能なら {@code null}
     * @param castItem     実際に右クリックして詠唱したアイテム（杖など）。{@code use-skill: ARS_MAGIC}
     *                     を持つ場合だけステ供給元として採用する。特定不能なら {@code null}
     * @param glyphId      ダメージを出したグリフのID（{@code "harm"} 等）。TF の
     *                     {@code glyph_damage_multiplier_bonus} の適用対象判定に使う。不明なら {@code null}
     * @param amplifyLevel 増幅段数（{@code SpellContext#getAmplifyLevel()}。Dampenで負にもなり得る）。
     *                     {@code 0} なら乗算ボーナスなし（{@code applyAmplifyDamageMultiplier=false} の
     *                     呼び出し元、または増幅グリフが1個も積まれていない詠唱）
     * @return 8stepパイプライン後の最終ダメージ。サービス未ロード時は {@code spellBase} をそのまま返す
     */
    public static double magicalFinalDamage(UUID casterUuid, LivingEntity victim, double spellBase,
                                            ItemStack catalyst, ItemStack castItem, String glyphId,
                                            int amplifyLevel) {
        SymmetricCombatService service = combatService();
        if (service == null) {
            warnUnavailableOnce();
            return spellBase;
        }
        // 仕様(ARSPAPER_FORK_SPEC / MAGIC_BALANCE_SPEC「デフォルト魔法ダメージ＝Arsスペル攻撃力
        // ＋ 触媒の攻撃力ステ」): 魔法基礎ダメージ = グリフ基礎ダメージ(spellBase)
        // ＋ 杖の攻撃力(attack-power) × combat/damage.yml の magical.attack-power-scale。
        // 物理(vanilla base + attack_power)と対称の加算。attack-power が無い/係数0なら加算値0
        // (グリフダメージ据え置き＝従来挙動)。
        //
        // 2026-07-31 D6: ここへ渡すステ供給元(statSource)は「catalysts.yml 登録品の触媒」だけでは
        // なく「実際に詠唱に使った杖(castItem)」も含む。TFカタログの杖11本は catalysts.yml に
        // 載っていないため、以前は触媒引数が魔導書に化けて杖の attack-power が完全に落ちていた。
        ItemStack statSource = resolveMagicStatSource(catalyst, castItem);
        // 2026-08-08: ステへ加算できるエンチャントを魔法にも効かせる(近接と対称)。
        // それまで魔法側は item-stats の生の attack-power しか読んでおらず、ダメージ増加/特攻/
        // 破壊(Breach)が「杖に付けられるのに一切効かない」状態だった。物理側は CombatListener が
        // 同じ EnchantmentStatBridge を通しているので、係数(ダメージ増加 +5%/lv・特攻 +7.5%/lv・
        // Breach 貫通 +10%/lv)はそちらと共通で、ここで新しい数値は一切定義しない。
        //
        // 二重計上は起きない: adjustedAttackPower の呼び出し元は CombatListener(近接専用)だけで、
        // PlayerStatAggregator / WeaponAttackStatResolver はエンチャントを集計に含めない。
        //
        // victim を渡すのが要点 — 特攻(Smite/Bane/Impaling)は対象の種類が一致したときだけ乗る。
        // null を渡すと特攻が黙って落ちて「アンデッドに聖なる力が効かない」形のバグになる。
        EnchantmentStatBridge.Bonuses enchantBonuses =
                EnchantmentStatBridge.bonuses(statSource, victim);
        double enchantedAttackPower = EnchantmentStatBridge.adjustedAttackPower(
                itemAttackPower(statSource), enchantBonuses);
        double effectiveBase = MagicStatSourcePolicy.effectiveBase(
                spellBase, enchantedAttackPower, magicalAttackPowerScale());
        // 2026-08-02: 増幅(Amplify)の乗算ボーナス。「グリフ基礎＋杖の攻撃力」の合計へ掛ける
        // (理由は MagicStatSourcePolicy#applyAmplifyMultiplier の javadoc — グリフ基礎だけに
        // 掛けると触媒ビルドで実質無効になるのが変更の動機そのものなので、glyph倍率と同じ層で掛ける)。
        effectiveBase = MagicStatSourcePolicy.applyAmplifyMultiplier(
                effectiveBase, amplifyLevel, amplifyDamageRatePerStack(), maxAmplifyDamageLevel());
        // 課題G5: glyph_damage_multiplier_bonus(TF公開API TrinityForge#glyphDamageMultiplier)は
        // lore に出るのにフォーク側の呼び出し元が1つも無く効いていなかった。基礎ダメージ層で掛ける
        // (理由は MagicStatSourcePolicy#applyGlyphMultiplier の javadoc)。
        effectiveBase = MagicStatSourcePolicy.applyGlyphMultiplier(
                effectiveBase, glyphDamageMultiplier(casterUuid, glyphId));
        try {
            // service が effectiveBase を defaultDamage として注入し、攻撃ステ(会心/貫通等)を別レイヤーで加味する。
            CombatHitResult hit = service.magicalFinalDamageResult(
                    casterUuid, victim, effectiveBase,
                    resolveMagicAttackStats(casterUuid, statSource, enchantBonuses));
            if (hit.crit()) {
                CritFlash.play(victim);
            }
            // 課題1(魔法出血): 出血ロール自体はTF側の公開API(TrinityForge#applyMagicBleed)に一本化し、
            // フォークは「魔法が当たったこと」だけを伝える(確率ロジックはこちらに書かない)。0以下
            // (回復含む)では絶対にロールしない — SpellContext#dealSpellDamage はこの時点以降、
            // finalDamage<=0では applyMagicDamage を呼ばない(=実際にダメージが適用されない)ため、
            // hit.damage()>0 をここで確認するのは「実際に適用される最終ダメージ」と同じ条件になる。
            // 注意: 将来 magicalFinalDamage を「算出だけ」に使う呼び出し元(applyMagicDamageへ進まない)
            // が増えた場合、この出血ロールは実際にダメージが適用されない攻撃に対しても空撃ちする。
            if (hit.damage() > 0.0 && casterUuid != null) {
                notifyMagicBleed(casterUuid, victim, statSource, hit.damage());
            }
            return hit.damage();
        } catch (Throwable t) {
            // TFホットリロード直後の失効サービス等でコア呼出しが例外を投げても、詠唱を中断させず
            // fail-open で未軽減の spellBase を返す(service==nullの既存分岐と同じ安全側)。
            return spellBase;
        }
    }

    /**
     * 課題1(魔法出血): TrinityForgeの公開API({@link TrinityForge#applyMagicBleed}) へ
     * 「魔法が当たったこと」を伝えるだけの薄い橋渡し。ロール判定(bleed-chance/bleed-damageの読み取り、
     * 乱数判定)とBleedService呼び出しの責務はTF側が持つ——フォーク側に確率ロジックを書かない。
     *
     * <p>渡す集約は {@link #resolveMagicAttackStats} が使うのと全く同じ集約対象
     * (触媒判定・メインハンド除外)。ただし bleed-chance/bleed-damage は {@link AttackStats} の
     * フィールドに含まれないため、{@link #resolveMagicBleedAggregate} で生の canonical map として
     * 別途取得する(既存の魔法ダメージ計算経路 {@link #resolveMagicAttackStats} 自体には手を入れない)。
     *
     * <p>TF未ロード / API不整合 / 例外時は no-op(fail-open) — 魔法ダメージの適用自体には一切影響しない。
     */
    private static void notifyMagicBleed(UUID casterUuid, LivingEntity victim, ItemStack statSource,
                                          double finalDamage) {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return;
            }
            Map<String, Double> attackerStats = resolveMagicBleedAggregate(casterUuid, statSource);
            tf.applyMagicBleed(attackerStats, victim, casterUuid, finalDamage);
        } catch (Throwable t) {
            // TF未ロード / API不整合: 出血ロールをスキップ(魔法ダメージ自体の適用には影響しない)。
        }
    }

    /**
     * {@link #resolveMagicAttackStats} と同じ集約対象(同一の statSource・メインハンド除外)を、生の
     * canonical stat map として返す。bleed-chance/bleed-damage は {@link AttackStats} のフィールドに
     * 含まれないため、この専用ヘルパで別途取得する(課題1: 魔法ダメージが読む集約と出血が読む集約を
     * 一致させる要件 — <b>片方だけ statSource を変えるとこの一致が崩れる</b>ので必ず両方へ同じ
     * {@code statSource} を渡すこと)。詠唱者がオフライン等で解決できない場合は、非プレイヤー詠唱時の
     * {@link #resolveCatalystStats} と対称に、statSource 自身のフル解決ステ
     * ({@link #resolveFullItemStats}) まで(statSource が null なら空集約)にフォールバックする。
     *
     * @param statSource {@link #resolveMagicStatSource} が選んだステ供給元。無ければ {@code null}
     */
    private static Map<String, Double> resolveMagicBleedAggregate(UUID casterUuid, ItemStack statSource) {
        try {
            Player caster = casterUuid != null ? Bukkit.getPlayer(casterUuid) : null;
            if (caster == null) {
                return statSource != null
                        ? new LinkedHashMap<>(resolveFullItemStats(statSource)) : new LinkedHashMap<>();
            }
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return statSource != null
                        ? new LinkedHashMap<>(resolveFullItemStats(statSource)) : new LinkedHashMap<>();
            }
            PlayerStatAggregator aggregator = tf.playerStatAggregator();
            if (aggregator == null) {
                return statSource != null
                        ? new LinkedHashMap<>(resolveFullItemStats(statSource)) : new LinkedHashMap<>();
            }
            return new LinkedHashMap<>(aggregator.aggregateExcludingMainhandWith(caster, statSource));
        } catch (Throwable t) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * Change 1(P10 魔法アグリゲーション): 魔法攻撃側の攻撃ステ(会心/貫通/固定ダメージ等)集計元。
     * <ul>
     *   <li>杖/触媒詠唱: (キャスターのメインハンドを除く装備＝防具4部位＋オフハンド適用時＋パーク＋アドオン)
     *       ＋ statSource 自身の解決済みステ(品質/ランダムロール込み)。</li>
     *   <li>ステ供給元なし(魔導書直接詠唱・剣にバインド等): 上記のメインハンド除く装備のみ。</li>
     * </ul>
     * メインハンドの武器ステを意図的に除外するのは、TF側 {@code aggregateExcludingMainhandWith} が
     * 構造的にメインハンドを集約しないため。杖はメインハンドにあるので、{@code statSource} として
     * 明示的に渡す必要がある(渡さないとどちらの経路にも入らず二重に落ちる — D6 の (3))。
     *
     * <p>フォールバック(挙動不変の安全策・fail-open): 詠唱者がオフライン/UUID未特定(儀式・タレット等の
     * 非プレイヤー詠唱)、TF未ロード、集計器/リゾルバ未初期化、または集計中の例外時は、
     * 従来どおり statSource のみのステ({@link #resolveCatalystStats})にフォールバックする
     * (statSource が null なら {@link AttackStats#plain(0)})。
     *
     * @param statSource {@link #resolveMagicStatSource} が選んだステ供給元。無ければ {@code null}
     */
    private static AttackStats resolveMagicAttackStats(UUID casterUuid, ItemStack statSource,
                                                       EnchantmentStatBridge.Bonuses enchantBonuses) {
        try {
            Player caster = casterUuid != null ? Bukkit.getPlayer(casterUuid) : null;
            if (caster == null) {
                return resolveCatalystStats(statSource);
            }
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return resolveCatalystStats(statSource);
            }
            WeaponAttackStatResolver resolver = tf.weaponAttackStats();
            PlayerStatAggregator aggregator = tf.playerStatAggregator();
            if (resolver == null || aggregator == null) {
                return resolveCatalystStats(statSource);
            }
            // statSource は「合算してから乗算」の内側に含める(その品自身の乗算レイヤ含む) — 近接パスで
            // メインハンド武器が乗算対象に含まれるのと対称にするため、TF側の集計APIへ渡す。
            Map<String, Double> attackerStats = new LinkedHashMap<>(
                    aggregator.aggregateExcludingMainhandWith(caster, statSource));
            // 2026-08-08: 破壊(Breach)の貫通を TF の貫通ステへ加算する。合算マップへ merge するのは
            // CombatListener(近接)と同じ位置 — bridgeStats の内側にある乗算レイヤを同じように通す
            // ため。AttackStats を後から組み直すと乗算の外側になり近接と値がズレる。
            // fail-open のフォールバック(詠唱者オフライン/TF未ロード)では集計自体を行わないので、
            // そちらはエンチャント分も乗らない(従来の安全側フォールバックと同じ扱い)。
            if (enchantBonuses != null && enchantBonuses.penetrationBonus() > 0) {
                attackerStats.merge(StatKeys.canonical("penetration"),
                        enchantBonuses.penetrationBonus(), Double::sum);
            }
            return resolver.bridgeStats(attackerStats);
        } catch (Throwable t) {
            return resolveCatalystStats(statSource);
        }
    }

    /**
     * 魔法の攻撃ステ供給元を決める(2026-07-31 D6)。
     *
     * <ol>
     *   <li>{@code castItem}(実際に右クリックして詠唱したアイテム)が {@code use-skill: ARS_MAGIC} を
     *       持つ、または {@code catalysts.yml} 登録品なら <b>castItem</b>。TFカタログの杖11本
     *       ({@code BLAZE_ROD#400002}〜{@code #400014} 等)がここで拾われる。</li>
     *   <li>そうでなければ従来どおり、{@code catalyst} が {@code catalysts.yml} 登録済みの触媒のときだけ
     *       <b>catalyst</b>。</li>
     *   <li>どちらでもなければ {@code null}(攻撃力も攻撃ステも乗らない)。</li>
     * </ol>
     *
     * <p><b>{@code use-skill} で絞る理由(オーケストレータ決定)</b>: {@code SpellBindListener#canBind}
     * は任意のアイテムにスペルをバインドできるため、castItem を無条件にステ源にすると
     * <b>ネザライトの剣やツルハシの近接ステが魔法に乗る</b>(＝近接の上位互換で魔法を撃てる)。
     *
     * <p><b>{@code bookItem} を渡してはいけない</b>: 非触媒バインド詠唱では {@code catalyst} 引数が
     * 魔導書 ItemStack になる。魔導書のステを魔法へ持ち込まないという現行仕様を維持するため、
     * ここは「登録済み触媒か」で必ず絞る(null 非 null では区別できない)。
     */
    private static ItemStack resolveMagicStatSource(ItemStack catalyst, ItemStack castItem) {
        boolean castItemAccepted = castItem != null
                && !castItem.getType().isAir()
                && acceptsAsMagicStatSource(castItem);
        return switch (MagicStatSourcePolicy.chooseStatSource(
                castItemAccepted, isRegisteredCatalyst(catalyst))) {
            case CAST_ITEM -> castItem;
            case CATALYST -> catalyst;
            case NONE -> null;
        };
    }

    /**
     * {@code item} を魔法のステ供給元として認めるか。TF の {@code use-skill}(=item-stats.yml が真源)が
     * {@code ARS_MAGIC} なら認める。{@code use-skill} が引けない品
     * ({@code catalysts.yml} の動的登録のみで item-stats.yml にエントリが無い触媒など)は、
     * 従来どおり {@code catalysts.yml} 登録の有無で認める(既存挙動の保全)。
     */
    private static boolean acceptsAsMagicStatSource(ItemStack item) {
        try {
            ItemUseGate gate = itemUseGate(item);
            if (gate != null && MagicStatSourcePolicy.isMagicUseSkill(gate.skill())) {
                return true;
            }
        } catch (Throwable t) {
            // TF未ロード/例外: 下の catalysts.yml 判定へ落とす(fail-open)。
        }
        return isRegisteredCatalyst(item);
    }

    /**
     * {@code combat/damage.yml} の {@code magical.attack-power-scale}(杖の攻撃力を魔法基礎ダメージへ
     * 加算するときの係数)。TF未ロード/例外時は既定 1.0(=仕様どおり100%加算)。
     */
    private static double magicalAttackPowerScale() {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null || tf.config() == null) {
                return MagicStatSourcePolicy.DEFAULT_ATTACK_POWER_SCALE;
            }
            return tf.config().combatDamage().magicalAttackPowerScale();
        } catch (Throwable t) {
            return MagicStatSourcePolicy.DEFAULT_ATTACK_POWER_SCALE;
        }
    }

    /**
     * {@code glyphs.yml} の {@code amplify.params.damage-rate-per-stack}（増幅1段あたりのダメージ乗率、
     * 既定10%）。ArsPaper未初期化/例外時は {@link MagicStatSourcePolicy#DEFAULT_AMPLIFY_DAMAGE_RATE}。
     */
    private static double amplifyDamageRatePerStack() {
        try {
            return ArsPaper.getInstance().getGlyphConfig()
                    .getParam("amplify", "damage-rate-per-stack", MagicStatSourcePolicy.DEFAULT_AMPLIFY_DAMAGE_RATE);
        } catch (Throwable t) {
            return MagicStatSourcePolicy.DEFAULT_AMPLIFY_DAMAGE_RATE;
        }
    }

    /**
     * {@code glyphs.yml} の {@code amplify.params.max-damage-level}（増幅ダメージ乗率の計算に使う
     * 段数の絶対値上限、既定20）。ArsPaper未初期化/例外時は
     * {@link MagicStatSourcePolicy#DEFAULT_MAX_AMPLIFY_DAMAGE_LEVEL}。
     */
    private static int maxAmplifyDamageLevel() {
        try {
            double value = ArsPaper.getInstance().getGlyphConfig().getParam(
                    "amplify", "max-damage-level",
                    (double) MagicStatSourcePolicy.DEFAULT_MAX_AMPLIFY_DAMAGE_LEVEL);
            return (int) value;
        } catch (Throwable t) {
            return MagicStatSourcePolicy.DEFAULT_MAX_AMPLIFY_DAMAGE_LEVEL;
        }
    }

    /**
     * 課題G5: TF の {@code glyph_damage_multiplier_bonus}(スキルツリー「害悪強化」等)を、
     * グリフIDごとに解決した倍率として返す。TF公開API {@link TrinityForge#glyphDamageMultiplier} が
     * 「どのグリフが対象か」({@code stats/glyph-damage-boost.yml})の真源を持つので、
     * フォーク側に対象リストを複製しない。
     *
     * <p>詠唱者がオフライン / グリフID不明 / TF未ロード / 例外時は {@code 1.0}(no-op)。
     */
    private static double glyphDamageMultiplier(UUID casterUuid, String glyphId) {
        if (casterUuid == null || glyphId == null || glyphId.isBlank()) {
            return 1.0;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return 1.0;
            }
            Player caster = Bukkit.getPlayer(casterUuid);
            if (caster == null) {
                return 1.0;
            }
            return tf.glyphDamageMultiplier(caster, glyphId);
        } catch (Throwable t) {
            return 1.0;
        }
    }

    /**
     * {@code item} が catalysts.yml に登録済みの触媒(material+CustomModelData照合)かどうかを返す。
     * {@code null}/ArsPaper未初期化/例外時は {@code false}(fail-open=非触媒扱い)。
     */
    private static boolean isRegisteredCatalyst(ItemStack item) {
        if (item == null) {
            return false;
        }
        try {
            return com.arspaper.ArsPaper.getInstance().getCatalystConfig().resolve(item) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    // 2026-07-31 F4: 「防御無視ダメージ(日輪/月輪の直接HP減少)へ杖の attack-power を加算する」入口
    // (旧 magicAttackPowerAddend) は撤去した。setHealth 経路は EntityDamageEvent すら発火しないため
    // 守備力・耐性・回避・トーテム・盾・TF の PvpDamagePolicy のいずれも通らず、そこへ伸びる値
    // (Lv100帯の杖で10000超)を足すと軽減不能の即死になっていた。攻撃力の加算は
    // magicalFinalDamage(=対称パイプラインを通る通常ダメージ経路)だけの機能である。
    // 詳細な線引きは SpellContext#defenseIgnoringDamage の javadoc に記録してある。

    /**
     * {@code item} の解決済み attack-power(品質/ランダムロール込み)。
     * {@code null} / TF未ロード / resolver未初期化 / 例外時は {@code 0.0}(fail-open)。
     */
    private static double itemAttackPower(ItemStack item) {
        if (item == null) {
            return 0.0;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return 0.0;
            }
            WeaponAttackStatResolver resolver = tf.weaponAttackStats();
            if (resolver == null) {
                return 0.0;
            }
            double power = resolver.attackPowerOf(item);
            return power > 0 ? power : 0.0;
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /**
     * 触媒 ItemStack から攻撃ステ（会心/貫通等）を導出する。
     *
     * <p>触媒が {@code null}、resolver未初期化、または導出失敗時は {@link AttackStats#plain(0)} を返す。
     * {@code forItem} は null/AIR/導出失敗を自身で plain(0) にフォールバックするが、
     * resolver 自体の未初期化（onEnable前）や例外に備えて多重に安全側へ倒す。
     */
    private static AttackStats resolveCatalystStats(ItemStack catalyst) {
        if (catalyst == null) {
            return AttackStats.plain(0);
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return AttackStats.plain(0);
            }
            WeaponAttackStatResolver resolver = tf.weaponAttackStats();
            if (resolver == null) {
                return AttackStats.plain(0);
            }
            AttackStats stats = resolver.forItem(catalyst);
            return stats != null ? stats : AttackStats.plain(0);
        } catch (Throwable t) {
            return AttackStats.plain(0);
        }
    }

    /**
     * 最終魔法ダメージを MAGIC ダメージソースで適用する（armor 二重軽減 / 物理再計算を回避）。
     * MAGIC ダメージソース未対応の環境では通常ダメージにフォールバックする。
     */
    public static void applyMagicDamage(LivingEntity victim, Player caster, double finalDamage) {
        if (finalDamage <= 0) {
            return;
        }
        // 課題2(レビュー修正): cause=MAGIC はTF/ArsPaperの魔法経路の専有ではない(バニラの負傷ポーション等も
        // 同じcauseで届く)ため、TrinityForge側のMagicResistanceFoldListenerはcauseだけでは
        // 「TFパイプラインを通った魔法ヒットか」を判別できない。MagicPipelineDamageで victim.damage(...)
        // 呼出しの直前後を明示的にマークし、この判別を可能にする(MobAbilityDamage/EliteCombatDelegationと
        // 同型の既存パターン)。DamageSource構築成功パス(cause=MAGIC)・失敗時のENTITY_ATTACKフォールバックの
        // 両方を囲ってよい — フォールバック側は既にCombatListener.FOLDED_MODIFIERSがRESISTANCEを0化済みの
        // 物理経路なので、二重に0を書くだけ(冪等・無害)。深度カウンタなので、victim.damage(...)がグリフの
        // オンヒット効果等で同期的に別の魔法ダメージを誘発しても、外側のマークは内側のclear()では剥がれない。
        com.trinityforge.combat.MagicPipelineDamage.mark();
        try {
            DamageSource source = DamageSource.builder(DamageType.MAGIC)
                .withCausingEntity(caster)
                .withDirectEntity(caster)
                .build();
            victim.damage(finalDamage, source);
        } catch (Throwable t) {
            // 古い API などで DamageSource が使えない場合のフォールバック。
            // ENTITY_ATTACK を発火させるため TrinityForge の CombatListener に再傍受され、
            // 物理パイプラインで二重処理される恐れがある。誤射を可視化するため SEVERE で記録する。
            Bukkit.getLogger().severe(
                "[ArsPaper] MAGIC DamageSource の適用に失敗し ENTITY_ATTACK フォールバックを使用しました。"
                    + "TrinityForge による二重処理の恐れがあります: " + t);
            try {
                victim.damage(finalDamage, caster);
            } catch (Throwable t2) {
                // フォールバック自体も失敗した場合は境界を越えて例外を伝播させない(fail-open)。
                Bukkit.getLogger().severe(
                    "[ArsPaper] MAGIC ダメージのフォールバック適用にも失敗しました: " + t2);
            }
        } finally {
            com.trinityforge.combat.MagicPipelineDamage.clear();
        }
    }

    /**
     * 対象を {@code amount} だけ回復する（#6: 負の最終魔法ダメージのヒール変換）。TrinityForge 物理側の
     * {@code CombatListener.healVictim} と対称: {@code defense.max-rate > 1} 等のクランプ設定で最終魔法
     * ダメージが負になったとき、ダメージの代わりに被弾者を回復させる。最大体力を超えず0未満にもならないよう
     * クランプする。{@code amount <= 0} や例外時は no-op（fail-open で既存挙動を壊さない）。
     */
    public static void healEntity(LivingEntity victim, double amount) {
        if (victim == null || amount <= 0) {
            return;
        }
        try {
            double newHealth = victim.getHealth() + amount;
            AttributeInstance maxHealth = victim.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                newHealth = Math.min(newHealth, maxHealth.getValue());
            }
            victim.setHealth(Math.max(0.0, newHealth));
        } catch (Throwable t) {
            // 回復失敗（無効なエンティティ等）は無視して既存挙動を維持する。
        }
    }

    // ============================================================
    // 厳選(ItemQuality + rollSeed) / soulbind の PDC 統一ヘルパ
    //
    // 真実は TrinityForge の ItemData PDC に置く（rollSeed + quality + bindType + owner）。
    // ステ値は不変ベイクせず、TrinityForge 側が rollSeed + quality + テーブルから live 導出する。
    // ArsPaper 側は rollSeed / quality / bindType / owner の書込みのみ担当する。
    // TrinityForge 未ロード時は ItemData クラスが解決できず Throwable になるため、
    // 全ヘルパは try/catch(Throwable) で no-op にフォールバックし、既存アイテム生成を壊さない。
    // ============================================================

    /**
     * 厳選用の rollSeed と品質(0-5)を ItemData 経由で PDC へ書き込む。
     * 既存 PDC（CUSTOM_ITEM_ID 等）には触れず追記のみ行う。
     *
     * @param meta     書込み対象の {@link ItemMeta}（{@code editMeta} 内で渡す想定）
     * @param rollSeed 生成毎にユニークな long
     * @param quality  品質。範囲外は {@link ItemData#MIN_QUALITY}/{@link ItemData#MAX_QUALITY} にクランプ
     */
    public static void writeItemRoll(ItemMeta meta, long rollSeed, int quality) {
        writeItemRoll(meta, rollSeed, quality, CraftRollMods.NONE);
    }

    /**
     * {@link #writeItemRoll(ItemMeta, long, int)} に加え、生成者のArs鍛冶「鍛冶ロール」パーク(段2)の
     * ロール補正を PDC へ焼き込むオーバーロード。補正が {@code NONE}(0)なら段2キーは書かれない
     * (setCraftRollMods 側で no-op)。TF未ロード / API不整合時は seed+quality もろとも no-op。
     *
     * @param rollMods 段2ロール補正(null は {@link CraftRollMods#NONE} 扱い)
     */
    public static void writeItemRoll(ItemMeta meta, long rollSeed, int quality, CraftRollMods rollMods) {
        if (meta == null) {
            return;
        }
        try {
            ItemData data = ItemData.of(meta);
            data.setRollSeed(rollSeed);
            int clamped = Math.max(ItemData.MIN_QUALITY, Math.min(ItemData.MAX_QUALITY, quality));
            data.setQuality(clamped);
            data.setCraftRollMods(rollMods == null ? CraftRollMods.NONE : rollMods);
        } catch (Throwable t) {
            // TrinityForge 未ロード: 厳選 PDC はスキップ（既存生成は維持）。
        }
    }

    /**
     * 触媒/魔導書の詠唱ゲート (use-requirements): {@code item} の use-skill / use-level 要件を
     * {@code caster} が満たさない場合、TF標準の拒否メッセージ(アクションバー用 Component)を返す。
     * 使用可・enforceオフ・要件なし・TF未ロード/例外時は {@code null}(fail-open)。
     * 近接/弓/ツール/防具ゲートと同じ規則・同じ文言(TF側 {@code UseRequirementService})。
     */
    public static net.kyori.adventure.text.Component useRequirementDenial(Player caster, ItemStack item) {
        if (caster == null || item == null || item.getType().isAir()) {
            return null;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return null;
            }
            com.trinityforge.progression.UseRequirementService gate = tf.useRequirementGate();
            if (gate == null) {
                return null;
            }
            return gate.denialFor(caster, item)
                    .map(com.trinityforge.progression.UseRequirementService::denialMessage)
                    .orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * アイテムの使用要件 (use-skill / use-level) を「値として」取り出す (2026-07-27 レシピGUI ソート用)。
     *
     * <p>{@link #useRequirementDenial(Player, ItemStack)} が「特定プレイヤーが使えるか」を返すのに対し、
     * こちらはプレイヤー非依存に要件そのものを返す。レシピ一覧の「種別順(スキル種別)」「使用可能レベル順」は
     * 閲覧者によって並びが変わってはいけないため、判定ではなく値が要る。
     *
     * @return 要件なし / TF未ロード / 例外時は {@code null}(fail-open: 呼び出し側は「要件なし」として扱う)
     */
    public static ItemUseGate itemUseGate(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null || tf.config() == null) {
                return null;
            }
            return com.trinityforge.progression.UseRequirementResolver
                    .resolve(item, tf.config().itemStats())
                    .map(r -> new ItemUseGate(r.skill(), r.level()))
                    .orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** {@link #itemUseGate(ItemStack)} の戻り値。{@code skill} は空文字になり得る。 */
    public record ItemUseGate(String skill, int level) {
    }

    /**
     * True when {@code actor} may use {@code item} under TrinityForge ownership rules.
     * Fail-open when TF is absent or the item has no ownership bind.
     */
    public static boolean mayActorUseItem(Player actor, ItemStack item) {
        if (actor == null || item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return true;
        }
        try {
            ItemData data = ItemData.of(item.getItemMeta());
            return OwnerBindPolicy.mayUse(data.bindType(), data.owner(), actor.getUniqueId());
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Builds an unstamped TrinityForge catalog item (identity only) for ritual craft results.
     * Returns {@code null} when TF is absent or the catalog id is unknown.
     */
    public static ItemStack createCatalogIdentity(String catalogId) {
        if (catalogId == null || catalogId.isBlank()) {
            return null;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null || tf.itemFactory() == null) {
                return null;
            }
            return tf.config().itemCatalog().template(catalogId)
                    .map(t -> tf.itemFactory().createIdentityOnly(t))
                    .orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * TrinityForge の素材互換リスト {@code list:<id>} を構成する Material 集合を返す。
     * TF未ロード / 未定義idのときは空集合({@link com.trinityforge.stats.MaterialLists#resolve}に準拠)。
     *
     * <p>ArsPaper の作業台レシピ素材({@code items.yml})や儀式素材で {@code list:} トークンを
     * 解決するために使う。TF側の {@code CatalogRecipeRegistrar}/{@code RecipeIngredient} と同じ
     * スナップショットを参照するため、TFとArsPaperで互換判定が一致する。
     */
    public static Set<Material> resolveMaterialList(String listId) {
        if (listId == null || listId.isBlank()) {
            return Collections.emptySet();
        }
        try {
            return com.trinityforge.stats.MaterialLists.resolve(listId);
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    /**
     * {@code list:<id>} に含まれる {@code custom:<id>}(TFカタログ/外部登録アイテム)メンバのidを返す。
     * TF未ロード / 未定義idのときは空集合。
     */
    public static Set<String> resolveMaterialListCustomIds(String listId) {
        if (listId == null || listId.isBlank()) {
            return Collections.emptySet();
        }
        try {
            return com.trinityforge.stats.MaterialLists.resolveCustomIds(listId);
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    /** {@code list:<id>} のエディタ表示用ラベル。未定義/TF未ロードなら id をそのまま返す。 */
    public static String materialListLabel(String listId) {
        if (listId == null || listId.isBlank()) {
            return listId;
        }
        try {
            return com.trinityforge.stats.MaterialLists.labelOf(listId);
        } catch (Throwable t) {
            return listId;
        }
    }

    /**
     * 「スレッド枠拡張」儀式用: {@code coreItem} に儀式由来の {@code thread-slots} 枠を+1して返す
     * ({@link com.trinityforge.stats.ItemFactory#expandRitualThreadSlot}への委譲)。累計付与数が
     * {@code maxSlots}(この儀式の {@code max-slots} パラメータ)に既に到達している、カテゴリ上限に
     * 到達している、TF未ロード等の場合は {@code null}(=付与不可、儀式側で失敗させて素材を消費しない)。
     */
    public static ItemStack expandThreadSlot(ItemStack coreItem, int maxSlots) {
        if (coreItem == null || coreItem.getType().isAir()) {
            return null;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null || tf.itemFactory() == null) {
                return null;
            }
            return tf.itemFactory().expandRitualThreadSlot(coreItem, maxSlots).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * {@link #expandThreadSlot(ItemStack, int)} が成功するかどうかの判定のみ（消費前 {@code validate} 用）。
     * 実行はコアアイテムのコピーに対して行われ副作用が無いため、そのまま可否判定に転用できる。
     */
    public static boolean canExpandThreadSlot(ItemStack coreItem, int maxSlots) {
        return expandThreadSlot(coreItem, maxSlots) != null;
    }

    /**
     * Finalizes a TrinityForge catalog ritual craft result: Ars smithing quality/EXP for tiered
     * equipment or Ars registry quality targets (materials stay stackable), and SOULBOUND owner stamp
     * to the crafter when applicable.
     */
    public static void finalizeCatalogRitualResult(ItemStack item, Player crafter) {
        finalizeCatalogRitualResult(item, crafter, java.util.List.of());
    }

    /**
     * {@link #finalizeCatalogRitualResult(ItemStack, Player)} に消費素材を伝える版 (U1/N6)。
     * {@code materialTokens} は TrinityForge の {@code smithing.exp-per-material} と同じ語彙
     * ({@code IRON_INGOT} / {@code custom:<id>})で、素材1個につき1要素。
     */
    public static void finalizeCatalogRitualResult(ItemStack item, Player crafter,
                                                   Collection<String> materialTokens) {
        finalizeCatalogRitualResult(item, crafter, materialTokens, 0);
    }

    /**
     * {@link #finalizeCatalogRitualResult(ItemStack, Player, Collection)} に<b>消費ソース量</b>を
     * 伝える版 (2026-08-04)。TrinityForge 側が {@code ars-smithing.exp-per-source} を掛けて
     * 儀式EXPへ足し込む。0 を渡せば従来と同一挙動。
     */
    public static void finalizeCatalogRitualResult(ItemStack item, Player crafter,
                                                   Collection<String> materialTokens,
                                                   int consumedSource) {
        if (item == null || crafter == null || item.getType().isAir()) {
            return;
        }
        try {
            if (MaterialTier.of(item.getType()).isEquipment() || isArsQualityStamped(item)) {
                finalizeArsSmithingResult(item, crafter, materialTokens, consumedSource);
            } else {
                // 2026-08-03 実サーバ報告「Ars鍛冶の経験値が入らない」の修正: 品質を刻む対象では
                // ない(装備でも刻印済みArsアイテムでもない) tfcatalog 儀式結果でも、儀式を行った
                // 労力ぶんのEXPだけは常に付与する。品質とEXPを同じ門に相乗りさせていたのが誤り
                // (下の grantArsSmithingExpOnly javadoc 参照)。
                grantArsSmithingExpOnly(item, crafter, materialTokens, consumedSource);
            }
            if (!item.hasItemMeta()) {
                return;
            }
            item.editMeta(meta -> {
                ItemData data = ItemData.of(meta);
                if (data.owner().isPresent()) {
                    return;
                }
                if (data.bindType().map(BindType::autoStampsOwner).orElse(false)) {
                    data.setOwner(crafter.getUniqueId());
                }
            });
        } catch (Throwable t) {
            // TF API mismatch: leave the identity item as-is.
        }
    }

    /**
     * Completes one Ars smithing production action <b>that is eligible for a quality stamp</b>
     * (equipment, or a native Ars item that opts into quality via {@code isQualityStamped()}).
     *
     * <p><b>2026-08-03 訂正</b>: このメソッドの旧javadocは "Quality and EXP deliberately share
     * this boundary" と書いていたが、これは誤り（実サーバ報告「Ars鍛冶の経験値が入らない」の真因）
     * だった。品質を刻めない(＝刻む意味が無い)儀式結果——ソースジェムの系譜・エンチャント本・
     * ウェイストーン・テレポートコンパス等——を呼び出し元がこのメソッドの手前で弾いていたため、
     * それらは<b>一度もEXP付与へ到達しなかった</b>。品質は「刻めるものにだけ刻む」が正しい線引きで、
     * EXPは「儀式を行った労力」に対して払うものなので<b>門を分ける必要がある</b>。品質を刻まない
     * 結果には代わりに {@link #grantArsSmithingExpOnly} を使うこと(呼び出し元を参照)。
     */
    public static void finalizeArsSmithingResult(ItemStack item, Player crafter) {
        finalizeArsSmithingResult(item, crafter, java.util.List.of());
    }

    /**
     * {@link #finalizeArsSmithingResult(ItemStack, Player)} に消費素材を伝える版 (U1/N6)。
     *
     * <p>儀式EXPはこれまで定額({@code ars-smithing.exp-per-craft})で、素材の重さを一切見ていなかった。
     * 消費素材のトークンを渡すと TrinityForge 側が作業台と<b>同じ</b>
     * {@code smithing.exp-per-material} 表で合計する。表から1つも引けなければ従来どおり定額に戻るので、
     * 表が未整備のサーバで儀式EXPが消える回帰にはならない。
     */
    public static void finalizeArsSmithingResult(ItemStack item, Player crafter,
                                                 Collection<String> materialTokens) {
        finalizeArsSmithingResult(item, crafter, materialTokens, 0);
    }

    /**
     * {@link #finalizeArsSmithingResult(ItemStack, Player, Collection)} に<b>消費ソース量</b>を
     * 伝える版 (2026-08-04)。0 を渡せば従来と同一挙動。
     */
    public static void finalizeArsSmithingResult(ItemStack item, Player crafter,
                                                 Collection<String> materialTokens,
                                                 int consumedSource) {
        if (item == null || crafter == null || item.getType().isAir()) {
            return;
        }
        stampCraftedQuality(item, crafter);
        grantArsSmithingExpOnly(item, crafter, materialTokens, consumedSource);
    }

    /**
     * 品質を刻まない(刻む意味が無い)儀式結果にも、儀式を行った労力ぶんのEXPだけを付与する
     * (2026-08-03 実サーバ報告「Ars鍛冶の経験値が入らない」の修正)。
     *
     * <p>ソースジェムの系譜(source_gem→…→singularity_proof)・エンチャント本(mana_regen/boost/
     * share/soulbound)・ウェイストーン・テレポートコンパス等が対象。いずれも
     * {@code RitualManager}/{@code RitualRecipe} に分解・逆儀式の概念が無い(grep 確認済み)ため、
     * ここでEXPを開いても「作って分解して作り直す」無限EXP経路にはならない
     * (圧縮素材の compress/decompress は<b>別系統</b>のバニラ作業台レシピ({@code RecipeManager}の
     * {@code reversible: true})であり、そちらは {@code CraftQualityListener} 側の
     * 「完成品に使用可能レベルが無ければEXPを出さない」ゲートで既に保護されている——本メソッドとは
     * 無関係)。
     *
     * <p>失敗を完全に握り潰さない — 従来 {@code catch (Throwable) {}} で完全に無言だったため、
     * TrinityForge 側の API 不整合(フォークの {@code libs/TrinityForge.jar} が古い等)が起きても
     * 誰にも気付けなかった。ここでは最低限の警告ログを残す。
     */
    public static void grantArsSmithingExpOnly(ItemStack item, Player crafter,
                                                Collection<String> materialTokens) {
        grantArsSmithingExpOnly(item, crafter, materialTokens, 0);
    }

    /**
     * {@link #grantArsSmithingExpOnly(ItemStack, Player, Collection)} に<b>消費ソース量</b>を
     * 伝える版 (2026-08-04)。
     *
     * <p><b>ここに渡すのは「本当に消えたソース量」でなければならない</b>。予約直後の値を渡すと、
     * 中断・素材差し替え・効果検証失敗などで {@code refundSource} がジャーへ返した分まで
     * EXPになり、儀式をわざと失敗させ続けるだけでEXPを稼げる経路になる
     * ({@code recordSourceSpent} が予約直後ではなく全返還経路の通過後に呼ばれているのと同じ理由)。
     */
    public static void grantArsSmithingExpOnly(ItemStack item, Player crafter,
                                                Collection<String> materialTokens,
                                                int consumedSource) {
        if (item == null || crafter == null || item.getType().isAir()) {
            return;
        }
        try {
            ArsProgressionBridge.grantSmithingCraftExp(ArsPaper.getInstance(), crafter, item,
                    materialTokens == null ? java.util.List.of() : materialTokens,
                    consumedSource);
        } catch (Throwable t) {
            ArsPaper.getInstance().getLogger().warning(
                    "Ars鍛冶(儀式)EXP付与に失敗しました(item=" + item.getType()
                            + ", crafter=" + crafter.getName() + "): " + t);
        }
    }

    private static boolean isArsQualityStamped(ItemStack item) {
        if (item == null || !item.hasItemMeta() || ArsPaper.getInstance() == null) {
            return false;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(
                com.arspaper.item.ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
        return id != null && ArsPaper.getInstance().getItemRegistry().get(id)
                .map(BaseCustomItem::isQualityStamped)
                .orElse(false);
    }

    /**
     * Re-pushes TrinityForge catalog ritual recipes into ArsPaper.
     * Called on Ars enable (TF enables first and cannot see Ars classes) and after {@code /ars reload}.
     * Invokes {@link CatalogRitualRegistrar} directly — TF→Ars reflection fails without join-classpath.
     */
    public static void repushCatalogRituals() {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return;
            }
            CatalogRitualRegistrar.registerTrinityForgeCatalog(tf, tf.config().itemCatalog().all());
        } catch (Throwable t) {
            // TF absent / not ready / catalog API mismatch.
        }
    }

    /**
     * TrinityForge catalog 内の魔導書アップグレード儀式（core-itemが {@code custom:spell_book_*} の
     * ritual recipe を持つ {@code spell_book_*} エントリ）の数を返す。spellbooks.yml の
     * upgrade-from 整合性検証用。TF未ロード/例外時は0。
     */
    public static long countCatalogSpellBookUpgradeRituals() {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return 0;
            }
            return tf.config().itemCatalog().all().entrySet().stream()
                    .filter(e -> e.getKey().startsWith("spell_book_"))
                    .flatMap(e -> e.getValue().recipes().stream())
                    .filter(com.trinityforge.stats.RecipeSpec::isRitual)
                    .filter(spec -> spec.coreItem() != null
                            && spec.coreItem().startsWith("custom:spell_book_"))
                    .count();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * TFカタログ作業台レシピ({@code trinityforge:catalog_*})の素材トークンを返す。
     * shaped はシンボル→トークン、shapeless は "1","2",… →トークン。トークンは
     * {@code custom:<catalogId>} または小文字material名。レシピブラウザの表示用
     * （MaterialChoiceでは custom: 素材を表現できないため、パース済みspecから取る）。
     * TF未ロード/該当キーなし/例外時は null。
     */
    public static java.util.Map<String, String> catalogWorkbenchIngredients(org.bukkit.NamespacedKey key) {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null || tf.catalogRecipeRegistrar() == null) {
                return null;
            }
            var registered = tf.catalogRecipeRegistrar().registered(key).orElse(null);
            if (registered == null) {
                return null;
            }
            var spec = registered.spec();
            java.util.Map<String, String> tokens = new java.util.LinkedHashMap<>();
            if (spec.type() == com.trinityforge.stats.RecipeSpec.Type.SHAPED) {
                spec.shapedIngredients().forEach(
                        (symbol, ing) -> tokens.put(String.valueOf(symbol), ing.configValue()));
            } else {
                int i = 1;
                for (var ing : spec.shapelessIngredients()) {
                    tokens.put(String.valueOf(i++), ing.configValue());
                }
            }
            return tokens;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * TFカタログ作業台レシピ(shaped)のパターン行を、TF側のパース済みspec(=items/catalog.yml
     * の元記号)から返す。Bukkitの {@code ShapedRecipe#getShape()} はサーバ再構築時に
     * パターン文字を別記号へ振り直すため、{@link #catalogWorkbenchIngredients}(元記号キー)と
     * 突き合わせると全スロットが不一致(null)になり素材が一切描画されない。表示側は本メソッドの
     * shapeを {@code catalogWorkbenchIngredients} とセットで用いること。shapeless/未ロード/
     * 該当なし/例外時は null。
     */
    public static java.util.List<String> catalogWorkbenchShape(org.bukkit.NamespacedKey key) {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null || tf.catalogRecipeRegistrar() == null) {
                return null;
            }
            var registered = tf.catalogRecipeRegistrar().registered(key).orElse(null);
            if (registered == null) {
                return null;
            }
            var spec = registered.spec();
            if (spec.type() != com.trinityforge.stats.RecipeSpec.Type.SHAPED) {
                return null;
            }
            return java.util.List.copyOf(spec.shape());
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * TFが現在登録しているcatalog作業台レシピのキー一覧。
     * Bukkit全体のrecipeIteratorに依存せず、/ars recipes がTFの管理対象を確実に列挙するために使う。
     */
    public static java.util.List<org.bukkit.NamespacedKey> catalogWorkbenchRecipeKeys() {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null || tf.catalogRecipeRegistrar() == null) {
                return java.util.List.of();
            }
            return tf.catalogRecipeRegistrar().allRegistered().stream()
                    .map(com.trinityforge.stats.CatalogRecipeRegistrar.RegisteredRecipe::key)
                    .toList();
        } catch (Throwable t) {
            return java.util.List.of();
        }
    }

    /**
     * TFカタログ設定を反映したレシピブラウザ表示用ItemStackを返す。
     * 品質で変動するstats loreは固定せず、カタログの固定identityとflavor loreだけを反映する。
     */
    public static ItemStack catalogWorkbenchDisplayItem(org.bukkit.NamespacedKey key) {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null || tf.catalogRecipeRegistrar() == null || tf.itemFactory() == null) {
                return null;
            }
            var registered = tf.catalogRecipeRegistrar().registered(key).orElse(null);
            if (registered == null) {
                return null;
            }
            var template = registered.template();
            ItemStack display = tf.itemFactory().createIdentityOnly(template);
            if (!template.lore().isEmpty()) {
                var miniMessage = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
                display.editMeta(meta -> meta.lore(template.lore().stream()
                        .map(line -> miniMessage.deserialize(line).decorationIfAbsent(
                                net.kyori.adventure.text.format.TextDecoration.ITALIC,
                                net.kyori.adventure.text.format.TextDecoration.State.FALSE))
                        .toList()));
            }
            return display;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * TrinityForge の {@code items/catalog.yml} で {@code draft: true}(準備中)と宣言されたIDか。
     *
     * <p><b>なぜ Ars 側から問い合わせるのか</b>: 準備中アイテムは「カタログには定義があるが
     * ゲーム内では一切入手できない」状態が仕様で、TF 側は
     * {@code CrossPluginItemResolver#create} 1箇所でそれを保証している。しかし Ars は
     * <b>同じ id の実体を自前のレジストリで持っている</b>ため、Ars のルートテーブルや
     * 儀式から配ると TF のゲートを一度も通らない。判定そのものを増やさず TF の1箇所へ
     * 問い合わせることで、{@code draft:} フラグを唯一のスイッチに保つ。
     *
     * <p>TF 未ロード / API 不一致のときは {@code false}(＝従来どおり配る)。準備中判定が取れない
     * ことを理由にルートを止めると、TF 抜きで動かす構成でチェストが空になる。
     */
    public static boolean isCatalogDraft(String catalogId) {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null || catalogId == null || catalogId.isBlank()) {
                return false;
            }
            return tf.config().itemCatalog().isDraft(catalogId);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * TFカタログエントリの表示名(MiniMessageタグ除去済みプレーン文字列)を返す。
     * レシピブラウザで Ars レジストリ未登録の catalog 素材(圧縮ブロック等)を表示するため。
     * TF未ロード/未知id/表示名未設定時は null。
     */
    public static String catalogDisplayNamePlain(String catalogId) {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null || catalogId == null) {
                return null;
            }
            return tf.config().itemCatalog().template(catalogId)
                    .map(t -> t.displayName())
                    .filter(n -> n != null && !n.isBlank())
                    // MiniMessage を正規表現で削るのではなく実パーサへ通す(書式解釈は DisplayText 1本)。
                    .map(com.arspaper.util.DisplayText::plain)
                    .filter(n -> !n.isBlank())
                    .orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * TFのカタログ作業台レシピを再登録させる。TFはArsより先にenableするため、初回登録時点では
     * Ars製アイテム(魔導書等)を結果に据えられずTF識別ビルドにフォールバックしている。Ars enable後
     * (と /ars reload 後)に呼ぶことで、結果スタックがArs実体(機能PDC付き)へ差し替わる。
     */
    public static void refreshCatalogRecipes() {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf != null) {
                tf.refreshCatalogRecipes();
            }
        } catch (Throwable t) {
            // TF absent / not ready / API mismatch.
        }
    }

    /**
     * 儀式クラフトの完成品に、生成者のArs鍛冶スキル駆動品質を刻印する(craft-quality一本化)。
     * バニラ卓クラフトは TrinityForge の CraftQualityListener が別途刻印するため、この経路は
     * CraftItemEvent を発火しない {@code RitualManager} 専用。刻印対象は {@code isQualityStamped()==true}
     * の完成品(装備/触媒)のみ — 素材への一意 rollSeed 刻印はスタック不能化を招くため呼び出し側で除外する。
     *
     * <p>品質は {@link CraftQualityService#rollArsSmithingQuality}(正規分布ドロー)で算出し、
     * ユニークな rollSeed と共に {@link #writeItemRoll} で PDC へ書き込む。
     * TF未ロード / サービス未初期化 / 例外時は no-op(品質0のまま=baseline)。
     *
     * @param item    刻印対象のクラフト成果物 ItemStack
     * @param crafter クラフトを行ったプレイヤー(スキルレベル参照元)
     */
    public static void stampCraftedQuality(ItemStack item, Player crafter) {
        if (item == null || crafter == null || item.getType().isAir()) {
            return;
        }
        try {
            // 「品質はまだ決めない」マーカーだけを刻む(2026-08-04 仕様変更)。実際の品質ロールと
            // lore/属性のフル再組み立ては TF 側 PickupQualityListener が「最初にインベントリへ
            // 入ったプレイヤー」のステータスで行う。crafter は互換のため受け取るが参照しない
            // (儀式結果は台座にドロップされるので、儀式実行者と回収者は一致しない)。
            item.editMeta(TrinityForgeBridge::markPendingCraftQuality);
        } catch (Throwable t) {
            // TF未ロード / API不整合: マーカーはスキップ(品質0のまま、既存生成は維持)。
        }
    }

    /**
     * TF {@link ItemData} の「品質未決定」マーカーを刻む。TF未ロード / 旧 TrinityForge jar
     * (マーカー未対応)では {@link NoSuchMethodError} を拾って no-op(従来どおり品質0のまま)。
     */
    private static void markPendingCraftQuality(ItemMeta meta) {
        if (meta == null) {
            return;
        }
        try {
            ItemData.of(meta).markPendingCraftQuality();
        } catch (Throwable t) {
            // TF未ロード / 旧jar: マーカー無しで進む。
        }
    }

    /**
     * 品質基準値 (item-stats {@code quality-mode-offset}) を適用する新TF APIで品質をロールする。
     * 旧TrinityForge jar (オフセット未対応) 下では {@link NoSuchMethodError} を拾い従来ロールへ
     * フォールバックする。
     */
    private static int rollQualityWithOffset(CraftQualityService svc, Player crafter, ItemStack item) {
        try {
            return svc.rollArsSmithingQuality(crafter, item);
        } catch (NoSuchMethodError legacyTf) {
            return svc.rollArsSmithingQuality(crafter);
        }
    }

    /**
     * スレッド厳選（TF {@code stats/item-stats.yml} の {@code items.<MATERIAL#CMD>.per-quality}/
     * {@code random}）向け: TF のクラフト品質(0-100目安)を、既存の Ars鍛冶品質ロール
     * ({@link CraftQualityService#rollArsSmithingQuality})でそのまま解決する。
     *
     * <p>スレッドは {@code isQualityStamped()==false}（装備ではなく素材扱い）なので
     * {@link #finalizeCatalogRitualResult} の通常経路では TF の ItemData 品質 PDC は刻印されない
     * （儀式クラフトの通常成果物と混ざらない）。この値は
     * {@link #stampThreadIdentity(ItemStack, Player)} が TF の ItemData 品質 PDC へ
     * 直接書き込む（2026-08-03、旧: Ars 独自 PDC で厳選結果そのものを焼き込んでいた設計から、
     * 武器と同じ TF ItemData(rollSeed+quality)駆動へ統合済み）。
     *
     * <p>TF未ロード / サービス未初期化 / {@code crafter == null}（品質情報が取れない経路 ──
     * ルートチェスト/ダンジョンドロップ/管理コマンド付与等） / 例外時は必ず {@code 0} を返す
     * （＝幅を広げない、従来どおりの抽選）。この経路が失敗しても厳選そのものは止めない(fail-open)。
     */
    public static int currentArsSmithingQuality(Player crafter, ItemStack itemContext) {
        if (crafter == null) {
            return 0;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return 0;
            }
            CraftQualityService svc = tf.craftQualityService();
            if (svc == null) {
                return 0;
            }
            return rollQualityWithOffset(svc, crafter, itemContext);
        } catch (Throwable t) {
            return 0;
        }
    }

    // ============================================================
    // スレッド個体差(rollSeed + quality)の TrinityForge 標準経路への統合 (2026-08-03)
    //
    // 旧実装は Ars 独自の共有抽選プール(item-stats.yml の random-roll-pools:、さらにその前は
    // thread-rolls.yml)へ委譲していたが、TF側はその専用の抽選プール機構自体を削除し「武器と全く同じ
    // fixed/per-quality/random/確率付与の導出(WeaponAttackStatResolver#resolveItemStats(material,
    // cmd, quality, rollSeed))」へ一本化した。stats/item-stats.yml の items: に thread_* が
    // 1件ずつ個別定義されているのはこのため。ArsPaper 側はもう「厳選テーブルを引く」役ではなく、
    // 「TFのrollSeed/qualityをアイテムへ刻む・読む・その値でTFの通常導出を呼ぶ」だけの薄い
    // 呼び出し元になる。ステ値そのものはアイテムへベイクしない(rollSeed+qualityのみPDC保存、TF
    // ItemData と同じ設計 ── item-stats.yml を変えれば既存スレッドの数値も追随する)。
    // ============================================================

    /**
     * {@link #stampThreadIdentity(ItemStack, Player)}/{@link #rerollThreadIdentity(ItemStack)}/
     * {@link #readThreadIdentity(ItemStack)} の戻り値。TF {@code ItemData} が持つ
     * rollSeed(乱数)とquality(0-100目安)の組。
     */
    public record ThreadIdentity(long rollSeed, int quality) {
        /** 未刻印/TF未ロード時のfail-open値(=従来どおりの幅、個体差なし)。 */
        public static final ThreadIdentity NONE = new ThreadIdentity(0L, 0);
    }

    /**
     * 効果付きスレッドを1個新規生成した瞬間に、TFのrollSeed(新規発番)とクラフト品質
     * ({@link #currentArsSmithingQuality})を刻む。{@link com.trinityforge.stats.ItemFactory#stamp}
     * ── 武器/触媒が品質を刻まれるのと同じ入口 ── へ委譲する。
     *
     * <p>{@code stamp} は本来 lore/vanilla属性も再組み立てするが、スレッドのステキー
     * (bleed-damage 等)は {@code AttributeProjection} に一切マップされていない
     * (2026-08-03 確認: stat mapped キーは knockback_resistance/armor_defense_rate/max_health/
     * move_speed/attack_speed/attack_speed_bonus/attack_reach の7種のみで、スレッド40件は
     * どれも使わない)ため、実害は無い。lore は呼び出し側({@code ThreadItem})がこの直後に
     * 独自の lore で必ず上書きする前提。
     *
     * @param crafter 生成者。{@code null}(生成者不明経路: ルートチェスト/ダンジョンドロップ/管理
     *                コマンド付与等)なら品質は0(=従来どおりの幅)だが rollSeed は発番する。
     * @return 刻んだ (rollSeed, quality)。TF未ロード/ItemFactory未初期化/例外時は
     *         {@link Optional#empty()}(=何も刻まない、fail-open)。
     */
    public static Optional<ThreadIdentity> stampThreadIdentity(ItemStack item, Player crafter) {
        if (item == null || item.getType().isAir()) {
            return Optional.empty();
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null || tf.itemFactory() == null) {
                return Optional.empty();
            }
            int quality = currentArsSmithingQuality(crafter, item);
            long rollSeed = UUID.randomUUID().getMostSignificantBits();
            tf.itemFactory().stamp(item, rollSeed, quality);
            return Optional.of(new ThreadIdentity(rollSeed, quality));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /**
     * 振り直し儀式向け: 既存スレッドの quality を据え置いたまま rollSeed だけを新規発番して書き直す。
     * {@link #writeItemRoll(ItemMeta, long, int)}(PDCのみ書く軽量経路)を使う ──
     * 呼び出し元({@code ThreadRerollRitualEffect})が直後に自前で lore を作り直すため、
     * {@link #stampThreadIdentity} のようなフル再組み立ては不要かつ無駄働きになる。
     *
     * @return 書き直した (rollSeed, quality)。対象が空/PDC無し/TF未ロード/例外時は
     *         {@link Optional#empty()}(=書き込まない、fail-open)。
     */
    public static Optional<ThreadIdentity> rerollThreadIdentity(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        try {
            int quality = readThreadIdentity(item).quality();
            long rollSeed = UUID.randomUUID().getMostSignificantBits();
            item.editMeta(meta -> writeItemRoll(meta, rollSeed, quality));
            return Optional.of(new ThreadIdentity(rollSeed, quality));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /**
     * アイテムに刻まれた (rollSeed, quality) を読む。未刻印/PDC無し/TF未ロード/例外時は
     * {@link ThreadIdentity#NONE}(rollSeed=0, quality=0)。
     *
     * <p><b>旧形式(装備側 {@code THREAD_SLOT_ROLLS} に入っていた
     * {@code "<rarityId>|main=値|sub=値;..."})はこのメソッドの対象外</b> ──
     * このメソッドはあくまで「スレッド単体アイテム」の TF {@code ItemData} PDC を読む。
     * 装備の装着済みスレッド枠(旧形式が残り得る場所)は {@code ArmorManaListener} 側の
     * 専用デコーダが担当し、パース失敗時は rollSeed=0/quality=0 にフォールバックする
     * (フェイルオープン: 個体差が消えるだけで、ステ自体が消えたり例外で止まったりはしない)。
     */
    public static ThreadIdentity readThreadIdentity(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return ThreadIdentity.NONE;
        }
        try {
            ItemData data = ItemData.of(item.getItemMeta());
            return new ThreadIdentity(data.rollSeed().orElse(0L), data.quality());
        } catch (Throwable t) {
            return ThreadIdentity.NONE;
        }
    }

    /**
     * 装着スレッド1個ぶんの導出済みステ(fixed + per-quality + random + 確率付与)を、
     * 指定の quality/rollSeed で解決する。
     * {@link WeaponAttackStatResolver#resolveItemStats(Material, Integer, int, long)}
     * (武器がステを得るのとまったく同じ導出経路)への薄い委譲。
     *
     * <p>TF未ロード / resolver未初期化 / 例外時は空の可変Mapを返す(fail-open)。
     */
    public static Map<String, Double> resolveThreadStats(Material material, Integer cmd,
                                                          int quality, long rollSeed) {
        if (material == null) {
            return new LinkedHashMap<>();
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return new LinkedHashMap<>();
            }
            WeaponAttackStatResolver resolver = tf.weaponAttackStats();
            if (resolver == null) {
                return new LinkedHashMap<>();
            }
            return resolver.resolveItemStats(material, cmd, quality, rollSeed);
        } catch (Throwable t) {
            return new LinkedHashMap<>();
        }
    }

    // 【復活させないこと】かつてここに threadStatDisplay(表示名+整形済み値を1件返す)があった。
    // 呼び出し側が "label + \" \" + value" を自前で連結する形になり、
    // (1) 引き当てに失敗するとステータスidを素で出す (2) 色/アイコン/テンプレートが TF 装備と別物、
    // という2つの食い違いを同時に生んだ(2026-08-04 のスレッド lore の実害)。
    // 表示は threadStatLore(=TF の LoreComposer#statLines)一本から作る。

    /**
     * 任意のステマップを <b>TF 装備とまったく同じ体裁</b>の lore 行へ組む
     * ({@code stats/lore.yml} の {@code layout.line-template} / 表示名 / アイコン / 桁数 / 単位 / 色 /
     * カテゴリ順 / {@code hide-when-zero} を全部通す)。スレッドの厳選値表示とチャット出力の唯一の入口。
     *
     * <p><b>自前で連結しないこと</b>: フォークが `label + " " + value` を組んでいた旧経路は
     * 表示名の引き当てに失敗するとステータスidを素で出し、成功しても色/アイコン/テンプレートが
     * TF 装備と別物になっていた({@link #threadStatDisplay} の javadoc 参照)。
     * TF 未ロード / 例外時は空リスト(呼び出し側は「行が無い」として扱えばよい)。
     */
    public static java.util.List<net.kyori.adventure.text.Component> threadStatLore(
            Map<String, Double> stats) {
        if (stats == null || stats.isEmpty()) {
            return java.util.List.of();
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null || tf.config() == null || tf.config().lore() == null
                    || tf.loreComposer() == null) {
                return java.util.List.of();
            }
            Map<String, Double> finite = new LinkedHashMap<>();
            stats.forEach((key, value) -> {
                if (key != null && value != null && Double.isFinite(value) && value != 0.0) {
                    finite.put(key, value);
                }
            });
            if (finite.isEmpty()) {
                return java.util.List.of();
            }
            // statLines(=区切り線/header/footer なし)を使う。compose を使うとカテゴリごとに
            // 「そのときの最長行」で幅が決まる ==== が入り、返却/リロールで旧行を内容一致で
            // 消している経路(restoreRoll / ThreadRerollRitualEffect)に古い区切り線が溜まる。
            return tf.loreComposer().statLines(finite,
                    tf.config().lore().displayTable(), tf.config().lore().layout());
        } catch (Throwable t) {
            return java.util.List.of();
        }
    }

    /**
     * <b>装備とまったく同じ体裁</b>のステ lore ブロック(品質行【名匠】…pt / カテゴリ区切り線 /
     * ロール色 / 確率付与色 / 乗算行つき)を TF の装備経路そのものから組む
     * ({@code ItemFactory#statLoreBlock} → {@code ItemAssembler#statLoreBlock})。
     *
     * <p>2026-08-05 の要望「スレッドに表記するステータスの lore の体裁とフォントを通常の装備と
     * 同じにしてほしい」への入口。{@link #threadStatLore} は<b>区切り線と品質行を落とし全行を
     * fixed 色で描く</b>ので装備とは体裁が揃わない ── あちらは「他のアイテムの lore へ差し込む行」
     * 専用(幅可変の区切り線が差し込み先に溜まる事故を避けるため)で、用途が違う。
     *
     * <p><b>この結果を「前回の行を内容一致で消してから足す」方式で使ってはいけない</b>:
     * 区切り線の幅は最長行で決まるので、値の桁が変わると古い線が消えずに溜まる。
     * スレッドの lore は必ず<b>まるごと組み直す</b>こと({@code ThreadItem#fullLore})。
     *
     * <p>TF 未ロード / 例外時は空リスト(呼び出し側は「行が無い」として扱えばよい)。
     */
    public static java.util.List<net.kyori.adventure.text.Component> threadEquipmentStyleLore(
            Material material, Integer cmd, int quality, long rollSeed) {
        if (material == null) {
            return java.util.List.of();
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null || tf.itemFactory() == null) {
                return java.util.List.of();
            }
            return tf.itemFactory().statLoreBlock(material, cmd, quality, rollSeed);
        } catch (Throwable t) {
            return java.util.List.of();
        }
    }

    /**
     * 品質ティアのラベル({@code stats/quality-tiers.yml} の name/color を通した【名匠】等)。
     * スレッド名の後ろに付ける品質表記の唯一の供給元 ── フォークで「品質3」等と数値表示しないこと
     * (TF 装備の品質表記と食い違う)。未ロード / 範囲外は {@link Optional#empty()}。
     */
    public static Optional<net.kyori.adventure.text.Component> qualityTierLabel(int quality) {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null || tf.config() == null || tf.config().qualityTiers() == null) {
                return Optional.empty();
            }
            return tf.config().qualityTiers().tierFor(quality)
                    .map(com.trinityforge.stats.QualityTier::label);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /**
     * soulbind の「真実」を ItemData に統一する。bindType=SOULBOUND を書き、
     * 所有者UUIDが分かる場合は owner も確定させる。
     *
     * <p>ArsEnchantments ベースの soulbound（回生エフェクト）は併存させてよいが、
     * 所有者・バインド種別の真実はこの ItemData 側に一本化する。
     *
     * @param meta  書込み対象の {@link ItemMeta}
     * @param owner 所有者UUID。未確定なら {@code null}（bindType のみ書き、owner は最初の取得者で確定）
     */
    public static void bindSoulbound(ItemMeta meta, UUID owner) {
        if (meta == null) {
            return;
        }
        try {
            ItemData data = ItemData.of(meta);
            data.setBindType(BindType.SOULBOUND);
            if (owner != null) {
                data.setOwner(owner);
            }
        } catch (Throwable t) {
            // TrinityForge 未ロード: bindType/owner はスキップ。
        }
    }

    /**
     * ItemData に記録された所有者UUIDを返す。未設定 / 未ロード時は {@code null}。
     */
    public static UUID soulboundOwner(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        try {
            return ItemData.of(meta).owner().orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    // ============================================================
    // 防具スレッドの戦闘ステ連携 (AddonCombatStats チャネル)
    //
    // スレッド1個ごとの個別ステは TrinityForge の stats/item-stats.yml (MATERIAL#CMD) から取得し
    // ({@link #resolveItemStats})、ArmorManaListener 側で全装着スレの個別ステ + thread-sets.yml の
    // セット効果を集計 → その合計を {@link #writeAddonCombatStats} でプレイヤーPDCへ書込む。
    // TrinityForge は攻撃/防御パイプラインでこの PDC を読み、戦闘へ合流する。
    // TrinityForge 未ロード時は全て no-op / 空Map にフォールバックし、スレッドのマナ機能は無影響。
    // ============================================================

    /**
     * 指定 material + CustomModelData の item-stats ステ(canonical key -&gt; 値)を取得する。
     * {@link WeaponAttackStatResolver#resolveItemStats} へ委譲。スレッドは品質を持たないため品質0のfixed値。
     *
     * <p>TF未ロード / resolver未初期化 / 例外時は空の可変Mapを返す(fail-open)。返り値は呼び出し側が
     * 直接マージ蓄積してよい可変Map。
     */
    public static Map<String, Double> resolveItemStats(Material material, Integer customModelData) {
        if (material == null) {
            return new LinkedHashMap<>();
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return new LinkedHashMap<>();
            }
            WeaponAttackStatResolver resolver = tf.weaponAttackStats();
            if (resolver == null) {
                return new LinkedHashMap<>();
            }
            return resolver.resolveItemStats(material, customModelData);
        } catch (Throwable t) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * ライブ {@link ItemStack} の TF item-stats をフル解決(fixed + per-quality + random +
     * crafter thread-slot bonus + category cap)。防具のスレッド枠/マナ加算はこちらを使う。
     */
    public static Map<String, Double> resolveFullItemStats(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return new LinkedHashMap<>();
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return new LinkedHashMap<>();
            }
            WeaponAttackStatResolver resolver = tf.weaponAttackStats();
            if (resolver == null) {
                return new LinkedHashMap<>();
            }
            return resolver.resolveFull(item);
        } catch (Throwable t) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * 集計済みの戦闘ステMapをプレイヤーPDC({@link PdcKeys#PLAYER_ADDON_COMBAT_STATS})へ書き込む。
     * TrinityForge 所有のコーデック({@link AddonCombatStats#encode})で符号化するため、TF側の読取と形式が一致する。
     * 空(または全ゼロ)なら PDC キーを削除し、装備解除で古いステが残らないようにする。
     * TF未ロード / 例外時は no-op(既存挙動を壊さない)。
     */
    public static void writeAddonCombatStats(Player player, Map<String, Double> stats) {
        if (player == null) {
            return;
        }
        try {
            String encoded = (stats == null || stats.isEmpty()) ? "" : AddonCombatStats.encode(stats);
            PersistentDataContainer pdc = player.getPersistentDataContainer();
            if (encoded.isEmpty()) {
                pdc.remove(PdcKeys.PLAYER_ADDON_COMBAT_STATS);
            } else {
                pdc.set(PdcKeys.PLAYER_ADDON_COMBAT_STATS, PersistentDataType.STRING, encoded);
            }
        } catch (Throwable t) {
            // TF未ロード / API不整合: addon戦闘ステはスキップ(スレッドのマナ機能等には無影響)。
        }
    }

    /**
     * 指定 material + CustomModelData のアイテムが、オフハンド装備時にも item-stats を適用する
     * (offhand-stats-apply: true)かどうかを返す。TrinityForge の {@code ItemStatsConfig#offhandStatsApply}
     * へ委譲する。
     *
     * <p>TF未ロード / 例外時は {@code false}(fail-open=false。誤ってオフハンドへ二重適用しない安全側)。
     */
    public static boolean offhandStatsApply(Material material, Integer customModelData) {
        if (material == null) {
            return false;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return false;
            }
            return tf.config().itemStats().offhandStatsApply(material, customModelData);
        } catch (Throwable t) {
            return false;
        }
    }

    // ============================================================
    // 触媒(catalysts.yml)ステのTrinityForge動的item-stats登録
    //
    // 触媒のステ解決(固定/品質別/ランダムロール)はTrinityForgeエンジンに一本化する。
    // フォークはCatalystConfigでパースした生値をここでTFの動的登録APIへ渡すだけで、
    // 品質/rollSeed込みの解決・lore自動生成・magicalFinalDamage反射・マナ集約は
    // TrinityForge既存の item-stats パイプラインがmaterial+cmd単位で統一的に処理する。
    // TrinityForge未ロード時は全メソッドがno-opにフォールバックする(fail-open)。
    // ============================================================

    /** 触媒の動的item-stats登録に使うnamespace（clearDynamicのスコープ単位）。 */
    private static final String CATALYST_NAMESPACE = "arspaper-catalyst";

    /**
     * 指定material+CustomModelDataの触媒ステをTrinityForgeへ動的登録する。
     * 既存登録がある場合は上書きされる(TF側{@code registerDynamic}の仕様)。
     *
     * @param material   触媒のベースマテリアル
     * @param cmd        触媒のCustomModelData
     * @param fixed      固定ステ(canonicalキー -&gt; 値)
     * @param perQuality 品質1あたりの加算ステ(canonicalキー -&gt; 値)
     * @param random     ランダムロール範囲(canonicalキー -&gt; min/max)。ArsPaper独自の
     *                   {@link com.arspaper.item.CatalystStatRange} をここでTF型へ変換する
     *                   （TF未ロード時にTF型の構築自体を避けるため、変換はこのtry節の中でのみ行う）。
     */
    public static void registerCatalystStats(Material material, int cmd,
            Map<String, Double> fixed, Map<String, Double> perQuality,
            Map<String, com.arspaper.item.CatalystStatRange> random) {
        // 2026-07-26 ユーザー決定(意図的・監査で再提起しないこと): 下記 registerDynamic 呼び出しの
        // 最終引数 offhandApplies は常に false 固定。触媒(ember_wand: material BLAZE_ROD 等)は
        // 能動的に構える「杖型キャスト媒体」であり、防具/武器の offhand-stats-apply トグルが対象と
        // する「装備したままのパッシブなアクセサリ」とはアーキテクチャ上別物 — 触媒の二刀持ち
        // (オフハンドに触媒を持って同時発動する運用)はそもそもサポート対象の遊び方ではない。
        // 監査で「意図しないハードコードでは」と指摘されたため、次回以降の監査が同じ指摘を
        // 繰り返さないようここへ経緯を明記した。
        if (material == null) {
            return;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return;
            }
            Map<String, com.trinityforge.stats.StatRange> tfRandom = new LinkedHashMap<>();
            if (random != null) {
                for (Map.Entry<String, com.arspaper.item.CatalystStatRange> entry : random.entrySet()) {
                    com.arspaper.item.CatalystStatRange range = entry.getValue();
                    tfRandom.put(entry.getKey(), new com.trinityforge.stats.StatRange(range.min(), range.max()));
                }
            }
            tf.config().itemStats().registerDynamic(
                CATALYST_NAMESPACE, material, cmd,
                fixed != null ? fixed : Map.of(),
                perQuality != null ? perQuality : Map.of(),
                tfRandom, null, false // offhandApplies=false: 意図的固定(2026-07-26, 経緯は上のコメント参照)
            );
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[ArsPaper] 触媒ステのTrinityForge動的登録に失敗しました ("
                + material + "#" + cmd + "): " + t);
        }
    }

    /**
     * {@link #CATALYST_NAMESPACE} 配下の動的登録を全て解除する。
     * reload時に古い触媒定義を残さないよう、再登録の直前に呼ぶ。
     */
    public static void clearCatalystStats() {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return;
            }
            tf.config().itemStats().clearDynamic(CATALYST_NAMESPACE);
        } catch (Throwable t) {
            // TrinityForge 未ロード: クリア不要(次回起動時に空の状態から登録される)。
        }
    }

    // ============================================================
    // 外部カスタムアイテムのTrinityForge識別台帳(ExternalItemRegistry)への登録
    //
    // materials.yml(圧縮素材/魔糸繊維/ソースストーン等)や spellbooks.yml/threads.yml のArsPaper
    // カスタムアイテムは items/catalog.yml の custom:<id> レシピ素材として参照されうる。TF側の
    // CatalogWorkbenchListener が per-slot 識別 (material + CustomModelData) できるよう、
    // ArsPaper.itemRegistry(CustomItemRegistry)の内容をそのまま丸ごと反映する。材料の手打ち二重管理
    // (external-items.yml への個別転記)は意図的に避ける — materials.yml 編集のたびに乖離するため。
    // ============================================================

    /** ExternalItemRegistry 側でこの登録元を識別するレイヤーキー。reload時は同じキーで置き換わる。 */
    private static final String EXTERNAL_ITEM_SOURCE = "arspaper";

    /**
     * ArsPaperの全カスタムアイテム(素材/魔導書/触媒/スレッド/ワンド/ベリー等、{@code itemRegistry}に
     * 登録済みの{@link BaseCustomItem}全部)をTrinityForgeの{@link ExternalItemRegistry}へ反映する。
     *
     * <p>呼び出しタイミング: ArsPaper.onEnable の {@code initRegistries()} 完了直後
     * (= {@code repushCatalogRituals}/{@code refreshCatalogRecipes} より前。この順序でないと
     * TFの初回カタログレシピ再登録がまだ空のExternalItemRegistryを見てしまう)、および
     * {@code reloadMaterialConfig}/{@code reloadSpellBookConfig}/{@code reloadCatalystConfig} 等の
     * 個別reloadフックの末尾。
     *
     * <p>{@link ExternalItemRegistry#updateExternalPlugin} はソース単位でレイヤー置換するため、この
     * 呼び出しはTFの{@code external-items.yml}レイヤーにも他プラグインのレイヤーにも影響しない —
     * 置き換わるのは常に {@value #EXTERNAL_ITEM_SOURCE} レイヤーのみ。TF未ロード時はno-op(fail-open)。
     */
    public static void registerExternalItems(Collection<BaseCustomItem> items) {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return;
            }
            Map<String, ExternalItemRegistry.Definition> defs = new LinkedHashMap<>();
            for (BaseCustomItem item : items) {
                String id = item.getItemId();
                Material material = item.getBaseMaterial();
                if (id == null || material == null) {
                    continue;
                }
                String displayName = PlainTextComponentSerializer.plainText().serialize(item.getDisplayName());
                defs.put(id, new ExternalItemRegistry.Definition(
                        id, material, item.getCustomModelData(), displayName));
            }
            ExternalItemRegistry.updateExternalPlugin(EXTERNAL_ITEM_SOURCE, defs);
        } catch (Throwable t) {
            Bukkit.getLogger().warning(
                    "[ArsPaper] カスタムアイテムのTrinityForge識別台帳(ExternalItemRegistry)登録に失敗しました: " + t);
        }
    }

    /**
     * 触媒のbind-type(TrinityForge {@code BindType})をPDCへ刻印する。
     * 未指定({@code null}/空文字)・不明な値・TrinityForge未ロード時はno-op(fail-open)。
     *
     * @param meta        書込み対象の{@link ItemMeta}
     * @param bindTypeName spellbooks.ymlの{@code bind-type}文字列（例: TRADEABLE）
     */
    public static void applyCatalystBindType(ItemMeta meta, String bindTypeName) {
        if (meta == null || bindTypeName == null || bindTypeName.isBlank()) {
            return;
        }
        try {
            java.util.Optional<com.trinityforge.pdc.BindType> type =
                com.trinityforge.pdc.BindType.fromStorage(bindTypeName);
            type.ifPresent(t -> ItemData.of(meta).setBindType(t));
        } catch (Throwable t) {
            // 不明なbind-type値 / TrinityForge未ロード: 刻印をスキップ(既存生成は維持)。
        }
    }

    // ============================================================
    // skilltree由来 dedicated-effect 解放マップ (要件⑥: 既存ゲートとのunion合流)
    //
    // TrinityForgeがスキルツリーのglyph/recipe/ritual解放系dedicated-effectから
    // 「target(グリフキー/レシピID/儀式ID) → perkId集合」を算出済み。ArsPaper側の
    // 既存ゲート(UsageGate/UnlockGate)は、この派生マップを yml 設定と union(OR)して
    // 使用可否を判定する。TF未ロード/未初期化/例外時は空Mapにフォールバックし(fail-open)、
    // 呼び出し側は「ymlにもTFにも該当キーが無い」ケースを従来通り「ゲート無し=許可」として扱う。
    // ============================================================

    /**
     * グリフキー(usage-gate.ymlのglyph-perksキーと同一空間) → 使用解放に足るperkId集合。
     * TF未ロード/例外時は{@link Collections#emptyMap()}。
     */
    public static Map<String, Set<String>> tfGlyphGatePerks() {
        return tfDedicatedEffectMap(com.trinityforge.config.domains.DedicatedEffectsConfig::glyphGatePerks);
    }

    /**
     * レシピID(unlock-gate.ymlのrecipe-perksキーと同一空間) → クラフト解放に足るperkId集合。
     * TF未ロード/例外時は{@link Collections#emptyMap()}。
     */
    public static Map<String, Set<String>> tfRecipeGatePerks() {
        return tfDedicatedEffectMap(com.trinityforge.config.domains.DedicatedEffectsConfig::recipeGatePerks);
    }

    /**
     * 儀式ID(unlock-gate.ymlのritual-perksキーと同一空間) → 儀式解放に足るperkId集合。
     * TF未ロード/例外時は{@link Collections#emptyMap()}。
     */
    public static Map<String, Set<String>> tfRitualGatePerks() {
        return tfDedicatedEffectMap(com.trinityforge.config.domains.DedicatedEffectsConfig::ritualGatePerks);
    }

    /**
     * TF未ロード/{@code config()}未初期化/例外(NoClassDefFoundError等含む)を一括で
     * {@link Collections#emptyMap()}へフォールバックする共通アクセサ。
     */
    private static Map<String, Set<String>> tfDedicatedEffectMap(
            java.util.function.Function<com.trinityforge.config.domains.DedicatedEffectsConfig,
                Map<String, Set<String>>> accessor) {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return Collections.emptyMap();
            }
            com.trinityforge.config.domains.DedicatedEffectsConfig dedicatedEffects = tf.config().dedicatedEffects();
            if (dedicatedEffects == null) {
                return Collections.emptyMap();
            }
            Map<String, Set<String>> result = accessor.apply(dedicatedEffects);
            return result != null ? result : Collections.emptyMap();
        } catch (Throwable t) {
            return Collections.emptyMap();
        }
    }

    // ============================================================
    // skilltree由来 dedicated-effect 数値/bool取得 (要件⑥: 既存消費/上限機構への反映)
    //
    // TrinityForgeがスキルツリーperkから算出した数値効果(dedicated-effects.ymlのキー空間)を
    // フォーク側の既存消費/上限計算点へ供給する。valueSum/isActiveへの単純な委譲であり、
    // TF未ロード/未初期化/例外時は無効値(0.0 / false)にフォールバックする(fail-open=効果無し)。
    // ============================================================

    /**
     * 魔導書/ワンドのグリフ配置枠加算(int, +N)のdedicated-effectキー。
     *
     * <p>STOP(2026-07-23 W2d-2): TF側 {@code ars_magic.yml} は本キーの配線を
     * "ArsMagicProfile.glyphSlots(native arsmagic_glyphslots_add)と同一対象への重複配線であり、
     * §3ゲート種別(glyph/recipe/ritual/drop/brew/trade/feature/ars-tier)にglyphSlots専用のゲート型が
     * 定義されていないため有効な変換先が無い" という理由で意図的に未解決のまま残している(TF側コード中に
     * 同旨のSTOPコメントあり)。{@link com.trinityforge.skilltree.effects.GateEffectId#parse} は
     * コロン無し・{@code ars-tier}以外の生idを受理しないため、このキーでの
     * {@code dedicatedEffects.valueSum} は常に0を返す(死んでいるが、TF側の未確定仕様に追随して
     * fork側もそのまま維持。指示なくstat等へ変換しない)。
     */
    public static final String EFFECT_GLYPH_SLOT_PLUS = "glyph-slot-plus";
    /**
     * ArsTier上限加算(int, +N)の動的ゲートID。TF {@code GateEffectId.ARS_TIER} と同一の裸リテラル
     * (2026-07-23 stat-gate-overhaul §3.1: 旧 dedicated-effect id {@code ars-tier-unlock} から追随)。
     *
     * <p>⚠ 2026-08-13 レーンD監査で判明: {@code ars_magic.yml} のノード A・E は
     * {@code buffs: {ars-tier-bonus: 1}}(stat語彙 {@code ars_tier_bonus} 経由、
     * {@link com.trinityforge.integration.ars.ArsNativeBridge} がパーク general + 永続バフ + 役職バフ +
     * base-stats を合算する現行の唯一の正規チャネル)と、この {@code dedicated-effects: [id: ars-tier]}
     * (perk保有のみを合算する旧チャネル)を<b>両方</b>同じ値で置いていた。{@link #tfArsTierUnlockBonus}
     * が両チャネルを加算していたため、ノードA・Eをそれぞれ2倍(合計+4、正しくは+2)にカウントする
     * 二重計上バグだった({@link #EFFECT_GLYPH_SLOT_PLUS}と違い、こちらの {@code GateEffectId.parse}
     * は "ars-tier" を有効なFLAGとして受理するため実際に非ゼロを返し、死んでいなかった)。
     * {@link #tfArsTierUnlockBonus} はこのチャネルを2026-08-13以降参照しない
     * ({@code ars_tier_bonus} stat語彙チャネルへ一本化)。{@code ars_magic.yml} 側の
     * {@code dedicated-effects: id: ars-tier} 記述は無害な死んだ記述として残る
     * (削除は別レーンが編集中の {@code ars_magic.yml} と衝突するため見送り、レポートのみで報告済み)。
     */
    public static final String EFFECT_ARS_TIER = "ars-tier";
    /** マナ不足時にソースを自動でマナ代わりに消費するflagのdedicated-effectキー(要件⑥ source-auto-consume)。 */
    public static final String EFFECT_SOURCE_AUTO_CONSUME = "source-auto-consume";

    /**
     * stat語彙キー: 儀式のソース(source)消費削減率(分数[0,1]、例 0.10 = 10%減)。
     * 旧 dedicated-effect {@code source-cost-reduction} から stat {@code source_cost_reduction}
     * へ移行(2026-07-23 stat-gate-overhaul §2 fork consumer系)。読取は {@link #tfStatTotal} 経由。
     * 2026-07-23 正準スケール分数統一により、値は百分点(10)ではなく分数(0.10)。
     */
    public static final String STAT_SOURCE_COST_REDUCTION = "source_cost_reduction";
    // 2026-08-14: STAT_LAPIS_COST_REDUCTION("lapis_cost_reduction")は廃止した。
    // TF 側の stat 語彙から消えているので tfStatTotal は常に 0 を返す ── 定数だけ残すと
    // 「効くように見えて何も起きない」配線を再び書ける状態になるため、定数ごと削除している。
    /**
     * stat語彙キー: 儀式のペデスタル素材消費時に1個返却する確率(分数[0,1]、例 0.10 = 10%)。
     * 旧 dedicated-effect {@code material-refund-chance} から移行。
     * 2026-07-23 正準スケール分数統一により、値は百分点(10)ではなく分数(0.10)。
     */
    public static final String STAT_MATERIAL_REFUND_CHANCE = "material_refund_chance";
    /**
     * stat語彙キー: ソースリンク素材投入時に消費をスキップする確率(分数[0,1]、例 0.10 = 10%)。
     * 旧 dedicated-effect {@code ingredient-no-consume-chance} から移行。
     * 2026-07-23 正準スケール分数統一により、値は百分点(10)ではなく分数(0.10)。
     */
    public static final String STAT_INGREDIENT_SAVE_CHANCE = "ingredient_save_chance";

    /**
     * 指定プレイヤーの保有perkに紐づく{@code effectId}のvalue総和を返す。
     * TF未ロード/{@code config()}未初期化/例外時は{@code 0.0}(fail-open=効果無し)。
     */
    public static double tfEffectValue(Player player, String effectId) {
        if (player == null || effectId == null) {
            return 0.0;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return 0.0;
            }
            com.trinityforge.config.domains.DedicatedEffectsConfig dedicatedEffects = tf.config().dedicatedEffects();
            if (dedicatedEffects == null) {
                return 0.0;
            }
            return dedicatedEffects.valueSum(player, effectId);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /**
     * 指定プレイヤーが{@code effectId}を有効化するperkを保有しているかを返す。
     * TF未ロード/未初期化/例外時は{@code false}(fail-open=無効)。
     */
    public static boolean tfEffectActive(Player player, String effectId) {
        if (player == null || effectId == null) {
            return false;
        }
        // /ars debug: flag 系解放(スレッド枠拡張等)も使用権限として全有効化
        try {
            ArsPaper ars = ArsPaper.getInstance();
            if (ars != null && ars.getManaManager() != null && ars.getManaManager().isDebugMode(player)) {
                return true;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return false;
            }
            com.trinityforge.config.domains.DedicatedEffectsConfig dedicatedEffects = tf.config().dedicatedEffects();
            if (dedicatedEffects == null) {
                return false;
            }
            return dedicatedEffects.isActive(player, effectId);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 指定プレイヤーの装備+skilltree perk合算stat({@code key})の値を返す(2026-07-23
     * stat-gate-overhaul §2 移行: TF static API {@code TrinityForge#statTotal} 経由)。
     * TF未ロード/未初期化/例外時は{@code 0.0}(fail-open=効果無し)。
     */
    public static double tfStatTotal(Player player, String statKey) {
        if (player == null || statKey == null) {
            return 0.0;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return 0.0;
            }
            return tf.statTotal(player, statKey);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /**
     * 装備アイテム由来を除いた、プレイヤー単位の寄与(パーク general / 役職バフ / 永続バフ / base-stats)
     * だけの合計を返す(2026-07-26 マナ系ステ穴埋め: TF static API {@code TrinityForge#nonItemStatTotal}
     * 経由)。{@link #tfStatTotal} と違い、防具4部位・メインハンド・オフハンドの item-stats を一切含まない
     * ため、フォーク側が装備を自前で既に集計している({@code ArmorManaListener}/{@code SpellCaster} の
     * 触媒集計等)場面で、二重計上せずにパーク分だけを上乗せするために使う。
     * TF未ロード/未初期化/例外時は{@code 0.0}(fail-open=効果無し)。
     */
    public static double tfNonItemStatTotal(Player player, String statKey) {
        if (player == null || statKey == null) {
            return 0.0;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return 0.0;
            }
            return tf.nonItemStatTotal(player, statKey);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /**
     * 割合(分数[0,1])の確率系効果(material_refund_chance / ingredient_save_chance等)を
     * 消費計算に使える範囲[0,1]へクランプする。NaN / 負値 / TF未ロード由来の0.0は0(発生なし)に、
     * 1超は1(必ず発生)にクランプする。
     * 2026-07-23 stat-gate-overhaul: TFの正準スケールが分数[0,1]に統一されたことに伴い、
     * 旧clampReductionPercent([0,100]percent想定)の確率系用途を引き継ぐ。
     */
    public static double clampChanceFraction(double frac) {
        if (Double.isNaN(frac)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, frac));
    }

    /**
     * 割合(分数[0,1])のコスト削減系効果(source_cost_reduction 等)を
     * 消費計算に使える範囲[0,0.95]へクランプする。NaN / 負値 / TF未ロード由来の0.0は0(削減なし)に、
     * 0.95超は0.95(コストが全額無料化しない安全上限)にクランプする。
     * 2026-07-23 stat-gate-overhaul: TFの正準スケールが分数[0,1]に統一されたことに伴い、
     * 旧clampReductionPercent([0,100]percent想定)のコスト削減系用途を引き継ぐ。
     */
    public static double clampReductionFraction(double frac) {
        if (Double.isNaN(frac)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(0.95, frac));
    }

    /**
     * int系加算効果(glyph-slot-plus / ars-tier)をfloorし、負値を0にクランプする。
     */
    public static int clampNonNegativeFloor(double value) {
        if (Double.isNaN(value) || value <= 0.0) {
            return 0;
        }
        return (int) Math.floor(value);
    }

    /**
     * マナ初期値(combat/base-stats.yml 専用キー、例 {@code mana-max-base})を1件読む
     * (2026-07-25 config editor T2: ArsPaper config.yml の mana.default-max 等を
     * TrinityForge のプレイヤー基礎ステータスへ移設)。TF未ロード/未初期化/未設定/例外時は
     * {@code fallback}(ArsPaperの従来デフォルト値)を返す(fail-open)。
     *
     * <p>{@link com.trinityforge.config.domains.BaseStatsConfig#stats()} を直接読む(新設の
     * {@code statOrDefault} ではなく)。fork は {@code libs/TrinityForge.jar} をコンパイル時依存として
     * 固定しており、この変更セッションでは jar 再ビルドを行わない方針のため、既存jarに含まれる
     * {@code stats()} だけで完結させる。
     */
    public static double manaBaseStat(String canonicalKey, double fallback) {
        java.util.OptionalDouble value = manaBaseStatRaw(canonicalKey);
        return value.isPresent() ? value.getAsDouble() : fallback;
    }

    /**
     * マナ初期値を「値が実際に届いたかどうか」まで含めて読む。
     *
     * <p>⚠ {@code BaseStatsConfig#load} は <b>0 を書いたキーをロード時に捨てる</b>
     * (「0 = 加算なし = 未記載」というTF側の規約。base-stats.yml のヘッダコメント参照)。
     * そのため「TFで 0 に設定した」と「TFがロードされていない/キーが無い」を
     * {@code stats()} だけでは区別できず、両方 empty で返る。
     * 0 を 0 として扱いたい呼び出し側は {@link #trinityForgeLoaded()} と組み合わせること
     * ({@link com.arspaper.mana.ManaBaseStats} が実例)。
     *
     * <p>{@link com.trinityforge.config.domains.BaseStatsConfig#stats()} を直接読む(新設の
     * {@code statOrDefault} ではなく)。fork は {@code libs/TrinityForge.jar} をコンパイル時依存として
     * 固定しているため、既存jarに含まれる {@code stats()} だけで完結させる。
     */
    public static java.util.OptionalDouble manaBaseStatRaw(String canonicalKey) {
        if (canonicalKey == null) {
            return java.util.OptionalDouble.empty();
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return java.util.OptionalDouble.empty();
            }
            Double value = tf.config().baseStats().stats().get(StatKeys.canonical(canonicalKey));
            return value != null ? java.util.OptionalDouble.of(value) : java.util.OptionalDouble.empty();
        } catch (Throwable t) {
            return java.util.OptionalDouble.empty();
        }
    }

    /**
     * TrinityForge 本体がロードされ、プレイヤー基礎ステータスを読める状態か。
     *
     * <p>{@link #isAvailable()}(戦闘サービスの可用性)とは別物で、ここでは
     * {@code combat/base-stats.yml} が読めることだけを見る。
     *
     * <p>⚠ これだけでは「0と書いた」と「行が無い/パース失敗」を区別できない。
     * マナ初期値の解決には {@link #manaBaseStatSource(String)} を使うこと。
     */
    public static boolean trinityForgeLoaded() {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            return tf != null && tf.config() != null && tf.config().baseStats() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    // --- base-stats.yml の「行が書かれているか」判定(2026-08-01 round2) ---------------------
    //
    // BaseStatsConfig#stats() は 0 のキーをロード時に捨てるので、値が届かない理由が
    //   (a) 0 と書いた / (b) 行が1つも無い / (c) yml のパースに失敗した
    // のどれなのか API からは分からない。(a) だけを 0 として扱い、(b)(c) はフォールバックへ
    // 落とす必要があるため、TF のデータフォルダにある実ファイルのキー集合を直接読む。
    // 読み直しはファイルの更新時刻+サイズが変わったときだけ(マナ回復は毎秒引かれるため)。

    /** base-stats.yml を再確認する最短間隔(ナノ秒)。/trinityforge reload への追随は数秒遅れて構わない。 */
    private static final long BASE_STATS_RECHECK_NANOS = 5_000_000_000L;

    /** 読めたときは canonical 化済みキー集合、読めなかったときは null。 */
    private static volatile java.util.Set<String> baseStatsDeclaredKeys = null;
    /** キャッシュした時点のファイル識別(更新時刻とサイズ)。 */
    private static volatile long baseStatsFileStamp = Long.MIN_VALUE;
    private static volatile long baseStatsCheckedAtNanos = 0L;
    private static volatile boolean baseStatsCacheInitialised = false;

    /**
     * マナ初期値キーが TF の {@code combat/base-stats.yml} でどう見えているかを返す。
     *
     * <p>{@link #manaBaseStatRaw(String)} が empty を返す理由を4通りに分ける。
     * 詳細な規約は {@link com.arspaper.mana.ManaBaseStats} の javadoc を参照。
     */
    public static com.arspaper.mana.ManaBaseStats.Source manaBaseStatSource(String canonicalKey) {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null || tf.config() == null || tf.config().baseStats() == null) {
                return com.arspaper.mana.ManaBaseStats.Source.TRINITYFORGE_ABSENT;
            }
            java.util.Set<String> declared = declaredBaseStatKeys(tf);
            if (declared == null) {
                return com.arspaper.mana.ManaBaseStats.Source.UNREADABLE;
            }
            if (canonicalKey == null) {
                return com.arspaper.mana.ManaBaseStats.Source.KEY_ABSENT;
            }
            return declared.contains(StatKeys.canonical(canonicalKey))
                    ? com.arspaper.mana.ManaBaseStats.Source.KEY_DECLARED
                    : com.arspaper.mana.ManaBaseStats.Source.KEY_ABSENT;
        } catch (Throwable t) {
            // TF のクラスが解決できない等。ArsPaper 単体運用と同じ扱い(fail-open)。
            return com.arspaper.mana.ManaBaseStats.Source.TRINITYFORGE_ABSENT;
        }
    }

    /**
     * base-stats.yml に実際に書かれているキー集合(canonical)。読めなければ null。
     *
     * <p>⚠ この判定はマナ回復のたび(＝毎秒×人数)に引かれるので、
     * <b>時間窓の中ではファイルシステムに一切触れない</b>。窓を過ぎたときだけ
     * 更新時刻+サイズを見て、変わっていれば読み直す({@code /trinityforge reload} への
     * 追随が数秒遅れるのは許容範囲)。
     */
    private static java.util.Set<String> declaredBaseStatKeys(TrinityForge tf) {
        long now = System.nanoTime();
        if (baseStatsCacheInitialised && now - baseStatsCheckedAtNanos < BASE_STATS_RECHECK_NANOS) {
            return baseStatsDeclaredKeys;
        }
        java.io.File file = new java.io.File(tf.getDataFolder(),
                com.trinityforge.config.domains.BaseStatsConfig.PATH);
        long stamp = file.exists() ? (file.lastModified() * 31L) ^ file.length() : Long.MIN_VALUE + 1L;
        baseStatsCheckedAtNanos = now;
        if (baseStatsCacheInitialised && stamp == baseStatsFileStamp) {
            return baseStatsDeclaredKeys;
        }
        java.util.Set<String> parsed = parseDeclaredBaseStatKeys(file);
        baseStatsDeclaredKeys = parsed;
        baseStatsFileStamp = stamp;
        baseStatsCacheInitialised = true;
        return parsed;
    }

    private static java.util.Set<String> parseDeclaredBaseStatKeys(java.io.File file) {
        if (!file.isFile()) {
            return null;
        }
        org.bukkit.configuration.file.YamlConfiguration yaml =
                new org.bukkit.configuration.file.YamlConfiguration();
        try {
            yaml.load(file);
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException broken) {
            return null;
        }
        org.bukkit.configuration.ConfigurationSection sec = yaml.getConfigurationSection("base-stats");
        if (sec == null) {
            return null;
        }
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        for (String key : sec.getKeys(false)) {
            keys.add(StatKeys.canonical(key));
        }
        return keys;
    }

    /** グリフ配置枠加算(int, +N, floor/0クランプ済み)。dedicated + native arsmagic_glyphslots_add。 */
    public static int tfGlyphSlotBonus(Player player) {
        return clampNonNegativeFloor(tfEffectValue(player, EFFECT_GLYPH_SLOT_PLUS)
                + tfNativeArsDouble(player, "glyphSlots"));
    }

    /**
     * 使用可能Ars tier上限加算(int, +N, floor/0クランプ済み)。native arsmagic_unlockedtier_add
     * ({@code ars_tier_bonus} stat語彙、{@link com.trinityforge.integration.ars.ArsNativeBridge}が
     * パーク general + 永続バフ + 役職バフ + base-stats を合算する唯一の正規チャネル)のみを読む。
     *
     * <p>⚠ 2026-08-13 レーンD監査で修正: 以前は {@code tfEffectValue(player, EFFECT_ARS_TIER)}
     * (dedicated-effectsの {@code ars-tier} チャネル、perk保有のみを合算)もここに加算していたが、
     * {@code ars_magic.yml} のノードA・Eは {@code buffs: ars-tier-bonus} と
     * {@code dedicated-effects: id: ars-tier} を同じ値で両方置いていたため、2チャネル合算が
     * 二重計上になっていた(該当ノード保有プレイヤーの実効tier加算が意図の2倍)。詳細は
     * {@link #EFFECT_ARS_TIER} のjavadoc参照。
     */
    public static int tfArsTierUnlockBonus(Player player) {
        return clampNonNegativeFloor(tfNativeArsDouble(player, "unlockedTier"));
    }

    /** native arsmagic_maxmanabonus_add（TF Services / ArsNativeBridge）。 */
    public static double tfNativeMaxManaBonus(Player player) {
        return Math.max(0.0, tfNativeArsDouble(player, "maxMana"));
    }

    /** native arsmagic_manaregenbonus_add（割合加算、例 0.1 = +10%）。 */
    public static double tfNativeManaRegenBonus(Player player) {
        return Math.max(0.0, tfNativeArsDouble(player, "manaRegen"));
    }

    private static double tfNativeArsDouble(Player player, String field) {
        if (player == null || field == null) return 0.0;
        try {
            var reg = org.bukkit.Bukkit.getServicesManager()
                    .getRegistration(com.trinityforge.integration.ars.ArsNativeBridge.class);
            if (reg == null || reg.getProvider() == null) return 0.0;
            var bridge = reg.getProvider();
            return switch (field) {
                case "glyphSlots" -> bridge.glyphSlots(player.getUniqueId());
                case "unlockedTier" -> bridge.unlockedTier(player.getUniqueId());
                case "maxMana" -> bridge.maxManaBonus(player.getUniqueId());
                case "manaRegen" -> bridge.manaRegenBonus(player.getUniqueId());
                default -> 0.0;
            };
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /**
     * ソースリンク素材投入時の消費スキップ確率(分数[0,1]、[0,1]クランプ済み)。TF未ロード/perk未所持時は0。
     * stat {@code ingredient_save_chance}(装備+perk合算)から取得(2026-07-23 stat-gate-overhaul §2)。
     * 2026-07-23 正準スケール分数統一に伴い旧名tfIngredientNoConsumeChancePercentから改名、
     * 呼び出し側は {@code ThreadLocalRandom.nextDouble() < frac} で判定する。
     */
    public static double tfIngredientNoConsumeChanceFraction(Player player) {
        return clampChanceFraction(tfStatTotal(player, STAT_INGREDIENT_SAVE_CHANCE));
    }

    /**
     * 儀式のペデスタル素材返却確率(分数[0,1]、[0,1]クランプ済み)。TF未ロード/perk未所持時は0。
     * stat {@code material_refund_chance}(装備+perk合算)から取得。
     * 2026-07-23 正準スケール分数統一に伴い旧名tfMaterialRefundChancePercentから改名、
     * 呼び出し側は {@code ThreadLocalRandom.nextDouble() < frac} で判定する。
     */
    public static double tfMaterialRefundChanceFraction(Player player) {
        return clampChanceFraction(tfStatTotal(player, STAT_MATERIAL_REFUND_CHANCE));
    }

    /**
     * 防具の実効スレッド枠数(= item-statsの基本枠 thread_slots)を返す。GUI表示(ThreadGui)と
     * ステ適用(ArmorManaListener)で同一ロジックを共有する。
     *
     * <p>2026-07-26: TFステータス経由でスレッド枠上限を増やす経路は廃止された(スレッド付与の儀式が
     * 既に提供している機能と重複するため)。以降は item-stats の基本枠のみを返す。
     *
     * @param armorItemStats 対象防具のTF item-stats({@link #resolveFullItemStats} 推奨)
     * @param wearer         ステ適用対象のプレイヤー(将来の拡張余地のため引数は維持)
     */
    public static int tfEffectiveThreadSlotCap(Map<String, Double> armorItemStats, Player wearer) {
        return (int) Math.floor(armorItemStats.getOrDefault("thread_slots", 0.0));
    }

    // ============================================================
    // P2-Java: 触媒マナ消費軽減の解決済みitem-stats化
    //
    // 旧来の catalysts.yml `mana-cost-reduction:{flat,percent}` はエディタが
    // `stats.fixed.mana-cost-reduction-flat` / `mana-cost-reduction-percent`(INTEGER item-stats)へ移行
    // 済み(移行時に旧キーは削除される)。この2キーは他の触媒ステ同様
    // {@link #registerCatalystStats} でTrinityForgeへ動的登録され、品質/ランダムロール込みで解決される。
    // ============================================================

    /** 触媒 stats 節の固定/品質別/ランダムキー名(エディタ書込みキーと一致、ハイフン区切り)。 */
    private static final String MANA_REDUCTION_FLAT_KEY = "mana-cost-reduction-flat";
    private static final String MANA_REDUCTION_PERCENT_KEY = "mana-cost-reduction-percent";

    /** 触媒の解決済みマナ消費軽減(品質/ランダムロール込み、floor済み整数)。 */
    public record CatalystManaReduction(int flat, int percent) {
        public static final CatalystManaReduction NONE = new CatalystManaReduction(0, 0);
    }

    /**
     * 触媒の解決済み {@code mana-cost-reduction-flat}/{@code -percent} を返す(P2-Java)。
     * 触媒のライブ ItemStack を {@link WeaponAttackStatResolver#resolveFull} でフル解決(fixed +
     * per-quality(触媒自身のPDC品質) + random)し、2キーの値を {@code (int) Math.floor(...)} で整数化する。
     *
     * <p>触媒が {@code null}/AIR、TF未ロード、resolver未初期化、または解決失敗時は
     * {@link CatalystManaReduction#NONE}(0,0)にフォールバックする(fail-open)。
     * キーはエディタが書き込むハイフン区切り("mana-cost-reduction-flat" 等)を第一候補に読み、
     * 見つからなければ canonical(snake_case)形へフォールバックして探す(登録経路の表記揺れに対する保険)。
     */
    public static CatalystManaReduction resolveCatalystManaReduction(ItemStack catalyst) {
        if (catalyst == null || catalyst.getType().isAir()) {
            return CatalystManaReduction.NONE;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return CatalystManaReduction.NONE;
            }
            WeaponAttackStatResolver resolver = tf.weaponAttackStats();
            if (resolver == null) {
                return CatalystManaReduction.NONE;
            }
            Map<String, Double> resolved = resolver.resolveFull(catalyst);
            int flat = (int) Math.floor(statValue(resolved, MANA_REDUCTION_FLAT_KEY));
            int percent = (int) Math.floor(statValue(resolved, MANA_REDUCTION_PERCENT_KEY));
            return new CatalystManaReduction(flat, percent);
        } catch (Throwable t) {
            return CatalystManaReduction.NONE;
        }
    }

    /**
     * {@code resolved} から {@code key} の値を返す。まず完全一致(エディタが書くハイフン区切りの生キー)、
     * 次に canonical(snake_case)完全一致、最後に canonical 同値の任意キーの順に探し、いずれも無ければ
     * {@code 0.0}。動的登録(登録経路)がキーをcanonicalize しない場合とする場合の双方に対応するための保険。
     */
    private static double statValue(Map<String, Double> resolved, String key) {
        Double exact = resolved.get(key);
        if (exact != null) {
            return exact;
        }
        String canonicalKey = StatKeys.canonical(key);
        Double canonicalExact = resolved.get(canonicalKey);
        if (canonicalExact != null) {
            return canonicalExact;
        }
        for (Map.Entry<String, Double> entry : resolved.entrySet()) {
            if (StatKeys.canonical(entry.getKey()).equals(canonicalKey)) {
                return entry.getValue();
            }
        }
        return 0.0;
    }

    // ============================================================
    // P9: 詠唱成功時のアイテムクールダウンゲージ(武器CT相当)
    //
    // 近接主命中(CombatListener)は既存の武器CTゲージを表示するが、触媒スペルの詠唱には何の
    // アイテムクールダウンゲージも出ない。詠唱成功時に触媒自身のクールダウン(秒)でゲージを表示する。
    // 2026-08-02: 呼び出し元は触媒(catalysts.yml登録品)経由に限らない。SpellCaster の
    // item-cooldown 汎用パス(触媒/魔導書のどちらもCTを持たない詠唱で、実際に右クリックした
    // アイテム自身の item-cooldown ステを使う経路)からも呼ばれる。
    // ============================================================

    /**
     * 触媒(または任意のアイテム)の詠唱後クールダウンゲージを表示する(P9)。
     * アイテムの解決済み {@code item-cooldown} ステ(秒)を優先して使用し、未設定/0以下なら
     * {@code fallbackSeconds}(呼び出し側が渡す触媒自身の {@code cooldown} 秒設定)にフォールバックする。
     * 両方とも0以下ならno-op(ゲージ無し)。
     *
     * <p>{@code player}/{@code item} が {@code null}・AIRの場合や例外時はno-op(fail-open)。
     *
     * @param player          詠唱に成功したプレイヤー
     * @param item            クールダウンを表示するアイテム(cooldown_group 刻印済みならグループ単位、無ければマテリアル単位)
     * @param fallbackSeconds {@code item-cooldown} ステ未設定時に使う秒数(触媒の {@code cooldown} 等)
     */
    /**
     * アイテムの解決済み {@code item-cooldown} ステ(秒)を返す。TF未ロード/解決失敗/未設定は 0.0。
     * 触媒詠唱のCTゲート判定 (このアイテムがCT設定を持つか) に使う。
     */
    public static double itemCooldownSeconds(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0.0;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return 0.0;
            }
            WeaponAttackStatResolver resolver = tf.weaponAttackStats();
            if (resolver == null) {
                return 0.0;
            }
            return resolver.itemCooldownSeconds(item);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    public static void startItemCooldown(Player player, ItemStack item, double fallbackSeconds) {
        if (player == null || item == null || item.getType().isAir()) {
            return;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return;
            }
            WeaponAttackStatResolver resolver = tf.weaponAttackStats();
            if (resolver == null) {
                return;
            }
            double seconds = resolver.itemCooldownSeconds(item);
            if (seconds <= 0.0) {
                seconds = fallbackSeconds;
            }
            if (seconds <= 0.0) {
                return;
            }
            // cooldown_reduction (アイテムCT短縮ステ)を近接側(CombatListener#startItemCooldown)と
            // 同じ規則で適用する(2026-08-02)。従来はここが未適用で、杖/触媒詠唱のCTだけ短縮ステの
            // 対象外という食い違いがあった。近接と全く同じ下限クランプ(最大90%短縮、下限5%は必ず残す)
            // を踏襲し、CTが0まで削れて無限連射になる穴を作らない。tfStatTotal は装備+skilltree perk
            // 合算(全ソース)を返すため、CombatListener側の aggregator.aggregate(...).totalOf(...) と
            // 同じ値になる。
            double reduction = tfStatTotal(player, "cooldown_reduction");
            if (Double.isFinite(reduction) && reduction > 0.0) {
                seconds = seconds * Math.max(0.05, 1.0 - Math.min(0.9, reduction));
            }
            long ticks = WeaponAttackStatResolver.cooldownTicksFor(seconds);
            if (ticks > 0) {
                // ItemStack版 setCooldown: TFが刻印する use_cooldown.cooldown_group (material+CMD単位)
                // でゲージを分離する。グループ未刻印なら従来どおりマテリアル共有にフォールバック。
                player.setCooldown(item, (int) ticks);
            }
        } catch (Throwable t) {
            // TF未ロード / API不整合: クールダウンゲージ表示はスキップ(既存の詠唱成功処理は継続)。
        }
    }

    /**
     * 儀式で消費したソースを、TF 側の「累計カウンタ」PDC へ加算する(2026-07-31)。
     * 第2目標「累計1億ソース」の達成判定({@code achievements.yml} の
     * {@code trigger.type: counter} / {@code counter: source_spent})がこの値を読む。
     *
     * <p><b>キーを文字列で組んでいる理由</b>: TF 側の {@code PdcKeys#lifetimeCounterKey} を呼ぶと
     * compileOnly の {@code libs/TrinityForge.jar} を作り直さないとフォークがビルドできなくなる。
     * このカウンタは PDC への単純な加算で TF のクラスを一切必要としないため、
     * 綴りだけ合わせて疎結合のままにしてある。TF 側は {@code LifetimeCounterKeyTest} で
     * この文字列を固定しているので、片方だけ変わると気づける。
     *
     * <p>累計は単調増加でなければ意味が壊れるので、{@code amount <= 0} は無視する。
     */
    public static void recordSourceSpent(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        try {
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey("trinityforge", "counter_source_spent");
            PersistentDataContainer pdc = player.getPersistentDataContainer();
            long current = pdc.getOrDefault(key, PersistentDataType.LONG, 0L);
            long updated = current + amount;
            if (updated < current) {
                updated = Long.MAX_VALUE; // 飽和(1億の目標に対して事実上の無限)
            }
            pdc.set(key, PersistentDataType.LONG, updated);
        } catch (Throwable t) {
            // 集計に失敗しても儀式自体は止めない(fail-open)。
        }
    }

    private static void warnUnavailableOnce() {
        if (!unavailableLogged) {
            unavailableLogged = true;
            Logger logger = Bukkit.getLogger();
            logger.warning("[ArsPaper] TrinityForge が見つかりません。魔法ダメージは対称パイプライン未経由のまま適用されます。");
        }
    }
}
