package com.arspaper.spell;

import com.arspaper.spell.effect.PhantomBlockEffect;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * 仮想ブロックの破壊時にドロップを無効化するリスナー。
 */
public class PhantomBlockListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (PhantomBlockEffect.isPhantomBlock(event.getBlock().getLocation())) {
            event.setDropItems(false);
            event.setExpToDrop(0);
            // Remove from phantom tracking (player manually broke it)
            PhantomBlockEffect.removeFromTracking(event.getBlock().getLocation());
        }
    }
}
