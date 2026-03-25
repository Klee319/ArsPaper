package com.arspaper.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

/**
 * WorldGuard連携ユーティリティ。
 * リフレクションベースでWorldGuardが無くても動作する。
 * WorldGuardが存在する場合のみリージョンのPVPフラグを確認する。
 */
public final class WorldGuardHelper {

    private static boolean initialized = false;
    private static boolean available = false;
    private static Method adaptMethod;
    private static Object pvpFlag;

    private WorldGuardHelper() {}

    /**
     * 指定位置でPvPが許可されているか確認する。
     * WorldGuardが無い場合はtrueを返す。
     */
    public static boolean isPvPAllowed(Location location) {
        if (!initialized) {
            initialized = true;
            available = initialize();
        }
        if (!available) return true;

        try {
            // WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
            Object worldGuard = Class.forName("com.sk89q.worldguard.WorldGuard")
                .getMethod("getInstance").invoke(null);
            Object platform = worldGuard.getClass().getMethod("getPlatform").invoke(worldGuard);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = container.getClass().getMethod("createQuery").invoke(container);

            // BukkitAdapter.adapt(location)
            Object wgLocation = adaptMethod.invoke(null, location);

            // query.testState(wgLocation, null, Flags.PVP)
            Class<?> stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Object flagArray = Array.newInstance(stateFlagClass, 1);
            Array.set(flagArray, 0, pvpFlag);

            for (Method m : query.getClass().getMethods()) {
                if (m.getName().equals("testState") && m.getParameterCount() == 3) {
                    return (boolean) m.invoke(query, wgLocation, null, flagArray);
                }
            }
        } catch (Exception e) {
            // WorldGuard API呼び出しエラー - PVP許可として扱う
        }
        return true;
    }

    private static boolean initialize() {
        try {
            if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return false;

            adaptMethod = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")
                .getMethod("adapt", Location.class);
            pvpFlag = Class.forName("com.sk89q.worldguard.protection.flags.Flags")
                .getField("PVP").get(null);

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
