package com.arspaper.spell;

import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * スペルのパーティクル/サウンドエフェクトを管理するユーティリティ。
 * Ars Nouveauのソースコードに基づき、Formレベルの統一エフェクトと
 * Effect固有の補助エフェクトを提供する。
 *
 * 全てバニラパーティクル/サウンドを使用（Geyser互換）。
 */
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
            Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.0f, 1.0f);
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
}
