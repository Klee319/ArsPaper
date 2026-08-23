package com.arspaper.block.impl;

import com.arspaper.ArsPaper;
import com.arspaper.block.BlockKeys;
import com.arspaper.block.CustomBlock;
import com.arspaper.block.SourceJarConfig;
import com.arspaper.item.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Source Jar - Sourceを貯蔵するブロック。
 * バニラのBARREL（樽）をベースに使用。
 * 最大10,000 Sourceを貯蔵可能（sourcejars.yml で上書き可）。
 *
 * 右クリックで貯蔵量を確認。
 */
public class SourceJar extends CustomBlock {

    /** 設定未読込時のフォールバック。実行時は {@link #maxSource()} を使う。 */
    public static final int MAX_SOURCE = SourceJarConfig.FALLBACK_CAPACITY;

    private static volatile int configuredMaxSource = MAX_SOURCE;

    public SourceJar(JavaPlugin plugin) {
        super(plugin, "source_jar");
    }

    /**
     * 上位ジャー用。sourcejars.yml の任意のidで同じ実装を使い回す。
     *
     * <p>見た目 (material / display-name / CMD / lore) と容量は各インスタンスが自身のidで
     * {@code jars.<id>} を引くので、yml に足すだけで反映される。カスタムソースリンクの
     * {@code Sourcelink(plugin, id)} と同じ形。
     */
    public SourceJar(JavaPlugin plugin, String id) {
        super(plugin, id);
    }

    /**
     * sourcejars.yml の {@code source_jar} の capacity を「idが引けなかったとき」の
     * フォールバックとして保持する。ジャーごとの実容量は {@link #maxSource(TileState)}。
     */
    public static void applyConfiguredCapacity(int capacity) {
        configuredMaxSource = capacity < 0 ? Integer.MAX_VALUE : Math.max(1, capacity);
    }

    /** フォールバック容量。ブロックが分かっているなら {@link #maxSource(TileState)} を使うこと。 */
    public static int maxSource() {
        return configuredMaxSource;
    }

    /**
     * そのブロック個体の容量。PDC のジャーid ({@code custom_block_id}) から sourcejars.yml を引く。
     *
     * <p>2026-07-31 追加。以前は容量が static 1 値だったため、yml に上位ジャーを足しても
     * 全ジャーが同じ容量で頭打ちになり「上位ジャー階梯」が成立しなかった。
     * id が引けない場合(設定から消えた/旧データ)はフォールバックへ落とす — 既に置かれている
     * ブロックを無容量にして中身を消すよりは、既定容量で動かし続ける方が安全。
     *
     * <p>クリエイティブジャーは {@code custom_block_id} を意図的に {@code "source_jar"} で
     * 書いている(ジャーとして扱わせるため)が、無限判定は {@code SOURCE_INFINITE} フラグ側で
     * 行うのでここで通常容量に解決されても問題ない。
     */
    public static int maxSource(TileState tileState) {
        ArsPaper ars = ArsPaper.getInstance();
        if (ars == null || ars.getSourceJarConfig() == null) {
            return configuredMaxSource;
        }
        String blockId = tileState.getPersistentDataContainer()
                .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
        if (blockId == null) {
            return configuredMaxSource;
        }
        return ars.getSourceJarConfig().get(blockId)
                .map(SourceJarConfig.JarDef::effectiveCapacity)
                .orElse(configuredMaxSource);
    }

    /**
     * そのジャー個体が満杯か。<b>投入経路はすべてここを通すこと</b>。
     *
     * <p>2026-08-08 追加。以前はホッパー投入だけが static 定数 {@link #MAX_SOURCE}
     * (=設定未読込時のフォールバック 10,000) と比べており、手投入だけが
     * {@link #maxSource(TileState)} を見ていた。そのため上位ジャーでは
     * 「手では入るのにホッパーでは 10,000 で止まる」というズレが出る。
     * 容量を解決する場所を1箇所に畳んで、経路ごとに食い違えないようにする。
     *
     * <p>無限ジャー(クリエイティブ)は常に {@code false}。
     */
    public static boolean isFull(TileState tileState) {
        if (isInfinite(tileState)) {
            return false;
        }
        return getSourceAmount(tileState) >= maxSource(tileState);
    }

