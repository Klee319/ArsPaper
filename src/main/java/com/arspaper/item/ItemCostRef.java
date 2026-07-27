package com.arspaper.item;

import com.arspaper.ArsPaper;
import com.arspaper.integration.TrinityForgeBridge;
import com.arspaper.util.JaTranslations;
import com.arspaper.util.PdcHelper;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 消費コスト／燃料マップ用のアイテム参照。
 * <ul>
 *   <li>バニラ: {@code COAL}, {@code ARROW}</li>
 *   <li>Ars / TF カスタム: {@code custom:source_gem} または登録済みなら裸の {@code source_gem}</li>
 * </ul>
 */
public record ItemCostRef(String id, boolean custom) {

    public static ItemCostRef ofMaterial(Material mat) {
        return new ItemCostRef(mat.name(), false);
    }

    public static ItemCostRef ofCustom(String customId) {
        String id = stripCustomPrefix(customId).toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            throw new IllegalArgumentException("empty custom item id");
        }
        return new ItemCostRef(id, true);
    }

    /**
     * YAMLキーやリスト要素をパースする。{@code custom:} 付き、または Material に一致しなければカスタムID。
     */
    public static ItemCostRef parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("blank item id");
        }
        String token = stripCount(raw.trim());
        if (token.regionMatches(true, 0, "custom:", 0, 7)) {
            return ofCustom(token.substring(7).trim());
        }
        Material mat = Material.matchMaterial(token);
        if (mat != null) {
            return ofMaterial(mat);
        }
        return ofCustom(token);
    }

    /** インベントリ上のスタックから参照を復元する。カスタムPDC / TF catalog を優先。 */
    public static Optional<ItemCostRef> fromStack(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Optional.empty();
        }
        Optional<String> arsId = PdcHelper.getCustomItemId(stack);
        if (arsId.isPresent()) {
            return Optional.of(ofCustom(arsId.get()));
        }
        try {
            if (stack.hasItemMeta()) {
                Optional<String> catalogId = com.trinityforge.pdc.ItemData.of(stack.getItemMeta()).catalogId();
                if (catalogId.isPresent() && !catalogId.get().isBlank()) {
                    return Optional.of(ofCustom(catalogId.get()));
                }
            }
        } catch (Throwable ignored) {
            // TF absent
        }
        return Optional.of(ofMaterial(stack.getType()));
    }

    public boolean matches(ItemStack stack) {
        return fromStack(stack).filter(this::equals).isPresent();
    }

    /** 表示・返却用スタック。解決不能なら AIR。 */
    public ItemStack createStack(int amount) {
        int qty = Math.max(1, amount);
        if (!custom) {
            Material mat = Material.matchMaterial(id);
            return mat != null ? new ItemStack(mat, qty) : new ItemStack(Material.AIR);
        }
        ArsPaper ars = ArsPaper.getInstance();
        if (ars != null && ars.getItemRegistry() != null) {
            Optional<ItemStack> created = ars.getItemRegistry().get(id).map(BaseCustomItem::createItemStack);
            if (created.isPresent()) {
                ItemStack stack = created.get();
                stack.setAmount(qty);
                return stack;
            }
        }
        ItemStack tf = TrinityForgeBridge.createCatalogIdentity(id);
        if (tf != null) {
            tf.setAmount(qty);
            return tf;
        }
        return new ItemStack(Material.PAPER, qty);
    }

    public String displayName() {
        if (!custom) {
            Material mat = Material.matchMaterial(id);
            return mat != null ? JaTranslations.translate(mat) : id;
        }
        ArsPaper ars = ArsPaper.getInstance();
        if (ars != null && ars.getItemRegistry() != null) {
            Optional<? extends BaseCustomItem> item = ars.getItemRegistry().get(id);
            if (item.isPresent()) {
                return plainDisplay(item.get().createItemStack());
            }
        }
        ItemStack tf = TrinityForgeBridge.createCatalogIdentity(id);
        if (tf != null) {
            return plainDisplay(tf);
        }
        return id;
    }

    public int countIn(PlayerInventory inv) {
        int count = 0;
        for (ItemStack slot : inv.getContents()) {
            if (matches(slot)) {
                count += slot.getAmount();
            }
        }
        return count;
    }

    public int countIn(Player player) {
        return countIn(player.getInventory());
    }

    /** インベントリから一致スタックを消費する。不足しても可能な分だけ減らす。 */
    public void removeFrom(PlayerInventory inv, int amount) {
        int remaining = amount;
        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack slot = inv.getItem(i);
            if (!matches(slot)) {
                continue;
            }
            int take = Math.min(remaining, slot.getAmount());
            if (slot.getAmount() - take <= 0) {
                inv.setItem(i, null);
            } else {
                slot.setAmount(slot.getAmount() - take);
            }
            remaining -= take;
        }
    }

    public void removeFrom(Player player, int amount) {
        removeFrom(player.getInventory(), amount);
    }

    public void giveOrDrop(Player player, int amount) {
        ItemStack stack = createStack(amount);
        if (stack.getType().isAir()) {
            return;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        overflow.values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    public String configKey() {
        return custom ? "custom:" + id : id;
    }

    private static String stripCustomPrefix(String raw) {
        String s = raw.trim();
        if (s.regionMatches(true, 0, "custom:", 0, 7)) {
            return s.substring(7).trim();
        }
        return s;
    }

    private static String stripCount(String raw) {
        int xIdx = raw.toLowerCase(Locale.ROOT).lastIndexOf(" x");
        if (xIdx > 0) {
            return raw.substring(0, xIdx).trim();
        }
        return raw;
    }

    private static String plainDisplay(ItemStack stack) {
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(stack.getItemMeta().displayName());
        }
        return stack.getType().name().toLowerCase(Locale.ROOT);
    }
}
