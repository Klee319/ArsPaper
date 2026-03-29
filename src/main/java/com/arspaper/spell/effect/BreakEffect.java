package com.arspaper.spell.effect;

import com.arspaper.block.BlockKeys;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 対象ブロックを破壊するEffect。
 * 保護プラグイン互換: BlockBreakEventを発火して許可を確認する。
 *
 * Augment連携:
 * - Dampen: ユーティリティモード（道を作る、樹皮を剥ぐ、さび落とし、さび止め落とし、苔落とし）
 * - Extract: シルクタッチドロップ
 * - Fortune: フォーチュンドロップ（Extractと排他）
 */
public class BreakEffect implements SpellEffect {

    // ============================================================
    // 樹皮剥ぎマップ
    // ============================================================
    private static final Map<Material, Material> STRIP_MAP;
    static {
        var map = new EnumMap<Material, Material>(Material.class);
        map.put(Material.OAK_LOG, Material.STRIPPED_OAK_LOG);
        map.put(Material.SPRUCE_LOG, Material.STRIPPED_SPRUCE_LOG);
        map.put(Material.BIRCH_LOG, Material.STRIPPED_BIRCH_LOG);
        map.put(Material.JUNGLE_LOG, Material.STRIPPED_JUNGLE_LOG);
        map.put(Material.ACACIA_LOG, Material.STRIPPED_ACACIA_LOG);
        map.put(Material.DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_LOG);
        map.put(Material.MANGROVE_LOG, Material.STRIPPED_MANGROVE_LOG);
        map.put(Material.CHERRY_LOG, Material.STRIPPED_CHERRY_LOG);
        map.put(Material.CRIMSON_STEM, Material.STRIPPED_CRIMSON_STEM);
        map.put(Material.WARPED_STEM, Material.STRIPPED_WARPED_STEM);
        map.put(Material.OAK_WOOD, Material.STRIPPED_OAK_WOOD);
        map.put(Material.SPRUCE_WOOD, Material.STRIPPED_SPRUCE_WOOD);
        map.put(Material.BIRCH_WOOD, Material.STRIPPED_BIRCH_WOOD);
        map.put(Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_WOOD);
        map.put(Material.ACACIA_WOOD, Material.STRIPPED_ACACIA_WOOD);
        map.put(Material.DARK_OAK_WOOD, Material.STRIPPED_DARK_OAK_WOOD);
        map.put(Material.MANGROVE_WOOD, Material.STRIPPED_MANGROVE_WOOD);
        map.put(Material.CHERRY_WOOD, Material.STRIPPED_CHERRY_WOOD);
        map.put(Material.CRIMSON_HYPHAE, Material.STRIPPED_CRIMSON_HYPHAE);
        map.put(Material.WARPED_HYPHAE, Material.STRIPPED_WARPED_HYPHAE);
        map.put(Material.BAMBOO_BLOCK, Material.STRIPPED_BAMBOO_BLOCK);
        map.put(Material.PALE_OAK_LOG, Material.STRIPPED_PALE_OAK_LOG);
        map.put(Material.PALE_OAK_WOOD, Material.STRIPPED_PALE_OAK_WOOD);
        STRIP_MAP = Collections.unmodifiableMap(map);
    }

