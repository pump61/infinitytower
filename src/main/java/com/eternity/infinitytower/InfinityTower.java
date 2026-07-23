package com.eternity.infinitytower;

import com.eternity.infinitytower.command.TowerCommand;
import com.eternity.infinitytower.database.DatabaseManager;
import com.eternity.infinitytower.database.PlayerStatsRepository;
import com.eternity.infinitytower.database.RunHistoryRepository;
import com.eternity.infinitytower.database.TowerStatsRepository;
import com.eternity.infinitytower.dungeon.DungeonRegistry;
import com.eternity.infinitytower.listener.CommandWhitelistListener;
import com.eternity.infinitytower.listener.MainMenuListener;
import com.eternity.infinitytower.listener.MenuListener;
import com.eternity.infinitytower.listener.MobFriendlyFireListener;
import com.eternity.infinitytower.listener.PartyFriendlyFireListener;
import com.eternity.infinitytower.listener.PlayerConnectionListener;
import com.eternity.infinitytower.listener.StatsMenuListener;
import com.eternity.infinitytower.listener.TowerMobListener;
import com.eternity.infinitytower.listener.TowerSessionListener;
import com.eternity.infinitytower.listener.TowerSpawnListener;
import com.eternity.infinitytower.key.AccessLimiter;
import com.eternity.infinitytower.key.KeyManager;
import com.eternity.infinitytower.log.RunLogger;
import com.eternity.infinitytower.manager.PartyManager;
import com.eternity.infinitytower.manager.RankingManager;
import com.eternity.infinitytower.menu.MenuManager;
import com.eternity.infinitytower.tower.InfinityTowerManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;

public final class InfinityTower extends JavaPlugin {

    private static InfinityTower instance;

    private PartyManager partyManager;
    private RankingManager rankingManager;
    private DatabaseManager databaseManager;
    private PlayerStatsRepository playerStatsRepository;

    private InfinityTowerManager infinityTowerManager;
    private DungeonRegistry dungeonRegistry;

    private MenuManager menuManager;

    private File langFile;
    private FileConfiguration lang;

    private KeyManager keyManager;
    private AccessLimiter accessLimiter;

    private TowerStatsRepository towerStatsRepository;
    private RunHistoryRepository runHistoryRepository;

    private RunLogger runLogger;

    private CommandWhitelistListener commandWhitelistListener;

    @Override
    public void onEnable() {

        instance = this;

        saveDefaultConfig();

        ensureLangFile();
        loadLang();

        this.dungeonRegistry = new DungeonRegistry(this);
        dungeonRegistry.ensureFolderAndDefaults();
        dungeonRegistry.reload();

        initializeManagers();

        // LISTENERS
        getServer().getPluginManager().registerEvents(new MainMenuListener(this), this);

        // ✅ CONEXÃO / DISCONNECT (SUBSTITUI PartyQuitListener)
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        getServer().getPluginManager().registerEvents(new TowerMobListener(this), this);
        getServer().getPluginManager().registerEvents(new TowerSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new TowerSessionListener(this), this);

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new StatsMenuListener(this), this);

        getServer().getPluginManager().registerEvents(new PartyFriendlyFireListener(this), this);
        getServer().getPluginManager().registerEvents(new MobFriendlyFireListener(this), this);

        // ✅ BLOQUEIO DE COMANDOS NA DUNGEON (guarda referência p/ blockBackFor)
        this.commandWhitelistListener = new CommandWhitelistListener(this);
        getServer().getPluginManager().registerEvents(commandWhitelistListener, this);

        // COMMAND
        PluginCommand towerCmd = Objects.requireNonNull(
                getCommand("tower"),
                "Comando 'tower' não encontrado no plugin.yml"
        );

        TowerCommand towerCommand = new TowerCommand(this);

        towerCmd.setExecutor(towerCommand);
        towerCmd.setTabCompleter(towerCommand);

        registerPlaceholdersIfPresent();

        printBanner();

