package com.eternity.infinitytower.key;

import com.eternity.infinitytower.InfinityTower;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.LocalDate;
import java.util.*;

public final class AccessLimiter {

    private final InfinityTower plugin;

    // uuid -> dungeonId -> lastEnterMillis
    private final Map<UUID, Map<String, Long>> lastEnter = new HashMap<>();

    // uuid -> date -> dungeonId -> count
    private final Map<UUID, LocalDate> dayMarker = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> dayCount = new HashMap<>();

    public AccessLimiter(InfinityTower plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled(String dungeonId) {
        FileConfiguration d = plugin.getDungeonRegistry().getDungeon(dungeonId);
        if (d == null) return false;
        return d.getBoolean("access.limiter.enabled", false);
    }

    public String checkAndMark(UUID playerId, String dungeonId) {
        FileConfiguration d = plugin.getDungeonRegistry().getDungeon(dungeonId);
        if (d == null) return null;

        boolean enabled = d.getBoolean("access.limiter.enabled", false);
        if (!enabled) return null;

        int cooldown = Math.max(0, d.getInt("access.limiter.cooldown_seconds", 0));
        int maxPerDay = Math.max(0, d.getInt("access.limiter.max_entries_per_day", 0));

        // reset diário por player (simples e eficiente)
        LocalDate today = LocalDate.now();
        LocalDate marked = dayMarker.get(playerId);
        if (marked == null || !marked.equals(today)) {
            dayMarker.put(playerId, today);
            dayCount.put(playerId, new HashMap<>());
        }

        String did = dungeonId.toLowerCase(Locale.ROOT);

        // cooldown
        if (cooldown > 0) {
            long now = System.currentTimeMillis();
            long last = lastEnter
                    .computeIfAbsent(playerId, k -> new HashMap<>())
                    .getOrDefault(did, 0L);

            long diff = now - last;
            long need = cooldown * 1000L;

            if (diff < need) {
                long left = (need - diff + 999) / 1000;
                return "cooldown:" + left; // mensagem tratada fora
            }
        }

        // max por dia
        if (maxPerDay > 0) {
            Map<String, Integer> map = dayCount.computeIfAbsent(playerId, k -> new HashMap<>());
            int used = map.getOrDefault(did, 0);
            if (used >= maxPerDay) {
                return "daylimit";
            }
        }

        // passou: marca
        if (cooldown > 0) {
            lastEnter.computeIfAbsent(playerId, k -> new HashMap<>()).put(did, System.currentTimeMillis());
        }
        if (maxPerDay > 0) {
            Map<String, Integer> map = dayCount.computeIfAbsent(playerId, k -> new HashMap<>());
            map.put(did, map.getOrDefault(did, 0) + 1);
        }

        return null; // ok
    }
}
