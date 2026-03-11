package com.arspaper.source;

import org.bukkit.Location;

/**
 * Sourceを貯蔵・入出力できるブロックのインターフェース。
 * Source Jar, Relay, Sourcelink 等が実装する。
 */
public interface SourceContainer {

    /** このコンテナのワールド内座標 */
    Location getLocation();

    /** 現在のSource量 */
    int getSourceAmount();

    /** 最大Source容量 */
    int getMaxSource();

    /**
     * Sourceを追加する。
     * @return 実際に追加された量
     */
    int addSource(int amount);

    /**
     * Sourceを消費する。
     * @return 消費に成功したか
     */
    boolean consumeSource(int amount);

    /** このコンテナがSourceを受け取れるか */
    default boolean canReceive() {
        return getSourceAmount() < getMaxSource();
    }

    /** このコンテナがSourceを送信できるか */
    default boolean canSend() {
        return getSourceAmount() > 0;
    }
}
