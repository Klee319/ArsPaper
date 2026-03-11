package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 対象にダメージ耐性を付与するEffect。
 * Resistance（ダメージ軽減）ポーション効果を付与する。
 */
public class ShieldEffect implements SpellEffect {

    private static final int BASE_DURATION = 200; // 10秒
    private final NamespacedKey id;

    public ShieldEffect(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "shield");
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int duration = BASE_DURATION + context.getDurationTicks();
        int amplifier = context.getAmplifyLevel();
        target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, amplifier));
        SpellFxUtil.spawnShieldFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Shield"; }

    @Override
    public int getManaCost() { return 20; }

    @Override
    public int getTier() { return 2; }
}
