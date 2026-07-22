package com.eternity.infinitytower.database;

import com.eternity.infinitytower.InfinityTower;

import java.io.File;
import java.sql.*;
import java.util.Locale;
import java.util.Map;

public final class DatabaseManager {

    private final InfinityTower plugin;
    private Connection connection;
    private boolean mysql = false;

    public DatabaseManager(InfinityTower plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        boolean enabled = plugin.getConfig().getBoolean("database.enabled", false);

        if (!enabled) {
            plugin.getLogger().warning(langRaw("database.disabled",
                    "Banco de dados desativado (database.enabled=false)."));
            this.connection = null;
            this.mysql = false;
            return;
        }

        String type = plugin.getConfig().getString("database.type", "sqlite");
        if (type == null) type = "sqlite";
        type = type.toLowerCase(Locale.ROOT);

        try {
            if (type.equals("mysql")) {
                connectMySQL();
                this.mysql = true;
            } else {
                connectSQLite();
                this.mysql = false;
            }

            plugin.getLogger().info(fmt(
                    langRaw("database.connect_success",
                            "Conexão com banco iniciada: {type}"),
                    Map.of("{type}", (mysql ? "mysql" : "sqlite"))
            ));

            // ✅ cria tabelas base
            createTables();

            // ✅ garante constraints/colunas/tabelas necessárias pro código atual
            ensureSchema();

        } catch (Exception e) {
            plugin.getLogger().severe(fmt(
                    langRaw("database.connect_fail",
                            "Falha ao conectar no banco ({type}): {error}"),
                    Map.of(
                            "{type}", type,
                            "{error}", String.valueOf(e.getMessage())
                    )
            ));
            this.connection = null;
            this.mysql = false;
        }
    }

    private void connectSQLite() throws Exception {
        String fileName = plugin.getConfig().getString("database.sqlite.file", "database.db");
        if (fileName == null || fileName.isBlank()) fileName = "database.db";

        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

        File dbFile = new File(plugin.getDataFolder(), fileName);
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        this.connection = DriverManager.getConnection(url);
    }

