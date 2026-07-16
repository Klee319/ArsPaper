package com.arspaper.integration;

import com.trinityforge.TrinityForge;
import com.trinityforge.combat.AttackStats;
import com.trinityforge.combat.SymmetricCombatService;
import com.trinityforge.combat.WeaponAttackStatResolver;
import com.trinityforge.pdc.BindType;
import com.trinityforge.pdc.ItemData;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

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
    private static volatile boolean unavailableLogged = false;

    private TrinityForgeBridge() {
    }

    /**
     * TrinityForge の対称戦闘サービスを取得する。未ロード時は {@code null}。
     */
    public static SymmetricCombatService combatService() {
        SymmetricCombatService service = cachedService;
        if (service != null) {
            return service;
        }
        try {
            if (Bukkit.getPluginManager().getPlugin(PLUGIN_NAME) == null) {
                return null;
            }
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return null;
            }
            service = tf.combatService();
            cachedService = service;
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
     * スペル基礎ダメージを対称パイプラインへ供給し、触媒の攻撃ステを乗せた最終魔法ダメージを返す。
     *
     * <p>触媒（ワンド/スペルブック）の会心・貫通等は {@link WeaponAttackStatResolver#forItem} で
     * {@link AttackStats} に導出し、対称パイプラインへ供給する。増減グリフ(Amplify/Dampen)は
     * 呼び出し側で {@code spellBase} に内包済み・会心/貫通はTF側という層分離を維持するため、
     * ここでは {@code spellBase} に触媒ステを二重計上しない（TF側 AttackStats が別レイヤーで加味する）。
     *
     * <p>フォールバック（挙動不変の安全策）:
     * <ul>
     *   <li>TF未ロード（{@link #combatService()}==null）→ {@code spellBase} を素通し（fail-open）。</li>
     *   <li>触媒が {@code null} / 取得失敗 / resolver未初期化 → {@link AttackStats#plain(0)} 相当で計算。</li>
     * </ul>
     *
     * @param casterUuid 詠唱者UUID
     * @param victim     被弾エンティティ（{@code PersistentDataHolder}）
     * @param spellBase  スペル基礎ダメージ（Ars攻撃力 + 増減グリフを内包済み）
     * @param catalyst   詠唱に使った触媒 ItemStack（ワンド/スペルブック）。特定不能なら {@code null}
     * @return 8stepパイプライン後の最終ダメージ。サービス未ロード時は {@code spellBase} をそのまま返す
     */
    public static double magicalFinalDamage(UUID casterUuid, LivingEntity victim, double spellBase,
                                            ItemStack catalyst) {
        SymmetricCombatService service = combatService();
        if (service == null) {
            warnUnavailableOnce();
            return spellBase;
        }
        // ハイブリッド設計: 魔法基礎ダメージ = グリフ基礎ダメージ(spellBase) × 触媒の攻撃力(attack-power)。
        // 触媒に attack-power が定義されていなければ係数 1.0（グリフダメージ据え置き＝従来挙動）。
        double effectiveBase = spellBase * catalystAttackPowerFactor(catalyst);
        // service が effectiveBase を defaultDamage として注入し、触媒 AttackStats(会心/貫通)を別レイヤーで加味する。
        return service.magicalFinalDamage(casterUuid, victim, effectiveBase, resolveCatalystStats(catalyst));
    }

    /**
     * 触媒の攻撃力(attack-power)を「グリフ基礎ダメージへの倍率」として返す（ハイブリッド設計）。
     *
     * <p>触媒に attack-power が定義されていない（{@code <= 0}）場合は {@code 1.0}（グリフダメージ据え置き）。
     * {@code null} / resolver未初期化 / 例外時も {@code 1.0} にフォールバックし、attack-power 未設定の
     * 触媒では従来どおりグリフダメージがそのまま基礎ダメージになる（挙動不変）。
     */
    private static double catalystAttackPowerFactor(ItemStack catalyst) {
        if (catalyst == null) {
            return 1.0;
        }
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) {
                return 1.0;
            }
            WeaponAttackStatResolver resolver = tf.weaponAttackStats();
            if (resolver == null) {
                return 1.0;
            }
            double power = resolver.attackPowerOf(catalyst);
            return power > 0 ? power : 1.0;
        } catch (Throwable t) {
            return 1.0;
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
            victim.damage(finalDamage, caster);
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
        if (meta == null) {
            return;
        }
        try {
            ItemData data = ItemData.of(meta);
            data.setRollSeed(rollSeed);
            int clamped = Math.max(ItemData.MIN_QUALITY, Math.min(ItemData.MAX_QUALITY, quality));
            data.setQuality(clamped);
        } catch (Throwable t) {
            // TrinityForge 未ロード: 厳選 PDC はスキップ（既存生成は維持）。
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

    private static void warnUnavailableOnce() {
        if (!unavailableLogged) {
            unavailableLogged = true;
            Logger logger = Bukkit.getLogger();
            logger.warning("[ArsPaper] TrinityForge が見つかりません。魔法ダメージは対称パイプライン未経由のまま適用されます。");
        }
    }
}
