package com.arspaper.item;

import com.arspaper.ArsPaper;
import com.arspaper.enchant.ArsEnchantments;
import com.arspaper.mana.ManaKeys;
import com.arspaper.mana.ManaManager;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import com.arspaper.integration.TrinityForgeBridge;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーの装備状態を監視し、マナボーナスを計算・更新する。
 * 防具システム(armors.yml)は撤去済みで、マナ/Ars系ステはTrinityForgeのitem-catalog/item-statsから
 * 供給される。防具4部位に加えメインハンド・オフハンド(offhand-stats-apply対象のみ)を含む
 * 全装備スロットでマナ系ステを集約する。被ダメ/与ダメ時のマナ回復もここで処理する。
 *
 * <p><b>2026-07-31 (F2)</b>: 装着スレッドの収集も防具4部位限定をやめ、メインハンド・
 * オフハンド(offhand-stats-apply対象のみ)へ広げた。ただし<b>広げたのは数値だけ</b>で、
 * 常時ポーション効果・飛行・バックパックは着用防具に留める ── 線引きと理由は
 * {@link ThreadApplicationPolicy} の javadoc に一元化してある。
 */
public class ArmorManaListener implements Listener {

    private static final int POTION_DURATION = -1; // Paper 1.20+: 無限持続
    private final JavaPlugin plugin;

    /**
     * スレッドが付与しうるポーション効果の全種は、もう固定配列ではなく
     * {@link ThreadConfig#allPotionTypes()} が動的に返す(2026-08-08、threads.yml の
     * {@code potion-effect:} でスレッドごとに型を選べるようにした際に動的化)。
     * {@link #updatePotionEffects} はこの動的集合を都度取得して走査する ── ここが静的配列の
     * ままだと、config で新しい型を選べても【付けても効かず、外しても剥がれない】(どちらも無言)。
     * 新しい許可効果を増やすときは {@link ThreadConfig#ALLOWED_POTION_EFFECTS} を編集すればよく、
     * このクラスを触る必要はない。
     */

    public ArmorManaListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        scheduleRecalc(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        // 防具スロット/シフトクリックに加え、オフハンドスロット(raw slot 40)への直接クリックでも
        // 再計算をスケジュールする。全装備スロット集約化に伴い、武器持ち替えでマナ最大値を追従させる必要がある。
        //
        // 2026-07-31 (F2): ホットバー(slot 0-8)への直接クリックと数字キーのホットバースワップも
        // 対象に加えた。装着スレッドのステがメインハンドからも乗るようになったため、
        // 「インベントリ画面で選択中のホットバー枠の武器を入れ替える」経路が漏れていると
        // ホイールを回すまでステが古いまま残る(PlayerItemHeldEvent は選択スロットが
        // 変わらないので飛ばない)。
        boolean ownInventory = event.getClickedInventory() == player.getInventory();
        boolean heldSlotTouched = ownInventory && event.getSlot() >= 0 && event.getSlot() <= 8;
        boolean offhandSlotTouched = ownInventory && event.getSlot() == 40;
        if (event.getSlotType() == InventoryType.SlotType.ARMOR
            || event.isShiftClick()
            || event.getHotbarButton() >= 0
            || heldSlotTouched
            || offhandSlotTouched) {
            scheduleRecalc(player);
        }
    }

