package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * 地面からエヴォーカーの牙を召喚するEffect。Ars NouveauのFangsに準拠。
 * エンティティ: 対象の足元に牙を生成。Amplifyで牙数増加（キャスター方向からの直線配置）。
 *   AOE付き: 円形に牙を配置。
 * ブロック: ブロック位置にキャスターの視線方向へ牙を直線配置。
 *   AOE付き: 広がり幅を増加。
 * 最大牙数: 15
 */
public class FangsEffect implements SpellEffect {

    private static final int BASE_FANG_COUNT_ENTITY = 1;
    private static final int BASE_FANG_COUNT_BLOCK = 3;
    private static final int AMPLIFY_BONUS = 2;
    private static final int MAX_FANGS = 15;
    private static final double FANG_SPACING = 1.2;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public FangsEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "fangs");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        Player caster = context.getCaster();
        int amplifyLevel = Math.max(0, context.getAmplifyLevel());
        int aoeLevel = context.getAoeRadiusLevel();
        int maxFangs = (int) config.getParam("fangs", "max-fangs", (double) MAX_FANGS);
        int amplifyBonus = (int) config.getParam("fangs", "amplify-bonus", (double) AMPLIFY_BONUS);
        int baseFangCountEntity = (int) config.getParam("fangs", "base-fang-count-entity", (double) BASE_FANG_COUNT_ENTITY);
        int fangCount = Math.min(baseFangCountEntity + amplifyLevel * amplifyBonus, maxFangs);

        Location targetLoc = target.getLocation();

        if (aoeLevel > 0) {
            // AOE: 円形に牙を配置
            spawnFangsCircle(targetLoc, fangCount, aoeLevel, caster);
        } else if (caster != null && fangCount > 1) {
            // 直線: キャスターから対象への方向に牙を配置
            Vector direction = targetLoc.toVector()
                .subtract(caster.getLocation().toVector()).normalize();
            spawnFangsLine(targetLoc, direction, fangCount, caster);
        } else {
            // 単体: 足元に1つ
            spawnSingleFang(targetLoc, caster);
        }

        spawnFangsFx(targetLoc);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Player caster = context.getCaster();
        if (caster == null) return;

        int amplifyLevel = Math.max(0, context.getAmplifyLevel());
        int aoeLevel = context.getAoeRadiusLevel();
        int maxFangs = (int) config.getParam("fangs", "max-fangs", (double) MAX_FANGS);
        int amplifyBonus = (int) config.getParam("fangs", "amplify-bonus", (double) AMPLIFY_BONUS);
        int baseFangCountBlock = (int) config.getParam("fangs", "base-fang-count-block", (double) BASE_FANG_COUNT_BLOCK);
        int fangCount = Math.min(baseFangCountBlock + amplifyLevel * amplifyBonus, maxFangs);

        // ブロック対象の場合、牙はブロックの上面に生成する
        Location spawnLoc = blockLocation.clone().add(0, 1, 0);

        if (aoeLevel > 0) {
            // AOE: 円形に牙を配置（広がり幅増加）
            spawnFangsCircle(spawnLoc, fangCount, aoeLevel, caster);
        } else {
            // 直線: キャスターの視線方向に牙を配置
            Vector direction = caster.getLocation().getDirection().setY(0).normalize();
            spawnFangsLine(spawnLoc, direction, fangCount, caster);
        }

        spawnFangsFx(blockLocation);
    }

    /**
     * 直線上に牙を配置する。
     */
    private void spawnFangsLine(Location origin, Vector direction, int count, LivingEntity owner) {
        double fangSpacing = config.getParam("fangs", "fang-spacing", FANG_SPACING);
        for (int i = 0; i < count; i++) {
            Location fangLoc = origin.clone().add(direction.clone().multiply(i * fangSpacing));
            spawnSingleFang(fangLoc, owner);
        }
    }

    /**
     * 円形に牙を配置する。
     */
    private void spawnFangsCircle(Location center, int count, int aoeLevel, LivingEntity owner) {
        double baseCircleRadius = config.getParam("fangs", "base-circle-radius", 1.0);
        double radiusPerAoe = config.getParam("fangs", "circle-radius-per-aoe", 0.8);
        double radius = baseCircleRadius + aoeLevel * radiusPerAoe;
        double angleStep = 2.0 * Math.PI / count;

        for (int i = 0; i < count; i++) {
            double angle = angleStep * i;
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;
            Location fangLoc = center.clone().add(offsetX, 0, offsetZ);
            spawnSingleFang(fangLoc, owner);
        }
    }

    /**
     * 1つのEvokerFangsを生成し、ownerを設定してダメージが入るようにする。
     */
    private void spawnSingleFang(Location loc, LivingEntity owner) {
        loc.getWorld().spawn(loc, EvokerFangs.class, fangs -> {
            if (owner != null) {
                fangs.setOwner(owner);
            }
        });
    }

    private void spawnFangsFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0, 0.5, 0),
            12, 0.5, 0.2, 0.5, 0.02);
        loc.getWorld().playSound(loc, Sound.ENTITY_EVOKER_FANGS_ATTACK,
            SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    @Override
    public boolean allowsTraceRepeating() { return true; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "牙"; }

    @Override
    public String getDescription() { return "地面からエヴォーカーの牙を召喚する"; }

    @Override
    public int getManaCost() { return config.getManaCost("fangs"); }

    @Override
    public int getTier() { return config.getTier("fangs"); }
}
