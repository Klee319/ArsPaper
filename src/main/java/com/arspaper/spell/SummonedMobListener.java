package com.arspaper.spell;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 召喚モブの制御リスナー。
 * - 死亡時: アイテムドロップとXPドロップを無効化
 * - ターゲット: キャスターへの攻撃を防止、他の召喚モブへの攻撃を防止
 * - インベントリ: 召喚馬の装備枠を開けさせない（鞍の複製・預けたアイテムの消失を両方防ぐ）
 *
 * PDCキー:
 *   arspaper:summoned (BYTE) - 召喚モブマーカー
 *   arspaper:summoner_uuid (STRING) - 召喚者のUUID
 */
public class SummonedMobListener implements Listener {

    private final NamespacedKey summonedKey;
    private final NamespacedKey summonerUuidKey;

    public SummonedMobListener(JavaPlugin plugin) {
        this.summonedKey = new NamespacedKey(plugin, "summoned");
        this.summonerUuidKey = new NamespacedKey(plugin, "summoner_uuid");
    }

    public NamespacedKey getSummonedKey() { return summonedKey; }
    public NamespacedKey getSummonerUuidKey() { return summonerUuidKey; }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.has(summonedKey, PersistentDataType.BYTE)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    /**
     * 召喚モブがキャスターまたは他の召喚モブをターゲットするのを防止する。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob)) return;

        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        if (!pdc.has(summonedKey, PersistentDataType.BYTE)) return;

        LivingEntity target = event.getTarget();
        if (target == null) return;

        // キャスターへのターゲットを防止
        String summonerUuid = pdc.get(summonerUuidKey, PersistentDataType.STRING);
        if (summonerUuid != null && target instanceof Player targetPlayer) {
            if (targetPlayer.getUniqueId().toString().equals(summonerUuid)) {
                event.setCancelled(true);
                return;
            }
        }

        // 他の召喚モブへのターゲットを防止
        if (target.getPersistentDataContainer().has(summonedKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // 召喚馬のインベントリ保護（鞍の複製対策）
    //
    // 召喚馬は「操作できる騎乗」を成立させるために実物の鞍を装備している
    // （SummonSteedEffect が addPassenger する。鞍が無いと乗れても操縦できず、
    //   再騎乗の右クリックもインベントリを開く方に化けるので鞍は外せない）。
    // ところが tamed な馬はシフト右クリックで装備枠を開けるため、
    // 召喚するたびに鞍を抜き取れてしまう＝アイテム複製が成立していた。
    // 死亡ドロップは onEntityDeath で既に潰してあるので、残る経路は装備枠GUIだけ。
    // 逆に装備枠へ馬鎧を預けると、時間切れの horse.remove() でドロップせず消える
    // （remove はドロップを出さない）。どちらの事故も「開けさせない」で同時に消える。
    // ------------------------------------------------------------------

    /**
     * 召喚モブの装備枠を開かせない。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!isSummonedMobInventory(event.getInventory())) return;
        event.setCancelled(true);
        if (event.getPlayer() instanceof Player player) {
            player.sendMessage(Component.text("召喚した騎獣の装備は取り外せません",
                    NamedTextColor.RED));
        }
    }

    /**
     * 多重防御。他プラグインなどが開いてしまった場合に備え、
     * 召喚モブの装備枠に触れるクリックを通さない。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        boolean clickedIsProtected = isSummonedMobInventory(event.getClickedInventory());
        boolean topIsProtected = isSummonedMobInventory(event.getView().getTopInventory());
        if (touchesProtectedInventory(clickedIsProtected, topIsProtected,
                movesAcrossInventories(event.getAction()))) {
            event.setCancelled(true);
        }
    }

    /**
     * 多重防御。ドラッグで召喚モブの装備枠に触れる経路を塞ぐ。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!isSummonedMobInventory(top)) return;
        int topSize = top.getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * クリックが保護対象インベントリに触れているかの純判定。
     *
     * @param clickedIsProtected    クリックされた枠が保護対象か
     * @param topIsProtected        開いている上段が保護対象か
     * @param movesAcrossInventories shift クリックや数字キーなど上下段をまたぐ操作か
     */
    static boolean touchesProtectedInventory(boolean clickedIsProtected,
                                             boolean topIsProtected,
                                             boolean movesAcrossInventories) {
        if (clickedIsProtected) return true;
        return topIsProtected && movesAcrossInventories;
    }

    /**
     * 上下段をまたいでアイテムが動く操作か。
     * shift クリック・数字キー・オフハンド入替・「同じ物をカーソルへ集める」は
     * プレイヤー側の枠をクリックしていても上段（＝召喚モブの装備枠）を書き換える。
     */
    @SuppressWarnings("removal") // HOTBAR_MOVE_AND_READD は削除予告済みだが現行 API では今も飛ぶ
    static boolean movesAcrossInventories(InventoryAction action) {
        return action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.HOTBAR_MOVE_AND_READD
                || action == InventoryAction.COLLECT_TO_CURSOR;
    }

    /**
     * 召喚マーカー付きの騎獣が持ち主のインベントリか。
     */
    private boolean isSummonedMobInventory(Inventory inventory) {
        if (inventory == null) return false;
        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof AbstractHorse horse)) return false;
        return horse.getPersistentDataContainer().has(summonedKey, PersistentDataType.BYTE);
    }
}
