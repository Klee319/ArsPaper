package com.arspaper.gui;

import com.arspaper.ArsPaper;
import com.arspaper.mana.ManaKeys;
import com.arspaper.spell.SpellComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

import java.util.*;

/**
 * グリフアンロック時の演出アニメーション。
 * 素材アイテムが筆記台の周りを軌道旋回し、収束・合成する演出を行う。
 * Geyser互換のためArmorStand（Marker）でアイテム表示。
 */
public class GlyphUnlockAnimation {

    private static final String CLEANUP_TAG = "arspaper_unlock_anim";
    private static final int TOTAL_DURATION = 60; // 3秒 = 60tick
    private static final int CONVERGE_START = 50;
    private static final double INITIAL_RADIUS = 1.5;
    private static final double ORBIT_Y_OFFSET = 1.2;
    private static final int SOUND_INTERVAL = 10;

    private GlyphUnlockAnimation() {}

    /**
     * アンロックアニメーションを開始する。
     * GUIを閉じ、素材を消費し、ArmorStandで軌道アニメーション後にグリフをアンロックする。
     *
     * @param plugin       プラグインインスタンス
     * @param player       対象プレイヤー
     * @param component    アンロック対象のグリフ
     * @param tableLoc     筆記台のブロック位置
     * @param materials    消費する素材マップ（tryPayUnlockCostで検証済み）
     * @param levelCost    消費する経験値レベル
     * @param unlocked     現在のアンロック済みグリフセット（変更可能）
     * @param saveCallback アンロック済みグリフを保存するコールバック
     */
    public static void play(
            ArsPaper plugin,
            Player player,
            SpellComponent component,
            Location tableLoc,
            Map<Material, Integer> materials,
            int levelCost,
            Set<String> unlocked,
            Runnable saveCallback
    ) {
        // GUI を閉じる
        player.closeInventory();

        // 素材をインベントリから消費（検証済みなので安全）
        for (var entry : materials.entrySet()) {
            player.getInventory().removeItem(new ItemStack(entry.getKey(), entry.getValue()));
        }

        // アニメーション中心点（ブロック中央、少し上）
        Location center = tableLoc.clone().add(0.5, ORBIT_Y_OFFSET, 0.5);
        World world = center.getWorld();

        // 素材ごとにArmorStandを生成
        List<ArmorStand> stands = new ArrayList<>();
        List<Material> matTypes = new ArrayList<>(materials.keySet());
        int count = matTypes.size();
        double angleStep = (2 * Math.PI) / Math.max(count, 1);

        for (int i = 0; i < count; i++) {
            double angle = angleStep * i;
            Location spawnLoc = center.clone().add(
                    INITIAL_RADIUS * Math.cos(angle),
                    0,
                    INITIAL_RADIUS * Math.sin(angle)
            );

            ArmorStand stand = world.spawn(spawnLoc, ArmorStand.class, as -> {
                as.setVisible(false);
                as.setSmall(true);
                as.setMarker(true);
                as.setGravity(false);
                as.setInvulnerable(true);
                as.setSilent(true);
                as.setBasePlate(false);
                as.setCanPickupItems(false);
                as.addScoreboardTag(CLEANUP_TAG);
                // 腕を表示して右手にアイテムを持たせる
                as.setArms(true);
                as.setRightArmPose(new EulerAngle(Math.toRadians(-90), 0, 0));
            });

            stand.getEquipment().setItemInMainHand(new ItemStack(matTypes.get(i)));
            stands.add(stand);
        }

        // 開始音
        player.playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.0f, 0.8f);

        // アニメーションタスク
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                // エッジケース: プレイヤーがオフラインまたは遠すぎる
                if (!player.isOnline() || player.getLocation().distanceSquared(center) > 400) {
                    cleanup(stands);
                    cancel();
                    return;
                }

                // ブロックが壊されたかチェック（LECTERNはisSolid()=falseのためisAirで判定）
                if (tableLoc.getBlock().getType().isAir()) {
                    // ブロックが壊された：素材をドロップして中断
                    for (int i = 0; i < matTypes.size(); i++) {
                        world.dropItemNaturally(center, new ItemStack(matTypes.get(i),
                                materials.get(matTypes.get(i))));
                    }
                    player.sendMessage(Component.text(
                            "筆記台が壊されたためアンロックが中断されました。", NamedTextColor.RED));
                    cleanup(stands);
                    cancel();
                    return;
                }

