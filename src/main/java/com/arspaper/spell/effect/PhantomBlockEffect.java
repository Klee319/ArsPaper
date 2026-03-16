package com.arspaper.spell.effect;

import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.SpellFxUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一時的なガラスブロックを設置するEffect。Ars NouveauのEffectPhantomBlockに準拠。
 * Amplify付き: 永続ブロックとして設置。
 * 通常: 10秒 + durationLevel × 5秒後に自動除去。
 * 空気ブロックにのみ設置可能。保護プラグイン互換のためBlockPlaceEventを発火する。
 */
public class PhantomBlockEffect implements SpellEffect {

    private static final int BASE_REMOVAL_TICKS = 200; // 10秒
    private static final int DURATION_BONUS_TICKS = 100; // 5秒
    private final JavaPlugin plugin;
    private final NamespacedKey id;
    private final GlyphConfig config;

    /** 一時的な仮想ブロックの位置を追跡。シャットダウン時にクリーンアップ。 */
    private static final Set<Location> activePhantomBlocks = ConcurrentHashMap.newKeySet();

    /** 永続化された仮想ブロックの位置を追跡（ドロップ防止用）。 */
    private static final Set<Location> permanentPhantomBlocks = ConcurrentHashMap.newKeySet();

    public PhantomBlockEffect(JavaPlugin plugin, GlyphConfig config) {
        this.plugin = plugin;
        this.id = new NamespacedKey(plugin, "phantom_block");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // エンティティの足元位置に仮想ブロックを設置
        applyToBlock(context, target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        Block block = blockLocation.getBlock();
        if (!block.getType().isAir()) return;

        org.bukkit.entity.Player caster = context.getCaster();
        if (caster == null) return;

        // 保護プラグイン互換: BlockPlaceEventを発火して許可を確認
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
            block,
            block.getState(),
            block.getRelative(BlockFace.DOWN),
            new ItemStack(Material.GLASS),
            caster,
            true,
            EquipmentSlot.HAND
        );
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) return;

        block.setType(Material.GLASS);
        SpellFxUtil.spawnPhantomBlockFx(blockLocation);

        Location savedLocation = blockLocation.getBlock().getLocation(); // 正規化

        if (context.getAmplifyLevel() <= 0) {
            // 一時ブロック: 一定時間後に除去
            int removalTicks = BASE_REMOVAL_TICKS + context.getDurationLevel() * DURATION_BONUS_TICKS;
            activePhantomBlocks.add(savedLocation);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                removePhantomBlock(savedLocation);
            }, removalTicks);
        } else {
            // 永続ブロック: ドロップ防止のため追跡（シルクタッチ対策）
            permanentPhantomBlocks.add(savedLocation);
        }
    }

    /**
     * 仮想ブロックを除去する。ブロックタイプに依存せず位置ベースで追跡。
     */
    private static void removePhantomBlock(Location location) {
        if (!activePhantomBlocks.remove(location)) return;
        Block block = location.getBlock();
        if (block.getType() == Material.GLASS) {
            block.setType(Material.AIR);
            SpellFxUtil.spawnPhantomBlockFx(location);
        }
    }

    /**
     * 指定位置が一時的な仮想ブロックかどうかを判定する。
     */
    public static boolean isPhantomBlock(Location location) {
        Location normalized = location.getBlock().getLocation();
        return activePhantomBlocks.contains(normalized) || permanentPhantomBlocks.contains(normalized);
    }

    /**
     * 仮想ブロックの追跡から除去する（プレイヤーが手動で破壊した場合など）。
     */
    public static void removeFromTracking(Location location) {
        Location normalized = location.getBlock().getLocation();
        activePhantomBlocks.remove(normalized);
        permanentPhantomBlocks.remove(normalized);
    }

    /**
     * サーバーシャットダウン時に全ての一時仮想ブロックを除去する。
     */
    public static void cleanupAll() {
        for (Location loc : activePhantomBlocks) {
            Block block = loc.getBlock();
            if (block.getType() == Material.GLASS) {
                block.setType(Material.AIR);
            }
        }
        activePhantomBlocks.clear();
    }

    @Override
    public AoeMode getAoeMode() { return AoeMode.HIT_FACE_OUTWARD; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "仮想ブロック"; }

    @Override
    public String getDescription() { return "一時的なブロックを設置する"; }

    @Override
    public int getManaCost() { return config.getManaCost("phantom_block"); }

    @Override
    public int getTier() { return config.getTier("phantom_block"); }
}
