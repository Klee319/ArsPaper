package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

/**
 * 対象位置に花火を打ち上げるEffect。
 * Ars Nouveau Tier 2準拠:
 *   - FIREWORK エンティティを対象位置にスポーン
 *   - Amplify: 花火スターの数を増加 (1 + amplifyLevel)
 *   - ExtendTime: 飛翔持続時間を増加 (1 + durationLevel)
 *   - ランダムカラーで花火エフェクト
 */
public class FireworkEffect implements SpellEffect {

    private static final Random RANDOM = new Random();
    private static final int DEFAULT_MAX_STARS = 5;

    private final NamespacedKey id;
    private final GlyphConfig config;

    public FireworkEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "firework");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        spawnFireworkAt(context, target.getLocation().add(0, 1, 0));
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        spawnFireworkAt(context, blockLocation.clone().add(0.5, 1, 0.5));
    }

    /**
     * 指定位置に花火エンティティをスポーンする。
     */
    private void spawnFireworkAt(SpellContext context, Location spawnLoc) {
        int amplifyLevel = Math.max(0, context.getAmplifyLevel());
        int durationLevel = context.getDurationLevel();

        // スター数: base + amplifyLevel (上限設定可能)
        int maxStars = (int) config.getParam("firework", "max-stars", (double) DEFAULT_MAX_STARS);
        int baseStars = (int) config.getParam("firework", "base-stars", 1.0);
        int starCount = Math.min(baseStars + amplifyLevel, maxStars);

        // 飛翔距離: base + durationLevel (上限設定可能)
        int maxFlightPower = (int) config.getParam("firework", "max-flight-power", 3.0);
        int baseFlightPower = (int) config.getParam("firework", "base-flight-power", 1.0);
        int flightPower = Math.min(baseFlightPower + Math.max(0, durationLevel), maxFlightPower);

        // 花火エンティティ数: base + aoeRadiusLevel
        int baseFireworkCount = (int) config.getParam("firework", "base-firework-count", 1.0);
        int maxFireworkCount = (int) config.getParam("firework", "max-firework-count", 5.0);
        int fireworkCount = Math.min(baseFireworkCount + context.getAoeRadiusLevel(), maxFireworkCount);

        for (int fc = 0; fc < fireworkCount; fc++) {
            // 複数花火は少しずらしてスポーン
            double ox = fc == 0 ? 0 : (RANDOM.nextDouble() * 2 - 1);
            double oz = fc == 0 ? 0 : (RANDOM.nextDouble() * 2 - 1);
            Location fwLoc = spawnLoc.clone().add(ox, 0, oz);

        fwLoc.getWorld().spawn(fwLoc, Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            meta.setPower(flightPower);

            for (int i = 0; i < starCount; i++) {
                meta.addEffect(buildRandomEffect());
            }

            fw.setFireworkMeta(meta);
        });
        } // end fireworkCount loop

        spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH,
            SoundCategory.PLAYERS, 0.7f, 1.0f);
    }

    /**
     * ランダムな色と形状で花火スターエフェクトを生成する。
     */
    private org.bukkit.FireworkEffect buildRandomEffect() {
        Color primaryColor = Color.fromRGB(RANDOM.nextInt(256), RANDOM.nextInt(256), RANDOM.nextInt(256));
        Color fadeColor = Color.fromRGB(RANDOM.nextInt(256), RANDOM.nextInt(256), RANDOM.nextInt(256));

        org.bukkit.FireworkEffect.Type[] types = org.bukkit.FireworkEffect.Type.values();
        org.bukkit.FireworkEffect.Type type = types[RANDOM.nextInt(types.length)];

        return org.bukkit.FireworkEffect.builder()
            .with(type)
            .withColor(primaryColor)
            .withFade(fadeColor)
            .trail(RANDOM.nextBoolean())
            .flicker(RANDOM.nextBoolean())
            .build();
    }

    @Override
    public boolean allowsTraceRepeating() { return false; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "花火"; }

    @Override
    public String getDescription() { return "花火を打ち上げる"; }

    @Override
    public int getManaCost() { return config.getManaCost("firework"); }

    @Override
    public int getTier() { return config.getTier("firework"); }
}
