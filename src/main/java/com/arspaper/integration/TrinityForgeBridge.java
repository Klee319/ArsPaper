package com.arspaper.integration;

import com.trinityforge.TrinityForge;
import com.trinityforge.combat.AttackStats;
import com.trinityforge.combat.SymmetricCombatService;
import com.trinityforge.pdc.BindType;
import com.trinityforge.pdc.ItemData;
import org.bukkit.Bukkit;
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
     * スペル基礎ダメージを対称パイプラインへ供給し、最終魔法ダメージを返す。
     *
     * @param casterUuid 詠唱者UUID
     * @param victim     被弾エンティティ（{@code PersistentDataHolder}）
     * @param spellBase  スペル基礎ダメージ（Ars攻撃力 + 増減グリフを内包済み）
     * @return 8stepパイプライン後の最終ダメージ。サービス未ロード時は {@code spellBase} をそのまま返す
     */
    public static double magicalFinalDamage(UUID casterUuid, LivingEntity victim, double spellBase) {
        SymmetricCombatService service = combatService();
        if (service == null) {
            warnUnavailableOnce();
            return spellBase;
        }
        // 触媒の攻撃ステ（会心/貫通等）は現状未連携のため plain(0)。
        // service が spellBase を defaultDamage として注入し、最終ダメージを計算する。
        return service.magicalFinalDamage(casterUuid, victim, spellBase, AttackStats.plain(0));
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
