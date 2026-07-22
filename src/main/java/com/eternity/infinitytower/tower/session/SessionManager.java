package com.eternity.infinitytower.tower.session;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.party.Party;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;

public final class SessionManager {

    private final InfinityTower plugin;

    // sessionId -> session
    private final Map<UUID, DungeonSession> sessions = new HashMap<>();

    // playerId -> sessionId
    private final Map<UUID, UUID> playerToSession = new HashMap<>();

    /**
     * baseId -> arenas ocupadas (guardamos o índice da arena ocupado)
     * Ex: base=solo_10, ocupado={1,2,3}
     */
    private final Map<String, Set<Integer>> occupiedArenas = new HashMap<>();

    public SessionManager(InfinityTower plugin) {
        this.plugin = plugin;
    }

    // =========================================
    // ✅ GET SESSION (Player OU UUID)
    // =========================================

    public boolean isInSession(Player player) {
        if (player == null) return false;
        return playerToSession.containsKey(player.getUniqueId());
    }

    public boolean isInSession(UUID playerId) {
        if (playerId == null) return false;
        return playerToSession.containsKey(playerId);
    }

    public DungeonSession getSession(Player player) {
        if (player == null) return null;
        return getSession(player.getUniqueId());
    }

    public DungeonSession getSession(UUID playerId) {
        if (playerId == null) return null;
        UUID sid = playerToSession.get(playerId);
        if (sid == null) return null;
        return sessions.get(sid);
    }

    // =========================================
    // ✅ LEAVE PLAYER (PLAYER SAINDO POR COMANDO)
    // =========================================

    /**
     * Regras:
     * - SOLO: encerra a sessão (LEAVE)
     * - PARTY:
     *    - líder: encerra a sessão (todo mundo sai) (LEAVE)
     *    - membro: sai sozinho e sessão continua
     */
    public boolean leavePlayer(Player player) {
        if (player == null) return false;

        DungeonSession s = getSession(player);
        if (s == null) return false;

        UUID pid = player.getUniqueId();

        // SOLO -> encerra tudo
        if (!s.isPartyRun()) {
            s.end(DungeonSession.EndReason.LEAVE);
            return true;
        }

        // PARTY -> líder encerra tudo
        UUID leader = s.getPartyLeader();
        if (leader != null && leader.equals(pid)) {
            s.end(DungeonSession.EndReason.LEAVE);
            return true;
        }

        // PARTY -> membro sai sozinho
        playerToSession.remove(pid);

        // remove do set interno da sessão (teleporta só ele)
        s.removePlayer(player);

        // se ficou vazio, encerra
        if (s.getPlayers().isEmpty()) {
            s.end(DungeonSession.EndReason.LEAVE);
        }

        return true;
    }

    // =========================================
    // ✅ DISCONNECT (QUIT/KICK)
    // =========================================

    /**
     * Regras:
     * - SOLO: encerra (DISCONNECT)
     * - PARTY:
     *   - líder: encerra tudo (DISCONNECT)
     *   - membro: sai sozinho (DISCONNECT) e sessão continua
     */
    public void handlePlayerQuit(Player player) {
        if (player == null) return;

        DungeonSession s = getSession(player);
        if (s == null) return;

        UUID pid = player.getUniqueId();

        if (!s.isPartyRun()) {
            s.end(DungeonSession.EndReason.DISCONNECT);
            return;
        }

        UUID leader = s.getPartyLeader();
        if (leader != null && leader.equals(pid)) {
            s.end(DungeonSession.EndReason.DISCONNECT);
            return;
        }

        // membro desconectou: remove só ele
        playerToSession.remove(pid);
        s.removePlayer(player);

        if (s.getPlayers().isEmpty()) {
            s.end(DungeonSession.EndReason.DISCONNECT);
        }
    }

    // =========================================
    // ARENAS (root + _arena2..N)
    // =========================================

