package com.arspaper.spell;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/**
 * スペルのパーティクル/サウンドエフェクトを管理するユーティリティ。
 * Ars Nouveauのソースコードに基づき、Formレベルの統一エフェクトと
 * Effect固有の補助エフェクトを提供する。
 *
 * 全てバニラパーティクル/サウンドを使用（Geyser互換）。
 */
@SuppressWarnings("unused")
public final class SpellFxUtil {

    private SpellFxUtil() {}

    // ========== Form共通 ==========

    /** キャスト時のサウンド（全Form共通） */
    public static void playCastSound(Player caster) {
        caster.getWorld().playSound(caster.getLocation(),
            Sound.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    /** Projectile軌跡パーティクル */
    public static void spawnProjectileTrail(Location loc) {
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 3, 0.1, 0.1, 0.1, 0.5);
    }

    /** 着弾バースト（Projectile/Touch共通） */
    public static void spawnImpactBurst(Location loc) {
        loc.getWorld().spawnParticle(Particle.WITCH, loc, 15, 0.3, 0.3, 0.3, 0.1);
        loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 10, 0.3, 0.3, 0.3, 0.5);
        loc.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
            SoundCategory.PLAYERS, 0.6f, 0.8f);
    }

    /** Self詠唱エフェクト */
    public static void spawnSelfCastParticles(LivingEntity entity) {
        Location loc = entity.getLocation().add(0, 1, 0);
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 20, 0.5, 0.5, 0.5, 1.0);
    }

    // ========== Effect固有 ==========

    /** Harm: クリティカルヒットパーティクル */
    public static void spawnHarmFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.CRIT, loc.clone().add(0, 1, 0),
            15, 0.3, 0.5, 0.3, 0.2);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT,
            SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    /** Heal: ハートパーティクル */
    public static void spawnHealFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, 1.5, 0),
            6, 0.4, 0.3, 0.4, 0);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP,
            SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    /** Blink: ポータルパーティクル + テレポートサウンド */
    public static void spawnBlinkFx(Location origin, Location destination) {
        origin.getWorld().spawnParticle(Particle.PORTAL, origin.clone().add(0, 1, 0),
            30, 0.3, 0.5, 0.3, 0.5);
        destination.getWorld().spawnParticle(Particle.PORTAL, destination.clone().add(0, 1, 0),
            30, 0.3, 0.5, 0.3, 0.5);
        destination.getWorld().spawnParticle(Particle.END_ROD, destination.clone().add(0, 1, 0),
            10, 0.3, 0.5, 0.3, 0.1);
        destination.getWorld().playSound(destination,
            Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.2f);
    }

    /** Bounce: スイープアタック + 風パーティクル */
    public static void spawnBounceFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 0.5, 0),
            5, 0.3, 0.1, 0.3, 0);
        loc.getWorld().playSound(loc, Sound.ENTITY_BAT_TAKEOFF,
            SoundCategory.PLAYERS, 0.8f, 0.6f);
    }

    /** Break: ブロック破壊パーティクル */
    public static void spawnBreakFx(Location loc, Material blockType) {
        if (blockType == null || blockType.isAir()) return;
        BlockData blockData = blockType.createBlockData();
        loc.getWorld().spawnParticle(Particle.BLOCK, loc.clone().add(0.5, 0.5, 0.5),
            20, 0.3, 0.3, 0.3, 0, blockData);
    }

    /** Grow: 骨粉パーティクル（バニラレベルイベント） */
    public static void spawnGrowFx(Location loc) {
        loc.getWorld().playEffect(loc, Effect.BONE_MEAL_USE, 0);
    }

    /** Light: エンドロッドパーティクル */
    public static void spawnLightFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0),
            10, 0.3, 0.3, 0.3, 0.05);
        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE,
            SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    /** Speed: 魔法パーティクル */
    public static void spawnSpeedFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.WITCH, loc.clone().add(0, 1, 0),
            10, 0.3, 0.5, 0.3, 0.1);
    }

    // ========== 新Effect用 ==========

    /** Ignite: 焚き火煙パーティクル */
    public static void spawnIgniteFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
            loc.clone().add(0, 0.5, 0), 8, 0.2, 0.3, 0.2, 0.02);
        loc.getWorld().spawnParticle(Particle.FLAME,
            loc.clone().add(0, 0.5, 0), 12, 0.3, 0.3, 0.3, 0.05);
        loc.getWorld().playSound(loc, Sound.ITEM_FIRECHARGE_USE,
            SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    /** Freeze: 雪花パーティクル */
    public static void spawnFreezeFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1, 0),
            20, 0.4, 0.5, 0.4, 0.05);
        loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK,
            SoundCategory.PLAYERS, 0.6f, 1.5f);
    }

    /** Knockback: スイープアタック */
    public static void spawnKnockbackFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 1, 0),
            8, 0.5, 0.3, 0.5, 0);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK,
            SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    /** Shield: エンドロッド防御パーティクル */
    public static void spawnShieldFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0),
            25, 0.5, 0.8, 0.5, 0.05);
        loc.getWorld().playSound(loc, Sound.ITEM_ARMOR_EQUIP_DIAMOND,
            SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    /** Snare: クモの巣パーティクル */
    public static void spawnSnareFx(Location loc) {
        BlockData cobwebData = Material.COBWEB.createBlockData();
        loc.getWorld().spawnParticle(Particle.BLOCK, loc.clone().add(0, 0.5, 0),
            15, 0.3, 0.3, 0.3, 0, cobwebData);
        loc.getWorld().playSound(loc, Sound.ENTITY_SPIDER_AMBIENT,
            SoundCategory.PLAYERS, 0.6f, 0.8f);
    }

    /** Pull: 引き寄せパーティクル（渦巻き状エンチャントパーティクル） */
    public static void spawnPullFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc.clone().add(0, 1, 0),
            20, 0.5, 0.5, 0.5, 1.5);
        loc.getWorld().playSound(loc, Sound.ENTITY_FISHING_BOBBER_SPLASH,
            SoundCategory.PLAYERS, 0.6f, 1.2f);
    }

    /** Harvest: 骨粉パーティクル + 収穫サウンド */
    public static void spawnHarvestFx(Location loc) {
        loc.getWorld().playEffect(loc, Effect.BONE_MEAL_USE, 0);
        loc.getWorld().playSound(loc, Sound.BLOCK_CROP_BREAK,
            SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    /** Leap: 飛び出しパーティクル（風+エンドロッド） */
    public static void spawnLeapFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 0.5, 0),
            12, 0.3, 0.3, 0.3, 0.15);
        loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 0.5, 0),
            3, 0.3, 0.1, 0.3, 0);
        loc.getWorld().playSound(loc, Sound.ENTITY_BAT_TAKEOFF,
            SoundCategory.PLAYERS, 0.7f, 1.3f);
    }

    /** Delay: 時計パーティクル（魔法+ノート） */
    public static void spawnDelayFx(Location loc, int delayTicks) {
        loc.getWorld().spawnParticle(Particle.NOTE, loc.clone().add(0, 1.5, 0),
            5, 0.3, 0.2, 0.3, 1.0);
        loc.getWorld().spawnParticle(Particle.WITCH, loc.clone().add(0, 1, 0),
            8, 0.2, 0.4, 0.2, 0.05);
        loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME,
            SoundCategory.PLAYERS, 0.5f, 1.0f);
    }

    /** Dispel: 牛乳バケツパーティクル（スプラッシュ+クリア） */
    public static void spawnDispelFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.SPLASH, loc.clone().add(0, 1, 0),
            20, 0.3, 0.5, 0.3, 0.2);
        loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc.clone().add(0, 1, 0),
            10, 0.4, 0.4, 0.4, 0.3);
        loc.getWorld().playSound(loc, Sound.ITEM_BUCKET_EMPTY,
            SoundCategory.PLAYERS, 0.6f, 1.5f);
    }

    /** Evaporate: 蒸気パーティクル（水/溶岩に応じて変化） */
    public static void spawnEvaporateFx(Location loc, Material liquidType) {
        loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0.5, 0.5, 0.5),
            15, 0.3, 0.3, 0.3, 0.05);
        if (liquidType == Material.LAVA) {
            loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0.5, 0.5, 0.5),
                10, 0.3, 0.3, 0.3, 0.03);
            loc.getWorld().playSound(loc, Sound.BLOCK_LAVA_EXTINGUISH,
                SoundCategory.PLAYERS, 0.8f, 1.0f);
        } else {
            loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.PLAYERS, 0.6f, 1.2f);
        }
    }

    /** PhantomBlock: ガラス設置/消滅パーティクル */
    public static void spawnPhantomBlockFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0.5, 0.5, 0.5),
            3, 0.2, 0.2, 0.2, 0.02);
        loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_PLACE,
            SoundCategory.PLAYERS, 0.3f, 1.2f);
    }

    /** Reset: アメジストパーティクル（初期化） */
    public static void spawnResetFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc.clone().add(0, 1, 0),
            20, 0.4, 0.5, 0.4, 0.3);
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc.clone().add(0, 1, 0),
            15, 0.5, 0.5, 0.5, 1.0);
        loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME,
            SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    /**
     * 指定ブロック位置にエンティティが存在するか（設置キャンセル判定用）。
     * バニラと同様、エンティティのバウンディングボックスとブロック位置が重なる場合true。
     */
    /**
     * 指定位置に特定のプレイヤーが存在するか判定。
     */
    public static boolean isPlayerOccupying(Location blockLocation, Player player) {
        if (player == null) return false;
        Block block = blockLocation.getBlock();
        BoundingBox blockBox = BoundingBox.of(block);
        return player.getBoundingBox().overlaps(blockBox);
    }

    /**
     * 後方互換: 任意のLivingEntityがいるか（非推奨）
     */
    public static boolean isEntityOccupying(Location blockLocation) {
        Block block = blockLocation.getBlock();
        BoundingBox blockBox = BoundingBox.of(block);
        for (Entity entity : block.getWorld().getNearbyEntities(blockBox)) {
            if (entity instanceof LivingEntity) {
                return true;
            }
        }
        return false;
    }
}
