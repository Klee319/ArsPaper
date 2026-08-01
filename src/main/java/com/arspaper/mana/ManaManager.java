package com.arspaper.mana;

import com.arspaper.ArsPaper;
import com.arspaper.item.impl.Wand;
import com.arspaper.world.WorldSettingsManager;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * プレイヤーのマナの消費・回復を管理する。
 * PDCで永続化し、BossBarで表示する。
 */
public class ManaManager implements Listener {

    private final JavaPlugin plugin;
    private volatile ManaConfig config;
    private final ManaBarDisplay barDisplay;
    private final BukkitTask regenTask;
    private final BukkitTask statsFlushTask;
    private static final NamespacedKey DEBUG_MODE_KEY = new NamespacedKey("arspaper", "debug_mode");
    private final RankingCache rankingCache;

    /** 累計マナ消費量のインメモリバッファ（PDC書き込み頻度を削減） */
    private final java.util.Map<UUID, Long> manaConsumedBuffer = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int STATS_FLUSH_INTERVAL = 6000; // 5分ごとにPDCへフラッシュ

    /** 最終詠唱時刻（エポックミリ秒）。非発動（idle）回復ボーナス判定に使用する。 */
    private final java.util.Map<UUID, Long> lastCastTime = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int PERCENT_DIVISOR = 100;

    /**
     * 要件⑥ source-auto-consume: 直近の{@link #consumeMana}呼び出しでSource補填によって賄われた
     * マナ量を、呼び出し元(SpellCaster)が読み取れるようにするための受け渡し変数。
     * consumeMana呼び出し直後、同一スレッド(メインスレッド前提)で即座に読むこと。
     * これはsource→mana変換遮断のため、キャンセル時のマナ返還からSource補填分を除外する用途で使う
     * (Source自体を返還する経路が複雑なため、最小実装として「補填分は返還しない」を採る)。
     */
    private final ThreadLocal<Integer> lastSourceConvertedAmount = ThreadLocal.withInitial(() -> 0);

    /**
     * 直近のconsumeMana呼び出しでSource補填によって賄われたマナ量を返す(0ならSource補填なし)。
     */
    public int getLastSourceConvertedAmount() {
        return lastSourceConvertedAmount.get();
    }

    public ManaManager(JavaPlugin plugin, ManaConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.barDisplay = new ManaBarDisplay();
        this.rankingCache = new RankingCache(plugin.getDataFolder(), plugin.getLogger());

        // マナ回復タスク
        this.regenTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::tickRegeneration,
            ManaBaseStats.regenIntervalTicks(),
            ManaBaseStats.regenIntervalTicks()
        );

