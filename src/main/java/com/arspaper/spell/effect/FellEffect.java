package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 木を丸ごと伐採するEffect。
 * 対象ブロックからBFSで接続されたログ・葉ブロックを全て探索し破壊する。
 * 保護プラグイン互換: 各ブロックにBlockBreakEventを発火する。
 * aoeLevel に応じて最大スキャン数が増加する（基本50 + aoeLevel×50）。
 */
public class FellEffect implements SpellEffect {

    private static final int BASE_MAX_BLOCKS = 50;
    private static final int AOE_BONUS_BLOCKS = 50;

    private static final Set<Material> LOG_MATERIALS;
    private static final Set<Material> LEAF_MATERIALS;

    static {
        Set<Material> logs = new HashSet<>();
        Set<Material> leaves = new HashSet<>();
        for (Material mat : Material.values()) {
            String name = mat.name();
            if (name.endsWith("_LOG") || name.endsWith("_WOOD")
                || name.endsWith("_STEM") || name.endsWith("_HYPHAE")) {
                logs.add(mat);
            }
            if (name.endsWith("_LEAVES")) {
                leaves.add(mat);
            }
        }
        LOG_MATERIALS = java.util.Collections.unmodifiableSet(logs);
        LEAF_MATERIALS = java.util.Collections.unmodifiableSet(leaves);
    }

    private final NamespacedKey id;
    private final GlyphConfig config;

    public FellEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "fell");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // エンティティ対象はNoOp
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block origin = blockLocation.getBlock();
        if (!LOG_MATERIALS.contains(origin.getType())) return;

        Player caster = context.getCaster();
        if (caster == null) return;

        int maxBlocks = BASE_MAX_BLOCKS + context.getAoeRadiusLevel() * AOE_BONUS_BLOCKS;

        // BFSで接続された木のブロックを収集
        List<Block> toBreak = new ArrayList<>();
        Set<Location> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();

        queue.add(origin);
        visited.add(origin.getLocation());

        while (!queue.isEmpty() && toBreak.size() < maxBlocks) {
            Block current = queue.poll();
            toBreak.add(current);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block neighbor = current.getRelative(dx, dy, dz);
                        if (visited.contains(neighbor.getLocation())) continue;
                        visited.add(neighbor.getLocation());

                        Material mat = neighbor.getType();
                        if (LOG_MATERIALS.contains(mat) || LEAF_MATERIALS.contains(mat)) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }

        // 収集したブロックを破壊（イベント確認後）
        for (Block block : toBreak) {
            BlockBreakEvent breakEvent = new BlockBreakEvent(block, caster);
            Bukkit.getPluginManager().callEvent(breakEvent);
            if (!breakEvent.isCancelled()) {
                block.breakNaturally();
            }
        }

        // 伐採エフェクト
        blockLocation.getWorld().spawnParticle(
            org.bukkit.Particle.BLOCK,
            blockLocation.clone().add(0.5, 0.5, 0.5),
            20, 0.5, 0.5, 0.5, 0,
            origin.getType().createBlockData()
        );
        blockLocation.getWorld().playSound(blockLocation,
            org.bukkit.Sound.BLOCK_WOOD_BREAK,
            org.bukkit.SoundCategory.BLOCKS, 1.0f, 0.8f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "伐採"; }

    @Override
    public String getDescription() { return "木を丸ごと伐採する"; }

    @Override
    public int getManaCost() { return config.getManaCost("fell"); }

    @Override
    public int getTier() { return config.getTier("fell"); }
}
