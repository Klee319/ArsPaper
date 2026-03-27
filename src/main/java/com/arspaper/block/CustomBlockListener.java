package com.arspaper.block;

import com.arspaper.source.SourcelinkTickTask;
import com.arspaper.source.sourcelink.Sourcelink;
import com.arspaper.util.PdcHelper;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * カスタムブロックの設置・破壊・インタラクションイベントを処理するリスナー。
 *
 * 設置時: ItemStack PDC → TileState PDC へ転送 + パーティクルキャッシュ登録
 * 破壊時: TileState PDC → ドロップItemStack PDC へ復元 + パーティクルキャッシュ除去
 * インタラクション: カスタムブロックのonBlockInteractを呼び出し
 */
public class CustomBlockListener implements Listener {

    private final JavaPlugin plugin;
    private final CustomBlockRegistry registry;
    private final BlockParticleTask particleTask;
    private final SourcelinkTickTask sourcelinkTickTask;

    /**
     * カスタムブロック設置直後の短い猶予期間中、実績クライテリアの付与を抑制するためのセット。
     * UUIDが含まれている間は PlayerAdvancementCriterionGrantEvent をキャンセルする。
     */
    private final Set<UUID> advancementBlockedPlayers = ConcurrentHashMap.newKeySet();

    public CustomBlockListener(JavaPlugin plugin, CustomBlockRegistry registry,
                               BlockParticleTask particleTask, SourcelinkTickTask sourcelinkTickTask) {
        this.plugin = plugin;
        this.registry = registry;
        this.particleTask = particleTask;
        this.sourcelinkTickTask = sourcelinkTickTask;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Optional<String> customId = PdcHelper.getCustomItemId(item);
        if (customId.isEmpty()) return;

        Optional<CustomBlock> customBlock = registry.get(customId.get());
        if (customBlock.isEmpty()) return;

        Block block = event.getBlock();
        if (!(block.getState() instanceof TileState tileState)) return;

        CustomBlock cb = customBlock.get();

        // 設置前バリデーション
        if (!cb.validatePlacement(event.getPlayer(), block)) {
            event.setCancelled(true);
            return;
        }

        // ItemStack PDC → TileState PDC 転送
        cb.writeToTileState(tileState, item);

        // パーティクルキャッシュに登録
        particleTask.addBlock(block.getLocation(), cb.getItemId());

        // Sourcelinkの場合はティックタスクに登録
        if (cb instanceof Sourcelink) {
            sourcelinkTickTask.addSourcelink(block.getLocation(), cb.getItemId());
        }

        // カスタムブロック固有の初期化
        cb.onBlockPlaced(event.getPlayer(), block, tileState);

        // カスタムブロック設置による実績付与を短期間ブロック
        UUID playerId = event.getPlayer().getUniqueId();
        advancementBlockedPlayers.add(playerId);
        plugin.getServer().getScheduler().runTaskLater(plugin,
            () -> advancementBlockedPlayers.remove(playerId), 5L);
    }

    /**
     * カスタムブロック設置直後の実績クライテリア付与をキャンセルする。
     * バニラブロック素材（BEACON等）を使用するカスタムブロックが
     * 実績を解除してしまう問題を防止する。
     */
    @EventHandler
    public void onAdvancementCriterion(
            com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent event) {
        if (advancementBlockedPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof TileState tileState)) return;

        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        String blockId = pdc.get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
        if (blockId == null) return;

        Optional<CustomBlock> customBlock = registry.get(blockId);
        if (customBlock.isEmpty()) return;

        CustomBlock cb = customBlock.get();

        // カスタムブロック固有の破壊処理
        cb.onBlockBroken(event.getPlayer(), block, tileState);

        // バニラドロップを抑制
        event.setDropItems(false);

        // パーティクルキャッシュから除去
        particleTask.removeBlock(block.getLocation());

        // Sourcelinkの場合はティックタスクから除去
        if (cb instanceof Sourcelink) {
            sourcelinkTickTask.removeSourcelink(block.getLocation());
        }

        // クリエイティブモードではカスタムアイテムもドロップしない
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // CreativeSourceJar: original_block_idが保持されている場合はそちらを使用
        String originalId = pdc.get(
            new org.bukkit.NamespacedKey("arspaper", "original_block_id"),
            PersistentDataType.STRING);
        CustomBlock dropCb = cb;
        if (originalId != null) {
            Optional<CustomBlock> originalBlock = registry.get(originalId);
            if (originalBlock.isPresent()) dropCb = originalBlock.get();
        }

        // TileState PDC → ドロップItemStack PDC 復元
        ItemStack drop = dropCb.createDropWithData(tileState);
        block.getWorld().dropItemNaturally(block.getLocation(), drop);

