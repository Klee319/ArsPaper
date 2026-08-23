package com.arspaper.source.sourcelink;

import com.arspaper.ArsPaper;
import com.arspaper.block.BlockKeys;
import com.arspaper.block.CustomBlock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sourcelink（Source生成装置）の抽象基底クラス。
 * 隣接するSource Jarに生成したSourceを自動供給する。
 *
 * 燃料消費型: PDCにソースポイントを蓄積し、tickごとに排出。
 * イベント駆動型: 外部イベントで直接supplyAdjacentを呼び出す。
 */
public abstract class Sourcelink extends CustomBlock {

    /** 蓄積されたソースポイントを保持するPDCキー */
    public static final NamespacedKey SOURCE_BUFFER = new NamespacedKey("arspaper", "sourcelink_buffer");

    // 1周期あたりの最大排出量(旧 MAX_DRAIN_PER_TICK = 50)は 2026-08-01 に
    // sourcelinks.yml の transfer.sourcelink.max-per-transfer へ移設した。
    // 既定値は SourceTransferConfig.DEFAULT_SOURCELINK_MAX_PER_TRANSFER(=50、挙動不変)。

    protected Sourcelink(JavaPlugin plugin, String blockId) {
        super(plugin, blockId);
    }

    /**
     * アイテムがカスタムアイテム（PDC付き）かどうかを判定する。
     * 溶岩バケツ返却などバニラ専用副作用のガードに使う。
     * 燃料判定自体は {@link com.arspaper.item.ItemCostTable} 側で custom id も許可する。
     */
    protected static boolean isCustomItem(org.bukkit.inventory.ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
            .has(com.arspaper.item.ItemKeys.CUSTOM_ITEM_ID, PersistentDataType.STRING);
    }

    /**
     * 1回のティックで供給へ回すSource量を、<b>バッファ由来</b>と<b>受動生成</b>に分けて返す。
     * ブロックのTileStateを参照してバッファから排出等の判断が可能。
     * {@link SourceYield#NONE}(総量0)を返すと生成しない。
     *
     * <p>⚠ <b>5実装で規約は1つ</b>: {@link #drainBuffer} で引いた分は
     * {@link SourceYield#fromBuffer()} に、それ以外(バイタリックの常時生成のような
     * 無から湧く分)は {@link SourceYield#passive()} に入れること。
     * 注ぎ切れなかった残量のうちバッファへ戻せるのは前者だけで、後者は捨てられる
     * ({@link SourceYield#refundToBuffer} 参照)。ここを取り違えると
     * 「ジャーを外して放置するだけでバッファが無限に増える」抜け穴になる。
     */
    public abstract SourceYield generateSource(Block block);

    /**
     * 指定アイテムのソースポイント値を返す。対応しないアイテムは0。
     * ホッパー連携で使用。
     */
    public int getSourceValueForItem(org.bukkit.inventory.ItemStack item) {
        return 0;
    }

    /**
     * 1回の右クリックで焼べる個数（2026-08-24 要望「ソースベリーと同様に一括でくべたい」）。
     *
     * <p><b>通常の右クリック = 手持ちスタック全部 / スニーク = 1個。</b>
     * ソースジャーへのソースベリー投入（{@link com.arspaper.block.impl.SourceJar#convertibleBerries}）は
     * 無条件で一括なのに、ソースリンクは逆に<b>スニークしたときだけ一括</b>だったため、
     * 同じ「くべる」操作で挙動が食い違っていた。さらにヴォルカニックの燃料は原木や石炭ブロックのような
     * <b>設置できるアイテム</b>が多く、スニーク＋右クリックはそもそも設置操作と競合するので、
     * 一括側をスニークに置くのは操作としても無理があった。
     *
     * <p>1個だけ焼べる手段は<b>残す</b>（スニーク側へ移した）。上位ソースリンクの燃料は1個あたりの
     * 価値が桁違い（例 {@code custom:source_engine} = 3,000万）で、握ったまま右クリックした瞬間に
     * スタック全部が消えるのを避ける逃げ道が要る。
     *
     * <p>ジャー側と違って「あふれる一段階手前で止める」上限は掛けない。ソースリンクのバッファ上限は
     * 既定が int 上限＝実質無制限で、{@link #addToBuffer} がクランプした分は警告ログに残るため、
     * ジャーのような「無言で消える」損失にはならない。
     *
     * @param handAmount 手に持っているスタックの個数
     * @param sneaking   スニーク中か
     * @return 消費してよい個数（手持ちを超えない）
     */
    public static int feedCount(int handAmount, boolean sneaking) {
        if (handAmount <= 0) {
            return 0;
        }
        return sneaking ? 1 : handAmount;
    }

