package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 小さな魔法の手品を披露するEffect。Ars NouveauのPrestidigitationに準拠。
 * エンティティ/ブロック: カラフルな色付きDUSTパーティクル + ランダムサウンド
 * Randomize: パーティクルの色がさらにランダムに変化
 */
public class PrestidigitationEffect implements SpellEffect {

    private static final Color[] MAGIC_COLORS = {
        Color.fromRGB(255, 50, 50),   // 赤
        Color.fromRGB(50, 255, 50),   // 緑
        Color.fromRGB(50, 50, 255),   // 青
        Color.fromRGB(255, 200, 50),  // 金
        Color.fromRGB(200, 50, 255),  // 紫
        Color.fromRGB(50, 255, 255),  // 水色
        Color.fromRGB(255, 100, 200), // ピンク
        Color.fromRGB(255, 150, 50),  // オレンジ
    };

    private static final Sound[] MAGICAL_SOUNDS = {
        Sound.ENTITY_FIREWORK_ROCKET_TWINKLE,
        Sound.BLOCK_AMETHYST_BLOCK_CHIME,
        Sound.BLOCK_ENCHANTMENT_TABLE_USE,
        Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
        Sound.BLOCK_NOTE_BLOCK_CHIME
    };

    private final NamespacedKey id;
    private final GlyphConfig config;

    public PrestidigitationEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "prestidigitation");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        Location loc = target.getLocation().add(0, 1, 0);
        spawnColorfulParticles(loc, context.isRandomizing());

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Sound sound = MAGICAL_SOUNDS[rng.nextInt(MAGICAL_SOUNDS.length)];
        loc.getWorld().playSound(loc, sound, SoundCategory.PLAYERS, 0.8f, 1.0f + rng.nextFloat() * 0.5f);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Location loc = blockLocation.clone().add(0.5, 0.5, 0.5);
        spawnColorfulParticles(loc, context.isRandomizing());

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Sound sound = MAGICAL_SOUNDS[rng.nextInt(MAGICAL_SOUNDS.length)];
        loc.getWorld().playSound(loc, sound, SoundCategory.PLAYERS, 0.6f, 1.0f + rng.nextFloat() * 0.5f);
    }

    /**
     * カラフルなDUSTパーティクルを複数色で描画する。
     * randomize=trueの場合、色の選択がよりランダムになる。
     */
    private void spawnColorfulParticles(Location center, boolean randomize) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        World world = center.getWorld();

        if (randomize) {
            // ランダムモード: 各パーティクルが完全にランダムな色
            for (int i = 0; i < 25; i++) {
                Color color = Color.fromRGB(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256));
                float size = 1.0f + rng.nextFloat() * 1.5f;
                Location offset = center.clone().add(
                    (rng.nextDouble() - 0.5) * 1.2,
                    (rng.nextDouble() - 0.5) * 1.2,
                    (rng.nextDouble() - 0.5) * 1.2);
                world.spawnParticle(Particle.DUST, offset, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(color, size));
            }
        } else {
            // 通常モード: プリセットカラーからランダムに選んで描画
            for (int i = 0; i < 20; i++) {
                Color color = MAGIC_COLORS[rng.nextInt(MAGIC_COLORS.length)];
                float size = 1.0f + rng.nextFloat();
                Location offset = center.clone().add(
                    (rng.nextDouble() - 0.5) * 1.0,
                    (rng.nextDouble() - 0.5) * 1.0,
                    (rng.nextDouble() - 0.5) * 1.0);
                world.spawnParticle(Particle.DUST, offset, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(color, size));
            }
        }

        // ENCHANT パーティクルを軽く追加（魔法感）
        world.spawnParticle(Particle.ENCHANT, center, 8, 0.4, 0.4, 0.4, 0.5);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "手品"; }

    @Override
    public String getDescription() { return "カラフルな魔法のパーティクルを披露する"; }

    @Override
    public int getManaCost() { return config.getManaCost("prestidigitation"); }

    @Override
    public int getTier() { return config.getTier("prestidigitation"); }
}
