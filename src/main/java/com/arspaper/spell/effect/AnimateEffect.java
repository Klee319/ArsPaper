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
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ブロック位置にアイアンゴーレムを一時召喚するEffect。
 * Ars Nouveau Tier 2準拠:
 *   - ブロック対象: 指定位置にアイアンゴーレムを召喚し、発動者を守らせる
 *   - 持続時間: 300 tick (15秒) + durationLevel × 160 tick (8秒)
 *   - エンティティ対象: エンティティ位置にゴーレムを召喚
 */
public class AnimateEffect implements SpellEffect {

    private static final int BASE_DURATION_TICKS = 300;
    private static final int DURATION_PER_LEVEL_TICKS = 160;

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public AnimateEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "animate");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        spawnGolemAt(context, target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        spawnGolemAt(context, blockLocation);
    }

    /**
     * AOEは内部で制御する（範囲の数+1体に制限）。
     */
    @Override
    public boolean handlesAoeInternally() { return true; }

    /**
     * 指定位置にアイアンゴーレムを召喚し、durationLevel後に除去スケジュールを組む。
     * AOEレベルに応じて複数体召喚する（aoeLevel + 1体）。
     */
    private void spawnGolemAt(SpellContext context, Location spawnLoc) {
        Player caster = context.getCaster();
        int durationLevel = context.getDurationLevel();
        int baseDuration = (int) config.getParam("animate", "base-duration-ticks", BASE_DURATION_TICKS);
        int durationPerLevel = (int) config.getParam("animate", "duration-per-level-ticks", DURATION_PER_LEVEL_TICKS);
        int durationTicks = Math.max(1, baseDuration + durationLevel * durationPerLevel);
        int spawnCount = context.getAoeRadiusLevel() + 1;

        for (int i = 0; i < spawnCount; i++) {
            double offsetX = (i == 0) ? 0 : (Math.random() * 4 - 2);
            double offsetZ = (i == 0) ? 0 : (Math.random() * 4 - 2);
            spawnSingleGolem(context, caster, spawnLoc.clone().add(offsetX, 0, offsetZ), durationTicks);
        }
    }

    private void spawnSingleGolem(SpellContext context, Player caster, Location spawnLoc, int durationTicks) {
        Location safeLocation = spawnLoc.clone().add(0.5, 0, 0.5);
        safeLocation.setYaw(0);
        safeLocation.setPitch(0);

        IronGolem golem = spawnLoc.getWorld().spawn(safeLocation, IronGolem.class, g -> {
            g.setPlayerCreated(true);

            // 召喚モブマーカー
            g.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "summoned"),
                PersistentDataType.BYTE, (byte) 1);

            // HPをAmplifyで強化
            int amplifyLevel = context.getAmplifyLevel();
            double baseHp = 100.0;
            double bonusHp = Math.max(0, amplifyLevel) * 20.0;
            var maxHpAttr = g.getAttribute(Attribute.MAX_HEALTH);
            if (maxHpAttr != null) {
                maxHpAttr.setBaseValue(baseHp + bonusHp);
            }
            g.setHealth(baseHp + bonusHp);
            g.setCustomName("§2召喚ゴーレム");
            g.setCustomNameVisible(true);
        });

        // 発動者をターゲットリストに入れないよう、発動者のチームメイト扱いにする
        // (バニラAPIではゴーレムの味方設定が困難なため、PvPターゲットから外す形で対応)
        if (caster != null) {
            NamespacedKey summonedKey = new NamespacedKey(plugin, "summoned");
            final Player finalCaster = caster;
            golem.setTarget(null);
            // ゴーレムが発動者や他の召喚モブを攻撃しないようにするため、
            // 定期的にターゲットをリセットするタスクを登録
            final IronGolem finalGolem = golem;
            plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
                if (finalGolem.isDead() || !finalGolem.isValid()) {
                    task.cancel();
                    return;
                }
                LivingEntity target = finalGolem.getTarget();
                if (target == null) return;
                // キャスターを攻撃しない
                if (finalCaster.equals(target)) {
                    finalGolem.setTarget(null);
                    return;
                }
                // 他の召喚モブを攻撃しない
                if (target.getPersistentDataContainer().has(summonedKey, PersistentDataType.BYTE)) {
                    finalGolem.setTarget(null);
                }
            }, 1L, 10L);
        }

        spawnAnimateFx(safeLocation);

        // 持続時間後にゴーレムを除去
        final IronGolem finalGolem = golem;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!finalGolem.isDead() && finalGolem.isValid()) {
                spawnAnimateFx(finalGolem.getLocation());
                finalGolem.remove();
            }
        }, durationTicks);
    }

    private void spawnAnimateFx(Location loc) {
        Location effectLoc = loc.clone().add(0, 1, 0);
        effectLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, effectLoc, 20, 0.3, 0.5, 0.3, 0.05);
        effectLoc.getWorld().playSound(effectLoc, Sound.ENTITY_IRON_GOLEM_HURT,
            SoundCategory.PLAYERS, 0.6f, 0.8f);
    }

    @Override
    public boolean allowsTraceRepeating() { return false; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "生命化"; }

    @Override
    public String getDescription() { return "一時的な味方ゴーレムを召喚する"; }

    @Override
    public int getManaCost() { return config.getManaCost("animate"); }

    @Override
    public int getTier() { return config.getTier("animate"); }
}
