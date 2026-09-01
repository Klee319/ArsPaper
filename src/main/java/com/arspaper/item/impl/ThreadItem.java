package com.arspaper.item.impl;

import com.arspaper.integration.TrinityForgeBridge;
import com.arspaper.integration.TrinityForgeBridge.ThreadIdentity;
import com.arspaper.item.BaseCustomItem;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.ThreadType;
import com.arspaper.util.PdcHelper;
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
import java.util.concurrent.ThreadLocalRandom;

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
        // 付与等）のフォールバック。PDC は意図的に未刻印のまま返す(下の createItemStack(Player) の
        // javadoc「W-53」節参照)。
        return createItemStack(null);
    }

    /**
     * 生成者（儀式クラフトの実行者）が分かる版。TF のクラフト品質を厳選のロール幅へ反映する
     * （{@link TrinityForgeBridge#stampThreadIdentity(ItemStack, Player)} 参照）。
     *
     * <p><b>⚠️ 2026-08-18 (W-53) crafter が {@code null} のとき、PDC は意図的に未刻印のまま返す。</b>
     * 以前は {@code crafter == null} でも {@link TrinityForgeBridge#stampThreadIdentity} を呼び、
     * rollSeed を新規発番しつつ quality=0 固定で刻んでいた。しかし TF の
     * {@code ItemData#hasRollSeed()} は PDC キーの<b>有無</b>だけを見る(値が0でも「刻印済み」扱い)ため、
     * この時点で刻んでしまうと TF {@code PickupQualityListener#stampIfEligible} の
     * {@code data.hasRollSeed()} ガードに永久に引っかかり、開運(loot-luck)ベースの品質ロールに
     * 二度と到達できなくなっていた(ルートチェスト/ダンジョンドロップ経由のスレッドが恒久的に
     * 品質0で固定される実害)。crafter が判明する経路(儀式クラフト)だけ即時に刻印し、それ以外は
     * {@link ThreadIdentity#NONE} 相当(未刻印)のまま返す ── 品質決定は TF 側の通常ドロップ品経路
     * ({@code PickupQualityListener})、または管理者が明示指定した場合は {@link #restampWithQuality}
     * (TF {@code GiveItemCommand} が reflection 経由で呼ぶ)に委ねる。
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
        // crafter == null(生成者不明経路)ではあえて刻まない ── 上のメソッドjavadoc「W-53」参照。
        ThreadIdentity identity = (threadType.hasEffect() && crafter != null)
                ? TrinityForgeBridge.stampThreadIdentity(item, crafter).orElse(ThreadIdentity.NONE)
                : ThreadIdentity.NONE;
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                ItemKeys.THREAD_ITEM_TYPE, PersistentDataType.STRING, threadType.getId()
            );
            // catalog の bind-type を PDC へ写す(rollSeed は書かない ── W-53)。
            // SOULBOUND なら PickupQualityListener / ThreadSoulbindListener が入手時に所有者を付ける。
            TrinityForgeBridge.applyCatalogBindType(meta, getItemId());
            meta.lore(fullLore(meta, threadType, identity));
            // 効果付きは rollSeed/品質/所有者という個体差を持つので1個ずつしか置けない。
            // EMPTY は儀式で型付きへ変える中間素材で個体差が無く、ここで1に固定すると
            // 素材が1個ごとに枠を食って重ならなくなる(2026-09-02 報告の不具合)。
            if (threadType.hasEffect()) {
                meta.setMaxStackSize(1);
            }
        });
        return item;
    }

    /**
     * 旧いカタログ作業台レシピが作った「見た目だけスレッド」の識別情報を復元する。
     *
     * <p>ArsPaper の有効化前に TrinityForge が作業台レシピを登録すると、その結果が
     * {@code trinityforge:catalog_id=thread_*} だけを持つ場合がある。通常は ArsPaper enable 後の
     * レシピ再登録で防止するが、既にクラフト済みの個体には効かない。スレッドGUIへ渡された時点で
     * Ars の2つの識別子を補い、同じ個体を安全に装着可能へ戻す。
     *
     * @return Ars の効果スレッドとして復元した場合のみ {@code true}
     */
    public static boolean restoreFunctionalMetadata(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        String existingCustomId = item.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
        String existingTypeId = item.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.THREAD_ITEM_TYPE, PersistentDataType.STRING);
        if (existingCustomId != null && existingCustomId.startsWith("thread_")
                && existingCustomId.substring("thread_".length()).equals(existingTypeId)) {
            ThreadType existingType = ThreadType.fromId(existingTypeId);
            if (existingType != null && existingType.hasEffect()) {
                return false;
            }
        }
        String itemId = PdcHelper.getCrossPluginItemId(item).orElse(null);
        if (itemId == null || !itemId.startsWith("thread_")) {
            return false;
        }
        ThreadType type = ThreadType.fromId(itemId.substring("thread_".length()));
        if (type == null || !type.hasEffect()) {
            return false;
        }
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                    ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING, itemId);
            meta.getPersistentDataContainer().set(
                    ItemKeys.THREAD_ITEM_TYPE, PersistentDataType.STRING, type.getId());
            TrinityForgeBridge.applyCatalogBindType(meta, itemId);
            meta.lore(fullLore(meta, type, TrinityForgeBridge.readThreadIdentity(item)));
            meta.setMaxStackSize(1);
        });
        return true;
    }

    /**
     * 生成者不明(crafter==null)で PDC 未刻印のまま作られたスレッドへ、後から品質を割り当て直す。
     * 呼び出し元は2つ: (1) TF {@code GiveItemCommand}(管理者が {@code /tf give} で明示指定した
     * quality)、(2) TF {@code PickupQualityListener}(開運(loot-luck)ベースでロールした quality)。
     * どちらも TF 側は ArsPaper へコンパイル依存を持てないため reflection 経由でこのメソッドへ
     * 委譲する(メソッド名/シグネチャは TF 側 reflection 呼び出しとの契約 ── 変更する場合は
     * 両方の呼び出し元を合わせて直すこと)。
     *
     * <p>{@link TrinityForgeBridge#writeItemRoll}(PDC のみ書く軽量経路)で新規 rollSeed + 指定
     * quality を書き込み、lore は必ず {@link #fullLore} で組み直す。TF の汎用装備 lore 経路
     * ({@code ItemFactory#stamp} → {@code ItemAssembler#assemble})をスレッドへそのまま適用すると、
     * 効果説明/スロット案内/バックパック行を含む専用 lore が上書きされてしまうため、この専用経路が
     * 必要(上の {@link #createItemStack(Player)} javadoc、および呼び出し元の TF 側実装コメント参照)。
     *
     * <p>効果を持たないスレッド(EMPTY 等、儀式の中間素材)は品質という概念自体が無いので対象外。
     *
     * @return 刻印を実際に行ったら {@code true}。{@code item} が null/メタ無し、または
     *         このスレッド種別が効果を持たない場合は {@code false}(何もしない、fail-open)。
     */
    public boolean restampWithQuality(ItemStack item, int quality) {
        if (item == null || !item.hasItemMeta() || !threadType.hasEffect()) {
            return false;
        }
        long rollSeed = ThreadLocalRandom.current().nextLong();
        ThreadIdentity identity = new ThreadIdentity(rollSeed, quality);
        item.editMeta(meta -> {
            TrinityForgeBridge.writeItemRoll(meta, rollSeed, quality);
            meta.lore(fullLore(meta, threadType, identity));
            meta.setMaxStackSize(1);
        });
        return true;
    }

    /**
     * 既存スレッドの lore を、今の {@code item-stats.yml} / セット効果表で組み直す。
     * {@link #restampWithQuality} と違い <b>rollSeed も quality も新規発番しない</b>
     * （品質と pt を維持したまま、主軸変更やセット閾値の折り込みを既存個体へ届ける）。
     *
     * <p>TF {@code ItemRefreshListener} が reflection で呼ぶ契約。汎用
     * {@code ItemAssembler#assemble} をスレッドへ通すと専用 lore が壊れるので、
     * テーブル世代が古くなったときの更新は必ずこちらへ委譲する。
     *
     * @return lore を組み直したら {@code true}。未刻印・効果なし・メタ無しは {@code false}。
     */
    public boolean refreshLoreKeepingIdentity(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        if (!threadType.hasEffect()) {
            // 空のスレッドは個体差を持たない素材。過去の生成で max_stack_size=1 を焼かれた個体は
            // アイテム側に上限が残るので、直しても配布済みの分が重ならない。持ち替え時に外す。
            return clearSingleStackCap(item);
        }
        com.trinityforge.pdc.ItemData data;
        try {
            data = com.trinityforge.pdc.ItemData.of(item.getItemMeta());
        } catch (Throwable tfMissing) {
            return false;
        }
        if (!data.hasRollSeed()) {
            return false;
        }
        ThreadIdentity identity = new ThreadIdentity(data.rollSeed().orElse(0L), data.quality());
        item.editMeta(meta -> {
            meta.lore(fullLore(meta, threadType, identity));
            meta.setMaxStackSize(1);
        });
        return true;
    }

    /**
     * 過去に焼き付けられた「1個までしか重ならない」上限を外す。
     *
     * <p>max_stack_size はアイテム側の components に残るので、生成コードを直しても
     * 既に配ってしまった個体には効かない。表更新の経路を通ったときに1度だけ外す。
     *
     * @return 実際に外したときだけ {@code true}（2回目以降は false になり空振りしない）
     */
    private static boolean clearSingleStackCap(ItemStack item) {
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasMaxStackSize()) {
            return false;
        }
        item.editMeta(m -> m.setMaxStackSize(null));
        return true;
    }

    /**
     * スレッドアイテムの lore <b>全体</b>を組む。スレッドの lore を書き換える経路は
     * 生成({@link #createItemStack(Player)})・返却({@code ThreadGui#restoreRoll})・
     * 振り直し({@code ThreadRerollRitualEffect})・表更新({@link #refreshLoreKeepingIdentity})の
     * 4つあり、<b>全部この1本でまるごと組み直す</b>。
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
        lore.addAll(setEffectLore(type));
        lore.add(Component.text("防具のスレッドスロットにセット可能", NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false));
        com.arspaper.gui.BackpackGui.appendItemDataLore(meta, lore);
        // このメソッドは返却・品質更新・定期リフレッシュの全経路で lore を作り直す。
        // 所有者PDCを表示へ戻さないと、PDCだけ残って「魂縛/所有者」行が定期的に消える。
        TrinityForgeBridge.appendOwnerLoreIfMissing(meta, lore);
        appendSoulboundLore(meta, lore);
        return lore;
    }

    /** Ars 側の控え台帳にも残る魂縛所有者を、専用 lore 再構築ごとに表示へ戻す。 */
    private static void appendSoulboundLore(org.bukkit.inventory.meta.ItemMeta meta, List<Component> lore) {
        if (meta == null || lore == null) {
            return;
        }
        String raw = meta.getPersistentDataContainer().get(
                ItemKeys.THREAD_SOULBOUND_OWNER, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            java.util.UUID owner = java.util.UUID.fromString(raw);
            org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(owner);
            String name = player.getName() == null ? owner.toString() : player.getName();
            lore.add(Component.text("魂縛: " + name, NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
        } catch (IllegalArgumentException ignored) {
            // 壊れた控えPDCは表示しない。使用判定側も同じく未刻印として扱う。
        }
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

    /**
     * <b>セット効果({@code thread-sets.yml})を lore へ自動注入する行</b>(2026-08-21)。
     *
     * <p>それまでスレッドの lore にはセット効果が<b>一切出ていなかった</b>。効果の実体が
     * セット効果側にしかないスレッドは、プレイヤーから見ると「厳選ステだけの微妙な品」に見えていた。
     * 設定エディタで lore を手書きして補う運用は<b>採らない</b> —— 手書きは thread-sets.yml を
     * 直した瞬間に嘘になり、しかも嘘になったことに誰も気づけないため。ここで毎回<b>設定から生成</b>する。
     *
     * <p>体裁: 「セット効果」見出しのあと、しきい値ごとに「Nセット」と
     * 全角スペースでインデントしたステ行（累積合計ではない）。ステ行の整形は
     * {@link TrinityForgeBridge#threadSetStatLore} 経由で TF の {@code LoreComposer} へ丸投げするので、
     * 表示名・アイコン・桁数・単位・色・カテゴリ順・乗算行({@code x1.10})はすべて装備 lore と一致する。
     *
     * <p>ArsPaper 単体起動 / TF 未ロード / セット効果未定義のときは空リスト(行が増えないだけ)。
     */
    public static List<Component> setEffectLore(ThreadType type) {
        if (type == null || !type.hasEffect()) {
            return List.of();
        }
        com.arspaper.item.ThreadSetConfig sets;
        try {
            sets = com.arspaper.ArsPaper.getInstance().getThreadSetConfig();
        } catch (Throwable notLoaded) {
            return List.of();
        }
        if (sets == null) {
            return List.of();
        }
        List<Integer> counts = sets.thresholds(type.getId());
        if (counts.isEmpty()) {
            return List.of();
        }
        List<Component> lore = new ArrayList<>();
        List<Component> body = new ArrayList<>();
        for (int count : counts) {
            List<Component> statLines = TrinityForgeBridge.threadSetStatLore(
                    sets.bonusAt(type.getId(), count), sets.multiplierAt(type.getId(), count));
            if (statLines.isEmpty()) {
                continue;
            }
            body.add(Component.text(count + "セット", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
            for (Component line : statLines) {
                body.add(Component.text("　")
                    .decoration(TextDecoration.ITALIC, false)
                    .append(line));
            }
        }
        if (body.isEmpty()) {
            return List.of();
        }
        lore.add(Component.text("セット効果", NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));
        lore.addAll(body);
        return lore;
    }

    public ThreadType getThreadType() {
        return threadType;
    }
}