    private static final class ArenaPick {
        final String baseId;     // ex: solo_10
        final int arenaIndex;    // 1,2,3...
        ArenaPick(String baseId, int arenaIndex) {
            this.baseId = baseId;
            this.arenaIndex = Math.max(1, arenaIndex);
        }
    }

    private ArenaPick pickArena(String requestedDungeonIdRaw) {
        if (requestedDungeonIdRaw == null) return null;

        String requested = requestedDungeonIdRaw.trim().toLowerCase(Locale.ROOT);
        if (requested.isBlank()) return null;

        String baseId = baseFromRequested(requested);
        int requestedArenaIndex = arenaIndexFromRequested(requested);

        // ✅ não depende de exists(), valida pelo arquivo carregado
        if (plugin.getDungeonRegistry().getDungeon(baseId) == null) {
            return null;
        }

        Set<Integer> occ = occupiedArenas.computeIfAbsent(baseId, k -> new HashSet<>());

        // Se o cara pediu arena específica
        if (requestedArenaIndex > 1) {
            if (!arenaExistsInFile(baseId, requestedArenaIndex)) return null;

            if (occ.contains(requestedArenaIndex)) return null;
            occ.add(requestedArenaIndex);
            return new ArenaPick(baseId, requestedArenaIndex);
        }

        // Arena 1 livre?
        if (!occ.contains(1)) {
            occ.add(1);
            return new ArenaPick(baseId, 1);
        }

        // Procura arena2..N existentes e livres
        for (int i = 2; i <= 999; i++) {
            if (!arenaExistsInFile(baseId, i)) break;
            if (occ.contains(i)) continue;

            occ.add(i);
            return new ArenaPick(baseId, i);
        }

        return null;
    }

    private void releaseArena(String baseIdRaw, int arenaIndex) {
        if (baseIdRaw == null) return;

        String baseId = baseIdRaw.toLowerCase(Locale.ROOT);
        Set<Integer> occ = occupiedArenas.get(baseId);
        if (occ == null) return;

        occ.remove(arenaIndex);
        if (occ.isEmpty()) occupiedArenas.remove(baseId);
    }

    private String baseFromRequested(String requestedLower) {
        int idx = requestedLower.lastIndexOf("_arena");
        if (idx <= 0) return requestedLower;

        String tail = requestedLower.substring(idx + "_arena".length());
        if (tail.isBlank()) return requestedLower;

        for (int i = 0; i < tail.length(); i++) {
            char c = tail.charAt(i);
            if (c < '0' || c > '9') return requestedLower;
        }

        return requestedLower.substring(0, idx);
    }

    private int arenaIndexFromRequested(String requestedLower) {
        int idx = requestedLower.lastIndexOf("_arena");
        if (idx <= 0) return 1;

        String tail = requestedLower.substring(idx + "_arena".length());
        if (tail.isBlank()) return 1;

        try {
            return Math.max(1, Integer.parseInt(tail));
        } catch (Exception ignored) {
            return 1;
        }
    }

    private boolean arenaExistsInFile(String baseId, int arenaIndex) {
        if (arenaIndex <= 1) return true;
        var cfg = plugin.getDungeonRegistry().getDungeon(baseId);
        if (cfg == null) return false;

        String key = baseId.toLowerCase(Locale.ROOT) + "_arena" + arenaIndex;
        return cfg.getConfigurationSection(key) != null;
    }

    // =========================================
    // CREATE SESSIONS
    // =========================================

    public DungeonSession createSoloSession(Player player, String dungeonId) {
        if (player == null) return null;

        DungeonSession old = getSession(player);
        if (old != null) old.end(DungeonSession.EndReason.LEAVE);

        ArenaPick pick = pickArena(dungeonId);
        if (pick == null) {
            player.sendMessage("§cTodas as arenas dessa dungeon estão em uso (ou dungeon inexistente).");
            return null;
        }

        DungeonSession session = new DungeonSession(plugin, pick.baseId, pick.arenaIndex);
        session.setPartyRun(false, player.getUniqueId());

        sessions.put(session.getSessionId(), session);
        playerToSession.put(player.getUniqueId(), session.getSessionId());

        session.addPlayer(player);

        String logDungeonId = pick.arenaIndex <= 1 ? pick.baseId : (pick.baseId + "_arena" + pick.arenaIndex);

        if (plugin.getRunLogger() != null) {
            plugin.getRunLogger().logStart(
                    session.getSessionId(),
                    logDungeonId,
                    "SOLO",
                    player.getUniqueId(),
                    session.getPlayers()
            );
        }

        session.start();
        return session;
    }