    private void connectMySQL() throws Exception {
        String host = plugin.getConfig().getString("database.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfig().getString("database.mysql.database", "infinitytower");
        String user = plugin.getConfig().getString("database.mysql.user", "root");
        String password = plugin.getConfig().getString("database.mysql.password", "");
        boolean ssl = plugin.getConfig().getBoolean("database.mysql.useSSL", false);

        if (host == null) host = "localhost";
        if (database == null || database.isBlank()) database = "infinitytower";
        if (user == null || user.isBlank()) user = "root";
        if (password == null) password = "";

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + ssl
                + "&allowPublicKeyRetrieval=true"
                + "&characterEncoding=utf8"
                + "&useUnicode=true"
                + "&serverTimezone=UTC";

        this.connection = DriverManager.getConnection(url, user, password);
    }

    private void createTables() throws SQLException {
        if (!isConnected()) return;

        try (Statement st = connection.createStatement()) {

            if (mysql) {
                // =========================================
                // PLAYER DATA (cache simples)
                // =========================================
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS itower_player (" +
                                "uuid VARCHAR(36) PRIMARY KEY, " +
                                "best_floor INT NOT NULL DEFAULT 0, " +
                                "extra_value INT NOT NULL DEFAULT 0, " +
                                "updated_at BIGINT NOT NULL DEFAULT 0" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                );

                // =========================================
                // STATS GLOBAIS (menu /tower stats)
                // =========================================
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS itower_player_stats (" +
                                "uuid VARCHAR(36) NOT NULL, " +
                                "solo_runs BIGINT NOT NULL DEFAULT 0, " +
                                "solo_wins BIGINT NOT NULL DEFAULT 0, " +
                                "solo_losses BIGINT NOT NULL DEFAULT 0, " +
                                "party_runs BIGINT NOT NULL DEFAULT 0, " +
                                "party_wins BIGINT NOT NULL DEFAULT 0, " +
                                "party_losses BIGINT NOT NULL DEFAULT 0, " +
                                "updated_at BIGINT NOT NULL DEFAULT 0, " +
                                "PRIMARY KEY (uuid)" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                );

                // =========================================
                // STATS POR DUNGEON (ranking / recordes)
                // =========================================
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS itower_player_dungeon (" +
                                "uuid VARCHAR(36) NOT NULL, " +
                                "dungeon_id VARCHAR(64) NOT NULL, " +
                                "solo_runs BIGINT NOT NULL DEFAULT 0, " +
                                "party_runs BIGINT NOT NULL DEFAULT 0, " +
                                "wins BIGINT NOT NULL DEFAULT 0, " +
                                "losses BIGINT NOT NULL DEFAULT 0, " +
                                "best_time_ms BIGINT NOT NULL DEFAULT 0, " +
                                "best_floor INT NOT NULL DEFAULT 0, " +
                                "PRIMARY KEY (uuid, dungeon_id)" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                );

                // =========================================
                // BEST SOLO PLAYER por dungeon
                // =========================================
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS itower_dungeon_best_player (" +
                                "dungeon_id VARCHAR(64) NOT NULL, " +
                                "uuid VARCHAR(36) NOT NULL, " +
                                "time_ms BIGINT NOT NULL DEFAULT 0, " +
                                "updated_at BIGINT NOT NULL DEFAULT 0, " +
                                "PRIMARY KEY (dungeon_id)" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                );

                // =========================================
                // BEST PARTY por dungeon
                // =========================================
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS itower_dungeon_best_party (" +
                                "dungeon_id VARCHAR(64) NOT NULL, " +
                                "leader_uuid VARCHAR(36) NOT NULL, " +
                                "members TEXT NOT NULL, " +
                                "time_ms BIGINT NOT NULL DEFAULT 0, " +
                                "updated_at BIGINT NOT NULL DEFAULT 0, " +
                                "PRIMARY KEY (dungeon_id)" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                );

                // =========================================
                // RUN HISTORY (logStart/logEnd)
                // + leader_name / members_names pra log legível
                // =========================================
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS itower_run_history (" +
                                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                                "session_id VARCHAR(36) NOT NULL, " +
                                "dungeon_id VARCHAR(64) NOT NULL, " +
                                "mode VARCHAR(10) NOT NULL, " +
                                "leader_uuid VARCHAR(36) NOT NULL, " +
                                "members TEXT NOT NULL, " +
                                "leader_name VARCHAR(32) NOT NULL DEFAULT '', " +
                                "members_names TEXT NOT NULL, " +
                                "started_at BIGINT NOT NULL, " +
                                "ended_at BIGINT NOT NULL DEFAULT 0, " +
                                "duration_ms BIGINT NOT NULL DEFAULT 0, " +
                                "reached_floor INT NOT NULL DEFAULT 0, " +
                                "finished TINYINT(1) NOT NULL DEFAULT 0, " +
                                "end_reason VARCHAR(24) NOT NULL DEFAULT '', " +
                                "KEY idx_leader (leader_uuid), " +
                                "KEY idx_dungeon (dungeon_id), " +
                                "KEY idx_session (session_id)" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                );

            } else {
                // SQLITE
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS itower_player (" +
                                "uuid TEXT PRIMARY KEY, " +
                                "best_floor INTEGER NOT NULL DEFAULT 0, " +
                                "extra_value INTEGER NOT NULL DEFAULT 0, " +
                                "updated_at INTEGER NOT NULL DEFAULT 0" +
                                ");"
                );

                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS itower_player_stats (" +
                                "uuid TEXT PRIMARY KEY, " +
                                "solo_runs INTEGER NOT NULL DEFAULT 0, " +
                                "solo_wins INTEGER NOT NULL DEFAULT 0, " +
                                "solo_losses INTEGER NOT NULL DEFAULT 0, " +
                                "party_runs INTEGER NOT NULL DEFAULT 0, " +
                                "party_wins INTEGER NOT NULL DEFAULT 0, " +
                                "party_losses INTEGER NOT NULL DEFAULT 0, " +
                                "updated_at INTEGER NOT NULL DEFAULT 0" +
                                ");"
                );

                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS itower_player_dungeon (" +
                                "uuid TEXT NOT NULL, " +
                                "dungeon_id TEXT NOT NULL, " +
                                "solo_runs INTEGER NOT NULL DEFAULT 0, " +
                                "party_runs INTEGER NOT NULL DEFAULT 0, " +
                                "wins INTEGER NOT NULL DEFAULT 0, " +
                                "losses INTEGER NOT NULL DEFAULT 0, " +
                                "best_time_ms INTEGER NOT NULL DEFAULT 0, " +
                                "best_floor INTEGER NOT NULL DEFAULT 0, " +
                                "PRIMARY KEY (uuid, dungeon_id)" +
                                ");"
                );

                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS itower_dungeon_best_player (" +
                                "dungeon_id TEXT PRIMARY KEY, " +
                                "uuid TEXT NOT NULL, " +
                                "time_ms INTEGER NOT NULL DEFAULT 0, " +
                                "updated_at INTEGER NOT NULL DEFAULT 0" +
                                ");"
                );

                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS itower_dungeon_best_party (" +
                                "dungeon_id TEXT PRIMARY KEY, " +
                                "leader_uuid TEXT NOT NULL, " +
                                "members TEXT NOT NULL, " +
                                "time_ms INTEGER NOT NULL DEFAULT 0, " +
                                "updated_at INTEGER NOT NULL DEFAULT 0" +
                                ");"
                );

                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS itower_run_history (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                "session_id TEXT NOT NULL, " +
                                "dungeon_id TEXT NOT NULL, " +
                                "mode TEXT NOT NULL, " +
                                "leader_uuid TEXT NOT NULL, " +
                                "members TEXT NOT NULL, " +
                                "leader_name TEXT NOT NULL DEFAULT '', " +
                                "members_names TEXT NOT NULL DEFAULT '', " +
                                "started_at INTEGER NOT NULL, " +
                                "ended_at INTEGER NOT NULL DEFAULT 0, " +
                                "duration_ms INTEGER NOT NULL DEFAULT 0, " +
                                "reached_floor INTEGER NOT NULL DEFAULT 0, " +
                                "finished INTEGER NOT NULL DEFAULT 0, " +
                                "end_reason TEXT NOT NULL DEFAULT ''" +
                                ");"
                );

                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_run_leader ON itower_run_history(leader_uuid);");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_run_dungeon ON itower_run_history(dungeon_id);");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_run_session ON itower_run_history(session_id);");
            }
        }
    }

