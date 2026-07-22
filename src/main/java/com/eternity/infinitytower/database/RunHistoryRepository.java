package com.eternity.infinitytower.database;

import com.eternity.infinitytower.InfinityTower;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.sql.DatabaseMetaData;

public final class RunHistoryRepository {

    private final InfinityTower plugin;
    private final DatabaseManager db;

    public RunHistoryRepository(InfinityTower plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    // =========================
    // NEW API (START / END)
    // =========================

    /**
     * Registra o START de uma run.
     *
     * Tabela esperada:
     * itower_run_history(
     *   session_id, dungeon_id, mode, leader_uuid, members,
     *   started_at, ended_at, duration_ms, reached_floor, finished, end_reason,
     *   leader_name, members_names (opcional)
     * )
     */
    public void logStart(UUID sessionId,
                         String dungeonId,
                         String mode,
                         UUID leaderUuid,
                         Set<UUID> members) {

        if (db == null || !db.isConnected()) return;
        if (sessionId == null) return;

        if (dungeonId == null) dungeonId = "";
        if (mode == null || mode.isBlank()) mode = "SOLO";
        if (leaderUuid == null) leaderUuid = new UUID(0L, 0L);

        String membersCsv = membersCsv(members);
        long now = System.currentTimeMillis();

        // nomes (pra não ficar só UUID no log/menu)
        String leaderName = nameOf(leaderUuid);
        String membersNames = membersNamesCsv(members);

        boolean hasNamesCols = hasColumn("itower_run_history", "leader_name")
                && hasColumn("itower_run_history", "members_names");

        String sql;
        if (db.isMySQL()) {
            if (hasNamesCols) {
                sql = "INSERT INTO itower_run_history " +
                        "(session_id, dungeon_id, mode, leader_uuid, members, started_at, ended_at, duration_ms, reached_floor, finished, end_reason, leader_name, members_names) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 0, 0, 0, 0, '', ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "dungeon_id = VALUES(dungeon_id), " +
                        "mode = VALUES(mode), " +
                        "leader_uuid = VALUES(leader_uuid), " +
                        "members = VALUES(members), " +
                        "started_at = VALUES(started_at), " +
                        "leader_name = VALUES(leader_name), " +
                        "members_names = VALUES(members_names)";
            } else {
                sql = "INSERT INTO itower_run_history " +
                        "(session_id, dungeon_id, mode, leader_uuid, members, started_at, ended_at, duration_ms, reached_floor, finished, end_reason) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 0, 0, 0, 0, '') " +
                        "ON DUPLICATE KEY UPDATE " +
                        "dungeon_id = VALUES(dungeon_id), " +
                        "mode = VALUES(mode), " +
                        "leader_uuid = VALUES(leader_uuid), " +
                        "members = VALUES(members), " +
                        "started_at = VALUES(started_at)";
            }
        } else {
            // SQLite: ON CONFLICT(session_id) exige UNIQUE/PRIMARY KEY no session_id (unique index resolve)
            if (hasNamesCols) {
                sql = "INSERT INTO itower_run_history " +
                        "(session_id, dungeon_id, mode, leader_uuid, members, started_at, ended_at, duration_ms, reached_floor, finished, end_reason, leader_name, members_names) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 0, 0, 0, 0, '', ?, ?) " +
                        "ON CONFLICT(session_id) DO UPDATE SET " +
                        "dungeon_id = excluded.dungeon_id, " +
                        "mode = excluded.mode, " +
                        "leader_uuid = excluded.leader_uuid, " +
                        "members = excluded.members, " +
                        "started_at = excluded.started_at, " +
                        "leader_name = excluded.leader_name, " +
                        "members_names = excluded.members_names";
            } else {
                sql = "INSERT INTO itower_run_history " +
                        "(session_id, dungeon_id, mode, leader_uuid, members, started_at, ended_at, duration_ms, reached_floor, finished, end_reason) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 0, 0, 0, 0, '') " +
                        "ON CONFLICT(session_id) DO UPDATE SET " +
                        "dungeon_id = excluded.dungeon_id, " +
                        "mode = excluded.mode, " +
                        "leader_uuid = excluded.leader_uuid, " +
                        "members = excluded.members, " +
                        "started_at = excluded.started_at";
            }
        }

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, sessionId.toString());
            ps.setString(2, dungeonId);
            ps.setString(3, mode.toUpperCase(Locale.ROOT));
            ps.setString(4, leaderUuid.toString());
            ps.setString(5, membersCsv);
            ps.setLong(6, now);

            if (hasNamesCols) {
                ps.setString(7, leaderName);
                ps.setString(8, membersNames);
            }

            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB logStart falhou: " + e.getMessage());
        }
    }

    /**
     * Registra o END de uma run (fecha a run do session_id).
     */
    public void logEnd(UUID sessionId,
                       boolean finished,
                       int reachedFloor,
                       long durationMs,
                       String endReason) {

        if (db == null || !db.isConnected()) return;
        if (sessionId == null) return;

        if (durationMs < 0) durationMs = 0;
        if (endReason == null) endReason = "";

        long now = System.currentTimeMillis();

        String sql = "UPDATE itower_run_history SET " +
                "ended_at = ?, " +
                "duration_ms = ?, " +
                "reached_floor = ?, " +
                "finished = ?, " +
                "end_reason = ? " +
                "WHERE session_id = ?";

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, now);
            ps.setLong(2, durationMs);
            ps.setInt(3, Math.max(0, reachedFloor));
            ps.setInt(4, finished ? 1 : 0);
            ps.setString(5, endReason);
            ps.setString(6, sessionId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("DB logEnd falhou: " + e.getMessage());
        }
    }

    // =========================
    // COMPAT API (OLD insertRun)
    // Mantido pra não quebrar chamadas antigas.
    // Faz um "start + end" com session fake.
    // =========================

    public void insertRun(
            String dungeonId,
            boolean finished,
            int reachedFloor,
            long durationMs,
            boolean isParty,
            String partyKey,
            int partySize,
            UUID leaderUuid,
            String membersCsv
    ) {
        UUID sid = UUID.randomUUID();
        Set<UUID> members = parseMembersCsvToSet(membersCsv);
        String mode = isParty ? "PARTY" : "SOLO";

        logStart(sid, dungeonId, mode, leaderUuid, members);
        logEnd(sid, finished, reachedFloor, durationMs, finished ? "FINISH" : "END");
    }

    // =========================
    // READS (se você quiser usar no menu depois)
    // =========================

    public PlayerStats selectPlayerStats(UUID playerUuid) {
        if (db == null || !db.isConnected()) return new PlayerStats(0, 0, 0, 0);

        String uuid = (playerUuid == null ? "" : playerUuid.toString());

        String sql =
                "SELECT " +
                        "SUM(CASE WHEN mode = 'SOLO' THEN 1 ELSE 0 END) AS solo_runs, " +
                        "SUM(CASE WHEN mode = 'PARTY' THEN 1 ELSE 0 END) AS party_runs, " +
                        "SUM(CASE WHEN finished = 1 THEN 1 ELSE 0 END) AS wins, " +
                        "SUM(CASE WHEN ended_at > 0 AND finished = 0 THEN 1 ELSE 0 END) AS losses " +
                        "FROM itower_run_history " +
                        "WHERE (members LIKE ? OR leader_uuid = ?)";

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, "%" + uuid + "%");
            ps.setString(2, uuid);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long solo = rs.getLong("solo_runs");
                    long party = rs.getLong("party_runs");
                    long wins = rs.getLong("wins");
                    long losses = rs.getLong("losses");
                    return new PlayerStats(solo, party, wins, losses);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB selectPlayerStats falhou: " + e.getMessage());
        }

        return new PlayerStats(0, 0, 0, 0);
    }

    public record PlayerStats(long soloRuns, long partyRuns, long wins, long losses) {}

    // =========================
    // HELPERS
    // =========================

    public static String partyKey(Set<UUID> members) {
        if (members == null || members.isEmpty()) return "";
        List<String> list = members.stream().map(UUID::toString).sorted().toList();
        return String.join("|", list);
    }

    public static String membersCsv(Set<UUID> members) {
        if (members == null || members.isEmpty()) return "";
        return members.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
    }

    public static String membersNamesCsv(Set<UUID> members) {
        if (members == null || members.isEmpty()) return "";
        List<String> names = new ArrayList<>();
        for (UUID u : members) {
            String n = nameOfStatic(u);
            if (n != null && !n.isBlank()) names.add(n);
        }
        return String.join(",", names);
    }

    private String nameOf(UUID uuid) {
        return nameOfStatic(uuid);
    }

    private static String nameOfStatic(UUID uuid) {
        if (uuid == null) return "";
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        // offline: tenta cache
        try {
            var off = Bukkit.getOfflinePlayer(uuid);
            String n = off.getName();
            return (n == null ? "" : n);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static Set<UUID> parseMembersCsvToSet(String membersCsv) {
        if (membersCsv == null || membersCsv.isBlank()) return new HashSet<>();
        Set<UUID> out = new HashSet<>();
        String[] parts = membersCsv.split(",");
        for (String p : parts) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            try { out.add(UUID.fromString(t)); } catch (Exception ignored) {}
        }
        return out;
    }

    private boolean hasColumn(String table, String column) {
        try {
            if (db == null || !db.isConnected()) return false;
            DatabaseMetaData md = db.getConnection().getMetaData();
            try (ResultSet rs = md.getColumns(null, null, table, column)) {
                return rs.next();
            }
        } catch (Exception ignored) {
            return false;
        }
    }
}