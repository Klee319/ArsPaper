package com.arspaper.spell.effect;

import com.arspaper.block.BlockKeys;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 対象を上方に打ち上げるEffect。
 * Bounceと似ているが、より強力な打ち上げ。
 */
public class LaunchEffect implements SpellEffect {

    private static final int MAX_LAUNCH_BLOCKS = 64;
    private static final double BASE_VELOCITY = 1.5;
    private static final double AMPLIFY_BONUS = 0.8;
    private static final double MAX_VELOCITY = 5.0;
    private final JavaPlugin plugin;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public LaunchEffect(JavaPlugin plugin, GlyphConfig config) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "launch");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        double baseVelocity = config.getParam("launch", "base-velocity", BASE_VELOCITY);
        double amplifyBonus = config.getParam("launch", "amplify-bonus", AMPLIFY_BONUS);
        double maxVelocity = config.getParam("launch", "max-velocity", MAX_VELOCITY);
        double velocity = Math.min(baseVelocity + context.getAmplifyLevel() * amplifyBonus, maxVelocity);

        if (target instanceof Mob mob) {
            // モブのAIがvelocityを即上書きするため、AI無効化→1tick後にvelocity設定→復元
            mob.setAI(false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (mob.isValid() && !mob.isDead()) {
                    mob.setVelocity(new Vector(0, velocity, 0));
                    // 打ち上げ中にAIが干渉しないよう少し待ってから復元
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (mob.isValid() && !mob.isDead()) {
                            mob.setAI(true);
                        }
                    }, 15L);
                }
            }, 1L);
        } else {
            target.setVelocity(new Vector(0, velocity, 0));
        }

        SpellFxUtil.spawnBounceFx(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        double baseVelocityB = config.getParam("launch", "base-velocity", BASE_VELOCITY);
        double amplifyBonusB = config.getParam("launch", "amplify-bonus", AMPLIFY_BONUS);
        double maxVelocityB = config.getParam("launch", "max-velocity", MAX_VELOCITY);
        double velocity = Math.min(baseVelocityB + context.getAmplifyLevel() * amplifyBonusB, maxVelocityB);
        int radius = context.getAoeRadiusLevel();
        Player caster = context.getCaster();

        if (radius > 0) {
            // Bug fix: 全ブロックを収集してから一括変換する
            // 個別にAIR化すると、隣接ブロックが支えを失って自然落下・アイテム化する
            List<BlockLaunchData> targets = new ArrayList<>();
            int count = 0;
            outer:
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = radius; dy >= -radius; dy--) {
                        if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                        Location loc = blockLocation.clone().add(dx, dy, dz);
                        BlockLaunchData data = collectBlock(loc, caster);
                        if (data != null) {
                            targets.add(data);
                            if (++count >= MAX_LAUNCH_BLOCKS) break outer;
                        }
                    }
                }
            }
            // 全ブロックを一括AIR化してからFallingBlock生成（上から順に処理済み）
            for (BlockLaunchData data : targets) {
                data.block().setType(Material.AIR, false);
            }
            for (BlockLaunchData data : targets) {
                FallingBlock fallingBlock = data.spawnLoc().getWorld()
                        .spawnFallingBlock(data.spawnLoc(), data.blockData());
                fallingBlock.setVelocity(new Vector(0, velocity, 0));
                fallingBlock.setDropItem(true);
                fallingBlock.setHurtEntities(true);
                fallingBlock.setGravity(true);
                SpellFxUtil.spawnBounceFx(data.spawnLoc());
            }
        } else {
            launchBlock(blockLocation, caster, velocity);
        }
    }

    /** AOE用: ブロック情報を収集（変換可能な場合のみ返す） */
    private BlockLaunchData collectBlock(Location blockLocation, Player caster) {
        Block block = blockLocation.getBlock();
        if (shouldSkipBlock(block, caster)) return null;

        return new BlockLaunchData(
                block,
                block.getBlockData().clone(),
                block.getLocation().add(0.5, 0, 0.5)
        );
    }

    /** 単体ブロック打ち上げ（AOE無し時） */
    private void launchBlock(Location blockLocation, Player caster, double velocity) {
        Block block = blockLocation.getBlock();
        if (shouldSkipBlock(block, caster)) return;

        BlockData blockData = block.getBlockData().clone();
        Location spawnLoc = block.getLocation().add(0.5, 0, 0.5);

        block.setType(Material.AIR);
        FallingBlock fallingBlock = spawnLoc.getWorld().spawnFallingBlock(spawnLoc, blockData);
        fallingBlock.setVelocity(new Vector(0, velocity, 0));
        fallingBlock.setDropItem(true);
        fallingBlock.setHurtEntities(true);
        fallingBlock.setGravity(true);

        SpellFxUtil.spawnBounceFx(spawnLoc);
    }

    /**
     * ブロックをスキップすべきか判定する。
     * - 空気/岩盤/黒曜石
     * - TileState（カスタムブロックデータ保持）を持つブロック
     * - Container（チェスト等、インベントリ保持）を持つブロック
     * - ArsPaperカスタムブロック（PDCにcustom_block_idあり）
     * - 保護プラグインでキャンセルされたブロック
     */
    private boolean shouldSkipBlock(Block block, Player caster) {
        if (block.getType().isAir() || block.getType() == Material.BEDROCK
                || block.getType() == Material.OBSIDIAN) {
            return true;
        }

        // TileState（Container含む）を持つブロックはデータ消失を防ぐためスキップ
        BlockState state = block.getState();
        if (state instanceof TileState) return true;

        // 保護チェック
        if (caster != null) {
            BlockBreakEvent evt = new BlockBreakEvent(block, caster);
            Bukkit.getPluginManager().callEvent(evt);
            if (evt.isCancelled()) return true;
        }

        return false;
    }

    private record BlockLaunchData(Block block, BlockData blockData, Location spawnLoc) {}

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "打上"; }

    @Override
    public String getDescription() { return "対象を上方に強く打ち上げる"; }

    @Override
    public int getManaCost() { return config.getManaCost("launch"); }

    @Override
    public int getTier() { return config.getTier("launch"); }
}
