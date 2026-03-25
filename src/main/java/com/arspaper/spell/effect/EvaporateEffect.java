package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 液体ブロック（水・溶岩）を除去するEffect。Ars NouveauのEffectEvaporateに準拠。
 * 保護プラグイン互換のためBlockBreakEventを発火して許可を確認する。
 * 水源・溶岩源・水流・溶岩流・Waterloggedブロックのいずれも対応。
 *
 * スペルのレイトレースはFluidCollisionMode.NEVERで液体を貫通するため、
 * 対象ブロックが液体でない場合は隣接ブロックの液体も検索する。
 */
public class EvaporateEffect implements SpellEffect {

    private static final BlockFace[] ADJACENT_FACES = {
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
        BlockFace.UP, BlockFace.DOWN
    };

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
        Player caster = context.getCaster();
        if (caster == null) return;

        if (isLiquid(block.getType())) {
            // 対象ブロックが液体 → 直接蒸発
            evaporateBlock(block, caster);
        } else if (isWaterlogged(block)) {
            // Waterloggedブロック → 水抜き
            removeWaterlogging(block, blockLocation, caster);
        } else {
            // 対象が固体 → ヒット面方向の液体のみ蒸発
            // （スペルが液体を貫通して背後のブロックに当たった場合）
            // AOEなしでは6方向全て蒸発すると過剰なため、ヒット面+上面のみ
            BlockFace hitFace = context.getHitFace();
            BlockFace[] checkFaces = hitFace != null
                ? new BlockFace[]{hitFace, BlockFace.UP}
                : new BlockFace[]{BlockFace.UP};
            for (BlockFace face : checkFaces) {
                Block adjacent = block.getRelative(face);
                if (isLiquid(adjacent.getType())) {
                    evaporateBlock(adjacent, caster);
                } else if (isWaterlogged(adjacent)) {
                    removeWaterlogging(adjacent, adjacent.getLocation(), caster);
                }
            }
        }
    }

    private void evaporateBlock(Block block, Player caster) {
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, caster);
        org.bukkit.Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) return;

        SpellFxUtil.spawnEvaporateFx(block.getLocation(), block.getType());
        block.setType(Material.AIR);
    }

    private void removeWaterlogging(Block block, Location loc, Player caster) {
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, caster);
        org.bukkit.Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) return;

        org.bukkit.block.data.Waterlogged data =
            (org.bukkit.block.data.Waterlogged) block.getBlockData();
        data.setWaterlogged(false);
        block.setBlockData(data);
        SpellFxUtil.spawnEvaporateFx(loc, Material.WATER);
    }

    private boolean isLiquid(Material material) {
        return material == Material.WATER
            || material == Material.LAVA;
    }

    private boolean isWaterlogged(Block block) {
        return block.getBlockData() instanceof org.bukkit.block.data.Waterlogged waterlogged
            && waterlogged.isWaterlogged();
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