    // ============================================================
    // 酸化段階マップ（さび落とし: 1段階戻す）
    // ============================================================
    private static final Map<Material, Material> DEOXIDIZE_MAP;
    static {
        var map = new EnumMap<Material, Material>(Material.class);
        // ブロック
        map.put(Material.OXIDIZED_COPPER, Material.WEATHERED_COPPER);
        map.put(Material.WEATHERED_COPPER, Material.EXPOSED_COPPER);
        map.put(Material.EXPOSED_COPPER, Material.COPPER_BLOCK);
        // 切り込み入り
        map.put(Material.OXIDIZED_CUT_COPPER, Material.WEATHERED_CUT_COPPER);
        map.put(Material.WEATHERED_CUT_COPPER, Material.EXPOSED_CUT_COPPER);
        map.put(Material.EXPOSED_CUT_COPPER, Material.CUT_COPPER);
        // 階段
        map.put(Material.OXIDIZED_CUT_COPPER_STAIRS, Material.WEATHERED_CUT_COPPER_STAIRS);
        map.put(Material.WEATHERED_CUT_COPPER_STAIRS, Material.EXPOSED_CUT_COPPER_STAIRS);
        map.put(Material.EXPOSED_CUT_COPPER_STAIRS, Material.CUT_COPPER_STAIRS);
        // ハーフブロック
        map.put(Material.OXIDIZED_CUT_COPPER_SLAB, Material.WEATHERED_CUT_COPPER_SLAB);
        map.put(Material.WEATHERED_CUT_COPPER_SLAB, Material.EXPOSED_CUT_COPPER_SLAB);
        map.put(Material.EXPOSED_CUT_COPPER_SLAB, Material.CUT_COPPER_SLAB);
        // 銅格子
        map.put(Material.OXIDIZED_COPPER_GRATE, Material.WEATHERED_COPPER_GRATE);
        map.put(Material.WEATHERED_COPPER_GRATE, Material.EXPOSED_COPPER_GRATE);
        map.put(Material.EXPOSED_COPPER_GRATE, Material.COPPER_GRATE);
        // 銅電球
        map.put(Material.OXIDIZED_COPPER_BULB, Material.WEATHERED_COPPER_BULB);
        map.put(Material.WEATHERED_COPPER_BULB, Material.EXPOSED_COPPER_BULB);
        map.put(Material.EXPOSED_COPPER_BULB, Material.COPPER_BULB);
        // 彫り銅
        map.put(Material.OXIDIZED_CHISELED_COPPER, Material.WEATHERED_CHISELED_COPPER);
        map.put(Material.WEATHERED_CHISELED_COPPER, Material.EXPOSED_CHISELED_COPPER);
        map.put(Material.EXPOSED_CHISELED_COPPER, Material.CHISELED_COPPER);
        // 銅トラップドア
        map.put(Material.OXIDIZED_COPPER_TRAPDOOR, Material.WEATHERED_COPPER_TRAPDOOR);
        map.put(Material.WEATHERED_COPPER_TRAPDOOR, Material.EXPOSED_COPPER_TRAPDOOR);
        map.put(Material.EXPOSED_COPPER_TRAPDOOR, Material.COPPER_TRAPDOOR);
        // 銅ドア
        map.put(Material.OXIDIZED_COPPER_DOOR, Material.WEATHERED_COPPER_DOOR);
        map.put(Material.WEATHERED_COPPER_DOOR, Material.EXPOSED_COPPER_DOOR);
        map.put(Material.EXPOSED_COPPER_DOOR, Material.COPPER_DOOR);
        DEOXIDIZE_MAP = Collections.unmodifiableMap(map);
    }

