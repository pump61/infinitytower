package com.eternity.infinitytower.manager;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.setup.SetupSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SetupManager {

    private final InfinityTower plugin;

    // adminUuid -> setup session
    private final Map<UUID, SetupSession> sessions = new HashMap<>();

    public SetupManager(InfinityTower plugin) {
        this.plugin = plugin;
    }

    public SetupSession get(Player admin) {
        return sessions.get(admin.getUniqueId());
    }

    public SetupSession require(Player admin) {
        // cria uma session "vazia" (dungeonId será setado no start)
        return sessions.computeIfAbsent(admin.getUniqueId(), id -> new SetupSession("", 1));
    }

    public boolean start(Player admin, String dungeonId) {
        if (dungeonId == null || dungeonId.isBlank()) return false;
        dungeonId = dungeonId.trim().toLowerCase(Locale.ROOT);

        if (!plugin.getDungeonRegistry().exists(dungeonId)) {
            admin.sendMessage("§cDungeon não existe: §f" + dungeonId);
            return false;
        }

        SetupSession s = require(admin);
        s.setDungeonId(dungeonId);

        admin.sendMessage("§aSetup iniciado para: §f" + dungeonId);
        return true;
    }

    public void exit(Player admin) {
        sessions.remove(admin.getUniqueId());
        admin.sendMessage("§eSetup encerrado.");
    }

    public void shutdown() {
        sessions.clear();
    }

    // util: player offline safe
    public static Player player(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        return (p != null && p.isOnline()) ? p : null;
    }
}