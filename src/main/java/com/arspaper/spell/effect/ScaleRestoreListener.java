package com.arspaper.spell.effect;

import com.arspaper.ArsPaper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * スケール魔法の後始末を「セッションを跨いでも」効かせるためのリスナー（2026-08-23 W-191）。
 *
 * <p>{@link ScaleEffect} は {@code Attribute.SCALE} の修飾子で実装されていて、
 * <b>修飾子はエンティティの NBT に保存される</b>。解除がスケジューラのタスクだけだと、
 * ログアウト・サーバ再起動・チャンクアンロードのどれか 1 つで解除役が消え、
 * 縮んだ（または巨大化した）まま二度と戻らなくなる。
 *
 * <p>そこで<b>戻ってきた瞬間</b>に必ず読み直す:
 * <ul>
 *   <li>{@link PlayerJoinEvent} — プレイヤーの再ログイン</li>
 *   <li>{@link EntitiesLoadEvent} — チャンクと一緒に戻ってきたモブ</li>
 * </ul>
 *
 * <p>参加時に 1 tick 遅らせるのは {@code FlightRitualEffect} と同じ理由（データの読み込み完了を待つ）。
 */
public class ScaleRestoreListener implements Listener {

    /**
     * 1 tick 後と 2 秒後の<b>2 回</b>見る。
     *
     * <p>2 回目が要るのは HuskSync のため。配備中の HuskSync は
     * <b>snapshot の適用が {@link PlayerJoinEvent} より後</b>で、その中で
     * {@code PersistentData#apply} が {@code clearNBT()} → 保存済み PDC を merge、
     * {@code Attributes#apply} が属性を入れ直す。参加直後の 1 回だけだと、
     * 剥がした側が後から書き戻される可能性がある
     * （同じ順序問題で職業バフが消えた前例が TF 側 W-138 にある）。
     * 2 回目は冪等なので、1 回目で片付いていれば何もしない。
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scheduleRestore(player, 1L);
        scheduleRestore(player, 40L);
    }

    private static void scheduleRestore(Player player, long delayTicks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    ScaleEffect.restore(player);
                }
            }
        }.runTaskLater(ArsPaper.getInstance(), delayTicks);
    }

    /**
     * チャンクと一緒に読み込まれたモブ。<b>PDC を持っている個体だけ</b>を
     * {@link ScaleEffect#restore} に通す —— 持っていない個体まで通すと
     * 「PDC 無し＝過去に固定化した被害者」の規則でチャンク内の全モブの属性を触ってしまう。
     * プレイヤーは {@link #onPlayerJoin} が見るのでここでは除く。
     */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Player || !(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living.getPersistentDataContainer().has(
                    com.arspaper.mana.ManaKeys.SPELL_SCALE_END,
                    org.bukkit.persistence.PersistentDataType.LONG)) {
                ScaleEffect.restore(living);
            }
        }
    }
}
