package com.arspaper.item;

/**
 * 触媒(catalysts.yml)のランダムロールステの範囲(min-max)。
 *
 * <p>TrinityForge の {@code com.trinityforge.stats.StatRange} と同じ意味を持つが、
 * TrinityForge未ロード環境でも {@link CatalystConfig} の読み込み自体が失敗しないよう、
 * ArsPaper側で独立して保持する（TF型への変換は {@link com.arspaper.integration.TrinityForgeBridge}
 * が登録直前にのみ行い、TF未ロード時はtry/catchで安全側にフォールバックする）。
 */
public record CatalystStatRange(double min, double max) {
}
