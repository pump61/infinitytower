package com.eternity.infinitytower.menu;

import com.eternity.infinitytower.InfinityTower;
import com.eternity.infinitytower.database.PlayerStatsRepository;
import com.eternity.infinitytower.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

public final class MenuManager {

    public enum MenuType { SOLO, PARTY }

    private final InfinityTower plugin;

    private File menusFile;
    private FileConfiguration menus;

    public MenuManager(InfinityTower plugin) {
        this.plugin = plugin;
        ensureMenusFile();
        reload();
    }

    public void reload() {
        this.menus = YamlConfiguration.loadConfiguration(menusFile);
    }

    /*
     * =========================================
     * MAIN MENU
     * =========================================
     */

    public void openMainMenu(Player player) {

        FileConfiguration config = plugin.getConfig();

        // aceita main-menu e main_menu
        ConfigurationSection main = config.getConfigurationSection("main-menu");
        if (main == null) main = config.getConfigurationSection("main_menu");

        if (main == null) {
            plugin.getLogger().warning(lang(
                    "logs.main_menu.section_missing",
                    "[MainMenu] Seção não encontrada no config.yml: 'main-menu' (ou 'main_menu')."
            ));

            Inventory inv = Bukkit.createInventory(
                    new MainMenuHolder(-1, -1),
                    27,
                    TextUtil.color(mainMenuFallbackTitle())
            );
            player.openInventory(inv);
            return;
        }

        String title = TextUtil.color(main.getString("title", mainMenuFallbackTitle()));

        int size = main.getInt("size", 27);
        size = clampToInventorySize(size);

        // lê slots dos botões (defaults)
        int soloSlot = 12;
        int partySlot = 14;

        ConfigurationSection buttons = main.getConfigurationSection("buttons");
        if (buttons != null) {
            ConfigurationSection solo = buttons.getConfigurationSection("solo");
            if (solo != null) soloSlot = solo.getInt("slot", soloSlot);

            ConfigurationSection party = buttons.getConfigurationSection("party");
            if (party != null) partySlot = party.getInt("slot", partySlot);
        }

        MainMenuHolder holder = new MainMenuHolder(soloSlot, partySlot);
        Inventory inv = Bukkit.createInventory(holder, size, title);

        /*
         * FILLER
         */
        ConfigurationSection filler = main.getConfigurationSection("filler");
        if (filler == null) {
            plugin.getLogger().warning(lang(
                    "logs.main_menu.filler_missing",
                    "[MainMenu] Seção não encontrada: main-menu.filler"
            ));
        } else {
            boolean enabled = filler.getBoolean("enabled", true);
            if (enabled) {
                ItemStack fillerItem = buildItem(filler, null);
                if (fillerItem != null) {
                    for (int i = 0; i < size; i++) inv.setItem(i, fillerItem);
                }
            }
        }

        /*
         * BOTÕES
         */
        if (buttons == null) {
            plugin.getLogger().warning(lang(
                    "logs.main_menu.buttons_missing",
                    "[MainMenu] Seção não encontrada: main-menu.buttons"
            ));
        } else {

            // SOLO
            ConfigurationSection solo = buttons.getConfigurationSection("solo");
            if (solo == null) {
                plugin.getLogger().warning(lang(
                        "logs.main_menu.button_solo_missing",
                        "[MainMenu] Seção não encontrada: main-menu.buttons.solo"
                ));
            } else {
                boolean enabled = solo.getBoolean("enabled", true);
                if (enabled) {
                    int slot = solo.getInt("slot", 12);
                    if (slot >= 0 && slot < size) {
                        ItemStack item = buildItem(solo, null);
                        if (item != null) inv.setItem(slot, item);
                    }
                }
            }

            // SPACER (opcional)
            ConfigurationSection spacer = buttons.getConfigurationSection("spacer");
            if (spacer != null) {
                boolean enabled = spacer.getBoolean("enabled", true);
                if (enabled) {
                    int slot = spacer.getInt("slot", 13);
                    if (slot >= 0 && slot < size) {
                        ItemStack item = buildItem(spacer, null);
                        inv.setItem(slot, item); // pode ser AIR pra “vazio”
                    }
                }
            }

            // PARTY
            ConfigurationSection party = buttons.getConfigurationSection("party");
            if (party == null) {
                plugin.getLogger().warning(lang(
                        "logs.main_menu.button_party_missing",
                        "[MainMenu] Seção não encontrada: main-menu.buttons.party"
                ));
            } else {
                boolean enabled = party.getBoolean("enabled", true);
                if (enabled) {
                    int slot = party.getInt("slot", 14);
                    if (slot >= 0 && slot < size) {
                        ItemStack item = buildItem(party, null);
                        if (item != null) inv.setItem(slot, item);
                    }
                }
            }
        }

        player.openInventory(inv);
    }

