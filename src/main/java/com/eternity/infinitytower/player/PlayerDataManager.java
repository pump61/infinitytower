package com.eternity.infinitytower.player;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.database.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerDataManager {

    private final InfinityTower plugin;
    private final DatabaseManager databaseManager;

    private final Map<UUID, PlayerData> cache = new HashMap<>();

    public PlayerDataManager(InfinityTower plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public PlayerData getOrCreate(UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> new PlayerData(id, 0, 0));
    }

    public void loadPlayer(UUID uuid) {
        if (databaseManager == null || !databaseManager.isConnected()) {
            cache.putIfAbsent(uuid, new PlayerData(uuid, 0, 0));
            return;
        }

        try (PreparedStatement ps = databaseManager.getConnection().prepareStatement(
                "SELECT best_floor, extra_value FROM itower_player WHERE uuid=?"
        )) {
            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int bestFloor = rs.getInt("best_floor");
                    int extraValue = rs.getInt("extra_value");
                    cache.put(uuid, new PlayerData(uuid, bestFloor, extraValue));
                } else {
                    // cria registro
                    PlayerData data = new PlayerData(uuid, 0, 0);
                    cache.put(uuid, data);

                    try (PreparedStatement ins = databaseManager.getConnection().prepareStatement(
                            "INSERT INTO itower_player(uuid, best_floor, extra_value, updated_at) VALUES(?,?,?,?)"
                    )) {
                        ins.setString(1, uuid.toString());
                        ins.setInt(2, 0);
                        ins.setInt(3, 0);
                        ins.setLong(4, System.currentTimeMillis());
                        ins.executeUpdate();
                    }
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning(apply(
                    lang("logs.playerdata.load_failed", "Falha ao carregar PlayerData de {uuid}: {error}"),
                    Map.of("{uuid}", String.valueOf(uuid), "{error}", String.valueOf(e.getMessage()))
            ));
            cache.putIfAbsent(uuid, new PlayerData(uuid, 0, 0));
        }
    }

    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid);
        cache.remove(uuid);
    }

    public void saveAll() {
        for (UUID uuid : cache.keySet()) {
            savePlayer(uuid);
        }
    }

    private void savePlayer(UUID uuid) {
        if (databaseManager == null || !databaseManager.isConnected()) return;

        PlayerData data = cache.get(uuid);
        if (data == null) return;

        try (PreparedStatement ps = databaseManager.getConnection().prepareStatement(
                "UPDATE itower_player SET best_floor=?, extra_value=?, updated_at=? WHERE uuid=?"
        )) {
            ps.setInt(1, Math.max(0, data.getMaxFloor()));
            ps.setInt(2, Math.max(0, data.getKeys()));
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, uuid.toString());
            ps.executeUpdate();

        } catch (Exception e) {
            plugin.getLogger().warning(apply(
                    lang("logs.playerdata.save_failed", "Falha ao salvar PlayerData de {uuid}: {error}"),
                    Map.of("{uuid}", String.valueOf(uuid), "{error}", String.valueOf(e.getMessage()))
            ));
        }
    }

    // =========================
    // LANG HELPERS (logs)
    // =========================

    private String lang(String path, String def) {
        String s = plugin.getLang().getString(path, def);
        return (s == null || s.isBlank()) ? def : s;
    }

    private String apply(String msg, Map<String, String> vars) {
        if (msg == null) return "";
        String out = msg;
        for (var e : vars.entrySet()) out = out.replace(e.getKey(), e.getValue());
        return out;
    }
}