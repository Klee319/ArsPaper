package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 一時的にアンデッドを召喚するEffect。
 * Ars Nouveau Tier 3準拠:
 *   - Amplify 0: Zombie召喚、Amplify 1+: Skeleton召喚（遠距離攻撃）
 *   - 持続時間: 300 tick (15秒) + durationLevel × 160 tick (8秒)
 *   - AOE: 追加召喚数 (1 + min(aoeLevel, 3)), 最大4体
 */
public class SummonUndeadEffect implements SpellEffect {

    private static final int DEFAULT_BASE_DURATION_TICKS = 300;
    private static final int DEFAULT_DURATION_PER_LEVEL_TICKS = 160;
    private static final int DEFAULT_MAX_SUMMONS = 4;

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public SummonUndeadEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "summon_undead");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        spawnUndeadAt(context, target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        spawnUndeadAt(context, blockLocation.clone().add(0.5, 1, 0.5));
    }

    private void spawnUndeadAt(SpellContext context, Location spawnLoc) {
        Player caster = context.getCaster();
        int durationLevel = context.getDurationLevel();
        int baseDurationTicks = (int) config.getParam("summon_undead", "base-duration-ticks", DEFAULT_BASE_DURATION_TICKS);
        int durationPerLevelTicks = (int) config.getParam("summon_undead", "duration-per-level-ticks", DEFAULT_DURATION_PER_LEVEL_TICKS);
        int maxSummons = (int) config.getParam("summon_undead", "max-summons", DEFAULT_MAX_SUMMONS);
        int durationTicks = Math.max(1, baseDurationTicks + durationLevel * durationPerLevelTicks);
        int amplifyLevel = context.getAmplifyLevel();
        int aoeLevel = context.getAoeRadiusLevel();
        int summonCount = Math.min(1 + Math.min(aoeLevel, 3), maxSummons);
        boolean useSkeleton = amplifyLevel >= 1;

        for (int i = 0; i < summonCount; i++) {
            // 召喚位置を少しずらす（複数体が重ならないように）
            double offsetX = (i % 2 == 0) ? i * 0.5 : -i * 0.5;
            double offsetZ = (i < 2) ? 0.5 : -0.5;
            Location safeLocation = spawnLoc.clone().add(offsetX, 0, offsetZ);
            safeLocation.setYaw(0);
            safeLocation.setPitch(0);

            if (useSkeleton) {
                spawnSkeleton(context, caster, safeLocation, durationTicks);
            } else {
                spawnZombie(context, caster, safeLocation, durationTicks);
            }
        }

        // 召喚エフェクト
        spawnSummonUndeadFx(spawnLoc, useSkeleton);
    }

    private void spawnZombie(SpellContext context, Player caster, Location loc, int durationTicks) {
        Zombie zombie = loc.getWorld().spawn(loc, Zombie.class, z -> {
            z.setCustomName("§c召喚アンデッド");
            z.setCustomNameVisible(true);
            z.setPersistent(false);
            z.setBaby(false);
            z.setShouldBurnInDay(false);
            z.setCanPickupItems(false);

            // 召喚モブマーカー
            z.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "summoned"),
                PersistentDataType.BYTE, (byte) 1);

            // 装備ドロップを無効化
            EntityEquipment equip = z.getEquipment();
            if (equip != null) {
                equip.setHelmetDropChance(0f);
                equip.setChestplateDropChance(0f);
                equip.setLeggingsDropChance(0f);
                equip.setBootsDropChance(0f);
                equip.setItemInMainHandDropChance(0f);
                equip.setItemInOffHandDropChance(0f);
            }
        });

        scheduleTargetProtection(zombie, caster);
        scheduleRemoval(zombie, durationTicks);
    }

    private void spawnSkeleton(SpellContext context, Player caster, Location loc, int durationTicks) {
        Skeleton skeleton = loc.getWorld().spawn(loc, Skeleton.class, s -> {
            s.setCustomName("§c召喚アンデッド");
            s.setCustomNameVisible(true);
            s.setPersistent(false);
            s.setShouldBurnInDay(false);

            // 召喚モブマーカー
            s.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "summoned"),
                PersistentDataType.BYTE, (byte) 1);

            // 装備ドロップを無効化
            EntityEquipment equip = s.getEquipment();
            if (equip != null) {
                equip.setHelmetDropChance(0f);
                equip.setChestplateDropChance(0f);
                equip.setLeggingsDropChance(0f);
                equip.setBootsDropChance(0f);
                equip.setItemInMainHandDropChance(0f);
                equip.setItemInOffHandDropChance(0f);
            }
        });

        scheduleTargetProtection(skeleton, caster);
        scheduleRemoval(skeleton, durationTicks);
    }

    /**
     * 召喚モブが発動者を攻撃しないよう、定期的にターゲットをリセットするタスクを登録。
     */
    private void scheduleTargetProtection(LivingEntity mob, Player caster) {
        if (caster == null) return;
        NamespacedKey summonedKey = new NamespacedKey(plugin, "summoned");
        final Player finalCaster = caster;
        org.bukkit.entity.Mob mobEntity = (org.bukkit.entity.Mob) mob;
        mobEntity.setTarget(null);

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (mob.isDead() || !mob.isValid()) {
                task.cancel();
                return;
            }
            LivingEntity target = mobEntity.getTarget();
            if (target == null) return;
            // キャスターを攻撃しない
            if (finalCaster.equals(target)) {
                mobEntity.setTarget(null);
                return;
            }
            // 他の召喚モブを攻撃しない
            if (target.getPersistentDataContainer().has(summonedKey, PersistentDataType.BYTE)) {
                mobEntity.setTarget(null);
            }
        }, 1L, 5L);
    }

    /**
     * 持続時間後にモブを除去するスケジューラを登録。
     */
    private void scheduleRemoval(LivingEntity mob, int durationTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!mob.isDead() && mob.isValid()) {
                spawnDespawnFx(mob.getLocation());
                mob.remove();
            }
        }, durationTicks);
    }

    private void spawnSummonUndeadFx(Location loc, boolean isSkeleton) {
        Location effectLoc = loc.clone().add(0, 1, 0);
        effectLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, effectLoc, 25, 0.4, 0.6, 0.4, 0.05);
        effectLoc.getWorld().spawnParticle(Particle.SMOKE, effectLoc, 15, 0.3, 0.5, 0.3, 0.05);
        if (isSkeleton) {
            effectLoc.getWorld().playSound(effectLoc, Sound.ENTITY_SKELETON_AMBIENT,
                SoundCategory.PLAYERS, 0.8f, 0.8f);
        } else {
            effectLoc.getWorld().playSound(effectLoc, Sound.ENTITY_ZOMBIE_AMBIENT,
                SoundCategory.PLAYERS, 0.8f, 0.8f);
        }
    }

    private void spawnDespawnFx(Location loc) {
        Location effectLoc = loc.clone().add(0, 1, 0);
        effectLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, effectLoc, 15, 0.3, 0.5, 0.3, 0.05);
        effectLoc.getWorld().spawnParticle(Particle.SMOKE, effectLoc, 10, 0.3, 0.4, 0.3, 0.03);
    }

    @Override
    public boolean handlesAoeInternally() { return true; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "不死召喚"; }

    @Override
    public String getDescription() { return "一時的にアンデッドを召喚する"; }

    @Override
    public int getManaCost() { return config.getManaCost("summon_undead"); }

    @Override
    public int getTier() { return config.getTier("summon_undead"); }
}
