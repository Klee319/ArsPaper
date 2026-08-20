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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 対象ブロックや近くのドロップアイテムを精錬するEffect。Ars Nouveau準拠。
 * ブロック: ハードコードされた精錬マップに基づきブロックを変換する。
 * Sensitive: ドロップアイテムのみを対象にする。
 */
public class SmeltEffect implements SpellEffect {

    /** ブロック精錬マップ: 精錬前 → 精錬後 */
    private static final Map<Material, Material> SMELT_MAP = new HashMap<>();

    static {
        // 鉱石 → 素材（溶鉱炉出力に準拠）
        SMELT_MAP.put(Material.IRON_ORE,       Material.IRON_INGOT);
        SMELT_MAP.put(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT);
        SMELT_MAP.put(Material.GOLD_ORE,       Material.GOLD_INGOT);
        SMELT_MAP.put(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT);
        SMELT_MAP.put(Material.COPPER_ORE,     Material.COPPER_INGOT);
        SMELT_MAP.put(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT);
        SMELT_MAP.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
        // 原石ブロック → 精錬ブロック
        SMELT_MAP.put(Material.RAW_IRON_BLOCK,   Material.IRON_BLOCK);
        SMELT_MAP.put(Material.RAW_GOLD_BLOCK,   Material.GOLD_BLOCK);
        SMELT_MAP.put(Material.RAW_COPPER_BLOCK, Material.COPPER_BLOCK);
        // 原石 → インゴット（ドロップアイテム精錬用）
        SMELT_MAP.put(Material.RAW_IRON,   Material.IRON_INGOT);
        SMELT_MAP.put(Material.RAW_GOLD,   Material.GOLD_INGOT);
        SMELT_MAP.put(Material.RAW_COPPER, Material.COPPER_INGOT);
        // ブロック変換
        SMELT_MAP.put(Material.COBBLESTONE,    Material.STONE);
        SMELT_MAP.put(Material.COBBLED_DEEPSLATE, Material.DEEPSLATE);
        SMELT_MAP.put(Material.STONE,          Material.SMOOTH_STONE);
        SMELT_MAP.put(Material.SANDSTONE,      Material.SMOOTH_SANDSTONE);
        SMELT_MAP.put(Material.RED_SANDSTONE,  Material.SMOOTH_RED_SANDSTONE);
        SMELT_MAP.put(Material.QUARTZ_BLOCK,   Material.SMOOTH_QUARTZ);
        SMELT_MAP.put(Material.SAND,           Material.GLASS);
        SMELT_MAP.put(Material.RED_SAND,       Material.GLASS);
        SMELT_MAP.put(Material.CLAY,           Material.TERRACOTTA);
        SMELT_MAP.put(Material.NETHERRACK,     Material.NETHER_BRICK);
        SMELT_MAP.put(Material.BASALT,         Material.SMOOTH_BASALT);
        // ひび割れたブロック（かまど精錬に準拠）
        SMELT_MAP.put(Material.STONE_BRICKS,           Material.CRACKED_STONE_BRICKS);
        SMELT_MAP.put(Material.DEEPSLATE_BRICKS,       Material.CRACKED_DEEPSLATE_BRICKS);
        SMELT_MAP.put(Material.DEEPSLATE_TILES,        Material.CRACKED_DEEPSLATE_TILES);
        SMELT_MAP.put(Material.NETHER_BRICKS,          Material.CRACKED_NETHER_BRICKS);
        SMELT_MAP.put(Material.POLISHED_BLACKSTONE_BRICKS, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS);
        SMELT_MAP.put(Material.CACTUS,         Material.GREEN_DYE);
        // 食料（調理）
        SMELT_MAP.put(Material.BEEF,           Material.COOKED_BEEF);
        SMELT_MAP.put(Material.CHICKEN,        Material.COOKED_CHICKEN);
        SMELT_MAP.put(Material.PORKCHOP,       Material.COOKED_PORKCHOP);
        SMELT_MAP.put(Material.MUTTON,         Material.COOKED_MUTTON);
        SMELT_MAP.put(Material.RABBIT,         Material.COOKED_RABBIT);
        SMELT_MAP.put(Material.COD,            Material.COOKED_COD);
        SMELT_MAP.put(Material.SALMON,         Material.COOKED_SALMON);
        SMELT_MAP.put(Material.POTATO,         Material.BAKED_POTATO);
        SMELT_MAP.put(Material.KELP,           Material.DRIED_KELP);
        // 木材 → 木炭
        SMELT_MAP.put(Material.OAK_LOG,        Material.CHARCOAL);
        SMELT_MAP.put(Material.SPRUCE_LOG,     Material.CHARCOAL);
        SMELT_MAP.put(Material.BIRCH_LOG,      Material.CHARCOAL);
        SMELT_MAP.put(Material.JUNGLE_LOG,     Material.CHARCOAL);
        SMELT_MAP.put(Material.ACACIA_LOG,     Material.CHARCOAL);
        SMELT_MAP.put(Material.DARK_OAK_LOG,   Material.CHARCOAL);
        SMELT_MAP.put(Material.MANGROVE_LOG,   Material.CHARCOAL);
        SMELT_MAP.put(Material.CHERRY_LOG,     Material.CHARCOAL);
    }

