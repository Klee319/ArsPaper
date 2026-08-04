package com.arspaper.item.impl;

import com.arspaper.integration.TrinityForgeBridge;
import com.arspaper.integration.TrinityForgeBridge.ThreadIdentity;
import com.arspaper.item.BaseCustomItem;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.ThreadType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * スレッドアイテム。防具のスレッドスロットにセットして使う。
 * 空スレッド（EMPTY）は儀式で型付きスレッドに変換する中間素材。
 */
public class ThreadItem extends BaseCustomItem {

    private final ThreadType threadType;

    public ThreadItem(JavaPlugin plugin, ThreadType threadType) {
        super(plugin, "thread_" + threadType.getId());
        this.threadType = threadType;
    }

    @Override
    public Material getBaseMaterial() {
        return threadType.getBaseMaterial();
    }

    @Override
    public Component getDisplayName() {
        return Component.text(threadType.getDisplayName(), threadType.getColor())
            .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public int getCustomModelData() {
        return threadType.getCustomModelData();
    }

    @Override
    public ItemStack createItemStack() {
        // 生成者（誰が作ったか）が分からない経路（ルートチェスト/ダンジョンドロップ/管理コマンド
        // 付与等）のフォールバック。品質は0扱いになり、従来どおりの幅でロールする
        // （TrinityForgeBridge#stampThreadIdentity 参照）。
        return createItemStack(null);
    }

    /**
     * 生成者（儀式クラフトの実行者）が分かる版。TF のクラフト品質を厳選のロール幅へ反映する
     * （{@link TrinityForgeBridge#stampThreadIdentity(ItemStack, Player)} 参照）。{@code crafter} が
     * {@code null} のときは {@link #createItemStack()} と同じ（quality=0 扱い）。
     */
    public ItemStack createItemStack(Player crafter) {
        ItemStack item = super.createItemStack();
        // 厳選(個体差)は「効果付きスレッドを1個作った瞬間」に決まる。EMPTY は儀式で型付きへ変換する
        // 中間素材なので厳選しない(変換後の ThreadItem 生成時に改めて抽選される)。
        // stampThreadIdentity は TF の ItemFactory#stamp(=武器/触媒と同じ入口)へ委譲し、
        // item(=このメソッド内で参照を保持している同一インスタンス)へ rollSeed/quality を刻む。
        // stamp は lore/属性も再組み立てするが、スレッドのステキーは AttributeProjection に
        // 一切マップされていない(TrinityForgeBridge#stampThreadIdentity のjavadoc参照)ので実害は無く、
        // lore はこの直後の editMeta で必ず上書きする。
        ThreadIdentity identity = threadType.hasEffect()
                ? TrinityForgeBridge.stampThreadIdentity(item, crafter).orElse(ThreadIdentity.NONE)
                : ThreadIdentity.NONE;
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                ItemKeys.THREAD_ITEM_TYPE, PersistentDataType.STRING, threadType.getId()
            );

            List<Component> lore = new ArrayList<>();
            if (threadType.hasEffect()) {
                lore.addAll(com.arspaper.ArsPaper.getInstance().getThreadConfig().getEffectLore(threadType));
            } else {
                lore.add(Component.text("儀式で効果付きスレッドに変換できます", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            }
            lore.addAll(rollLore(threadType, identity));
            lore.add(Component.text("防具のスレッドスロットにセット可能", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return item;
    }

    /**
     * 厳選結果の lore 行。<b>整形は TF の {@code LoreComposer} に丸投げする</b>
     * ({@link TrinityForgeBridge#threadStatLore})ので、表示名/アイコン/桁数/単位/色/カテゴリ順は
     * TF 装備の lore と必ず一致する。
     * {@code identity} が {@link ThreadIdentity#NONE} でも(rollSeed=0, quality=0の)ステは
     * 決定的に解決されるので、必ず fixed 分だけは表示される。
     *
     * <p><b>2026-08-04 の修正</b>: 旧実装は表示名だけを1件ずつ引いて {@code "  ・ " + label + " " + value}
     * を自前で組んでいた。引き当てが canonical 化の食い違いで<b>常に失敗していた</b>ため
     * フォールバックが働き、実機ではステータスidが素で並んでいた(依頼#46)。加えて成功しても
     * 黄色1色・アイコン無し・テンプレート無視で TF 装備と体裁が揃わなかった。連結は復活させないこと。
     *
     * @param type     ステを解決するための material/CMD 供給元（スレッドの種類）
     * @param identity そのスレッド個体の rollSeed + quality
     */
    public static List<Component> rollLore(ThreadType type, ThreadIdentity identity) {
        if (type == null || identity == null || !type.hasEffect()) {
            return List.of();
        }
        Map<String, Double> stats = TrinityForgeBridge.resolveThreadStats(
                type.getBaseMaterial(), type.getCustomModelData(), identity.quality(), identity.rollSeed());
        if (stats.isEmpty()) {
            return List.of();
        }
        return TrinityForgeBridge.threadStatLore(stats);
    }

    public ThreadType getThreadType() {
        return threadType;
    }
}
