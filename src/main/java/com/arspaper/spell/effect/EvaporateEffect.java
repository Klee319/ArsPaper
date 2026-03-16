package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 液体ブロック（水・溶岩）を除去するEffect。Ars NouveauのEffectEvaporateに準拠。
 * 保護プラグイン互換のためBlockBreakEventを発火して許可を確認する。
 * 水源・溶岩源・水流・溶岩流のいずれも除去可能。
 */
public class EvaporateEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public EvaporateEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "evaporate");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // エンティティ対象はNoOp
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        if (!isLiquid(block.getType())) return;

        org.bukkit.entity.Player caster = context.getCaster();
        if (caster == null) return;

        // 保護プラグイン互換: BlockBreakEventを発火して許可を確認
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, caster);
        org.bukkit.Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) return;

        SpellFxUtil.spawnEvaporateFx(blockLocation, block.getType());
        block.setType(Material.AIR);
    }

    private boolean isLiquid(Material material) {
        return material == Material.WATER
            || material == Material.LAVA;
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "蒸発"; }

    @Override
    public String getDescription() { return "液体ブロックを除去する"; }

    @Override
    public int getManaCost() { return config.getManaCost("evaporate"); }

    @Override
    public int getTier() { return config.getTier("evaporate"); }
}