    private String mainMenuFallbackTitle() {
        return lang("menus.main.title_fallback", "&bInfinity Tower");
    }

    public boolean isMainMenu(Inventory inv) {
        return inv != null && inv.getHolder() instanceof MainMenuHolder;
    }

    public MainMenuHolder getMainMenuHolder(Inventory inv) {
        if (inv == null) return null;
        if (!(inv.getHolder() instanceof MainMenuHolder h)) return null;
        return h;
    }

    /*
     * =========================================
     * DUNGEON MENUS (SOLO / PARTY) - EDITÁVEL VIA menus.yml
     * =========================================
     */

    public void openSoloMenu(Player player) {
        openMenu(player, MenuType.SOLO);
    }

    public void openPartyMenu(Player player) {
        openMenu(player, MenuType.PARTY);
    }

    public boolean isTowerMenu(Inventory inv) {
        return inv != null && inv.getHolder() instanceof TowerMenuHolder;
    }

    public TowerMenuHolder getHolder(Inventory inv) {
        if (inv == null) return null;
        if (!(inv.getHolder() instanceof TowerMenuHolder h)) return null;
        return h;
    }

    private void openMenu(Player player, MenuType type) {

        // Mantive compatível com o que você já usa:
        // menus.solo / menus.party
        String path = (type == MenuType.SOLO) ? "menus.solo" : "menus.party";
        ConfigurationSection sec = menus.getConfigurationSection(path);

        if (sec == null) {
            player.sendMessage(apply(
                    lang("messages.menus_invalid_section",
                            "&cmenus.yml inválido: seção do menu não encontrada (&f{path}&c)."),
                    Map.of("{path}", path)
            ));
            return;
        }

        String title = sec.getString("title", "&bInfinityTower");
        int size = clampToInventorySize(sec.getInt("size", 27));

        TowerMenuHolder holder = new TowerMenuHolder(type);

        // placeholders globais pro título também
        Map<String, String> global = new HashMap<>();
        global.put("{player}", player.getName());
        global.put("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()));
        global.put("{max_online}", String.valueOf(Bukkit.getMaxPlayers()));
        String coloredTitle = TextUtil.color(applyPlaceholders(title, global));

        Inventory inv = Bukkit.createInventory(holder, size, coloredTitle);

        /*
         * FILLER (opcional)
         */
        ConfigurationSection fillerSec = sec.getConfigurationSection("filler");
        if (fillerSec != null && fillerSec.getBoolean("enabled", true)) {
            ItemStack filler = buildItem(fillerSec, global);
            if (filler != null && filler.getType() != Material.AIR) {
                for (int i = 0; i < size; i++) inv.setItem(i, filler);
            }
        }

        /*
         * ITENS FIXOS (static-items) - opcional
         * Ex:
         * static-items:
         *   info:
         *     enabled: true
         *     slot: 4
         *     material: BOOK
         *     name: "&eInfo"
         *     lore: [...]
         */
        ConfigurationSection staticItems = sec.getConfigurationSection("static-items");
        if (staticItems != null) {
            for (String key : staticItems.getKeys(false)) {
                ConfigurationSection itSec = staticItems.getConfigurationSection(key);
                if (itSec == null) continue;
                if (!itSec.getBoolean("enabled", true)) continue;

                int slot = itSec.getInt("slot", -1);
                if (slot < 0 || slot >= size) continue;

                ItemStack it = buildItem(itSec, global);
                // AIR => limpa slot
                if (it == null || it.getType() == Material.AIR) inv.setItem(slot, null);
                else inv.setItem(slot, it);
            }
        }

        /*
         * LEAVE / BACK (opcional)
         */
        ConfigurationSection leaveSec = sec.getConfigurationSection("leave");
        if (leaveSec != null && leaveSec.getBoolean("enabled", false)) {
            int slot = leaveSec.getInt("slot", -1);
            if (slot >= 0 && slot < size) {
                ItemStack leaveItem = buildItem(leaveSec, global);
                if (leaveItem == null || leaveItem.getType() == Material.AIR) {
                    inv.setItem(slot, null);
                } else {
                    inv.setItem(slot, leaveItem);
                }
                holder.leaveSlot = slot;
            }
        }

        /*
         * SLOTS ONDE VÃO ENTRAR AS DUNGEONS (obrigatório)
         */
        List<Integer> slots = sec.getIntegerList("dungeon-slots");
        if (slots == null || slots.isEmpty()) {
            player.sendMessage(lang("messages.menus_invalid_slots",
                    "&cmenus.yml inválido: dungeon-slots vazio."));
            return;
        }

        // garante que os slots existam no inventário e "limpa" eles (mesmo se tiver filler)
        List<Integer> usableSlots = new ArrayList<>();
        for (Integer s : slots) {
            if (s == null) continue;
            if (s >= 0 && s < size) {
                usableSlots.add(s);
                inv.setItem(s, null);
            }
        }
        if (usableSlots.isEmpty()) {
            player.sendMessage(lang("messages.menus_invalid_slots",
                    "&cmenus.yml inválido: dungeon-slots não possui slots válidos pro tamanho do menu."));
            return;
        }

        /*
         * ITEM PADRÃO DOS DUNGEONS (dungeon-item) - opcional, mas recomendado
         */
        ConfigurationSection defaultDungeonItem = sec.getConfigurationSection("dungeon-item");

        /*
         * LISTA DUNGEONS
         */
        List<String> dungeonIds = new ArrayList<>(plugin.getDungeonRegistry().getDungeonIds());
        Collections.sort(dungeonIds);

        int placed = 0;

        for (String dungeonId : dungeonIds) {

            if (placed >= usableSlots.size()) break;

            FileConfiguration d = plugin.getDungeonRegistry().getDungeon(dungeonId);
            if (d == null) continue;

            String mode = d.getString("mode", "SOLO").toUpperCase(Locale.ROOT);

            if (type == MenuType.SOLO) {
                if (!mode.equals("SOLO")) continue;
            } else {
                if (!mode.equals("PARTY")) continue;
            }

            int slot = usableSlots.get(placed);

            ConfigurationSection itemSec = d.getConfigurationSection("menu-item");
            if (itemSec == null) itemSec = defaultDungeonItem;

            ItemStack icon = buildDungeonIcon(itemSec, dungeonId, player);
            if (icon == null || icon.getType() == Material.AIR) {
                // se AIR, só pula e não ocupa (pra não "sumir" dungeon)
                // mas mantém placed++ pra respeitar paginação linear
                placed++;
                continue;
            }

            inv.setItem(slot, icon);
            holder.slotToDungeon.put(slot, dungeonId);
            placed++;
        }

        player.openInventory(inv);
    }

    /*
     * =========================================
     * STATS MENU (o seu continua como está)
     * =========================================
     */

    public void openStatsMenu(Player player) {

        int size = 27;
        String title = plugin.getLang().getString("menus.stats.title", "&b&lSuas Estatísticas");
        Inventory inv = Bukkit.createInventory(new StatsMenuHolder(), size, TextUtil.color(title));

        ItemStack filler = simpleItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < size; i++) inv.setItem(i, filler);

        PlayerStatsRepository repo = plugin.getPlayerStatsRepository();
        PlayerStatsRepository.PlayerStats st =
                repo != null ? repo.getStats(player.getUniqueId()) : PlayerStatsRepository.PlayerStats.empty();

        int soloRuns = st.soloRuns;
        int soloWins = st.soloWins;
        int soloLoss = st.soloLosses;

        int partyRuns = st.partyRuns;
        int partyWins = st.partyWins;
        int partyLoss = st.partyLosses;

        int totalRuns = soloRuns + partyRuns;
        int totalWins = soloWins + partyWins;
        int totalLoss = soloLoss + partyLoss;

        inv.setItem(11, simpleItem(Material.DIAMOND_SWORD,
                TextUtil.color(plugin.getLang().getString("menus.stats.solo_name", "&#00FF7F&lSOLO")),
                List.of(
                        TextUtil.color(plugin.getLang().getString("menus.stats.runs", "&7Runs: &f{v}")
                                .replace("{v}", String.valueOf(soloRuns))),
                        TextUtil.color(plugin.getLang().getString("menus.stats.wins", "&7Vitórias: &a{v}")
                                .replace("{v}", String.valueOf(soloWins))),
                        TextUtil.color(plugin.getLang().getString("menus.stats.losses", "&7Derrotas: &c{v}")
                                .replace("{v}", String.valueOf(soloLoss)))
                )
        ));

        inv.setItem(13, simpleItem(Material.BOOK,
                TextUtil.color(plugin.getLang().getString("menus.stats.total_name", "&#FFD700&lGERAL")),
                List.of(
                        TextUtil.color(plugin.getLang().getString("menus.stats.runs", "&7Runs: &f{v}")
                                .replace("{v}", String.valueOf(totalRuns))),
                        TextUtil.color(plugin.getLang().getString("menus.stats.wins", "&7Vitórias: &a{v}")
                                .replace("{v}", String.valueOf(totalWins))),
                        TextUtil.color(plugin.getLang().getString("menus.stats.losses", "&7Derrotas: &c{v}")
                                .replace("{v}", String.valueOf(totalLoss)))
                )
        ));

        inv.setItem(15, simpleItem(Material.GOLDEN_SWORD,
                TextUtil.color(plugin.getLang().getString("menus.stats.party_name", "&#FFD700&lPARTY")),
                List.of(
                        TextUtil.color(plugin.getLang().getString("menus.stats.runs", "&7Runs: &f{v}")
                                .replace("{v}", String.valueOf(partyRuns))),
                        TextUtil.color(plugin.getLang().getString("menus.stats.wins", "&7Vitórias: &a{v}")
                                .replace("{v}", String.valueOf(partyWins))),
                        TextUtil.color(plugin.getLang().getString("menus.stats.losses", "&7Derrotas: &c{v}")
                                .replace("{v}", String.valueOf(partyLoss)))
                )
        ));

        player.openInventory(inv);
    }

