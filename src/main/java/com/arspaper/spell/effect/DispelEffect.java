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

import java.util.List;

/**
 * 対象のポーション効果を除去するEffect。
 * 通常: 有害効果のみ除去。
 * 増幅: 全ポーション効果を除去。
 * 減衰: 良い効果のみ除去（敵に使って有利な状態を剥がす用途）。
 */
public class DispelEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public DispelEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "dispel");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        List<PotionEffect> effects = List.copyOf(target.getActivePotionEffects());

        if (context.getAmplifyLevel() > 0) {
            // 増幅: 全ポーション効果を除去
            for (PotionEffect effect : effects) {
                target.removePotionEffect(effect.getType());
            }
        } else if (context.hasDampen()) {
            // 減衰: 良い効果のみ除去（敵のバフ剥がし）
            for (PotionEffect effect : effects) {
                if (!effect.getType().isInstant() && !isHarmful(effect)) {
                    target.removePotionEffect(effect.getType());
                }
            }
        } else {
            // 通常: 有害ポーション効果のみ除去
            for (PotionEffect effect : effects) {
                if (!effect.getType().isInstant() && isHarmful(effect)) {
                    target.removePotionEffect(effect.getType());
                }
            }
        }

        SpellFxUtil.spawnDispelFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    /**
     * ポーション効果が有害かどうかを判定する。
     * Paper/Bukkit APIのisHarmful()が存在しない場合の補助判定。
     */
    private boolean isHarmful(PotionEffect effect) {
        return switch (effect.getType().getKey().getKey()) {
            case "poison", "wither", "weakness", "slowness", "mining_fatigue",
                "nausea", "blindness", "hunger", "levitation", "unluck", "bad_omen" -> true;
            default -> false;
        };
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "解呪"; }

    @Override
    public String getDescription() { return "対象の悪いポーション効果を全て除去する"; }

    @Override
    public int getManaCost() { return config.getManaCost("dispel"); }

    @Override
    public int getTier() { return config.getTier("dispel"); }
}
