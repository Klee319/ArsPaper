package com.arspaper.integration;

import com.arspaper.item.ItemKeys;
import com.arspaper.util.PdcHelper;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 要件⑥ source-auto-consume(Ars鍛冶A-2): skilltree由来のperkでmana-unlock-effectを
 * 解放したプレイヤーが詠唱時にマナ不足になった場合、プレイヤーの<b>インベントリ内アイテム</b>を
 * マナ代わりに変換消費して詠唱を成立させるブリッジ。
 *
 * <p>変換対象は {@code config.yml} の {@code mana.source-auto-consume.items}
 * (itemId -&gt; 1個あたりのマナ変換量)で定義される。itemId は次のいずれかのPDCキーで解決する:
 * <ul>
 *   <li>Arsカスタムアイテムid ({@link ItemKeys#CUSTOM_ITEM_ID}, 例: {@code source_berry})</li>
 *   <li>TrinityForgeカタログid ({@code trinityforge:catalog_id})</li>
 * </ul>
 * どちらも {@link com.arspaper.util.PdcHelper#getCrossPluginItemId} で解決する。
 *
 * <p>TF未ロード/perk未所持時は{@link TrinityForgeBridge#tfEffectActive}が{@code false}を返すため、
 * 呼び出し元(ManaManager#consumeMana)は従来どおりマナ不足として扱う(fail-open)。
 * 変換は all-or-nothing: 不足マナを満たすだけのアイテムが無ければ何も消費しない。
 */
public final class SourceAutoConsume {

    private SourceAutoConsume() {
    }

    /**
     * プレイヤーのインベントリから、不足マナ量(deficitMana)を満たすだけのアイテムをマナへ変換消費する。
     * perk未所持/TF未ロード、または対象アイテムが不足していれば消費は行わず{@code 0}を返す
     * （部分消費なし＝all-or-nothingでManaManager側の整合を保つ）。
     *
     * @param player         詠唱者（プレイヤー特定不能な経路は本メソッドを呼ばないこと）
     * @param deficitMana    現在マナだけでは賄えない不足量（&gt; 0 のみ意味を持つ）
     * @param itemManaConfig itemId(Ars custom_item_id または TFカタログid) -&gt; 1個あたりのマナ変換量
     *                       (ManaConfig#sourceAutoConsumeItems() 由来)
     * @return 実際に変換されたマナ量。0なら未変換（呼び出し元は従来どおりマナ不足として扱う）。
     *         成功時は常に {@code deficitMana} と一致する（過剰変換分は破棄し、ちょうど賄う）。
     */
    public static int tryConvert(Player player, int deficitMana, Map<String, Integer> itemManaConfig) {
        if (player == null || deficitMana <= 0) {
            return 0;
        }
        if (!TrinityForgeBridge.tfEffectActive(player, TrinityForgeBridge.EFFECT_SOURCE_AUTO_CONSUME)) {
            return 0;
        }
        if (itemManaConfig == null || itemManaConfig.isEmpty()) {
            return 0;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();

        List<MatchedStack> matches = new ArrayList<>();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            String id = resolveItemId(stack);
            if (id == null) {
                continue;
            }
            Integer manaPerItem = itemManaConfig.get(id);
            if (manaPerItem == null || manaPerItem <= 0) {
                continue;
            }
            matches.add(new MatchedStack(slot, manaPerItem, stack.getAmount()));
        }

        List<ConsumeEntry> plan = computeConsumptionPlan(matches, deficitMana);
        if (plan == null) {
            return 0;
        }

        for (ConsumeEntry entry : plan) {
            ItemStack stack = contents[entry.slotIndex()];
            int remainingAmount = stack.getAmount() - entry.consumeCount();
            if (remainingAmount <= 0) {
                inventory.setItem(entry.slotIndex(), null);
            } else {
                ItemStack updated = stack.clone();
                updated.setAmount(remainingAmount);
                inventory.setItem(entry.slotIndex(), updated);
            }
        }

        return deficitMana;
    }

    /**
     * ItemStackのid(Arsカスタムid優先、無ければTFカタログid)を解決する。
     * どちらのPDCキーも持たない/メタ無しのアイテムは {@code null}(=変換対象外)。
     *
     * <p>【2026-07-30 修正】以前はTFカタログキーを {@code trinityforge:item_catalog_id} と
     * 手書きしており、実在するキーは {@code trinityforge:catalog_id} なので<b>TFカタログ品が
     * 一切マッチしなかった</b>(Ars素材だけが変換されていた)。キー名の手書きをやめ、
     * TFの定数を参照する {@link PdcHelper#getCrossPluginItemId} へ一本化した。
     */
    private static String resolveItemId(ItemStack stack) {
        return PdcHelper.getCrossPluginItemId(stack).orElse(null);
    }

    /**
     * 純粋計算部: マッチしたスタック一覧から不足マナを満たす消費計画を算出する。
     * 合計変換可能マナ &lt; deficitMana なら {@code null}(=消費不可、all-or-nothing)。
     * Bukkitに依存しないためユニットテスト可能（{@link #tryConvert}のPlayer/Inventory走査から分離）。
     *
     * @param matches     マッチしたスタック一覧（走査順=消費優先順。特定の並び順は保証しない）
     * @param deficitMana 満たすべき不足マナ量（&gt; 0 前提。呼び出し元で保証する）
     * @return 消費すべき(スロット, 消費個数)一覧。不足時は{@code null}
     */
    static List<ConsumeEntry> computeConsumptionPlan(List<MatchedStack> matches, int deficitMana) {
        long totalAvailable = 0;
        for (MatchedStack m : matches) {
            totalAvailable += (long) m.manaPerItem() * m.count();
        }
        if (totalAvailable < deficitMana) {
            return null;
        }

        List<ConsumeEntry> entries = new ArrayList<>();
        int remaining = deficitMana;
        for (MatchedStack m : matches) {
            if (remaining <= 0) {
                break;
            }
            int needCount = (int) Math.ceil(remaining / (double) m.manaPerItem());
            int consume = Math.min(needCount, m.count());
            if (consume <= 0) {
                continue;
            }
            entries.add(new ConsumeEntry(m.slotIndex(), consume));
            remaining -= consume * m.manaPerItem();
        }
        return entries;
    }

    /** インベントリ内でマッチしたスタック1つ分の情報（スロット番号 / 1個あたりのマナ / 保有数）。 */
    record MatchedStack(int slotIndex, int manaPerItem, int count) {
    }

    /** 消費計画の1エントリ（スロット番号 / そのスロットから消費する個数）。 */
    record ConsumeEntry(int slotIndex, int consumeCount) {
    }
}
