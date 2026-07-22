package com.eternity.infinitytower.listener;

import com.eternity.infinitytower.InfinityTower;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public final class TowerMobListener implements Listener {

    private final InfinityTower plugin;

    public TowerMobListener(InfinityTower plugin) {
        this.plugin = plugin;
    }

    /**
     * ✅ MONITOR priority
     * roda depois que todos plugins processaram
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {

        LivingEntity entity = event.getEntity();

        // segurança extra
        if (entity == null) return;

        // delega pro manager
        plugin.getInfinityTowerManager().handleMobDeath(entity);
    }
}