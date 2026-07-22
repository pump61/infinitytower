package com.eternity.infinitytower.tower;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.party.Party;
import com.eternity.infinitytower.tower.session.SessionManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class InfinityTowerManager {

    private final InfinityTower plugin;
    private final SessionManager sessionManager;

    public InfinityTowerManager(InfinityTower plugin) {
        this.plugin = plugin;
        this.sessionManager = new SessionManager(plugin);
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Compatibilidade (se algum lugar ainda chama startTower).
     * Mantém solo como padrão.
     */
    public void startTower(Player player, String dungeonId) {
        startSolo(player, dungeonId);
    }

    /**
     * ✅ SOLO
     */
    public void startSolo(Player player, String dungeonId) {
        sessionManager.createSoloSession(player, dungeonId);
    }

    /**
     * ✅ PARTY
     */
    public void startParty(Party party, String dungeonId) {
        sessionManager.createPartySession(party, dungeonId);
    }

    /**
     * ✅ /tower leave
     *
     * Regras:
     * - SOLO: sai e encerra a sessão
     * - PARTY: se for líder -> encerra tudo
     * - PARTY: se for membro -> sai sozinho (sessão continua)
     */
    public boolean leave(Player player) {
        return sessionManager.leavePlayer(player);
    }

    /**
     * ✅ Quit / Disconnect
     * - SOLO: encerra
     * - PARTY: líder encerra tudo (DISCONNECT)
     * - PARTY: membro sai sozinho (DISCONNECT)
     */
    public void handlePlayerDisconnected(Player player) {
        sessionManager.handlePlayerQuit(player);
    }

    public void handleMobSpawn(LivingEntity entity) {
        sessionManager.handleMobSpawn(entity);
    }

    public void handleMobDeath(LivingEntity entity) {
        sessionManager.handleMobDeath(entity);
    }

    public void shutdown() {
        sessionManager.shutdownAll();
    }
}