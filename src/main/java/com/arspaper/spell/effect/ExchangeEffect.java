package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 対象ブロックをリスト内の次のブロックに変更するEffect。
 * ブロック: ブロックが属するティアリストから次のブロックに変換する。
 *   - Amplifyでティアを上げる（上位のリストから選択）
 *   - BlockBreakEvent + BlockPlaceEventを発火して保護プラグイン互換。
 *   - AOE対応。
 * エンティティ: キャスターと対象エンティティの位置を入れ替える。
 */
public class ExchangeEffect implements SpellEffect {

    private final NamespacedKey id;
    private final GlyphConfig config;

    /**
     * ブロックのティアリストをglyphs.ymlから取得する。
     */
    private List<List<Material>> getBlockTiers() {
        return config.getExchangeTiers();
    }

    public ExchangeEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "exchange");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        Player caster = context.getCaster();
        if (caster == null) return;

        // PvP無効ワールドではプレイヤー対象を拒否
        if (target instanceof Player && !target.getWorld().getPVP()) return;

        // キャスターと対象エンティティの位置を入れ替える
        Location casterLoc = caster.getLocation().clone();
        Location targetLoc = target.getLocation().clone();

        Location casterDest = targetLoc.clone();
        casterDest.setYaw(casterLoc.getYaw());
        casterDest.setPitch(casterLoc.getPitch());

        Location targetDest = casterLoc.clone();
        targetDest.setYaw(targetLoc.getYaw());
        targetDest.setPitch(targetLoc.getPitch());

        target.teleport(targetDest);
        caster.teleport(casterDest);

        casterDest.getWorld().spawnParticle(
            org.bukkit.Particle.PORTAL, casterDest.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.5);
        targetDest.getWorld().spawnParticle(
            org.bukkit.Particle.PORTAL, targetDest.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.5);
        casterDest.getWorld().playSound(casterDest,
            org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, org.bukkit.SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Player caster = context.getCaster();
        if (caster == null) return;

        Block target = blockLocation.getBlock();
        if (target.getType().isAir()) return;

        Material currentType = target.getType();
        int amplify = Math.max(0, context.getAmplifyLevel());

        // ブロックが属するティアリストを検索
        Material nextType = findNextBlock(currentType, amplify);
        if (nextType == null || nextType == currentType) return;

        // 保護チェック: 破壊
        BlockBreakEvent breakEvent = new BlockBreakEvent(target, caster);
        Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) return;

        // 保護チェック: 設置
        BlockState previousState = target.getState();
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
            target, previousState, target.getRelative(BlockFace.DOWN),
            new ItemStack(nextType), caster, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) return;

        // ブロック変換（ドロップなし）
        target.setType(nextType);

        // エフェクト
        blockLocation.getWorld().spawnParticle(
            org.bukkit.Particle.PORTAL, blockLocation.clone().add(0.5, 1.0, 0.5), 15, 0.3, 0.4, 0.3, 0.4);
        blockLocation.getWorld().playSound(blockLocation,
            org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, org.bukkit.SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    /**
     * 現在のブロックタイプが属するティアリストから次のブロックを返す。
     * amplifyでティアを上げる（上位のリストから選択）。
     * リストに見つからない場合はnullを返す。
     */
    private Material findNextBlock(Material current, int amplify) {
        List<List<Material>> blockTiers = getBlockTiers();
        for (int tierIndex = 0; tierIndex < blockTiers.size(); tierIndex++) {
            List<Material> tier = blockTiers.get(tierIndex);
            int currentIndex = tier.indexOf(current);
            if (currentIndex >= 0) {
                // amplifyでティアアップ
                int targetTier = Math.min(tierIndex + amplify, blockTiers.size() - 1);
                List<Material> targetList = blockTiers.get(targetTier);

                if (targetTier == tierIndex) {
                    // 同一ティア内でサイクル
                    int nextIndex = (currentIndex + 1) % tier.size();
                    return tier.get(nextIndex);
                } else {
                    // 上位ティアの先頭を返す
                    return targetList.get(0);
                }
            }
        }
        return null;
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "交換"; }

    @Override
    public String getDescription() { return "ブロックを変換する"; }

    @Override
    public int getManaCost() { return config.getManaCost("exchange"); }

    @Override
    public int getTier() { return config.getTier("exchange"); }
}
