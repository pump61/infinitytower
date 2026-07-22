package com.eternity.infinitytower.listener;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.util.TextUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public final class CommandWhitelistListener implements Listener {

    public static final String PLAYER_TAG_PREFIX = "itower_player:";

    private final InfinityTower plugin;

    // playerId -> millis until
    private final Map<UUID, Long> backBlockedUntil = new HashMap<>();

    private Set<String> cachedAllowed = null;
    private long cacheBuiltAtMs = 0L;

    private static final Set<String> BACK_ALIASES = Set.of(
            "back", "eback", "cback", "essentials:back", "cmi:back"
    );

    private static final Set<String> HARD_BLOCK_IN_DUNGEON = Set.of(
            "back", "spawn", "home", "warp", "tpa", "tpaccept", "tpdeny", "tpahere",
            "sethome", "delhome",
            "call", "return",
            "essentials:back", "essentials:spawn", "essentials:home", "essentials:warp",
            "cmi:back", "cmi:spawn", "cmi:home", "cmi:warp"
    );

    public CommandWhitelistListener(InfinityTower plugin) {
        this.plugin = plugin;
    }

    /**
     * Bloqueia /back por X segundos (usado quando o player sai / é teleportado pra fora).
     */
    public void blockBackFor(Player player, int seconds) {
        if (player == null) return;
        if (seconds <= 0) return;

        long until = System.currentTimeMillis() + (seconds * 1000L);
        backBlockedUntil.put(player.getUniqueId(), until);
    }

    private boolean isBackBlocked(Player p) {
        if (p == null) return false;

        Long until = backBlockedUntil.get(p.getUniqueId());
        if (until == null) return false;

        long now = System.currentTimeMillis();
        if (now >= until) {
            backBlockedUntil.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    private boolean isInDungeonByTag(Player p) {
        if (p == null) return false;
        for (String t : p.getScoreboardTags()) {
            if (t != null && t.startsWith(PLAYER_TAG_PREFIX)) return true;
        }
        return false;
    }

    private String msg(String path, String def) {
        return TextUtil.color(plugin.getLang().getString(path, def));
    }

    private static final class Cmd {
        final String base;       // ex: "essentials:back" ou "back" ou "."
        final String normalized; // ex: "back" ou "."
        Cmd(String base, String normalized) {
            this.base = base;
            this.normalized = normalized;
        }
    }

    private Cmd parseCommand(String raw) {
        if (raw == null) return new Cmd("", "");
        String full = raw.trim();
        if (full.isEmpty()) return new Cmd("", "");

        if (full.startsWith("/")) full = full.substring(1);
        full = full.trim();
        if (full.isEmpty()) return new Cmd("", "");

        String base = full;
        int space = base.indexOf(' ');
        if (space >= 0) base = base.substring(0, space);

        base = base.trim().toLowerCase(Locale.ROOT);
        if (base.isEmpty()) return new Cmd("", "");

        String normalized = base;
        int colon = normalized.indexOf(':');
        if (colon >= 0 && colon + 1 < normalized.length()) {
            normalized = normalized.substring(colon + 1);
        }

        return new Cmd(base, normalized);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // evita acumular no map (player saiu, então não precisa mais bloquear /back dele)
        backBlockedUntil.remove(event.getPlayer().getUniqueId());
    }

    /**
     * ✅ Importante:
     * - LOWEST para rodar antes de plugins de chat/canais
     * - ignoreCancelled = false para não "sumir" quando outro plugin cancela /g sem args, etc.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {

        Player p = event.getPlayer();

        // ✅ BYPASS para admin / staff
        if (p.hasPermission("infinitytower.admin") || p.hasPermission("infinitytower.commandbypass")) {
            return;
        }

        Cmd cmd = parseCommand(event.getMessage());
        if (cmd.base.isEmpty()) return;

        // 1) BLOQUEIO DO /BACK (fora ou dentro, se estiver no cooldown)
        if (isBackBlocked(p)) {
            if (BACK_ALIASES.contains(cmd.base) || BACK_ALIASES.contains(cmd.normalized)) {
                event.setCancelled(true);
                p.sendMessage(msg(
                        "messages.command_back_blocked",
                        "&cVocê saiu da dungeon agora. Aguarde alguns segundos para usar &f/back&c."
                ));
                return;
            }
        }

        // 2) VERIFICA SE ESTÁ NA DUNGEON (sessão OU tag)
        var sessionManager = plugin.getInfinityTowerManager().getSessionManager();
        boolean inSession = sessionManager.isInSession(p) || isInDungeonByTag(p);
        if (!inSession) return; // fora

        // 3) BLOQUEIO RÍGIDO
        if (HARD_BLOCK_IN_DUNGEON.contains(cmd.base) || HARD_BLOCK_IN_DUNGEON.contains(cmd.normalized)) {
            event.setCancelled(true);
            p.sendMessage(msg(
                    "messages.command_blocked_in_dungeon",
                    "&cVocê não pode usar esse comando dentro da dungeon."
            ));
            return;
        }

        // 4) WHITELIST
        Set<String> allowed = getAllowedCommandsLowerCached();

        boolean ok = allowed.contains(cmd.base) || allowed.contains(cmd.normalized);
        if (ok) {
            // ✅ se algum plugin cancelou antes (ex: chat channel /g sem args), libera
            if (event.isCancelled()) event.setCancelled(false);
            return;
        }

        event.setCancelled(true);
        p.sendMessage(msg(
                "messages.command_blocked_in_dungeon",
                "&cVocê não pode usar comandos dentro da dungeon."
        ));
    }

    private Set<String> getAllowedCommandsLowerCached() {

        long now = System.currentTimeMillis();
        if (cachedAllowed != null && (now - cacheBuiltAtMs) < 2000L) return cachedAllowed;

        List<String> list = plugin.getConfig().getStringList("dungeon.command_whitelist");

        if (list == null || list.isEmpty()) {
            list = Arrays.asList(
                    "tower",
                    "t",
                    "msg", "tell", "w", "r",
                    "reply",
                    "help"
            );
        }

        Set<String> out = new HashSet<>();
        for (String s : list) {
            if (s == null) continue;
            String v = s.trim().toLowerCase(Locale.ROOT);
            if (v.isEmpty()) continue;
            if (v.startsWith("/")) v = v.substring(1);
            out.add(v);

            // também guarda sem namespace caso coloquem "plugin:cmd" no config
            int colon = v.indexOf(':');
            if (colon >= 0 && colon + 1 < v.length()) {
                out.add(v.substring(colon + 1));
            }
        }

        // reforça
        out.add("tower");

        cachedAllowed = out;
        cacheBuiltAtMs = now;
        return out;
    }
}