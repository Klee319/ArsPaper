package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 対象ブロックに対してプレイヤーの右クリック操作をシミュレートするEffect。
 * PlayerInteractEvent(RIGHT_CLICK_BLOCK)を発火させるため、
 * 他プラグインとの互換性を保ちつつ全ての右クリック対応ブロックが動作する。
 */
public class InteractEffect implements SpellEffect {

    private final JavaPlugin plugin;
    private final NamespacedKey id;
    private final GlyphConfig config;

    public InteractEffect(JavaPlugin plugin, GlyphConfig config) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "interact");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // エンティティの足元ブロックに右クリック
        applyToBlock(context, target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        if (block.getType().isAir()) return;

        Player caster = context.getCaster();
        if (caster == null) return;

        // PlayerInteractEvent(RIGHT_CLICK_BLOCK)を発火（保護プラグイン互換）
        // 素手として発火することで、スペルブック側のキャンセル処理を回避
        BlockFace face = context.getHitFace() != null ? context.getHitFace() : BlockFace.UP;
        PlayerInteractEvent interactEvent = new PlayerInteractEvent(
            caster, Action.RIGHT_CLICK_BLOCK, null,
            block, face, EquipmentSlot.HAND
        );
        Bukkit.getPluginManager().callEvent(interactEvent);

        // useInteractedBlockがDENYの場合のみ中止（保護プラグインによるブロック）
        if (interactEvent.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) return;

        // バニラの右クリック操作をシミュレート
        // interactBlock は内部的にブロックの interact を呼ぶ
        // Paper APIではブロック操作の直接シミュレートが限定的なため、
        // 主要ブロックは明示的に処理する
        simulateRightClick(caster, block);
    }

    /**
     * バニラの右クリック操作を再現する。
     * PlayerInteractEventで保護チェック済みの前提。
     */
    private void simulateRightClick(Player caster, Block block) {
        var data = block.getBlockData();

        // Openable: ドア・トラップドア・門
        if (data instanceof org.bukkit.block.data.Openable openable) {
            openable.setOpen(!openable.isOpen());
            block.setBlockData(openable);
            // ドアの上下連動
            if (data instanceof org.bukkit.block.data.type.Door door) {
                Block otherHalf = door.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM
                    ? block.getRelative(0, 1, 0) : block.getRelative(0, -1, 0);
                if (otherHalf.getType() == block.getType()
                        && otherHalf.getBlockData() instanceof org.bukkit.block.data.Openable other) {
                    other.setOpen(openable.isOpen());
                    otherHalf.setBlockData(other);
                }
            }
            return;
        }

        // Powerable: ボタン・レバー
        if (data instanceof org.bukkit.block.data.Powerable powerable) {
            if (block.getType().name().contains("BUTTON")) {
                // ボタン: 一時的にON
                powerable.setPowered(true);
                block.setBlockData(powerable);
                Location savedLoc = block.getLocation();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Block current = savedLoc.getBlock();
                    if (current.getBlockData() instanceof org.bukkit.block.data.Powerable p) {
                        p.setPowered(false);
                        current.setBlockData(p);
                    }
                }, 30L);
            } else {
                // レバー等: トグル
                powerable.setPowered(!powerable.isPowered());
                block.setBlockData(powerable);
            }
            return;
        }

        // コンテナ系: チェスト・ホッパー・かまど等
        if (block.getState() instanceof org.bukkit.block.Container container) {
            caster.openInventory(container.getInventory());
            return;
        }

        // 作業台・エンチャント台・金床等
        var type = block.getType();
        if (type == org.bukkit.Material.CRAFTING_TABLE) {
            caster.openWorkbench(block.getLocation(), true);
        } else if (type == org.bukkit.Material.ENCHANTING_TABLE) {
            caster.openEnchanting(block.getLocation(), true);
        } else if (type.name().contains("ANVIL")) {
            // 金床: 遠隔オープン（通常/欠け/大きく欠けた金床）
            caster.openInventory(org.bukkit.Bukkit.createInventory(caster,
                org.bukkit.event.inventory.InventoryType.ANVIL));
        } else if (type == org.bukkit.Material.ENDER_CHEST) {
            caster.openInventory(caster.getEnderChest());
        } else if (data instanceof org.bukkit.block.data.type.NoteBlock noteBlock) {
            block.getWorld().playSound(block.getLocation(),
                noteBlock.getInstrument().getSound(),
                org.bukkit.SoundCategory.RECORDS, 3.0f, noteBlock.getNote().getPitch());
        } else if (data instanceof org.bukkit.block.data.type.Repeater repeater) {
            int newDelay = (repeater.getDelay() % 4) + 1;
            repeater.setDelay(newDelay);
            block.setBlockData(repeater);
        } else if (data instanceof org.bukkit.block.data.type.Comparator comparator) {
            comparator.setMode(comparator.getMode() == org.bukkit.block.data.type.Comparator.Mode.COMPARE
                ? org.bukkit.block.data.type.Comparator.Mode.SUBTRACT
                : org.bukkit.block.data.type.Comparator.Mode.COMPARE);
            block.setBlockData(comparator);
        } else if (data instanceof org.bukkit.block.data.type.DaylightDetector daylight) {
            daylight.setInverted(!daylight.isInverted());
            block.setBlockData(daylight);
        }
    }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "操作"; }

    @Override
    public String getDescription() { return "遠隔で右クリック操作する"; }

    @Override
    public int getManaCost() { return config.getManaCost("interact"); }

    @Override
    public int getTier() { return config.getTier("interact"); }
}
