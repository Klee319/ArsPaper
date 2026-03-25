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
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * ブロックを粉砕変換し、ドロップアイテムも粉砕するEffect。Ars Nouveau準拠。
 * ブロック: 粉砕マップに基づきブロックを変換する（石→砂利、砂利→砂など）。
 * アイテム: 着弾地点付近のドロップアイテムも粉砕マップで変換する。
 * エンティティ: NoOp（ダメージは砕波エフェクトに分離）。
 */
public class CrushEffect implements SpellEffect {

    /**
     * 粉砕マップをglyphs.ymlから取得する。
     */
    private Map<Material, Material> getCrushMap() {
        return config.getCrushMap();
    }

    private final NamespacedKey id;
    private final GlyphConfig config;

    public CrushEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "crush");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // エンティティダメージは砕波(crush_wave)に分離済み - NoOp
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        Player caster = context.getCaster();
        if (caster == null) return;

        int fortuneLevel = context.getFortuneLevel();

        // ブロック粉砕
        Material result = getCrushMap().get(block.getType());
        if (result != null) {
            crushBlock(block, result, caster, blockLocation, fortuneLevel);
        }

        // ドロップアイテム粉砕: 着弾地点1.5ブロック以内のアイテムエンティティを変換
        // AOE時の二重処理を防止: 変換済みアイテムはマップに存在しないのでスキップされる
        for (org.bukkit.entity.Entity e : blockLocation.getWorld().getNearbyEntities(
                blockLocation.clone().add(0.5, 0.5, 0.5), 1.5, 1.5, 1.5)) {
            if (e instanceof Item itemEntity) {
                crushItemEntity(itemEntity, fortuneLevel);
            }
        }
    }

    private void crushBlock(Block block, Material result, Player caster, Location blockLocation, int fortuneLevel) {
        // 花 → 染料の場合: ブロックを空気にしてアイテムをドロップ
        if (!result.isBlock()) {
            org.bukkit.event.block.BlockBreakEvent breakEvent =
                new org.bukkit.event.block.BlockBreakEvent(block, caster);
            Bukkit.getPluginManager().callEvent(breakEvent);
            if (breakEvent.isCancelled()) return;

            block.setType(Material.AIR);
            int amount = 1 + fortuneLevel; // 幸運1段あたり+1個
            block.getWorld().dropItemNaturally(
                blockLocation.clone().add(0.5, 0.5, 0.5), new ItemStack(result, amount));
            spawnCrushFx(blockLocation);
            return;
        }

        // 通常ブロック変換: BlockPlaceEventで保護確認
        BlockState previousState = block.getState();
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
            block,
            previousState,
            block.getRelative(BlockFace.DOWN),
            new ItemStack(result),
            caster,
            true,
            EquipmentSlot.HAND
        );
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) return;

        block.setType(result);
        spawnCrushFx(blockLocation);
    }

    /**
     * ドロップアイテムを粉砕マップに基づいて変換する。
     * 幸運: 変換先がアイテム（非ブロック）の場合のみ、幸運の数だけ追加ドロップ。
     */
    private void crushItemEntity(Item itemEntity, int fortuneLevel) {
        ItemStack stack = itemEntity.getItemStack();
        Material crushedResult = getCrushMap().get(stack.getType());
        if (crushedResult == null) return;

        int baseAmount = stack.getAmount();
        int bonus = (!crushedResult.isBlock() && fortuneLevel > 0) ? fortuneLevel * baseAmount : 0;
        itemEntity.setItemStack(new ItemStack(crushedResult, baseAmount + bonus));
        spawnCrushFx(itemEntity.getLocation());
    }

    private void spawnCrushFx(Location loc) {
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.BLOCK, loc.clone().add(0.5, 0.5, 0.5),
            20, 0.3, 0.3, 0.3, 0.1,
            Material.GRAVEL.createBlockData());
        loc.getWorld().playSound(loc,
            org.bukkit.Sound.BLOCK_GRAVEL_BREAK, org.bukkit.SoundCategory.PLAYERS, 0.8f, 0.9f);
    }

    @Override
    public AoeMode getAoeMode() { return AoeMode.HIT_FACE_INWARD; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "粉砕"; }

    @Override
    public String getDescription() { return "ブロックとアイテムを粉砕変換する"; }

    @Override
    public int getManaCost() { return config.getManaCost("crush"); }

    @Override
    public int getTier() { return config.getTier("crush"); }
}