        // 支持ブロック連動: 上にウェイストーンがある場合は連動破壊
        checkDependentBlocks(block, event.getPlayer());
    }

    /**
     * 支持ブロック（ダイヤモンドブロック等）の破壊時、上に依存カスタムブロックがあれば連動破壊。
     */
    private void checkDependentBlocks(Block broken, Player player) {
        if (broken.getType() != Material.DIAMOND_BLOCK) return;
        Block above = broken.getRelative(org.bukkit.block.BlockFace.UP);
        if (!(above.getState() instanceof TileState ts)) return;
        String aboveId = ts.getPersistentDataContainer()
            .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
        if ("waystone".equals(aboveId)) {
            // ウェイストーンを連動破壊（BlockBreakEventを発火）
            BlockBreakEvent breakEvent = new BlockBreakEvent(above, player);
            org.bukkit.Bukkit.getPluginManager().callEvent(breakEvent);
            if (!breakEvent.isCancelled()) {
                // onBlockBreakが処理するため、ここではブロック除去のみ
                above.setType(Material.AIR);
                // ArmorStand表示除去
                above.getWorld().getNearbyEntities(
                    above.getLocation().add(0.5, 0, 0.5), 1, 2, 1
                ).forEach(entity -> {
                    if (entity instanceof org.bukkit.entity.ArmorStand stand
                            && stand.getPersistentDataContainer().has(BlockKeys.DISPLAY_MARKER)) {
                        stand.remove();
                    }
                });
            }
        }
    }

    /**
     * 爆発によるカスタムブロック破壊: バニラドロップ抑制 + カスタムドロップ生成。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        var iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (handleNonPlayerBreak(block)) {
                iterator.remove(); // バニラ処理から除外
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        var iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (handleNonPlayerBreak(block)) {
                iterator.remove();
            }
        }
    }

    /**
     * プレイヤー以外の原因でカスタムブロックが壊された場合のドロップ処理。
     * @return カスタムブロックだった場合true
     */
    private boolean handleNonPlayerBreak(Block block) {
        if (!(block.getState() instanceof TileState tileState)) return false;

        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        String blockId = pdc.get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
        if (blockId == null) return false;

        Optional<CustomBlock> customBlock = registry.get(blockId);
        if (customBlock.isEmpty()) return false;

        CustomBlock cb = customBlock.get();
        cb.onBlockBroken(null, block, tileState);

        particleTask.removeBlock(block.getLocation());
        if (cb instanceof Sourcelink) {
            sourcelinkTickTask.removeSourcelink(block.getLocation());
        }

        // CreativeSourceJar: original_block_idが保持されている場合はそちらでドロップ生成
        String originalId = pdc.get(
            new org.bukkit.NamespacedKey("arspaper", "original_block_id"),
            PersistentDataType.STRING);
        CustomBlock dropCb = cb;
        if (originalId != null) {
            Optional<CustomBlock> originalBlock = registry.get(originalId);
            if (originalBlock.isPresent()) dropCb = originalBlock.get();
        }

        ItemStack drop = dropCb.createDropWithData(tileState);
        block.getWorld().dropItemNaturally(block.getLocation(), drop);
        block.setType(org.bukkit.Material.AIR);

        return true;
    }

    /**
     * ピストンによるカスタムブロックの移動を防止する。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (isCustomBlock(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (isCustomBlock(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isCustomBlock(Block block) {
        if (!(block.getState() instanceof TileState tileState)) return false;
        return tileState.getPersistentDataContainer()
            .has(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
    }

    /**
     * 矢・トライデント等の投射物がカスタムブロック（特にDECORATED_POT）に
     * 命中した際の破壊を防止する。
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        Block hitBlock = event.getHitBlock();
        if (hitBlock == null) return;
        if (!(hitBlock.getState() instanceof TileState tileState)) return;

        String blockId = tileState.getPersistentDataContainer()
            .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
        if (blockId != null) {
            // カスタムブロックへの投射物命中をキャンセル（破壊防止）
            event.setCancelled(true);
        }
    }

    /**
     * 台座上の透明額縁をクリックした場合、下のカスタムブロック(Pedestal)に
     * インタラクションを転送する。
     */
    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof ItemFrame frame)) return;
        if (!frame.getScoreboardTags().contains("arspaper_display_frame")) return;

        event.setCancelled(true);

        // 額縁の下のブロックを取得（額縁はブロック上面Y+1に配置）
        Location frameLoc = frame.getLocation();
        Block below = frameLoc.clone().add(0, -1, 0).getBlock();
        if (!(below.getState() instanceof TileState tileState)) return;

        String blockId = tileState.getPersistentDataContainer()
            .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
        if (blockId == null) return;

        Optional<CustomBlock> customBlock = registry.get(blockId);
        if (customBlock.isEmpty()) return;

        customBlock.get().onBlockInteract(event.getPlayer(), below, tileState);
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!(block.getState() instanceof TileState tileState)) return;

        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        String blockId = pdc.get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
        if (blockId == null) return;

        Optional<CustomBlock> customBlock = registry.get(blockId);
        if (customBlock.isEmpty()) return;

        // バニラのインタラクション（書見台のGUI等）をキャンセル
        event.setCancelled(true);

        customBlock.get().onBlockInteract(event.getPlayer(), block, tileState);
    }

    /**
     * ホッパーからソースリンクへのアイテム移動を検知し、ソース変換を行う。
     * バニラ炉のインベントリにアイテムが消えるのを防止する。
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // 移動先がブロックインベントリか確認
        if (!(event.getDestination().getHolder() instanceof org.bukkit.block.BlockState destState)) return;
        if (!(destState instanceof TileState destTile)) return;

        String blockId = destTile.getPersistentDataContainer()
            .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
        if (blockId == null) return;

        Optional<CustomBlock> customBlock = registry.get(blockId);
        if (customBlock.isEmpty() || !(customBlock.get() instanceof Sourcelink sourcelink)) return;

        // ソースリンクへの移動 → アイテムをソース変換
        ItemStack item = event.getItem();
        int sourceValue = sourcelink.getSourceValueForItem(item);
        if (sourceValue > 0) {
            // ホッパーは1個ずつ移動するためそのまま消費してバッファに追加
            sourcelink.addToBuffer(destState.getBlock(), sourceValue);
            event.setCancelled(true);
            // ホッパー側のアイテムを1個減らす（次tickで反映）
            Material itemType = item.getType();
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                for (int i = 0; i < event.getSource().getSize(); i++) {
                    ItemStack slot = event.getSource().getItem(i);
                    if (slot != null && slot.getType() == itemType) {
                        slot.setAmount(slot.getAmount() - 1);
                        break;
                    }
                }
            });
        } else {
            // 対応しないアイテム → ソースリンクに入れさせない
            event.setCancelled(true);
        }
    }
}