    public boolean isStatsMenu(Inventory inv) {
        return inv != null && inv.getHolder() instanceof StatsMenuHolder;
    }

    /*
     * =========================================
     * ITEM BUILDERS
     * =========================================
     */

    private ItemStack buildDungeonIcon(ConfigurationSection itemSec, String dungeonId, Player viewer) {

        if (itemSec == null) return null;

        FileConfiguration d = plugin.getDungeonRegistry().getDungeon(dungeonId);
        if (d == null) return null;

        String display = d.getString("display", dungeonId);
        int maxFloors = d.getInt("max_floors", 10);
        String mode = d.getString("mode", "SOLO");

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{player}", viewer.getName());
        placeholders.put("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()));
        placeholders.put("{max_online}", String.valueOf(Bukkit.getMaxPlayers()));

        placeholders.put("{id}", dungeonId);
        placeholders.put("{display}", display);
        placeholders.put("{max_floors}", String.valueOf(maxFloors));
        placeholders.put("{mode}", mode);

        return buildItem(itemSec, placeholders);
    }

    private ItemStack buildItem(ConfigurationSection itemSec, Map<String, String> placeholders) {

        if (itemSec == null) return null;

        String materialName = itemSec.getString("material", "PAPER");

        Material mat;
        try {
            mat = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            mat = Material.PAPER;
        }

        int amount = Math.max(1, itemSec.getInt("amount", 1));
        ItemStack item = new ItemStack(mat, amount);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {

            String name = itemSec.getString("name", "");
            if (placeholders != null) name = applyPlaceholders(name, placeholders);
            if (!name.isBlank()) meta.setDisplayName(TextUtil.color(name));

            List<String> lore = itemSec.getStringList("lore");
            if (lore != null && !lore.isEmpty()) {
                List<String> out = new ArrayList<>();
                for (String line : lore) {
                    if (placeholders != null) line = applyPlaceholders(line, placeholders);
                    out.add(TextUtil.color(line));
                }
                meta.setLore(out);
            }

            // cmd suportando os 2 formatos
            int cmd = 0;
            if (itemSec.contains("custom-model-data")) cmd = itemSec.getInt("custom-model-data", 0);
            if (itemSec.contains("custom_model_data")) cmd = itemSec.getInt("custom_model_data", cmd);

            if (cmd > 0) {
                try { meta.setCustomModelData(cmd); } catch (Throwable ignored) {}
            }

            // flags (opcional, mas bom pra esconder atributos)
            boolean hide = itemSec.getBoolean("hide_attributes", true);
            if (hide) meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);

            item.setItemMeta(meta);
        }

        // AIR => slot vazio
        if (item.getType() == Material.AIR) return new ItemStack(Material.AIR);

        return item;
    }

