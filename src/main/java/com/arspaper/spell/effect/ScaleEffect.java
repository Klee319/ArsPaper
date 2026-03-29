package com.arspaper.spell.effect;

import com.arspaper.ArsPaper;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * スケールエフェクト。対象のエンティティサイズを変更する。
 * 増幅: サイズ増加 | 減衰: サイズ縮小
 * 延長/短縮: 効果時間
 * 当たり判定もサイズに連動する（GENERIC_SCALE属性）。
 */
public class ScaleEffect implements SpellEffect {

    private static final int BASE_DURATION = 600;            // 30秒
    private static final int DURATION_PER_LEVEL = 200;       // +10秒/段
    private static final double SCALE_PER_LEVEL = 0.5;       // +50%/段
    private static final double MIN_SCALE = 0.0625;          // MC最小
    private static final double MAX_SCALE = 16.0;            // MC最大

    /** エンティティUUID → 前回のremovalタスク。再適用時にキャンセルする。 */
    private static final Map<UUID, BukkitTask> REMOVAL_TASKS = new ConcurrentHashMap<>();

    /** プラグイン無効化時に全タスクをキャンセルしマップをクリアする。 */
    public static void cleanupAll() {
        REMOVAL_TASKS.values().forEach(BukkitTask::cancel);
        REMOVAL_TASKS.clear();
    }

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public ScaleEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "scale");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        AttributeInstance scaleAttr = target.getAttribute(Attribute.SCALE);
        if (scaleAttr == null) return;

        int baseDuration = (int) config.getParam("scale", "base-duration", BASE_DURATION);
        int durationPerLevel = (int) config.getParam("scale", "duration-per-level", DURATION_PER_LEVEL);
        int durationTicks = baseDuration + context.getDurationLevel() * durationPerLevel;

        double scalePerLevel = config.getParam("scale", "scale-per-level", SCALE_PER_LEVEL);
        int level = context.getAmplifyLevel();
        // level>0: 大きく、level<0: 小さく、level=0: 基本倍率
        double scaleFactor = 1.0 + level * scalePerLevel;
        double minScale = config.getParam("scale", "min-scale", MIN_SCALE);
        double maxScale = config.getParam("scale", "max-scale", MAX_SCALE);
        scaleFactor = Math.max(minScale, Math.min(maxScale, scaleFactor));

        // 修飾子を適用（乗算ベース）
        UUID entityUUID = target.getUniqueId();
        NamespacedKey modKey = new NamespacedKey(plugin, "arspaper_scale_" + entityUUID);

        // 既存の同名修飾子を除去
        scaleAttr.getModifiers().stream()
            .filter(m -> m.getKey().equals(modKey))
            .forEach(scaleAttr::removeModifier);

        // 前回のremovalタスクをキャンセル
        BukkitTask prevTask = REMOVAL_TASKS.remove(entityUUID);
        if (prevTask != null) prevTask.cancel();

        double modifierAmount = scaleFactor - 1.0; // MULTIPLY_SCALAR_1 は (1 + amount) 倍
        AttributeModifier modifier = new AttributeModifier(
            modKey, modifierAmount, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        scaleAttr.addModifier(modifier);

        spawnScaleFx(target.getLocation(), level >= 0);

        // 持続時間後に修飾子を除去
        BukkitTask removalTask = new BukkitRunnable() {
            @Override
            public void run() {
                REMOVAL_TASKS.remove(entityUUID);
                if (target.isValid() && !target.isDead()) {
                    AttributeInstance attr = target.getAttribute(Attribute.SCALE);
                    if (attr != null) {
                        attr.getModifiers().stream()
                            .filter(m -> m.getKey().equals(modKey))
                            .forEach(attr::removeModifier);
                    }
                }
            }
        }.runTaskLater(plugin, durationTicks);
        REMOVAL_TASKS.put(entityUUID, removalTask);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {}

    private void spawnScaleFx(Location loc, boolean growing) {
        if (growing) {
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0),
                5, 0.3, 0.3, 0.3, 0);
            loc.getWorld().playSound(loc, Sound.ENTITY_PUFFER_FISH_BLOW_UP,
                SoundCategory.PLAYERS, 0.8f, 0.8f);
        } else {
            loc.getWorld().spawnParticle(Particle.COMPOSTER, loc.clone().add(0, 0.5, 0),
                10, 0.2, 0.2, 0.2, 0);
            loc.getWorld().playSound(loc, Sound.ENTITY_PUFFER_FISH_BLOW_OUT,
                SoundCategory.PLAYERS, 0.8f, 1.5f);
        }
    }

    @Override public NamespacedKey getId() { return id; }
    @Override public String getDisplayName() { return "スケール"; }
    @Override public String getDescription() { return "対象のサイズを変更する（当たり判定含む）"; }
    @Override public int getManaCost() { return config.getManaCost("scale"); }
    @Override public int getTier() { return config.getTier("scale"); }
}
