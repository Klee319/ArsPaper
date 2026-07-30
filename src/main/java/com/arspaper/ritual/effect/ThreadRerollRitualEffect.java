package com.arspaper.ritual.effect;

import com.arspaper.ArsPaper;
import com.arspaper.block.impl.RitualCore;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.ThreadRoll;
import com.arspaper.item.ThreadRollConfig;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * スレッド厳選の振り直し儀式 ── コアに置いたスレッド1個の主ステ/サブステを再抽選する。
 *
 * <p>厳選値は生成時にアイテムへ焼き込むので、他に振り直す手段が無い（外して付け直しても
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

    private static final Random RANDOM = new Random();

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
        ThreadRollConfig config = rollConfig();
        if (config == null || !config.isEnabled()) {
            player.sendMessage(Component.text("厳選が無効化されているため振り直せません！", NamedTextColor.RED));
            return false;
        }
        return true;
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
        ThreadRollConfig config = rollConfig();
        if (config == null) {
            return;
        }
        ThreadRoll rerolled = config.roll(RANDOM).orElse(null);
        if (rerolled == null) {
            player.sendMessage(Component.text("厳選の抽選に失敗しました（thread-rolls.yml を確認してください）",
                    NamedTextColor.RED));
            return;
        }

        // 旧厳選の lore 行だけを取り除いてから新しい行を入れる（種類ごとの効果説明は残す）。
        List<Component> previousRollLore = ThreadRoll.decode(ThreadRoll.rawOf(core))
                .map(ThreadItem::rollLore).orElse(List.of());
        core.editMeta(meta -> {
            ThreadRoll.write(meta.getPersistentDataContainer(), rerolled);
            List<Component> current = meta.lore() == null ? List.<Component>of() : meta.lore();
            List<Component> rebuilt = new ArrayList<>();
            for (Component line : current) {
                if (!previousRollLore.contains(line)) {
                    rebuilt.add(line);
                }
            }
            rebuilt.addAll(ThreadItem.rollLore(rerolled));
            meta.lore(rebuilt);
        });
        RitualCore.setStoredItem(tileState, core);

        Location effectLoc = coreLocation.clone().add(0.5, 1.5, 0.5);
        coreLocation.getWorld().spawnParticle(Particle.WITCH, effectLoc, 90, 0.5, 0.6, 0.5, 0.6);
        coreLocation.getWorld().playSound(effectLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);
        player.sendMessage(Component.text("スレッドを振り直しました！", NamedTextColor.LIGHT_PURPLE));
        ThreadItem.rollLore(rerolled).forEach(player::sendMessage);
    }

    private static ThreadRollConfig rollConfig() {
        ArsPaper ars = ArsPaper.getInstance();
        return ars == null ? null : ars.getThreadRollConfig();
    }

    private static boolean isEffectThread(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String typeId = item.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.THREAD_ITEM_TYPE, PersistentDataType.STRING);
        ThreadType type = ThreadType.fromId(typeId);
        return type != null && type.hasEffect();
    }

    private static ItemStack resolveCoreItem(Location coreLocation) {
        if (!(coreLocation.getBlock().getState() instanceof TileState tileState)) {
            return null;
        }
        return RitualCore.getStoredItem(tileState);
    }
}