        // 統計フラッシュタスク（5分ごとにバッファをPDCへ書き込み）。
        // shutdown() で確実に停止できるようハンドルを保持する。
        this.statsFlushTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin, this::flushManaStats, STATS_FLUSH_INTERVAL, STATS_FLUSH_INTERVAL
        );
    }

    public RankingCache getRankingCache() {
        return rankingCache;
    }

    public ManaConfig getConfig() {
        return config;
    }

    /**
     * ManaConfigを再読み込みする。/ars reload で呼ばれる。
     * ※ regenTaskのインターバルは変更不可（サーバ再起動が必要）。
     */
    public void reloadConfig(ManaConfig newConfig) {
        this.config = newConfig;
    }

    public int getCurrentMana(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        return pdc.getOrDefault(ManaKeys.CURRENT_MANA, PersistentDataType.INTEGER, ManaBaseStats.defaultMax());
    }

    public int getMaxMana(Player player) {
        WorldSettingsManager wsm = ArsPaper.getInstance().getWorldSettingsManager();
        WorldSettingsManager.WorldManaSettings worldMana = (wsm != null)
            ? wsm.getWorldMana(player.getWorld().getName())
            : WorldSettingsManager.WorldManaSettings.EMPTY;

        // ワールド別固定マナが設定されている場合はそれを返す
        if (worldMana.hasFixedMax()) {
            return worldMana.fixMax();
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int glyphBonus = pdc.getOrDefault(ManaKeys.GLYPH_MANA_BONUS, PersistentDataType.INTEGER, 0);
        int armorBonus = pdc.getOrDefault(ManaKeys.ARMOR_MANA_BONUS, PersistentDataType.INTEGER, 0);
        int threadBonus = pdc.getOrDefault(ManaKeys.THREAD_MANA_BONUS, PersistentDataType.INTEGER, 0);
        int enchantBonus = pdc.getOrDefault(ManaKeys.ENCHANT_MANA_BONUS, PersistentDataType.INTEGER, 0);
        int skillManaBonus = (int) Math.round(
                com.arspaper.integration.TrinityForgeBridge.tfNativeMaxManaBonus(player));
        int fixedMax = ManaBaseStats.defaultMax() + glyphBonus + armorBonus + threadBonus
                + enchantBonus + skillManaBonus + worldMana.maxBonus();

        // %上昇（装備由来）を固定値合計に乗算。上限はconfigでクランプ。デフォルト0%なら従来挙動。
        int maxPercent = Math.min(
            pdc.getOrDefault(ManaKeys.THREAD_MANA_MAX_PERCENT, PersistentDataType.INTEGER, 0),
            config.maxPercentCap());
        if (maxPercent <= 0) return fixedMax;
        return fixedMax + (int) Math.round(fixedMax * maxPercent / (double) PERCENT_DIVISOR);
    }

    /**
     * マナ消費量低下%の合算値を返す（0-100）。
     * 現状はスレッド由来（THREAD_COST_REDUCTION, ArmorManaListenerが装備の合算値を書込）を集約する。
     * 装備/防具由来の追加削減源も将来ここに合算する集約点。
     */
    public int getCostReductionPercent(Player player) {
        int threadReduction = player.getPersistentDataContainer()
            .getOrDefault(ManaKeys.THREAD_COST_REDUCTION, PersistentDataType.INTEGER, 0);
        return Math.min(PERCENT_DIVISOR, threadReduction);
    }

    /**
     * 詠唱時刻を記録する（SpellCasterから発動成立時に呼ばれる）。
     * 非発動（idle）回復ボーナスの判定に使用する。
     */
    public void touchCast(Player player) {
        lastCastTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * idle-seconds 以上詠唱していなければ非発動（idle）とみなす。
     * 一度も詠唱していない場合も非発動扱い。
     */
    private boolean isIdle(Player player) {
        Long last = lastCastTime.get(player.getUniqueId());
        if (last == null) return true;
        return System.currentTimeMillis() - last >= ManaBaseStats.idleSeconds() * 1000L;
    }

    /**
     * 旧MANA_BONUSキーからGLYPH_MANA_BONUSへの一回限りのマイグレーション。
     * PlayerJoinEvent時に呼ばれる。
     */
    private void migrateLegacyManaBonus(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int legacyBonus = pdc.getOrDefault(ManaKeys.MANA_BONUS, PersistentDataType.INTEGER, 0);
        if (legacyBonus > 0) {
            int glyphBonus = pdc.getOrDefault(ManaKeys.GLYPH_MANA_BONUS, PersistentDataType.INTEGER, 0);
            if (glyphBonus == 0) {
                pdc.set(ManaKeys.GLYPH_MANA_BONUS, PersistentDataType.INTEGER, legacyBonus);
            }
            pdc.remove(ManaKeys.MANA_BONUS);
        }
    }

    public boolean consumeMana(Player player, int amount) {
        lastSourceConvertedAmount.set(0);
        if (isInfiniteMana(player)) return true;
        int current = getCurrentMana(player);
        if (current < amount) {
            // 要件⑥ source-auto-consume(Ars鍛冶A-2): マナ不足時、skilltree由来のperkを持つプレイヤーは
            // インベントリ内の設定済みアイテム(mana.source-auto-consume.items)をマナ代わりに変換消費して
            // 補填する。perk未所持/TF未ロード/対象アイテム不足時はconvertedが0のまま返り、従来どおり
            // マナ不足として不発になる(fail-open)。
            int deficit = amount - current;
            int converted = com.arspaper.integration.SourceAutoConsume.tryConvert(
                player, deficit, config.sourceAutoConsumeItems());
            if (converted < deficit) return false;
            current += converted;
            lastSourceConvertedAmount.set(converted);
        }
        setCurrentMana(player, current - amount);
        // 累計マナ消費量をバッファに記録（PDC書き込みは定期フラッシュで行う）
        if (amount > 0) {
            manaConsumedBuffer.merge(player.getUniqueId(), (long) amount, Long::sum);
        }
        return true;
    }

    /**
     * デバッグモード（マナ無限 + パーク解放ゲート全バイパス）をトグルする。
     * PDCに永続化されるため再参加・再起動後も維持される。
     * @return トグル後の状態（true=ON）
     */
    public boolean toggleInfiniteMana(Player player) {
        boolean next = !isInfiniteMana(player);
        setInfiniteMana(player, next);
        return next;
    }

    /** デバッグモードを明示的に ON/OFF する。 */
    public void setInfiniteMana(Player player, boolean enabled) {
        if (enabled) {
            player.getPersistentDataContainer().set(DEBUG_MODE_KEY, PersistentDataType.BYTE, (byte) 1);
            plugin.getLogger().info("[Debug] Debug mode ON for " + player.getName());
        } else {
            player.getPersistentDataContainer().remove(DEBUG_MODE_KEY);
            plugin.getLogger().info("[Debug] Debug mode OFF for " + player.getName());
        }
    }

    /** {@code /ars debug} 相当。マナ無限に加え、グリフ/レシピ/儀式のパーク解放ゲートを全通過させる。 */
    public boolean isInfiniteMana(Player player) {
        return player.getPersistentDataContainer().has(DEBUG_MODE_KEY);
    }

    /** {@link #isInfiniteMana(Player)} のエイリアス（ゲート判定側の読みやすさ用）。 */
    public boolean isDebugMode(Player player) {
        return isInfiniteMana(player);
    }

    public void addMana(Player player, int amount) {
        int current = getCurrentMana(player);
        int max = getMaxMana(player);
        setCurrentMana(player, Math.min(current + amount, max));
    }

    public void setCurrentMana(Player player, int mana) {
        // max低下（パーク再振り分け/TF未ロード等）で永続化済みcurrentがmaxを超えたまま残ると、
        // tickRegeneration の `current >= max` 早期returnにより回復が永久停止し、BossBarも100%超で
        // 描画され続ける。永続化前に[0, max]へクランプする。getMaxMana はここから setCurrentMana を
        // 呼ばない（再帰安全）ため、素直に先に呼んでクランプ幅を確定できる。
        int max = getMaxMana(player);
        int clamped = Math.max(0, Math.min(mana, max));
        player.getPersistentDataContainer().set(
            ManaKeys.CURRENT_MANA, PersistentDataType.INTEGER, clamped
        );
        applyBar(player, clamped, max);
    }

    /**
     * マナバーの内容を更新し、表示/非表示を切り替える(2026-07-28 ユーザー要望:
     * 魔導書か魔法バインド済みアイテムを<strong>手に持っている間だけ</strong>出す)。
     *
     * <p>従来は各所で無条件に {@code player.showBossBar(bar)} していたため、魔法を使わない
     * プレイヤーの画面上部も常にボスバーで埋まっていた。表示条件は
     * {@link ManaBarVisibility#holdsMagicItem} 1か所に集約し、show/hide の対を必ずここで作る
     * (片方だけ書き換えると「一度出たら消えないバー」が生まれる)。
     */
    private void applyBar(Player player, int current, int max) {
        BossBar bar = barDisplay.update(player.getUniqueId(), current, max);
        if (ManaBarVisibility.holdsMagicItem(player)) {
            player.showBossBar(bar);
        } else {
            player.hideBossBar(bar);
        }
    }

    /** 手持ちが変わった等で表示条件だけ再評価する(マナ値は変えない)。 */
    private void refreshBar(Player player) {
        applyBar(player, getCurrentMana(player), getMaxMana(player));
    }

    private int getRegenRate(Player player) {
        WorldSettingsManager wsm = ArsPaper.getInstance().getWorldSettingsManager();
        WorldSettingsManager.WorldManaSettings worldMana = (wsm != null)
            ? wsm.getWorldMana(player.getWorld().getName())
            : WorldSettingsManager.WorldManaSettings.EMPTY;

        // ワールド別固定回復量が設定されている場合はそれを返す
        if (worldMana.hasFixedRegen()) {
            return worldMana.fixRegen();
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int baseRate = pdc.getOrDefault(ManaKeys.REGEN_RATE, PersistentDataType.INTEGER, ManaBaseStats.defaultRegenRate());
        int threadBonus = pdc.getOrDefault(ManaKeys.THREAD_REGEN_BONUS, PersistentDataType.INTEGER, 0);
        int enchantBonus = pdc.getOrDefault(ManaKeys.ENCHANT_REGEN_BONUS, PersistentDataType.INTEGER, 0);
        int armorBonus = pdc.getOrDefault(ManaKeys.ARMOR_REGEN_BONUS, PersistentDataType.INTEGER, 0);
        int flatRate = baseRate + threadBonus + enchantBonus + armorBonus + worldMana.regenBonus();
        // skilltree regenノード由来の回復%上昇。ノード多重取得で合算値が際限なく積み上がるため、
        // 装備由来%上昇(THREAD_REGEN_PERCENT)と同じ config.maxPercentCap() でクランプする
        // (詠唱コスト実質0化を防ぐ安全弁)。
        double skillRegen = com.arspaper.integration.TrinityForgeBridge.tfNativeManaRegenBonus(player);
        if (skillRegen > 0.0) {
            double cappedSkillRegen = Math.min(skillRegen, config.maxPercentCap() / (double) PERCENT_DIVISOR);
            flatRate += (int) Math.round(flatRate * cappedSkillRegen);
        }

        // 回復速度%上昇（装備由来）を固定値合計に乗算。デフォルト0%なら従来挙動。
        // %回復も getMaxMana の maxPercentCap パターンを踏襲し config 上限でクランプ。
        // 不正/巨大な THREAD_REGEN_PERCENT による暴走回復（毎tick満タン化）を防ぐ。
        int regenPercent = Math.min(
            pdc.getOrDefault(ManaKeys.THREAD_REGEN_PERCENT, PersistentDataType.INTEGER, 0),
            config.maxPercentCap());
        if (regenPercent <= 0) return flatRate;
        return flatRate + (int) Math.round(flatRate * regenPercent / (double) PERCENT_DIVISOR);
    }

    /**
     * 非発動（idle）時の回復ボーナス量を返す（固定値＋最大値に対する%）。
     * idleでない、または設定が0の場合は0。
     */
    private int getIdleRecoveryBonus(Player player, int max) {
        if (!isIdle(player)) return 0;
        int bonus = ManaBaseStats.idleBonusFlat();
        double idleBonusPercent = ManaBaseStats.idleBonusPercent();
        if (idleBonusPercent > 0) {
            // ManaBaseStats は分数[0,1]で返す(TF側 PERCENT stat の保存形式)ため /100 補正は不要。
            bonus += (int) Math.round(max * idleBonusPercent);
        }
        return bonus;
    }

    private void tickRegeneration() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            int current = getCurrentMana(player);
            int max = getMaxMana(player);
            if (current >= max) {
                // 満タンでも表示条件だけは取り直す — ここで continue すると、
                // 満タンのまま魔導書を持った/しまったプレイヤーのバーが切り替わらない。
                applyBar(player, current, max);
                continue;
            }

            int regenRate = getRegenRate(player) + getIdleRecoveryBonus(player, max);
            int newMana = Math.min(current + regenRate, max);
            setCurrentMana(player, newMana);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        migrateLegacyManaBonus(player);
        // 旅路デバフのAttributeModifier残留をクリーンアップ
        cleanupJourneyDebuff(player);
        refreshBar(player);
    }

    /**
     * 2026-07-28: 持ち替えた瞬間にマナバーの出し入れを反映する。回復タスク任せだと
     * 満タン時は {@code tickRegeneration} が早期returnするため、持ち替えても切り替わらない。
     */
    @EventHandler
    public void onItemHeld(org.bukkit.event.player.PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        // このイベント時点ではまだ選択スロットが切り替わっていないので、新スロットを直接見る。
        boolean magic = ManaBarVisibilityPolicy.shouldShow(
                ManaBarVisibility.isMagicItem(player.getInventory().getItem(event.getNewSlot())),
                ManaBarVisibility.isMagicItem(player.getInventory().getItemInOffHand())
        );
        BossBar bar = barDisplay.update(player.getUniqueId(), getCurrentMana(player), getMaxMana(player));
        if (magic) {
            player.showBossBar(bar);
        } else {
            player.hideBossBar(bar);
        }
    }

    /** オフハンド入れ替え(F)でも即座に反映する。 */
    @EventHandler
    public void onSwapHands(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        boolean magic = ManaBarVisibilityPolicy.shouldShow(
                ManaBarVisibility.isMagicItem(event.getMainHandItem()),
                ManaBarVisibility.isMagicItem(event.getOffHandItem())
        );
        BossBar bar = barDisplay.update(player.getUniqueId(), getCurrentMana(player), getMaxMana(player));
        if (magic) {
            player.showBossBar(bar);
        } else {
            player.hideBossBar(bar);
        }
    }

    /** インベントリを閉じた時(装備変更後)にも表示条件を取り直す。 */
    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            refreshBar(player);
        }
    }

    /**
     * インベントリ操作の適用後に手持ちを再評価する。
     *
     * <p>InventoryClickEvent/InventoryDragEvent の発火中はまだインベントリの最終状態ではないため、
     * Paper APIの推奨どおり次tickへ送る。クリック、数字キー、オフハンドキー、ドラッグのいずれで
     * 手持ちが変わっても同じ経路で表示を同期できる。
     */
    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            refreshBarNextTick(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            refreshBarNextTick(player);
        }
    }

    /** 手に持ったバインド品を捨てた、または破損した場合も次tickで隠す。 */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        refreshBarNextTick(event.getPlayer());
    }

    @EventHandler
    public void onPlayerItemBreak(PlayerItemBreakEvent event) {
        refreshBarNextTick(event.getPlayer());
    }

    private void refreshBarNextTick(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                refreshBar(player);
            }
        });
    }

    /** ログイン時に旅路デバフの残留AttributeModifierを除去する */
    private void cleanupJourneyDebuff(Player player) {
        org.bukkit.attribute.AttributeInstance attr = player.getAttribute(
            org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr == null) return;
        org.bukkit.NamespacedKey debuffKey = new org.bukkit.NamespacedKey(
            ArsPaper.getInstance(), "journey_debuff");
        attr.getModifiers().stream()
            .filter(m -> m.getKey().equals(debuffKey))
            .forEach(attr::removeModifier);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // リスポーン後1tick遅延でBossBar再表示（リスポーン処理完了を待つ）
        org.bukkit.Bukkit.getScheduler().runTaskLater(ArsPaper.getInstance(), () -> {
            Player player = event.getPlayer();
            if (player.isOnline()) {
                refreshBar(player);
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        // ディメンション移動後にBossBar再表示（ワールド別マナ補正でmax変動する場合にクランプ）
        Player player = event.getPlayer();
        int max = getMaxMana(player);
        int current = getCurrentMana(player);
        if (current > max) {
            setCurrentMana(player, max);
        } else {
            applyBar(player, current, max);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        flushPlayerStats(player);
        lastCastTime.remove(player.getUniqueId());
        rankingCache.updatePlayer(player, getTotalManaConsumed(player));
        rankingCache.save();
        BossBar bar = barDisplay.get(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
        barDisplay.remove(player.getUniqueId());

        // SpellCasterのクールダウンをクリーンアップ
        ArsPaper.getInstance().getSpellCaster().clearCooldown(player.getUniqueId());

        // 滑空状態をクリーンアップ（防具復元）
        com.arspaper.spell.effect.GlideEffect.cancelGlide(player.getUniqueId());

        // Wandの選択状態をクリーンアップ
        ArsPaper.getInstance().getItemRegistry().get("dominion_wand")
            .filter(item -> item instanceof Wand)
            .map(item -> (Wand) item)
            .ifPresent(wand -> wand.clearSelection(player.getUniqueId()));
    }

    /**
     * バッファに溜まったマナ消費量をPDCへフラッシュする。
     */
    private void flushManaStats() {
        var iterator = manaConsumedBuffer.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                PersistentDataContainer pdc = player.getPersistentDataContainer();
                long existing = pdc.getOrDefault(ManaKeys.TOTAL_MANA_CONSUMED, PersistentDataType.LONG, 0L);
                pdc.set(ManaKeys.TOTAL_MANA_CONSUMED, PersistentDataType.LONG, existing + entry.getValue());
            }
            iterator.remove();
        }
    }

    /**
     * 特定プレイヤーのバッファをフラッシュ（ログアウト時用）。
     */
    private void flushPlayerStats(Player player) {
        Long buffered = manaConsumedBuffer.remove(player.getUniqueId());
        if (buffered != null && buffered > 0) {
            PersistentDataContainer pdc = player.getPersistentDataContainer();
            long existing = pdc.getOrDefault(ManaKeys.TOTAL_MANA_CONSUMED, PersistentDataType.LONG, 0L);
            pdc.set(ManaKeys.TOTAL_MANA_CONSUMED, PersistentDataType.LONG, existing + buffered);
        }
    }

    /**
     * ランキング用: バッファ含みの累計マナ消費量を取得。
     */
    public long getTotalManaConsumed(Player player) {
        long persisted = player.getPersistentDataContainer()
            .getOrDefault(ManaKeys.TOTAL_MANA_CONSUMED, PersistentDataType.LONG, 0L);
        Long buffered = manaConsumedBuffer.getOrDefault(player.getUniqueId(), 0L);
        return persisted + buffered;
    }

    public void shutdown() {
        if (regenTask != null) {
            regenTask.cancel();
        }
        if (statsFlushTask != null) {
            statsFlushTask.cancel();
        }
        // シャットダウン前にバッファをフラッシュ
        flushManaStats();
        // 全プレイヤーのランキングキャッシュを更新
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            rankingCache.updatePlayer(player, getTotalManaConsumed(player));
            BossBar bar = barDisplay.get(player.getUniqueId());
            if (bar != null) {
                player.hideBossBar(bar);
            }
        }
        rankingCache.save();
    }
}
