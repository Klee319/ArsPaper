package com.arspaper.spell;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import java.util.List;

/**
 * スペル発動時の実行コンテキスト。
 * Formが生成し、Effect/Augmentがデータを読み書きする。
 *
 * Augmentは直後のEffectにのみ適用され、次のEffectの前にリセットされる。
 */
public class SpellContext {

    private final Player caster;
    private final SpellRecipe recipe;
    private final List<SpellComponent> components;

    // Augmentで変動するパラメータ（Effect適用後にリセット）
    private double damageMultiplier = 1.0;
    private double aoeRadius = 0.0;
    private int durationTicks = 0;
    private int amplifyLevel = 0;
    private int pierceCount = 0;
    private double projectileSpeedMultiplier = 1.0;
    private int splitCount = 0;

    public SpellContext(Player caster, SpellRecipe recipe) {
        this.caster = caster;
        this.recipe = recipe;
        this.components = recipe.getComponents();
    }

    public Player getCaster() {
        return caster;
    }

    public SpellRecipe getRecipe() {
        return recipe;
    }

    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public void setDamageMultiplier(double damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }

    public double getAoeRadius() {
        return aoeRadius;
    }

    public void setAoeRadius(double aoeRadius) {
        this.aoeRadius = aoeRadius;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public void setDurationTicks(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    public int getAmplifyLevel() {
        return amplifyLevel;
    }

    public void setAmplifyLevel(int amplifyLevel) {
        this.amplifyLevel = amplifyLevel;
    }

    public int getPierceCount() {
        return pierceCount;
    }

    public void setPierceCount(int pierceCount) {
        this.pierceCount = pierceCount;
    }

    public double getProjectileSpeedMultiplier() {
        return projectileSpeedMultiplier;
    }

    public void setProjectileSpeedMultiplier(double projectileSpeedMultiplier) {
        this.projectileSpeedMultiplier = projectileSpeedMultiplier;
    }

    public int getSplitCount() {
        return splitCount;
    }

    public void setSplitCount(int splitCount) {
        this.splitCount = splitCount;
    }

    /**
     * Formに影響するAugment（Accelerate, Split等）を先行適用する。
     * componentsリストの先頭（Form）直後から最初のEffectまでのAugmentをmodify。
     */
    public void applyFormAugments() {
        boolean pastForm = false;
        for (SpellComponent comp : components) {
            if (comp instanceof SpellForm) {
                pastForm = true;
                continue;
            }
            if (!pastForm) continue;
            if (comp instanceof SpellEffect) break;
            if (comp instanceof SpellAugment augment) {
                augment.modify(this);
            }
        }
    }

    /**
     * ヒット対象にEffectチェーンを実行する（エンティティ対象）。
     * Augmentは直後のEffectにのみ適用し、その後リセットする。
     */
    public void resolveOnEntity(LivingEntity target) {
        for (SpellComponent comp : components) {
            if (comp instanceof SpellAugment augment) {
                augment.modify(this);
            } else if (comp instanceof SpellEffect effect) {
                effect.applyToEntity(this, target);
                // AOEがあれば周辺にも適用（フレンド/フォー判定付き）
                if (aoeRadius > 0) {
                    target.getLocation().getNearbyLivingEntities(aoeRadius).stream()
                        .filter(e -> !e.equals(target) && !e.equals(caster))
                        .filter(this::isValidAoeTarget)
                        .forEach(e -> effect.applyToEntity(this, e));
                }
                // Effect適用後にAugmentパラメータをリセット
                resetAugmentState();
            }
        }
    }

    /**
     * ヒット対象にEffectチェーンを実行する（ブロック対象）。
     */
    public void resolveOnBlock(Location blockLocation) {
        for (SpellComponent comp : components) {
            if (comp instanceof SpellAugment augment) {
                augment.modify(this);
            } else if (comp instanceof SpellEffect effect) {
                effect.applyToBlock(this, blockLocation);
                resetAugmentState();
            }
        }
    }

    /**
     * AOE対象のフィルタリング。
     * 術者のペット、PvP無効時の他プレイヤーを除外。
     */
    private boolean isValidAoeTarget(LivingEntity entity) {
        // 術者が飼い主のペットを除外
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            if (caster.equals(tameable.getOwner())) return false;
        }
        // PvP無効時は他プレイヤーを除外
        if (entity instanceof Player targetPlayer) {
            if (!caster.getWorld().getPVP()) return false;
        }
        return true;
    }

    /**
     * Augmentパラメータをデフォルトにリセット。
     */
    private void resetAugmentState() {
        damageMultiplier = 1.0;
        aoeRadius = 0.0;
        durationTicks = 0;
        amplifyLevel = 0;
        pierceCount = 0;
    }
}
