package com.arspaper.ritual;

import com.arspaper.ArsPaper;
import com.arspaper.block.BlockKeys;
import com.arspaper.block.impl.Pedestal;
import com.arspaper.block.impl.RitualCore;
import com.arspaper.block.impl.SourceJar;
import com.arspaper.integration.TrinityForgeBridge;
import com.arspaper.item.BaseCustomItem;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.impl.ThreadItem;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * 儀式の実行を管理する。
 * Ritual Coreの周囲1ブロック以内のPedestalを検索し、
 * コアアイテム+台座素材が一致するレシピを発動する。
 * 3秒間の吸い込みパーティクルアニメーション付き。
 */
public class RitualManager {

    /** 台座の検索距離: コアから1ブロック空けた正方形リング (max(|x|,|z|)==2, 計16マス) */
    private static final int PEDESTAL_DISTANCE = 2;
    /**
     * コアアイテムを消費せず、効果側がコアの中身を変換して同じコアへ書き戻す effect-type 群。
     * "thread"（空スレッド→型付きスレッド）に加え、"thread_slot_expand"（装備のスレッド枠+1儀式）と
     * "thread_reroll"（スレッドの厳選振り直し。コアのスレッドをその場で書き換える）が該当。
     */
    private static final Set<String> CORE_PRESERVING_EFFECT_TYPES =
            Set.of("thread", "thread_slot_expand", "thread_reroll");
    private final RitualRecipeRegistry recipeRegistry;
    private final RitualEffectRegistry effectRegistry;
    /** 儀式の perk 解放ゲート + 修繕儀式コスト設定の参照。 */
    private final com.arspaper.recipe.UnlockGate unlockGate;
    private final Set<Location> activatingRituals = new HashSet<>();

