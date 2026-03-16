package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 作物や植物の成長を促進するEffect。
 * Amplifyで成長ステージの増加量が上昇。
 */
public class GrowEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public GrowEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "grow");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // GrowはエンティティにはNoOp
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        if (!(block.getBlockData() instanceof Ageable ageable)) return;

        org.bukkit.entity.Player caster = context.getCaster();
        if (caster == null) return;

        // 保護プラグイン互換: BlockPlaceEventを発火して許可を確認
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
            block,
            block.getState(),
            block.getRelative(BlockFace.DOWN),
            new ItemStack(block.getType()),
            caster,
            true,
            EquipmentSlot.HAND
        );
        org.bukkit.Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) return;

        int growth = 1 + context.getAmplifyLevel();
        int newAge = Math.min(ageable.getAge() + growth, ageable.getMaximumAge());
        ageable.setAge(newAge);
        block.setBlockData(ageable);
        SpellFxUtil.spawnGrowFx(blockLocation);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "成長"; }

    @Override
    public String getDescription() { return "作物を成長させる"; }

    @Override
    public int getManaCost() { return config.getManaCost("grow"); }

    @Override
    public int getTier() { return config.getTier("grow"); }
}
