package com.arspaper.ritual.effect;

import com.arspaper.block.impl.RitualCore;
import com.arspaper.integration.TrinityForgeBridge;
import com.arspaper.integration.TrinityForgeBridge.ThreadIdentity;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.ThreadType;
import com.arspaper.item.impl.ThreadItem;
import com.arspaper.ritual.RitualEffect;
import com.arspaper.ritual.RitualRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/**
 * スレッド厳選の振り直し儀式 ── コアに置いたスレッド1個の rollSeed を新規発番して再抽選する
 * （quality は据え置き）。実際のステ値は TF の item-stats.yml から都度導出されるため、
 * この儀式は「rollSeed という乱数の種」を引き直すだけで済む。
 *
 * <p>厳選値(rollSeed)は生成時にアイテムへ焼き込むので、他に振り直す手段が無い（外して付け直しても
 * {@code ThreadGui} が装着時の値を復元するため変わらない）。「沼」を回すための唯一の入口がここ。
 *
 * <p>コアのアイテムは<b>消費せず</b>その場で書き換える（{@code RitualManager} の
 * {@code CORE_PRESERVING_EFFECT_TYPES} に {@code thread_reroll} を登録してある）。
 * ペデスタルの素材だけが消える設計なので、素材が「振り直し券」として働く。
 *
 * <p>スタックが2個以上のときは<b>失敗させる</b>。1個だけ振り直して残りを据え置く挙動は
 * 直感に反し、まとめて同じ値にするのは厳選の意味を壊すため。
 */
public class ThreadRerollRitualEffect implements RitualEffect {

    @Override
    public boolean validate(Location coreLocation, Player player, RitualRecipe recipe) {
        ItemStack core = resolveCoreItem(coreLocation);
        if (core == null || !isEffectThread(core)) {
            player.sendMessage(Component.text("コアに効果付きスレッドを1個だけ置いてください！", NamedTextColor.RED));
            return false;
        }
        if (core.getAmount() != 1) {
            player.sendMessage(Component.text("スレッドは1個だけ置いてください！(まとめて振り直しはできません)",
                    NamedTextColor.RED));
            return false;
        }
        ThreadType type = threadTypeOf(core);
        if (type == null || !hasResolvableThreadStats(core, type)) {
            player.sendMessage(Component.text("厳選が無効化されているため振り直せません！", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    /**
     * 「厳選が無効化されているか」の判定手段。TF側に「random 定義の有無」を直接問う入口が無いため、
     * <b>現在の (rollSeed, quality) で resolveThreadStats が空マップを返すか</b>で代用する
     * （item-stats.yml にそのスレッドの定義自体が無い/TF未ロードなら空になる）。
     */
    private static boolean hasResolvableThreadStats(ItemStack core, ThreadType type) {
        ThreadIdentity identity = TrinityForgeBridge.readThreadIdentity(core);
        return !TrinityForgeBridge.resolveThreadStats(type.getBaseMaterial(), type.getCustomModelData(),
                identity.quality(), identity.rollSeed()).isEmpty();
    }

    @Override
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        if (!(coreLocation.getBlock().getState() instanceof TileState tileState)) {
            return;
        }
        ItemStack core = RitualCore.getStoredItem(tileState);
        if (core == null || !isEffectThread(core) || core.getAmount() != 1) {
            player.sendMessage(Component.text("コアに効果付きスレッドを1個だけ置いてください！", NamedTextColor.RED));
            return;
        }
        ThreadType type = threadTypeOf(core);
        if (type == null) {
            return;
        }

        // quality は据え置き、rollSeed だけを新規発番して刻み直す。
        Optional<ThreadIdentity> rerolledOpt = TrinityForgeBridge.rerollThreadIdentity(core);
        if (rerolledOpt.isEmpty()) {
            player.sendMessage(Component.text("厳選の振り直しに失敗しました（TrinityForge未ロード等）",
                    NamedTextColor.RED));
            return;
        }
        ThreadIdentity rerolled = rerolledOpt.get();

        // lore はまるごと組み直す(2026-08-05)。旧実装は「旧厳選の行と内容一致した行を消してから足す」
        // 方式で、ステ部分が装備と同じ体裁(幅可変の区切り線を含む)になった以上、値の桁が変わると
        // 古い区切り線が一致せず溜まり続ける。
        core.editMeta(meta -> meta.lore(ThreadItem.fullLore(meta, type, rerolled)));
        RitualCore.setStoredItem(tileState, core);

        Location effectLoc = coreLocation.clone().add(0.5, 1.5, 0.5);
        coreLocation.getWorld().spawnParticle(Particle.WITCH, effectLoc, 90, 0.5, 0.6, 0.5, 0.6);
        coreLocation.getWorld().playSound(effectLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);
        player.sendMessage(Component.text("スレッドを振り直しました！", NamedTextColor.LIGHT_PURPLE));
        ThreadItem.rollLore(type, rerolled).forEach(player::sendMessage);
    }

    /** {@code item} のPDC({@code ItemKeys.THREAD_ITEM_TYPE})からスレッド種別を復元する。未設定/不明なら null。 */
    private static ThreadType threadTypeOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String typeId = item.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.THREAD_ITEM_TYPE, PersistentDataType.STRING);
        return ThreadType.fromId(typeId);
    }

    private static boolean isEffectThread(ItemStack item) {
        ThreadType type = threadTypeOf(item);
        return type != null && type.hasEffect();
    }

    private static ItemStack resolveCoreItem(Location coreLocation) {
        if (!(coreLocation.getBlock().getState() instanceof TileState tileState)) {
            return null;
        }
        return RitualCore.getStoredItem(tileState);
    }
}