    /**
     * Source生成時の追加処理（副産物の生成など）。
     */
    public void onGenerate(Block block) {}

    /**
     * バッファからソースポイントを取得する。
     */
    protected int getBuffer(TileState tile) {
        return tile.getPersistentDataContainer()
            .getOrDefault(SOURCE_BUFFER, PersistentDataType.INTEGER, 0);
    }

    /**
     * バッファにソースポイントを設定する。
     */
    protected void setBuffer(TileState tile, int amount) {
        tile.getPersistentDataContainer().set(SOURCE_BUFFER, PersistentDataType.INTEGER, amount);
        tile.update();
    }

    /**
     * バッファにソースポイントを追加する。
     *
     * <p>⚠ 2026-08-01 修正: 以前は {@code getBuffer(tile) + amount} を int で行っており、
     * 高価値燃料({@code custom:source_engine} = 3000万)を焼べ続けると約2.1億で
     * <b>オーバーフローして負値になり、蓄積が無言で全損</b>していた。
     * long で計算して {@code transfer.sourcelink.buffer-cap}(既定 = int上限)へクランプする。
     * クランプで捨てた分はログに残す(素材は既に消費されているため、黙って消さない)。
     *
     * <p>⚠ 2026-08-01 追加: 警告は<b>同じブロックにつき1回だけ</b>出す({@link #BUFFER_CAP_WARNED} のラッチ)。
     * {@code buffer-cap} を有限にすると「上限に張り付いたまま毎周期クランプする」のが定常状態になり、
     * ラッチ無しだと既定100tick周期で1台あたり日864行の同一警告がログを埋める。
     * 一度上限を下回れば解除され、次に上限へ達したときにまた1回だけ出る。
     */
    public void addToBuffer(Block block, int amount) {
        if (!(block.getState() instanceof TileState tile)) return;
        if (amount <= 0) return;
        int current = getBuffer(tile);
        int cap = effectiveBufferCap(block);
        int next = com.arspaper.source.SourceTransferConfig.clampBuffer(current, amount, cap);
        long lost = (long) current + (long) amount - (long) next;
        Location key = block.getLocation();
        if (lost > 0L) {
            if (BUFFER_CAP_WARNED.add(key)) {
                plugin.getLogger().warning("Sourcelink buffer capped at " + cap + " ("
                        + getItemId() + " @ " + block.getX() + "," + block.getY() + "," + block.getZ()
                        + "): discarded " + lost + " source points"
                        + " (this warning is latched per block until it drops below the cap)");
            }
        } else {
            BUFFER_CAP_WARNED.remove(key);
        }
        setBuffer(tile, next);
    }

    /**
     * buffer-cap 到達の警告を既に出したブロック。設置座標そのものをキーにするので、
     * ソースリンクの種別を跨いで衝突しない。
     */
    private static final java.util.Set<Location> BUFFER_CAP_WARNED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** ソースリンクが撤去された/失われたときにラッチを解放する({@code SourcelinkTickTask} から呼ぶ)。 */
    public static void forgetBufferCapWarning(Location location) {
        if (location != null) {
            BUFFER_CAP_WARNED.remove(location.getBlock().getLocation());
        }
    }

    /**
     * バッファから1周期あたりの上限({@code transfer.sourcelink.max-per-transfer})まで排出する。
     * 排出量を返す。
     */
    protected int drainBuffer(Block block) {
        if (!(block.getState() instanceof TileState tile)) return 0;
        int buffer = getBuffer(tile);
        if (buffer <= 0) return 0;
        int drain = Math.min(buffer, effectiveMaxPerTransfer(block));
        setBuffer(tile, buffer - drain);
        return drain;
    }

    /**
     * 転送設定。ArsPaper 未初期化(テスト/早期呼び出し)でも落ちないよう既定値へフォールバックする。
     */
    protected static com.arspaper.source.SourceTransferConfig transferConfig() {
        ArsPaper ars = ArsPaper.getInstance();
        if (ars == null || ars.getSourcelinkConfig() == null) {
            return com.arspaper.source.SourceTransferConfig.defaults();
        }
        return ars.getSourcelinkConfig().transfer();
    }

