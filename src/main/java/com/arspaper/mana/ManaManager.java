package com.arspaper.mana;

import com.arspaper.ArsPaper;
import com.arspaper.item.impl.Wand;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
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
    private final Set<UUID> infiniteManaPlayers = new HashSet<>();
    private final RankingCache rankingCache;

    /** 累計マナ消費量のインメモリバッファ（PDC書き込み頻度を削減） */
    private final java.util.Map<UUID, Long> manaConsumedBuffer = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int STATS_FLUSH_INTERVAL = 6000; // 5分ごとにPDCへフラッシュ

    public ManaManager(JavaPlugin plugin, ManaConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.barDisplay = new ManaBarDisplay();
        this.rankingCache = new RankingCache(plugin.getDataFolder(), plugin.getLogger());

        // マナ回復タスク
        this.regenTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::tickRegeneration,
            config.regenIntervalTicks(),
            config.regenIntervalTicks()
        );

        // 統計フラッシュタスク（5分ごとにバッファをPDCへ書き込み）
        plugin.getServer().getScheduler().runTaskTimer(
            plugin, this::flushManaStats, STATS_FLUSH_INTERVAL, STATS_FLUSH_INTERVAL
        );
    }

    public RankingCache getRankingCache() {
        return rankingCache;
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
        return pdc.getOrDefault(ManaKeys.CURRENT_MANA, PersistentDataType.INTEGER, config.defaultMaxMana());
    }

    public int getMaxMana(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int glyphBonus = pdc.getOrDefault(ManaKeys.GLYPH_MANA_BONUS, PersistentDataType.INTEGER, 0);
        int armorBonus = pdc.getOrDefault(ManaKeys.ARMOR_MANA_BONUS, PersistentDataType.INTEGER, 0);
        int threadBonus = pdc.getOrDefault(ManaKeys.THREAD_MANA_BONUS, PersistentDataType.INTEGER, 0);
        int enchantBonus = pdc.getOrDefault(ManaKeys.ENCHANT_MANA_BONUS, PersistentDataType.INTEGER, 0);
        return config.defaultMaxMana() + glyphBonus + armorBonus + threadBonus + enchantBonus;
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
        if (infiniteManaPlayers.contains(player.getUniqueId())) return true;
        int current = getCurrentMana(player);
        if (current < amount) return false;
        setCurrentMana(player, current - amount);
        // 累計マナ消費量をバッファに記録（PDC書き込みは定期フラッシュで行う）
        if (amount > 0) {
            manaConsumedBuffer.merge(player.getUniqueId(), (long) amount, Long::sum);
        }
        return true;
    }

    /**
     * マナ無限モードをトグルする。
     * @return トグル後の状態（true=無限ON）
     */
    public boolean toggleInfiniteMana(Player player) {
        UUID uuid = player.getUniqueId();
        if (infiniteManaPlayers.contains(uuid)) {
            infiniteManaPlayers.remove(uuid);
            plugin.getLogger().info("[Debug] Infinite mana OFF for " + player.getName());
            return false;
        } else {
            infiniteManaPlayers.add(uuid);
            plugin.getLogger().info("[Debug] Infinite mana ON for " + player.getName());
            return true;
        }
    }

    public boolean isInfiniteMana(Player player) {
        return infiniteManaPlayers.contains(player.getUniqueId());
    }

    public void addMana(Player player, int amount) {
        int current = getCurrentMana(player);
        int max = getMaxMana(player);
        setCurrentMana(player, Math.min(current + amount, max));
    }

    public void setCurrentMana(Player player, int mana) {
        player.getPersistentDataContainer().set(
            ManaKeys.CURRENT_MANA, PersistentDataType.INTEGER, mana
        );
        int max = getMaxMana(player);
        BossBar bar = barDisplay.update(player.getUniqueId(), mana, max);
        player.showBossBar(bar);
    }

    private int getRegenRate(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int baseRate = pdc.getOrDefault(ManaKeys.REGEN_RATE, PersistentDataType.INTEGER, config.defaultRegenRate());
        int threadBonus = pdc.getOrDefault(ManaKeys.THREAD_REGEN_BONUS, PersistentDataType.INTEGER, 0);
        int enchantBonus = pdc.getOrDefault(ManaKeys.ENCHANT_REGEN_BONUS, PersistentDataType.INTEGER, 0);
        int armorBonus = pdc.getOrDefault(ManaKeys.ARMOR_REGEN_BONUS, PersistentDataType.INTEGER, 0);
        return baseRate + threadBonus + enchantBonus + armorBonus;
    }

    private void tickRegeneration() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            int current = getCurrentMana(player);
            int max = getMaxMana(player);
            if (current >= max) continue;

            int regenRate = getRegenRate(player);
            int newMana = Math.min(current + regenRate, max);
            setCurrentMana(player, newMana);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        migrateLegacyManaBonus(player);
        int current = getCurrentMana(player);
        int max = getMaxMana(player);
        BossBar bar = barDisplay.update(player.getUniqueId(), current, max);
        player.showBossBar(bar);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        flushPlayerStats(player);
        rankingCache.updatePlayer(player, getTotalManaConsumed(player));
        rankingCache.save();
        BossBar bar = barDisplay.get(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
        barDisplay.remove(player.getUniqueId());
        infiniteManaPlayers.remove(player.getUniqueId());

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
