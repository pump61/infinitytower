package com.eternity.infinitytower.listener;

import com.eternity.infinitytower.InfinityTower;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.List;
import java.util.Locale;

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

        // ✅ só considera pro tracking de "mob externo" (ex: add invocado por MythicMobs)
        // motivos de spawn selvagem/natural (NATURAL, SPAWNER de bloco, etc.) nunca entram aqui,
        // senão qualquer mob comum do mundo perto da arena seria contado como parte da run.
        if (!isTrackableSpawnReason(event.getSpawnReason())) return;

        plugin.getInfinityTowerManager().handleMobSpawn(living);
    }

    private boolean isTrackableSpawnReason(CreatureSpawnEvent.SpawnReason reason) {
        if (reason == null) return false;

        List<String> allowed = plugin.getConfig().getStringList("tower.tracking.allowed_spawn_reasons");
        if (allowed == null || allowed.isEmpty()) {
            allowed = List.of("CUSTOM", "SPAWNER");
        }

        String reasonName = reason.name();
        for (String a : allowed) {
            if (a != null && a.trim().toUpperCase(Locale.ROOT).equals(reasonName)) return true;
        }
        return false;
    }
}