package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 対象の動きを完全に拘束するEffect。Ars Nouveau Tier 1準拠。
 * Slowness 127（移動完全停止）+ Jump Boost -1（ジャンプ不可）+ Mining Fatigueを付与する。
 */
public class SnareEffect implements SpellEffect {

    private static final int DEFAULT_BASE_DURATION = 160;       // 8秒
    private static final int DEFAULT_DURATION_PER_LEVEL = 20;  // ExtendTimeごと +1秒
    private final NamespacedKey id;
    private final GlyphConfig config;

    public SnareEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "snare");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int baseDuration = (int) config.getParam("snare", "base-duration", DEFAULT_BASE_DURATION);
        int durationPerLevel = (int) config.getParam("snare", "duration-per-level", DEFAULT_DURATION_PER_LEVEL);
        int duration = baseDuration + context.getDurationLevel() * durationPerLevel;

        // Slowness 255 = 移動を完全停止
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 255));
        // Jump Boost amplifier 128 = signed byteで-128、ジャンプ高度を負にして跳ねさせない
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 128));
        // Mining Fatigue: 採掘・攻撃速度も低下
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration, 3));

        SpellFxUtil.spawnSnareFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "拘束"; }

    @Override
    public String getDescription() { return "対象の移動・ジャンプを完全に封じる"; }

    @Override
    public int getManaCost() { return config.getManaCost("snare"); }

    @Override
    public int getTier() { return config.getTier("snare"); }
}
