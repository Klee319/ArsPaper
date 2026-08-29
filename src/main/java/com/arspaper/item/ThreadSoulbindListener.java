package com.arspaper.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <b>SOULBOUND スレッドを「最初に拾った人」へ焼き付ける</b> —— 2026-08-25 (W-259)。
 *
 * <p>対象は {@link TreasureThreadSoulbindPolicy#isSoulbound}（catalog の bind-type。
 * 作業台/儀式で作れる ID は TRADEABLE なので対象外）。
 * 拾得だけでなく、チェストからインベントリへ移したときも刻印する
 * （{@code EntityPickupItemEvent} はコンテナ取り出しでは飛ばない）。
 *
 * <p>⚠ 刻印すると PDC が変わるので<b>同種でもスタックが分かれる</b>。これは仕様
 * ── 別人の個体が1スタックに混ざると、どちらの所有者を残すかを決められない。
 *
 * <p>⚠ 落とし直しでは所有者は消えない(刻印済みなら何もしない)。「捨てて他人に拾わせる」
 * 抜け道を塞ぐのがこの機構の目的なので、ここを「拾うたびに上書き」にすると
 * <b>機構ごと無意味になる</b>。
 */
public class ThreadSoulbindListener implements Listener {

    /** 刻印済みであることを見て分かるようにする lore 行の目印。 */
    private static final String LORE_MARK = "魂縛: ";

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();
        ThreadType type = threadTypeOf(stack);
        if (!TreasureThreadSoulbindPolicy.isSoulbound(type)) {
            return;
        }
        if (readOwner(stack) != null) {
            return; // 既に持ち主が居る。上書きすると機構ごと無意味になる。
        }
        stampOwner(stack, player);
        entity.setItemStack(stack);

        player.sendActionBar(Component.text(
            type.getDisplayName() + " があなたに魂縛されました(他の人は装着できません)",
            NamedTextColor.LIGHT_PURPLE));
    }

    /**
     * チェスト等から取り出した個体は pickup を飛ばすので、閉じたときにインベントリを見る。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            stampUnownedSoulbound(stack, player);
        }
        stampUnownedSoulbound(player.getItemOnCursor(), player);
    }

    private static void stampUnownedSoulbound(ItemStack stack, Player player) {
        ThreadType type = threadTypeOf(stack);
        if (!TreasureThreadSoulbindPolicy.isSoulbound(type)) {
            return;
        }
        if (readOwner(stack) != null) {
            return;
        }
        stampOwner(stack, player);
    }

    /** PDC からスレッド種別を引く。スレッドでないアイテムは {@code null}。 */
    public static ThreadType threadTypeOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.THREAD_ITEM_TYPE, PersistentDataType.STRING);
        return ThreadType.fromId(id);
    }

    /**
     * このスレッドの所有者。刻印が無ければ {@code null}。
     *
     * <p>TrinityForge の所有者が入っている品ではそちらが正
     * ({@code ItemKeys.THREAD_SOULBOUND_OWNER} の Javadoc 参照)。
     */
    public static UUID readOwner(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        UUID tfOwner = com.arspaper.integration.TrinityForgeBridge.tfOwnerId(item);
        if (tfOwner != null) {
            return tfOwner;
        }
        String raw = item.getItemMeta().getPersistentDataContainer()
            .get(ItemKeys.THREAD_SOULBOUND_OWNER, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException malformed) {
            return null; // 壊れた値は「未刻印」として扱う(永久に弾き続けるより安全側)
        }
    }

    private static void stampOwner(ItemStack stack, Player player) {
        final UUID owner = player.getUniqueId();
        final String name = player.getName();
        stack.editMeta(meta -> {
            meta.getPersistentDataContainer()
                .set(ItemKeys.THREAD_SOULBOUND_OWNER, PersistentDataType.STRING, owner.toString());
            com.arspaper.integration.TrinityForgeBridge.bindSoulbound(meta, owner);
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.text(LORE_MARK + name, NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
    }
}
