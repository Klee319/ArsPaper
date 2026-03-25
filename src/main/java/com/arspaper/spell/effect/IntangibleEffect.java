package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ブロックを一時的に消失させるEffect。Ars NouveauのIntangibleに準拠。
 * エンティティ: NoOp
 * ブロック: 対象ブロックを一時的にAIRに置換し、一定時間後に復元する。
 *   Amplify: 影響ブロック数を増加（垂直方向のカラム）。
 *   保護プラグイン互換: BlockBreakEventを発火して許可を確認する。
 *   ベッドロック等の不壊ブロックは対象外。
 */
public class IntangibleEffect implements SpellEffect {

    private static final int BASE_DURATION = 60;           // 3秒
    private static final int DURATION_PER_LEVEL = 40;      // ExtendTimeごと +2秒
    private static final int MAX_COLUMN_HEIGHT = 8;
    private final JavaPlugin plugin;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public IntangibleEffect(JavaPlugin plugin, GlyphConfig config) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "intangible");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // エンティティ対象はNoOp
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Player caster = context.getCaster();
        if (caster == null) return;

        int baseDuration = (int) config.getParam("intangible", "base-duration", (double) BASE_DURATION);
        int durationPerLevel = (int) config.getParam("intangible", "duration-per-level", (double) DURATION_PER_LEVEL);
        int duration = Math.max(1, baseDuration + context.getDurationLevel() * durationPerLevel);

        // 3軸AOE: 幅(左右), 上下(高さ), 奥行き(視線方向=破壊と同じ向き)
        int width = context.getAoeLevel();
        int height = context.getAoeHeight();
        int depth = context.getAoeDepth();

        if (width == 0 && height == 0 && depth == 0) {
            // AOEなし: 単体ブロック
            makeIntangible(blockLocation, caster, duration);
            return;
        }

        // 視線方向から3軸座標系を構築（破壊グリフと同じロジック）
        org.bukkit.block.BlockFace face = context.getHitFace();
        if (face == null) face = org.bukkit.block.BlockFace.UP;

        int ny = Math.abs(face.getDirection().getBlockY());
        int nx = face.getDirection().getBlockX();
        int nz = face.getDirection().getBlockZ();

        if (ny > 0) {
            // 床/天井ヒット: 奥行き=視線の水平方向
            org.bukkit.util.Vector lookH = caster.getLocation().getDirection().setY(0);
            if (lookH.lengthSquared() < 0.01) lookH = new org.bukkit.util.Vector(0, 0, 1);
            boolean fwdZ = Math.abs(lookH.getZ()) >= Math.abs(lookH.getX());
            int fX = fwdZ ? 0 : (lookH.getX() > 0 ? 1 : -1);
            int fZ = fwdZ ? (lookH.getZ() > 0 ? 1 : -1) : 0;
            int rX = fwdZ ? 1 : 0;
            int rZ = fwdZ ? 0 : 1;
            // 奥行き: 視線方向（壁の中に向かって、設置と逆）
            int dY = -face.getDirection().getBlockY();

            for (int w = -width; w <= width; w++) {
                for (int h = -height; h <= height; h++) {
                    for (int d = 0; d <= depth; d++) {
                        int dx = rX * w + fX * h;
                        int dz = rZ * w + fZ * h;
                        int dy = dY * d;
                        makeIntangible(blockLocation.clone().add(dx, dy, dz), caster, duration);
                    }
                }
            }
        } else {
            // 壁ヒット: 幅=左右, 上下=Y, 奥行き=壁の中（法線の反対=視線の奥方向）
            boolean isXFace = Math.abs(nx) > 0;
            for (int w = -width; w <= width; w++) {
                for (int h = -height; h <= height; h++) {
                    for (int d = 0; d <= depth; d++) {
                        int ddx = isXFace ? -nx * d : w;
                        int ddy = h;
                        int ddz = isXFace ? w : -nz * d;
                        makeIntangible(blockLocation.clone().add(ddx, ddy, ddz), caster, duration);
                    }
                }
            }
        }
    }

    @Override
    public boolean handlesAoeInternally() { return true; }

    /**
     * 指定位置のブロックを一時的にAIRに置換し、一定時間後に復元する。
     */
    private void makeIntangible(Location location, Player caster, int durationTicks) {
        Block block = location.getBlock();
        Material type = block.getType();

        // AIR・液体・不壊ブロックは対象外
        if (type.isAir() || !type.isSolid() || type == Material.BEDROCK
            || type == Material.END_PORTAL_FRAME || type == Material.BARRIER
            || type == Material.COMMAND_BLOCK || type == Material.STRUCTURE_BLOCK) {
            return;
        }

        // 保護プラグイン互換: BlockBreakEventを発火して許可を確認
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, caster);
        Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) return;

        // ブロックデータを保存してAIRに置換
        BlockData savedData = block.getBlockData().clone();
        Location savedLocation = location.clone();

        block.setType(Material.AIR, false);
        spawnIntangibleFx(savedLocation);

        // 一定時間後に復元
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Block targetBlock = savedLocation.getBlock();
            // 復元先がまだAIRの場合のみ復元（プレイヤーが何か置いた場合は上書きしない）
            if (targetBlock.getType().isAir()) {
                targetBlock.setBlockData(savedData);
                spawnIntangibleFx(savedLocation);
            }
        }, durationTicks);
    }

    private void spawnIntangibleFx(Location loc) {
        loc.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0.5, 0.5, 0.5),
            20, 0.3, 0.3, 0.3, 0.5);
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc.clone().add(0.5, 0.5, 0.5),
            10, 0.3, 0.3, 0.3, 1.0);
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT,
            SoundCategory.PLAYERS, 0.6f, 1.5f);
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "無形化"; }

    @Override
    public String getDescription() { return "ブロックを一時的に消失させる"; }

    @Override
    public int getManaCost() { return config.getManaCost("intangible"); }

    @Override
    public int getTier() { return config.getTier("intangible"); }
}