    /**
     * sourcejars.yml に定義済みのジャーidかどうか。儀式・ソースリンク・パーティクルの
     * 「これはジャーか」判定はすべてここを通す({@code "source_jar"} の決め打ちを置き換えた)。
     * 設定が読めない場合は既定の2種だけを真とみなし、最低限の互換を保つ。
     */
    public static boolean isSourceJarId(String blockId) {
        if (blockId == null) {
            return false;
        }
        ArsPaper ars = ArsPaper.getInstance();
        if (ars == null || ars.getSourceJarConfig() == null) {
            return "source_jar".equals(blockId) || "creative_source_jar".equals(blockId);
        }
        return ars.getSourceJarConfig().isJar(blockId);
    }

    private Optional<SourceJarConfig.JarDef> jarDef() {
        ArsPaper ars = ArsPaper.getInstance();
        if (ars == null || ars.getSourceJarConfig() == null) {
            return Optional.empty();
        }
        return ars.getSourceJarConfig().get(getItemId());
    }

    @Override
    public Material getBlockMaterial() {
        return jarDef().map(SourceJarConfig.JarDef::material).orElse(Material.DECORATED_POT);
    }

    @Override
    public Component getDisplayName() {
        // sourcejars.yml の display-name はレガシー &記法 (例 "&bソースジャー II")。
        // Component.text(生文字列) で包むと "&b" がそのまま名前に出る(2026-08-03 実バグ)ため、
        // 書式解釈は DisplayText 1本へ寄せる。
        return jarDef()
                .map(d -> com.arspaper.util.DisplayText.component(d.displayName()))
                .orElse(Component.text("ソースジャー", NamedTextColor.BLUE)
                        .decoration(TextDecoration.ITALIC, false));
    }

    @Override
    public int getCustomModelData() {
        return jarDef().map(SourceJarConfig.JarDef::customModelData).orElse(200002);
    }

