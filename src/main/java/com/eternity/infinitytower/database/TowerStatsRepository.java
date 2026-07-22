package com.eternity.infinitytower.database;

import com.eternity.infinitytower.InfinityTower;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public final class TowerStatsRepository {

    public enum Mode { SOLO, PARTY }

    private final InfinityTower plugin;
    private final DatabaseManager db;

    public TowerStatsRepository(InfinityTower plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    // =========================================================
    // WRITE (DungeonSession / runs)
    // =========================================================

    public void recordRun(UUID player, String dungeonId, Mode mode, int reachedFloor, boolean finished, long durationMs) {
        if (db == null || !db.isConnected()) return;
        if (player == null) return;
        if (dungeonId == null || dungeonId.isBlank()) return;

        if (durationMs < 0) durationMs = 0;

        upsertPlayerDungeon(player, dungeonId, mode, finished, reachedFloor, durationMs);

        if (finished && mode == Mode.SOLO) {
            upsertBestPlayerIfBetter(dungeonId, player, durationMs);
        }
    }

    public void recordRun(Set<UUID> players, String dungeonId, boolean finished, int reachedFloor, long durationMs) {
        if (db == null || !db.isConnected()) return;
        if (dungeonId == null || dungeonId.isBlank()) return;
        if (players == null || players.isEmpty()) return;

        if (durationMs < 0) durationMs = 0;

        Mode mode = (players.size() >= 2) ? Mode.PARTY : Mode.SOLO;

        for (UUID pid : players) {
            upsertPlayerDungeon(pid, dungeonId, mode, finished, reachedFloor, durationMs);
        }

        if (!finished) return;

        if (players.size() == 1) {
            UUID only = players.iterator().next();
            upsertBestPlayerIfBetter(dungeonId, only, durationMs);
        } else {
            UUID leader = players.iterator().next();
            String membersCsv = RunHistoryRepository.membersCsv(players);
            upsertBestPartyIfBetter(dungeonId, leader, membersCsv, durationMs);
        }
    }

    private void upsertPlayerDungeon(UUID uuid, String dungeonId, Mode mode, boolean finished, int reachedFloor, long durationMs) {
        boolean mysql = db.isMySQL();

        // OBS: sua tabela tem solo_runs/party_runs e wins/losses/best_time_ms/best_floor
        // então aqui a gente atualiza:
        // - run do modo (solo_runs OU party_runs)
        // - wins/losses
        // - best_floor
        // - best_time_ms (só quando win e tempo melhor)

        String runCol = (mode == Mode.PARTY) ? "party_runs" : "solo_runs";
        int winsToAdd = finished ? 1 : 0;
        int lossesToAdd = finished ? 0 : 1;

        String sql;
        if (mysql) {
            sql = "INSERT INTO itower_player_dungeon " +
                    "(uuid, dungeon_id, solo_runs, party_runs, wins, losses, best_time_ms, best_floor) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    runCol + " = " + runCol + " + 1, " +
                    "wins = wins + VALUES(wins), " +
                    "losses = losses + VALUES(losses), " +
                    "best_floor = GREATEST(best_floor, VALUES(best_floor)), " +
                    "best_time_ms = CASE " +
                    "  WHEN VALUES(wins) = 0 THEN best_time_ms " +
                    "  WHEN best_time_ms = 0 THEN VALUES(best_time_ms) " +
                    "  WHEN VALUES(best_time_ms) < best_time_ms THEN VALUES(best_time_ms) " +
                    "  ELSE best_time_ms " +
                    "END";
        } else {
            sql = "INSERT INTO itower_player_dungeon " +
                    "(uuid, dungeon_id, solo_runs, party_runs, wins, losses, best_time_ms, best_floor) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(uuid, dungeon_id) DO UPDATE SET " +
                    runCol + " = " + runCol + " + 1, " +
                    "wins = wins + excluded.wins, " +
                    "losses = losses + excluded.losses, " +
                    "best_floor = CASE WHEN excluded.best_floor > best_floor THEN excluded.best_floor ELSE best_floor END, " +
                    "best_time_ms = CASE " +
                    "  WHEN excluded.wins = 0 THEN best_time_ms " +
                    "  WHEN best_time_ms = 0 THEN excluded.best_time_ms " +
                    "  WHEN excluded.best_time_ms < best_time_ms THEN excluded.best_time_ms " +
                    "  ELSE best_time_ms " +
                    "END";
        }

        int soloRuns = (mode == Mode.SOLO) ? 1 : 0;
        int partyRuns = (mode == Mode.PARTY) ? 1 : 0;

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, dungeonId);
            ps.setInt(3, soloRuns);
            ps.setInt(4, partyRuns);
            ps.setInt(5, winsToAdd);
            ps.setInt(6, lossesToAdd);
            ps.setLong(7, finished ? Math.max(0L, durationMs) : 0L);
            ps.setInt(8, Math.max(0, reachedFloor));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB upsertPlayerDungeon falhou: " + e.getMessage());
        }
    }

    private void upsertBestPlayerIfBetter(String dungeonId, UUID uuid, long durationMs) {
        if (db == null || !db.isConnected()) return;

        boolean mysql = db.isMySQL();
        long now = System.currentTimeMillis();

        String sql;
        if (mysql) {
            sql = "INSERT INTO itower_dungeon_best_player (dungeon_id, uuid, time_ms, updated_at) " +
                    "VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "uuid = IF(VALUES(time_ms) < time_ms, VALUES(uuid), uuid), " +
                    "time_ms = LEAST(time_ms, VALUES(time_ms)), " +
                    "updated_at = IF(VALUES(time_ms) < time_ms, VALUES(updated_at), updated_at)";
        } else {
            sql = "INSERT INTO itower_dungeon_best_player (dungeon_id, uuid, time_ms, updated_at) " +
                    "VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(dungeon_id) DO UPDATE SET " +
                    "uuid = CASE WHEN excluded.time_ms < time_ms THEN excluded.uuid ELSE uuid END, " +
                    "time_ms = CASE WHEN excluded.time_ms < time_ms THEN excluded.time_ms ELSE time_ms END, " +
                    "updated_at = CASE WHEN excluded.time_ms < time_ms THEN excluded.updated_at ELSE updated_at END";
        }

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, dungeonId);
            ps.setString(2, uuid.toString());
            ps.setLong(3, Math.max(0L, durationMs));
            ps.setLong(4, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB upsertBestPlayerIfBetter falhou: " + e.getMessage());
        }
    }

    private void upsertBestPartyIfBetter(String dungeonId, UUID leaderUuid, String membersCsv, long durationMs) {
        if (db == null || !db.isConnected()) return;

        boolean mysql = db.isMySQL();
        long now = System.currentTimeMillis();

        String sql;
        if (mysql) {
            sql = "INSERT INTO itower_dungeon_best_party (dungeon_id, leader_uuid, members, time_ms, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "leader_uuid = IF(VALUES(time_ms) < time_ms, VALUES(leader_uuid), leader_uuid), " +
                    "members = IF(VALUES(time_ms) < time_ms, VALUES(members), members), " +
                    "time_ms = LEAST(time_ms, VALUES(time_ms)), " +
                    "updated_at = IF(VALUES(time_ms) < time_ms, VALUES(updated_at), updated_at)";
        } else {
            sql = "INSERT INTO itower_dungeon_best_party (dungeon_id, leader_uuid, members, time_ms, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?) " +
                    "ON CONFLICT(dungeon_id) DO UPDATE SET " +
                    "leader_uuid = CASE WHEN excluded.time_ms < time_ms THEN excluded.leader_uuid ELSE leader_uuid END, " +
                    "members = CASE WHEN excluded.time_ms < time_ms THEN excluded.members ELSE members END, " +
                    "time_ms = CASE WHEN excluded.time_ms < time_ms THEN excluded.time_ms ELSE time_ms END, " +
                    "updated_at = CASE WHEN excluded.time_ms < time_ms THEN excluded.updated_at ELSE updated_at END";
        }

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, dungeonId);
            ps.setString(2, leaderUuid.toString());
            ps.setString(3, membersCsv == null ? "" : membersCsv);
            ps.setLong(4, Math.max(0L, durationMs));
            ps.setLong(5, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB upsertBestPartyIfBetter falhou: " + e.getMessage());
        }
    }

    // =========================================================
    // READS (RankingManager / Placeholders)
    // =========================================================

    public java.util.List<com.eternity.infinitytower.manager.RankingManager.Entry> selectTopWins(String dungeonId, int limit) {
        if (db == null || !db.isConnected()) return java.util.Collections.emptyList();
        if (dungeonId == null || dungeonId.isBlank()) return java.util.Collections.emptyList();

        String sql = "SELECT uuid, wins FROM itower_player_dungeon " +
                "WHERE dungeon_id = ? " +
                "ORDER BY wins DESC, uuid ASC " +
                "LIMIT ?";

        java.util.List<com.eternity.infinitytower.manager.RankingManager.Entry> out = new java.util.ArrayList<>();

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, dungeonId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    long wins = rs.getLong("wins");
                    out.add(new com.eternity.infinitytower.manager.RankingManager.Entry(uuid, wins));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB selectTopWins falhou: " + e.getMessage());
        }

        return out;
    }

    public java.util.List<com.eternity.infinitytower.manager.RankingManager.Entry> selectTopBestTimes(String dungeonId, int limit) {
        if (db == null || !db.isConnected()) return java.util.Collections.emptyList();
        if (dungeonId == null || dungeonId.isBlank()) return java.util.Collections.emptyList();

        String sql = "SELECT uuid, best_time_ms FROM itower_player_dungeon " +
                "WHERE dungeon_id = ? AND best_time_ms > 0 " +
                "ORDER BY best_time_ms ASC, uuid ASC " +
                "LIMIT ?";

        java.util.List<com.eternity.infinitytower.manager.RankingManager.Entry> out = new java.util.ArrayList<>();

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, dungeonId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    long timeMs = rs.getLong("best_time_ms");
                    out.add(new com.eternity.infinitytower.manager.RankingManager.Entry(uuid, timeMs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB selectTopBestTimes falhou: " + e.getMessage());
        }

        return out;
    }

    // ✅ ESTES 2 MÉTODOS são os que o seu erro tá pedindo no RankingManager
    public com.eternity.infinitytower.manager.RankingManager.BestPlayerRecord selectBestPlayerRecord(String dungeonId) {
        if (db == null || !db.isConnected()) return null;
        if (dungeonId == null || dungeonId.isBlank()) return null;

        String sql = "SELECT uuid, time_ms FROM itower_dungeon_best_player WHERE dungeon_id = ? LIMIT 1";

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, dungeonId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    long timeMs = rs.getLong("time_ms");
                    return new com.eternity.infinitytower.manager.RankingManager.BestPlayerRecord(uuid, timeMs);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB selectBestPlayerRecord falhou: " + e.getMessage());
        }

        return null;
    }

    public com.eternity.infinitytower.manager.RankingManager.BestPartyRecord selectBestPartyRecord(String dungeonId) {
        if (db == null || !db.isConnected()) return null;
        if (dungeonId == null || dungeonId.isBlank()) return null;

        String sql = "SELECT leader_uuid, members, time_ms FROM itower_dungeon_best_party WHERE dungeon_id = ? LIMIT 1";

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, dungeonId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UUID leader = UUID.fromString(rs.getString("leader_uuid"));
                    String members = rs.getString("members");
                    long timeMs = rs.getLong("time_ms");
                    return new com.eternity.infinitytower.manager.RankingManager.BestPartyRecord(
                            leader,
                            members == null ? "" : members,
                            timeMs
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB selectBestPartyRecord falhou: " + e.getMessage());
        }

        return null;
    }
}