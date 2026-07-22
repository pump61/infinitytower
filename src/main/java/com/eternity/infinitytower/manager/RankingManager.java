package com.eternity.infinitytower.manager;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.database.TowerStatsRepository;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RankingManager {

    public enum BoardType { WINS, TIME }

    public record Entry(UUID uuid, long value) {}

    public record BestPlayerRecord(UUID uuid, long timeMs) {}
    public record BestPartyRecord(UUID leaderUuid, String membersCsv, long timeMs) {}

    private final InfinityTower plugin;

    // cache simples pra não spammar query em cada comando
    private final Map<String, CacheEntry<List<Entry>>> cacheBoards = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<BestPlayerRecord>> cacheBestPlayer = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<BestPartyRecord>> cacheBestParty = new ConcurrentHashMap<>();

    private long cacheTtlMs = 10_000L; // 10s padrão

    public RankingManager(InfinityTower plugin) {
        this.plugin = plugin;
        long ttl = plugin.getConfig().getLong("ranking.cache_ttl_ms", 10_000L);
        if (ttl < 0) ttl = 0;
        this.cacheTtlMs = ttl;
    }

    public void shutdown() {
        cacheBoards.clear();
        cacheBestPlayer.clear();
        cacheBestParty.clear();
    }

    public List<Entry> getTopWins(String dungeonId, int limit) {
        return getBoard(BoardType.WINS, dungeonId, limit);
    }

    public List<Entry> getTopBestTime(String dungeonId, int limit) {
        return getBoard(BoardType.TIME, dungeonId, limit);
    }

    private List<Entry> getBoard(BoardType type, String dungeonId, int limit) {
        if (dungeonId == null) dungeonId = "";
        dungeonId = dungeonId.toLowerCase(Locale.ROOT);

        if (limit <= 0) limit = 10;
        if (limit > 50) limit = 50;

        String key = type.name() + ":" + dungeonId + ":" + limit;

        CacheEntry<List<Entry>> cached = cacheBoards.get(key);
        if (cached != null && !cached.isExpired(cacheTtlMs)) {
            return cached.value();
        }

        TowerStatsRepository repo = plugin.getTowerStatsRepository();
        List<Entry> list = switch (type) {
            case WINS -> repo.selectTopWins(dungeonId, limit);
            case TIME -> repo.selectTopBestTimes(dungeonId, limit);
        };

        cacheBoards.put(key, new CacheEntry<>(list));
        return list;
    }

    public BestPlayerRecord getBestPlayerRecord(String dungeonId) {
        if (dungeonId == null) dungeonId = "";
        dungeonId = dungeonId.toLowerCase(Locale.ROOT);

        String key = dungeonId;

        CacheEntry<BestPlayerRecord> cached = cacheBestPlayer.get(key);
        if (cached != null && !cached.isExpired(cacheTtlMs)) return cached.value();

        BestPlayerRecord rec = plugin.getTowerStatsRepository().selectBestPlayerRecord(dungeonId);
        cacheBestPlayer.put(key, new CacheEntry<>(rec));
        return rec;
    }

    public BestPartyRecord getBestPartyRecord(String dungeonId) {
        if (dungeonId == null) dungeonId = "";
        dungeonId = dungeonId.toLowerCase(Locale.ROOT);

        String key = dungeonId;

        CacheEntry<BestPartyRecord> cached = cacheBestParty.get(key);
        if (cached != null && !cached.isExpired(cacheTtlMs)) return cached.value();

        BestPartyRecord rec = plugin.getTowerStatsRepository().selectBestPartyRecord(dungeonId);
        cacheBestParty.put(key, new CacheEntry<>(rec));
        return rec;
    }

    // =========================
    // FORMAT HELPERS
    // =========================

    public String nameOf(UUID uuid) {
        if (uuid == null) return "N/A";
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        String name = off.getName();
        return (name == null || name.isBlank()) ? uuid.toString() : name;
    }

    public static String formatDuration(long ms) {
        if (ms <= 0) return "0s";

        long totalSeconds = ms / 1000L;
        long s = totalSeconds % 60L;
        long totalMinutes = totalSeconds / 60L;
        long m = totalMinutes % 60L;
        long h = totalMinutes / 60L;

        if (h > 0) return h + "h " + m + "m " + s + "s";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private record CacheEntry<T>(T value, long atMs) {
        CacheEntry(T value) { this(value, System.currentTimeMillis()); }
        boolean isExpired(long ttlMs) { return ttlMs > 0 && (System.currentTimeMillis() - atMs) > ttlMs; }
    }
}