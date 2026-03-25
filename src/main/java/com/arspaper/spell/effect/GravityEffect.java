package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * 対象に下方向の重力を与えるEffect。
 * Ars Nouveau Tier 1準拠:
 *   ExtendTimeなし: 瞬時の下方向速度 (0.5 + 0.25 × amp) を付与
 *   ExtendTimeあり: SLOW_FALLINGの逆として作用する重力ポーション付与
 *                  落下速度増加 + 落下ダメージ増幅
 * ブロックへの適用: FallingBlock エンティティに変換する
 */
public class GravityEffect implements SpellEffect {

    private static final double BASE_PUSH_DOWN = 0.5;
    private static final double AMPLIFY_PUSH_BONUS = 0.25;
    private static final int GRAVITY_DURATION_BASE_TICKS = 100; // 5秒
    private static final int GRAVITY_DURATION_PER_LEVEL_TICKS = 60; // 3秒
    private final NamespacedKey id;
    private final GlyphConfig config;

    public GravityEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "gravity");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int durationLevel = context.getDurationLevel();
        int amplifyLevel = context.getAmplifyLevel();

        if (durationLevel > 0) {
            // ExtendTime付き: 継続的な重力増加効果
            // SLOW_FALLINGを打ち消し、落下ダメージを増やすためSLOW_FALLINGを除去しHEAVINESSを模倣
            // バニラ互換でSLOW_FALLINGを強制除去 + 下方向速度を継続的に押さえる手段として
            // MOVEMENT_SLOWDOWN (SLOWNESS) + 落下ダメージ増幅を組み合わせる
            target.removePotionEffect(PotionEffectType.SLOW_FALLING);
            int baseDurationTicks = (int) config.getParam("gravity", "gravity-duration-base-ticks", GRAVITY_DURATION_BASE_TICKS);
            int durationPerLevel = (int) config.getParam("gravity", "gravity-duration-per-level-ticks", GRAVITY_DURATION_PER_LEVEL_TICKS);
            int durationTicks = baseDurationTicks + durationLevel * durationPerLevel;
            int amplifier = Math.max(0, amplifyLevel);
            // WEAKNESS と SLOWNESS の組み合わせで重力効果を模倣
            target.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, durationTicks, amplifier, false, true, true));
            // 初期の下方向押し込みも加える
            applyDownwardVelocity(target, amplifyLevel);
        } else {
            // ExtendTimeなし: 瞬時の下方向速度
            applyDownwardVelocity(target, amplifyLevel);
        }

        spawnGravityFx(target.getLocation());
    }

    /**
     * エンティティに下方向速度を付与する。
     */
    private void applyDownwardVelocity(LivingEntity target, int amplifyLevel) {
        double basePush = config.getParam("gravity", "base-push-down", BASE_PUSH_DOWN);
        double amplifyBonus = config.getParam("gravity", "amplify-push-bonus", AMPLIFY_PUSH_BONUS);
        double downSpeed = basePush + amplifyLevel * amplifyBonus;
        Vector velocity = target.getVelocity();
        // 現在の横方向速度を維持しつつ、縦方向を強制的に下に
        target.setVelocity(new Vector(velocity.getX(), -downSpeed, velocity.getZ()));
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        if (block == null || block.getType().isAir() || !block.getType().isSolid()) return;
        // 破壊不可能ブロックはFallingBlock変換しない
        if (block.getType() == Material.BEDROCK || block.getType() == Material.BARRIER
            || block.getType() == Material.END_PORTAL_FRAME || block.getType().getHardness() < 0) return;

        Material type = block.getType();
        org.bukkit.block.data.BlockData blockData = block.getBlockData();

        // 保護プラグイン互換: BlockBreakEventを発火して許可を確認
        Player caster = context.getCaster();
        if (caster != null) {
            BlockBreakEvent evt = new BlockBreakEvent(block, caster);
            Bukkit.getPluginManager().callEvent(evt);
            if (evt.isCancelled()) return;
        }

        // ブロックをFallingBlockエンティティに変換
        block.setType(Material.AIR);
        double basePush = config.getParam("gravity", "base-push-down", BASE_PUSH_DOWN);
        double amplifyBonus = config.getParam("gravity", "amplify-push-bonus", AMPLIFY_PUSH_BONUS);
        double downSpeed = basePush + context.getAmplifyLevel() * amplifyBonus;
        FallingBlock fallingBlock = blockLocation.getWorld().spawn(
            blockLocation.clone().add(0.5, 0, 0.5),
            FallingBlock.class,
            fb -> {
                fb.setBlockData(blockData);
                fb.setDropItem(true);
                fb.setHurtEntities(true);
                fb.setVelocity(new Vector(0, -downSpeed, 0));
            });

        spawnGravityFx(blockLocation);
    }

    /**
     * 重力効果のパーティクル・サウンド。
     */
    private void spawnGravityFx(Location loc) {
        Location effectLoc = loc.clone().add(0, 1, 0);
        effectLoc.getWorld().spawnParticle(Particle.CLOUD, effectLoc, 10, 0.3, 0.3, 0.3, 0.05);
        effectLoc.getWorld().spawnParticle(Particle.WITCH, effectLoc, 5, 0.2, 0.2, 0.2, 0.1);
        effectLoc.getWorld().playSound(effectLoc, Sound.ENTITY_ENDER_DRAGON_FLAP,
            SoundCategory.PLAYERS, 0.4f, 0.5f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "重力"; }

    @Override
    public String getDescription() { return "下方向の重力を与える"; }

    @Override
    public int getManaCost() { return config.getManaCost("gravity"); }

    @Override
    public int getTier() { return config.getTier("gravity"); }
}
