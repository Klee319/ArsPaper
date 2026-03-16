package com.arspaper.spell.form;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellForm;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 術者の前方に効果の壁を展開するForm。
 * 視線方向に対して垂直な壁を瞬時に生成し、壁上のブロック・エンティティにEffectチェーンを適用する。
 */
public class WallForm implements SpellForm {

    private static final int BASE_HALF_WIDTH = 3;
    private static final int WALL_HEIGHT = 3;
    private static final double BASE_DISTANCE = 5.0;
    private static final double ACCEL_DISTANCE_BONUS = 2.0;
    private static final double ENTITY_HIT_RADIUS = 1.0;

    private final JavaPlugin plugin;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public WallForm(JavaPlugin plugin, GlyphConfig config) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "wall");
        this.config = config;
    }

    @Override
    public void cast(Player caster, SpellContext context) {
        SpellFxUtil.playCastSound(caster);
        // applyFormAugments()はSpellCaster.cast()で既に呼び出し済み

        int halfWidth = Math.min(BASE_HALF_WIDTH + context.getSplitCount(), 8);
        double distance = BASE_DISTANCE + context.getAcceleration() * ACCEL_DISTANCE_BONUS;

        // 水平方向の視線ベクトル（Y=0に射影）
        Vector forward = caster.getLocation().getDirection().setY(0).normalize();
        // 壁は視線に対して垂直 → 右方向ベクトル
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize();

        // 壁の中心位置
        Location wallCenter = caster.getLocation().add(forward.clone().multiply(distance));

        // 壁上の全ブロック位置を列挙し、効果適用+パーティクル生成
        Set<UUID> hitEntities = new HashSet<>();

        for (int w = -halfWidth; w <= halfWidth; w++) {
            for (int h = 0; h < WALL_HEIGHT; h++) {
                Location blockLoc = wallCenter.clone()
                    .add(right.clone().multiply(w))
                    .add(0, h, 0);

                // ブロックに効果適用（AOE拡張はWall自体が担当するため無効化）
                SpellContext blockContext = context.copy();
                blockContext.resolveOnBlockNoAoe(blockLoc.getBlock().getLocation());

                // パーティクル
                blockLoc.getWorld().spawnParticle(Particle.CLOUD, blockLoc.clone().add(0.5, 0.5, 0.5),
                    2, 0.2, 0.2, 0.2, 0.02);

                // この位置付近のエンティティに効果適用（重複防止）
                for (LivingEntity entity : blockLoc.getWorld().getNearbyLivingEntities(blockLoc, ENTITY_HIT_RADIUS)) {
                    if (entity.equals(caster)) continue;
                    if (hitEntities.contains(entity.getUniqueId())) continue;

                    hitEntities.add(entity.getUniqueId());
                    SpellFxUtil.spawnImpactBurst(entity.getLocation());
                    SpellContext entityContext = context.copy();
                    entityContext.resolveOnEntityNoAoe(entity);
                }
            }
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "壁"; }

    @Override
    public String getDescription() { return "前方に効果の壁を展開する"; }

    @Override
    public int getManaCost() { return config.getManaCost("wall"); }

    @Override
    public int getTier() { return config.getTier("wall"); }
}
