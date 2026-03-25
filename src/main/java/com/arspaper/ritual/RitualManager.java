package com.arspaper.ritual;

import com.arspaper.ArsPaper;
import com.arspaper.block.BlockKeys;
import com.arspaper.block.impl.Pedestal;
import com.arspaper.block.impl.RitualCore;
import com.arspaper.block.impl.SourceJar;
import com.arspaper.item.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 儀式の実行を管理する。
 * Ritual Coreの周囲1ブロック以内のPedestalを検索し、
 * コアアイテム+台座素材が一致するレシピを発動する。
 * 3秒間の吸い込みパーティクルアニメーション付き。
 */
public class RitualManager {

    private static final int SEARCH_RADIUS = 3;
    private final RitualRecipeRegistry recipeRegistry;
    private final RitualEffectRegistry effectRegistry;
    private final Set<Location> activatingRituals = new HashSet<>();

    public RitualManager(RitualRecipeRegistry recipeRegistry, RitualEffectRegistry effectRegistry) {
        this.recipeRegistry = recipeRegistry;
        this.effectRegistry = effectRegistry;
    }

    /** シャットダウン時にアクティブ儀式のロックを解放する。 */
    public void shutdown() {
        activatingRituals.clear();
    }

    /**
     * 儀式の実行を試みる。
     */
    public void tryPerformRitual(Player player, Location coreLocation) {
        // 排他制御（Block座標のみで比較、yaw/pitchを除外）
        if (activatingRituals.contains(coreLocation.getBlock().getLocation())) {
            player.sendMessage(Component.text("儀式が進行中です！", NamedTextColor.YELLOW));
            return;
        }

        Block coreBlock = coreLocation.getBlock();
        if (!(coreBlock.getState() instanceof TileState coreTileState)) return;

        // コアアイテムを取得
        RitualIngredient coreIngredient = RitualCore.getCoreIngredient(coreTileState);

        // 周囲のPedestalを検索
        List<PedestalInfo> pedestals = findNearbyPedestals(coreLocation);

        // Pedestalの素材リストを構築
        List<RitualIngredient> pedestalIngredients = new ArrayList<>();
        for (PedestalInfo info : pedestals) {
            if (info.ingredient != null) {
                pedestalIngredients.add(info.ingredient);
            }
        }

        if (pedestalIngredients.isEmpty() && coreIngredient == null) {
            player.sendMessage(Component.text("台座とコアにアイテムを置いてください", NamedTextColor.YELLOW));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 1.0f, 0.5f);
            return;
        }