    private void ensureSchema() {
        if (!isConnected()) return;

        try (Statement st = connection.createStatement()) {

            ensureColumnExists(st, "itower_player", "best_floor",
                    mysql ? "INT NOT NULL DEFAULT 0" : "INTEGER NOT NULL DEFAULT 0");
            ensureColumnExists(st, "itower_player", "extra_value",
                    mysql ? "INT NOT NULL DEFAULT 0" : "INTEGER NOT NULL DEFAULT 0");
            ensureColumnExists(st, "itower_player", "updated_at",
                    mysql ? "BIGINT NOT NULL DEFAULT 0" : "INTEGER NOT NULL DEFAULT 0");

            ensureColumnExists(st, "itower_run_history", "leader_name",
                    mysql ? "VARCHAR(32) NOT NULL DEFAULT ''" : "TEXT NOT NULL DEFAULT ''");
            ensureColumnExists(st, "itower_run_history", "members_names",
                    mysql ? "TEXT NOT NULL" : "TEXT NOT NULL DEFAULT ''");

            if (mysql) {
                try {
                    st.executeUpdate("ALTER TABLE itower_run_history ADD UNIQUE KEY uq_run_session (session_id);");
                } catch (SQLException ignored) {}
            } else {
                st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS uq_run_session ON itower_run_history(session_id);");
            }

            // ✅ garante as tabelas de recorde (pra banco antigo que já existia)
            ensureBestRecordTables(st);

        } catch (SQLException e) {
            plugin.getLogger().warning(fmt(
                    langRaw("database.ensure_schema_fail",
                            "ensureSchema falhou: {error}"),
                    Map.of("{error}", String.valueOf(e.getMessage()))
            ));
        }
    }

