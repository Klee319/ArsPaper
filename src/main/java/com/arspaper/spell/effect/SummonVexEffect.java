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
import org.bukkit.entity.Vex;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 一時的にヴェックスを召喚するEffect。
 * Ars Nouveau Tier 3準拠:
 *   - 飛行するヴェックスを召喚し、周囲の敵を攻撃させる
 *   - 持続時間: 200 tick (10秒) + durationLevel × 100 tick (5秒)
 *   - Amplify: ヴェックスのHP強化 (14HP + amplifyLevel × 4HP)
 *   - AOE: 追加召喚数 (1 + min(aoeLevel, 2)), 最大3体
 */
public class SummonVexEffect implements SpellEffect {

    private static final int BASE_DURATION_TICKS = 200;
    private static final int DURATION_PER_LEVEL_TICKS = 100;
    private static final double BASE_HEALTH = 14.0;
    private static final double HEALTH_PER_AMPLIFY = 4.0;
    private static final int MAX_SUMMONS = 3;

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public SummonVexEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "summon_vex");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        spawnVexAt(context, target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        spawnVexAt(context, blockLocation.clone().add(0.5, 1, 0.5));
    }

    private void spawnVexAt(SpellContext context, Location spawnLoc) {
        Player caster = context.getCaster();
        if (caster == null) return;

        // 召喚上限チェック
        int maxSummonsPerCaster = (int) config.getParam("summon_vex", "max-summons-per-caster", 3.0);
        long currentCount = countSummonedEntities(caster);
        if (currentCount >= maxSummonsPerCaster) {
            caster.sendMessage(net.kyori.adventure.text.Component.text(
                "召喚ヴェックスの上限に達しています (" + maxSummonsPerCaster + "体)",
                net.kyori.adventure.text.format.NamedTextColor.RED));
            context.setCancelled(true);
            return;
        }

        int durationLevel = context.getDurationLevel();
        int baseDuration = (int) config.getParam("summon_vex", "base-duration-ticks", (double) BASE_DURATION_TICKS);
        int durationPerLevel = (int) config.getParam("summon_vex", "duration-per-level", (double) DURATION_PER_LEVEL_TICKS);
        int durationTicks = Math.max(1, baseDuration + durationLevel * durationPerLevel);
        int amplifyLevel = context.getAmplifyLevel();
        int aoeLevel = context.getAoeRadiusLevel();
        int maxSummons = (int) config.getParam("summon_vex", "max-summons", (double) MAX_SUMMONS);
        int baseSummonCount = (int) config.getParam("summon_vex", "base-summon-count", 1.0);
        int summonCount = Math.min(baseSummonCount + aoeLevel, maxSummons);

        double baseHealth = config.getParam("summon_vex", "base-health", BASE_HEALTH);
        double healthPerAmplify = config.getParam("summon_vex", "health-per-amplify", HEALTH_PER_AMPLIFY);
        double health = baseHealth + Math.max(0, amplifyLevel) * healthPerAmplify;

        for (int i = 0; i < summonCount; i++) {
            // 召喚位置を少しずらす
            double offsetX = (i % 2 == 0) ? i * 0.4 : -i * 0.4;
            double offsetZ = (i < 2) ? 0.3 : -0.3;
            Location safeLocation = spawnLoc.clone().add(offsetX, 1.0, offsetZ);

            final double finalHealth = health;
            Vex vex = spawnLoc.getWorld().spawn(safeLocation, Vex.class, v -> {
                v.setCustomName("§b召喚ヴェックス");
                v.setCustomNameVisible(true);
                v.setPersistent(false);

                // 召喚モブマーカー + 召喚者UUID
                v.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "summoned"),
                    PersistentDataType.BYTE, (byte) 1);
                if (caster != null) {
                    v.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "summoner_uuid"),
                        PersistentDataType.STRING, caster.getUniqueId().toString());
                }

                // HPをAmplifyで強化
                var maxHpAttr = v.getAttribute(Attribute.MAX_HEALTH);
                if (maxHpAttr != null) {
                    maxHpAttr.setBaseValue(finalHealth);
                }
                v.setHealth(finalHealth);
            });

            scheduleTargetProtection(vex, caster);
            scheduleRemoval(vex, durationTicks);
        }

        // 召喚エフェクト
        spawnSummonVexFx(spawnLoc);
    }

    /**
     * 召喚ヴェックスが発動者を攻撃しないよう、定期的にターゲットをリセットするタスクを登録。
     */
    private void scheduleTargetProtection(Vex vex, Player caster) {
        if (caster == null) return;
        NamespacedKey summonedKey = new NamespacedKey(plugin, "summoned");
        final Player finalCaster = caster;
        vex.setTarget(null);

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (vex.isDead() || !vex.isValid()) {
                task.cancel();
                return;
            }
            LivingEntity target = vex.getTarget();
            if (target == null) return;
            // キャスターを攻撃しない
            if (finalCaster.equals(target)) {
                vex.setTarget(null);
                return;
            }
            // 他の召喚モブを攻撃しない
            if (target.getPersistentDataContainer().has(summonedKey, PersistentDataType.BYTE)) {
                vex.setTarget(null);
            }
        }, 1L, 5L);
    }

    /**
     * 持続時間後にヴェックスを除去するスケジューラを登録。
     */
    private void scheduleRemoval(Vex vex, int durationTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!vex.isDead() && vex.isValid()) {
                spawnDespawnFx(vex.getLocation());
                vex.remove();
            }
        }, durationTicks);
    }

    private long countSummonedEntities(Player caster) {
        org.bukkit.NamespacedKey summonedKey = new org.bukkit.NamespacedKey(plugin, "summoned");
        org.bukkit.NamespacedKey summonerKey = new org.bukkit.NamespacedKey(plugin, "summoner_uuid");
        String casterUuid = caster.getUniqueId().toString();
        return caster.getWorld().getEntities().stream()
            .filter(e -> e instanceof org.bukkit.entity.LivingEntity le
                && le.getPersistentDataContainer().has(summonedKey, org.bukkit.persistence.PersistentDataType.BYTE)
                && casterUuid.equals(le.getPersistentDataContainer().get(summonerKey, org.bukkit.persistence.PersistentDataType.STRING)))
            .count();
    }

    private void spawnSummonVexFx(Location loc) {
        Location effectLoc = loc.clone().add(0, 1, 0);
        effectLoc.getWorld().spawnParticle(Particle.ENCHANT, effectLoc, 25, 0.4, 0.6, 0.4, 1.0);
        effectLoc.getWorld().spawnParticle(Particle.END_ROD, effectLoc, 12, 0.3, 0.5, 0.3, 0.05);
        effectLoc.getWorld().playSound(effectLoc, Sound.ENTITY_VEX_AMBIENT,
            SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    private void spawnDespawnFx(Location loc) {
        Location effectLoc = loc.clone().add(0, 1, 0);
        effectLoc.getWorld().spawnParticle(Particle.ENCHANT, effectLoc, 15, 0.3, 0.5, 0.3, 0.8);
        effectLoc.getWorld().spawnParticle(Particle.END_ROD, effectLoc, 8, 0.3, 0.4, 0.3, 0.03);
    }

    @Override
    public boolean handlesAoeInternally() { return true; }

    @Override
    public boolean allowsTraceRepeating() { return false; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "妖精召喚"; }

    @Override
    public String getDescription() { return "一時的にヴェックスを召喚する"; }

    @Override
    public int getManaCost() { return config.getManaCost("summon_vex"); }

    @Override
    public int getTier() { return config.getTier("summon_vex"); }
}