        // マッチするレシピを検索（コアアイテムも含めて）
        Optional<RitualRecipe> matchOpt = recipeRegistry.findMatch(coreIngredient, pedestalIngredients);
        if (matchOpt.isEmpty()) {
            player.sendMessage(Component.text("これらのアイテムに一致する儀式がありません！", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 1.0f, 0.5f);
            return;
        }

        RitualRecipe recipe = matchOpt.get();

        // Source予約消費（TOCTOU防止: チェックと消費を一体化）
        // アニメーション中に他の儀式がSourceを使い切るのを防ぐため、先に消費する
        final boolean sourceReserved;
        if (recipe.sourceRequired() > 0) {
            if (!consumeSourceFromNearby(coreLocation, recipe.sourceRequired())) {
                player.sendMessage(Component.text(
                    "ソースが不足しています！必要量: " + recipe.sourceRequired(), NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 1.0f, 0.5f);
                return;
            }
            sourceReserved = true;
        } else {
            sourceReserved = false;
        }

        // アニメーション付き儀式実行（3秒）
        Location coreLoc = coreLocation.getBlock().getLocation();
        activatingRituals.add(coreLoc);

        player.sendMessage(Component.text("儀式を開始しています...", NamedTextColor.GOLD));
        coreLocation.getWorld().playSound(
            coreLocation.clone().add(0.5, 0.5, 0.5),
            Sound.BLOCK_BEACON_AMBIENT, 1.0f, 0.8f);

        new BukkitRunnable() {
            private int ticks = 0;
            private static final int TOTAL_TICKS = 60; // 3秒

            @Override
            public void run() {
                ticks++;

                if (ticks <= TOTAL_TICKS) {
                    Location center = coreLocation.clone().add(0.5, 1.5, 0.5);
                    Location coreTop = coreLocation.clone().add(0.5, 1.2, 0.5);
                    double progress = ticks / (double) TOTAL_TICKS;
                    World world = coreLocation.getWorld();

                    // === コアアイテムのパーティクル ===
                    // コア上で回転する光の輪
                    double coreAngle = ticks * 0.3;
                    double coreRadius = 0.4 * (1.0 + progress * 0.3);
                    for (int ring = 0; ring < 3; ring++) {
                        double a = coreAngle + ring * (2.0 * Math.PI / 3.0);
                        double cx = Math.cos(a) * coreRadius;
                        double cz = Math.sin(a) * coreRadius;
                        Location ringLoc = coreTop.clone().add(cx, 0.1 * Math.sin(ticks * 0.2 + ring), cz);
                        world.spawnParticle(Particle.ENCHANT, ringLoc, 1, 0.02, 0.02, 0.02, 0.3);
                    }
                    // コア上の浮遊パーティクル（進行に応じて濃く）
                    if (ticks % 3 == 0) {
                        world.spawnParticle(Particle.END_ROD, coreTop, 1, 0.15, 0.3, 0.15, 0.02);
                    }
                    // 後半: コアから上昇する光柱
                    if (progress > 0.6) {
                        double intensity = (progress - 0.6) / 0.4; // 0→1
                        int count = (int) (3 * intensity) + 1;
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, coreTop, count, 0.1, 0.4, 0.1, 0.01);
                    }

                    // === 各Pedestalから中心へ吸い込まれるパーティクル ===
                    for (PedestalInfo pedestal : pedestals) {
                        Location pedLoc = pedestal.block.getLocation().clone().add(0.5, 1.5, 0.5);
                        drawAbsorbingParticle(pedLoc, center, progress);
                    }

                    // 中心で渦巻くパーティクル（進行に応じて激しく）
                    double angle = ticks * 0.4;
                    double radius = 0.8 * (1.0 - progress * 0.5);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    double y = 1.0 + progress * 1.5;
                    Location spiralLoc = coreLocation.clone().add(0.5 + x, y, 0.5 + z);
                    world.spawnParticle(Particle.END_ROD, spiralLoc, 1, 0, 0, 0, 0);

                    // 後半は中心にパーティクルが集中
                    if (progress > 0.5) {
                        world.spawnParticle(Particle.WITCH, center, 3, 0.2, 0.2, 0.2, 0.05);
                    }

                    return;
                }

                // アニメーション完了 → 結果生成
                cancel();
                activatingRituals.remove(coreLoc);

                // アイテム複製防止: アニメーション後に素材を再検証
                Block revalidateBlock = coreLocation.getBlock();
                if (!(revalidateBlock.getState() instanceof TileState revalidateCore)) {
                    player.sendMessage(Component.text("儀式が中断されました！", NamedTextColor.RED));
                    refundSource(coreLocation, sourceReserved ? recipe.sourceRequired() : 0);
                    return;
                }
                RitualIngredient revalidateCoreItem = RitualCore.getCoreIngredient(revalidateCore);
                List<PedestalInfo> revalidatePedestals = findNearbyPedestals(coreLocation);
                List<RitualIngredient> revalidateIngredients = new ArrayList<>();
                for (PedestalInfo info : revalidatePedestals) {
                    if (info.ingredient != null) revalidateIngredients.add(info.ingredient);
                }
                Optional<RitualRecipe> revalidateMatch = recipeRegistry.findMatch(revalidateCoreItem, revalidateIngredients);
                if (revalidateMatch.isEmpty() || !revalidateMatch.get().equals(recipe)) {
                    player.sendMessage(Component.text("素材が変更されたため儀式が失敗しました！", NamedTextColor.RED));
                    refundSource(coreLocation, sourceReserved ? recipe.sourceRequired() : 0);
                    return;
                }

                // エフェクトの事前検証（素材消費前にチェック）
                if (!recipe.isCraftType()) {
                    Optional<RitualEffect> preValidateEffect = effectRegistry.get(recipe.effectType());
                    if (preValidateEffect.isPresent() && !preValidateEffect.get().validate(coreLocation, player, recipe)) {
                        refundSource(coreLocation, sourceReserved ? recipe.sourceRequired() : 0);
                        return;
                    }
                }

                // Source消費は予約済み（アニメーション前に消費済み）

                // Pedestalの素材を消費（再検証後のPedestalを使用）
                consumePedestalItems(revalidatePedestals, recipe.pedestalItems());

                // effectType分岐
                if (!recipe.isCraftType()) {
                    // world_effect / thread タイプ
                    Optional<RitualEffect> effectOpt = effectRegistry.get(recipe.effectType());
                    if (effectOpt.isPresent()) {
                        // thread/enchant_book以外ではコアアイテムを消費
                        // enchant_bookはエフェクト内で本の存在確認後に自身でクリアする
                        if (recipe.coreItem() != null
                                && !"thread".equals(recipe.effectType())
                                && !"enchant_book".equals(recipe.effectType())) {
                            RitualCore.clearCoreItem(revalidateCore);
                        }
                        effectOpt.get().execute(coreLocation, player, recipe);
                    } else {
                        player.sendMessage(Component.text(
                            "不明な儀式タイプ: " + recipe.effectType(), NamedTextColor.RED));
                        return;
                    }
                    playRitualCompleteEffects(coreLocation);
                    player.sendMessage(Component.text(
                        "儀式完了: " + recipe.name() + "！", NamedTextColor.GREEN));
                } else {
                    // craft タイプ（従来のアイテム生成）
                    ItemStack result = resolveResult(recipe);
                    if (result == null) {
                        player.sendMessage(Component.text("儀式の結果が無効です！", NamedTextColor.RED));
                        return;
                    }

                    // コアアイテムを消費（レシピがコアアイテムを要求する場合）
                    if (recipe.coreItem() != null) {
                        // アップグレード儀式: 旧アイテムのデータを結果に転送
                        if (recipe.isCustomResult()) {
                            String rid = recipe.resultId();
                            if (rid.startsWith("spell_book_") || rid.startsWith("wand_")) {
                                // スペルブック/ワンド: スペルデータ転送
                                String oldSpellSlots = RitualCore.getStoredSpellSlots(revalidateCore);
                                Integer oldSpellSlot = RitualCore.getStoredSpellSlot(revalidateCore);
                                if (oldSpellSlots != null || oldSpellSlot != null) {
                                    result.editMeta(meta -> {
                                        var pdc = meta.getPersistentDataContainer();
                                        if (oldSpellSlots != null) {
                                            pdc.set(ItemKeys.SPELL_SLOTS, PersistentDataType.STRING, oldSpellSlots);
                                        }
                                        if (oldSpellSlot != null) {
                                            pdc.set(ItemKeys.SPELL_SLOT, PersistentDataType.INTEGER, oldSpellSlot);
                                        }
                                    });
                                }
                            } else if (rid.startsWith("mage_")) {
                                // 防具アップグレード: 旧アイテムの全PDCデータ+エンチャントを転送
                                ItemStack oldItem = RitualCore.getStoredItem(revalidateCore);
                                if (oldItem != null && oldItem.hasItemMeta()) {
                                    var oldMeta = oldItem.getItemMeta();
                                    var oldPdc = oldMeta.getPersistentDataContainer();

                                    // 転送対象のPDCキー
                                    org.bukkit.NamespacedKey[] transferKeys = {
                                        ItemKeys.THREAD_SLOTS,
                                        com.arspaper.mana.ManaKeys.THREAD_MANA_BONUS,
                                        com.arspaper.mana.ManaKeys.THREAD_REGEN_BONUS,
                                        com.arspaper.mana.ManaKeys.THREAD_COST_REDUCTION,
                                    };

                                    // エンチャント転送（バニラ + カスタム両方）
                                    var enchants = oldMeta.getEnchants();

                                    result.editMeta(meta -> {
                                        var dstPdc = meta.getPersistentDataContainer();
                                        // PDCキー転送
                                        for (var key : transferKeys) {
                                            // String型
                                            String strVal = oldPdc.get(key, PersistentDataType.STRING);
                                            if (strVal != null) {
                                                dstPdc.set(key, PersistentDataType.STRING, strVal);
                                                continue;
                                            }
                                            // Integer型
                                            Integer intVal = oldPdc.get(key, PersistentDataType.INTEGER);
                                            if (intVal != null) {
                                                dstPdc.set(key, PersistentDataType.INTEGER, intVal);
                                            }
                                        }
                                        // エンチャント転送
                                        for (var entry : enchants.entrySet()) {
                                            meta.addEnchant(entry.getKey(), entry.getValue(), true);
                                        }
                                        // loreは次回ThreadGui表示時に再生成される
                                    });
                                }
                            }
                        }
                        RitualCore.clearCoreItem(revalidateCore);
                    }

                    // 結果数量を適用
                    if (recipe.resultAmount() > 1) {
                        result.setAmount(recipe.resultAmount());
                    }

                    // 結果をドロップ
                    coreLocation.getWorld().dropItemNaturally(
                        coreLocation.clone().add(0.5, 1.5, 0.5), result
                    );

                    // 完了エフェクト
                    playRitualCompleteEffects(coreLocation);

                    player.sendMessage(Component.text(
                        "儀式完了: " + recipe.name() + "！", NamedTextColor.GREEN));
                }
            }
        }.runTaskTimer(ArsPaper.getInstance(), 1L, 1L);
    }

