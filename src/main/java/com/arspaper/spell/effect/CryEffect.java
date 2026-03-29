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

import java.util.concurrent.ThreadLocalRandom;

/**
 * 対象の位置からランダムな音を発声するEffect。
 * 増幅: 音量上昇 | 減衰: 音量低下
 * 半径増加: 音の減衰率低下（遠くまで聞こえる）
 */
public class CryEffect implements SpellEffect {

    private static final Sound[] RANDOM_SOUNDS = {
        Sound.ENTITY_CAT_AMBIENT, Sound.ENTITY_COW_AMBIENT, Sound.ENTITY_PIG_AMBIENT,
        Sound.ENTITY_SHEEP_AMBIENT, Sound.ENTITY_CHICKEN_AMBIENT, Sound.ENTITY_WOLF_AMBIENT,
        Sound.ENTITY_VILLAGER_AMBIENT, Sound.ENTITY_GOAT_SCREAMING_AMBIENT,
        Sound.ENTITY_PARROT_AMBIENT, Sound.ENTITY_DONKEY_AMBIENT,
        Sound.ENTITY_BLAZE_AMBIENT, Sound.ENTITY_GHAST_AMBIENT,
        Sound.ENTITY_ENDERMAN_AMBIENT, Sound.ENTITY_CREEPER_HURT,
        Sound.ENTITY_WITCH_AMBIENT, Sound.ENTITY_IRON_GOLEM_HURT,
        Sound.ENTITY_PANDA_AMBIENT, Sound.ENTITY_FOX_AMBIENT,
        Sound.ENTITY_DOLPHIN_AMBIENT, Sound.ENTITY_RAVAGER_AMBIENT,
        Sound.ENTITY_WARDEN_AMBIENT, Sound.ENTITY_BREEZE_IDLE_GROUND,
        Sound.BLOCK_NOTE_BLOCK_HARP, Sound.BLOCK_NOTE_BLOCK_BASS,
        Sound.BLOCK_NOTE_BLOCK_BELL, Sound.BLOCK_NOTE_BLOCK_BANJO,
        Sound.BLOCK_AMETHYST_BLOCK_CHIME, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM
    };

    private final NamespacedKey id;
    private final GlyphConfig config;

    public CryEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "cry");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        playRandomSound(context, target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        playRandomSound(context, blockLocation.clone().add(0.5, 0.5, 0.5));
    }

    private void playRandomSound(SpellContext context, Location loc) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        // 音量: 基本1.0 + 増幅0.5/段 + 半径増加1.0/段（遠距離可聴）
        float volume = Math.max(0.1f,
            1.0f + context.getAmplifyLevel() * 0.5f + context.getAoeRadiusLevel() * 1.0f);
        float pitch = 0.5f + rand.nextFloat() * 1.5f; // 0.5 ~ 2.0

        Sound sound = RANDOM_SOUNDS[rand.nextInt(RANDOM_SOUNDS.length)];
        loc.getWorld().playSound(loc, sound, SoundCategory.PLAYERS, volume, pitch);

        // 音符パーティクル
        loc.getWorld().spawnParticle(Particle.NOTE, loc.clone().add(0, 1, 0),
            3 + context.getAmplifyLevel(), 0.3, 0.3, 0.3, 0);
    }

    @Override public NamespacedKey getId() { return id; }
    @Override public String getDisplayName() { return "鳴き声"; }
    @Override public String getDescription() { return "ランダムな音を対象の位置から発声する"; }
    @Override public int getManaCost() { return config.getManaCost("cry"); }
    @Override public int getTier() { return config.getTier("cry"); }
}
