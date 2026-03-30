package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/**
 * 対象ブロックをリスト内の次のブロックに変更するEffect。
 * ブロック: ブロックが属するティアリストから次のブロックに変換する。
 *   - Amplifyでティアを上げる（上位のリストから選択）
 *   - BlockBreakEvent + BlockPlaceEventを発火して保護プラグイン互換。
 *   - AOE対応。
 * エンティティ: glyphs.ymlのentity_exchange_pairsで定義されたペアの種類を入れ替える。
 *   - 例: ホグリン⇔ゾグリン、村人⇔魔女 等
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
        if (target instanceof Player) return; // プレイヤーは対象外

        Map<EntityType, EntityType> exchangeMap = config.getEntityExchangeMap();
        EntityType targetType = target.getType();
        EntityType newType = exchangeMap.get(targetType);
        if (newType == null) return;

        Location loc = target.getLocation().clone();

        // 新エンティティを同じ位置にスポーン
        LivingEntity newEntity = (LivingEntity) loc.getWorld().spawnEntity(loc, newType);

        // === 状態引き継ぎ ===

        // HP比率を維持
        double hpRatio = target.getHealth() / target.getMaxHealth();
        newEntity.setHealth(Math.max(1, newEntity.getMaxHealth() * hpRatio));

        // カスタム名
        if (target.customName() != null) {
            newEntity.customName(target.customName());
            newEntity.setCustomNameVisible(target.isCustomNameVisible());
        }

        // 装備引き継ぎ
        if (target.getEquipment() != null && newEntity.getEquipment() != null) {
            var srcEquip = target.getEquipment();
            var dstEquip = newEntity.getEquipment();
            dstEquip.setHelmet(srcEquip.getHelmet());
            dstEquip.setChestplate(srcEquip.getChestplate());
            dstEquip.setLeggings(srcEquip.getLeggings());
            dstEquip.setBoots(srcEquip.getBoots());
            dstEquip.setItemInMainHand(srcEquip.getItemInMainHand());
            dstEquip.setItemInOffHand(srcEquip.getItemInOffHand());
            dstEquip.setHelmetDropChance(srcEquip.getHelmetDropChance());
            dstEquip.setChestplateDropChance(srcEquip.getChestplateDropChance());
            dstEquip.setLeggingsDropChance(srcEquip.getLeggingsDropChance());
            dstEquip.setBootsDropChance(srcEquip.getBootsDropChance());
        }

        // ポーション効果引き継ぎ
        for (var effect : target.getActivePotionEffects()) {
            newEntity.addPotionEffect(effect);
        }

        // ベビー状態引き継ぎ
        if (target instanceof org.bukkit.entity.Ageable oldAge
                && newEntity instanceof org.bukkit.entity.Ageable newAge) {
            if (!oldAge.isAdult()) newAge.setBaby();
        }

        // 炎上引き継ぎ
        if (target.getFireTicks() > 0) {
            newEntity.setFireTicks(target.getFireTicks());
        }

        // 凍結引き継ぎ
        if (target.getFreezeTicks() > 0) {
            newEntity.setFreezeTicks(target.getFreezeTicks());
        }

        // 元エンティティを除去
        target.remove();

        // エフェクト
        loc.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0),
            20, 0.3, 0.5, 0.3, 0.5);
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT,
            SoundCategory.PLAYERS, 1.0f, 1.0f);
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
    public String getDescription() { return "対象を等価交換する"; }

    @Override
    public int getManaCost() { return config.getManaCost("exchange"); }

    @Override
    public int getTier() { return config.getTier("exchange"); }
}