    public RitualManager(RitualRecipeRegistry recipeRegistry, RitualEffectRegistry effectRegistry,
                         com.arspaper.recipe.UnlockGate unlockGate) {
        this.recipeRegistry = recipeRegistry;
        this.effectRegistry = effectRegistry;
        this.unlockGate = unlockGate;
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

        // perk 解放ゲート（Source消費前にチェック）。
        // 必要 perk 未所持なら中止し、Sourceは消費しない。
        if (!unlockGate.hasRitualPermission(player, recipe.id())) {
            player.sendMessage(Component.text("この儀式を行う権限がありません", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 1.0f, 0.5f);
            return;
        }

        // 修繕儀式の有効/無効 + 追加 Source コスト（設定駆動）。
        boolean isRepair = "repair".equals(recipe.effectType());
        if (isRepair && !unlockGate.isRepairEnabled()) {
            player.sendMessage(Component.text("修繕の儀式は現在無効化されています", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 1.0f, 0.5f);
            return;
        }
        int extraSource = isRepair ? unlockGate.repairExtraSourceCost() : 0;
        int baseSourceRequired = recipe.sourceRequired() + extraSource;
        // 要件⑥ source-cost-reduction: skilltree由来のstat(source_cost_reduction、装備+perk合算)で
        // ソース消費を割合減する(2026-07-23 stat-gate-overhaul §2でdedicated-effectからstat化)。
        // プレイヤーが特定できる儀式実行経路(このメソッドの呼び出し元)にのみ適用する。
        // TF未ロード/未所持時はtfStatTotalが0.0を返し、reductionFrac=0で従来消費のまま(fail-open)。
        // 2026-07-23 正準スケール分数統一によりstat値は分数[0,1](例 0.10=10%減)、
        // clampReductionFractionで[0,0.95]へ安全クランプ(全額無料化を防止)する。
        double reductionFrac = TrinityForgeBridge.clampReductionFraction(
            TrinityForgeBridge.tfStatTotal(player, TrinityForgeBridge.STAT_SOURCE_COST_REDUCTION));
        int totalSourceRequired = Math.max(0,
            (int) Math.round(baseSourceRequired * (1.0 - reductionFrac)));

        // Source予約消費（TOCTOU防止: チェックと消費を一体化）
        // アニメーション中に他の儀式がSourceを使い切るのを防ぐため、先に消費する
        final int reservedSource;
        if (totalSourceRequired > 0) {
            if (!consumeSourceFromNearby(coreLocation, totalSourceRequired)) {
                player.sendMessage(Component.text(
                    "ソースが不足しています！必要量: " + totalSourceRequired, NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 1.0f, 0.5f);
                return;
            }
            reservedSource = totalSourceRequired;
        } else {
            reservedSource = 0;
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
                    refundSource(coreLocation, reservedSource);
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
                    refundSource(coreLocation, reservedSource);
                    return;
                }

                // エフェクトの事前検証 + craft結果の事前解決（素材消費前 — 失敗時の消滅防止）
                ItemStack craftResult = null;
                if (!recipe.isCraftType()) {
                    Optional<RitualEffect> preValidateEffect = effectRegistry.get(recipe.effectType());
                    if (preValidateEffect.isEmpty()) {
                        player.sendMessage(Component.text(
                            "不明な儀式タイプ: " + recipe.effectType(), NamedTextColor.RED));
                        refundSource(coreLocation, reservedSource);
                        return;
                    }
                    if (!preValidateEffect.get().validate(coreLocation, player, recipe)) {
                        refundSource(coreLocation, reservedSource);
                        return;
                    }
                } else {
                    craftResult = resolveResult(recipe, player);
                    if (craftResult == null) {
                        player.sendMessage(Component.text("儀式の結果が無効です！", NamedTextColor.RED));
                        refundSource(coreLocation, reservedSource);
                        return;
                    }
                }

                // Source消費は予約済み（アニメーション前に消費済み）

                // Pedestalの素材を消費（再検証後のPedestalを使用）
                consumePedestalItems(player, revalidatePedestals, recipe.pedestalItems());

                // 第2目標「累計1億ソース」用の集計(2026-07-31)。
                //
                // **予約直後ではなくここで積む理由**: 予約後の中断・素材差し替え・効果検証失敗など
                // 5経路が refundSource でソースをジャーへ返す。予約時点で積むと「返ってきた分も
                // 累計に入る」ため、儀式をわざと失敗させ続けるだけで実質ゼロコストで累計を膨らませられた
                // (累計カウンタは単調増加が要件で減算口を持たないので、後から引くこともできない)。
                // ここまで来れば返還経路は全て通過済み＝ソースは本当に消えている。
                TrinityForgeBridge.recordSourceSpent(player, reservedSource);

                // effectType分岐
                if (!recipe.isCraftType()) {
                    // world_effect / thread タイプ（存在は消費前に確認済み）
                    Optional<RitualEffect> effectOpt = effectRegistry.get(recipe.effectType());
                    if (effectOpt.isPresent()) {
                        // thread / thread_slot_expand 以外ではコアアイテムを消費
                        // (thread_slot_expand はコアの装備を変換して同じコアへ書き戻すため、
                        // thread と同じ「コア非消費」側 = CORE_PRESERVING_EFFECT_TYPES に加える)
                        if (recipe.coreItem() != null
                                && !CORE_PRESERVING_EFFECT_TYPES.contains(recipe.effectType())) {
                            RitualCore.clearCoreItem(revalidateCore);
                        }
                        effectOpt.get().execute(coreLocation, player, recipe);
                    }
                    playRitualCompleteEffects(coreLocation);
                    player.sendMessage(Component.text(
                        "儀式完了: " + recipe.name() + "！", NamedTextColor.GREEN));
                } else {
                    // craft タイプ（結果は消費前に解決済み）
                    ItemStack result = craftResult;

                    // コアアイテムを消費（レシピがコアアイテムを要求する場合）
                    if (recipe.coreItem() != null) {
                        // アップグレード儀式: 旧アイテムのデータを結果に転送
                        if (recipe.isCustomResult()) {
                            // tfcatalog: 経由(カタログ儀式)でも同じ転送処理が効くようプレフィックスを剥がす
                            String rid = recipe.resultId();
                            if (rid.startsWith(CatalogRitualRegistrar.RESULT_PREFIX)) {
                                rid = rid.substring(CatalogRitualRegistrar.RESULT_PREFIX.length());
                            }
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
                                        ItemKeys.THREAD_LORE,
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
                                        // Ars所有のthread loreもPDCで転送され、TF再構築後に末尾へ復元される。
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

                    // craft-quality一本化: TFカタログは装備のみ品質+SOULBOUND作成者刻印。
                    // Arsカスタムは isQualityStamped のもののみ品質刻印。
                    if (recipe.isCustomResult()) {
                        String rid = recipe.resultId();
                        // U1/N6: 儀式EXPを素材ごとに決めるため、消費素材のトークンを渡す。
                        List<String> consumedTokens = consumedMaterialTokens(recipe);
                        // 2026-08-04: 消費ソース量ぶんの追加EXP。ここは全ての refundSource 経路を
                        // 通過した後(recordSourceSpent と同じ地点より下)なので、reservedSource は
                        // 「本当に消えたソース量」と一致する。予約直後の値を渡すと、儀式をわざと
                        // 失敗させて返還させるだけでEXPを稼げる経路になる。
                        if (rid != null && rid.startsWith(CatalogRitualRegistrar.RESULT_PREFIX)) {
                            TrinityForgeBridge.finalizeCatalogRitualResult(
                                    result, player, consumedTokens, reservedSource);
                        } else {
                            // 2026-08-03 実サーバ報告「Ars鍛冶の経験値が入らない」の修正: 品質刻印の
                            // 可否(isQualityStamped)はEXP付与の可否と別物。ソースジェムの系譜・
                            // エンチャント本・ウェイストーン・テレポートコンパス等、品質を刻む対象で
                            // ない儀式結果でも、儀式を行った労力ぶんのEXPは常に付与する
                            // (TrinityForgeBridge#grantArsSmithingExpOnly のjavadoc参照。分解/逆儀式
                            // が無いため無限EXP経路にはならないことを確認済み)。
                            boolean qualityStamped = ArsPaper.getInstance().getItemRegistry()
                                .get(recipe.resultId())
                                .filter(BaseCustomItem::isQualityStamped)
                                .isPresent();
                            if (qualityStamped) {
                                TrinityForgeBridge.finalizeArsSmithingResult(
                                        result, player, consumedTokens, reservedSource);
                            } else {
                                TrinityForgeBridge.grantArsSmithingExpOnly(
                                        result, player, consumedTokens, reservedSource);
                            }
                        }
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

    /**
     * コアから1ブロック空けた正方形リング上の台座を検索する。
     * XZ平面で max(|x|,|z|) == PEDESTAL_DISTANCE の16マス、Y方向は±1を許容。
     */
    private List<PedestalInfo> findNearbyPedestals(Location center) {
        List<PedestalInfo> pedestals = new ArrayList<>();
        int d = PEDESTAL_DISTANCE;

        for (int x = -d; x <= d; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -d; z <= d; z++) {
                    // 正方形リングの外周のみ（内側を除外）
                    if (Math.abs(x) < d && Math.abs(z) < d) continue;

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
        // 2026-07-31: long で積む。上位ジャー(容量5000万)を並べると int では溢れる
        // (605マス x 5000万 = 3.0e10)。溢れると合計が負に化けて「十分あるのに足りない」判定になる。
        long totalAvailable = 0;
        int searchRadius = 5;
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    Block block = center.getBlock().getRelative(x, y, z);
                    if (!(block.getState() instanceof TileState tileState)) continue;
                    String blockId = tileState.getPersistentDataContainer()
                        .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
                    // 2026-07-31: 上位ジャーも吸えるように sourcejars.yml 定義の全ジャーへ拡張。
                    if (!SourceJar.isSourceJarId(blockId)) continue;
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
        // 2026-07-31: hasEnoughSource と同じ理由で long。
        long totalAvailable = 0;
        int searchRadius = 5;

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    Block block = center.getBlock().getRelative(x, y, z);
                    if (!(block.getState() instanceof TileState tileState)) continue;

                    String blockId = tileState.getPersistentDataContainer()
                        .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
                    // 2026-07-31: 上位ジャーも吸えるように sourcejars.yml 定義の全ジャーへ拡張。
                    if (!SourceJar.isSourceJarId(blockId)) continue;

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
                    // 2026-07-31: 上位ジャーも吸えるように sourcejars.yml 定義の全ジャーへ拡張。
                    if (!SourceJar.isSourceJarId(blockId)) continue;
                    int added = SourceJar.addSource(tileState, remaining);
                    remaining -= added;
                }
            }
        }
    }

    /**
     * この儀式が消費する素材のトークン列 (U1/N6)。TrinityForge の
     * {@code smithing.exp-per-material} と同じ語彙で、素材1個につき1要素
     * (台座は1台につき1個なので {@code pedestalItems()} の要素数がそのまま個数になる)。
     *
     * <p><b>実際に台座から取り出した ItemStack ではなくレシピ定義を使う理由</b>:
     * レシピ側は最初から {@code custom:<id>} / Material 名という config と同じ語彙で持っており、
     * PDC を読み直す必要がない。台座の実物から起こすと、Ars と TF の刻印の読み分けを
     * ここでもう一度実装することになり、TF 側の表と食い違う余地が増える。
     *
     * <p>コアアイテムも craft 儀式では消費されるので含める。
     */
    private List<String> consumedMaterialTokens(RitualRecipe recipe) {
        List<String> tokens = new ArrayList<>();
        if (recipe == null) {
            return tokens;
        }
        addMaterialToken(tokens, recipe.coreItem());
        for (RitualIngredient ingredient : recipe.pedestalItems()) {
            addMaterialToken(tokens, ingredient);
        }
        return tokens;
    }

    /** package-private: 1素材ぶんのトークンを積む(ユニットテストから直接叩けるように)。 */
    static void addMaterialToken(List<String> tokens, RitualIngredient ingredient) {
        if (ingredient == null || ingredient.materialOrCustomId() == null
                || ingredient.materialOrCustomId().isBlank()) {
            return;
        }
        tokens.add(ingredient.isCustom()
            ? "custom:" + ingredient.materialOrCustomId()
            : ingredient.materialOrCustomId());
    }

    private void consumePedestalItems(Player player, List<PedestalInfo> pedestals,
            List<RitualIngredient> requiredItems) {
        List<RitualIngredient> toConsume = new ArrayList<>(requiredItems);

        // 要件 material-refund-chance: skilltree由来のperkでペデスタル素材の消費をまれに1個返却する。
        // TF未ロード/perk未所持時はfrac<=0のため必ず従来通り消費のまま(fail-open)。
        // 2026-07-23 正準スケール分数統一によりstat値は分数[0,1](例 0.10=10%)。
        double refundFrac = TrinityForgeBridge.tfMaterialRefundChanceFraction(player);

        for (PedestalInfo pedestal : pedestals) {
            if (pedestal.ingredient != null && toConsume.remove(pedestal.ingredient)) {
                // clearPedestalItemで消し去る前に、可能ならTileStateから正確なItemStackを復元しておく。
                ItemStack storedItem = Pedestal.getStoredItemStack(pedestal.tileState);
                Pedestal.clearPedestalItem(pedestal.tileState);

                boolean doRefund = refundFrac > 0.0
                    && ThreadLocalRandom.current().nextDouble() < refundFrac;
                if (doRefund) {
                    // 返却量の定義は最小(1個/1種)であり要調整。
                    ItemStack refundStack = storedItem != null
                        ? storedItem.asOne()
                        : resolveIngredientAsItemStack(pedestal.ingredient);
                    if (refundStack != null) {
                        var overflow = player.getInventory().addItem(refundStack);
                        if (!overflow.isEmpty()) {
                            overflow.values().forEach(item ->
                                player.getWorld().dropItemNaturally(player.getLocation(), item));
                        }
                    }
                }
            }
        }
    }

    /**
     * {@link RitualIngredient}からItemStackを復元する(TileState由来のItemStackが取得できなかった場合の
     * フォールバック)。カスタムアイテムはitemRegistryから、バニラ素材はMaterial名から解決する。
     * 解決不能時は{@code null}(fail-open、返却なし)。
     */
    private ItemStack resolveIngredientAsItemStack(RitualIngredient ingredient) {
        if (ingredient.isCustom()) {
            return ArsPaper.getInstance().getItemRegistry()
                .get(ingredient.materialOrCustomId())
                .map(item -> item.createItemStack())
                .orElse(null);
        }
        Material mat = Material.matchMaterial(ingredient.materialOrCustomId());
        return mat != null ? new ItemStack(mat, 1) : null;
    }

    private ItemStack resolveResult(RitualRecipe recipe, Player player) {
        if (recipe.isCustomResult()) {
            String rid = recipe.resultId();
            if (rid != null && rid.startsWith(CatalogRitualRegistrar.RESULT_PREFIX)) {
                String catalogId = rid.substring(CatalogRitualRegistrar.RESULT_PREFIX.length());
                // Ars登録済みアイテム(魔導書/素材等)はArs実体を優先: 機能PDC(book tier等)を持たせる。
                ItemStack ars = ArsPaper.getInstance().getItemRegistry()
                    .get(catalogId)
                    .map(item -> createResultItem(item, player))
                    .orElse(null);
                if (ars != null) {
                    return ars;
                }
                ItemStack tf = TrinityForgeBridge.createCatalogIdentity(catalogId);
                if (tf != null) {
                    return tf;
                }
            }
            return ArsPaper.getInstance().getItemRegistry()
                .get(recipe.resultId())
                .map(item -> createResultItem(item, player))
                .orElse(null);
        }
        if (recipe.resultMaterial() != null) {
            return new ItemStack(recipe.resultMaterial());
        }
        return null;
    }

    /**
     * 儀式クラフトの結果 ItemStack を生成する。{@link ThreadItem} だけは生成者(player)を渡し、
     * TF のクラフト品質をスレッド個体の quality として刻ませる(TF {@code stats/item-stats.yml} の
     * {@code items.<MATERIAL#CMD>.per-quality}/{@code random})。他のカスタムアイテムは従来どおり
     * player 情報を使わない。
     */
    private static ItemStack createResultItem(BaseCustomItem item, Player player) {
        if (item instanceof ThreadItem threadItem) {
            return threadItem.createItemStack(player);
        }
        return item.createItemStack();
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
