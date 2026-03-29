package com.arspaper.spell;

import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持続型スペルエフェクトの同時稼働タスク数を制限するユーティリティ。
 * エフェクトキー（例: "solar", "wind_burst"）ごとに最大稼働数を管理し、
 * 上限超過時は最古のタスクをキャンセルする。
 */
public final class SpellTaskLimiter {

    private SpellTaskLimiter() {}

    /** デフォルトの同時タスク上限 */
    public static final int DEFAULT_MAX_TASKS = 3;

    /** エフェクトキー → 稼働中タスクのキュー（FIFO） */
    private static final Map<String, Deque<BukkitTask>> ACTIVE_TASKS = new ConcurrentHashMap<>();

    /**
     * タスクを登録する。上限超過時は最古のタスクをキャンセル。
     * @param effectKey エフェクト識別子（例: "solar"）
     * @param task 登録するBukkitTask
     * @param maxTasks 最大同時稼働数
     */
    public static void register(String effectKey, BukkitTask task, int maxTasks) {
        Deque<BukkitTask> queue = ACTIVE_TASKS.computeIfAbsent(effectKey, k -> new ArrayDeque<>());

        // 完了済みタスクを除去
        queue.removeIf(t -> t.isCancelled());

        // 上限超過 → 最古をキャンセル
        while (queue.size() >= maxTasks) {
            BukkitTask oldest = queue.pollFirst();
            if (oldest != null && !oldest.isCancelled()) {
                oldest.cancel();
            }
        }

        queue.addLast(task);
    }

    /** デフォルト上限で登録 */
    public static void register(String effectKey, BukkitTask task) {
        register(effectKey, task, DEFAULT_MAX_TASKS);
    }

    /** プラグイン無効化時に全タスクをキャンセル */
    public static void cleanupAll() {
        ACTIVE_TASKS.values().forEach(queue -> {
            queue.forEach(t -> { if (!t.isCancelled()) t.cancel(); });
            queue.clear();
        });
        ACTIVE_TASKS.clear();
    }
}
