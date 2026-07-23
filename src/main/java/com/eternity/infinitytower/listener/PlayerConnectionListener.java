package com.eternity.infinitytower.listener;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.party.Party;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public final class PlayerConnectionListener implements Listener {

    private final InfinityTower plugin;

    public PlayerConnectionListener(InfinityTower plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        handleDisconnect(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        handleDisconnect(event.getPlayer());
    }

    private void handleDisconnect(Player player) {
        if (player == null) return;

        final UUID id = player.getUniqueId();

        // 1) Se estiver em dungeon, encerra sessão por disconnect
        try {
            if (plugin.getInfinityTowerManager() != null && plugin.getInfinityTowerManager().getSessionManager() != null) {
                plugin.getInfinityTowerManager().getSessionManager().handlePlayerQuit(player);
            }
        } catch (Throwable ignored) {
            // não derruba servidor
        }

        // 2) Party cleanup (líder disband / membro remove)
        try {
            if (plugin.getPartyManager() != null) {
                Party party = plugin.getPartyManager().getParty(id);
                if (party != null) {
                    if (party.isLeader(id)) {
                        // ✅ precisa existir no PartyManager
                        plugin.getPartyManager().disbandByLeader(id);
                    } else {
                        // ✅ precisa existir no PartyManager
                        plugin.getPartyManager().leaveById(id);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
}