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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * メイジアーマーの装備状態を監視し、マナボーナスを計算・更新する。
 * Paper固有のPlayerArmorChangeEventを使用して効率的に検知。
 * ボーナス変更時にBossBarとcurrentManaも同期する。
 * ポーション効果型スレッドの付与・解除も管理する。
 */
public class ArmorManaListener implements Listener {

    private static final Gson GSON = new Gson();
    private static final int POTION_DURATION = Integer.MAX_VALUE; // 装備中は常時
    private final JavaPlugin plugin;

    /** スレッドが付与するポーション効果タイプ一覧（解除用） */
    private static final PotionEffectType[] THREAD_POTION_TYPES = {
        PotionEffectType.SPEED, PotionEffectType.JUMP_BOOST,
        PotionEffectType.NIGHT_VISION, PotionEffectType.FIRE_RESISTANCE,
        PotionEffectType.WATER_BREATHING
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
     * プレイヤーの装備中メイジアーマーのマナボーナス合計を再計算。
     * スレッドボーナス（マルチスロット対応）、ポーション効果、
     * スペルボーナスも含めてBossBarとcurrentManaを同期する。
     */
    public static void recalculateArmorBonus(Player player) {
        int totalBonus = 0;
        int totalThreadMana = 0;
        int totalThreadRegen = 0;
        int totalEnchantMana = 0;
        int totalEnchantRegen = 0;
        int totalSpellPower = 0;
        int totalCostReduction = 0;
        Set<PotionEffectType> activeThreadPotions = new HashSet<>();

        for (ItemStack armorPiece : player.getInventory().getArmorContents()) {
            if (armorPiece == null || !armorPiece.hasItemMeta()) continue;

            PersistentDataContainer pdc = armorPiece.getItemMeta().getPersistentDataContainer();
            String itemId = pdc.get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);

            // エンチャントボーナス
            try {
                int regenLevel = ArsEnchantments.getManaRegenLevel(armorPiece);
                if (regenLevel > 0) {
                    totalEnchantRegen += regenLevel * ArsEnchantments.REGEN_PER_LEVEL;
                }
                int boostLevel = ArsEnchantments.getManaBoostLevel(armorPiece);
                if (boostLevel > 0) {
                    totalEnchantMana += ArsEnchantments.getManaBoostForLevel(boostLevel);
                }
            } catch (Exception ignored) {}

            if (itemId == null || !itemId.startsWith("mage_")) continue;

            Integer tier = pdc.get(ItemKeys.ARMOR_TIER, PersistentDataType.INTEGER);
            if (tier != null) {
                totalBonus += ArmorTier.fromTier(tier).getManaBonus();
            }

            // スレッドボーナス計算（新形式: JSON配列）
            List<ThreadType> threads = collectThreads(pdc);
            for (ThreadType thread : threads) {
                totalThreadMana += thread.getManaBonus();
                totalThreadRegen += thread.getRegenBonus();
                totalSpellPower += thread.getSpellPowerPercent();
                totalCostReduction += thread.getCostReductionPercent();
                if (thread.hasPotionEffect()) {
                    activeThreadPotions.add(thread.getPotionEffect());
                }
            }
        }

        // PDCに書き込み
        PersistentDataContainer playerPdc = player.getPersistentDataContainer();
        playerPdc.set(ManaKeys.ARMOR_MANA_BONUS, PersistentDataType.INTEGER, totalBonus);
        playerPdc.set(ManaKeys.THREAD_MANA_BONUS, PersistentDataType.INTEGER, totalThreadMana);
        playerPdc.set(ManaKeys.THREAD_REGEN_BONUS, PersistentDataType.INTEGER, totalThreadRegen);
        playerPdc.set(ManaKeys.ENCHANT_MANA_BONUS, PersistentDataType.INTEGER, totalEnchantMana);
        playerPdc.set(ManaKeys.ENCHANT_REGEN_BONUS, PersistentDataType.INTEGER, totalEnchantRegen);
        playerPdc.set(ManaKeys.THREAD_SPELL_POWER, PersistentDataType.INTEGER, totalSpellPower);
        playerPdc.set(ManaKeys.THREAD_COST_REDUCTION, PersistentDataType.INTEGER, totalCostReduction);

        // ポーション効果の付与・解除
        updatePotionEffects(player, activeThreadPotions);

        // BossBar再描画 & currentManaが新maxManaを超えていたらクランプ
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

    /**
     * 防具PDCからスレッドリストを収集する。旧形式からのフォールバック対応。
     */
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
            // 旧形式フォールバック
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

    /**
     * スレッドによるポーション効果を更新する。
     * スレッドが付与したポーション効果のみを管理し、他の要因による同効果には干渉しない。
     */
    private static void updatePotionEffects(Player player, Set<PotionEffectType> activeThreadPotions) {
        for (PotionEffectType type : THREAD_POTION_TYPES) {
            if (activeThreadPotions.contains(type)) {
                // 効果付与（既に同じ効果がある場合は上書き）
                player.addPotionEffect(new PotionEffect(
                    type, POTION_DURATION, 0, true, false, true
                ));
            } else {
                // スレッドが付与した効果を解除
                // 他の要因（ポーション飲用等）による効果は短い持続時間なので
                // MAX_VALUE持続の効果のみ解除する
                PotionEffect existing = player.getPotionEffect(type);
                if (existing != null && existing.getDuration() >= POTION_DURATION - 100) {
                    player.removePotionEffect(type);
                }
            }
        }
    }
}
