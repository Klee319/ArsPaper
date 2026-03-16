package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 対象に滑空能力を付与するEffect。Ars NouveauのGlideに準拠。
 * エンティティ: SLOW_FALLING付与 + 落下距離リセット。
 *   Amplify 1+: SPEED Iを併用して移動速度を維持。
 * ブロック: NoOp
 */
public class GlideEffect implements SpellEffect {

    private static final int BASE_DURATION = 600;          // 30秒
    private static final int DURATION_PER_LEVEL = 160;     // ExtendTimeごと +8秒
    private final NamespacedKey id;
    private final GlyphConfig config;

    public GlideEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "glide");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int duration = Math.max(1, BASE_DURATION + context.getDurationLevel() * DURATION_PER_LEVEL);
        int amplifyLevel = Math.max(0, context.getAmplifyLevel());

        // SLOW_FALLING: ゆっくり落下（滑空感）
        target.addPotionEffect(new PotionEffect(
            PotionEffectType.SLOW_FALLING, duration, 0, false, true, true));

        // Amplify 1+: SPEED付与で滑空中の水平移動を強化
        if (amplifyLevel >= 1) {
            target.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED, duration, 0, false, true, true));
        }

        // 落下距離をリセットして落下ダメージを軽減
        target.setFallDistance(0f);

        spawnGlideFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    private void spawnGlideFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.5, 0),
            15, 0.4, 0.2, 0.4, 0.05);
        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0),
            8, 0.3, 0.3, 0.3, 0.05);
        loc.getWorld().playSound(loc, Sound.ENTITY_PHANTOM_FLAP,
            SoundCategory.PLAYERS, 0.7f, 1.2f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "滑空"; }

    @Override
    public String getDescription() { return "対象に滑空能力を付与する"; }

    @Override
    public int getManaCost() { return config.getManaCost("glide"); }

    @Override
    public int getTier() { return config.getTier("glide"); }
}
