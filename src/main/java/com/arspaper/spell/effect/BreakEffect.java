package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 対象ブロックを破壊するEffect。
 * 保護プラグイン互換: BlockBreakEventを発火して許可を確認する。
 */
public class BreakEffect implements SpellEffect {

    private final NamespacedKey id;

    public BreakEffect(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "break");
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // BreakはエンティティにはNoOp
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        if (block.getType().isAir()) return;

        // 保護プラグイン互換: BlockBreakEventを発火してキャンセルされないか確認
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, context.getCaster());
        org.bukkit.Bukkit.getPluginManager().callEvent(breakEvent);

        if (!breakEvent.isCancelled()) {
            Material blockType = block.getType();
            SpellFxUtil.spawnBreakFx(blockLocation, blockType);
            block.breakNaturally();
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Break"; }

    @Override
    public int getManaCost() { return 10; }

    @Override
    public int getTier() { return 1; }
}
