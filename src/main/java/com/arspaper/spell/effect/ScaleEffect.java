package com.arspaper.spell.effect;

import com.arspaper.ArsPaper;
import com.arspaper.mana.ManaKeys;
import com.arspaper.spell.GlyphConfig;
import com.arspaper.spell.SpellContext;
import com.arspaper.spell.SpellEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * スケールエフェクト。対象のエンティティサイズを変更する。
 * 増幅: サイズ増加 | 減衰: サイズ縮小
 * 延長/短縮: 効果時間
 * 当たり判定もサイズに連動する（GENERIC_SCALE属性）。
 *
 * <h2>⚠️ 解除はスケジューラだけに任せてはいけない（2026-08-23 W-191）</h2>
 * <p>この効果は {@link Attribute#SCALE} の {@link AttributeModifier} で実装している。
 * <b>属性修飾子はエンティティの NBT に保存される</b>ので、サーバを抜ける・再起動する・
 * チャンクがアンロードされる、のいずれでも<b>そのまま残る</b>。一方で解除は
 * {@code runTaskLater} のタスクでしかやっていなかったため、
 * <b>30 秒経つ前にログアウトすると二度と元に戻らなかった</b>
 * （実サーバ報告「スケール魔法で小さくなって鯖抜けて入ったらずっと小さい」）。
 * さらに古い実装はタスクが {@code LivingEntity} の参照を握っていたので、
 * 再ログインで別インスタンスになると {@code isValid()} が false になり、
 * <b>タスクが動いても何もしなかった</b>（＝一瞬抜けただけでも固定化した）。
 *
 * <p>そこで終了時刻をエンティティの PDC（{@link ManaKeys#SPELL_SCALE_END}、エポックミリ秒）にも
 * 書き、{@link #restore(LivingEntity)} が
 * <b>参加時・チャンク読み込み時・プラグイン有効化時</b>に読み直して
 * 「期限切れなら剥がす／残っていればタスクを張り直す」を行う。
 * <b>PDC が無いのに修飾子だけ付いている個体は、この修正より前に固定化した被害者</b>なので
 * 無条件に剥がす（＝入り直すだけで直る）。設計の前例は {@code FlightRitualEffect}。
 */
public class ScaleEffect implements SpellEffect {

    private static final int BASE_DURATION = 600;            // 30秒
    private static final int DURATION_PER_LEVEL = 200;       // +10秒/段
    private static final double SCALE_PER_LEVEL = 0.5;       // +50%/段
    private static final double MIN_SCALE = 0.0625;          // MC最小
    private static final double MAX_SCALE = 16.0;            // MC最大

    /** 修飾子キーの共通接頭辞。剥がすときはエンティティUUIDを知らなくても済むよう名前で引く。 */
    static final String MODIFIER_PREFIX = "arspaper_scale_";

    /** エンティティUUID → 前回のremovalタスク。再適用時にキャンセルする。 */
    private static final Map<UUID, BukkitTask> REMOVAL_TASKS = new ConcurrentHashMap<>();

    /**
     * プラグイン無効化時。<b>タスクを止めるだけでは足りない</b> ——
     * 修飾子はエンティティ側に残るので、オンラインのプレイヤーからは実際に剥がす。
     * （残したままだと、プラグインを落とした状態のサーバで全員が縮んだままになる。）
     */
    public static void cleanupAll() {
        REMOVAL_TASKS.values().forEach(BukkitTask::cancel);
        REMOVAL_TASKS.clear();
        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            clear(player);
        }
    }

    /**
     * プラグイン有効化時。既にオンラインの全員を{@link #restore}に通す
     * （{@code /reload} や再有効化でも {@link org.bukkit.event.player.PlayerJoinEvent} は飛ばないため）。
     */
    public static void restoreAll() {
        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            restore(player);
        }
    }

    /**
     * PDC の終了時刻を見て、期限切れなら剥がし、残っていれば解除タスクを張り直す。
     *
     * <p><b>PDC が無いのに修飾子が付いている個体は無条件に剥がす。</b>
     * それは PDC を書くようになる前に固定化した個体＝直すべき対象そのものだから。
     */
    public static void restore(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        Long endTime = entity.getPersistentDataContainer()
            .get(ManaKeys.SPELL_SCALE_END, PersistentDataType.LONG);
        long remaining = remainingTicks(endTime, System.currentTimeMillis());
        if (remaining <= 0L) {
            clear(entity);
            return;
        }
        scheduleRemoval(entity.getUniqueId(), remaining);
    }

    /**
     * 残り tick。<b>0 以下なら「今すぐ剥がす」</b>。
     *
     * <p>{@code endTimeMillis} が {@code null}（＝PDC が無いのに修飾子だけ付いている個体）も
     * 「剥がす」に倒す。これは PDC を書くようになる前に固定化した被害者そのものなので、
     * ここを「何もしない」にすると<b>既に縮んでいる人が入り直しても直らない</b>。
     *
     * <p>1 tick 未満の端数は 1 tick に切り上げる（0 を渡すと即時実行になり、
     * 「効果を掛けた同じ tick に消える」挙動になりうるため）。
     */
    static long remainingTicks(Long endTimeMillis, long nowMillis) {
        if (endTimeMillis == null) {
            return -1L;
        }
        long remainingMillis = endTimeMillis - nowMillis;
        if (remainingMillis <= 0L) {
            return 0L;
        }
        return Math.max(1L, remainingMillis / 50L);
    }

    /** 修飾子と PDC の両方を落とす（＝効果を完全に終わらせる）。 */
    public static void clear(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        entity.getPersistentDataContainer().remove(ManaKeys.SPELL_SCALE_END);
        removeModifiers(entity);
    }

    /** {@code arspaper_scale_*} の修飾子をすべて外す。無ければ何もしない。 */
    private static void removeModifiers(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attribute.SCALE);
        if (attr == null) {
            return;
        }
        // getModifiers() のコピーを回してから外す（走査中の除去を避ける）。
        for (AttributeModifier modifier : java.util.List.copyOf(attr.getModifiers())) {
            if (modifier.getKey().getKey().startsWith(MODIFIER_PREFIX)) {
                attr.removeModifier(modifier);
            }
        }
    }

    /**
     * 解除タスクを張り直す。<b>エンティティの参照は握らず UUID で引き直す</b> ——
     * 握ると再ログイン後に別インスタンスになって {@code isValid()} が false になり、
     * タスクが動いても何もしないまま固定化する（これが元の不具合の第2の原因）。
     */
    private static void scheduleRemoval(UUID entityUuid, long delayTicks) {
        ArsPaper plugin = ArsPaper.getInstance();
        if (plugin == null) {
            return;
        }
        BukkitTask prevTask = REMOVAL_TASKS.remove(entityUuid);
        if (prevTask != null) {
            prevTask.cancel();
        }
        BukkitTask removalTask = new BukkitRunnable() {
            @Override
            public void run() {
                REMOVAL_TASKS.remove(entityUuid);
                org.bukkit.entity.Entity current = org.bukkit.Bukkit.getEntity(entityUuid);
                if (current instanceof LivingEntity living) {
                    clear(living);
                }
                // 見つからない（ログアウト済み／チャンク外）場合は PDC が残るので、
                // 次に読み込まれたときに restore() が期限切れとして剥がす。
            }
        }.runTaskLater(plugin, delayTicks);
        REMOVAL_TASKS.put(entityUuid, removalTask);
    }

    private final NamespacedKey id;
    private final GlyphConfig config;
    private final JavaPlugin plugin;

    public ScaleEffect(JavaPlugin plugin, GlyphConfig config) {
        this.id = new NamespacedKey(plugin, "scale");
        this.config = config;
        this.plugin = plugin;
    }

    @Override
    public void applyToEntity(SpellContext context, LivingEntity target) {
        AttributeInstance scaleAttr = target.getAttribute(Attribute.SCALE);
        if (scaleAttr == null) return;

        int baseDuration = (int) config.getParam("scale", "base-duration", BASE_DURATION);
        int durationPerLevel = (int) config.getParam("scale", "duration-per-level", DURATION_PER_LEVEL);
        int durationTicks = baseDuration + context.getDurationLevel() * durationPerLevel;

        double scalePerLevel = config.getParam("scale", "scale-per-level", SCALE_PER_LEVEL);
        int level = context.getAmplifyLevel();
        // level>0: 大きく、level<0: 小さく、level=0: 基本倍率
        double scaleFactor = 1.0 + level * scalePerLevel;
        double minScale = config.getParam("scale", "min-scale", MIN_SCALE);
        double maxScale = config.getParam("scale", "max-scale", MAX_SCALE);
        scaleFactor = Math.max(minScale, Math.min(maxScale, scaleFactor));

        // 修飾子を適用（乗算ベース）
        UUID entityUUID = target.getUniqueId();
        NamespacedKey modKey = new NamespacedKey(plugin, MODIFIER_PREFIX + entityUUID);

        // 既存の修飾子は接頭辞で全部落とす。キーにエンティティUUIDが入っている都合で、
        // 過去バージョンや別経路で付いた個体の分も確実に外すため equals では見ない。
        removeModifiers(target);

        double modifierAmount = scaleFactor - 1.0; // MULTIPLY_SCALAR_1 は (1 + amount) 倍
        AttributeModifier modifier = new AttributeModifier(
            modKey, modifierAmount, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        scaleAttr.addModifier(modifier);

        // ⚠ 終了時刻を PDC にも書く。タスクだけだとログアウト/再起動/チャンクアンロードを
        //   跨いだ瞬間に解除役が消え、修飾子（NBT 保存）だけが残って永久に戻らない。
        target.getPersistentDataContainer().set(
            ManaKeys.SPELL_SCALE_END, PersistentDataType.LONG,
            System.currentTimeMillis() + durationTicks * 50L);

        spawnScaleFx(target.getLocation(), level >= 0);

        scheduleRemoval(entityUUID, durationTicks);
    }

    @Override
    public void applyToBlock(SpellContext context, Location blockLocation) {}

    private void spawnScaleFx(Location loc, boolean growing) {
        if (growing) {
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0),
                5, 0.3, 0.3, 0.3, 0);
            loc.getWorld().playSound(loc, Sound.ENTITY_PUFFER_FISH_BLOW_UP,
                SoundCategory.PLAYERS, 0.8f, 0.8f);
        } else {
            loc.getWorld().spawnParticle(Particle.COMPOSTER, loc.clone().add(0, 0.5, 0),
                10, 0.2, 0.2, 0.2, 0);
            loc.getWorld().playSound(loc, Sound.ENTITY_PUFFER_FISH_BLOW_OUT,
                SoundCategory.PLAYERS, 0.8f, 1.5f);
        }
    }

    @Override public NamespacedKey getId() { return id; }
    @Override public String getDisplayName() { return "スケール"; }
    @Override public String getDescription() { return "対象のサイズを変更する（当たり判定含む）"; }
    @Override public int getManaCost() { return config.getManaCost("scale"); }
    @Override public int getTier() { return config.getTier("scale"); }
}
