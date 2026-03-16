package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

/**
 * 採掘2 (Tier 3) - 幸運3と増幅3を内蔵した強化版破壊Effect。
 * ダイヤモンドツール相当の採掘能力 + 幸運3のドロップ増加を標準搭載。
 * 追加のFortuneやAmplifyはこれ以上効果を持たない（既に最大値を内蔵）。
 */
public class AdvancedBreakEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public AdvancedBreakEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "advanced_break");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // エンティティにはNoOp
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        if (block.getType().isAir()) return;
        if (!isBreakable(block.getType())) return;

        Player caster = context.getCaster();
        if (caster == null) return;

        // 保護プラグイン互換
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, caster);
        Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) return;

        Material blockType = block.getType();
        SpellFxUtil.spawnBreakFx(blockLocation, blockType);

        // ダイヤツール + 幸運3を内蔵
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        tool.addUnsafeEnchantment(Enchantment.FORTUNE, 3);

        // Extract(シルクタッチ)が付いている場合はそちらを優先
        if (context.getExtractCount() > 0) {
            tool = new ItemStack(Material.DIAMOND_PICKAXE);
            tool.addEnchantment(Enchantment.SILK_TOUCH, 1);
        }

        Collection<ItemStack> drops = block.getDrops(tool);
        block.setType(Material.AIR);

        Location dropLoc = blockLocation.clone().add(0.5, 0.5, 0.5);
        for (ItemStack drop : drops) {
            block.getWorld().dropItemNaturally(dropLoc, drop);
        }
    }

    private static boolean isBreakable(Material type) {
        if (type.getHardness() < 0) return false;
        return switch (type) {
            case COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK,
                 STRUCTURE_BLOCK, STRUCTURE_VOID, JIGSAW,
                 END_PORTAL, END_PORTAL_FRAME, END_GATEWAY,
                 NETHER_PORTAL, MOVING_PISTON, LIGHT -> false;
            default -> true;
        };
    }

    @Override
    public AoeMode getAoeMode() { return AoeMode.HIT_FACE_INWARD; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "採掘"; }

    @Override
    public String getDescription() { return "幸運3+ダイヤ相当で破壊"; }

    @Override
    public int getManaCost() { return config.getManaCost("advanced_break"); }

    @Override
    public int getTier() { return config.getTier("advanced_break"); }
}
