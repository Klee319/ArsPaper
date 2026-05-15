package com.arspaper.api;

import com.arspaper.ArsPaper;
import com.arspaper.api.modifier.ModifierStore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * ArsAPIのライフサイクル初期化と永続化ロード/セーブのリスナー。
 * ArsPaper#onEnable() から register() を呼ぶ。
 */
public final class ApiBootstrap implements Listener {

    private final ArsPaper plugin;
    private final ModifierStore modifierStore;
    private final ArsPlayerDataStore playerDataStore;

    public ApiBootstrap(ArsPaper plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        boolean persistModifiers = plugin.getConfig().getBoolean("api.modifier-persistence", false);
        this.modifierStore = new ModifierStore();
        this.playerDataStore = new ArsPlayerDataStore(plugin, modifierStore, persistModifiers);
        ArsAPI.init(plugin, modifierStore, playerDataStore);
    }

    public ModifierStore getModifierStore() { return modifierStore; }
    public ArsPlayerDataStore getPlayerDataStore() { return playerDataStore; }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // 起動時にオンラインプレイヤーがいたら読み込み(リロード対策)
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            playerDataStore.load(p);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        playerDataStore.load(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        playerDataStore.unload(e.getPlayer().getUniqueId());
    }

    public void shutdown() {
        playerDataStore.saveAll();
    }
}
