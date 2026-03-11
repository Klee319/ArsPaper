package com.arspaper.spell.form;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellForm;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

/**
 * 近距離のエンティティ/ブロックに直接効果を適用するForm。
 * レイトレースで5ブロック先までの対象を検出。
 */
public class TouchForm implements SpellForm {

    private static final double RANGE = 5.0;
    private final NamespacedKey id;

    public TouchForm(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "touch");
    }

    @Override
    public void cast(Player caster, SpellContext context) {
        SpellFxUtil.playCastSound(caster);

        RayTraceResult result = caster.getWorld().rayTrace(
            caster.getEyeLocation(),
            caster.getLocation().getDirection(),
            RANGE,
            org.bukkit.FluidCollisionMode.NEVER,
            true,
            0.5,
            entity -> entity instanceof LivingEntity && !entity.equals(caster)
        );

        if (result == null) return;

        if (result.getHitEntity() instanceof LivingEntity target) {
            SpellFxUtil.spawnImpactBurst(target.getLocation());
            context.resolveOnEntity(target);
        } else if (result.getHitBlock() != null) {
            SpellFxUtil.spawnImpactBurst(result.getHitBlock().getLocation().add(0.5, 0.5, 0.5));
            context.resolveOnBlock(result.getHitBlock().getLocation());
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Touch"; }

    @Override
    public int getManaCost() { return 3; }

    @Override
    public int getTier() { return 1; }
}