    public DungeonSession createPartySession(Party party, String dungeonId) {
        if (party == null) return null;

        // encerra sessões antigas de quem estiver online (ou mapeado)
        for (UUID pid : party.getMembers()) {
            DungeonSession old = getSession(pid);
            if (old != null) old.end(DungeonSession.EndReason.LEAVE);
        }

        // limpa mapping de quem estiver offline
        for (UUID pid : party.getMembers()) {
            Player p = plugin.getServer().getPlayer(pid);
            if (p == null || !p.isOnline()) {
                playerToSession.remove(pid);
            }
        }

        ArenaPick pick = pickArena(dungeonId);
        if (pick == null) {
            Player leader = plugin.getServer().getPlayer(party.getLeader());
            if (leader != null && leader.isOnline()) {
                leader.sendMessage("§cTodas as arenas dessa dungeon estão em uso (ou dungeon inexistente).");
            }
            return null;
        }

        DungeonSession session = new DungeonSession(plugin, pick.baseId, pick.arenaIndex);
        session.setPartyRun(true, party.getLeader());

        sessions.put(session.getSessionId(), session);

        int added = 0;

        for (UUID pid : party.getMembers()) {
            Player p = plugin.getServer().getPlayer(pid);
            if (p == null || !p.isOnline()) continue;

            playerToSession.put(pid, session.getSessionId());
            session.addPlayer(p);
            added++;
        }

        if (added == 0) {
            sessions.remove(session.getSessionId());
            releaseArena(pick.baseId, pick.arenaIndex);
            return null;
        }

        String logDungeonId = pick.arenaIndex <= 1 ? pick.baseId : (pick.baseId + "_arena" + pick.arenaIndex);

        if (plugin.getRunLogger() != null) {
            plugin.getRunLogger().logStart(
                    session.getSessionId(),
                    logDungeonId,
                    "PARTY",
                    party.getLeader(),
                    session.getPlayers()
            );
        }

        session.start();
        return session;
    }

    // =========================================
    // REMOVE / CLEANUP
    // =========================================

    public void removeSession(DungeonSession session) {
        if (session == null) return;

        sessions.remove(session.getSessionId());

        // usa cópia por segurança
        for (UUID pid : new ArrayList<>(session.getPlayers())) {
            playerToSession.remove(pid);
        }

        releaseArena(session.getDungeonId(), session.getArenaIndex());
    }

    // =========================================
    // MOBS TAGGED
    // =========================================

    public DungeonSession findByMobTags(LivingEntity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (!tag.startsWith(DungeonSession.TAG_PREFIX)) continue;

            String uuidText = tag.substring(DungeonSession.TAG_PREFIX.length());
            try {
                UUID sid = UUID.fromString(uuidText);
                return sessions.get(sid);
            } catch (Exception ignored) {}
        }
        return null;
    }

    public void handleMobDeath(LivingEntity entity) {
        DungeonSession session = findByMobTags(entity);
        if (session != null) session.handleMobDeath(entity);
    }

    public void handleMobSpawn(LivingEntity entity) {
        for (DungeonSession s : new ArrayList<>(sessions.values())) {
            s.handleExternalMobSpawn(entity);
        }
    }

    // =========================================
    // SHUTDOWN
    // =========================================

    public void shutdownAll() {
        for (DungeonSession s : new ArrayList<>(sessions.values())) {
            s.end(DungeonSession.EndReason.SHUTDOWN);
        }
        sessions.clear();
        playerToSession.clear();
        occupiedArenas.clear();
    }
}