    private final NamespacedKey id;
    private final GlyphConfig config;

    public SmeltEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "smelt");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // エンティティには直接作用しないが、周囲のドロップアイテムを精錬する
        smeltNearbyItems(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック精錬 + 近くのドロップアイテムも精錬
        smeltNearbyItems(blockLocation);

        Block block = blockLocation.getBlock();
        Material result = SMELT_MAP.get(block.getType());
        if (result == null) return;

        org.bukkit.entity.Player caster = context.getCaster();
        if (caster == null) return;

        // 結果がアイテムの場合（鉱石→インゴット等）はドロップとして出力
        if (!result.isBlock()) {
            // BlockBreakEventで保護確認してからブロックを空気に置換
            org.bukkit.event.block.BlockBreakEvent breakEvent =
                    SpellBreakMarker.callMarkedBreakEvent(block, caster);
            if (breakEvent.isCancelled()) return;

            block.setType(Material.AIR);
            block.getWorld().dropItemNaturally(
                blockLocation.clone().add(0.5, 0.5, 0.5), new ItemStack(result));
            spawnSmeltFx(blockLocation);
            return;
        }

        // 結果もブロックの場合はBlackPlaceEventで保護確認してからブロック変換
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
        spawnSmeltFx(blockLocation);
    }

    /**
     * 同一tick中に既に精錬したドロップアイテム。<b>範囲グリフ対応で必須になったガード</b>
     * (2026-08-19)。
     *
     * <p>{@link #applyToBlock} は範囲内の全ブロックについて1回ずつ呼ばれ、その都度
     * {@link #smeltNearbyItems} が半径2を掃くので、<b>同じアイテムが何十回も精錬対象になる</b>。
     * {@link #SMELT_MAP} には {@code COBBLESTONE → STONE → SMOOTH_STONE} という2段の連鎖があり、
     * ガードが無いと落ちている丸石が1回の詠唱で滑らかな石まで進んでしまう
     * (範囲が1ブロックだった頃は掃き取りも1回だけだったので表面化しなかった)。
     *
     * <p>スペル解決はメインスレッド同期なので単純なフィールドで足りる。tick が変われば
     * 別の詠唱／遅延グリフ経由の別フェーズなので捨てる。
     */
    private long itemSweepTick = Long.MIN_VALUE;
    private final java.util.Set<java.util.UUID> smeltedThisTick = new java.util.HashSet<>();

    /**
     * 指定位置の半径2ブロック以内にあるドロップアイテムを精錬する。
     * 1つのアイテムは1tickにつき1回しか精錬しない（{@link #smeltedThisTick}）。
     */
    private void smeltNearbyItems(Location center) {
        long tick = Bukkit.getCurrentTick();
        if (tick != itemSweepTick) {
            itemSweepTick = tick;
            smeltedThisTick.clear();
        }
        Collection<Item> items = center.getWorld().getNearbyEntitiesByType(
            Item.class, center, 2.0);
        for (Item item : items) {
            ItemStack stack = item.getItemStack();
            // 2026-08-20 W-172: カスタムアイテムは Material が一致しても絶対に焼かない
            // (差し替えなので CMD/PDC/表示名が丸ごと消える)。理由は
            // CustomItemConversionPolicy の javadoc。
            Material smelted = CustomItemConversionPolicy.resultFor(SMELT_MAP, stack);
            if (smelted == null) continue;
            if (!smeltedThisTick.add(item.getUniqueId())) continue;
            item.setItemStack(new ItemStack(smelted, stack.getAmount()));
            spawnSmeltFx(item.getLocation());
        }
    }

    private void spawnSmeltFx(Location loc) {
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.FLAME, loc.clone().add(0.5, 0.5, 0.5), 12, 0.2, 0.3, 0.2, 0.04);
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.SMOKE, loc.clone().add(0.5, 0.8, 0.5), 8, 0.2, 0.2, 0.2, 0.02);
        loc.getWorld().playSound(loc,
            org.bukkit.Sound.BLOCK_FURNACE_FIRE_CRACKLE, org.bukkit.SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    /**
     * 範囲グリフの展開はヒット面基準・法線は<b>奥へ</b>({@code BreakEffect} と同じ)。
     *
     * <p>既定の {@code FIXED} だと法線方向の符号が {@code +1}（＝手前＝設置系の向き）になるため、
     * 壁を狙って「範囲[法線]」を積むと<b>壁の中ではなく自分側の空気が対象になる</b>。
     * 精錬は破壊と同じく「狙った面から内側へ効く」操作なので INWARD が正しい。
     */
    @Override
    public AoeMode getAoeMode() { return AoeMode.HIT_FACE_INWARD; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "精錬"; }

    @Override
    public String getDescription() { return "対象を精錬する"; }

    @Override
    public int getManaCost() { return config.getManaCost("smelt"); }

    @Override
    public int getTier() { return config.getTier("smelt"); }
}
