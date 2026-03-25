package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 一時的にオオカミの群れを召喚するEffect。Ars NouveauのEffectSummonWolvesに準拠。
 * エンティティ: 対象の位置にオオカミを召喚
 * ブロック: ブロック位置にオオカミを召喚
 */
public class SummonWolvesEffect implements SpellEffect {

    private static final int DEFAULT_BASE_DURATION_TICKS = 400;       // 20秒
    private static final int DEFAULT_DURATION_PER_LEVEL = 160;        // ExtendTimeごと +8秒
    private static final int DEFAULT_BASE_WOLF_COUNT = 1;
    private static final int DEFAULT_MAX_WOLF_COUNT = 4;
    private static final double DEFAULT_BASE_HP = 20.0;
    private static final double DEFAULT_HP_PER_AMPLIFY = 8.0;
    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public SummonWolvesEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "summon_wolves");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        spawnWolves(context, target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        spawnWolves(context, blockLocation.clone().add(0.5, 1, 0.5));
    }

    private void spawnWolves(SpellContext context, Location location) {
        Player caster = context.getCaster();
        if (caster == null) return;

        int baseDurationTicks = (int) config.getParam("summon_wolves", "base-duration-ticks", DEFAULT_BASE_DURATION_TICKS);
        int durationPerLevel = (int) config.getParam("summon_wolves", "duration-per-level", DEFAULT_DURATION_PER_LEVEL);
        int baseWolfCount = (int) config.getParam("summon_wolves", "base-wolf-count", DEFAULT_BASE_WOLF_COUNT);
        int maxWolfCount = (int) config.getParam("summon_wolves", "max-wolf-count", DEFAULT_MAX_WOLF_COUNT);
        double baseHp = config.getParam("summon_wolves", "base-hp", DEFAULT_BASE_HP);
        double hpPerAmplify = config.getParam("summon_wolves", "hp-per-amplify", DEFAULT_HP_PER_AMPLIFY);
        int duration = Math.max(1, baseDurationTicks + context.getDurationLevel() * durationPerLevel);
        int wolfCount = baseWolfCount + Math.min(context.getAoeRadiusLevel(), maxWolfCount - baseWolfCount);
        double wolfHp = Math.max(1.0, baseHp + Math.max(0, context.getAmplifyLevel()) * hpPerAmplify);

        List<Wolf> wolves = new ArrayList<>();

        for (int i = 0; i < wolfCount; i++) {
            // 少しずらして召喚
            double offsetX = (Math.random() - 0.5) * 2.0;
            double offsetZ = (Math.random() - 0.5) * 2.0;
            Location spawnLoc = location.clone().add(offsetX, 0, offsetZ);

            Wolf wolf = spawnLoc.getWorld().spawn(spawnLoc, Wolf.class, w -> {
                w.setTamed(true);
                w.setOwner(caster);
                w.setPersistent(false);

                // 召喚モブマーカー + 召喚者UUID
                w.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "summoned"),
                    PersistentDataType.BYTE, (byte) 1);
                w.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "summoner_uuid"),
                    PersistentDataType.STRING, caster.getUniqueId().toString());
                w.customName(net.kyori.adventure.text.Component.text("召喚オオカミ")
                    .color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
                w.setCustomNameVisible(true);

                // AmplifyによるHP増加
                AttributeInstance maxHpAttr = w.getAttribute(Attribute.MAX_HEALTH);
                if (maxHpAttr != null) {
                    maxHpAttr.setBaseValue(wolfHp);
                    w.setHealth(wolfHp);
                }
            });

            wolves.add(wolf);
        }

        // 召喚エフェクト
        spawnSummonFx(location);

        // 一定時間後に全オオカミを消去
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Wolf wolf : wolves) {
                if (wolf.isValid()) {
                    spawnDespawnFx(wolf.getLocation());
                    wolf.remove();
                }
            }
        }, duration);
    }

    private void spawnSummonFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 20, 0.5, 0.5, 0.5, 0);
        loc.getWorld().playSound(loc, Sound.ENTITY_WOLF_AMBIENT,
            SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private void spawnDespawnFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 10, 0.3, 0.3, 0.3, 0.05);
    }

    @Override
    public boolean handlesAoeInternally() { return true; }

    @Override
    public boolean allowsTraceRepeating() { return false; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "狼召喚"; }

    @Override
    public String getDescription() { return "一時的にオオカミの群れを召喚する"; }

    @Override
    public int getManaCost() { return config.getManaCost("summon_wolves"); }

    @Override
    public int getTier() { return config.getTier("summon_wolves"); }
}