    private List<Component> flavorLore() {
        Optional<SourceJarConfig.JarDef> def = jarDef();
        if (def.isPresent() && !def.get().lore().isEmpty()) {
            List<Component> lines = new ArrayList<>(def.get().lore().size());
            for (String line : def.get().lore()) {
                // 色指定が書かれていればそれを尊重し、無ければ従来どおり灰色。
                lines.add(com.arspaper.util.DisplayText.hasMarkup(line)
                        ? com.arspaper.util.DisplayText.component(line)
                        : Component.text(line, NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false));
            }
            return lines;
        }
        return List.of(Component.text("魔法のソースエネルギーを貯蔵", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
    }

    /** このジャー種別の容量。アイテム状態(TileStateが無い)でも正しい上限を lore に出すために使う。 */
    private int declaredCapacity() {
        return jarDef().map(SourceJarConfig.JarDef::effectiveCapacity).orElse(maxSource());
    }

    private List<Component> loreWithSource(int source) {
        List<Component> lore = new ArrayList<>(flavorLore());
        lore.add(Component.text("ソース: " + source + " / " + declaredCapacity(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        return lore;
    }

    @Override
    public ItemStack createItemStack() {
        ItemStack item = super.createItemStack();
        item.editMeta(meta -> meta.lore(loreWithSource(0)));
        return item;
    }

    @Override
    public ItemStack getDisplayHeadItem() {
        ItemStack head = new ItemStack(Material.BLUE_STAINED_GLASS);
        head.editMeta(meta -> meta.setCustomModelData(getCustomModelData()));
        return head;
    }

    /** Source量をItemStackのPDCに保存するキー */
    private static final NamespacedKey ITEM_SOURCE_KEY = new NamespacedKey("arspaper", "stored_source");

    @Override
    public void onBlockPlaced(Player player, Block block, TileState tileState) {
        // 設置に使ったアイテムからSource量を復元
        // BlockPlaceEvent時点ではアイテムが既に消費されている場合があるため、
        // 両手をチェックし、ITEM_SOURCE_KEYを持つアイテムを探す
        int restoredSource = 0;
        for (ItemStack hand : new ItemStack[]{
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand()}) {
            if (hand != null && hand.hasItemMeta()) {
                Integer stored = hand.getItemMeta().getPersistentDataContainer()
                    .get(ITEM_SOURCE_KEY, PersistentDataType.INTEGER);
                if (stored != null && stored > 0) {
                    restoredSource = stored;
                    break;
                }
            }
        }
        tileState.getPersistentDataContainer().set(
            BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, restoredSource
        );
        tileState.update();
    }

    @Override
    public ItemStack createDropWithData(TileState tileState) {
        ItemStack drop = super.createDropWithData(tileState);
        int source = getSourceAmount(tileState);
        drop.editMeta(meta -> {
            // Source量をアイテムPDCに保存
            meta.getPersistentDataContainer().set(
                ITEM_SOURCE_KEY, PersistentDataType.INTEGER, source
            );
            meta.lore(loreWithSource(source));
        });
        return drop;
    }

    /** ソースベリー1個あたりのSource追加量 */
    public static final int SOURCE_PER_BERRY = 100;

    /**
     * 1回の右クリックで流し込めるソースベリーの個数を返す(2026-08-23 / W-189)。
     *
     * <p>「入れたい個数」か「ジャーがあふれる一段階前」の<b>小さい方</b>。
     * 端数は必ず切り捨てる —— {@code room} が 1 個ぶんに満たない状態で 1 個消費すると、
     * 入り切らなかったぶんが黙って消える。プレイヤーからは「ベリーが1個消えた」としか
     * 見えず、ログにも何も出ない種類の損失になる。
     *
     * <p>「入れたい個数」を決めるのは {@link com.arspaper.source.BulkFeed}（スニーク = 手持ち全部 /
     * 通常 = 1個）。ここは容器側の頭打ちだけを担当する。
     *
     * @param handAmount    入れたい個数（{@code BulkFeed#count} の戻り値）
     * @param room          ジャーの残り容量(= 最大 - 現在値)
     * @return 消費してよい個数。0 なら「残り容量が1個ぶんに足りない」
     */
    static int convertibleBerries(int handAmount, int room) {
        if (handAmount <= 0 || room <= 0) {
            return 0;
        }
        return Math.min(handAmount, room / SOURCE_PER_BERRY);
    }

    @Override
    public void onBlockInteract(Player player, Block block, TileState tileState) {
        // ソースベリーを持っている場合: Source追加
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.hasItemMeta()) {
            String customId = hand.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
            if ("source_berry".equals(customId) && !isInfinite(tileState)) {
                int currentSource = getSourceAmount(tileState);
                if (isFull(tileState)) {
                    player.sendMessage(Component.text("ソースジャーは満タンです", NamedTextColor.YELLOW));
                    return;
                }

                // 【一括変換 2026-08-23 (W-189)】以前は1回の右クリックで1個だけ消費していたため、
                // 上位ジャー(容量200万〜5,000万)では現実的に連打しきれなかった
                // (2,000,000 / 100 = 20,000 回)。スタックを一度に流し込めるようにした。
                //
                // 【2026-08-24】一括の合図をソースリンク側と揃えて BulkFeed へ集約
                // ── スニーク = 手持ち全部 / 通常 = 1個。以前は無条件で一括だったので、
                //    1個だけ入れて残量を微調整する手段が無かった。
                //
                // ⚠ 端数を切り捨てて「あふれる一段階前」で止める ── ceil にすると最後の1個が
                //   部分的にしか入らず、余ったソースが無言で消える(ユーザーから見れば
                //   「ベリーが1個消えた」だけになる)。
                int room = maxSource(tileState) - currentSource;
                int convertible = convertibleBerries(
                    com.arspaper.source.BulkFeed.count(hand.getAmount(), player.isSneaking()), room);
                if (convertible <= 0) {
                    // 残り容量が 1個ぶんに満たない = 実質満タン。消費せずに知らせる。
                    player.sendMessage(Component.text(
                        "残り容量がソースベリー1個ぶん(" + SOURCE_PER_BERRY + ")に足りません ("
                            + currentSource + "/" + maxSource(tileState) + ")",
                        NamedTextColor.YELLOW));
                    return;
                }

                // ベリー消費 + Source追加
                hand.setAmount(hand.getAmount() - convertible);
                int added = setSourceAmount(
                    tileState, currentSource + SOURCE_PER_BERRY * convertible) - currentSource;
                player.sendMessage(Component.text(
                    "ソースベリー" + convertible + "個でソースを" + added + "追加しました ("
                        + getSourceAmount(tileState) + "/" + maxSource(tileState) + ")",
                    NamedTextColor.AQUA));
                player.playSound(player.getLocation(),
                    org.bukkit.Sound.BLOCK_BREWING_STAND_BREW,
                    org.bukkit.SoundCategory.BLOCKS, 0.5f, 1.5f);
                player.getWorld().spawnParticle(org.bukkit.Particle.END_ROD,
                    block.getLocation().add(0.5, 1.0, 0.5), 8, 0.2, 0.3, 0.2, 0.03);
                return;
            }
        }

        // 通常の右クリック: 貯蔵量表示
        if (isInfinite(tileState)) {
            player.sendMessage(Component.text(
                "ソース: \u221E (無限)", NamedTextColor.LIGHT_PURPLE
            ));
        } else {
            int source = getSourceAmount(tileState);
            player.sendMessage(Component.text(
                "ソース: " + source + " / " + maxSource(tileState), NamedTextColor.AQUA
            ));
        }
    }

    /**
     * TileStateが無限ソースかどうかを判定する。
     */
    public static boolean isInfinite(TileState tileState) {
        return tileState.getPersistentDataContainer()
            .getOrDefault(BlockKeys.SOURCE_INFINITE, PersistentDataType.BYTE, (byte) 0) != 0;
    }

    /**
     * TileStateからSource量を取得。無限の場合は常にmaxSource()。
     */
    public static int getSourceAmount(TileState tileState) {
        if (isInfinite(tileState)) return maxSource(tileState);
        return tileState.getPersistentDataContainer()
            .getOrDefault(BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, 0);
    }

    /**
     * TileStateにSource量を設定。
     *
     * @return 実際に設定された量
     */
    public static int setSourceAmount(TileState tileState, int amount) {
        int clamped = Math.clamp(amount, 0, maxSource(tileState));
        tileState.getPersistentDataContainer().set(
            BlockKeys.SOURCE_AMOUNT, PersistentDataType.INTEGER, clamped
        );
        tileState.update();
        return clamped;
    }

    /**
     * Sourceを追加する。
     *
     * @return 実際に追加された量（溢れ分は返さない）
     */
    public static int addSource(TileState tileState, int amount) {
        int current = getSourceAmount(tileState);
        int added = Math.min(amount, maxSource(tileState) - current);
        if (added > 0) {
            setSourceAmount(tileState, current + added);
        }
        return added;
    }

    /**
     * Sourceを消費する。無限の場合は減らさずに成功を返す。
     *
     * @return 消費に成功したかどうか
     */
    public static boolean consumeSource(TileState tileState, int amount) {
        if (isInfinite(tileState)) return true;
        int current = getSourceAmount(tileState);
        if (current < amount) return false;
        setSourceAmount(tileState, current - amount);
        return true;
    }
}
