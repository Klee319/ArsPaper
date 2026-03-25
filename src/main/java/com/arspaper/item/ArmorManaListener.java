package com.arspaper.item;

import com.arspaper.ArsPaper;
import com.arspaper.enchant.ArsEnchantments;
import com.arspaper.mana.ManaKeys;
import com.arspaper.mana.ManaManager;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * メイジアーマーの装備状態を監視し、マナボーナスを計算・更新する。
 * 設定ベース防具（ARMOR_SET_ID）とレガシー防具（ARMOR_TIER）の両方に対応。
 * 被ダメ/与ダメ時のマナ回復もここで処理する。
 */
public class ArmorManaListener implements Listener {

    private static final Gson GSON = new Gson();
    private static final int POTION_DURATION = -1; // Paper 1.20+: 無限持続
    private final JavaPlugin plugin;

    private static final PotionEffectType[] THREAD_POTION_TYPES = {
        PotionEffectType.SPEED, PotionEffectType.JUMP_BOOST,
        PotionEffectType.NIGHT_VISION, PotionEffectType.FIRE_RESISTANCE,
        PotionEffectType.DOLPHINS_GRACE, PotionEffectType.CONDUIT_POWER,
        PotionEffectType.HERO_OF_THE_VILLAGE, PotionEffectType.HEALTH_BOOST
    };

    public ArmorManaListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        scheduleRecalc(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getSlotType() == InventoryType.SlotType.ARMOR
            || event.isShiftClick()) {
            scheduleRecalc(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        recalculateArmorBonus(event.getPlayer());
    }

    /**
     * 被ダメ時マナ回復: プレイヤーがエンティティからダメージを受けた時に装備の hit_mana_recovery 分マナを回復。
     * 自己ダメージ（落下、炎、窒息等）は除外し、エンティティ起因のダメージのみ対象。
     */
    @EventHandler
    public void onPlayerDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.isCancelled()) return;

