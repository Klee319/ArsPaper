package com.arspaper.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/**
 * PDC読み書きユーティリティ。
 * ItemStackは不変パターンで扱い、新しいItemStackを返す。
 */
public final class PdcHelper {

    private PdcHelper() {}

    public static <T, Z> Optional<Z> getFromItem(ItemStack item, NamespacedKey key, PersistentDataType<T, Z> type) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(key, type)) return Optional.empty();
        return Optional.ofNullable(pdc.get(key, type));
    }

    public static <T, Z> ItemStack setOnItem(ItemStack item, NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        ItemStack result = item.clone();
        result.editMeta(meta ->
            meta.getPersistentDataContainer().set(key, type, value)
        );
        return result;
    }

    public static boolean hasOnItem(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key);
    }

    public static Optional<String> getCustomItemId(ItemStack item) {
        NamespacedKey key = new NamespacedKey("arspaper", "custom_item_id");
        return getFromItem(item, key, PersistentDataType.STRING).filter(id -> !id.isBlank());
    }

    /**
     * このアイテムの「カスタムアイテムid」を、Ars 側 → TrinityForge 側の順に解決する。
     *
     * <p>Ars 実体は {@code arspaper:custom_item_id}、TFカタログ実体は
     * {@link com.trinityforge.pdc.PdcKeys#ITEM_CATALOG_ID}({@code trinityforge:catalog_id})を持つ。
     * 同じ id 空間を2つのプラグインが分担しているため、id で照合する箇所は必ず両方を読む必要がある。
     *
     * <p>【2026-07-30 これを入れた理由】Ars 側だけを読んでいたため、TFカタログ品(全 mage_* 防具・
     * thread_empty 等)を儀式の核/台座に置いても「カスタムアイテムidを持たない普通の防具」として
     * 記録され、{@code RitualIngredient.ofCustom(<catalog id>)} と一致せず
     * <b>TFカタログ由来の儀式39件(mage_* 昇格24 + スレッド15)が全て成立しなかった</b>。
     *
     * <p>キーは自前で {@code new NamespacedKey(...)} せず TF の定数を参照する。手書きしていた
     * {@code SourceAutoConsume} が {@code trinityforge:item_catalog_id} という実在しない名前を
     * 持っていて無言で外れていたため(同日修正)、定数参照だけを正とする。
     * TF は {@code paper-plugin.yml} で {@code required: true} + {@code join-classpath: true} の
     * ハード依存なので、TF の型を直接参照して問題ない。
     */
    public static Optional<String> getCrossPluginItemId(ItemStack item) {
        Optional<String> arsId = getCustomItemId(item);
        if (arsId.isPresent()) {
            return arsId;
        }
        return getFromItem(item, com.trinityforge.pdc.PdcKeys.ITEM_CATALOG_ID, PersistentDataType.STRING)
            .filter(id -> !id.isBlank());
    }
}
