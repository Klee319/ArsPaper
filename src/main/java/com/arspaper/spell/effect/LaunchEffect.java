package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * 対象を上方に打ち上げるEffect。
 * Bounceと似ているが、より強力な打ち上げ。
 */
public class LaunchEffect implements SpellEffect {

    private static final double BASE_VELOCITY = 1.5;
    private static final double AMPLIFY_BONUS = 0.8;
    private static final double MAX_VELOCITY = 5.0;
    private final JavaPlugin plugin;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public LaunchEffect(JavaPlugin plugin, GlyphConfig config) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "launch");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        double velocity = Math.min(BASE_VELOCITY + context.getAmplifyLevel() * AMPLIFY_BONUS, MAX_VELOCITY);

        if (target instanceof Mob mob) {
            // モブのAIがvelocityを即上書きするため、AI無効化→1tick後にvelocity設定→復元
            mob.setAI(false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (mob.isValid() && !mob.isDead()) {
                    mob.setVelocity(new Vector(0, velocity, 0));
                    // 打ち上げ中にAIが干渉しないよう少し待ってから復元
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (mob.isValid() && !mob.isDead()) {
                            mob.setAI(true);
                        }
                    }, 15L);
                }
            }, 1L);
        } else {
            target.setVelocity(new Vector(0, velocity, 0));
        }

        SpellFxUtil.spawnBounceFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "打上"; }

    @Override
    public String getDescription() { return "対象を上方に強く打ち上げる"; }

    @Override
    public int getManaCost() { return config.getManaCost("launch"); }

    @Override
    public int getTier() { return config.getTier("launch"); }
}
