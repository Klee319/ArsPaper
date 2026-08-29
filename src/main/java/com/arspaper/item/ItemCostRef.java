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
        if (!fromStack(stack).filter(this::equals).isPresent()) {
            return false;
        }
        // バニラ素材コストはプレーンな同材質だけ。エンチャント本やエンチャント付き装備を
        // 筆記台が吸い込まないようにする（2026-08-29 実サーバ報告）。
        return custom || isPlainVanillaIngredient(stack);
    }

    /**
     * エンチャント（装備エンチャント／本の格納エンチャント）が付いているスタックは
     * バニラ素材コストの支払い対象にしない。
     */
    static boolean vanillaCostRejectsEnchanted(boolean customCost, boolean hasEnchants,
                                               boolean hasStoredEnchants) {
        if (customCost) {
            return false;
        }
        return hasEnchants || hasStoredEnchants;
    }

    private static boolean isPlainVanillaIngredient(ItemStack stack) {
        boolean stored = stack.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta meta
                && !meta.getStoredEnchants().isEmpty();
        return !vanillaCostRejectsEnchanted(false, !stack.getEnchantments().isEmpty(), stored);
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

    /** ホットバー＋メインのみ。装着中の防具・オフハンドは IRON_CHESTPLATE 等のコストに数えない。 */
    public int countIn(PlayerInventory inv) {
        int count = 0;
        for (ItemStack slot : inv.getStorageContents()) {
            if (matches(slot)) {
                count += slot.getAmount();
            }
        }
        return count;
    }

    /**
     * プレイヤーが実質保持している全数を数える。
     *
     * <p>ホットバー＋メインの36枠に加え、<b>カーソル上のアイテム</b>と
     * <b>クラフト結果枠（作業台・インベントリクラフト）</b>も走査する。
     * これらを見逃すと「結果枠に出したまま再度クラフト」でスレッドが重複作成できてしまう（W-269）。
     */
    public int countIn(Player player) {
        int count = countIn(player.getInventory());

        // カーソル上のアイテム(GUIドラッグ中 / インベントリ外に持ち出した状態)
        ItemStack cursor = player.getItemOnCursor();
        if (matches(cursor)) {
            count += cursor.getAmount();
        }

        // 作業台/インベントリクラフトの結果枠(slot 0 of top inventory in CRAFTING/WORKBENCH view)
        org.bukkit.inventory.InventoryView view = player.getOpenInventory();
        if (view != null) {
            org.bukkit.event.inventory.InventoryType topType = view.getTopInventory().getType();
            if (topType == org.bukkit.event.inventory.InventoryType.CRAFTING
                    || topType == org.bukkit.event.inventory.InventoryType.WORKBENCH) {
                ItemStack resultSlot = view.getTopInventory().getItem(0);
                if (matches(resultSlot)) {
                    count += resultSlot.getAmount();
                }
            }
            // 金床の出力スロット(slot 2)も見る
            if (topType == org.bukkit.event.inventory.InventoryType.ANVIL) {
                ItemStack anvilOut = view.getTopInventory().getItem(2);
                if (matches(anvilOut)) {
                    count += anvilOut.getAmount();
                }
            }
        }

        return count;
    }

    /** インベントリから一致スタックを消費する。不足しても可能な分だけ減らす。装着中の防具・オフハンドは対象外。 */
    public void removeFrom(PlayerInventory inv, int amount) {
        int remaining = amount;
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length && remaining > 0; i++) {
            ItemStack slot = storage[i];
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
