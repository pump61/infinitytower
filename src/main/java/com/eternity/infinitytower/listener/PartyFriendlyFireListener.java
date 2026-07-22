package com.eternity.infinitytower.listener;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.party.Party;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class PartyFriendlyFireListener implements Listener {

    private final InfinityTower plugin;

    public PartyFriendlyFireListener(InfinityTower plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {

        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;

        // hit direto
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        }

        // hit por projétil (flecha, tridente, etc)
        else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker == null) return;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;

        // só bloquear dentro da dungeon
        var sessionManager = plugin.getInfinityTowerManager().getSessionManager();
        if (!sessionManager.isInSession(attacker)) return;
        if (!sessionManager.isInSession(victim)) return;

        Party aParty = plugin.getPartyManager().getParty(attacker.getUniqueId());
        if (aParty == null) return;

        // mesma party? cancela
        if (aParty.contains(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}