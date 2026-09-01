package com.arspaper.gui;

import com.arspaper.ArsPaper;
import com.arspaper.integration.TrinityForgeBridge;
import com.arspaper.integration.TrinityForgeBridge.ThreadIdentity;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.SocketedThreads;
import com.arspaper.item.ThreadSlotIdentity;
import com.arspaper.item.ThreadType;
import com.arspaper.item.impl.ThreadItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 装備 PDC にだけ載っている装着スレッドを、手持ちアイテムへ戻す。
 *
 * <p>{@link ThreadGui} の取り外しと、装備破壊時の返却が同じ組み立てを使う。
 * 種類・厳選・魂縛・バックパック中身は装備側にしか残っていないので、ここを通さないと
 * 装備と一緒に消える。
 */
public final class SocketedThreadReturn {

    private SocketedThreadReturn() {
    }

    /**
     * 装備に挿さっている効果付きスレッドを、取り外しと同じ個体として組み直す。
     * バックパック中身は先頭のバックパックスレッド1本へだけ移す（複数本へ複製しない）。
     */
    public static List<ItemStack> materialize(ItemStack equipment) {
        List<ItemStack> out = new ArrayList<>();
        if (equipment == null || !equipment.hasItemMeta()) {
            return out;
        }
        boolean backpackMoved = false;
        for (SocketedThreads.Entry entry : SocketedThreads.readAll(
                equipment.getItemMeta().getPersistentDataContainer())) {
            boolean moveBackpack = entry.type().isBackpackThread() && !backpackMoved;
            out.add(restore(equipment, entry.type(),
                    new ThreadSlotIdentity(entry.rollSeed(), entry.quality()).encode(),
                    ownerString(entry.owner()),
                    moveBackpack));
            if (moveBackpack) {
                backpackMoved = true;
            }
        }
        return out;
    }

    /**
     * GUI 取り外し1枠ぶん。{@code createThreadItemStack} は新品なので厳選と所有者を上書きする。
     */
    static ItemStack restore(ItemStack equipment, ThreadType type, String encodedRoll,
                             String ownerUuid, boolean moveBackpack) {
        ItemStack threadItem = createThreadItemStack(type);
        restoreRoll(threadItem, encodedRoll);
        restoreSoulboundOwner(threadItem, ownerUuid);
        if (moveBackpack && type != null && type.isBackpackThread()) {
            BackpackGui.transferDataToThread(equipment, threadItem);
        }
        return threadItem;
    }

    static ItemStack createThreadItemStack(ThreadType type) {
        if (type == null) {
            return new ItemStack(org.bukkit.Material.AIR);
        }
        ArsPaper plugin = ArsPaper.getInstance();
        if (plugin == null || plugin.getItemRegistry() == null) {
            return new ItemStack(type.getBaseMaterial());
        }
        return plugin.getItemRegistry()
                .get("thread_" + type.getId())
                .map(item -> item.createItemStack())
                .orElse(new ItemStack(type.getBaseMaterial()));
    }

    public static void giveOrDrop(Player player, Collection<ItemStack> items) {
        if (player == null || items == null || items.isEmpty()) {
            return;
        }
        for (ItemStack stack : items) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            var leftover = player.getInventory().addItem(stack);
            leftover.values().forEach(left ->
                    player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    public static void returnFromBrokenGear(Player player, ItemStack broken) {
        giveOrDrop(player, materialize(broken));
    }

    static void restoreRoll(ItemStack threadItem, String encodedRoll) {
        if (threadItem == null || threadItem.getType().isAir()) {
            return;
        }
        ThreadSlotIdentity slotIdentity = ThreadSlotIdentity.decode(encodedRoll);
        ThreadType type = threadTypeOf(threadItem);
        ThreadIdentity saved = new ThreadIdentity(slotIdentity.rollSeed(), slotIdentity.quality());
        threadItem.editMeta(meta -> {
            TrinityForgeBridge.writeItemRoll(meta, saved.rollSeed(), saved.quality());
            meta.lore(ThreadItem.fullLore(meta, type, saved));
        });
    }

    static void restoreSoulboundOwner(ItemStack threadItem, String ownerUuid) {
        if (threadItem == null || ownerUuid == null || ownerUuid.isBlank()) {
            return;
        }
        UUID owner;
        try {
            owner = UUID.fromString(ownerUuid);
        } catch (IllegalArgumentException malformed) {
            return;
        }
        org.bukkit.OfflinePlayer holder = org.bukkit.Bukkit.getOfflinePlayer(owner);
        String name = holder.getName() == null ? owner.toString() : holder.getName();
        threadItem.editMeta(meta -> {
            meta.getPersistentDataContainer()
                    .set(ItemKeys.THREAD_SOULBOUND_OWNER, PersistentDataType.STRING, ownerUuid);
            // Ars 側の控えPDCだけでは、ThreadItem#fullLore が読む TF の所有者行を復元できない。
            // 返却前と同じ真実台帳(ItemData)へも戻し、枠の出し入れで所有者表示と使用判定がずれないようにする。
            TrinityForgeBridge.bindSoulbound(meta, owner);
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            TrinityForgeBridge.appendOwnerLoreIfMissing(meta, lore);
            lore.add(Component.text("魂縛: " + name, NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
    }

    private static ThreadType threadTypeOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String typeId = item.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.THREAD_ITEM_TYPE, PersistentDataType.STRING);
        return ThreadType.fromId(typeId);
    }

    private static String ownerString(UUID owner) {
        return owner == null ? "" : owner.toString();
    }
}
