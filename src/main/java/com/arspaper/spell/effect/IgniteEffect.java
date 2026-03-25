package com.arspaper.spell.effect;

import com.arspaper.ArsPaper;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 対象を燃焼させるEffect。
 * カスタム火炎ダメージタスクにより、ダメージ量・間隔・持続を全て設定可能。
 * DamageType.ON_FIREを使用するため火炎耐性が有効。
 * 増幅でダメージ増加、延長で持続延長。
 *
 * params:
 *   base-fire-ticks: 基本炎上持続 (tick, デフォルト: 60=3秒)
 *   duration-bonus-ticks: 延長1段あたり加算 (tick, デフォルト: 20=1秒)
 *   base-damage: 1回あたりの火炎ダメージ (HP, デフォルト: 1.0)
 *   amplify-damage-bonus: 増幅1段あたりのダメージ加算 (HP, デフォルト: 0.5)
 *   damage-interval: ダメージ間隔 (tick, デフォルト: 20=1秒)
 */
public class IgniteEffect implements SpellEffect {

    private static final Map<UUID, BukkitTask> activeFireTasks = new ConcurrentHashMap<>();

    private static final int DEFAULT_BASE_FIRE_TICKS = 60;
    private static final int DEFAULT_DURATION_BONUS_TICKS = 20;
    private static final double DEFAULT_BASE_DAMAGE = 1.0;
    private static final double DEFAULT_AMPLIFY_DAMAGE_BONUS = 0.5;
    private static final int DEFAULT_DAMAGE_INTERVAL = 20;

    private final JavaPlugin plugin;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public IgniteEffect(JavaPlugin plugin, GlyphConfig config) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "ignite");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int baseFireTicks = (int) config.getParam("ignite", "base-fire-ticks", DEFAULT_BASE_FIRE_TICKS);
        int durationBonusTicks = (int) config.getParam("ignite", "duration-bonus-ticks", DEFAULT_DURATION_BONUS_TICKS);
        double baseDamage = config.getParam("ignite", "base-damage", DEFAULT_BASE_DAMAGE);
        double amplifyBonus = config.getParam("ignite", "amplify-damage-bonus", DEFAULT_AMPLIFY_DAMAGE_BONUS);
        int damageInterval = (int) config.getParam("ignite", "damage-interval", DEFAULT_DAMAGE_INTERVAL);

        int totalTicks = baseFireTicks + context.getDurationLevel() * durationBonusTicks;
        double damage = baseDamage + context.getAmplifyLevel() * amplifyBonus;

        // 視覚的な炎上エフェクト（バニラの炎表示のみ、ダメージはカスタムタスクで）
        target.setFireTicks(Math.max(1, totalTicks));

        // カスタム火炎ダメージタスク（DamageType.ON_FIREで火炎耐性が有効）
        final double finalDamage = Math.max(0.5, damage);
        final int interval = Math.max(1, damageInterval);

        // 既存の火炎タスクをキャンセル（スタック防止）
        BukkitTask existing = activeFireTasks.remove(target.getUniqueId());
        if (existing != null) existing.cancel();

        BukkitTask task = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                elapsed += interval;
                if (elapsed > totalTicks || !target.isValid() || target.isDead()
                        || target.getFireTicks() <= 0) {
                    activeFireTasks.remove(target.getUniqueId());
                    cancel();
                    return;
                }

                // 火炎ダメージ（火炎耐性で軽減/無効化される）
                DamageSource fireSource = DamageSource.builder(DamageType.ON_FIRE).build();
                target.damage(finalDamage, fireSource);
            }
        }.runTaskTimer(plugin, interval, interval);
        activeFireTasks.put(target.getUniqueId(), task);

        SpellFxUtil.spawnIgniteFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        Block above = block.getRelative(BlockFace.UP);
        if (above.isEmpty()) {
            org.bukkit.entity.Player caster = context.getCaster();
            if (caster == null) return;

            BlockPlaceEvent placeEvent = new BlockPlaceEvent(
                above, above.getState(), block,
                new ItemStack(Material.FLINT_AND_STEEL), caster, true, EquipmentSlot.HAND
            );
            Bukkit.getPluginManager().callEvent(placeEvent);
            if (placeEvent.isCancelled()) return;

            above.setType(Material.FIRE);
            SpellFxUtil.spawnIgniteFx(above.getLocation());
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "着火"; }

    @Override
    public String getDescription() { return "対象を炎上させる"; }

    @Override
    public int getManaCost() { return config.getManaCost("ignite"); }

    @Override
    public int getTier() { return config.getTier("ignite"); }

    /**
     * 全ての火炎タスクをキャンセルしてクリーンアップする。
     */
    public static void cleanupAll() {
        activeFireTasks.values().forEach(BukkitTask::cancel);
        activeFireTasks.clear();
    }
}
