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
 * 対象ブロックを破壊するEffect。
 * 保護プラグイン互換: BlockBreakEventを発火して許可を確認する。
 *
 * Augment連携:
 * - Amplify: ツールティア（破壊可能な硬度上限を決定）
 * - Dampen (負のAmplify): 即時破壊ブロックのみ (hardness == 0)
 * - Extract: シルクタッチドロップ
 * - Fortune: フォーチュンドロップ（Extractと排他）
 */
public class BreakEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    public BreakEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "break");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // BreakはエンティティにはNoOp
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        if (block.getType().isAir()) return;

        // 破壊不可ブロックを除外
        if (!isBreakable(block.getType())) return;

        // Amplifyベースのツールティア判定
        int amplify = context.getAmplifyLevel();
        float hardness = block.getType().getHardness();

        if (amplify < 0) {
            // Dampen: 即時破壊ブロックのみ (松明、花、草等)
            if (hardness > 0) return;
        } else {
            double maxHardness = getMaxHardness(amplify);
            if (hardness > maxHardness) return;
        }

        Player caster = context.getCaster();
        if (caster == null) return;

        // 保護プラグイン互換: BlockBreakEventを発火してキャンセルされないか確認
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, caster);
        Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) return;

        Material blockType = block.getType();
        SpellFxUtil.spawnBreakFx(blockLocation, blockType);

        // 仮想ツールを構築してドロップを計算
        ItemStack tool = buildVirtualTool(context);
        Collection<ItemStack> drops = block.getDrops(tool);

        block.setType(Material.AIR);

        // アイテムドロップ
        Location dropLoc = blockLocation.clone().add(0.5, 0.5, 0.5);
        for (ItemStack drop : drops) {
            block.getWorld().dropItemNaturally(dropLoc, drop);
        }
    }

    /**
     * Amplifyレベルに基づく最大破壊硬度を返す。
     * 0: 木ツール相当 (hardness <= 2.0)
     * 1: 石ツール相当 (hardness <= 5.0)
     * 2: 鉄ツール相当 (hardness <= 25.0)
     * 3+: ダイヤツール相当 (全破壊可能ブロック)
     */
    private double getMaxHardness(int amplifyLevel) {
        return switch (amplifyLevel) {
            case 0 -> config.getParam("break", "hardness-tier-0", 2.0);
            case 1 -> config.getParam("break", "hardness-tier-1", 5.0);
            case 2 -> config.getParam("break", "hardness-tier-2", 25.0);
            default -> Float.MAX_VALUE;
        };
    }

    /**
     * Extract/Fortune Augmentに基づく仮想ツールを構築する。
     * Extract (シルクタッチ) はFortune と排他で優先される。
     */
    private ItemStack buildVirtualTool(SpellContext context) {
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        if (context.getExtractCount() > 0) {
            tool.addEnchantment(Enchantment.SILK_TOUCH, 1);
        } else if (context.getFortuneLevel() > 0) {
            tool.addUnsafeEnchantment(Enchantment.FORTUNE, context.getFortuneLevel());
        }
        return tool;
    }

    /**
     * ブロックが破壊可能かどうかを判定する。
     * Bedrock、Barrier、構造物ブロック等の破壊不可ブロックを除外。
     */
    private static boolean isBreakable(Material type) {
        // hardness < 0 のブロックはバニラで破壊不可（Bedrock, Barrier等）
        if (type.getHardness() < 0) return false;
        // 追加の安全チェック
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
    public String getDisplayName() { return "破壊"; }

    @Override
    public String getDescription() { return "ブロックを破壊する"; }

    @Override
    public int getManaCost() { return config.getManaCost("break"); }

    @Override
    public int getTier() { return config.getTier("break"); }
}
