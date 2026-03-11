package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 作物や植物の成長を促進するEffect。
 * Amplifyで成長ステージの増加量が上昇。
 */
public class GrowEffect implements SpellEffect {

    private final NamespacedKey id;

    public GrowEffect(JavaPlugin plugin) {
        this.id = new NamespacedKey(plugin, "grow");
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // GrowはエンティティにはNoOp
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        if (!(block.getBlockData() instanceof Ageable ageable)) return;

        int growth = 1 + context.getAmplifyLevel();
        int newAge = Math.min(ageable.getAge() + growth, ageable.getMaximumAge());
        ageable.setAge(newAge);
        block.setBlockData(ageable);
        SpellFxUtil.spawnGrowFx(blockLocation);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "Grow"; }

    @Override
    public int getManaCost() { return 10; }

    @Override
    public int getTier() { return 1; }
}