    // ============================================================
    // ワックス除去マップ（さび止め落とし）
    // ============================================================
    private static final Map<Material, Material> DEWAX_MAP;
    static {
        var map = new EnumMap<Material, Material>(Material.class);
        map.put(Material.WAXED_COPPER_BLOCK, Material.COPPER_BLOCK);
        map.put(Material.WAXED_EXPOSED_COPPER, Material.EXPOSED_COPPER);
        map.put(Material.WAXED_WEATHERED_COPPER, Material.WEATHERED_COPPER);
        map.put(Material.WAXED_OXIDIZED_COPPER, Material.OXIDIZED_COPPER);
        map.put(Material.WAXED_CUT_COPPER, Material.CUT_COPPER);
        map.put(Material.WAXED_EXPOSED_CUT_COPPER, Material.EXPOSED_CUT_COPPER);
        map.put(Material.WAXED_WEATHERED_CUT_COPPER, Material.WEATHERED_CUT_COPPER);
        map.put(Material.WAXED_OXIDIZED_CUT_COPPER, Material.OXIDIZED_CUT_COPPER);
        map.put(Material.WAXED_CUT_COPPER_STAIRS, Material.CUT_COPPER_STAIRS);
        map.put(Material.WAXED_EXPOSED_CUT_COPPER_STAIRS, Material.EXPOSED_CUT_COPPER_STAIRS);
        map.put(Material.WAXED_WEATHERED_CUT_COPPER_STAIRS, Material.WEATHERED_CUT_COPPER_STAIRS);
        map.put(Material.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Material.OXIDIZED_CUT_COPPER_STAIRS);
        map.put(Material.WAXED_CUT_COPPER_SLAB, Material.CUT_COPPER_SLAB);
        map.put(Material.WAXED_EXPOSED_CUT_COPPER_SLAB, Material.EXPOSED_CUT_COPPER_SLAB);
        map.put(Material.WAXED_WEATHERED_CUT_COPPER_SLAB, Material.WEATHERED_CUT_COPPER_SLAB);
        map.put(Material.WAXED_OXIDIZED_CUT_COPPER_SLAB, Material.OXIDIZED_CUT_COPPER_SLAB);
        map.put(Material.WAXED_COPPER_GRATE, Material.COPPER_GRATE);
        map.put(Material.WAXED_EXPOSED_COPPER_GRATE, Material.EXPOSED_COPPER_GRATE);
        map.put(Material.WAXED_WEATHERED_COPPER_GRATE, Material.WEATHERED_COPPER_GRATE);
        map.put(Material.WAXED_OXIDIZED_COPPER_GRATE, Material.OXIDIZED_COPPER_GRATE);
        map.put(Material.WAXED_COPPER_BULB, Material.COPPER_BULB);
        map.put(Material.WAXED_EXPOSED_COPPER_BULB, Material.EXPOSED_COPPER_BULB);
        map.put(Material.WAXED_WEATHERED_COPPER_BULB, Material.WEATHERED_COPPER_BULB);
        map.put(Material.WAXED_OXIDIZED_COPPER_BULB, Material.OXIDIZED_COPPER_BULB);
        map.put(Material.WAXED_CHISELED_COPPER, Material.CHISELED_COPPER);
        map.put(Material.WAXED_EXPOSED_CHISELED_COPPER, Material.EXPOSED_CHISELED_COPPER);
        map.put(Material.WAXED_WEATHERED_CHISELED_COPPER, Material.WEATHERED_CHISELED_COPPER);
        map.put(Material.WAXED_OXIDIZED_CHISELED_COPPER, Material.OXIDIZED_CHISELED_COPPER);
        map.put(Material.WAXED_COPPER_TRAPDOOR, Material.COPPER_TRAPDOOR);
        map.put(Material.WAXED_EXPOSED_COPPER_TRAPDOOR, Material.EXPOSED_COPPER_TRAPDOOR);
        map.put(Material.WAXED_WEATHERED_COPPER_TRAPDOOR, Material.WEATHERED_COPPER_TRAPDOOR);
        map.put(Material.WAXED_OXIDIZED_COPPER_TRAPDOOR, Material.OXIDIZED_COPPER_TRAPDOOR);
        map.put(Material.WAXED_COPPER_DOOR, Material.COPPER_DOOR);
        map.put(Material.WAXED_EXPOSED_COPPER_DOOR, Material.EXPOSED_COPPER_DOOR);
        map.put(Material.WAXED_WEATHERED_COPPER_DOOR, Material.WEATHERED_COPPER_DOOR);
        map.put(Material.WAXED_OXIDIZED_COPPER_DOOR, Material.OXIDIZED_COPPER_DOOR);
        DEWAX_MAP = Collections.unmodifiableMap(map);
    }

    // ============================================================
    // 苔落としマップ
    // ============================================================
    private static final Map<Material, Material> DEMOSS_MAP = Map.of(
        Material.MOSSY_COBBLESTONE, Material.COBBLESTONE,
        Material.MOSSY_STONE_BRICKS, Material.STONE_BRICKS,
        Material.MOSSY_COBBLESTONE_STAIRS, Material.COBBLESTONE_STAIRS,
        Material.MOSSY_STONE_BRICK_STAIRS, Material.STONE_BRICK_STAIRS,
        Material.MOSSY_COBBLESTONE_SLAB, Material.COBBLESTONE_SLAB,
        Material.MOSSY_STONE_BRICK_SLAB, Material.STONE_BRICK_SLAB,
        Material.MOSSY_COBBLESTONE_WALL, Material.COBBLESTONE_WALL,
        Material.MOSSY_STONE_BRICK_WALL, Material.STONE_BRICK_WALL
    );

