package com.eternity.infinitytower.listener;

import com.eternity.infinitytower.InfinityTower;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.*;

public final class TowerCommandBlockListener implements Listener {

    private final InfinityTower plugin;

    // uuid -> timestamp até quando NÃO pode usar /back
    private final Map<UUID, Long> backBlockedUntil = new HashMap<>();

    public TowerCommandBlockListener(InfinityTower plugin) {
        this.plugin = plugin;
    }

    /** Chame isso quando o player sair/for teleportado pra fora da dungeon (leave/end). */
    public void blockBackFor(Player p, int seconds) {
        if (p == null) return;
        long until = System.currentTimeMillis() + (Math.max(0, seconds) * 1000L);
        backBlockedUntil.put(p.getUniqueId(), until);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String msg = e.getMessage();
        if (msg == null) return;

        String raw = msg.trim();
        if (raw.startsWith("/")) raw = raw.substring(1);
        if (raw.isBlank()) return;

        String cmd = raw.split("\\s+")[0].toLowerCase(Locale.ROOT);

        // ✅ trava /back por X segundos depois de sair da dungeon (impede voltar)
        Long until = backBlockedUntil.get(p.getUniqueId());
        if (until != null) {
            if (System.currentTimeMillis() <= until) {
                if (cmd.equals("back") || cmd.endsWith(":back")) {
                    e.setCancelled(true);
                    p.sendMessage(plugin.getLang().getString("messages.command_blocked_in_dungeon",
                            "§cVocê não pode usar comandos dentro da dungeon."));
                    return;
                }
            } else {
                backBlockedUntil.remove(p.getUniqueId());
            }
        }

        // ✅ bloqueio total enquanto está em sessão
        if (!plugin.getInfinityTowerManager().getSessionManager().isInSession(p)) return;

        // whitelist (permite só o essencial)
        // Você pode por isso no config.yml depois; aqui deixei hardcoded pra não travar chat/comandos básicos.
        Set<String> allowed = Set.of(
                "tower", "itower",
                "msg", "tell", "r", "reply",
                "l", "login", "register"
        );

        if (allowed.contains(cmd)) return;

        // (se quiser permitir /party, /clan, etc, adiciona aqui)
        e.setCancelled(true);
        p.sendMessage(plugin.getLang().getString("messages.command_blocked_in_dungeon",
                "§cVocê não pode usar comandos dentro da dungeon."));
    }
}