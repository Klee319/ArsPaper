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
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * 瞬間移動エフェクト。
 *
 * ■ 自己モード（視線先TP）:
 *   1. 視線先の最大射程位置からチェック開始
 *   2. TP可能な空間（足場+プレイヤー高さ分の空気）を探す
 *   3. 見つからなければ術者側に1ブロックずつ近づけて再試行
 *   4. 全て失敗でマナ消費なしのエラー
 *
 * ■ エンティティ対象モード:
 *   対象エンティティと術者の位置を入れ替え（互いの向きを維持）
 *
 * ■ 貫通(pierce)連携:
 *   - 形態に貫通が搭載されている場合、貫通先の位置から射程チェック
 *   - 貫通1つごとにブロック1つを貫通してTP可能かチェック
 *
 * ■ 延伸(extend_reach)連携:
 *   - 射程距離を延長
 */
public class BlinkEffect implements SpellEffect {

    private static final int BASE_DISTANCE = 12;
    private static final int REACH_BONUS = 8;
    private static final int MAX_DISTANCE = 64;
    private static final int DOWNWARD_SCAN = 3;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public BlinkEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "blink");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        Player caster = context.getCaster();
        if (caster == null) return;

        // エンティティ対象: 術者と対象の位置を入れ替え
        if (target != caster) {
            swapPositions(caster, target);
            return;
        }

        // 自己モード: 視線先にテレポート
        int baseDistance = (int) config.getParam("blink", "base-distance", (double) BASE_DISTANCE);
        int reachBonusPerLevel = (int) config.getParam("blink", "reach-bonus", (double) REACH_BONUS);
        int maxDistance = (int) config.getParam("blink", "max-distance", (double) MAX_DISTANCE);
        int distance = Math.min(baseDistance + context.getReachLevel() * reachBonusPerLevel, maxDistance);
        int pierceLevel = context.getPierceCount();

        Location origin = caster.getLocation().clone();
        Vector direction = caster.getLocation().getDirection().normalize();
        double playerHeight = caster.getHeight();

        // 射程距離から術者側に向かって安全な着地点を探す
        Location destination = findTeleportDestination(
            caster.getEyeLocation(), direction, distance, pierceLevel, playerHeight);

        if (destination == null) {
            // 全て失敗 → マナ消費なし
            caster.sendMessage(net.kyori.adventure.text.Component.text(
                "テレポート先が見つかりません", net.kyori.adventure.text.format.NamedTextColor.RED));
            context.setCancelled(true);
            return;
        }

        // 向きを維持してテレポート
        destination.setYaw(origin.getYaw());
        destination.setPitch(origin.getPitch());
        caster.teleport(destination);
        SpellFxUtil.spawnBlinkFx(origin, destination);
    }

    /**
     * 視線方向の最大射程から術者側に近づけながらTP可能な位置を探す。
     *
     * @param eyeLocation 視線の起点（目の位置）
     * @param direction   視線方向（正規化済み）
     * @param maxDist     最大射程（ブロック）
     * @param pierceLevel 貫通レベル（ブロック貫通数）
     * @param playerHeight プレイヤーの高さ
     * @return TP可能な足元位置、見つからなければnull
     */
    private Location findTeleportDestination(Location eyeLocation, Vector direction,
                                              int maxDist, int pierceLevel, double playerHeight) {
        // 最大射程から1ブロックずつ近づけてチェック
        for (int dist = maxDist; dist >= 1; dist--) {
            Location checkPoint = eyeLocation.clone().add(direction.clone().multiply(dist));

            // 貫通レベルに応じてブロック内もチェック
            Location safe = findSafeAtPoint(checkPoint, pierceLevel, playerHeight);
            if (safe != null) return safe;
        }
        return null;
    }

    /**
     * 指定位置でTP可能な空間を探す。
     * ブロック内にいる場合は貫通レベル分だけブロックを突き抜けて先を探す。
     */
    private Location findSafeAtPoint(Location point, int pierceLevel, double playerHeight) {
        // まずその位置自体をチェック
        Location safe = findFootholdNear(point, playerHeight);
        if (safe != null) return safe;

        // 貫通レベル分だけ先のブロックもチェック
        if (pierceLevel > 0) {
            for (int p = 1; p <= pierceLevel; p++) {
                Location penetrated = point.clone().add(0, 0, 0);
                // 上下にずらして貫通先を探す
                for (int dy = -p; dy <= p; dy++) {
                    Location shifted = point.clone().add(0, dy, 0);
                    safe = findFootholdNear(shifted, playerHeight);
                    if (safe != null) return safe;
                }
            }
        }
        return null;
    }

    /**
     * 指定位置の付近（下方DOWNWARD_SCANブロック）でTP可能な足場を探す。
     *
     * TP可能な空間の定義:
     * - 足元位置から下3ブロック以内に足場（非通過ブロック）がある
     * - 足場の上にプレイヤーの高さ分以上の通過可能な空間がある
     */
    private Location findFootholdNear(Location point, double playerHeight) {
        int requiredAirBlocks = (int) Math.ceil(playerHeight);

        // 現在位置から下方にスキャンして足場を探す
        int minY = point.getWorld().getMinHeight();
        int maxY = point.getWorld().getMaxHeight();
        for (int dy = 0; dy <= DOWNWARD_SCAN; dy++) {
            Location feetCandidate = point.clone().subtract(0, dy, 0);
            if (feetCandidate.getBlockY() <= minY || feetCandidate.getBlockY() >= maxY - 1) continue;
            Block ground = feetCandidate.clone().subtract(0, 1, 0).getBlock();

            // 足場がないならスキップ
            if (ground.isPassable()) continue;

            // 足場の上にプレイヤー分の空間があるかチェック
            boolean hasSpace = true;
            for (int h = 0; h < requiredAirBlocks; h++) {
                Block airCheck = feetCandidate.clone().add(0, h, 0).getBlock();
                if (!airCheck.isPassable()) {
                    hasSpace = false;
                    break;
                }
            }

            if (hasSpace) {
                // 足場の上面の中央に配置
                return new Location(
                    feetCandidate.getWorld(),
                    feetCandidate.getBlockX() + 0.5,
                    ground.getY() + 1.0,
                    feetCandidate.getBlockZ() + 0.5
                );
            }
        }
        return null;
    }

    /**
     * 2つのエンティティの位置を入れ替える（互いの向きを維持）。
     */
    private void swapPositions(Player caster, LivingEntity target) {
        Location casterLoc = caster.getLocation().clone();
        Location targetLoc = target.getLocation().clone();

        // 互いの向きを維持
        float casterYaw = casterLoc.getYaw();
        float casterPitch = casterLoc.getPitch();
        float targetYaw = targetLoc.getYaw();
        float targetPitch = targetLoc.getPitch();

        Location newCasterLoc = targetLoc.clone();
        newCasterLoc.setYaw(casterYaw);
        newCasterLoc.setPitch(casterPitch);

        Location newTargetLoc = casterLoc.clone();
        newTargetLoc.setYaw(targetYaw);
        newTargetLoc.setPitch(targetPitch);

        caster.teleport(newCasterLoc);
        target.teleport(newTargetLoc);

        SpellFxUtil.spawnBlinkFx(casterLoc, targetLoc);
        SpellFxUtil.spawnBlinkFx(targetLoc, casterLoc);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック対象: 自己モードとして処理
        Player caster = context.getCaster();
        if (caster != null) {
            applyToEntity(context, caster);
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "瞬間移動"; }

    @Override
    public String getDescription() { return "視線方向にテレポート。対象エンティティとは位置交換"; }

    @Override
    public int getManaCost() { return config.getManaCost("blink"); }

    @Override
    public int getTier() { return config.getTier("blink"); }
}
