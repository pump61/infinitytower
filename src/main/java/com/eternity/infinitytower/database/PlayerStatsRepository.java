package com.eternity.infinitytower.database;

import com.eternity.infinitytower.InfinityTower;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class PlayerStatsRepository {

    private final InfinityTower plugin;
    private final DatabaseManager db;

    public PlayerStatsRepository(InfinityTower plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    // =====================================================
    // RECORD RUN
    // =====================================================

    public void recordRun(UUID playerUuid, boolean party, boolean finished) {

        if (db == null || !db.isConnected()) return;

        String runsCol = party ? "party_runs" : "solo_runs";
        String winsCol = party ? "party_wins" : "solo_wins";
        String lossCol = party ? "party_losses" : "solo_losses";

        String sql;

        if (db.isMySQL()) {

            sql =
                "INSERT INTO itower_player_stats " +
                "(uuid, solo_runs, solo_wins, solo_losses, party_runs, party_wins, party_losses, updated_at) " +
                "VALUES (?,0,0,0,0,0,0,?) " +
                "ON DUPLICATE KEY UPDATE " +
                runsCol + " = " + runsCol + " + 1, " +
                (finished
                        ? winsCol + " = " + winsCol + " + 1"
                        : lossCol + " = " + lossCol + " + 1") +
                ", updated_at = ?";

        } else {

            sql =
                "INSERT INTO itower_player_stats " +
                "(uuid, solo_runs, solo_wins, solo_losses, party_runs, party_wins, party_losses, updated_at) " +
                "VALUES (?,0,0,0,0,0,0,?) " +
                "ON CONFLICT(uuid) DO UPDATE SET " +
                runsCol + " = " + runsCol + " + 1, " +
                (finished
                        ? winsCol + " = " + winsCol + " + 1"
                        : lossCol + " = " + lossCol + " + 1") +
                ", updated_at = ?";
        }

        long now = System.currentTimeMillis();

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {

            ps.setString(1, playerUuid.toString());
            ps.setLong(2, now);
            ps.setLong(3, now);

            ps.executeUpdate();

        } catch (SQLException e) {

            plugin.getLogger().warning("PlayerStatsRepository recordRun falhou: " + e.getMessage());

        }
    }

    // =====================================================
    // READ STATS (USADO PELO MENU FUTURO)
    // =====================================================

    public PlayerStats getStats(UUID playerUuid) {

        if (db == null || !db.isConnected()) return PlayerStats.empty();

        String sql =
                "SELECT solo_runs, solo_wins, solo_losses, " +
                "party_runs, party_wins, party_losses " +
                "FROM itower_player_stats WHERE uuid = ?";

        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {

            ps.setString(1, playerUuid.toString());

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    return PlayerStats.empty();
                }

                return new PlayerStats(
                        rs.getInt("solo_runs"),
                        rs.getInt("solo_wins"),
                        rs.getInt("solo_losses"),
                        rs.getInt("party_runs"),
                        rs.getInt("party_wins"),
                        rs.getInt("party_losses")
                );

            }

        } catch (SQLException e) {

            plugin.getLogger().warning("PlayerStatsRepository getStats falhou: " + e.getMessage());

        }

        return PlayerStats.empty();
    }

    // =====================================================
    // DATA CLASS
    // =====================================================

    public static final class PlayerStats {

        public final int soloRuns;
        public final int soloWins;
        public final int soloLosses;

        public final int partyRuns;
        public final int partyWins;
        public final int partyLosses;

        public PlayerStats(
                int soloRuns,
                int soloWins,
                int soloLosses,
                int partyRuns,
                int partyWins,
                int partyLosses
        ) {
            this.soloRuns = soloRuns;
            this.soloWins = soloWins;
            this.soloLosses = soloLosses;
            this.partyRuns = partyRuns;
            this.partyWins = partyWins;
            this.partyLosses = partyLosses;
        }

        public static PlayerStats empty() {
            return new PlayerStats(0,0,0,0,0,0);
        }
    }
}
