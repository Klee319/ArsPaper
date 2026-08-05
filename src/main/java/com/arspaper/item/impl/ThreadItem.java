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
            meta.lore(fullLore(meta, threadType, identity));
        });
        return item;
    }

    /**
     * スレッドアイテムの lore <b>全体</b>を組む。スレッドの lore を書き換える経路は
     * 生成({@link #createItemStack(Player)})・返却({@code ThreadGui#restoreRoll})・
     * 振り直し({@code ThreadRerollRitualEffect})の3つあり、<b>全部この1本でまるごと組み直す</b>。
     *
     * <p><b>部分書き換え(「前回の行を内容一致で消してから足す」)へ戻さないこと</b>:
     * ステ部分は装備と同じ体裁になり幅可変の区切り線を含む(2026-08-05 の要望)。区切り線の幅は
     * そのときの最長行で決まるので、値の桁が変わった瞬間に古い線が一致せず消えずに溜まる
     * (2026-08-05 に一度踏んだ実害そのもの)。まるごと組み直せば桁が変わっても溜まらない。
     *
     * <p>バックパックデータ行は PDC を見て {@link com.arspaper.gui.BackpackGui#appendItemDataLore}
     * が足す — 組み直しで落とすと「中身は残っているのに表示だけ消える」ため。
     */
    public static List<Component> fullLore(org.bukkit.inventory.meta.ItemMeta meta,
                                           ThreadType type, ThreadIdentity identity) {
        List<Component> lore = new ArrayList<>();
        if (type != null && type.hasEffect()) {
            lore.addAll(com.arspaper.ArsPaper.getInstance().getThreadConfig().getEffectLore(type));
        } else {
            lore.add(Component.text("儀式で効果付きスレッドに変換できます", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }
        lore.addAll(equipmentStyleRollLore(type, identity));
        lore.add(Component.text("防具のスレッドスロットにセット可能", NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false));
        com.arspaper.gui.BackpackGui.appendItemDataLore(meta, lore);
        return lore;
    }

    /**
     * 厳選結果を<b>装備とまったく同じ体裁</b>で組んだ lore 行(品質行【名匠】…pt / カテゴリ区切り線 /
     * ロール色つき)。2026-08-05 の要望「スレッドに表記するステータスの lore の体裁とフォントを
     * 通常の装備と同じにしてほしい」への対応で、TF の装備経路そのもの
     * ({@code ItemAssembler#statLoreBlock})へ丸投げしている。
     *
     * <p>差し込み用の {@link #rollLore}(区切り線・品質行なし)との使い分け:
     * <b>まるごと組み直す先だけ</b>こちらを使う。他アイテムの lore へ差し込む/チャットへ流す用途は
     * {@link #rollLore} のまま(区切り線が差し込み先に溜まる、チャットで無駄に幅を取る)。
     */
    public static List<Component> equipmentStyleRollLore(ThreadType type, ThreadIdentity identity) {
        if (type == null || identity == null || !type.hasEffect()) {
            return List.of();
        }
        return TrinityForgeBridge.threadEquipmentStyleLore(
                type.getBaseMaterial(), type.getCustomModelData(),
                identity.quality(), identity.rollSeed());
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
     * <p><b>返る行に区切り線({@code ====})が混ざってはいけない</b>: この結果は
     * {@link com.arspaper.gui.ThreadGui} のスレッド返却と
     * {@link com.arspaper.ritual.effect.ThreadRerollRitualEffect} が<b>「前回の行を内容一致で消してから
     * 新しい行を足す」</b>形で使う。区切り線の幅は「そのときの最長行」で決まるので、値の桁が変わると
     * 古い線が消えずに溜まる。だから {@code threadStatLore} は TF の
     * {@code LoreComposer#statLines}(区切り線なし)を使っている。
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
