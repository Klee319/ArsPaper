package com.arspaper.spell.effect;

import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import com.arspaper.spell.GlyphConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 対象ブロックや近くのドロップアイテムを精錬するEffect。Ars Nouveau準拠。
 *
 * <p>変換表は<b>サーバに登録されているバニラのかまどレシピそのもの</b>
 * ({@link #buildTable})。ハードコードの表を持っていた頃は、バニラで焼けるのに
 * 魔法では焼けないものが多数あった(2026-08-22「精錬魔法が粘土玉に効かない」)。
 * TF 独自の追加は {@link #EXTRA_SMELTS} だけ。
 *
 * <p>Sensitive: ドロップアイテムのみを対象にする。
 */
public class SmeltEffect implements SpellEffect {

    /**
     * <b>バニラのかまどレシピに無い</b>TF 独自の追加分。
     *
     * <p>原石ブロックをまとめて焼けるのはこのフォークの独自仕様
     * (バニラのかまどに {@code raw_iron_block -> iron_block} は無い)。
     * これ以外の変換は<b>ハードコードせず、サーバのかまどレシピ登録から引く</b> ——
     * 手書きの表は必ず腐るため。2026-08-22 の実サーバ報告「精錬魔法が粘土玉に効かない」の
     * 真因がまさにこれで、{@code CLAY_BALL -> BRICK} をはじめ<b>バニラで焼けるのに
     * 表に無いものが多数</b>あった(石炭/ラピス/レッドストーン/ダイヤ/エメラルドの各鉱石、
     * ネザーの金鉱石・ネザークォーツ鉱石、淡いオークなど後から増えた原木、
     * コーラスフルーツ、濡れたスポンジ、シーピクルス など)。
     */
    private static final Map<Material, Material> EXTRA_SMELTS = Map.of(
            Material.RAW_IRON_BLOCK,   Material.IRON_BLOCK,
            Material.RAW_GOLD_BLOCK,   Material.GOLD_BLOCK,
            Material.RAW_COPPER_BLOCK, Material.COPPER_BLOCK);

    /** かまどレシピ1件を「入力材質の集合 -> 出力材質」へ潰したもの。 */
    record SmeltRule(java.util.Set<Material> inputs, Material result) {
    }

    /**
     * 精錬表。<b>初回の詠唱時にサーバのレシピ登録から組む</b>(遅延)。
     *
     * <p>静的初期化子では組めない: {@code Bukkit.recipeIterator()} はサーバが立ち上がって
     * レシピが登録されたあとでないと空になる。クラスのロード時点では間に合わない。
     */
    private static volatile Map<Material, Material> smeltTable;

    private static Map<Material, Material> smeltTable() {
        Map<Material, Material> table = smeltTable;
        if (table == null) {
            table = buildTable(vanillaFurnaceRules(), material -> material.getMaxDurability() > 0);
            smeltTable = table;
        }
        return table;
    }

    /**
     * サーバに登録されている<b>バニラの</b>かまどレシピを {@link SmeltRule} へ落とす。
     *
     * <p><b>{@code minecraft:} 名前空間だけを見る</b>のは意図的。プラグインが登録した
     * かまどレシピを混ぜると、入力/出力の CustomModelData が落ちた「材質だけ」の変換になり、
     * W-172 と同じ<b>カスタムアイテムの無言の喪失</b>を作ってしまう
     * ({@link CustomItemConversionPolicy} が守れるのは入力側だけで、出力側は守れない)。
     */
    private static java.util.List<SmeltRule> vanillaFurnaceRules() {
        java.util.List<SmeltRule> rules = new java.util.ArrayList<>();
        java.util.Iterator<org.bukkit.inventory.Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            org.bukkit.inventory.Recipe recipe = it.next();
            if (!(recipe instanceof org.bukkit.inventory.FurnaceRecipe furnace)) {
                continue;
            }
            if (!"minecraft".equals(furnace.getKey().getNamespace())) {
                continue;
            }
            Material result = furnace.getResult().getType();
            if (result.isAir()) {
                continue;
            }
            java.util.Set<Material> inputs = inputMaterials(furnace.getInputChoice());
            if (!inputs.isEmpty()) {
                rules.add(new SmeltRule(inputs, result));
            }
        }
        return rules;
    }

    private static java.util.Set<Material> inputMaterials(org.bukkit.inventory.RecipeChoice choice) {
        if (choice instanceof org.bukkit.inventory.RecipeChoice.MaterialChoice materials) {
            return new java.util.LinkedHashSet<>(materials.getChoices());
        }
        if (choice instanceof org.bukkit.inventory.RecipeChoice.ExactChoice exact) {
            java.util.Set<Material> out = new java.util.LinkedHashSet<>();
            exact.getChoices().forEach(stack -> out.add(stack.getType()));
            return out;
        }
        return java.util.Set.of();
    }

    /**
     * 精錬表を組む本体。Bukkit ランタイム無しで固定できるよう、レシピの読み出しと分けてある
     * (このフォークのテスト基盤は MockBukkit/Mockito を持たない)。
     *
     * @param damageable 「耐久を持つ材質か」の判定。<b>道具・防具は精錬しない</b> ——
     *                   バニラには {@code iron_pickaxe -> iron_nugget} のようなレシピが実在し、
     *                   そのまま取り込むと<b>足元に落とした装備が詠唱1回でナゲットに化ける</b>。
     *                   実運用では {@code Material#getMaxDurability() > 0}。
     */
    static Map<Material, Material> buildTable(java.util.Collection<SmeltRule> rules,
                                              java.util.function.Predicate<Material> damageable) {
        Map<Material, Material> table = new HashMap<>();
        for (SmeltRule rule : rules) {
            for (Material input : rule.inputs()) {
                if (input == null || damageable.test(input)) {
                    continue;
                }
                table.putIfAbsent(input, rule.result());
            }
        }
        // 独自分はバニラより優先(原石ブロックはバニラに無いので実際には衝突しない)。
        table.putAll(EXTRA_SMELTS);
        // Map.copyOf にしないのは意図的 —— 不変Mapは get(null) で NPE を投げる。
        // ここは Material が null になり得ない経路だけだが、同じ罠で右クリックが
        // 全部落ちた前例があるので null 許容の実装を包む。
        return java.util.Collections.unmodifiableMap(table);
    }

    private final NamespacedKey id;
    private final GlyphConfig config;

    public SmeltEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "smelt");
        this.config = config;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        // エンティティには直接作用しないが、周囲のドロップアイテムを精錬する
        smeltNearbyItems(target.getLocation());
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {
        // ブロック精錬 + 近くのドロップアイテムも精錬
        smeltNearbyItems(blockLocation);

        Block block = blockLocation.getBlock();
        Material result = smeltTable().get(block.getType());
        if (result == null) return;

        org.bukkit.entity.Player caster = context.getCaster();
        if (caster == null) return;

        // 結果がアイテムの場合（鉱石→インゴット等）はドロップとして出力
        if (!result.isBlock()) {
            // BlockBreakEventで保護確認してからブロックを空気に置換
            org.bukkit.event.block.BlockBreakEvent breakEvent =
                    SpellBreakMarker.callMarkedBreakEvent(block, caster);
            if (breakEvent.isCancelled()) return;

            block.setType(Material.AIR);
            block.getWorld().dropItemNaturally(
                blockLocation.clone().add(0.5, 0.5, 0.5), new ItemStack(result));
            spawnSmeltFx(blockLocation);
            return;
        }

        // 結果もブロックの場合はBlackPlaceEventで保護確認してからブロック変換
        BlockState previousState = block.getState();
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
            block,
            previousState,
            block.getRelative(BlockFace.DOWN),
            new ItemStack(result),
            caster,
            true,
            EquipmentSlot.HAND
        );
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) return;

        block.setType(result);
        spawnSmeltFx(blockLocation);
    }

    /**
     * 同一tick中に既に精錬したドロップアイテム。<b>範囲グリフ対応で必須になったガード</b>
     * (2026-08-19)。
     *
     * <p>{@link #applyToBlock} は範囲内の全ブロックについて1回ずつ呼ばれ、その都度
     * {@link #smeltNearbyItems} が半径2を掃くので、<b>同じアイテムが何十回も精錬対象になる</b>。
     * 精錬表には {@code COBBLESTONE → STONE → SMOOTH_STONE} という2段の連鎖があり、
     * ガードが無いと落ちている丸石が1回の詠唱で滑らかな石まで進んでしまう
     * (範囲が1ブロックだった頃は掃き取りも1回だけだったので表面化しなかった)。
     *
     * <p>スペル解決はメインスレッド同期なので単純なフィールドで足りる。tick が変われば
     * 別の詠唱／遅延グリフ経由の別フェーズなので捨てる。
     */
    private long itemSweepTick = Long.MIN_VALUE;
    private final java.util.Set<java.util.UUID> smeltedThisTick = new java.util.HashSet<>();

    /**
     * 指定位置の半径2ブロック以内にあるドロップアイテムを精錬する。
     * 1つのアイテムは1tickにつき1回しか精錬しない（{@link #smeltedThisTick}）。
     */
    private void smeltNearbyItems(Location center) {
        long tick = Bukkit.getCurrentTick();
        if (tick != itemSweepTick) {
            itemSweepTick = tick;
            smeltedThisTick.clear();
        }
        Collection<Item> items = center.getWorld().getNearbyEntitiesByType(
            Item.class, center, 2.0);
        for (Item item : items) {
            ItemStack stack = item.getItemStack();
            // 2026-08-20 W-172: カスタムアイテムは Material が一致しても絶対に焼かない
            // (差し替えなので CMD/PDC/表示名が丸ごと消える)。理由は
            // CustomItemConversionPolicy の javadoc。
            Material smelted = CustomItemConversionPolicy.resultFor(smeltTable(), stack);
            if (smelted == null) continue;
            if (!smeltedThisTick.add(item.getUniqueId())) continue;
            item.setItemStack(new ItemStack(smelted, stack.getAmount()));
            spawnSmeltFx(item.getLocation());
        }
    }

    private void spawnSmeltFx(Location loc) {
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.FLAME, loc.clone().add(0.5, 0.5, 0.5), 12, 0.2, 0.3, 0.2, 0.04);
        loc.getWorld().spawnParticle(
            org.bukkit.Particle.SMOKE, loc.clone().add(0.5, 0.8, 0.5), 8, 0.2, 0.2, 0.2, 0.02);
        loc.getWorld().playSound(loc,
            org.bukkit.Sound.BLOCK_FURNACE_FIRE_CRACKLE, org.bukkit.SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    /**
     * 範囲グリフの展開はヒット面基準・法線は<b>奥へ</b>({@code BreakEffect} と同じ)。
     *
     * <p>既定の {@code FIXED} だと法線方向の符号が {@code +1}（＝手前＝設置系の向き）になるため、
     * 壁を狙って「範囲[法線]」を積むと<b>壁の中ではなく自分側の空気が対象になる</b>。
     * 精錬は破壊と同じく「狙った面から内側へ効く」操作なので INWARD が正しい。
     */
    @Override
    public AoeMode getAoeMode() { return AoeMode.HIT_FACE_INWARD; }

    @Override
    public NamespacedKey getId() { return id; }

    @Override
    public String getDisplayName() { return "精錬"; }

    @Override
    public String getDescription() { return "対象を精錬する"; }

    @Override
    public int getManaCost() { return config.getManaCost("smelt"); }

    @Override
    public int getTier() { return config.getTier("smelt"); }
}
