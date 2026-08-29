package com.arspaper.spell.effect;

import com.arspaper.ArsPaper;
import com.arspaper.mana.ManaKeys;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 対象の動きを拘束する。強烈な鈍足・跳躍不能・採掘低下（wiki {@code snare}）。
 *
 * <p>1.8 由来の {@code JUMP_BOOST} amplifier 128 は、当時 signed byte で -128 になり
 * ジャンプを潰していた。1.21 では amplifier が int のままなので Jump Boost 129 になり、
 * {@code jump_strength} が加算されて空へ飛ばす。{@code SLOWNESS} 255 も同様に溢れる。
 *
 * <p>跳躍と移動は {@link AttributeModifier} の乗算 -1 で 0 にする。属性修飾子は NBT に
 * 残るので、解除は {@link ScaleEffect} と同じく PDC の終了時刻＋参加時の読み直し
 * （W-191）。ポーションは採掘低下と、見た目用の妥当な鈍足だけ。
 */
public class SnareEffect implements SpellEffect {

    private static final int DEFAULT_BASE_DURATION = 160;
    private static final int DEFAULT_DURATION_PER_LEVEL = 20;
    /** 見た目用。移動そのものは属性側で 0 にする。旧実装の極端な amplifier は 1.21 で溢れる。 */
    private static final int SLOWNESS_AMPLIFIER = 6;
    private static final int MINING_FATIGUE_AMPLIFIER = 3;

    /** 修飾子キーの接頭辞。付ける側と剥がす側で同じものを使う。 */
    static final String MODIFIER_PREFIX = "snare_";

    private static final Map<UUID, BukkitTask> REMOVAL_TASKS = new ConcurrentHashMap<>();

    public static void cleanupAll() {
        REMOVAL_TASKS.values().forEach(BukkitTask::cancel);
        REMOVAL_TASKS.clear();
        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            clear(player);
        }
    }

    public static void restoreAll() {
        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            restore(player);
        }
    }

    public static void restore(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        Long endTime = entity.getPersistentDataContainer()
                .get(ManaKeys.SPELL_SNARE_END, PersistentDataType.LONG);
        long remaining = remainingTicks(endTime, System.currentTimeMillis());
        if (remaining <= 0L) {
            clear(entity);
            return;
        }
        scheduleRemoval(entity.getUniqueId(), remaining);
    }

    static long remainingTicks(Long endTimeMillis, long nowMillis) {
        if (endTimeMillis == null) {
            return -1L;
        }
        long remainingMillis = endTimeMillis - nowMillis;
        if (remainingMillis <= 0L) {
            return 0L;
        }
        return Math.max(1L, remainingMillis / 50L);
    }

    public static void clear(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        entity.getPersistentDataContainer().remove(ManaKeys.SPELL_SNARE_END);
        removeModifiers(entity);
    }

    private static void removeModifiers(LivingEntity entity) {
        removePrefixed(entity, Attribute.JUMP_STRENGTH);
        removePrefixed(entity, Attribute.MOVEMENT_SPEED);
    }

    private static void removePrefixed(LivingEntity entity, Attribute attribute) {
        AttributeInstance attr = entity.getAttribute(attribute);
        if (attr == null) {
            return;
        }
        for (AttributeModifier modifier : java.util.List.copyOf(attr.getModifiers())) {
            if (modifier.getKey().getKey().startsWith(MODIFIER_PREFIX)) {
                attr.removeModifier(modifier);
            }
        }
    }

    private static void scheduleRemoval(UUID entityUuid, long delayTicks) {
        ArsPaper plugin = ArsPaper.getInstance();
        if (plugin == null) {
            return;
        }
        BukkitTask prevTask = REMOVAL_TASKS.remove(entityUuid);
        if (prevTask != null) {
            prevTask.cancel();
        }
        BukkitTask removalTask = new BukkitRunnable() {
            @Override
            public void run() {
                REMOVAL_TASKS.remove(entityUuid);
                org.bukkit.entity.Entity current = org.bukkit.Bukkit.getEntity(entityUuid);
                if (current instanceof LivingEntity living) {
                    clear(living);
                }
            }
        }.runTaskLater(plugin, delayTicks);
        REMOVAL_TASKS.put(entityUuid, removalTask);
    }

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public SnareEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "snare");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int baseDuration = (int) config.getParam("snare", "base-duration", DEFAULT_BASE_DURATION);
        int durationPerLevel = (int) config.getParam("snare", "duration-per-level", DEFAULT_DURATION_PER_LEVEL);
        int duration = baseDuration + context.getDurationLevel() * durationPerLevel;

        removeModifiers(target);
        zeroAttribute(target, Attribute.JUMP_STRENGTH, MODIFIER_PREFIX + "jump");
        zeroAttribute(target, Attribute.MOVEMENT_SPEED, MODIFIER_PREFIX + "move");

        target.getPersistentDataContainer().set(
                ManaKeys.SPELL_SNARE_END, PersistentDataType.LONG,
                System.currentTimeMillis() + duration * 50L);

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, SLOWNESS_AMPLIFIER));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration, MINING_FATIGUE_AMPLIFIER));

        SpellFxUtil.spawnSnareFx(target.getLocation());
        scheduleRemoval(target.getUniqueId(), duration);
    }

    private void zeroAttribute(LivingEntity target, Attribute attribute, String keyPath) {
        AttributeInstance attr = target.getAttribute(attribute);
        if (attr == null) {
            return;
        }
        NamespacedKey modKey = new NamespacedKey(plugin, keyPath);
        attr.addModifier(new AttributeModifier(
                modKey, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
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
