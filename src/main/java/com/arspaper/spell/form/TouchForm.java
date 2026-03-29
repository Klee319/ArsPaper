package com.arspaper.spell.form;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellForm;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

/**
 * 近距離のエンティティ/ブロックに直接効果を適用するForm。
 * レイトレースで5ブロック先までの対象を検出。
 */
public class TouchForm implements SpellForm {

    private static final double RANGE = 5.0;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public TouchForm(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "touch");
        this.config = config;
    }

    @Override
    public void cast(Player caster, SpellContext context) {
        SpellFxUtil.playCastSound(caster);

        RayTraceResult result = caster.getWorld().rayTrace(
            caster.getEyeLocation(),
            caster.getLocation().getDirection(),
            config.getParam("touch", "range", RANGE),
            org.bukkit.FluidCollisionMode.NEVER,
            false,
            0.5,
            entity -> entity instanceof LivingEntity && !entity.equals(caster)
        );

        if (result == null) {
            // 空気に向けて発動 → マナ消費なしで失敗
            caster.sendMessage(net.kyori.adventure.text.Component.text(
                "対象が見つかりません", net.kyori.adventure.text.format.NamedTextColor.RED));
            context.setCancelled(true);
            return;
        }

        if (result.getHitEntity() instanceof LivingEntity target) {
            SpellFxUtil.spawnImpactBurst(target.getLocation());
            context.resolveOnEntity(target);
        } else if (result.getHitBlock() != null) {
            SpellFxUtil.spawnImpactBurst(result.getHitBlock().getLocation().add(0.5, 0.5, 0.5));
            if (result.getHitBlockFace() != null) {
                context.setHitFace(result.getHitBlockFace());
            }
            context.resolveOnBlock(result.getHitBlock().getLocation());
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "接触"; }

    @Override
    public String getDescription() { return "近距離の対象に直接効果を適用する"; }

    @Override
    public int getManaCost() { return config.getManaCost("touch"); }

    @Override
    public int getTier() { return config.getTier("touch"); }
}