    private ItemStack simpleItem(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(TextUtil.color(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> out = new ArrayList<>();
                for (String s : lore) out.add(TextUtil.color(s));
                meta.setLore(out);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        if (text == null) return "";
        String out = text;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    private int clampToInventorySize(int size) {
        if (size < 9) size = 9;
        if (size > 54) size = 54;
        return (size / 9) * 9;
    }

    /*
     * =========================================
     * FILE
     * =========================================
     */

    private void ensureMenusFile() {

        File data = plugin.getDataFolder();
        if (!data.exists()) data.mkdirs();

        this.menusFile = new File(data, "menus.yml");

        if (!menusFile.exists())
            plugin.saveResource("menus.yml", false);
    }

    /*
     * =========================================
     * LANG HELPERS
     * =========================================
     */

    private String lang(String path, String def) {
        return plugin.getLang().getString(path, def);
    }

    private String apply(String msg, Map<String, String> vars) {
        if (msg == null) return "";
        String out = msg;
        for (var e : vars.entrySet()) out = out.replace(e.getKey(), e.getValue());
        return TextUtil.color(out);
    }

    /*
     * =========================================
     * HOLDERS
     * =========================================
     */

    public static final class MainMenuHolder implements InventoryHolder {

        private final int soloSlot;
        private final int partySlot;

        public MainMenuHolder(int soloSlot, int partySlot) {
            this.soloSlot = soloSlot;
            this.partySlot = partySlot;
        }

        public int getSoloSlot() {
            return soloSlot;
        }

        public int getPartySlot() {
            return partySlot;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static final class TowerMenuHolder implements InventoryHolder {

        private final MenuType type;
        private final Map<Integer, String> slotToDungeon = new HashMap<>();

        private int leaveSlot = -1;

        public TowerMenuHolder(MenuType type) {
            this.type = type;
        }

        public MenuType getType() {
            return type;
        }

        public String getDungeonIdAt(int slot) {
            return slotToDungeon.get(slot);
        }

        public boolean isLeaveSlot(int slot) {
            return leaveSlot != -1 && leaveSlot == slot;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static final class StatsMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}