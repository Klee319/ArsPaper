package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 対象にエリトラ飛行能力を一時的に付与するEffect。
 * エリトラを装備していなくてもエリトラと同じ滑空が可能になる。
 * 一定時間経過または着地で効果終了。
 *
 * 実装: setGliding(true) + EntityToggleGlideEvent制御による純粋API方式。
 * エリトラの装備置換は行わない。
 * 基本持続: 300tick (15秒)
 * ExtendTime: +100tick/level (5秒)
 */
public class GlideEffect implements SpellEffect, Listener {

    private static final int BASE_DURATION = 300;          // 15秒
    private static final int DURATION_PER_LEVEL = 100;     // ExtendTimeごと +5秒

    /** 滑空中プレイヤーのUUIDセット */
    private static final Set<UUID> glidingPlayers = ConcurrentHashMap.newKeySet();

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public GlideEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "glide");
        this.config = config;
        this.plugin = plugin;
    }

    /**
     * サーバー停止時やプレイヤーログアウト時に全員の滑空を解除する。
     */
    public static void restoreAll() {
        for (UUID uuid : glidingPlayers) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setGliding(false);
                player.setFallDistance(0f);
            }
        }
        glidingPlayers.clear();
    }

    /**
     * 指定プレイヤーの滑空状態を解除する（ログアウト時等）。
     */
    public static void cancelGlide(UUID playerId) {
        if (glidingPlayers.remove(playerId)) {
            Player player = org.bukkit.Bukkit.getPlayer(playerId);
            if (player != null) {
                player.setGliding(false);
                player.setFallDistance(0f);
            }
        }
    }

    /**
     * 指定プレイヤーが滑空中かどうかを返す。
     */
    public static boolean isGliding(UUID playerId) {
        return glidingPlayers.contains(playerId);
    }

    /**
     * EntityToggleGlideEventハンドラ。
     * サーバーが滑空を解除しようとした場合、管理下プレイヤーならキャンセルする。
     */
    @EventHandler
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!glidingPlayers.contains(player.getUniqueId())) return;
        // サーバーが滑空を停止しようとしている場合のみキャンセル
        if (!event.isGliding()) {
            event.setCancelled(true);
        }
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        if (!(target instanceof Player player)) return;

        // 既に滑空中の場合はスキップ
        if (glidingPlayers.contains(player.getUniqueId())) return;

        int baseDuration = (int) config.getParam("glide", "base-duration", (double) BASE_DURATION);
        int durationPerLevel = (int) config.getParam("glide", "duration-per-level", (double) DURATION_PER_LEVEL);
        int duration = Math.max(1, baseDuration + context.getDurationLevel() * durationPerLevel);

        glidingPlayers.add(player.getUniqueId());

        // 空中にいる場合は即座に滑空開始
        if (!player.isOnGround()) {
            player.setGliding(true);
        }

        player.sendActionBar(Component.text("滑空モード有効", NamedTextColor.AQUA));
        spawnGlideFx(player.getLocation());

        // 持続タイマー
        new BukkitRunnable() {
            int ticks = 0;
            boolean wasGliding = false;
            int glidingStartTick = -1; // 滑空開始tickを記録（着地判定の猶予用）

            @Override
            public void run() {
                if (++ticks >= duration || !player.isOnline()
                        || !glidingPlayers.contains(player.getUniqueId())) {
                    endGlide(player);
                    cancel();
                    return;
                }

                // 着地検出（滑空後に地面に触れたら終了）
                // 猶予期間: 滑空開始後10tick以上経過してから着地判定
                if (wasGliding && player.isOnGround()
                        && (ticks - glidingStartTick) >= 10) {
                    endGlide(player);
                    cancel();
                    return;
                }

                // 空中なら滑空状態を維持
                if (!player.isOnGround()) {
                    player.setGliding(true);
                    if (!wasGliding) {
                        wasGliding = true;
                        glidingStartTick = ticks;
                    }
                    if (ticks % 3 == 0) {
                        player.getWorld().spawnParticle(Particle.CLOUD,
                            player.getLocation(), 2, 0.2, 0, 0.2, 0.01);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    private void endGlide(Player player) {
        // セットから先に除去（イベントハンドラがキャンセルしないように）
        glidingPlayers.remove(player.getUniqueId());
        player.setGliding(false);
        player.setFallDistance(0f);

        // 地面に近い場合（0.5ブロック以内）はテレポートで着地を安定させる
        Location loc = player.getLocation();
        if (!player.isOnGround()) {
            Location below = loc.clone();
            for (double dy = 0; dy <= 0.5; dy += 0.1) {
                below.setY(loc.getY() - dy);
                if (below.getBlock().getType().isSolid()) {
                    Location landLoc = loc.clone();
                    landLoc.setY(below.getBlockY() + 1.0);
                    player.teleport(landLoc);
                    break;
                }
            }
        }

        player.sendActionBar(Component.text("滑空モード終了", NamedTextColor.GRAY));
        spawnEndFx(player.getLocation());
    }

    private void spawnGlideFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.5, 0),
            15, 0.4, 0.2, 0.4, 0.05);
        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0),
            8, 0.3, 0.3, 0.3, 0.05);
        loc.getWorld().playSound(loc, Sound.ENTITY_PHANTOM_FLAP,
            SoundCategory.PLAYERS, 0.7f, 1.2f);
    }

    private void spawnEndFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 10, 0.3, 0.2, 0.3, 0.03);
        loc.getWorld().playSound(loc, Sound.ITEM_ARMOR_EQUIP_LEATHER,
            SoundCategory.PLAYERS, 0.5f, 1.0f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "滑空"; }

    @Override
    public String getDescription() { return "エリトラなしで一時的に滑空できる"; }

    @Override
    public int getManaCost() { return config.getManaCost("glide"); }

    @Override
    public int getTier() { return config.getTier("glide"); }
}
