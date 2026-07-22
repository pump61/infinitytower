package com.eternity.infinitytower.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

public final class EssentialsBackUtil {

    private EssentialsBackUtil() {}

    /**
     * Força o /back do Essentials/EssentialsX pra um local seguro.
     * - Compatível com variações de assinatura do getUser
     * - Tenta setar múltiplos campos (depende da versão)
     */
    public static void overrideBackLocation(Player player, Location safeLocation) {
        if (player == null || safeLocation == null) return;

        World w = safeLocation.getWorld();
        if (w == null) return;

        // clona pra garantir que ninguém altere depois
        Location safe = safeLocation.clone();

        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess == null || !ess.isEnabled()) return;

        try {
            Object user = getUserSafely(ess, player);
            if (user == null) return;

            // 1) setLastLocation(Location) (mais comum)
            invokeIfExists(user, "setLastLocation", new Class<?>[]{Location.class}, new Object[]{safe});

            // 2) Algumas versões usam "last teleport/back"
            invokeIfExists(user, "setLastTeleportLocation", new Class<?>[]{Location.class}, new Object[]{safe});
            invokeIfExists(user, "setLastTeleport", new Class<?>[]{Location.class}, new Object[]{safe});

            // 3) fallback extra (raros forks)
            invokeIfExists(user, "setBackLocation", new Class<?>[]{Location.class}, new Object[]{safe});
            invokeIfExists(user, "setLastBackLocation", new Class<?>[]{Location.class}, new Object[]{safe});

        } catch (Throwable ignored) {
            // intencionalmente silencioso
        }
    }

    private static Object getUserSafely(Plugin essentialsPlugin, Player player) {
        try {
            // getUser(Player)
            Method m = essentialsPlugin.getClass().getMethod("getUser", Player.class);
            return m.invoke(essentialsPlugin, player);
        } catch (Throwable ignored) {}

        UUID uuid = player.getUniqueId();

        try {
            // getUser(UUID)
            Method m = essentialsPlugin.getClass().getMethod("getUser", UUID.class);
            return m.invoke(essentialsPlugin, uuid);
        } catch (Throwable ignored) {}

        try {
            // getUser(String)
            Method m = essentialsPlugin.getClass().getMethod("getUser", String.class);
            return m.invoke(essentialsPlugin, player.getName());
        } catch (Throwable ignored) {}

        return null;
    }

    private static void invokeIfExists(Object target, String method, Class<?>[] sig, Object[] args) {
        try {
            Method m = target.getClass().getMethod(method, sig);
            m.invoke(target, args);
        } catch (Throwable ignored) {}
    }
}