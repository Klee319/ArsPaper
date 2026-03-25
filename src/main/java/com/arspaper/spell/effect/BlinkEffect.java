package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * 対象をテレポートさせるEffect。
 * エンティティ: 向いている方向に最大8ブロック（+Amplify毎+4）テレポート。
 * 安全な着地点を下方スキャンして確保する。
 */
public class BlinkEffect implements SpellEffect {

    private static final int BASE_DISTANCE = 8;
    private static final int AMPLIFY_BONUS = 4;
    private static final int REACH_BONUS = 8;
    private static final int MAX_DISTANCE = 48;
    private static final int MIN_Y = -64;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public BlinkEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "blink");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        int baseDistance = (int) config.getParam("blink", "base-distance", (double) BASE_DISTANCE);
        int reachBonusPerLevel = (int) config.getParam("blink", "reach-bonus", (double) REACH_BONUS);
        int maxDistance = (int) config.getParam("blink", "max-distance", (double) MAX_DISTANCE);
        int distance = Math.min(baseDistance + context.getReachLevel() * reachBonusPerLevel, maxDistance);
        boolean canPenetrate = context.getReachLevel() > 0;
        Vector direction = target.getLocation().getDirection();

        Location destination;
        if (canPenetrate) {
            // 延伸付き: 透過ブロック（ガラス、フェンス等）を貫通してその先にテレポート
            destination = findPenetratingDestination(target, direction, distance);
        } else {
            RayTraceResult result = target.getWorld().rayTraceBlocks(
                target.getEyeLocation(), direction, distance
            );
            if (result != null && result.getHitBlock() != null) {
                destination = result.getHitPosition().toLocation(target.getWorld());
                destination.subtract(direction.clone().normalize().multiply(0.5));
            } else {
                destination = target.getLocation().add(direction.clone().multiply(distance));
            }
        }

        // 安全な着地点を探す: 下方スキャンで足場を見つける
        destination = findSafeLocation(destination);
        if (destination == null) {
            return;
        }

        destination.setYaw(target.getLocation().getYaw());
        destination.setPitch(target.getLocation().getPitch());

        Location origin = target.getLocation().clone();
        target.teleport(destination);
        SpellFxUtil.spawnBlinkFx(origin, destination);
    }

    /**
     * 透過ブロックを貫通してテレポート先を探す。
     * 視線方向に1ブロックずつ進み、不透過ソリッドブロックに当たったら
     * その先の空気スペースを探す。
     */
    private Location findPenetratingDestination(LivingEntity target, Vector direction, int maxDist) {
        Location start = target.getEyeLocation().clone();
        Vector step = direction.clone().normalize();
        Location lastOpen = target.getLocation().clone();

        for (int i = 1; i <= maxDist; i++) {
            Location check = start.clone().add(step.clone().multiply(i));
            Block block = check.getBlock();
            if (block.isPassable() || isTransparentSolid(block)) {
                lastOpen = check.clone().subtract(0, target.getEyeHeight() - 0.1, 0);
            }
        }
        return lastOpen;
    }

    /**
     * 透過可能なソリッドブロック（ガラス、フェンス、鉄柵等）かどうか。
     */
    private boolean isTransparentSolid(Block block) {
        Material type = block.getType();
        return type.name().contains("GLASS") || type.name().contains("FENCE")
            || type.name().contains("BARS") || type.name().contains("WALL")
            || type.name().contains("SLAB") || type.name().contains("STAIR")
            || type.name().contains("LEAVES") || type == Material.ICE
            || type == Material.COBWEB;
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象はNoOp
    }

    /**
     * 指定位置から下方にスキャンし、安全な着地点を返す。
     * 足元と頭が空気で、その下にブロックがある場所を探す。
     */
    private Location findSafeLocation(Location loc) {
        Location check = loc.clone();

        // まず現在位置が安全か確認
        if (isSafe(check)) return check;

        // 下方に最大10ブロックスキャン
        for (int dy = 1; dy <= 10; dy++) {
            check = loc.clone().subtract(0, dy, 0);
            if (check.getBlockY() < MIN_Y) break;
            if (isSafe(check)) return check;
        }

        // 上方に最大5ブロックスキャン
        for (int dy = 1; dy <= 5; dy++) {
            check = loc.clone().add(0, dy, 0);
            if (isSafe(check)) return check;
        }

        return null; // 安全な場所なし
    }

    private boolean isSafe(Location loc) {
        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        return feet.isPassable() && head.isPassable() && !ground.isPassable();
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "瞬間移動"; }

    @Override
    public String getDescription() { return "視線方向にテレポートする"; }

    @Override
    public int getManaCost() { return config.getManaCost("blink"); }

    @Override
    public int getTier() { return config.getTier("blink"); }
}
