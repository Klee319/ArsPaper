package com.arspaper.item;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * スレッド装着 GUI が握っている「対象装備」の同一性。
 *
 * <h2>なぜ参照同一性ではだめなのか(2026-07-31 F3 指摘5)</h2>
 * {@code /ars thread} の対象はホットバーのスタックで、そのスロットは開いている
 * {@code ThreadGui} の下段に描画されていて<b>クリックできる</b>
 * ({@code GuiListener} は ThreadGui に限りプレイヤーインベントリ側のクリックを意図的に
 * 通している ── カーソルにスレッドを載せるため)。対象の剣を下段で拾ってカーソルへ載せてから
 * 空きスロットをクリックすると、カーソルはスレッドではないので在庫のスレッドが 1 個消費される
 * 一方、GUI が握っている {@code targetItem} はスロットから抜けた側の参照なので
 * 書き込みが乗らず、<b>プレイヤーは成功したと思ってスレッドを失う</b>。
 *
 * <p>そこで装着/取り外しの直前に「対象スロットの中身が今も同じ品か」を
 * <b>material + CustomModelData + カスタムアイテムID</b> で再確認する。
 * {@code ItemStack} の参照同一性には依存しない ── Bukkit のスタックはスロット移動や
 * {@code editMeta} で別インスタンスに化けるため、参照比較は正常系でも外れる。
 *
 * <p>Bukkit ランタイム無しで評価できる純粋な比較だけを持つ(生成だけが {@link ItemStack} を要る)。
 *
 * @param materialName     材質名({@code null} = 対象なし)
 * @param customModelData  CustomModelData(未設定は {@code null})
 * @param itemId           Ars/TF どちらかのカスタムアイテムID(素のバニラ品は {@code null})
 */
public record ThreadTargetIdentity(String materialName, Integer customModelData, String itemId) {

    /** 対象が存在しない(スロットが空、または解決できなかった)ことを表す値。 */
    public static final ThreadTargetIdentity NONE = new ThreadTargetIdentity(null, null, null);

    /**
     * ライブの {@link ItemStack} から同一性を作る。
     * カスタムアイテムIDは Ars({@code arspaper:custom_item_id})と
     * TF({@code trinityforge:catalog_id})の<b>両方</b>を見る
     * ({@code PdcHelper#getCrossPluginItemId})── スレッド枠を持つ装備は TF カタログ側にある。
     */
    public static ThreadTargetIdentity of(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return NONE;
        }
        Integer cmd = null;
        if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData()) {
            cmd = item.getItemMeta().getCustomModelData();
        }
        String id = com.arspaper.util.PdcHelper.getCrossPluginItemId(item).orElse(null);
        return new ThreadTargetIdentity(item.getType().name(), cmd, id);
    }

    /** 対象が実在するか。 */
    public boolean isPresent() {
        return materialName != null;
    }

    /** 同じ品と見なせるか。どちらかが「対象なし」なら常に不一致。 */
    public boolean matches(ThreadTargetIdentity other) {
        if (other == null || !isPresent() || !other.isPresent()) {
            return false;
        }
        return materialName.equals(other.materialName)
                && Objects.equals(customModelData, other.customModelData)
                && Objects.equals(itemId, other.itemId);
    }
}