                if (tick >= TOTAL_DURATION) {
                    // === 完了: フラッシュエフェクト + アンロック ===
                    try {
                        world.spawnParticle(Particle.END_ROD, center, 60, 0.5, 0.5, 0.5, 0.2);
                        world.spawnParticle(Particle.ENCHANT, center, 50, 0.5, 0.5, 0.5, 1.0);
                    } catch (Exception ignored) {}

                    // 完了音
                    player.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
                    player.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.8f, 1.2f);

                    // ArmorStand除去
                    cleanup(stands);

                    // グリフアンロック実行
                    completeUnlock(plugin, player, component, unlocked, saveCallback);

                    cancel();
                    return;
                }

                // === 軌道更新 ===
                double progress = (double) tick / TOTAL_DURATION;
                double radius;
                double yOffset;

                if (tick < CONVERGE_START) {
                    // 通常軌道: 半径を徐々に縮小
                    double orbitProgress = (double) tick / CONVERGE_START;
                    radius = INITIAL_RADIUS * (1.0 - orbitProgress * 0.5);
                    yOffset = orbitProgress * 0.3; // 少しずつ上昇
                } else {
                    // 収束フェーズ: 急速に中心へ
                    double convergeProgress = (double) (tick - CONVERGE_START) / (TOTAL_DURATION - CONVERGE_START);
                    radius = INITIAL_RADIUS * 0.5 * (1.0 - convergeProgress);
                    yOffset = 0.3 + convergeProgress * 0.2;
                }

                // 回転速度: 後半ほど高速
                double angularSpeed = 0.15 + progress * 0.25;

                for (int i = 0; i < stands.size(); i++) {
                    ArmorStand stand = stands.get(i);
                    if (stand.isDead()) continue;

                    double baseAngle = angleStep * i;
                    double currentAngle = baseAngle + tick * angularSpeed;

                    double x = radius * Math.cos(currentAngle);
                    double z = radius * Math.sin(currentAngle);

                    Location newLoc = center.clone().add(x, yOffset, z);
                    // ArmorStandの向きを中心に向ける
                    newLoc.setYaw((float) Math.toDegrees(Math.atan2(-x, z)));
                    stand.teleport(newLoc);
                }

                // パーティクル（軌道に沿って）
                if (tick % 2 == 0) {
                    for (ArmorStand stand : stands) {
                        if (stand.isDead()) continue;
                        world.spawnParticle(Particle.ENCHANT, stand.getLocation().add(0, 0.5, 0),
                                3, 0.1, 0.1, 0.1, 0.5);
                    }
                }

                // 収束フェーズの強調パーティクル
                if (tick >= CONVERGE_START && tick % 2 == 0) {
                    world.spawnParticle(Particle.END_ROD, center.clone().add(0, yOffset, 0),
                            5, 0.2, 0.2, 0.2, 0.02);
                }

                // 定期的にサウンド再生
                if (tick % SOUND_INTERVAL == 0 && tick > 0) {
                    float pitch = 0.8f + (float) (progress * 1.2); // 高くなっていく
                    player.playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE,
                            SoundCategory.BLOCKS, 0.6f, pitch);
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * アンロック完了処理。アニメーション終了後に呼ばれる。
     */
    private static void completeUnlock(
            ArsPaper plugin,
            Player player,
            SpellComponent component,
            Set<String> unlocked,
            Runnable saveCallback
    ) {
        // 経験値レベル消費はtryPayUnlockCostで既に行われている

        // グリフをアンロック済みに追加
        unlocked.add(component.getId().toString());
        saveCallback.run();

        // マナボーナス付与
        int currentBonus = player.getPersistentDataContainer()
                .getOrDefault(ManaKeys.GLYPH_MANA_BONUS, PersistentDataType.INTEGER, 0);
        int perGlyphBonus = plugin.getConfig().getInt("mana.per-glyph-unlock-bonus", 5);
        player.getPersistentDataContainer().set(
                ManaKeys.GLYPH_MANA_BONUS, PersistentDataType.INTEGER, currentBonus + perGlyphBonus
        );

        player.sendMessage(Component.text(
                "解放: " + component.getDisplayName() + "！ (最大マナ+" + perGlyphBonus + ")",
                NamedTextColor.GREEN
        ));
    }

    /**
     * アニメーション用ArmorStandを全て除去する。
     */
    private static void cleanup(List<ArmorStand> stands) {
        for (ArmorStand stand : stands) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
    }
}