    /**
     * 台座から中心へ吸い込まれるパーティクル。
     * 進行度に応じて発生位置が中心に近づく。
     */
    private void drawAbsorbingParticle(Location from, Location to, double progress) {
        World world = from.getWorld();
        // 進行に応じて台座側から中心寄りの位置にパーティクル生成
        double t = 0.3 + progress * 0.7; // 0.3〜1.0の範囲で中心に向かう
        double x = from.getX() + (to.getX() - from.getX()) * t;
        double y = from.getY() + (to.getY() - from.getY()) * t;
        double z = from.getZ() + (to.getZ() - from.getZ()) * t;
        Location particleLoc = new Location(world, x, y, z);

        // ENDRODで光の筋、ENCHANTで魔法の粒子
        world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0.05, 0.05, 0.05, 0.01);
        world.spawnParticle(Particle.ENCHANT, particleLoc, 2, 0.1, 0.1, 0.1, 0.5);
    }

    private List<PedestalInfo> findNearbyPedestals(Location center) {
        List<PedestalInfo> pedestals = new ArrayList<>();

        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    Block block = center.getBlock().getRelative(x, y, z);
                    if (block.getType() != Material.BREWING_STAND) continue;
                    if (!(block.getState() instanceof TileState tileState)) continue;

                    String blockId = tileState.getPersistentDataContainer()
                        .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
                    if (!"pedestal".equals(blockId)) continue;

                    RitualIngredient ingredient = Pedestal.getPedestalIngredient(tileState);
                    pedestals.add(new PedestalInfo(block, tileState, ingredient));
                }
            }
        }
        return pedestals;
    }

    private boolean hasEnoughSource(Location center, int amount) {
        int totalAvailable = 0;
        int searchRadius = 5;
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    Block block = center.getBlock().getRelative(x, y, z);
                    if (!(block.getState() instanceof TileState tileState)) continue;
                    String blockId = tileState.getPersistentDataContainer()
                        .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
                    if (!"source_jar".equals(blockId)) continue;
                    totalAvailable += SourceJar.getSourceAmount(tileState);
                    if (totalAvailable >= amount) return true;
                }
            }
        }
        return totalAvailable >= amount;
    }

    private boolean consumeSourceFromNearby(Location center, int amount) {
        record JarInfo(TileState tileState, int available) {}
        List<JarInfo> jars = new ArrayList<>();
        int totalAvailable = 0;
        int searchRadius = 5;

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    Block block = center.getBlock().getRelative(x, y, z);
                    if (!(block.getState() instanceof TileState tileState)) continue;

                    String blockId = tileState.getPersistentDataContainer()
                        .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
                    if (!"source_jar".equals(blockId)) continue;

                    int available = SourceJar.getSourceAmount(tileState);
                    if (available > 0) {
                        jars.add(new JarInfo(tileState, available));
                        totalAvailable += available;
                    }
                }
            }
        }

        if (totalAvailable < amount) return false;

        int remaining = amount;
        for (JarInfo jar : jars) {
            if (remaining <= 0) break;
            int consume = Math.min(jar.available, remaining);
            SourceJar.consumeSource(jar.tileState, consume);
            remaining -= consume;
        }
        return true;
    }

    /**
     * 儀式失敗時にSourceを返還する。最寄りのSourceJarに追加。
     */
    private void refundSource(Location center, int amount) {
        if (amount <= 0) return;
        int remaining = amount;
        int searchRadius = 5;
        for (int x = -searchRadius; x <= searchRadius && remaining > 0; x++) {
            for (int y = -2; y <= 2 && remaining > 0; y++) {
                for (int z = -searchRadius; z <= searchRadius && remaining > 0; z++) {
                    Block block = center.getBlock().getRelative(x, y, z);
                    if (!(block.getState() instanceof TileState tileState)) continue;
                    String blockId = tileState.getPersistentDataContainer()
                        .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
                    if (!"source_jar".equals(blockId)) continue;
                    int added = SourceJar.addSource(tileState, remaining);
                    remaining -= added;
                }
            }
        }
    }

    private void consumePedestalItems(List<PedestalInfo> pedestals, List<RitualIngredient> requiredItems) {
        List<RitualIngredient> toConsume = new ArrayList<>(requiredItems);

        for (PedestalInfo pedestal : pedestals) {
            if (pedestal.ingredient != null && toConsume.remove(pedestal.ingredient)) {
                Pedestal.clearPedestalItem(pedestal.tileState);
            }
        }
    }

    private ItemStack resolveResult(RitualRecipe recipe) {
        if (recipe.isCustomResult()) {
            return ArsPaper.getInstance().getItemRegistry()
                .get(recipe.resultId())
                .map(item -> item.createItemStack())
                .orElse(null);
        }
        if (recipe.resultMaterial() != null) {
            return new ItemStack(recipe.resultMaterial());
        }
        return null;
    }

    private void playRitualCompleteEffects(Location location) {
        Location effectLoc = location.clone().add(0.5, 1.5, 0.5);
        location.getWorld().spawnParticle(Particle.ENCHANT, effectLoc, 100, 1.0, 1.0, 1.0, 0.5);
        location.getWorld().spawnParticle(Particle.END_ROD, effectLoc, 50, 0.5, 1.0, 0.5, 0.2);
        location.getWorld().spawnParticle(Particle.EXPLOSION, effectLoc, 1, 0, 0, 0, 0);
        location.getWorld().playSound(effectLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        location.getWorld().playSound(effectLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
    }

    private record PedestalInfo(Block block, TileState tileState, RitualIngredient ingredient) {}
}
