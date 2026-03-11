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

/**
 * プレイヤーのマナの消費・回復を管理する。
 * PDCで永続化し、BossBarで表示する。
 */
public class ManaManager implements Listener {

    private final JavaPlugin plugin;
    private final ManaConfig config;
    private final ManaBarDisplay barDisplay;
    private final BukkitTask regenTask;

    public ManaManager(JavaPlugin plugin, ManaConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.barDisplay = new ManaBarDisplay();

        // マナ回復タスク
        this.regenTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::tickRegeneration,
            config.regenIntervalTicks(),
            config.regenIntervalTicks()
        );
    }

    public int getCurrentMana(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        return pdc.getOrDefault(ManaKeys.CURRENT_MANA, PersistentDataType.INTEGER, config.defaultMaxMana());
    }

    public int getMaxMana(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int glyphBonus = pdc.getOrDefault(ManaKeys.GLYPH_MANA_BONUS, PersistentDataType.INTEGER, 0);
        int armorBonus = pdc.getOrDefault(ManaKeys.ARMOR_MANA_BONUS, PersistentDataType.INTEGER, 0);
        return config.defaultMaxMana() + glyphBonus + armorBonus;
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
        int current = getCurrentMana(player);
        if (current < amount) return false;
        setCurrentMana(player, current - amount);
        return true;
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
        return player.getPersistentDataContainer()
            .getOrDefault(ManaKeys.REGEN_RATE, PersistentDataType.INTEGER, config.defaultRegenRate());
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
        BossBar bar = barDisplay.get(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
        barDisplay.remove(player.getUniqueId());

        // Wandの選択状態をクリーンアップ
        ArsPaper.getInstance().getItemRegistry().get("dominion_wand")
            .filter(item -> item instanceof Wand)
            .map(item -> (Wand) item)
            .ifPresent(wand -> wand.clearSelection(player.getUniqueId()));
    }

    public void shutdown() {
        if (regenTask != null) {
            regenTask.cancel();
        }
        // 全プレイヤーのBossBarを非表示
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            BossBar bar = barDisplay.get(player.getUniqueId());
            if (bar != null) {
                player.hideBossBar(bar);
            }
        }
    }
}
