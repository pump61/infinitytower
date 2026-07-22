package com.eternity.infinitytower.tower;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.util.TextUtil;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TowerManager {

    private final InfinityTower plugin;
    private final HashMap<UUID, Integer> playerFloor = new HashMap<>();

    public TowerManager(InfinityTower plugin) {
        this.plugin = plugin;
    }

    public void startTower(Player player) {
        playerFloor.put(player.getUniqueId(), 1);

        player.sendMessage(lang("messages.tower_started", "&aVocê iniciou a Infinity Tower!"));
        nextFloor(player);
    }

    public void nextFloor(Player player) {
        int floor = playerFloor.getOrDefault(player.getUniqueId(), 1);

        player.sendMessage(apply(
                lang("messages.tower_next_floor", "&eIndo para o andar &6{floor}&e."),
                Map.of("{floor}", String.valueOf(floor))
        ));

        spawnMobs(player, floor);
    }

    public void finishFloor(Player player) {
        int floor = playerFloor.getOrDefault(player.getUniqueId(), 1);
        floor++;

        playerFloor.put(player.getUniqueId(), floor);
        nextFloor(player);
    }

    private void spawnMobs(Player player, int floor) {
        player.getWorld().spawnEntity(
                player.getLocation(),
                org.bukkit.entity.EntityType.ZOMBIE
        );

        player.sendMessage(apply(
                lang("messages.tower_mobs_spawned", "&cMobs spawnados para o andar &f{floor}&c."),
                Map.of("{floor}", String.valueOf(floor))
        ));
    }

    public void stopTower(Player player) {
        playerFloor.remove(player.getUniqueId());
        player.sendMessage(lang("messages.tower_left", "&cVocê saiu da Infinity Tower."));
    }

    // =========================
    // LANG HELPERS
    // =========================

    private String lang(String path, String def) {
        return TextUtil.color(plugin.getLang().getString(path, def));
    }

    private String apply(String msg, Map<String, String> vars) {
        if (msg == null) return "";
        String out = msg;
        for (var e : vars.entrySet()) out = out.replace(e.getKey(), e.getValue());
        return out;
    }
}