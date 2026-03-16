package com.arspaper.ritual.effect;

import com.arspaper.ArsPaper;
import com.arspaper.ritual.RitualEffect;
import com.arspaper.ritual.RitualRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 飛行の儀式 - プレイヤーに一時的なクリエイティブ飛行を付与する。
 * duration パラメータで秒数を指定（デフォルト300秒）。
 */
public class FlightRitualEffect implements RitualEffect {

    private static final int DEFAULT_DURATION_SECONDS = 300;

    @Override
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        int durationSeconds = DEFAULT_DURATION_SECONDS;
        String durationParam = recipe.effectParams().get("duration");
        if (durationParam != null) {
            try { durationSeconds = Integer.parseInt(durationParam); } catch (NumberFormatException ignored) {}
        }

        player.setAllowFlight(true);
        player.setFlying(true);

        Location effectLoc = coreLocation.clone().add(0.5, 2.0, 0.5);
        coreLocation.getWorld().spawnParticle(Particle.END_ROD, effectLoc, 80, 1, 2, 1, 0.1);
        coreLocation.getWorld().playSound(effectLoc, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.2f);

        int finalDuration = durationSeconds;
        player.sendMessage(Component.text(
            "飛行が " + finalDuration + " 秒間有効になりました！", NamedTextColor.LIGHT_PURPLE
        ));

        // 時間経過後に飛行を解除
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                // クリエイティブモードの場合は解除しない
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;

                player.setFlying(false);
                player.setAllowFlight(false);
                player.sendMessage(Component.text(
                    "飛行の効果が切れました。", NamedTextColor.GRAY
                ));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
            }
        }.runTaskLater(ArsPaper.getInstance(), finalDuration * 20L);
    }
}
