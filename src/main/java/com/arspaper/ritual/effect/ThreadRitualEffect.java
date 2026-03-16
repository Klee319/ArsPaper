package com.arspaper.ritual.effect;

import com.arspaper.ArsPaper;
import com.arspaper.block.impl.RitualCore;
import com.arspaper.item.ItemKeys;
import com.arspaper.item.ThreadType;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * スレッドクラフトの儀式 - コアに置いた空スレッドを型付きスレッドに変換する。
 * effect-params の thread パラメータで変換先のスレッドタイプを指定。
 */
public class ThreadRitualEffect implements RitualEffect {

    @Override
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        String threadId = recipe.effectParams().get("thread");
        if (threadId == null) {
            player.sendMessage(Component.text("スレッドタイプが指定されていません！", NamedTextColor.RED));
            return;
        }

        ThreadType threadType = ThreadType.fromId(threadId);
        if (threadType == null || !threadType.hasEffect()) {
            player.sendMessage(Component.text("不明なスレッドタイプ: " + threadId, NamedTextColor.RED));
            return;
        }

        // コアのTileStateを取得
        if (!(coreLocation.getBlock().getState() instanceof TileState tileState)) return;

        PersistentDataContainer corePdc = tileState.getPersistentDataContainer();
        String customId = corePdc.get(new org.bukkit.NamespacedKey("arspaper", "core_custom_id"), PersistentDataType.STRING);

        // コアに空スレッドが置かれているか確認
        if (!"thread_empty".equals(customId)) {
            player.sendMessage(Component.text("コアに空のスレッドを置いてください！", NamedTextColor.RED));
            return;
        }

        // 型付きスレッドアイテムを生成
        ItemStack threadItem = ArsPaper.getInstance().getItemRegistry()
            .get("thread_" + threadType.getId())
            .map(item -> item.createItemStack())
            .orElse(null);

        if (threadItem == null) {
            player.sendMessage(Component.text("スレッドアイテムの生成に失敗しました！", NamedTextColor.RED));
            return;
        }

        // コアをクリアしてスレッドを返却
        RitualCore.clearCoreItem(tileState);

        coreLocation.getWorld().dropItemNaturally(
            coreLocation.clone().add(0.5, 1.5, 0.5), threadItem
        );

        // エフェクト
        Location effectLoc = coreLocation.clone().add(0.5, 1.5, 0.5);
        coreLocation.getWorld().spawnParticle(Particle.ENCHANT, effectLoc, 80, 0.5, 0.5, 0.5, 1.0);
        coreLocation.getWorld().playSound(effectLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);

        player.sendMessage(Component.text(
            threadType.getDisplayName() + " を精製しました！", NamedTextColor.GREEN
        ));
    }
}
