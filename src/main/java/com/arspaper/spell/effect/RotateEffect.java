package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 対象を回転させるEffect。
 * Amplify: 回転角度を変更（0=90°, 1=180°, 2=270°）
 * Dampen (amplifyLevel < 0): 反時計回り
 * ExtendTime: エンティティの向きを一定時間固定する
 */
public class RotateEffect implements SpellEffect {

    private static final int BASE_LOCK_TICKS = 60;  // 3秒
    private static final int LOCK_BONUS_TICKS = 40;  // durationLevelあたり+2秒
    private final JavaPlugin plugin;
    private final NamespacedKey id;
    private final GlyphConfig config;

    /** アクティブな視点固定タスクを追跡（shutdown cleanup用） */
    private static final Set<BukkitTask> activeLocks = ConcurrentHashMap.newKeySet();

    public RotateEffect(JavaPlugin plugin, GlyphConfig config) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "rotate");
        this.config = config;
    }

    /** サーバーシャットダウン時に全視点固定を解除する。 */
    public static void cleanupAll() {
        for (BukkitTask task : activeLocks) {
            task.cancel();
        }
        activeLocks.clear();
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int amp = context.getAmplifyLevel();
        // 回転角度: |amp|に応じて90°単位で変化（0=90°, 1=180°, 2+=270°）
        int steps = Math.min(Math.abs(amp) + 1, 4); // 1〜4 (90°〜360°)
        float angle = steps * 90f;
        if (amp < 0) angle = -angle;

        float yaw = target.getLocation().getYaw() + angle;
        yaw = ((yaw + 180f) % 360f + 360f) % 360f - 180f;

        Location newLoc = target.getLocation().clone();
        newLoc.setYaw(yaw);
        target.teleport(newLoc);

        // ExtendTime: 向きを一定時間固定（モブ: AI無効化、プレイヤー: 視点強制テレポ）
        int durationLevel = context.getDurationLevel();
        if (durationLevel > 0) {
            int baseLock = (int) config.getParam("rotate", "base-lock-ticks", BASE_LOCK_TICKS);
            int lockBonus = (int) config.getParam("rotate", "lock-bonus-ticks", LOCK_BONUS_TICKS);
            int lockTicks = baseLock + durationLevel * lockBonus;
            final float lockedYaw = yaw;
            final float lockedPitch = target.getLocation().getPitch();

            if (target instanceof Mob mob) {
                // モブ: AI無効化で向き固定
                mob.setAI(false);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (mob.isValid() && !mob.isDead()) {
                        mob.setAI(true);
                    }
                }, lockTicks);
            } else if (target instanceof Player player) {
                // 統合版(Geyser)プレイヤーはsetRotationで動けなくなるため代替処理
                if (isBedrockPlayer(player)) {
                    // 鈍足+盲目で疑似拘束
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, lockTicks, 3, false, false, true));
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.BLINDNESS, lockTicks, 0, false, false, true));
                } else {
                    // Java版: 視点のみ毎tickロック（移動は許可）
                    BukkitTask lockTask = new BukkitRunnable() {
                        private int elapsed = 0;

                        @Override
                        public void run() {
                            elapsed++;
                            if (elapsed > lockTicks || !player.isOnline() || player.isDead()) {
                                activeLocks.remove(this);
                                cancel();
                                return;
                            }
                            player.setRotation(lockedYaw, lockedPitch);
                        }
                    }.runTaskTimer(plugin, 1L, 1L);
                    activeLocks.add(lockTask);
                }
            }
        }

        target.getWorld().spawnParticle(
            org.bukkit.Particle.ENCHANT, target.getLocation().add(0, 1, 0),
            8, 0.3, 0.3, 0.3, 0.5
        );
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        if (block.getType().isAir()) return;

        // WorldGuard保護チェック
        Player caster = context.getCaster();
        if (caster != null && !com.arspaper.util.WorldGuardHelper.canBuild(caster, blockLocation)) return;

        BlockData data = block.getBlockData();
        int amp = context.getAmplifyLevel();
        int steps = Math.min(Math.abs(amp) + 1, 4);
        boolean counterClockwise = amp < 0;

        if (data instanceof Rotatable rotatable) {
            BlockFace face = rotatable.getRotation();
            for (int i = 0; i < steps; i++) {
                face = rotateBlockFace(face, counterClockwise);
            }
            rotatable.setRotation(face);
            block.setBlockData(rotatable);
        } else if (data instanceof Directional directional) {
            BlockFace face = directional.getFacing();
            for (int i = 0; i < steps; i++) {
                face = rotateHorizontalFace(face, counterClockwise);
            }
            directional.setFacing(face);
            block.setBlockData(directional);
        } else {
            return;
        }

        block.getWorld().spawnParticle(
            org.bukkit.Particle.ENCHANT, blockLocation.clone().add(0.5, 0.5, 0.5),
            8, 0.3, 0.3, 0.3, 0.5
        );
        block.getWorld().playSound(blockLocation,
            org.bukkit.Sound.BLOCK_STONE_BUTTON_CLICK_ON,
            org.bukkit.SoundCategory.BLOCKS, 0.5f, 1.2f);
    }

    private BlockFace rotateBlockFace(BlockFace face, boolean counterClockwise) {
        BlockFace[] horizontal = {
            BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST,
            BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
            BlockFace.EAST, BlockFace.EAST_SOUTH_EAST,
            BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST,
            BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST,
            BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST, BlockFace.WEST_NORTH_WEST,
            BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST
        };
        for (int i = 0; i < horizontal.length; i++) {
            if (horizontal[i] == face) {
                int next = counterClockwise
                    ? (i - 4 + horizontal.length) % horizontal.length
                    : (i + 4) % horizontal.length;
                return horizontal[next];
            }
        }
        return face;
    }

    private BlockFace rotateHorizontalFace(BlockFace face, boolean counterClockwise) {
        return switch (face) {
            case NORTH -> counterClockwise ? BlockFace.WEST : BlockFace.EAST;
            case EAST  -> counterClockwise ? BlockFace.NORTH : BlockFace.SOUTH;
            case SOUTH -> counterClockwise ? BlockFace.EAST : BlockFace.WEST;
            case WEST  -> counterClockwise ? BlockFace.SOUTH : BlockFace.NORTH;
            default    -> face;
        };
    }

    /**
     * FloodgateプラグインでBedrock版プレイヤーか判定する。
     * Floodgateが無い場合はfalse。
     */
    private static boolean isBedrockPlayer(Player player) {
        try {
            Class<?> floodgateApi = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object instance = floodgateApi.getMethod("getInstance").invoke(null);
            return (boolean) floodgateApi.getMethod("isFloodgatePlayer", java.util.UUID.class)
                .invoke(instance, player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "回転"; }

    @Override
    public String getDescription() { return "対象を回転させる"; }

    @Override
    public int getManaCost() { return config.getManaCost("rotate"); }

    @Override
    public int getTier() { return config.getTier("rotate"); }
}
