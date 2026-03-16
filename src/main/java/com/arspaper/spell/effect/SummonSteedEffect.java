package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 一時的に騎乗可能な馬を召喚するEffect。Ars NouveauのEffectSummonSteedに準拠。
 * エンティティ: 対象の位置に馬を召喚
 * ブロック: ブロック位置に馬を召喚
 */
public class SummonSteedEffect implements SpellEffect {

    private static final int BASE_DURATION_TICKS = 600;       // 30秒
    private static final int DURATION_PER_LEVEL = 200;        // ExtendTimeごと +10秒
    private static final double SPEED_BOOST_PER_AMPLIFY = 0.05;
    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public SummonSteedEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "summon_steed");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        spawnHorse(context, target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        spawnHorse(context, blockLocation.clone().add(0.5, 1, 0.5));
    }

    private void spawnHorse(SpellContext context, Location location) {
        Player caster = context.getCaster();
        if (caster == null) return;

        int duration = Math.max(1, BASE_DURATION_TICKS + context.getDurationLevel() * DURATION_PER_LEVEL);
        int amplifyLevel = context.getAmplifyLevel();

        Horse horse = location.getWorld().spawn(location, Horse.class, h -> {
            h.setTamed(true);
            h.setOwner(caster);
            h.setPersistent(false);

            // 召喚モブマーカー
            h.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "summoned"),
                PersistentDataType.BYTE, (byte) 1);

            h.customName(net.kyori.adventure.text.Component.text("召喚馬")
                .color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
            h.setCustomNameVisible(true);

            // サドル装備
            h.getInventory().setSaddle(new ItemStack(Material.SADDLE));

            // Amplifyによるスピードブースト（最大3段階）
            int clampedAmplify = Math.min(Math.max(0, amplifyLevel), 3);
            if (clampedAmplify > 0) {
                AttributeInstance speedAttr = h.getAttribute(Attribute.MOVEMENT_SPEED);
                if (speedAttr != null) {
                    double baseSpeed = speedAttr.getBaseValue();
                    speedAttr.setBaseValue(baseSpeed + SPEED_BOOST_PER_AMPLIFY * clampedAmplify);
                }
            }
        });

        // 術者を騎乗させる
        horse.addPassenger(caster);

        // 召喚エフェクト
        spawnSummonFx(location);

        // 一定時間後に消滅
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (horse.isValid()) {
                horse.eject();
                spawnDespawnFx(horse.getLocation());
                horse.remove();
            }
        }, duration);
    }

    private void spawnSummonFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 20, 0.5, 0.5, 0.5, 0);
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 15, 0.4, 0.4, 0.4, 0.05);
        loc.getWorld().playSound(loc, Sound.ENTITY_HORSE_AMBIENT,
            SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private void spawnDespawnFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 20, 0.5, 0.5, 0.5, 0.1);
        loc.getWorld().playSound(loc, Sound.ENTITY_HORSE_DEATH,
            SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    @Override
    public boolean handlesAoeInternally() { return true; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "騎馬召喚"; }

    @Override
    public String getDescription() { return "一時的に騎乗可能な馬を召喚する"; }

    @Override
    public int getManaCost() { return config.getManaCost("summon_steed"); }

    @Override
    public int getTier() { return config.getTier("summon_steed"); }
}