    /**
     * ホットバー選択スロット変更（メインハンド持ち替え）でも再計算する。
     * 全装備スロット集約化(Phase: 防具撤去)により、武器持ち替えでマナボーナスが変わるため必須。
     */
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        scheduleRecalc(event.getPlayer());
    }

    /**
     * F キーによるメインハンド/オフハンド入れ替えでも再計算する。
     */
    @EventHandler
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        scheduleRecalc(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        recalculateArmorBonus(event.getPlayer());
    }

    /**
     * 被ダメ時マナ回復: プレイヤーがエンティティからダメージを受けた時に装備の hit_mana_recovery 分マナを回復。
     * 自己ダメージ（落下、炎、窒息等）は除外し、エンティティ起因のダメージのみ対象。
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // 実際にダメージが通った時のみ回復する。最終ダメ0(完全ガード/後段cancel相当)では回復しない。
        // HIGHEST + ignoreCancelled で他プラグインの軽減/キャンセルが確定した後に評価する。
        if (event.getFinalDamage() <= 0.0) return;

        int recovery = player.getPersistentDataContainer()
            .getOrDefault(ManaKeys.ARMOR_HIT_MANA_RECOVERY, PersistentDataType.INTEGER, 0);
        if (recovery > 0) {
            ManaManager mm = ArsPaper.getInstance().getManaManager();
            if (mm != null) mm.addMana(player, recovery);
        }
    }

    /**
     * 与ダメ時マナ回復: プレイヤーが敵に「近接の直接攻撃」でダメージを与えた時に damage_mana_recovery 分回復。
     * 呪文/投射などプレイヤー起因の非近接ダメージは対象外(AoE呪文でのマナ増殖=自己永続詠唱ループを防ぐ)。
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        // 近接の直接攻撃(通常/なぎ払い)のみ。MAGIC/PROJECTILE 等の呪文・遠隔ダメージでは回復しない。
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        // 実際にダメージが通った時のみ回復(0ダメージのフェイントで回復しない)。
        if (event.getFinalDamage() <= 0.0) return;

        int recovery = player.getPersistentDataContainer()
            .getOrDefault(ManaKeys.ARMOR_DAMAGE_MANA_RECOVERY, PersistentDataType.INTEGER, 0);
        if (recovery > 0) {
            ManaManager mm = ArsPaper.getInstance().getManaManager();
            if (mm != null) mm.addMana(player, recovery);
        }
    }

    private void scheduleRecalc(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    recalculateArmorBonus(player);
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * 集計中の合計値をまとめて運ぶ入れ物。装備1点ぶんの収集を
     * {@link #collectThreadsInto} へ切り出すために必要(int の out 引数を10本並べる代わり)。
     */
    private static final class ThreadTotals {
        int manaBonus;
        int manaRegen;
        int hitRecovery;
        int damageRecovery;
        int threadMana;
        int threadRegen;
        int costReduction;
        int manaMaxPercent;
        int regenPercent;
        boolean flightThread;
        /** 型 → amplifier(レベル-1)。同じ型を複数のスレッドが与える場合は amplifier の最大値を採る。 */
        final Map<PotionEffectType, Integer> potions = new HashMap<>();
        /** 装着スレッド1個ごとの厳選ステ + item-stats ステの合計(canonical化はTF側)。 */
        final Map<String, Double> combatStats = new LinkedHashMap<>();
        /** 同種スレッドの合計個数(thread-sets.yml の累積しきい値判定用)。 */
        // W-102 で ThreadType が enum ではなくなった(threads.yml から実行時に増える)ので EnumMap は使えない。
        // 1 id につき1インスタンスなのは変わらないため、参照同一性で数えるこの用途は LinkedHashMap でそのまま動く。
        final Map<ThreadType, Integer> counts = new LinkedHashMap<>();
    }

    /**
     * プレイヤーの装備中アイテムのマナボーナス合計を再計算する。
     *
     * <p>防具は廃止済みで、TrinityForgeのitem-catalog/item-statsから供給される前提となったため、
     * マナ/Ars系ステ(mana_bonus/mana_regen/hit_mana_recovery/damage_mana_recovery)は
     * 防具4部位に限定せず、メインハンド・オフハンドも含めた全装備スロットで集約する。
     * ただしオフハンドは、そのアイテムが {@code offhand-stats-apply: true} の場合のみ加算対象とする
     * ({@link TrinityForgeBridge#offhandStatsApply}で判定)。
     * TF item-statsが唯一のソースであり、armors.yml由来の加算・4部位セットボーナスは廃止した。
     *
     * <p><b>装着スレッドも同じ3スロット群から集める(2026-07-31 F2)。</b>ただし
     * 常時ポーション効果・飛行・バックパックは着用防具のみ
     * ({@link ThreadApplicationPolicy#appliesAmbientEffects})。
     */
    public static void recalculateArmorBonus(Player player) {
        ThreadTotals totals = new ThreadTotals();
        int totalEnchantMana = 0;
        int totalEnchantRegen = 0;

        ThreadConfig threadConfig = ArsPaper.getInstance().getThreadConfig();

        // 防具4部位: エンチャントボーナス + TF item-statsマナ加算 + スレッド収集(常時効果もここだけ)。
        for (ItemStack armorPiece : player.getInventory().getArmorContents()) {
            if (armorPiece == null || !armorPiece.hasItemMeta()) continue;

            // エンチャントボーナス（防具限定機能のため従来どおり防具4部位のみ走査）
            try {
                int regenLevel = ArsEnchantments.getManaRegenLevel(armorPiece);
                if (regenLevel > 0) {
                    totalEnchantRegen += ArsEnchantments.getManaRegenForLevel(regenLevel);
                }
                int boostLevel = ArsEnchantments.getManaBoostLevel(armorPiece);
                if (boostLevel > 0) {
                    totalEnchantMana += ArsEnchantments.getManaBoostForLevel(boostLevel);
                }
            } catch (Exception e) {
                ArsPaper.getInstance().getLogger().warning(
                    "Failed to read enchantment from armor: " + e.getMessage());
            }

            // TF item-statsマナ加算（唯一のソース）
            addTfManaDeltas(totals, armorPiece);
            collectThreadsInto(totals, armorPiece, player, threadConfig,
                    ThreadApplicationPolicy.SlotOrigin.WORN_ARMOR);
        }

        // メインハンド: 武器/触媒などは加算。防具を手持ちした場合は着用時のみ(二重加算防止)。
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!isWornOnlyArmorMaterial(mainHand)) {
            addTfManaDeltas(totals, mainHand);
            collectThreadsInto(totals, mainHand, player, threadConfig,
                    ThreadApplicationPolicy.SlotOrigin.MAIN_HAND);
        }

        // オフハンド: そのアイテムの offhand-stats-apply=true の場合のみ加算対象。
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhandStatsApplies(offhand)) {
            addTfManaDeltas(totals, offhand);
            collectThreadsInto(totals, offhand, player, threadConfig,
                    ThreadApplicationPolicy.SlotOrigin.OFF_HAND);
        }

        int totalBonus = totals.manaBonus;
        int totalArmorRegen = totals.manaRegen;
        int totalHitRecovery = totals.hitRecovery;
        int totalDamageRecovery = totals.damageRecovery;
        int totalThreadMana = totals.threadMana;
        int totalThreadRegen = totals.threadRegen;
        int totalCostReduction = totals.costReduction;
        int totalMaxPercent = totals.manaMaxPercent;
        int totalRegenPercent = totals.regenPercent;
        boolean hasFlightThread = totals.flightThread;
        Map<PotionEffectType, Integer> activeThreadPotions = totals.potions;
        Map<String, Double> threadCombatStats = totals.combatStats;
        Map<ThreadType, Integer> threadCounts = totals.counts;

        // 2026-07-26 マナ系ステ穴埋め(縮小版タスク4'、オーケストレータ決定): hit_mana_recovery/
        // damage_mana_recoveryは、mana_bonus/mana_regenと違いArsNativeBridge(パーク/役職/永続バフ/
        // base-stats)側に相当経路が無い(その2キーはArsNativeBridgeが唯一の非装備供給源として担当する
        // 設計へ変更済み — このメソッドでは意図的に mana_bonus/mana_regen を一切読まない)。
        // TF公開API TrinityForgeBridge#tfNonItemStatTotal で非装備分(パーク/役職/永続バフ/base-stats)
        // だけを取得して加算する — 装備分は上のtfManaDeltas(armor/mainhand/offhand)が引き続き担当して
        // おり、非装備専用APIなので二重計上しない。TF未ロード/例外時は0(fail-open、tfNonItemStatTotal自身
        // の契約)。
        totalHitRecovery += (int) Math.round(
                TrinityForgeBridge.tfNonItemStatTotal(player, "hit_mana_recovery"));
        totalDamageRecovery += (int) Math.round(
                TrinityForgeBridge.tfNonItemStatTotal(player, "damage_mana_recovery"));

        // 同種合計個数に応じた thread-sets.yml の累積セット効果を足し込み、TF戦闘パイプラインへ渡す
        // (空なら PDC キーを消して古いステを残さない)。全体を try で隔離しマナ/飛行処理と分離する。
        try {
            ThreadSetConfig threadSetConfig = ArsPaper.getInstance().getThreadSetConfig();
            // 乗算モードのセット効果は加算チャネルへ混ぜてはいけない(意味が違う)。別Mapに集めて
            // 別PDCキーへ書く。TF 側は「加算合算が終わった総合値」へ乗算レイヤを掛ける。
            // 2026-08-22(W-186): レイヤID -> ステ -> 増分 の二段。同じレイヤ同士だけを足し合わせ、
            // レイヤをまたいだ合算はしない(TF 側で別レイヤは掛け算になるため、ここで潰すと意味が変わる)。
            Map<String, Map<String, Double>> threadCombatMultipliers = new LinkedHashMap<>();
            if (threadSetConfig != null) {
                for (Map.Entry<ThreadType, Integer> entry : threadCounts.entrySet()) {
                    threadSetConfig.cumulativeBonus(entry.getKey().getId(), entry.getValue())
                            .forEach((key, value) -> threadCombatStats.merge(key, value, Double::sum));
                    threadSetConfig.cumulativeMultiplier(entry.getKey().getId(), entry.getValue())
                            .forEach((layer, stats) -> {
                                Map<String, Double> into = threadCombatMultipliers
                                        .computeIfAbsent(layer, k -> new LinkedHashMap<>());
                                stats.forEach((key, value) -> into.merge(key, value, Double::sum));
                            });
                }
            }
            TrinityForgeBridge.writeAddonCombatStats(player, threadCombatStats);
            TrinityForgeBridge.writeAddonCombatMultipliers(player, threadCombatMultipliers);
        } catch (Throwable tfUnavailable) {
            // TF未ロード / 連携失敗: 戦闘ステPDCはスキップ(マナ/飛行/ポーションへ波及させない)。
        }

        // PDCに書き込み
        PersistentDataContainer playerPdc = player.getPersistentDataContainer();
        playerPdc.set(ManaKeys.ARMOR_MANA_BONUS, PersistentDataType.INTEGER, totalBonus);
        playerPdc.set(ManaKeys.ARMOR_REGEN_BONUS, PersistentDataType.INTEGER, totalArmorRegen);
        playerPdc.set(ManaKeys.ARMOR_HIT_MANA_RECOVERY, PersistentDataType.INTEGER, totalHitRecovery);
        playerPdc.set(ManaKeys.ARMOR_DAMAGE_MANA_RECOVERY, PersistentDataType.INTEGER, totalDamageRecovery);
        playerPdc.set(ManaKeys.THREAD_MANA_BONUS, PersistentDataType.INTEGER, totalThreadMana);
        playerPdc.set(ManaKeys.THREAD_REGEN_BONUS, PersistentDataType.INTEGER, totalThreadRegen);
        playerPdc.set(ManaKeys.ENCHANT_MANA_BONUS, PersistentDataType.INTEGER, totalEnchantMana);
        playerPdc.set(ManaKeys.ENCHANT_REGEN_BONUS, PersistentDataType.INTEGER, totalEnchantRegen);
        playerPdc.set(ManaKeys.THREAD_COST_REDUCTION, PersistentDataType.INTEGER, totalCostReduction);
        playerPdc.set(ManaKeys.THREAD_MANA_MAX_PERCENT, PersistentDataType.INTEGER, totalMaxPercent);
        playerPdc.set(ManaKeys.THREAD_REGEN_PERCENT, PersistentDataType.INTEGER, totalRegenPercent);

        updatePotionEffects(player, activeThreadPotions, threadConfig);

        // 飛行スレッド: エリトラなしで滑空可能にする（インスタンスメソッド呼出）
        ArmorManaListener listener = ArsPaper.getInstance().getArmorManaListener();
        if (listener != null) {
            listener.updateFlightThread(player, hasFlightThread);
        }

        ManaManager manaManager = ArsPaper.getInstance().getManaManager();
        if (manaManager != null) {
            int currentMana = manaManager.getCurrentMana(player);
            int newMax = manaManager.getMaxMana(player);
            if (currentMana > newMax) {
                currentMana = newMax;
            }
            manaManager.setCurrentMana(player, currentMana);
        }
    }

    /**
     * 装備1点ぶんの装着スレッドを {@code totals} へ足し込む(防具4部位／メインハンド／オフハンド共通)。
     *
     * <p>実効枠数(装備自身のitem-stats {@code thread_slots})を超える分のスレッドは適用しない。GUI側
     * ({@link com.arspaper.gui.ThreadGui})と同じ {@link TrinityForgeBridge#tfEffectiveThreadSlotCap}
     * を使い、表示と効果を一致させる。枠数が減る方向に変わっても PDC上のスレッドデータ自体は
     * 消さない(枠が戻れば復活する)。
     * 2026-07-26: 装着者のperk/ステータスで枠を増やす経路({@code thread_slot_cap_bonus})は
     * 「スレッド枠拡張の儀式」と機能が重複するため廃止済み。枠は装備側だけで決まる。
     *
     * <p><b>{@code origin} で変わるのは常時効果だけ</b>。数値(マナ系・厳選ステ・セット効果個数)は
     * どのスロットでも同じように足すが、ポーション効果と飛行は
     * {@link ThreadApplicationPolicy#appliesAmbientEffects} が真のスロット(=着用防具)でのみ拾う
     * ── 手持ちで発動させると持ち替えのたびに点滅するため(線引きの理由は
     * {@link ThreadApplicationPolicy} の javadoc)。
     */
    private static void collectThreadsInto(ThreadTotals totals, ItemStack item, Player player,
                                           ThreadConfig threadConfig,
                                           ThreadApplicationPolicy.SlotOrigin origin) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }
        if (!ThreadApplicationPolicy.appliesNumericStats(origin)) {
            return;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        int effectiveSlotCap = TrinityForgeBridge.tfEffectiveThreadSlotCap(
                TrinityForgeBridge.resolveFullItemStats(item), player);
        if (effectiveSlotCap <= 0) {
            return;
        }
        boolean ambient = ThreadApplicationPolicy.appliesAmbientEffects(origin);

        // 装着済みスレッド1個ずつ、その枠の(rollSeed, quality)で戦闘ステを「1回だけ」解決する
        // (2026-08-03統合: 以前はここで(a)厳選PDCの生値合算(b)テンプレ値(quality=0固定)合算の
        // 2回を別々に足していて二重計上になっていた。ArmorManaListener#collectThreadsIntoの
        // javadoc/報告参照)。
        for (SocketedThreads.Entry equipped : SocketedThreads.read(pdc, effectiveSlotCap)) {
            ThreadType thread = equipped.type();

            // 魂縛(2026-08-25 W-259): ダンジョン産スレッドは所有者以外には【何も配らない】。
            // ユーザーが最初に指摘した抜け道「他の人にスレッド付きの武器とかが渡された場合に
            // どうやって対処しよう」への答えがここ ── ThreadGui のゲートは
            // 「他人のスレッドを挿す」しか止められず、【自分で挿してから装備ごと渡す】は素通りする。
            // ⚠ continue の位置に注意: counts への加算より【前】。数えてしまうと
            //   thread-sets.yml のセット効果だけが他人にも乗る。
            if (TreasureThreadSoulbindPolicy.isSoulbound(thread)
                    && !TreasureThreadSoulbindPolicy.mayUse(equipped.owner(), player.getUniqueId())) {
                continue;
            }
            totals.threadMana += threadConfig.getManaBonus(thread);
            totals.threadRegen += threadConfig.getRegenBonus(thread);
            totals.costReduction += threadConfig.getCostReduction(thread);
            totals.manaMaxPercent += threadConfig.getManaMaxPercent(thread);
            totals.regenPercent += threadConfig.getRegenPercent(thread);
            totals.hitRecovery += threadConfig.getHitManaRecovery(thread);
            totals.damageRecovery += threadConfig.getDamageManaRecovery(thread);
            if (ambient) {
                // config優先(ThreadConfig#getPotionEffect)で解決する。潜在的な型は
                // ThreadConfig#allPotionTypes() が超集合として持つので、ここで見つかった型は
                // 必ず updatePotionEffects の走査対象に含まれる。
                PotionEffectType potionType = threadConfig.getPotionEffect(thread);
                if (potionType != null) {
                    int amplifier = threadConfig.getPotionLevel(thread) - 1;
                    totals.potions.merge(potionType, amplifier, Integer::max);
                }
            }
            if (ambient && threadConfig.isFlightThread(thread)) {
                totals.flightThread = true;
            }

            // 戦闘ステ: 同種個数を数え、そのスロットの厳選(rollSeed/quality)込みで item-stats を
            // 一度だけ解決する。TF連携は完全に隔離: 万一のlinkageエラー等でも上のマナ/飛行/
            // ポーション処理を止めない。
            totals.counts.merge(thread, 1, Integer::sum);
            try {
                // 呼び出し元が返すMapの可変性を仮定しない(TF側の実装差で
                // UnmodifiableMap が返ればremoveで落ちる)ため、必ず自前のMapへ写してから仕分ける。
                Map<String, Double> threadStats = new LinkedHashMap<>(
                        TrinityForgeBridge.resolveThreadStats(thread.getBaseMaterial(),
                                thread.getCustomModelData(), equipped.quality(), equipped.rollSeed()));
                // 2026-08-03: マナ系5キーだけは combatStats(=addon戦闘チャネル)へ流しても
                // 誰も読まず無言で死ぬ。threads.yml と同じ整数カウンタへ移す
                // (経路の詳細と単位変換の理由は ThreadManaStatRouting の javadoc)。
                ThreadManaStatRouting.Deltas mana = ThreadManaStatRouting.extract(threadStats);
                totals.threadMana += mana.manaBonus();
                totals.threadRegen += mana.manaRegen();
                totals.hitRecovery += mana.hitManaRecovery();
                totals.damageRecovery += mana.damageManaRecovery();
                totals.costReduction += mana.costReductionPercent();
                threadStats.forEach((key, value) -> totals.combatStats.merge(key, value, Double::sum));
            } catch (Throwable tfUnavailable) {
                // TF未ロード等: このスレの戦闘ステはスキップ(マナ機能は無影響)。
            }
        }
    }

    /** {@link #tfManaDeltas} の結果を {@code totals} のマナ系4カウンタへ足す。 */
    private static void addTfManaDeltas(ThreadTotals totals, ItemStack item) {
        int[] delta = tfManaDeltas(item);
        totals.manaBonus += delta[0];
        totals.manaRegen += delta[1];
        totals.hitRecovery += delta[2];
        totals.damageRecovery += delta[3];
    }

    /**
     * 指定アイテムのTF item-statsから、マナ系4種(mana_bonus/mana_regen/hit_mana_recovery/
     * damage_mana_recovery)を [bonus, regen, hitRecovery, damageRecovery] の順で返す。
     * 装備品の「実際の」CustomModelDataで解決する(未設定ならnullを渡し、TF側のmaterialのみ解決に委ねる)。
     * null/AIR/TF未ロード/例外時は全て0(fail-open)。
     */
    private static int[] tfManaDeltas(ItemStack item) {
        if (item == null || item.getType().isAir()) return new int[4];
        try {
            Map<String, Double> tfManaStats = TrinityForgeBridge.resolveFullItemStats(item);
            return new int[] {
                (int) Math.round(tfManaStats.getOrDefault("mana_bonus", 0.0)),
                (int) Math.round(tfManaStats.getOrDefault("mana_regen", 0.0)),
                (int) Math.round(tfManaStats.getOrDefault("hit_mana_recovery", 0.0)),
                (int) Math.round(tfManaStats.getOrDefault("damage_mana_recovery", 0.0))
            };
        } catch (Throwable tfUnavailable) {
            // TF未ロード等: このアイテム分のTF item-statsマナ加算はスキップ。
            return new int[4];
        }
    }

    /**
     * オフハンドのアイテムがマナ系item-statsの加算対象か判定する
     * ({@link TrinityForgeBridge#offhandStatsApply}へ委譲)。null/AIR/TF未ロード/例外時はfalse。
     */
    private static boolean offhandStatsApplies(ItemStack offhand) {
        if (offhand == null || offhand.getType().isAir()) return false;
        try {
            ItemMeta meta = offhand.hasItemMeta() ? offhand.getItemMeta() : null;
            Integer cmd = (meta != null && meta.hasCustomModelData()) ? meta.getCustomModelData() : null;
            return TrinityForgeBridge.offhandStatsApply(offhand.getType(), cmd);
        } catch (Throwable tfUnavailable) {
            return false;
        }
    }

    /**
     * 防具は装着スロットでのみマナ/スレッド寄与。手持ち二重加算を防ぐ
     * ({@link ThreadApplicationPolicy#isArmorSlotMaterial} が判定の正本)。
     */
    private static boolean isWornOnlyArmorMaterial(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return ThreadApplicationPolicy.isArmorSlotMaterial(item.getType());
    }

    /**
     * スレッド由来の効果の「見た目上の」目印(無期限=isInfinite)。
     *
     * <p>2026-07-26: 以前は付与側が無条件 {@code addPotionEffect} で、Bukkit の仕様上これは
     * 同種の既存効果を**上書き**する。そのため HEALTH_BOOST スレッドを装備したまま
     * 体力増強II（振幅1）のポーションを飲むと、次の再計算（ホットバーのスクロールでも走る）で
     * レベル1・無期限に書き換えられていた。解除側も「無期限なら剥がす」だけで自前由来かを
     * 判定しておらず、他ソースの無期限効果を誤爆で剥がし得た。
     *
     * <p><b>2026-08-08</b>: amplifier<=0 の判定条件は削除した。potion-level(1以上)対応で
     * 自前付与の amplifier が0以外にもなるため、amplifier で自前判定すると
     * レベル2以上のスレッドを外したときに解除できなくなる(【外しても剥がれない】の再発)。
     * 無期限(isInfinite)であることが唯一の識別子で、バニラの通常ポーションは必ず有限期間なので
     * amplifierを見なくても誤検出しない。
     *
     * <p><b>⚠️ 2026-08-18 (W-54)</b>: この判定<b>単独</b>を「自分が付けたか」の判定に使ってはいけない。
     * プレイヤーが管理コマンド等で付けた無期限効果(例: {@code /effect give @s luck infinite})も
     * {@code isInfinite()} を満たすため、無条件にこれだけで剥がすと他人の無期限効果まで誤爆で消す
     * (実サーバ報告: 無期限LUCKを付けた直後にスレッド再計算で消える)。この形状チェックは
     * 「まだ他ソースに上書きされていないか」の確認にのみ使い、「そもそも自分が付けたものか」の
     * 判定は {@link #threadGrantedPotions} の所有権台帳に一元化した。
     */
    private static boolean isThreadGranted(PotionEffect effect) {
        if (effect == null) return false;
        return effect.isInfinite() || effect.getDuration() >= Integer.MAX_VALUE - 100;
    }

    /**
     * プレイヤーごとに「今スレッドが実際に付与している(と自分で記録した)ポーション型→amplifier」を
     * 持つ所有権台帳(2026-08-18, W-54)。
     *
     * <p><b>直した問題</b>: 旧実装は {@link #isThreadGranted(PotionEffect)}(無期限かどうか)だけで
     * 「この効果はスレッドが付けたものか」を判定していた。プレイヤーが無期限 LUCK を手動/管理コマンドで
     * 付けても同じ条件に一致するため、次の再計算(装備変更のたびに走る)で
     * {@code player.removePotionEffect(type)} により無条件に剥がされていた。
     *
     * <p><b>直し方</b>: 「無期限か」ではなく「実際に自分(スレッド)がこのプレイヤー・このタイプへ
     * 付与した記録が残っているか」で所有権を判定する。付与するたびに必ずこの台帳へ記録し
     * ({@link #updatePotionEffects} の付与分岐)、除去は「台帳に記録があり(=自分が付けた)、かつ
     * 現在も無期限のまま(=他ソースに上書きされていない)」の両方を満たす場合だけ行う。
     *
     * <p><b>再起動/再ログインを跨いだ場合の挙動(明示的な設計選択)</b>: この台帳はプロセス内メモリのみ
     * (サーバ再起動で必ず失われる)。再起動直後、台帳に記録が無い状態で無期限効果が残っていた場合は
     * <b>「自分のものではない(=他人/他プラグイン起因)」として扱い、絶対に剥がさない</b>
     * ── 安全側(消さない方向)へ倒す設計判断であり、「台帳が無ければ自分のものとみなして消す」
     * 側は選ばない(まさにこの誤爆がW-54の実害そのものだったため)。
     * 一方でスレッド自身が本来付与すべき効果は自己修復する: {@code onPlayerJoin} が必ず
     * {@link #recalculateArmorBonus} を呼ぶため、対象スレッドをまだ装着していれば同じtickで
     * この台帳が再構築され、以後は通常どおり除去対象になる(装着中のスレッドの状態は毎回PDCから
     * 導出し直すため、台帳の有無に依存しない)。
     */
    private static final Map<UUID, Map<PotionEffectType, Integer>> threadGrantedPotions =
            new ConcurrentHashMap<>();

    private static void updatePotionEffects(Player player, Map<PotionEffectType, Integer> activeThreadPotions,
                                             ThreadConfig threadConfig) {
        Map<PotionEffectType, Integer> owned = threadGrantedPotions.computeIfAbsent(
                // PotionEffectType は Paper 1.21 では enum ではない(Registry 化された Keyed)ので
                // EnumMap は使えない。ここは装備変更のたびに走るがキー数はスレッド種別ぶんしか無い。
                player.getUniqueId(), id -> new ConcurrentHashMap<>());
        for (PotionEffectType type : threadConfig.allPotionTypes()) {
            PotionEffect existing = player.getPotionEffect(type);
            Integer desiredAmplifier = activeThreadPotions.get(type);
            if (desiredAmplifier != null) {
                // 既により強い効果（ポーション等）が乗っているなら格下げしない。
                // amplifierが常に0だった前提の旧判定(ゼロより大きいかだけを見る比較)だと、
                // レベル2以上のスレッド(desiredAmplifier が1以上)が自分自身の付与済み効果を
                // 「既存のほうが強い」と誤判定し一生付かなくなる。desiredAmplifierとの比較に直す。
                if (existing != null && existing.getAmplifier() > desiredAmplifier) {
                    continue;
                }
                player.addPotionEffect(new PotionEffect(
                    type, POTION_DURATION, desiredAmplifier, true, false, true
                ));
                // 所有権台帳を更新: このタイプは今回スレッドが実際に付与したと記録する。
                owned.put(type, desiredAmplifier);
            } else if (ThreadPotionOwnership.mayRemoveGrantedPotion(
                    owned.remove(type), existing != null,
                    existing != null && isThreadGranted(existing),
                    existing == null ? Integer.MIN_VALUE : existing.getAmplifier())) {
                // 剥がす条件は {@link ThreadPotionOwnership#mayRemoveGrantedPotion} が正本:
                // 台帳に付与記録があり、現在も無期限で、かつ amplifier が記録と一致する場合だけ。
                // 台帳に記録が無い無期限効果(再起動を跨いだ/プレイヤー自身・他プラグインが付けた)は
                // 絶対に剥がさない(W-54 第1波)。amplifier が違うものも剥がさない ──
                // 同じ型・無期限のまま他ソースに上書きされた効果を消していたのが第2波の実害
                // (「幸運のエフェクトが消える」)。
                player.removePotionEffect(type);
            }
        }
        if (owned.isEmpty()) {
            threadGrantedPotions.remove(player.getUniqueId());
        }
    }

    /**
     * 飛行スレッド装備中のプレイヤーUUIDセット（GlideEffectと同じ方式）。
     */
    private static final java.util.Set<java.util.UUID> flightThreadPlayers =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private boolean flightTaskStarted = false;
    private BukkitTask flightTask;

    /**
     * 飛行スレッドの状態を更新する。
     * allowFlightは常にtrue維持（ジャンプキー検出用）。
     * ジャンプキーでグライドのON/OFFをトグルする。
     */
    private void updateFlightThread(Player player, boolean hasFlightThread) {
        if (!hasFlightThread) {
            if (flightThreadPlayers.remove(player.getUniqueId())) {
                player.setGliding(false);
                player.setFallDistance(0f);
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE
                        && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    player.setAllowFlight(false);
                }
            }
            return;
        }

        flightThreadPlayers.add(player.getUniqueId());
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE
                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            player.setAllowFlight(true);
        }
        startFlightTask();
    }

    private void startFlightTask() {
        if (flightTaskStarted) return;
        flightTaskStarted = true;

        flightTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(
            ArsPaper.getInstance(), () -> {
                if (flightThreadPlayers.isEmpty()) {
                    stopFlightTask();
                    return;
                }

                var iterator = flightThreadPlayers.iterator();
                while (iterator.hasNext()) {
                    Player p = org.bukkit.Bukkit.getPlayer(iterator.next());
                    if (p == null || !p.isOnline()) {
                        iterator.remove();
                        continue;
                    }
                    // 着地時に滑空解除
                    if (p.isOnGround() && p.isGliding()) {
                        p.setGliding(false);
                        p.setFallDistance(0f);
                    }
                    if (p.isGliding()) {
                        p.setFallDistance(0f);
                    }
                    // allowFlightを常時true維持（クライアントのダブルタップタイマーリセットを防止）
                    if (p.getGameMode() != org.bukkit.GameMode.CREATIVE
                            && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                        if (!p.getAllowFlight()) {
                            p.setAllowFlight(true);
                        }
                        // flying状態の検出（ダブルタップによる飛行開始を即座にグライドに変換）
                        // 儀式飛行がアクティブな場合はクリエ飛行を維持
                        if (p.isFlying()
                                && !com.arspaper.ritual.effect.FlightRitualEffect.hasActiveFlight(p)) {
                            p.setFlying(false);
                            if (!p.isOnGround() && !p.isGliding()) {
                                p.setGliding(true);
                                p.setFallDistance(0f);
                            }
                        }
                    }
                }
            }, 0L, 1L);
    }

    private void stopFlightTask() {
        if (flightTask != null) {
            flightTask.cancel();
            flightTask = null;
        }
        flightTaskStarted = false;
    }

    /**
     * 飛行スレッド: 空中でジャンプキーを押すと滑空をトグル。
     * allowFlightは常にtrue維持し、クリエイティブ飛行だけキャンセルする。
     */
    @org.bukkit.event.EventHandler
    public void onPlayerToggleFlight(org.bukkit.event.player.PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!flightThreadPlayers.contains(player.getUniqueId())) return;
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;

        // 儀式飛行がアクティブな場合はクリエ飛行を許可（グライド変換しない）
        if (com.arspaper.ritual.effect.FlightRitualEffect.hasActiveFlight(player)) return;

        // クリエイティブ飛行を常にキャンセル（スレッドのグライドに変換）
        event.setCancelled(true);
        player.setFlying(false);

        if (player.isGliding()) {
            player.setGliding(false);
            player.setFallDistance(0f);
        } else if (!player.isOnGround()) {
            player.setGliding(true);
            player.setFallDistance(0f);
        }
    }

    /**
     * エリトラ未装備時のグライディング解除をキャンセル（飛行スレッドで代替）。
     */
    @org.bukkit.event.EventHandler
    public void onEntityToggleGlide(org.bukkit.event.entity.EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!flightThreadPlayers.contains(player.getUniqueId())) return;
        if (!event.isGliding() && player.isGliding() && !player.isOnGround()) {
            event.setCancelled(true);
        }
    }

    /**
     * 飛行スレッド装備中の落下ダメージを無効化。
     */
    @org.bukkit.event.EventHandler
    public void onFlightFallDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!flightThreadPlayers.contains(player.getUniqueId())) return;
        event.setCancelled(true);
    }

    /**
     * サーバー停止時に全飛行スレッドプレイヤーの滑空を解除。
     */
    public static void cleanupFlightThread() {
        // インスタンスの飛行タスクをキャンセル
        ArmorManaListener listener = ArsPaper.getInstance().getArmorManaListener();
        if (listener != null && listener.flightTask != null) {
            listener.flightTask.cancel();
            listener.flightTask = null;
        }
        for (java.util.UUID uuid : flightThreadPlayers) {
            Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setGliding(false);
                p.setFallDistance(0f);
            }
        }
        flightThreadPlayers.clear();
    }
}
