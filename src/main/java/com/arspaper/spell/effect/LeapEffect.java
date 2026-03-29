package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
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
 * 対象にジャンプブースト効果を付与するEffect。
 * 増幅でジャンプ力上昇、減衰で跳躍不能（amplifier 128）。
 * 延長/短縮で持続時間を調整。
 */
public class LeapEffect implements SpellEffect {

    private static final int BASE_DURATION = 200;           // 10秒
    private static final int DURATION_PER_EXTEND = 100;     // ExtendTimeごと +5秒

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public LeapEffect(JavaPlugin plugin, GlyphConfig config) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "leap");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int baseDuration = (int) config.getParam("leap", "base-duration", BASE_DURATION);
        int durationPerLevel = (int) config.getParam("leap", "duration-per-level", DURATION_PER_EXTEND);
        int duration = Math.max(20, baseDuration + context.getDurationLevel() * durationPerLevel);

        if (context.hasDampen()) {
            // 減衰: amplifier 128 = 跳躍不能（バニラ仕様）
            target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 128));
        } else {
            // 増幅: ジャンプブーストレベル上昇
            int amplifier = Math.max(0, context.getAmplifyLevel());
            target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, amplifier));
        }

        SpellFxUtil.spawnLeapFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {}

    @Override public NamespacedKey getId() { return id; }
    @Override public String getDisplayName() { return "跳躍"; }
    @Override public String getDescription() { return "ジャンプブースト効果を付与する（減衰で跳躍不能）"; }
    @Override public int getManaCost() { return config.getManaCost("leap"); }
    @Override public int getTier() { return config.getTier("leap"); }
}
