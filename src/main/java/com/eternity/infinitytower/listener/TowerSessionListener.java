package com.eternity.infinitytower.listener;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.tower.session.DungeonSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class TowerSessionListener implements Listener {

    private final InfinityTower plugin;

    public TowerSessionListener(InfinityTower plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (event == null) return;

        Player p = event.getEntity();
        if (p == null) return;

        // (Opcional) se algum plugin cancelar a morte / comportamento estranho, evita rodar
        // PlayerDeathEvent normalmente não é cancelável, mas deixo a proteção:
        try {
            if (event.isCancelled()) return;
        } catch (Throwable ignored) {}

        DungeonSession session = null;
        try {
            session = plugin.getInfinityTowerManager()
                    .getSessionManager()
                    .getSession(p);
        } catch (Throwable ignored) {}

        if (session != null) {
            session.handlePlayerDeath(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (event == null) return;

        Player p = event.getPlayer();
        if (p == null) return;

        DungeonSession session = null;
        try {
            session = plugin.getInfinityTowerManager()
                    .getSessionManager()
                    .getSession(p);
        } catch (Throwable ignored) {}

        if (session != null) {
            session.handlePlayerRespawn(event, p);
        }
    }
}