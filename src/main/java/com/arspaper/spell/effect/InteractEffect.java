package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 対象ブロック/エンティティに対してプレイヤーの右クリック操作をシミュレートするEffect。
 *
 * ブロック: PlayerInteractEvent(RIGHT_CLICK_BLOCK)を発火し、主要ブロックを明示処理。
 * エンティティ: 村人→取引、プレイヤー→右クリックシミュレート、動物→騎乗・餌やり等。
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
        Player caster = context.getCaster();
        if (caster == null) return;

        // 保護プラグイン互換: PlayerInteractEntityEventを発火
        PlayerInteractEntityEvent entityEvent = new PlayerInteractEntityEvent(
            caster, target, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(entityEvent);
        if (entityEvent.isCancelled()) return;

        simulateEntityInteract(caster, target);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        if (block.getType().isAir()) return;

        Player caster = context.getCaster();
        if (caster == null) return;

        // PlayerInteractEvent(RIGHT_CLICK_BLOCK)を発火（保護プラグイン互換）
        BlockFace face = context.getHitFace() != null ? context.getHitFace() : BlockFace.UP;
        PlayerInteractEvent interactEvent = new PlayerInteractEvent(
            caster, Action.RIGHT_CLICK_BLOCK, null,
            block, face, EquipmentSlot.HAND
        );
        Bukkit.getPluginManager().callEvent(interactEvent);

        if (interactEvent.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) return;

        simulateRightClick(caster, block);
    }

    /**
     * エンティティへの右クリック操作を再現する。
     */
    private void simulateEntityInteract(Player caster, LivingEntity target) {
        // 村人 → 取引画面を開く
        if (target instanceof Villager villager) {
            if (villager.getProfession() != Villager.Profession.NONE
                    && villager.getProfession() != Villager.Profession.NITWIT) {
                caster.openMerchant(villager, true);
            }
            return;
        }

        // 行商人 → 取引画面を開く
        if (target instanceof WanderingTrader trader) {
            caster.openMerchant(trader, true);
            return;
        }

        // プレイヤー → 対象プレイヤーが手持ちアイテムで右クリック操作をシミュレート
        if (target instanceof Player targetPlayer) {
            ItemStack heldItem = targetPlayer.getInventory().getItemInMainHand();
            Block footBlock = targetPlayer.getLocation().subtract(0, 1, 0).getBlock();
            if (!footBlock.getType().isAir()) {
                BlockFace face = BlockFace.UP;
                PlayerInteractEvent fakeEvent = new PlayerInteractEvent(
                    targetPlayer, Action.RIGHT_CLICK_BLOCK, heldItem,
                    footBlock, face, EquipmentSlot.HAND
                );
                Bukkit.getPluginManager().callEvent(fakeEvent);
                if (fakeEvent.useInteractedBlock() != org.bukkit.event.Event.Result.DENY) {
                    simulateRightClick(targetPlayer, footBlock);
                }
            }
            return;
        }

        // その他のエンティティ → 術者がそのエンティティの位置のブロックに右クリック操作
        Block targetBlock = target.getLocation().getBlock();
        if (!targetBlock.getType().isAir()) {
            simulateRightClick(caster, targetBlock);
        }
    }

    /**
     * バニラの右クリック操作を再現する。
     * PlayerInteractEventで保護チェック済みの前提。
     */
    private void simulateRightClick(Player caster, Block block) {
        var data = block.getBlockData();
        var type = block.getType();

        // === 収穫系ブロック ===

        // グロウベリー（洞窟つた）: 実がなっていれば収穫
        if (type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT) {
            if (data instanceof org.bukkit.block.data.type.CaveVinesPlant cvp && cvp.isBerries()) {
                cvp.setBerries(false);
                block.setBlockData(cvp);
                block.getWorld().dropItemNaturally(
                    block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.GLOW_BERRIES));
                block.getWorld().playSound(block.getLocation(),
                    Sound.BLOCK_CAVE_VINES_PICK_BERRIES, SoundCategory.BLOCKS, 1.0f, 1.0f);
                return;
            }
            // CaveVines (先端) もCaveVinesPlantとして扱えるか確認
            if (data instanceof org.bukkit.block.data.type.CaveVines cv && cv.isBerries()) {
                cv.setBerries(false);
                block.setBlockData(cv);
                block.getWorld().dropItemNaturally(
                    block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.GLOW_BERRIES));
                block.getWorld().playSound(block.getLocation(),
                    Sound.BLOCK_CAVE_VINES_PICK_BERRIES, SoundCategory.BLOCKS, 1.0f, 1.0f);
                return;
            }
        }

        // スイートベリー: 成熟していれば収穫（age 2-3）
        if (type == Material.SWEET_BERRY_BUSH && data instanceof Ageable ageable) {
            int age = ageable.getAge();
            if (age >= 2) {
                int dropCount = (age == 3) ? 2 + (int)(Math.random() * 2) : 1 + (int)(Math.random() * 2);
                block.getWorld().dropItemNaturally(
                    block.getLocation().add(0.5, 0.5, 0.5),
                    new ItemStack(Material.SWEET_BERRIES, dropCount));
                ageable.setAge(1);
                block.setBlockData(ageable);
                block.getWorld().playSound(block.getLocation(),
                    Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, SoundCategory.BLOCKS, 1.0f, 1.0f);
                return;
            }
        }

        // ケーキ: 一口食べる
        if (type == Material.CAKE && data instanceof org.bukkit.block.data.type.Cake cake) {
            int bites = cake.getBites();
            if (bites < cake.getMaximumBites()) {
                cake.setBites(bites + 1);
                block.setBlockData(cake);
                block.getWorld().playSound(block.getLocation(),
                    Sound.ENTITY_GENERIC_EAT, SoundCategory.BLOCKS, 0.5f, 1.0f);
            } else {
                block.setType(Material.AIR);
            }
            return;
        }

        // コンポスター: 堆肥レベル確認（8=収穫可能）
        if (type == Material.COMPOSTER && data instanceof org.bukkit.block.data.Levelled levelled) {
            if (levelled.getLevel() == levelled.getMaximumLevel()) {
                levelled.setLevel(0);
                block.setBlockData(levelled);
                block.getWorld().dropItemNaturally(
                    block.getLocation().add(0.5, 1.0, 0.5), new ItemStack(Material.BONE_MEAL));
                block.getWorld().playSound(block.getLocation(),
                    Sound.BLOCK_COMPOSTER_EMPTY, SoundCategory.BLOCKS, 1.0f, 1.0f);
                return;
            }
        }

        // === 開閉・トグル系ブロック ===

        // Openable: ドア・トラップドア・門
        if (data instanceof org.bukkit.block.data.Openable openable) {
            openable.setOpen(!openable.isOpen());
            block.setBlockData(openable);
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
                powerable.setPowered(!powerable.isPowered());
                block.setBlockData(powerable);
            }
            return;
        }

        // === コンテナ・GUI系ブロック ===

        if (block.getState() instanceof org.bukkit.block.Container container) {
            caster.openInventory(container.getInventory());
            return;
        }

        if (type == Material.CRAFTING_TABLE) {
            caster.openWorkbench(block.getLocation(), true);
        } else if (type == Material.ENCHANTING_TABLE) {
            caster.openEnchanting(block.getLocation(), true);
        } else if (type.name().contains("ANVIL")) {
            caster.openInventory(Bukkit.createInventory(caster,
                org.bukkit.event.inventory.InventoryType.ANVIL));
        } else if (type == Material.ENDER_CHEST) {
            caster.openInventory(caster.getEnderChest());
        } else if (data instanceof org.bukkit.block.data.type.NoteBlock noteBlock) {
            block.getWorld().playSound(block.getLocation(),
                noteBlock.getInstrument().getSound(),
                SoundCategory.RECORDS, 3.0f, noteBlock.getNote().getPitch());
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