        int recovery = player.getPersistentDataContainer()
            .getOrDefault(ManaKeys.ARMOR_HIT_MANA_RECOVERY, PersistentDataType.INTEGER, 0);
        if (recovery > 0) {
            ManaManager mm = ArsPaper.getInstance().getManaManager();
            if (mm != null) mm.addMana(player, recovery);
        }
    }

    /**
     * 与ダメ時マナ回復: プレイヤーが敵にダメージを与えた時に装備の damage_mana_recovery 分マナを回復。
     */
    @EventHandler
    public void onPlayerDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (event.isCancelled()) return;

        int recovery = player.getPersistentDataContainer()
            .getOrDefault(ManaKeys.ARMOR_DAMAGE_MANA_RECOVERY, PersistentDataType.INTEGER, 0);
        if (recovery > 0) {
            ManaManager mm = ArsPaper.getInstance().getManaManager();
            if (mm != null) mm.addMana(player, recovery);
        }
    }

    private void scheduleRecalc(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    recalculateArmorBonus(player);
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * プレイヤーの装備中防具のマナボーナス合計を再計算。
     * 設定ベース防具（ARMOR_SET_ID）を優先し、レガシー（ARMOR_TIER）にフォールバック。
     */
    public static void recalculateArmorBonus(Player player) {
        int totalBonus = 0;
        int totalArmorRegen = 0;
        int totalHitRecovery = 0;
        int totalDamageRecovery = 0;
        int totalThreadMana = 0;
        int totalThreadRegen = 0;
        int totalEnchantMana = 0;
        int totalEnchantRegen = 0;
        int totalCostReduction = 0;
        boolean hasFlightThread = false;
        Set<PotionEffectType> activeThreadPotions = new HashSet<>();

        ArmorConfigManager armorConfig = ArsPaper.getInstance().getArmorConfigManager();

        for (ItemStack armorPiece : player.getInventory().getArmorContents()) {
            if (armorPiece == null || !armorPiece.hasItemMeta()) continue;

            PersistentDataContainer pdc = armorPiece.getItemMeta().getPersistentDataContainer();
            String itemId = pdc.get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);

            // エンチャントボーナス
            try {
                int regenLevel = ArsEnchantments.getManaRegenLevel(armorPiece);
                if (regenLevel > 0) {
                    totalEnchantRegen += ArsEnchantments.getManaRegenForLevel(regenLevel);
                }
                int boostLevel = ArsEnchantments.getManaBoostLevel(armorPiece);
                if (boostLevel > 0) {
                    totalEnchantMana += ArsEnchantments.getManaBoostForLevel(boostLevel);
                }
            } catch (Exception ignored) {}

            // 設定ベース防具（ARMOR_SET_ID優先）
            String armorSetId = pdc.get(ItemKeys.ARMOR_SET_ID, PersistentDataType.STRING);
            if (armorSetId != null && armorConfig != null) {
                ArmorSetConfig config = armorConfig.getSetById(armorSetId);
                if (config != null) {
                    totalBonus += config.getManaBonus();
                    totalArmorRegen += config.getManaRegen();
                    totalHitRecovery += config.getHitManaRecovery();
                    totalDamageRecovery += config.getDamageManaRecovery();
                }
            } else if (itemId != null && itemId.startsWith("mage_")) {
                // レガシーフォールバック: ArmorTier enum
                Integer tier = pdc.get(ItemKeys.ARMOR_TIER, PersistentDataType.INTEGER);
                if (tier != null) {
                    totalBonus += ArmorTier.fromTier(tier).getManaBonus();
                }
            }

            // スレッドボーナス計算（ThreadConfigで効果量をオーバーライド）
            ThreadConfig threadConfig = ArsPaper.getInstance().getThreadConfig();
            List<ThreadType> threads = collectThreads(pdc);
            for (ThreadType thread : threads) {
                totalThreadMana += threadConfig.getManaBonus(thread);
                totalThreadRegen += threadConfig.getRegenBonus(thread);
                totalCostReduction += threadConfig.getCostReduction(thread);
                totalHitRecovery += threadConfig.getHitManaRecovery(thread);
                totalDamageRecovery += threadConfig.getDamageManaRecovery(thread);
                if (thread.hasPotionEffect()) {
                    activeThreadPotions.add(thread.getPotionEffect());
                }
                if (thread.isFlightThread()) {
                    hasFlightThread = true;
                }
            }
        }

        // PDCに書き込み
        PersistentDataContainer playerPdc = player.getPersistentDataContainer();
        playerPdc.set(ManaKeys.ARMOR_MANA_BONUS, PersistentDataType.INTEGER, totalBonus);
        playerPdc.set(ManaKeys.ARMOR_REGEN_BONUS, PersistentDataType.INTEGER, totalArmorRegen);
        playerPdc.set(ManaKeys.ARMOR_HIT_MANA_RECOVERY, PersistentDataType.INTEGER, totalHitRecovery);
        playerPdc.set(ManaKeys.ARMOR_DAMAGE_MANA_RECOVERY, PersistentDataType.INTEGER, totalDamageRecovery);
        playerPdc.set(ManaKeys.THREAD_MANA_BONUS, PersistentDataType.INTEGER, totalThreadMana);
        playerPdc.set(ManaKeys.THREAD_REGEN_BONUS, PersistentDataType.INTEGER, totalThreadRegen);
        playerPdc.set(ManaKeys.ENCHANT_MANA_BONUS, PersistentDataType.INTEGER, totalEnchantMana);
        playerPdc.set(ManaKeys.ENCHANT_REGEN_BONUS, PersistentDataType.INTEGER, totalEnchantRegen);
        playerPdc.set(ManaKeys.THREAD_COST_REDUCTION, PersistentDataType.INTEGER, totalCostReduction);

        updatePotionEffects(player, activeThreadPotions);

        // 飛行スレッド: エリトラなしで滑空可能にする（インスタンスメソッド呼出）
        ArmorManaListener listener = ArsPaper.getInstance().getArmorManaListener();
        if (listener != null) {
            listener.updateFlightThread(player, hasFlightThread);
        }

        ManaManager manaManager = ArsPaper.getInstance().getManaManager();
        if (manaManager != null) {
            int currentMana = manaManager.getCurrentMana(player);
            int newMax = manaManager.getMaxMana(player);
            if (currentMana > newMax) {
                currentMana = newMax;
            }
            manaManager.setCurrentMana(player, currentMana);
        }
    }

    private static List<ThreadType> collectThreads(PersistentDataContainer pdc) {
        java.util.ArrayList<ThreadType> result = new java.util.ArrayList<>();

        String threadSlotsJson = pdc.get(ItemKeys.THREAD_SLOTS, PersistentDataType.STRING);
        if (threadSlotsJson != null) {
            try {
                List<String> slots = GSON.fromJson(threadSlotsJson,
                    new TypeToken<List<String>>(){}.getType());
                if (slots != null) {
                    for (String threadId : slots) {
                        if (threadId == null) continue;
                        ThreadType thread = ThreadType.fromId(threadId);
                        if (thread != null && thread.hasEffect()) {
                            result.add(thread);
                        }
                    }
                }
            } catch (Exception ignored) {}
        } else {
            String oldThreadId = pdc.get(ItemKeys.THREAD_TYPE, PersistentDataType.STRING);
            if (oldThreadId != null) {
                ThreadType thread = ThreadType.fromId(oldThreadId);
                if (thread != null && thread.hasEffect()) {
                    result.add(thread);
                }
            }
        }
        return result;
    }

    private static void updatePotionEffects(Player player, Set<PotionEffectType> activeThreadPotions) {
        for (PotionEffectType type : THREAD_POTION_TYPES) {
            if (activeThreadPotions.contains(type)) {
                player.addPotionEffect(new PotionEffect(
                    type, POTION_DURATION, 0, true, false, true
                ));
            } else {
                PotionEffect existing = player.getPotionEffect(type);
                if (existing != null && (existing.isInfinite()
                        || existing.getDuration() >= Integer.MAX_VALUE - 100)) {
                    player.removePotionEffect(type);
                }
            }
        }
    }

    /**
     * 飛行スレッド装備中のプレイヤーUUIDセット（GlideEffectと同じ方式）。
     */
    private static final java.util.Set<java.util.UUID> flightThreadPlayers =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** allowFlight再有効化の重複防止フラグ */
    private static final java.util.Set<java.util.UUID> glideCooldown =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private boolean flightTaskStarted = false;
    private BukkitTask flightTask;

    /**
     * 飛行スレッドの状態を更新する。
     * GlideEffect（滑空グリフ）と同じバニラエリトラ方式。
     * 空中にいる間は自動でsetGliding(true)を維持。
     */
    private void updateFlightThread(Player player, boolean hasFlightThread) {
        if (!hasFlightThread) {
            if (flightThreadPlayers.remove(player.getUniqueId())) {
                player.setGliding(false);
                player.setFallDistance(0f);
                // クリエイティブ/スペクテイター以外はallowFlight解除
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE
                        && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    player.setAllowFlight(false);
                }
            }
            return;
        }

        flightThreadPlayers.add(player.getUniqueId());
        // ジャンプキー検出のためにallowFlight有効化
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE
                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            player.setAllowFlight(true);
        }
        startFlightTask();
    }

    /**
     * 飛行スレッド用の定期タスクを開始。
     * プレイヤーがいる間のみ実行し、空になったらタスクを停止する。
     */
    private void startFlightTask() {
        if (flightTaskStarted) return;
        flightTaskStarted = true;

        flightTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(
            ArsPaper.getInstance(), () -> {
                // プレイヤーがいない場合はタスクを停止
                if (flightThreadPlayers.isEmpty()) {
                    stopFlightTask();
                    return;
                }

                var iterator = flightThreadPlayers.iterator();
                while (iterator.hasNext()) {
                    Player p = org.bukkit.Bukkit.getPlayer(iterator.next());
                    if (p == null || !p.isOnline()) {
                        iterator.remove();
                        continue;
                    }
                    // 着地: 滑空を解除
                    if (p.isOnGround() && p.isGliding()) {
                        p.setGliding(false);
                        p.setFallDistance(0f);
                    }
                    // 滑空中: 落下ダメージリセット
                    if (p.isGliding()) {
                        p.setFallDistance(0f);
                    }
                    // allowFlight維持（ジャンプキー検出用: 滑空開始/停止の両方に必要）
                    // glideCooldown中は再有効化しない（滑空開始の1tick遅延を妨げないため）
                    if (!p.isOnGround() && !p.getAllowFlight()
                            && !glideCooldown.contains(p.getUniqueId())
                            && p.getGameMode() != org.bukkit.GameMode.CREATIVE
                            && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                        // 滑空中は2tick後に再有効化（即座に設定すると滑空が解除される）
                        if (p.isGliding()) {
                            java.util.UUID uid = p.getUniqueId();
                            glideCooldown.add(uid);
                            org.bukkit.Bukkit.getScheduler().runTaskLater(
                                ArsPaper.getInstance(), () -> {
                                    glideCooldown.remove(uid);
                                    if (p.isOnline() && p.isGliding()
                                            && flightThreadPlayers.contains(uid)) {
                                        p.setAllowFlight(true);
                                    }
                                }, 2L);
                        } else {
                            p.setAllowFlight(true);
                        }
                    }
                }
            }, 0L, 1L);
    }

    private void stopFlightTask() {
        if (flightTask != null) {
            flightTask.cancel();
            flightTask = null;
        }
        flightTaskStarted = false;
    }

    /**
     * 飛行スレッド: 空中でジャンプキーを押すと滑空を開始（バニラエリトラと同じ操作）。
     * setAllowFlight(true)状態で空中ジャンプするとPlayerToggleFlightEventが発火する。
     */
    @org.bukkit.event.EventHandler
    public void onPlayerToggleFlight(org.bukkit.event.player.PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!flightThreadPlayers.contains(player.getUniqueId())) return;
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;

        event.setCancelled(true);
        player.setAllowFlight(false);
        player.setFlying(false);

        if (player.isGliding()) {
            // 滑空中にジャンプキー → 滑空解除
            player.setGliding(false);
            player.setFallDistance(0f);
        } else if (!player.isOnGround()) {
            // 非滑空中にジャンプキー → 1tick後に滑空開始
            // 同一tickでallowFlight(false)+setGliding(true)するとPaperが無視するため遅延必須
            // glideCooldownでtaskがallowFlightを復帰するのを防ぐ
            glideCooldown.add(player.getUniqueId());
            org.bukkit.Bukkit.getScheduler().runTaskLater(ArsPaper.getInstance(), () -> {
                glideCooldown.remove(player.getUniqueId());
                if (player.isOnline() && !player.isOnGround() && !player.isGliding()
                        && flightThreadPlayers.contains(player.getUniqueId())) {
                    player.setGliding(true);
                    player.setFallDistance(0f);
                }
            }, 1L);
        }
    }

    /**
     * エリトラ未装備時のグライディング解除をキャンセル（飛行スレッドで代替）。
     */
    @org.bukkit.event.EventHandler
    public void onEntityToggleGlide(org.bukkit.event.entity.EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!flightThreadPlayers.contains(player.getUniqueId())) return;
        if (!event.isGliding() && player.isGliding() && !player.isOnGround()) {
            event.setCancelled(true);
        }
    }

    /**
     * 飛行スレッド装備中の落下ダメージを無効化。
     */
    @org.bukkit.event.EventHandler
    public void onFlightFallDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!flightThreadPlayers.contains(player.getUniqueId())) return;
        event.setCancelled(true);
    }

    /**
     * サーバー停止時に全飛行スレッドプレイヤーの滑空を解除。
     */
    public static void cleanupFlightThread() {
        // インスタンスの飛行タスクをキャンセル
        ArmorManaListener listener = ArsPaper.getInstance().getArmorManaListener();
        if (listener != null && listener.flightTask != null) {
            listener.flightTask.cancel();
            listener.flightTask = null;
        }
        for (java.util.UUID uuid : flightThreadPlayers) {
            Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setGliding(false);
                p.setFallDistance(0f);
            }
        }
        flightThreadPlayers.clear();
    }
}
