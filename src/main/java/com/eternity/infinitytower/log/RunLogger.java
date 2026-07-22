package com.eternity.infinitytower.log;

import com.eternity.infinitytower.InfinityTower;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.io.FileWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

public final class RunLogger {

    private final InfinityTower plugin;
    private final File file;

    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public RunLogger(InfinityTower plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "run-log.txt");
    }

    // =====================================================
    // LOGS
    // =====================================================

    /**
     * Compat com o SessionManager atual:
     * logStart(sessionId, dungeonId, mode, leaderUuid, members)
     *
     * mode exemplos: "SOLO" / "PARTY"
     */
    public void logStart(UUID sessionId, String dungeonId, String mode, UUID leaderUuid, Set<UUID> members) {
        if (sessionId == null) sessionId = new UUID(0L, 0L);
        if (dungeonId == null) dungeonId = "unknown";
        if (mode == null) mode = "UNKNOWN";

        StringBuilder sb = new StringBuilder();
        sb.append(fmt.format(Instant.now()))
          .append(" | START")
          .append(" | ").append(mode)
          .append(" | dungeon=").append(dungeonId)
          .append(" | session=").append(sessionId)
          .append(" | leader=").append(nameOf(leaderUuid));

        sb.append(" | members=");
        if (members == null || members.isEmpty()) {
            sb.append("[]");
        } else {
            sb.append("[");
            boolean first = true;
            for (UUID u : members) {
                if (!first) sb.append(",");
                sb.append(nameOf(u));
                first = false;
            }
            sb.append("]");
        }

        writeLine(sb.append("\n").toString());
    }

    /**
     * LOG quando a key é consumida
     * (chamado pelo KeyManager)
     */
    public void logKeyUsed(UUID playerUuid, String dungeonId) {
        if (playerUuid == null) playerUuid = new UUID(0L, 0L);
        if (dungeonId == null) dungeonId = "unknown";

        String line = fmt.format(Instant.now())
                + " | KEY_USED"
                + " | dungeon=" + dungeonId
                + " | player=" + nameOf(playerUuid)
                + "\n";

        writeLine(line);
    }

    /**
     * Se quiser usar no futuro pra registrar "participou", etc.
     */
    public void logJoin(UUID playerUuid, String dungeonId, String mode) {
        if (playerUuid == null) playerUuid = new UUID(0L, 0L);
        if (dungeonId == null) dungeonId = "unknown";
        if (mode == null) mode = "UNKNOWN";

        String line = fmt.format(Instant.now())
                + " | JOIN"
                + " | " + mode
                + " | dungeon=" + dungeonId
                + " | player=" + nameOf(playerUuid)
                + "\n";

        writeLine(line);
    }

    public void logJoin(Set<UUID> players, String dungeonId, String mode) {
        if (players == null) return;
        for (UUID u : players) logJoin(u, dungeonId, mode);
    }

    // =====================================================
    // HELPERS
    // =====================================================

    /**
     * Retorna o nome do jogador.
     * - online: Player#getName
     * - offline cache: OfflinePlayer#getName (só se hasPlayedBefore)
     * - fallback: "desconhecido" (NUNCA UUID)
     */
    private String nameOf(UUID uuid) {
        if (uuid == null) return "desconhecido";

        try {
            var p = Bukkit.getPlayer(uuid);
            if (p != null) {
                String name = p.getName();
                if (name != null && !name.isBlank()) return name;
            }

            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            if (op != null && op.hasPlayedBefore()) {
                String name = op.getName();
                if (name != null && !name.isBlank()) return name;
            }
        } catch (Exception ignored) {}

        return "desconhecido";
    }

    private synchronized void writeLine(String line) {
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            try (FileWriter fw = new FileWriter(file, true)) {
                fw.write(line);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("RunLogger falhou: " + e.getMessage());
        }
    }
}