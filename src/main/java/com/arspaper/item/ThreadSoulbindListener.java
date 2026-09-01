package com.arspaper.item;

import com.arspaper.integration.TrinityForgeBridge;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <b>SOULBOUND スレッドを「最初に持った人」へ焼き付ける</b> —— 2026-08-25 (W-259) /
 * 2026-08-29 (W-274: 導入前個体の参加走査)。
 *
 * <p>対象は {@link TreasureThreadSoulbindPolicy#isSoulbound}（catalog の bind-type。
 * 作業台/儀式で作れる ID は TRADEABLE なので対象外）。
 * 拾得だけでなく、チェストからインベントリへ移したときも刻印する
 * （{@code EntityPickupItemEvent} はコンテナ取り出しでは飛ばない）。
 *
 * <p>導入前からインベントリ／エンダーチェストに居る個体は拾得も閉じるも飛ばない。
 * 参加時に走査し、HuskSync が snapshot を後から書く経路は
 * {@link PlayerInventorySlotChangeEvent} と参加の遅延再走査で拾う。
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

    /**
     * 参加直後の再走査猶予。HuskSync の snapshot は {@code PlayerJoinEvent} より後で、
     * 先に空を読むと導入前個体が永久に無主のまま残る。
     * TrinityForge の {@code ParticleEffectService.JOIN_REFRESH_DELAY_TICKS} と同じ 40tick。
     */
    static final long JOIN_REFRESH_DELAY_TICKS = 40L;

    private final JavaPlugin plugin;

    public ThreadSoulbindListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

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
     * チェスト等から取り出した個体は pickup を飛ばすので、閉じたときに所持品を見る。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        stampCarried(player);
    }

    /**
     * 導入前から持っている個体は拾得も閉じるも飛ばない。即刻印＋ HuskSync 待ちの再走査。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        stampCarried(player);
        UUID id = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player still = plugin.getServer().getPlayer(id);
            if (still != null && still.isOnline()) {
                stampCarried(still);
            }
        }, JOIN_REFRESH_DELAY_TICKS);
    }

    /**
     * 地面を経由しない付与（HuskSync・コマンド give・シフトクリック）。
     * アクションバーは出さない ── pickup 以外で出すとサーバ移動のたびに点く。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSlotChange(PlayerInventorySlotChangeEvent event) {
        stampUnownedSoulbound(event.getNewItemStack(), event.getPlayer());
    }

    /** インベントリ・カーソル・エンダーチェストの未刻印を刻印する。 */
    private static void stampCarried(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            stampUnownedSoulbound(stack, player);
        }
        stampUnownedSoulbound(player.getItemOnCursor(), player);
        for (ItemStack stack : player.getEnderChest().getContents()) {
            stampUnownedSoulbound(stack, player);
        }
    }

    private static void stampUnownedSoulbound(ItemStack stack, Player player) {
        ThreadType type = threadTypeOf(stack);
        if (!TreasureThreadSoulbindPolicy.isSoulbound(type)) {
            return;
        }
        if (readOwner(stack) != null) {
            return;
        }
        if (TrinityForgeBridge.isOwnershipReleased(stack)) {
            // 魂縛解きの符で意図的に解かれた個体。ここで焼き直すと符が「使っても意味が無い」
            // 道具になる(符の説明文「拾っても再び所有者は焼き付きません」も嘘になる)。
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
