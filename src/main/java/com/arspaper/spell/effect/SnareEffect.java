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
 * 対象の動きを拘束するEffect。Ars NouveauのEffectSnareに準拠。
 * 極度の移動速度低下 + 採掘速度低下を付与して実質的に行動不能にする。
 */
public class SnareEffect implements SpellEffect {

    private static final int BASE_DURATION = 100; // 5秒
    private final NamespacedKey id;

    public SnareEffect(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "snare");
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int duration = BASE_DURATION + context.getDurationTicks();
        int amplifier = 2 + context.getAmplifyLevel();

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration, amplifier));
        SpellFxUtil.spawnSnareFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Snare"; }

    @Override
    public int getManaCost() { return 12; }

    @Override
    public int getTier() { return 1; }
}
