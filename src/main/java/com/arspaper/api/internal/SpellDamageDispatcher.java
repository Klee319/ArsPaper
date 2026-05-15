package com.arspaper.api.internal;

import com.arspaper.api.ArsAPI;
import com.arspaper.api.event.ArsSpellDamageEvent;
import com.arspaper.spell.SpellComponent;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellRecipe;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * スペル由来ダメージのフックポイント。
 * 各ダメージEffect (Harm/Lightning/Wither/...) は LivingEntity.damage() を呼ぶ
 * 直前に dispatch() を呼ぶ。Marker付与とArsSpellDamageEventを統一的に処理する。
 *
 * 戻り値: イベント後の (cancelled, finalDamage) ペア。
 */
public final class SpellDamageDispatcher {

    public record Result(boolean cancelled, double damage) {}

    private SpellDamageDispatcher() {}

    /**
     * ダメージEffect発火直前のフック。
     *
     * @param context スペルコンテキスト
     * @param target 対象 (null可、ブロックターゲットでは呼ばない)
     * @param effectId 発火元Effect (例: "arspaper:harm")
     * @param baseDamage 計算済み基本ダメージ
     * @return (cancelled?, 最終ダメージ)
     */
    public static Result dispatch(SpellContext context, LivingEntity target,
                                   String effectId, double baseDamage) {
        if (target == null) return new Result(false, baseDamage);
        if (!ArsAPI.isInitialized()) return new Result(false, baseDamage);

        Player caster = context.getCaster();
        SpellRecipe recipe = context.getRecipe();
        List<String> glyphs = new ArrayList<>();
        if (recipe != null) {
            for (SpellComponent c : recipe.getComponents()) {
                glyphs.add(c.getId().toString());
            }
        }

        ArsSpellDamageEvent ev = new ArsSpellDamageEvent(target, caster, glyphs, effectId, baseDamage);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return new Result(true, 0.0);

        // Magic damage marker (5 tick TTL)
        if (caster != null) {
            MagicDamageMarker.attach(ArsAPI.plugin(), target, caster);
        }
        return new Result(false, ev.getDamage());
    }
}
