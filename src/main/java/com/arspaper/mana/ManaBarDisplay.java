package com.arspaper.mana;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BossBarでプレイヤーのマナをリアルタイム表示。
 */
public class ManaBarDisplay {

    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    /**
     * マナ表示を更新する。BossBarがなければ新規作成。
     */
    public BossBar update(UUID playerId, int current, int max) {
        float progress = max > 0 ? Math.clamp((float) current / max, 0f, 1f) : 0f;
        Component title = Component.text("マナ: " + current + " / " + max, NamedTextColor.AQUA);

        BossBar bar = bars.computeIfAbsent(playerId, id ->
            BossBar.bossBar(title, progress, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS)
        );

        bar.name(title);
        bar.progress(progress);
        return bar;
    }

    /**
     * プレイヤーのBossBarを取得。
     */
    public BossBar get(UUID playerId) {
        return bars.get(playerId);
    }

    /**
     * プレイヤーのBossBarを削除。
     */
    public void remove(UUID playerId) {
        bars.remove(playerId);
    }
}
