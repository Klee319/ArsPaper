package com.arspaper.ritual.effect;

import com.arspaper.mana.ManaKeys;
import com.arspaper.ritual.RitualEffect;
import com.arspaper.ritual.RitualRecipe;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 帰還の儀式 - 帰還ポイントの設定とテレポート。
 * mode=set: 現在の儀式コア位置をプレイヤーPDCに保存
 * mode=teleport: 保存位置にテレポート
 */
public class RecallRitualEffect implements RitualEffect {

    @Override
    public void execute(Location coreLocation, Player player, RitualRecipe recipe) {
        String mode = recipe.effectParams().getOrDefault("mode", "set");
        Location effectLoc = coreLocation.clone().add(0.5, 2.0, 0.5);

        if ("set".equals(mode)) {
            setRecallPoint(player, coreLocation);
            coreLocation.getWorld().spawnParticle(Particle.ENCHANT, effectLoc, 60, 1, 2, 1, 1.0);
            coreLocation.getWorld().playSound(effectLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
            player.sendMessage(Component.text(
                "帰還ポイントを設定しました！", NamedTextColor.GREEN
            ));
        } else if ("teleport".equals(mode)) {
            Location recallPoint = getRecallPoint(player);
            if (recallPoint == null) {
                player.sendMessage(Component.text(
                    "帰還ポイントが設定されていません！先に設定の儀式を行ってください。", NamedTextColor.RED
                ));
                return;
            }

            // テレポートエフェクト（出発地点）
            coreLocation.getWorld().spawnParticle(Particle.PORTAL, effectLoc, 100, 1, 2, 1, 0.5);
            coreLocation.getWorld().playSound(effectLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

            // テレポート実行
            player.teleport(recallPoint);

            // テレポートエフェクト（到着地点）
            recallPoint.getWorld().spawnParticle(Particle.PORTAL, recallPoint.clone().add(0, 1, 0), 100, 1, 2, 1, 0.5);
            recallPoint.getWorld().playSound(recallPoint, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

            player.sendMessage(Component.text(
                "帰還ポイントにテレポートしました！", NamedTextColor.LIGHT_PURPLE
            ));
        }
    }

    private void setRecallPoint(Player player, Location location) {
        JsonObject json = new JsonObject();
        json.addProperty("world", location.getWorld().getName());
        json.addProperty("x", location.getX() + 0.5);
        json.addProperty("y", location.getY() + 1.0);
        json.addProperty("z", location.getZ() + 0.5);
        json.addProperty("yaw", player.getLocation().getYaw());
        json.addProperty("pitch", player.getLocation().getPitch());

        player.getPersistentDataContainer().set(
            ManaKeys.RECALL_POINT, PersistentDataType.STRING, json.toString()
        );
    }

    private Location getRecallPoint(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String jsonStr = pdc.get(ManaKeys.RECALL_POINT, PersistentDataType.STRING);
        if (jsonStr == null) return null;

        try {
            JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
            World world = Bukkit.getWorld(json.get("world").getAsString());
            if (world == null) return null;

            return new Location(
                world,
                json.get("x").getAsDouble(),
                json.get("y").getAsDouble(),
                json.get("z").getAsDouble(),
                json.get("yaw").getAsFloat(),
                json.get("pitch").getAsFloat()
            );
        } catch (Exception e) {
            return null;
        }
    }
}
