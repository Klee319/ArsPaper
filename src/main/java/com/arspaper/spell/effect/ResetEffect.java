package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * スペルチェーンをリセットするEffect。Ars NouveauのEffectResetに準拠。
 * 本家では後続Effectのターゲットを元のヒット対象に戻す役割を持つが、
 * 本実装ではスペルチェーン解決が単純なため、パーティクルによる
 * 視覚的フィードバックのみを提供するプレースホルダーとして実装する。
 */
public class ResetEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public ResetEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "reset");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        SpellFxUtil.spawnResetFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        SpellFxUtil.spawnResetFx(blockLocation);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "初期化"; }

    @Override
    public String getDescription() { return "スペルチェーンをリセットする"; }

    @Override
    public int getManaCost() { return config.getManaCost("reset"); }

    @Override
    public int getTier() { return config.getTier("reset"); }
}
