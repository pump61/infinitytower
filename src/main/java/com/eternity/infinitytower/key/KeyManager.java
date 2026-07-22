package com.eternity.infinitytower.key;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public final class KeyManager {

    private final InfinityTower plugin;

    private final NamespacedKey KEY_TYPE;
    private final NamespacedKey KEY_DUNGEON;

    public KeyManager(InfinityTower plugin) {
        this.plugin = plugin;
        this.KEY_TYPE = new NamespacedKey(plugin, "itower_key_type");
        this.KEY_DUNGEON = new NamespacedKey(plugin, "itower_key_dungeon");
    }

    public boolean requiresKey(String dungeonId) {
        FileConfiguration d = plugin.getDungeonRegistry().getDungeon(dungeonId);
        if (d == null) return false;
        return d.getBoolean("access.require_key", false);
    }

    public boolean consumeKeyOnEnter(String dungeonId) {
        FileConfiguration d = plugin.getDungeonRegistry().getDungeon(dungeonId);
        if (d == null) return false;
        return d.getBoolean("access.consume_key", true);
    }

    public ItemStack buildKeyItem(String dungeonId, int amount) {
        FileConfiguration d = plugin.getDungeonRegistry().getDungeon(dungeonId);
        if (d == null) return null;

        ConfigurationSection keySec = d.getConfigurationSection("access.key");
        if (keySec == null) return null;

        String materialName = keySec.getString("material", "PAPER");
        Material mat;
        try {
            mat = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            mat = Material.PAPER;
        }

        ItemStack item = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String display = d.getString("display", dungeonId);
        int maxFloors = d.getInt("max_floors", 10);

        Map<String, String> ph = new HashMap<>();
        ph.put("{id}", dungeonId);
        ph.put("{display}", display);
        ph.put("{max_floors}", String.valueOf(maxFloors));

        String name = keySec.getString("name", "&bKey");
        name = applyPlaceholders(name, ph);
        meta.setDisplayName(TextUtil.color(name));

        List<String> lore = keySec.getStringList("lore");
        if (lore != null && !lore.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (String line : lore) out.add(TextUtil.color(applyPlaceholders(line, ph)));
            meta.setLore(out);
        }

        // custom model data (aceita custom-model-data e custom_model_data)
        int cmd = 0;
        if (keySec.contains("custom-model-data")) cmd = keySec.getInt("custom-model-data", 0);
        else if (keySec.contains("custom_model_data")) cmd = keySec.getInt("custom_model_data", 0);
        if (cmd > 0) meta.setCustomModelData(cmd);

        // tags internas (identificação real)
        meta.getPersistentDataContainer().set(KEY_TYPE, PersistentDataType.STRING, "key");
        meta.getPersistentDataContainer().set(KEY_DUNGEON, PersistentDataType.STRING, dungeonId.toLowerCase(Locale.ROOT));

        item.setItemMeta(meta);

        // glow opcional
        if (keySec.getBoolean("glow", false)) {
            item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
            ItemMeta m2 = item.getItemMeta();
            if (m2 != null) {
                m2.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(m2);
            }
        }

        return item;
    }

    public boolean isKeyForDungeon(ItemStack stack, String dungeonId) {
        if (stack == null || stack.getType().isAir()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;

        String type = meta.getPersistentDataContainer().get(KEY_TYPE, PersistentDataType.STRING);
        if (type == null || !type.equalsIgnoreCase("key")) return false;

        String did = meta.getPersistentDataContainer().get(KEY_DUNGEON, PersistentDataType.STRING);
        if (did == null) return false;

        return did.equalsIgnoreCase(dungeonId);
    }

    public boolean hasKey(Player player, String dungeonId) {
        for (ItemStack it : player.getInventory().getContents()) {
            if (isKeyForDungeon(it, dungeonId)) return true;
        }
        return false;
    }

    /**
     * Remove 1 chave do inventário (retorna true se removeu).
     * ✅ LOGA key usada NO EXATO ponto em que a chave foi consumida.
     */
    public boolean consumeOneKey(Player player, String dungeonId) {
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (!isKeyForDungeon(it, dungeonId)) continue;

            int amt = it.getAmount();
            if (amt <= 1) contents[i] = null;
            else it.setAmount(amt - 1);

            player.getInventory().setContents(contents);
            player.updateInventory();

            // ✅ LOG KEY USED aqui (EXATO ponto do consumo)
            if (plugin.getRunLogger() != null) {
                plugin.getRunLogger().logKeyUsed(player.getUniqueId(), dungeonId);
            }

            return true;
        }
        return false;
    }

    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        if (text == null) return "";
        String out = text;
        for (var e : placeholders.entrySet()) out = out.replace(e.getKey(), e.getValue());
        return out;
    }
}