    /**
     * {@code block} の位置が設置済み {@code infinity_source_core} の半径内かどうか。
     * 2026-08-01 確定仕様 柱6: マルチブロックのパターン判定はせず、半径判定だけで成立させる。
     * トラッカー未初期化(テスト/早期呼び出し)では常に false(補正なし)。
     */
    private static boolean withinInfinityCoreRadius(Block block, int radius) {
        ArsPaper ars = ArsPaper.getInstance();
        if (ars == null) return false;
        com.arspaper.source.InfinityCoreTracker tracker = ars.getInfinityCoreTracker();
        if (tracker == null) return false;
        return tracker.isWithinRadiusOfAny(block.getLocation(), radius);
    }

    /**
     * {@code transfer.sourcelink.buffer-cap} に、半径内であれば
     * {@code transfer.infinity-core.buffer-multiplier} を掛けた値。
     *
     * <p>⚠ 倍率適用後の値も {@link #addToBuffer} 内の {@code clampBuffer}(オーバーフロー安全)を
     * 必ず通す ―― ここで返すのは「掛け算した cap」であって、加算結果そのものではない。
     */
    private static int effectiveBufferCap(Block block) {
        com.arspaper.source.SourceTransferConfig cfg = transferConfig();
        int base = cfg.sourcelinkBufferCap();
        if (!withinInfinityCoreRadius(block, cfg.infinityCoreRadius())) return base;
        return com.arspaper.source.InfinityCoreEffect.scaleCap(base, cfg.infinityCoreBufferMultiplier());
    }

    /**
     * {@code transfer.sourcelink.max-per-transfer} に、まず自分の {@code items.<id>.transfer-multiplier}
     * (階梯レート。K-16対応、2026-08-02)を掛け、さらに infinity_source_core の半径内であれば
     * {@code transfer.infinity-core.transfer-multiplier} を重ねて掛けた値。
     *
     * <p>⚠ K-16 の元の問題は「階梯(volcanic/mycelial/...)を上げても容量(buffer-cap)は伸びるが
     * <b>転送レート</b>は伸びず、レートを上げる唯一の手段が『ソースリンクの台数を並べる』ことだった」点。
     * ここで {@code transferMultiplier} を先に掛けることで、上位ソースリンク(例: {@code *_ii}/{@code *_iii})
     * 単体でレートそのものが上がるようにする。buffer-cap 側は元々 int上限=実質無制限なので、
     * 階梯による倍率はレート(このメソッド)にだけ掛ける。
     */
    private int effectiveMaxPerTransfer(Block block) {
        com.arspaper.source.SourceTransferConfig cfg = transferConfig();
        int base = cfg.sourcelinkMaxPerTransfer();
        double tierMultiplier = itemDef()
                .map(SourcelinkConfig.ItemDef::transferMultiplier)
                .orElse(1.0);
        int tiered = com.arspaper.source.InfinityCoreEffect.scaleCap(base, tierMultiplier);
        if (!withinInfinityCoreRadius(block, cfg.infinityCoreRadius())) return tiered;
        return com.arspaper.source.InfinityCoreEffect.scaleCap(tiered, cfg.infinityCoreTransferMultiplier());
    }

    /**
     * <b>新しく生まれた</b>ソース量に自分の {@code items.<id>.yield-multiplier}(階梯の生成量倍率、
     * 2026-08-03)を掛ける。燃料投入・受動生成・成長/撃破ボーナスの各生成点から呼ぶ。
     *
     * <p>⚠ {@link #addToBuffer} の中では掛けない —— あちらは
     * {@code SourceYield#refundToBuffer} の<b>返却</b>からも呼ばれるので、掛けると
     * 「隣接ジャーが満杯の間だけソースが毎周期増える」増殖になる
     * (理由の全文は {@link com.arspaper.source.SourceGenerationScaling})。
     *
     * @param rawAmount 倍率適用前の生成量。単価3000万×64個が int を越えるため long で受ける。
     */
    public int scaleGeneratedYield(long rawAmount) {
        double multiplier = itemDef()
                .map(SourcelinkConfig.ItemDef::yieldMultiplier)
                .orElse(1.0);
        return com.arspaper.source.SourceGenerationScaling.scaleYield(rawAmount, multiplier);
    }

