package com.eternity.infinitytower.menu;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class MainMenuFactory {

    private final InfinityTower plugin;

    public MainMenuFactory(InfinityTower plugin) {
        this.plugin = plugin;
    }

    public Inventory createMainMenu() {
        FileConfiguration menus = getMenusConfig();
        ConfigurationSection sec = menus.getConfigurationSection("main_menu");
        if (sec == null || !sec.getBoolean("enabled", true)) return null;

        int size = sec.getInt("size", 27);
        if (size < 9) size = 9;
        if (size > 54) size = 54;
        if (size % 9 != 0) size = 27;

        String title = sec.getString("title", "&8Infinity Tower");

        MainMenuHolder holder = new MainMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, size, TextUtil.color(title));
        holder.setInventory(inv);

        // filler
        ConfigurationSection filler = sec.getConfigurationSection("filler");
        if (filler != null && filler.getBoolean("enabled", true)) {
            ItemStack fill = buildItem(
                    filler.getString("material", "BLACK_STAINED_GLASS_PANE"),
                    filler.getString("name", " "),
                    filler.getStringList("lore"),
                    filler.getInt("custom_model_data", 0)
            );

            if (fill != null && fill.getType() != Material.AIR) {
                for (int i = 0; i < size; i++) inv.setItem(i, fill);
            }
        }

        // buttons
        ConfigurationSection buttons = sec.getConfigurationSection("buttons");
        if (buttons != null) {
            // SOLO
            ConfigurationSection solo = buttons.getConfigurationSection("solo");
            if (solo != null && solo.getBoolean("enabled", true)) {
                int slot = solo.getInt("slot", 12);
                ItemStack item = buildItem(
                        solo.getString("material", "DIAMOND_SWORD"),
                        solo.getString("name", "&a&lDUNGEON SOLO"),
                        solo.getStringList("lore"),
                        solo.getInt("custom_model_data", 0)
                );
                if (inBounds(slot, size) && item != null) inv.setItem(slot, item);
            }

            // SPACER
            ConfigurationSection spacer = buttons.getConfigurationSection("spacer");
            if (spacer != null && spacer.getBoolean("enabled", true)) {
                int slot = spacer.getInt("slot", 13);
                String matName = spacer.getString("material", "AIR");
                ItemStack item = buildItem(
                        matName,
                        spacer.getString("name", " "),
                        spacer.getStringList("lore"),
                        spacer.getInt("custom_model_data", 0)
                );

                if (inBounds(slot, size)) {
                    if (item == null || item.getType() == Material.AIR) inv.setItem(slot, null);
                    else inv.setItem(slot, item);
                }
            }

            // PARTY
            ConfigurationSection party = buttons.getConfigurationSection("party");
            if (party != null && party.getBoolean("enabled", true)) {
                int slot = party.getInt("slot", 14);
                ItemStack item = buildItem(
                        party.getString("material", "GOLDEN_SWORD"),
                        party.getString("name", "&e&lDUNGEON PARTY"),
                        party.getStringList("lore"),
                        party.getInt("custom_model_data", 0)
                );
                if (inBounds(slot, size) && item != null) inv.setItem(slot, item);
            }
        }

        return inv;
    }

    private boolean inBounds(int slot, int size) {
        return slot >= 0 && slot < size;
    }

    private ItemStack buildItem(String materialName, String name, List<String> lore, int cmd) {
        Material mat;
        try {
            mat = Material.valueOf(String.valueOf(materialName).toUpperCase());
        } catch (Exception e) {
            mat = Material.STONE;
        }

        ItemStack it = new ItemStack(mat, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.color(name == null ? " " : name));

            List<String> outLore = new ArrayList<>();
            if (lore != null) {
                for (String line : lore) outLore.add(TextUtil.color(line));
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