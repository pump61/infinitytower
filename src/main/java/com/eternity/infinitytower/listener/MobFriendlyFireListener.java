package com.eternity.infinitytower.listener;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.tower.session.DungeonSession;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class MobFriendlyFireListener implements Listener {

    private final InfinityTower plugin;

    public MobFriendlyFireListener(InfinityTower plugin) {
        this.plugin = plugin;
    }

    /**
     * Impede que mobs da dungeon se firam entre si (ex: flecha de um esqueleto
     * acertando um zumbi por acidente). Sem isso, a IA vanilla (HurtByTargetGoal)
     * faz o mob atingido mirar em quem acertou ele, brigando com outro mob da
     * mesma sala em vez de perseguir o jogador.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {

        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!isDungeonMob(victim)) return;

        Entity rawDamager = event.getDamager();
        Entity actualDamager = rawDamager;

        // flecha/tridente/etc -> pega quem atirou
        if (rawDamager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            actualDamager = shooter;
        }

        if (!(actualDamager instanceof LivingEntity damager)) return;
        if (damager.getUniqueId().equals(victim.getUniqueId())) return;

        if (isDungeonMob(damager)) {
            event.setCancelled(true);
        }
    }

    private boolean isDungeonMob(LivingEntity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (tag.startsWith(DungeonSession.TAG_PREFIX)) return true;
        }
        return false;
    }
}