    private void ensureBestRecordTables(Statement st) {
        try {
            if (mysql) {
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS itower_dungeon_best_player (" +
                                "dungeon_id VARCHAR(64) NOT NULL, " +
                                "uuid VARCHAR(36) NOT NULL, " +
                                "time_ms BIGINT NOT NULL DEFAULT 0, " +
                                "updated_at BIGINT NOT NULL DEFAULT 0, " +
                                "PRIMARY KEY (dungeon_id)" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                );

                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS itower_dungeon_best_party (" +
                                "dungeon_id VARCHAR(64) NOT NULL, " +
                                "leader_uuid VARCHAR(36) NOT NULL, " +
                                "members TEXT NOT NULL, " +
                                "time_ms BIGINT NOT NULL DEFAULT 0, " +
                                "updated_at BIGINT NOT NULL DEFAULT 0, " +
                                "PRIMARY KEY (dungeon_id)" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                );
            } else {
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS itower_dungeon_best_player (" +
                                "dungeon_id TEXT PRIMARY KEY, " +
                                "uuid TEXT NOT NULL, " +
                                "time_ms INTEGER NOT NULL DEFAULT 0, " +
                                "updated_at INTEGER NOT NULL DEFAULT 0" +
                                ");"
                );

                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS itower_dungeon_best_party (" +
                                "dungeon_id TEXT PRIMARY KEY, " +
                                "leader_uuid TEXT NOT NULL, " +
                                "members TEXT NOT NULL, " +
                                "time_ms INTEGER NOT NULL DEFAULT 0, " +
                                "updated_at INTEGER NOT NULL DEFAULT 0" +
                                ");"
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().warning(fmt(
                    langRaw("database.ensure_best_tables_fail",
                            "ensureBestRecordTables falhou: {error}"),
                    Map.of("{error}", String.valueOf(e.getMessage()))
            ));
        }
    }

    private void ensureColumnExists(Statement st, String table, String column, String ddlType) throws SQLException {
        if (!tableExists(table)) return;
        if (columnExists(table, column)) return;

        try {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddlType + ";");
            plugin.getLogger().info(fmt(
                    langRaw("database.column_added",
                            "DB: coluna adicionada {table}.{column}"),
                    Map.of("{table}", table, "{column}", column)
            ));
        } catch (SQLException e) {
            plugin.getLogger().warning(fmt(
                    langRaw("database.column_add_fail",
                            "DB: falha ao adicionar coluna {table}.{column}: {error}"),
                    Map.of(
                            "{table}", table,
                            "{column}", column,
                            "{error}", String.valueOf(e.getMessage())
                    )
            ));
        }
    }

    private boolean tableExists(String table) {
        try {
            if (!mysql) {
                try (Statement st = connection.createStatement();
                     ResultSet rs = st.executeQuery(
                             "SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'"
                     )) {
                    return rs.next();
                }
            }

            DatabaseMetaData md = connection.getMetaData();
            try (ResultSet rs = md.getTables(null, null, table, null)) {
                if (rs.next()) return true;
            }
            try (ResultSet rs2 = md.getTables(null, null, table.toUpperCase(Locale.ROOT), null)) {
                return rs2.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean columnExists(String table, String column) {
        try {
            if (!mysql) {
                try (Statement st = connection.createStatement();
                     ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ");")) {
                    while (rs.next()) {
                        String name = rs.getString("name");
                        if (name != null && name.equalsIgnoreCase(column)) return true;
                    }
                    return false;
                }
            }

            DatabaseMetaData md = connection.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, table, column)) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean isMySQL() {
        return mysql;
    }

    public Connection getConnection() {
        return connection;
    }

    public void disconnect() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (Exception ignored) {}
        connection = null;
        mysql = false;

        plugin.getLogger().info(langRaw("database.disconnected", "Banco desconectado."));
    }

    // =========================
    // LANG HELPERS (local)
    // =========================

    private String langRaw(String path, String def) {
        if (plugin.getLang() == null) return def;
        return plugin.getLang().getString(path, def);
    }

    private String fmt(String msg, Map<String, String> vars) {
        if (msg == null) return "";
        String out = msg;
        for (var e : vars.entrySet()) out = out.replace(e.getKey(), e.getValue());
        return out;
    }
}