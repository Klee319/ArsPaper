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
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 囮のデコイを召喚して敵の注意を引くEffect。
 * Ars Nouveau Tier 3準拠:
 *   - キャスターの装備をコピーしたZombieを召喚（見た目で囮として機能）
 *   - 敵対モブが自然にデコイをターゲットする
 *   - 持続時間: 200 tick (10秒) + durationLevel × 100 tick (5秒)
 *   - Amplify: デコイHP強化 (20HP + amplifyLevel × 10HP)
 */
public class SummonDecoyEffect implements SpellEffect {

    private static final int DEFAULT_BASE_DURATION_TICKS = 200;
    private static final int DEFAULT_DURATION_PER_LEVEL_TICKS = 100;
    private static final double DEFAULT_BASE_HEALTH = 20.0;
    private static final double DEFAULT_HEALTH_PER_AMPLIFY = 10.0;

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public SummonDecoyEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "summon_decoy");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        spawnDecoyAt(context, target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        spawnDecoyAt(context, blockLocation.clone().add(0.5, 1, 0.5));
    }

    private void spawnDecoyAt(SpellContext context, Location spawnLoc) {
        Player caster = context.getCaster();
        if (caster == null) return;

        int durationLevel = context.getDurationLevel();
        int baseDurationTicks = (int) config.getParam("summon_decoy", "base-duration-ticks", DEFAULT_BASE_DURATION_TICKS);
        int durationPerLevelTicks = (int) config.getParam("summon_decoy", "duration-per-level-ticks", DEFAULT_DURATION_PER_LEVEL_TICKS);
        int durationTicks = Math.max(1, baseDurationTicks + durationLevel * durationPerLevelTicks);
        int amplifyLevel = context.getAmplifyLevel();
        double baseHealth = config.getParam("summon_decoy", "base-health", DEFAULT_BASE_HEALTH);
        double healthPerAmplify = config.getParam("summon_decoy", "health-per-amplify", DEFAULT_HEALTH_PER_AMPLIFY);
        double health = baseHealth + Math.max(0, amplifyLevel) * healthPerAmplify;

        Location safeLocation = spawnLoc.clone().add(0.5, 0, 0.5);
        safeLocation.setYaw(caster.getLocation().getYaw());
        safeLocation.setPitch(0);

        final double finalHealth = health;
        final Player finalCaster = caster;
        Zombie decoy = spawnLoc.getWorld().spawn(safeLocation, Zombie.class, z -> {
            z.setCustomName(finalCaster.getName());
            z.setCustomNameVisible(true);
            z.setPersistent(false);
            z.setBaby(false);
            z.setShouldBurnInDay(false);
            z.setSilent(true);
            z.setAI(true);
            z.setCanPickupItems(false);

            // 召喚モブマーカー
            z.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "summoned"),
                PersistentDataType.BYTE, (byte) 1);

            // HPをAmplifyで強化
            var maxHpAttr = z.getAttribute(Attribute.MAX_HEALTH);
            if (maxHpAttr != null) {
                maxHpAttr.setBaseValue(finalHealth);
            }
            z.setHealth(finalHealth);

            // キャスターの装備をコピー
            copyEquipment(finalCaster, z);
        });

        // デコイがキャスターを攻撃しないようにする
        scheduleTargetProtection(decoy, caster);

        // 周辺の敵対モブにデコイをターゲットさせる
        attractNearbyMobs(decoy, spawnLoc);

        // 召喚エフェクト
        spawnDecoyFx(safeLocation);

        // 持続時間後にデコイを除去
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!decoy.isDead() && decoy.isValid()) {
                spawnDespawnFx(decoy.getLocation());
                decoy.remove();
            }
        }, durationTicks);
    }

    /**
     * キャスターの装備をデコイにコピーする。
     */
    private void copyEquipment(Player caster, Zombie decoy) {
        EntityEquipment casterEquip = caster.getEquipment();
        EntityEquipment decoyEquip = decoy.getEquipment();
        if (casterEquip == null || decoyEquip == null) return;

        decoyEquip.setHelmet(casterEquip.getHelmet() != null ? casterEquip.getHelmet().clone() : null);
        decoyEquip.setChestplate(casterEquip.getChestplate() != null ? casterEquip.getChestplate().clone() : null);
        decoyEquip.setLeggings(casterEquip.getLeggings() != null ? casterEquip.getLeggings().clone() : null);
        decoyEquip.setBoots(casterEquip.getBoots() != null ? casterEquip.getBoots().clone() : null);
        decoyEquip.setItemInMainHand(casterEquip.getItemInMainHand() != null ? casterEquip.getItemInMainHand().clone() : null);
        decoyEquip.setItemInOffHand(casterEquip.getItemInOffHand() != null ? casterEquip.getItemInOffHand().clone() : null);

        // 装備ドロップを無効化
        decoyEquip.setHelmetDropChance(0.0f);
        decoyEquip.setChestplateDropChance(0.0f);
        decoyEquip.setLeggingsDropChance(0.0f);
        decoyEquip.setBootsDropChance(0.0f);
        decoyEquip.setItemInMainHandDropChance(0.0f);
        decoyEquip.setItemInOffHandDropChance(0.0f);
    }

    /**
     * 周辺の敵対モブにデコイをターゲットさせる。
     */
    private void attractNearbyMobs(Zombie decoy, Location loc) {
        double attractRadius = 16.0;
        loc.getNearbyLivingEntities(attractRadius).stream()
            .filter(e -> e instanceof Mob)
            .filter(e -> !(e instanceof Player))
            .filter(e -> !e.equals(decoy))
            .map(e -> (Mob) e)
            .forEach(mob -> mob.setTarget(decoy));
    }

    /**
     * デコイが発動者を攻撃しないよう、定期的にターゲットをリセットするタスクを登録。
     */
    private void scheduleTargetProtection(Zombie decoy, Player caster) {
        NamespacedKey summonedKey = new NamespacedKey(plugin, "summoned");
        final Player finalCaster = caster;
        decoy.setTarget(null);

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (decoy.isDead() || !decoy.isValid()) {
                task.cancel();
                return;
            }
            LivingEntity target = decoy.getTarget();
            if (target == null) return;
            // キャスターを攻撃しない
            if (finalCaster.equals(target)) {
                decoy.setTarget(null);
                return;
            }
            // 他の召喚モブを攻撃しない
            if (target.getPersistentDataContainer().has(summonedKey, PersistentDataType.BYTE)) {
                decoy.setTarget(null);
            }
        }, 1L, 5L);
    }

    private void spawnDecoyFx(Location loc) {
        Location effectLoc = loc.clone().add(0, 1, 0);
        effectLoc.getWorld().spawnParticle(Particle.INSTANT_EFFECT, effectLoc, 20, 0.4, 0.6, 0.4, 0.1);
        effectLoc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, effectLoc, 12, 0.3, 0.5, 0.3, 0.05);
        effectLoc.getWorld().playSound(effectLoc, Sound.ENTITY_ILLUSIONER_CAST_SPELL,
            SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    private void spawnDespawnFx(Location loc) {
        Location effectLoc = loc.clone().add(0, 1, 0);
        effectLoc.getWorld().spawnParticle(Particle.INSTANT_EFFECT, effectLoc, 12, 0.3, 0.5, 0.3, 0.08);
        effectLoc.getWorld().spawnParticle(Particle.SMOKE, effectLoc, 8, 0.3, 0.4, 0.3, 0.03);
    }

    @Override
    public boolean handlesAoeInternally() { return true; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "囮召喚"; }

    @Override
    public String getDescription() { return "囮のデコイを召喚して敵の注意を引く"; }

    @Override
    public int getManaCost() { return config.getManaCost("summon_decoy"); }

    @Override
    public int getTier() { return config.getTier("summon_decoy"); }
}