    /** シャベル適正判定用の仮想ツール（掘削モード） */
    private static final ItemStack SHOVEL_CHECK = new ItemStack(Material.WOODEN_SHOVEL);

    private final NamespacedKey id;
    private final GlyphConfig config;

    public BreakEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "break");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // BreakはエンティティにはNoOp
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        if (block.getType().isAir()) return;

        Player caster = context.getCaster();
        if (caster == null) return;

        int dampenLevel = context.getDampenAccum();

        // Dampen 2+: ユーティリティモード（道/樹皮剥ぎ/さび落とし等）
        if (dampenLevel >= 2 && context.getAmplifyLevel() <= 0) {
            applyUtility(block, caster, blockLocation);
            return;
        }

        // Dampen 1: 掘削モード（シャベル適正ブロックのみ破壊）
        if (dampenLevel == 1) {
            if (!block.isPreferredTool(SHOVEL_CHECK)) return;
            breakBlock(block, caster, context, blockLocation);
            return;
        }

        // 通常破壊モード
        if (!isBreakable(block.getType())) return;

        float hardness = block.getType().getHardness();
        int amplify = context.getAmplifyLevel();
        double baseMaxHardness = config.getParam("break", "max-hardness", 25.0);
        double hardnessPerAmplify = config.getParam("break", "hardness-per-amplify", 25.0);
        double maxHardness = baseMaxHardness + amplify * hardnessPerAmplify;
        if (hardness > maxHardness) return;

        ItemStack tool = buildVirtualTool(context);
        if (hardness > 0 && !block.isPreferredTool(tool)) return;

        breakBlock(block, caster, context, blockLocation);
    }

    /**
     * ブロックを破壊してドロップする共通処理。
     * 通常破壊モードと掘削モードの両方から呼ばれる。
     */
    private void breakBlock(Block block, Player caster, SpellContext context, Location blockLocation) {
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, caster);
        Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) return;

        // 掘削モードはシャベル、通常モードはピッケル
        ItemStack tool = context.getDampenAccum() >= 1
            ? buildVirtualShovel(context) : buildVirtualTool(context);
        Material blockType = block.getType();
        SpellFxUtil.spawnBreakFx(blockLocation, blockType);

        // カスタムブロック: BlockBreakEventリスナーが既にドロップ・クリーンアップ済み
        if (block.getState() instanceof TileState ts
                && ts.getPersistentDataContainer().has(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING)) {
            block.setType(Material.AIR);
            return;
        }

        Location dropLoc = blockLocation.clone().add(0.5, 0.5, 0.5);
        BlockState state = block.getState();

        // コンテナブロック（チェスト、樽等）: 中身をドロップしてからブロック自体も破壊
        // シュルカーボックスはドロップアイテム自体に中身を保持するためスキップ
        if (!isShulkerBox(blockType) && state instanceof Container container) {
            // ルートテーブル（トレジャーチェスト等）が未生成の場合、先に中身を生成
            if (container instanceof org.bukkit.loot.Lootable lootable
                    && lootable.getLootTable() != null) {
                org.bukkit.loot.LootTable lootTable = lootable.getLootTable();
                long seed = lootable.getSeed();
                org.bukkit.loot.LootContext ctx = new org.bukkit.loot.LootContext.Builder(blockLocation)
                    .killer(caster)
                    .build();
                lootTable.fillInventory(container.getInventory(),
                    new java.util.Random(seed != 0 ? seed : System.nanoTime()), ctx);
                lootable.setLootTable(null);
                container.update();
            }

            for (ItemStack item : container.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    block.getWorld().dropItemNaturally(dropLoc, item);
                }
            }
            container.getInventory().clear();
            // ブロック自体も破壊してドロップ（バニラと同じ挙動）
            Collection<ItemStack> drops = block.getDrops(tool);
            block.setType(Material.AIR);
            for (ItemStack drop : drops) {
                block.getWorld().dropItemNaturally(dropLoc, drop);
            }
        }

        // 他プラグインのカスタムデータ(PDC)を持つTileStateブロック:
        // BlockStateMetaでデータを保持したままドロップ
        else if (state instanceof TileState tileState
                && !tileState.getPersistentDataContainer().getKeys().isEmpty()) {
            ItemStack drop = new ItemStack(blockType);
            if (drop.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta bsm) {
                bsm.setBlockState(block.getState());
                drop.setItemMeta(bsm);
            }
            block.setType(Material.AIR);
            block.getWorld().dropItemNaturally(dropLoc, drop);
        } else {
            // 通常ブロック: バニラドロップ
            Collection<ItemStack> drops = block.getDrops(tool);
            block.setType(Material.AIR);
            for (ItemStack drop : drops) {
                block.getWorld().dropItemNaturally(dropLoc, drop);
            }
        }
    }

    // ============================================================
    // ユーティリティモード（Dampen）
    // ============================================================

    /**
     * 減少モード: ブロックを破壊せず加工する。
     * 耕す、道を作る、樹皮を剥ぐ、さび落とし、さび止め落とし、苔落とし。
     */
    private void applyUtility(Block block, Player caster, Location blockLocation) {
        Material type = block.getType();

        // 道を作る: GRASS_BLOCK/DIRT/PODZOL/MYCELIUM/COARSE_DIRT → DIRT_PATH
        if (isPathable(type)) {
            Block above = block.getRelative(BlockFace.UP);
            if (above.getType().isAir()) {
                if (tryTransform(block, Material.DIRT_PATH, caster, blockLocation)) {
                    playUtilityFx(blockLocation, Sound.ITEM_SHOVEL_FLATTEN);
                }
            }
            return;
        }

        // 樹皮を剥ぐ
        Material stripped = STRIP_MAP.get(type);
        if (stripped != null) {
            if (tryTransform(block, stripped, caster, blockLocation)) {
                playUtilityFx(blockLocation, Sound.ITEM_AXE_STRIP);
            }
            return;
        }

        // さび止め落とし（ワックス除去 - さび落としより先に判定）
        Material dewaxed = DEWAX_MAP.get(type);
        if (dewaxed != null) {
            if (tryTransform(block, dewaxed, caster, blockLocation)) {
                playUtilityFx(blockLocation, Sound.ITEM_AXE_WAX_OFF);
            }
            return;
        }

        // さび落とし（酸化1段階戻す）
        Material deoxidized = DEOXIDIZE_MAP.get(type);
        if (deoxidized != null) {
            if (tryTransform(block, deoxidized, caster, blockLocation)) {
                playUtilityFx(blockLocation, Sound.ITEM_AXE_SCRAPE);
            }
            return;
        }

        // 苔落とし
        Material demossed = DEMOSS_MAP.get(type);
        if (demossed != null) {
            if (tryTransform(block, demossed, caster, blockLocation)) {
                playUtilityFx(blockLocation, Sound.ITEM_AXE_SCRAPE);
            }
        }
    }

    /**
     * 保護プラグイン互換でブロック変換を行う。
     * ブロック状態（階段の向き、ハーフブロックの上下、ドアの開閉等）を保持する。
     */
    private boolean tryTransform(Block block, Material result, Player caster, Location blockLocation) {
        BlockState previousState = block.getState();
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
            block, previousState,
            block.getRelative(BlockFace.DOWN),
            new ItemStack(result),
            caster, true, EquipmentSlot.HAND
        );
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) return false;

        // ブロック状態を保持して変換（階段向き、ハーフ上下、ドア開閉等）
        org.bukkit.block.data.BlockData oldData = block.getBlockData();
        block.setType(result);
        org.bukkit.block.data.BlockData newData = block.getBlockData();

        // 共通プロパティをコピー
        if (oldData instanceof org.bukkit.block.data.Directional oldDir
                && newData instanceof org.bukkit.block.data.Directional newDir) {
            newDir.setFacing(oldDir.getFacing());
        }
        if (oldData instanceof org.bukkit.block.data.Bisected oldBi
                && newData instanceof org.bukkit.block.data.Bisected newBi) {
            newBi.setHalf(oldBi.getHalf());
        }
        if (oldData instanceof org.bukkit.block.data.type.Stairs oldStairs
                && newData instanceof org.bukkit.block.data.type.Stairs newStairs) {
            newStairs.setShape(oldStairs.getShape());
        }
        if (oldData instanceof org.bukkit.block.data.type.Slab oldSlab
                && newData instanceof org.bukkit.block.data.type.Slab newSlab) {
            newSlab.setType(oldSlab.getType());
        }
        if (oldData instanceof org.bukkit.block.data.Waterlogged oldWl
                && newData instanceof org.bukkit.block.data.Waterlogged newWl) {
            newWl.setWaterlogged(oldWl.isWaterlogged());
        }
        if (oldData instanceof org.bukkit.block.data.Openable oldOpen
                && newData instanceof org.bukkit.block.data.Openable newOpen) {
            newOpen.setOpen(oldOpen.isOpen());
        }
        if (oldData instanceof org.bukkit.block.data.Powerable oldPow
                && newData instanceof org.bukkit.block.data.Powerable newPow) {
            newPow.setPowered(oldPow.isPowered());
        }

        block.setBlockData(newData);
        return true;
    }

    private void playUtilityFx(Location loc, Sound sound) {
        loc.getWorld().playSound(loc, sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
        loc.getWorld().spawnParticle(Particle.BLOCK, loc.clone().add(0.5, 1.0, 0.5),
            10, 0.3, 0.2, 0.3, 0.0, loc.getBlock().getType().createBlockData());
    }

    /** 道を作る対象 → DIRT_PATH */
    private boolean isPathable(Material type) {
        return type == Material.GRASS_BLOCK || type == Material.DIRT
            || type == Material.PODZOL || type == Material.MYCELIUM
            || type == Material.COARSE_DIRT;
    }

    // ============================================================
    // 通常破壊モード
    // ============================================================


    /**
     * Amplify/Extract/Fortune Augmentに基づく仮想ツールを構築する。
     * - Amplify: ツールティアを上昇（0=鉄, 1=ダイヤ, 2+=ネザライト）
     * - Extract (シルクタッチ): Fortune と排他で優先
     * - Fortune: ドロップ増加
     */
    private ItemStack buildVirtualTool(SpellContext context) {
        int amplify = context.getAmplifyLevel();
        Material toolMat;
        if (amplify >= 3) {
            toolMat = Material.NETHERITE_PICKAXE;
        } else if (amplify >= 2) {
            toolMat = Material.DIAMOND_PICKAXE;
        } else if (amplify >= 1) {
            toolMat = Material.IRON_PICKAXE;
        } else {
            toolMat = Material.STONE_PICKAXE;
        }
        ItemStack tool = new ItemStack(toolMat);
        if (context.getExtractCount() > 0) {
            tool.addEnchantment(Enchantment.SILK_TOUCH, 1);
        } else if (context.getFortuneLevel() > 0) {
            tool.addUnsafeEnchantment(Enchantment.FORTUNE, context.getFortuneLevel());
        }
        return tool;
    }

    /**
     * 掘削モード用のシャベル仮想ツールを構築する。
     * Extract/Fortune Augmentのみ適用（増幅はツールティアに影響しない）。
     */
    private ItemStack buildVirtualShovel(SpellContext context) {
        ItemStack tool = new ItemStack(Material.NETHERITE_SHOVEL);
        if (context.getExtractCount() > 0) {
            tool.addEnchantment(Enchantment.SILK_TOUCH, 1);
        } else if (context.getFortuneLevel() > 0) {
            tool.addUnsafeEnchantment(Enchantment.FORTUNE, context.getFortuneLevel());
        }
        return tool;
    }

    private static boolean isShulkerBox(Material type) {
        return type.name().endsWith("SHULKER_BOX");
    }

    private static boolean isBreakable(Material type) {
        if (type.getHardness() < 0) return false;
        return switch (type) {
            case COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK,
                 STRUCTURE_BLOCK, STRUCTURE_VOID, JIGSAW,
                 END_PORTAL, END_PORTAL_FRAME, END_GATEWAY,
                 NETHER_PORTAL, MOVING_PISTON, LIGHT -> false;
            default -> true;
        };
    }

    @Override
    public AoeMode getAoeMode() { return AoeMode.HIT_FACE_INWARD; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "破壊"; }

    @Override
    public String getDescription() { return "ブロックを破壊する"; }

    @Override
    public int getManaCost() { return config.getManaCost("break"); }

    @Override
    public int getTier() { return config.getTier("break"); }
}
