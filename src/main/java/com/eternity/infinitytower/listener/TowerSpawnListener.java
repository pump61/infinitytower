package com.eternity.infinitytower.listener;

import com.eternity.infinitytower.InfinityTower;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class TowerSpawnListener implements Listener {

    private final InfinityTower plugin;

    public TowerSpawnListener(InfinityTower plugin) {
        this.plugin = plugin;
    }

    /**
     * ✅ MONITOR priority
     * roda depois do spawn ser finalizado
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {

        LivingEntity living = event.getEntity();

        if (living == null) return;

        plugin.getInfinityTowerManager().handleMobSpawn(living);
    }
}