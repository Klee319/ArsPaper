package com.arspaper.ritual.effect;

import com.arspaper.ArsPaper;
import com.arspaper.mana.ManaKeys;
import com.arspaper.ritual.RitualEffect;
import com.arspaper.ritual.RitualRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飛行の儀式 - プレイヤーに一時的なクリエイティブ飛行を付与する。
 * PDCに終了時刻を保存し、死亡・再ログイン・サーバ再起動後も飛行を復元する。
 * 10分、5分、1分前にチャット通知を送信する。
 */
public class FlightRitualEffect implements RitualEffect, Listener {

    private static final int DEFAULT_DURATION_SECONDS = 600;

    /** アクティブな飛行タスクのキャンセル用 (UUID → taskIdリスト) */
    private static final Map<UUID, List<Integer>> activeFlightTasks = new ConcurrentHashMap<>();

    @Override
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        int durationSeconds = DEFAULT_DURATION_SECONDS;
        String durationParam = recipe.effectParams().get("duration");
        if (durationParam != null) {
            try { durationSeconds = Integer.parseInt(durationParam); } catch (NumberFormatException ignored) {}
        }

        // PDCに終了時刻を保存
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        player.getPersistentDataContainer().set(
            ManaKeys.RITUAL_FLIGHT_END, PersistentDataType.LONG, endTime);

        // 飛行を付与
        player.setAllowFlight(true);
        player.setFlying(true);

        Location effectLoc = coreLocation.clone().add(0.5, 2.0, 0.5);
        coreLocation.getWorld().spawnParticle(Particle.END_ROD, effectLoc, 80, 1, 2, 1, 0.1);
        coreLocation.getWorld().playSound(effectLoc, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.2f);

        int minutes = durationSeconds / 60;
        player.sendMessage(Component.text(
            "飛行が " + minutes + " 分間有効になりました！", NamedTextColor.LIGHT_PURPLE
        ));

        // タイマー開始
        scheduleFlightTasks(player, durationSeconds);
    }

    /**
     * 飛行タイマーと通知をスケジュールする。
     */
    public static void scheduleFlightTasks(Player player, int remainingSeconds) {
        UUID uuid = player.getUniqueId();
        ArsPaper plugin = ArsPaper.getInstance();

        // 既存のタスクを全てキャンセル
        cancelExistingTasks(uuid);

        List<Integer> taskIds = new ArrayList<>();

        // 通知スケジュール（10分、5分、1分前）
        int[] notifyAt = {600, 300, 60}; // 秒
        for (int threshold : notifyAt) {
            int secondsUntilNotify = remainingSeconds - threshold;
            if (secondsUntilNotify > 0) {
                BukkitRunnable notifyTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!player.isOnline()) return;
                        if (!hasActiveFlight(player)) return;
                        int minutesLeft = threshold / 60;
                        player.sendMessage(Component.text(
                            "飛行終了まであと " + minutesLeft + " 分！", NamedTextColor.YELLOW
                        ));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
                    }
                };
                notifyTask.runTaskLater(plugin, secondsUntilNotify * 20L);
                taskIds.add(notifyTask.getTaskId());
            }
        }

        // 飛行終了タスク
        BukkitRunnable endTask = new BukkitRunnable() {
            @Override
            public void run() {
                activeFlightTasks.remove(uuid);
                if (!player.isOnline()) return;
                removeFlight(player);
            }
        };
        endTask.runTaskLater(plugin, remainingSeconds * 20L);
        taskIds.add(endTask.getTaskId());

        activeFlightTasks.put(uuid, taskIds);
    }

    /**
     * プレイヤーの飛行を除去する。
     */
    private static void removeFlight(Player player) {
        player.getPersistentDataContainer().remove(ManaKeys.RITUAL_FLIGHT_END);
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        player.setFlying(false);
        player.setAllowFlight(false);
        // 空中で飛行終了した場合の落下ダメージを防ぐためスローフォール付与
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.SLOW_FALLING, 10 * 20, 0, false, true));
        player.sendMessage(Component.text("飛行の効果が切れました。", NamedTextColor.GRAY));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
    }

    /**
     * プレイヤーがアクティブな飛行を持っているかチェック。
     */
    public static boolean hasActiveFlight(Player player) {
        Long endTime = player.getPersistentDataContainer()
            .get(ManaKeys.RITUAL_FLIGHT_END, PersistentDataType.LONG);
        return endTime != null && endTime > System.currentTimeMillis();
    }

    /**
     * プレイヤーの残り飛行時間を取得（秒）。0以下なら期限切れ。
     */
    public static int getRemainingSeconds(Player player) {
        Long endTime = player.getPersistentDataContainer()
            .get(ManaKeys.RITUAL_FLIGHT_END, PersistentDataType.LONG);
        if (endTime == null) return 0;
        return (int) ((endTime - System.currentTimeMillis()) / 1000);
    }

    /**
     * 飛行を復元する（ログイン時・リスポーン時）。
     */
    public static void restoreFlight(Player player) {
        int remaining = getRemainingSeconds(player);
        if (remaining <= 0) {
            // 期限切れ → PDCクリーンアップ
            player.getPersistentDataContainer().remove(ManaKeys.RITUAL_FLIGHT_END);
            return;
        }

        player.setAllowFlight(true);
        player.setFlying(true);

        int minutes = remaining / 60;
        int seconds = remaining % 60;
        String timeStr = minutes > 0 ? minutes + "分" + (seconds > 0 ? seconds + "秒" : "") : seconds + "秒";
        player.sendMessage(Component.text(
            "飛行が復元されました（残り " + timeStr + "）", NamedTextColor.LIGHT_PURPLE
        ));

        scheduleFlightTasks(player, remaining);
    }

    private static void cancelExistingTasks(UUID uuid) {
        List<Integer> taskIds = activeFlightTasks.remove(uuid);
        if (taskIds != null) {
            for (int taskId : taskIds) {
                org.bukkit.Bukkit.getScheduler().cancelTask(taskId);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 1tick遅延で復元（データロード完了を待つ）
        new BukkitRunnable() {
            @Override
            public void run() {
                Player player = event.getPlayer();
                if (player.isOnline() && hasActiveFlight(player)) {
                    restoreFlight(player);
                }
            }
        }.runTaskLater(ArsPaper.getInstance(), 1L);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // 1tick遅延で復元
        new BukkitRunnable() {
            @Override
            public void run() {
                Player player = event.getPlayer();
                if (player.isOnline() && hasActiveFlight(player)) {
                    restoreFlight(player);
                }
            }
        }.runTaskLater(ArsPaper.getInstance(), 1L);
    }
}
