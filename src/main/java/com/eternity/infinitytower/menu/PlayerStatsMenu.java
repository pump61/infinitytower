package com.eternity.infinitytower.menu;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.database.PlayerStatsRepository;
import com.eternity.infinitytower.database.PlayerStatsRepository.PlayerStats;
import com.eternity.infinitytower.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class PlayerStatsMenu {

    private final InfinityTower plugin;

    public PlayerStatsMenu(InfinityTower plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        FileConfiguration menus = getMenusConfig();
        ConfigurationSection sec = menus.getConfigurationSection("player_stats_menu");
        if (sec == null || !sec.getBoolean("enabled", true)) return;

        PlayerStatsRepository repo = plugin.getPlayerStatsRepository();
        PlayerStats stats = repo.getStats(player.getUniqueId());

        int size = sec.getInt("size", 27);
        if (size < 9) size = 9;
        if (size > 54) size = 54;
        if (size % 9 != 0) size = 27;

        String title = sec.getString("title", "&8Suas Estatísticas");

        Inventory inv = Bukkit.createInventory(null, size, TextUtil.color(applyPlaceholders(title, player, stats)));

        // filler
        ConfigurationSection filler = sec.getConfigurationSection("filler");
        if (filler != null && filler.getBoolean("enabled", true)) {
            ItemStack fill = buildItem(
                    filler.getString("material", "BLACK_STAINED_GLASS_PANE"),
                    filler.getString("name", " "),
                    filler.getStringList("lore"),
                    filler.getInt("custom_model_data", 0),
                    player,
                    stats
            );

            if (fill != null && fill.getType() != Material.AIR) {
                for (int i = 0; i < size; i++) inv.setItem(i, fill);
            }
        }

        ConfigurationSection items = sec.getConfigurationSection("items");
        if (items != null) {
            // SOLO
            ConfigurationSection solo = items.getConfigurationSection("solo");
            if (solo != null && solo.getBoolean("enabled", true)) {
                int slot = solo.getInt("slot", 11);
                ItemStack item = buildItem(
                        solo.getString("material", "DIAMOND_SWORD"),
                        solo.getString("name", "&a&lSOLO"),
                        solo.getStringList("lore"),
                        solo.getInt("custom_model_data", 0),
                        player,
                        stats
                );
                if (inBounds(slot, size) && item != null) inv.setItem(slot, item);
            }

            // PARTY
            ConfigurationSection party = items.getConfigurationSection("party");
            if (party != null && party.getBoolean("enabled", true)) {
                int slot = party.getInt("slot", 15);
                ItemStack item = buildItem(
                        party.getString("material", "GOLDEN_SWORD"),
                        party.getString("name", "&6&lPARTY"),
                        party.getStringList("lore"),
                        party.getInt("custom_model_data", 0),
                        player,
                        stats
                );
                if (inBounds(slot, size) && item != null) inv.setItem(slot, item);
            }
        }

        player.openInventory(inv);
    }

    private boolean inBounds(int slot, int size) {
        return slot >= 0 && slot < size;
    }

    private ItemStack buildItem(String materialName, String name, List<String> lore, int cmd, Player player, PlayerStats stats) {
        Material mat;
        try {
            mat = Material.valueOf(String.valueOf(materialName).toUpperCase());
        } catch (Exception e) {
            mat = Material.STONE;
        }

        ItemStack it = new ItemStack(mat, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(applyPlaceholders(name == null ? " " : name, player, stats)));

            List<String> outLore = new ArrayList<>();
            if (lore != null) {
                for (String line : lore) outLore.add(TextUtil.color(applyPlaceholders(line, player, stats)));
            }
            meta.setLore(outLore);

            if (cmd > 0) {
                try { meta.setCustomModelData(cmd); } catch (Throwable ignored) {}
            }

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            it.setItemMeta(meta);
        }
        return it;
    }

    /**
     * Placeholders suportados:
     * {player}
     * {soloRuns} {soloWins} {soloLosses}
     * {partyRuns} {partyWins} {partyLosses}
     */
    private String applyPlaceholders(String s, Player player, PlayerStats stats) {
        if (s == null) return "";
        return s
                .replace("{player}", player.getName())
                .replace("{soloRuns}", String.valueOf(stats.soloRuns))
                .replace("{soloWins}", String.valueOf(stats.soloWins))
                .replace("{soloLosses}", String.valueOf(stats.soloLosses))
                .replace("{partyRuns}", String.valueOf(stats.partyRuns))
                .replace("{partyWins}", String.valueOf(stats.partyWins))
                .replace("{partyLosses}", String.valueOf(stats.partyLosses));
    }

    /**
     * Tenta pegar menus.yml do plugin via reflection (pra não depender do nome exato do método).
     * Se não existir, cai no config.yml.
     */
    private FileConfiguration getMenusConfig() {
        FileConfiguration cfg = tryCallMenusGetter("getMenusConfig");
        if (cfg != null) return cfg;

        cfg = tryCallMenusGetter("getMenus");
        if (cfg != null) return cfg;

        cfg = tryCallMenusGetter("getMenusYml");
        if (cfg != null) return cfg;

        cfg = tryCallMenusGetter("getMenuConfig");
        if (cfg != null) return cfg;

        return plugin.getConfig();
    }

    private FileConfiguration tryCallMenusGetter(String methodName) {
        try {
            Method m = plugin.getClass().getMethod(methodName);
            Object o = m.invoke(plugin);
            if (o instanceof FileConfiguration fc) return fc;
        } catch (Throwable ignored) {}
        return null;
    }
}