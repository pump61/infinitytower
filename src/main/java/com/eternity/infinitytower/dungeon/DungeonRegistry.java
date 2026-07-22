package com.eternity.infinitytower.dungeon;

import com.eternity.infinitytower.InfinityTower;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public final class DungeonRegistry {

    private final InfinityTower plugin;

    // plugins/InfinityTower/dungeons
    private File dungeonsFolder;

    /**
     * DungeonId -> DungeonEntry
     * - principal: id = nome do arquivo (sem .yml)
     * - extra: id = nome da section top-level (ex: my_tower_extra2)
     *
     * OBS: arenas do mesmo arquivo (ex: solo_10_arena2) NÃO entram aqui como dungeon extra,
     * elas são usadas apenas pelo sistema de arenas do SessionManager/DungeonSession.
     */
    private final Map<String, DungeonEntry> entries = new HashMap<>();

    public DungeonRegistry(InfinityTower plugin) {
        this.plugin = plugin;
    }

    // =========================
    // FOLDER + DEFAULTS
    // =========================

    public void ensureFolderAndDefaults() {

        File data = plugin.getDataFolder();
        if (!data.exists()) data.mkdirs();

        this.dungeonsFolder = new File(data, "dungeons");
        if (!dungeonsFolder.exists()) dungeonsFolder.mkdirs();

        // templates dentro do jar: resources/dungeons/*.yml
        copyDefaultIfMissing("dungeons/solo_10.yml");
        copyDefaultIfMissing("dungeons/party_10.yml");
    }

    private void copyDefaultIfMissing(String resourcePath) {

        File outFile = new File(plugin.getDataFolder(), resourcePath);

        if (outFile.exists()) {
            return; // não sobrescreve
        }

        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        try {
            plugin.saveResource(resourcePath, false);
            plugin.getLogger().info("Template criado: " + resourcePath);
        } catch (Throwable t) {
            plugin.getLogger().warning("Falha ao copiar template " + resourcePath + ": " + t.getMessage());
        }
    }

    // =========================
    // LOAD/RELOAD
    // =========================

    public void reload() {
        ensureFolderAndDefaults();

        entries.clear();

        File[] files = dungeonsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;

        for (File f : files) {
            try {
                String fileId = stripExt(f.getName()).toLowerCase(Locale.ROOT);
                if (fileId.isBlank()) continue;

                FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);

                // 1) registra principal (root do arquivo)
                registerEntry(fileId, f, cfg, null);

                // 2) registra extras: top-level keys com seção
                for (String k : cfg.getKeys(false)) {
                    ConfigurationSection sec = cfg.getConfigurationSection(k);
                    if (sec == null) continue;

                    String keyLower = k.toLowerCase(Locale.ROOT);

                    // ✅ NÃO registrar arenas como dungeon extra
                    if (keyLower.startsWith(fileId + "_arena")) continue;

                    if (!looksLikeDungeonSection(sec)) continue;

                    String extraId = sanitizeId(k);
                    if (extraId.isBlank()) continue;
                    if (extraId.equalsIgnoreCase(fileId)) continue;

                    registerEntry(extraId, f, cfg, k);
                }

            } catch (Throwable t) {
                plugin.getLogger().warning("Falha ao carregar dungeon " + f.getName() + ": " + t.getMessage());
            }
        }
    }

    private void registerEntry(String dungeonId, File file, FileConfiguration fileCfg, String extraKeyOrNull) {
        DungeonEntry e = new DungeonEntry(dungeonId, file, fileCfg, extraKeyOrNull);
        entries.put(dungeonId.toLowerCase(Locale.ROOT), e);
    }

    private boolean looksLikeDungeonSection(ConfigurationSection sec) {
        return sec.contains("player_spawns")
                || sec.contains("return_spawn")
                || sec.contains("floors")
                || sec.contains("mode")
                || sec.contains("display");
    }

    public Set<String> getDungeonIds() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    /**
     * Retorna a dungeon "mesclada":
     * - principal: root
     * - extra: root + overrides da section extra
     */
    public FileConfiguration getDungeon(String dungeonIdRaw) {
        DungeonEntry e = entryOf(dungeonIdRaw);
        if (e == null) return null;
        return e.getMergedView();
    }

    public boolean exists(String dungeonIdRaw) {
        return entryOf(dungeonIdRaw) != null;
    }

    public File getDungeonFile(String dungeonIdRaw) {
        DungeonEntry e = entryOf(dungeonIdRaw);
        return e == null ? null : e.file;
    }

    public boolean isExtra(String dungeonIdRaw) {
        DungeonEntry e = entryOf(dungeonIdRaw);
        return e != null && e.extraKey != null;
    }

    public String getExtraKey(String dungeonIdRaw) {
        DungeonEntry e = entryOf(dungeonIdRaw);
        return e == null ? null : e.extraKey;
    }

    /**
     * Retorna a "view editável" (onde você deve escrever para salvar):
     * - principal: retorna o próprio FileConfiguration do arquivo (root)
     * - extra: retorna a ConfigurationSection do extra
     */
    public ConfigurationSection getEditableSection(String dungeonIdRaw) {
        DungeonEntry e = entryOf(dungeonIdRaw);
        if (e == null) return null;

        if (e.extraKey == null) {
            return e.fileCfg; // root do arquivo
        }

        ConfigurationSection sec = e.fileCfg.getConfigurationSection(e.extraKey);
        if (sec == null) sec = e.fileCfg.createSection(e.extraKey);
        return sec;
    }

    /**
     * Salva o arquivo da dungeon (principal ou extra).
     */
    public boolean saveDungeon(String dungeonIdRaw) {
        DungeonEntry e = entryOf(dungeonIdRaw);
        if (e == null) return false;

        try {
            e.invalidateMerged();
            e.fileCfg.save(e.file);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Falha ao salvar dungeon " + e.id + ": " + t.getMessage());
            return false;
        }
    }

    // =========================
    // CREATE FROM TEMPLATE
    // =========================

    public boolean createFromTemplate(String dungeonIdRaw) {
        ensureFolderAndDefaults();

        String dungeonId = sanitizeId(dungeonIdRaw);
        if (dungeonId.isBlank()) return false;

        File outFile = new File(dungeonsFolder, dungeonId + ".yml");
        if (outFile.exists()) return false;

        boolean wantParty = dungeonId.startsWith("party");
        File template = new File(dungeonsFolder, wantParty ? "party_10.yml" : "solo_10.yml");

        try {
            java.nio.file.Files.copy(template.toPath(), outFile.toPath());
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Falha ao criar dungeon " + dungeonId + ": " + t.getMessage());
            return false;
        }
    }

    // =========================
    // INTERNAL
    // =========================

    private DungeonEntry entryOf(String raw) {
        if (raw == null) return null;
        String id = sanitizeId(raw);
        if (id.isBlank()) return null;
        return entries.get(id.toLowerCase(Locale.ROOT));
    }

    private String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i == -1 ? name : name.substring(0, i);
    }

    private String sanitizeId(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return s.replaceAll("[^a-z0-9_-]", "");
    }

    // =========================
    // ENTRY
    // =========================

    private static final class DungeonEntry {
        final String id;
        final File file;
        final FileConfiguration fileCfg;
        final String extraKey; // null = principal, != null = extra top-level key

        // cache da view mesclada
        private FileConfiguration merged;

        DungeonEntry(String id, File file, FileConfiguration fileCfg, String extraKey) {
            this.id = id;
            this.file = file;
            this.fileCfg = fileCfg;
            this.extraKey = extraKey;
        }

        void invalidateMerged() {
            this.merged = null;
        }

        FileConfiguration getMergedView() {
            if (merged != null) return merged;

            // ✅ precisa ser FileConfiguration -> usa YamlConfiguration
            YamlConfiguration out = new YamlConfiguration();

            // copia root inteiro
            deepCopySection(fileCfg, out);

            // se for extra, sobrescreve com o que tem na seção
            if (extraKey != null) {
                ConfigurationSection sec = fileCfg.getConfigurationSection(extraKey);
                if (sec != null) {
                    deepCopySection(sec, out);
                }
            }

            this.merged = out;
            return merged;
        }

        private static void deepCopySection(ConfigurationSection from, ConfigurationSection to) {
            for (String key : from.getKeys(false)) {
                Object val = from.get(key);
                if (val instanceof ConfigurationSection sub) {
                    ConfigurationSection subTo = to.getConfigurationSection(key);
                    if (subTo == null) subTo = to.createSection(key);
                    deepCopySection(sub, subTo);
                } else {
                    to.set(key, val);
                }
            }
        }
    }
}