        getLogger().info("Dungeons carregadas: " + dungeonRegistry.getDungeonIds().size());
    }

    @Override
    public void onDisable() {

        if (infinityTowerManager != null)
            infinityTowerManager.shutdown();

        shutdownManagers();

        getLogger().info("InfinityTower desativado.");
    }

    // =====================================================
    // LANG
    // =====================================================

    private void ensureLangFile() {

        String langCode = getConfig().getString("lang", "pt-BR");

        if (langCode == null || langCode.isBlank())
            langCode = "pt-BR";

        langCode = langCode.trim();

        File langFolder = new File(getDataFolder(), "lang");

        if (!langFolder.exists())
            langFolder.mkdirs();

        File target = new File(langFolder, langCode + ".yml");

        if (!target.exists()) {

            boolean extracted = saveResourceSafe("lang/" + langCode + ".yml");

            if (!extracted) {

                getLogger().warning("Idioma não encontrado no JAR: " + langCode);

                saveResourceSafe("lang/pt-BR.yml");

                target = new File(langFolder, "pt-BR.yml");
            }
        }

        this.langFile = target;
    }

    private boolean saveResourceSafe(String path) {

        try {
            saveResource(path, false);
            return true;
        } catch (Exception ignored) {}

        return false;
    }

    private void loadLang() {

        if (langFile == null)
            langFile = new File(getDataFolder(), "lang/pt-BR.yml");

        if (!langFile.exists())
            saveResourceSafe("lang/pt-BR.yml");

        this.lang = YamlConfiguration.loadConfiguration(langFile);

        getLogger().info("Idioma carregado: " + langFile.getName());
    }

    // =====================================================
    // MANAGERS
    // =====================================================

    private void initializeManagers() {

        databaseManager = new DatabaseManager(this);
        databaseManager.connect();

        runLogger = new RunLogger(this);

        keyManager = new KeyManager(this);
        accessLimiter = new AccessLimiter(this);

        towerStatsRepository = new TowerStatsRepository(this);
        runHistoryRepository = new RunHistoryRepository(this);
        playerStatsRepository = new PlayerStatsRepository(this);

        partyManager = new PartyManager(this);
        rankingManager = new RankingManager(this);

        infinityTowerManager = new InfinityTowerManager(this);

        menuManager = new MenuManager(this);
    }

    private void shutdownManagers() {

        if (rankingManager != null)
            rankingManager.shutdown();

        if (databaseManager != null)
            databaseManager.disconnect();

        if (partyManager != null)
            partyManager.clear();
    }

    private void registerPlaceholdersIfPresent() {

        Plugin papi = getServer().getPluginManager().getPlugin("PlaceholderAPI");

        if (papi == null || !papi.isEnabled()) {

            getLogger().warning("PlaceholderAPI não encontrado.");
            return;
        }

        try {

            new com.eternity.infinitytower.placeholder.InfinityTowerExpansion(this).register();

            getLogger().info("PlaceholderAPI registrado.");

        } catch (Throwable t) {

            getLogger().warning("Erro ao registrar PlaceholderAPI: " + t.getMessage());
        }
    }

    private void printBanner() {

        getLogger().info(" ");
        getLogger().info("InfinityTower iniciado com sucesso!");
        getLogger().info("Versão: " + getDescription().getVersion());
        getLogger().info(" ");
    }

    // =====================================================
    // GETTERS
    // =====================================================

    public static InfinityTower getInstance() {
        return instance;
    }

    public FileConfiguration getLang() {
        return lang;
    }

    public DungeonRegistry getDungeonRegistry() {
        return dungeonRegistry;
    }

    public InfinityTowerManager getInfinityTowerManager() {
        return infinityTowerManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public RankingManager getRankingManager() {
        return rankingManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public TowerStatsRepository getTowerStatsRepository() {
        return towerStatsRepository;
    }

    public RunHistoryRepository getRunHistoryRepository() {
        return runHistoryRepository;
    }

    public KeyManager getKeyManager() {
        return keyManager;
    }

    public AccessLimiter getAccessLimiter() {
        return accessLimiter;
    }

    public RunLogger getRunLogger() {
        return runLogger;
    }

    public CommandWhitelistListener getCommandWhitelistListener() {
        return commandWhitelistListener;
    }

    public PlayerStatsRepository getPlayerStatsRepository() {
        return playerStatsRepository;
    }
}