    /**
     * 隣接するSource JarにSourceを供給する。
     * 供給成功時にパーティクルとサウンドを再生。
     *
     * <p>⚠ 戻り値は<b>注ぎ切れなかった残量</b>。隣接ジャーが満杯/不在だと注げない。
     * このうち<b>バッファから引いた分だけ</b>を呼び出し元がバッファへ戻す
     * ({@link SourceYield#refundToBuffer} — 転送速度
     * {@code transfer.sourcelink.max-per-transfer} を上げるほど
     * 「ジャーの空きより多く排出する」状況が普通になるため、この返却は必須)。
     * <b>受動生成分は戻さず捨てる</b>(ジャーの容量を実質的な上限として働かせる元設計の復元)。
     */
    public int supplyAdjacent(Block block, int amount) {
        Location loc = block.getLocation();
        int[][] offsets = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};

        int remaining = amount;
        for (int[] offset : offsets) {
            if (remaining <= 0) break;

            Block adjacent = loc.getWorld().getBlockAt(
                loc.getBlockX() + offset[0],
                loc.getBlockY() + offset[1],
                loc.getBlockZ() + offset[2]
            );

            if (!(adjacent.getState() instanceof TileState tile)) continue;

            String blockId = tile.getPersistentDataContainer()
                .get(BlockKeys.CUSTOM_BLOCK_ID, PersistentDataType.STRING);
            // 2026-07-31: 上位ジャーへも注げるように sourcejars.yml 定義の全ジャーへ拡張。
            if (!com.arspaper.block.impl.SourceJar.isSourceJarId(blockId)) continue;

            // 無限ソースジャーはスキップ（書き込み不要）
            if (com.arspaper.block.impl.SourceJar.isInfinite(tile)) continue;

            int added = com.arspaper.block.impl.SourceJar.addSource(tile, remaining);
            if (added > 0) {
                remaining -= added;
                // ソース瓶に溜まるエフェクト
                spawnJarFillFx(adjacent.getLocation());
            }
        }
        return remaining;
    }

    /**
     * ソース瓶にソースが供給された時のパーティクル＆サウンド。
     */
    private void spawnJarFillFx(Location jarLoc) {
        Location center = jarLoc.clone().add(0.5, 0.8, 0.5);
        jarLoc.getWorld().spawnParticle(
            Particle.ENCHANT, center, 8, 0.2, 0.3, 0.2, 0.5);
        jarLoc.getWorld().spawnParticle(
            Particle.WITCH, center, 4, 0.15, 0.2, 0.15, 0.02);
        jarLoc.getWorld().playSound(center,
            Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.4f, 1.4f);
    }

    /** sourcelinks.yml {@code items.<id>} — 未定義なら empty。 */
    protected Optional<SourcelinkConfig.ItemDef> itemDef() {
        ArsPaper ars = ArsPaper.getInstance();
        if (ars == null || ars.getSourcelinkConfig() == null) {
            return Optional.empty();
        }
        return ars.getSourcelinkConfig().item(getItemId());
    }

    protected Material materialOr(Material fallback) {
        return itemDef().map(SourcelinkConfig.ItemDef::material).orElse(fallback);
    }

    protected Component displayNameOr(Component fallback) {
        Optional<SourcelinkConfig.ItemDef> def = itemDef();
        if (def.isPresent()) {
            // sourcelinks.yml の display-name はレガシー &記法 (例 "&cヴォルカニックソースリンク II")。
            // Component.text(生文字列) だと "&c" が名前に出る(2026-08-03 実バグ)。
            return com.arspaper.util.DisplayText.component(def.get().displayName());
        }
        return fallback;
    }

    protected int cmdOr(int fallback) {
        return itemDef().map(SourcelinkConfig.ItemDef::customModelData).orElse(fallback);
    }

    /**
     * 設定 lore があればそれを、なければ {@code defaultLore} を付与する。
     */
    protected ItemStack withConfiguredOrDefaultLore(ItemStack item, List<Component> defaultLore) {
        List<Component> lore;
        Optional<SourcelinkConfig.ItemDef> def = itemDef();
        if (def.isPresent() && !def.get().lore().isEmpty()) {
            lore = new ArrayList<>(def.get().lore().size());
            for (String line : def.get().lore()) {
                // 色指定が書かれていればそれを尊重し、無ければ従来どおり灰色。
                lore.add(com.arspaper.util.DisplayText.hasMarkup(line)
                        ? com.arspaper.util.DisplayText.component(line)
                        : Component.text(line, NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false));
            }
        } else {
            lore = defaultLore;
        }
        item.editMeta(meta -> meta.lore(lore));
        return item;
    }
}
