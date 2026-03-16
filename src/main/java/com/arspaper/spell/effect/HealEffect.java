package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 対象を回復するEffect。
 * Ars Nouveau準拠: heal = 3.0 + 3.0 * amplifyLevel
 * アンデッドに対してはマジックダメージを与える。
 * キャスターの食料レベルを2.5消費する。
 */
public class HealEffect implements SpellEffect {

    private static final double DEFAULT_BASE_HEAL = 3.0;
    private static final double DEFAULT_AMPLIFY_BONUS = 3.0;
    private static final double DEFAULT_FOOD_COST = 2.5;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public HealEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "heal");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        if (target.isDead()) return;

        double baseHeal = config.getParam("heal", "base-heal", DEFAULT_BASE_HEAL);
        double amplifyBonus = config.getParam("heal", "amplify-bonus", DEFAULT_AMPLIFY_BONUS);
        double amount = Math.max(1, baseHeal + context.getAmplifyLevel() * amplifyBonus);

        if (isUndead(target)) {
            // アンデッドにはマジックダメージ
            Player caster = context.getCaster();
            if (caster != null) {
                target.damage(amount, caster);
            } else {
                target.damage(amount);
            }
        } else {
            // 通常エンティティ・プレイヤーを回復
            double maxHealth = target.getMaxHealth();
            double currentHealth = target.getHealth();
            double healed = Math.min(amount, maxHealth - currentHealth);
            target.setHealth(Math.min(currentHealth + amount, maxHealth));

            // 回復結果をキャスターに通知
            Player caster = context.getCaster();
            if (caster != null) {
                String msg = healed > 0
                    ? String.format("§a%.1f HP 回復", healed)
                    : "§7体力が満タンです";
                caster.sendActionBar(net.kyori.adventure.text.Component.text(msg));
            }
        }

        // キャスターの食料を消費
        Player caster = context.getCaster();
        if (caster != null && caster.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
            int currentFood = caster.getFoodLevel();
            double foodCost = config.getParam("heal", "food-cost", DEFAULT_FOOD_COST);
            caster.setFoodLevel(Math.max(0, (int)(currentFood - foodCost)));
        }

        SpellFxUtil.spawnHealFx(target.getLocation());
    }

    /**
     * エンティティがアンデッドかどうかを判定する。
     * Paper 1.21+ではEntityCategory廃止のため、EntityTypeで直接判定。
     */
    private boolean isUndead(LivingEntity entity) {
        return entity.getType().getKey().getKey().matches(
            "zombie|skeleton|wither_skeleton|stray|husk|drowned|phantom|"
            + "zombified_piglin|zoglin|wither|skeleton_horse|zombie_horse|"
            + "zombie_villager|bogged"
        );
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // HealはブロックにはNoOp
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "回復"; }

    @Override
    public String getDescription() { return "対象の体力を回復する"; }

    @Override
    public int getManaCost() { return config.getManaCost("heal"); }

    @Override
    public int getTier() { return config.getTier("heal"); }
}
