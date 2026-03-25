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
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.block.Container;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

/**
 * 採掘2 (Tier 3) - 幸運3を内蔵した強化版破壊Effect。
 * ダイヤモンドツール相当の採掘能力 + 幸運3のドロップ増加を標準搭載。
 * Fortune増強で内蔵3に加算可能（上限はglyphs.ymlで設定）。
 * Extract(シルクタッチ)付与時は幸運を上書き。AOEで範囲破壊可能。
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

        // カスタムブロック: BlockBreakEventリスナーが既にドロップ・クリーンアップ済み
        if (block.getState() instanceof org.bukkit.block.TileState ts
                && ts.getPersistentDataContainer().has(
                    com.arspaper.block.BlockKeys.CUSTOM_BLOCK_ID,
                    org.bukkit.persistence.PersistentDataType.STRING)) {
            block.setType(Material.AIR);
            return;
        }

        // ダイヤツール + 幸運(内蔵3 + fortune増強分)
        ItemStack tool = new ItemStack(Material.DIAMOND_PICKAXE);
        int fortuneLevel = 3 + context.getFortuneLevel();
        tool.addUnsafeEnchantment(Enchantment.FORTUNE, fortuneLevel);

        // Extract(シルクタッチ)が付いている場合はそちらを優先（幸運を上書き）
        if (context.getExtractCount() > 0) {
            tool = new ItemStack(Material.DIAMOND_PICKAXE);
            tool.addEnchantment(Enchantment.SILK_TOUCH, 1);
        }

        Location dropLoc = blockLocation.clone().add(0.5, 0.5, 0.5);
        BlockState state = block.getState();

        // コンテナブロック（チェスト、樽等）の中身をドロップ
        // シュルカーボックスはドロップアイテム自体に中身を保持するためスキップ
        if (!isShulkerBox(blockType) && state instanceof Container container) {
            for (ItemStack item : container.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    block.getWorld().dropItemNaturally(dropLoc, item);
                }
            }
            container.getInventory().clear();
        }

        // 他プラグインのカスタムデータ(PDC)を持つTileStateブロック:
        // BlockStateMetaでデータを保持したままドロップ
        else if (state instanceof TileState tileState
                && !tileState.getPersistentDataContainer().getKeys().isEmpty()) {
            ItemStack drop = new ItemStack(blockType);
            if (drop.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta bsm) {
                bsm.setBlockState(block.getState());
                drop.setItemMeta(bsm);
            }
            block.setType(Material.AIR);
            block.getWorld().dropItemNaturally(dropLoc, drop);
        } else {
            // 通常ブロック: バニラドロップ
            Collection<ItemStack> drops = block.getDrops(tool);
            block.setType(Material.AIR);
            for (ItemStack drop : drops) {
                block.getWorld().dropItemNaturally(dropLoc, drop);
            }
        }
    }

    private static boolean isShulkerBox(Material type) {
        return type.name().endsWith("SHULKER_BOX");
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
