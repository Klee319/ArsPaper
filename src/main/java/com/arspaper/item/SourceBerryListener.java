package com.arspaper.item;

import com.arspaper.ArsPaper;
import com.arspaper.item.impl.SourceBerry;
import com.arspaper.util.PdcHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;

/**
 * ソースベリーの食べるイベントを監視し、マナを回復する。
 */
public class SourceBerryListener implements Listener {

    private final ArsPaper plugin;

    public SourceBerryListener(ArsPaper plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        var customId = PdcHelper.getCustomItemId(event.getItem());
        if (customId.isEmpty() || !"source_berry".equals(customId.get())) return;

        Player player = event.getPlayer();
        int current = plugin.getManaManager().getCurrentMana(player);
        int max = plugin.getManaManager().getMaxMana(player);
        int restore = Math.min(SourceBerry.MANA_RESTORE, max - current);

        if (restore > 0) {
            plugin.getManaManager().addMana(player, restore);
            player.sendMessage(Component.text("マナを" + restore + "回復しました", NamedTextColor.AQUA));
        } else {
            player.sendMessage(Component.text("マナは満タンです", NamedTextColor.GRAY));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.5f, 1.5f);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0),
            8, 0.3, 0.3, 0.3, 0.05);
    }
}
