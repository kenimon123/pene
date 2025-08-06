package mp.kenimon;

import mp.kenimon.listeners.PanelClickListener;
import mp.kenimon.listeners.CosmeticMenuListener;
import mp.kenimon.listeners.ShopListener;
import mp.kenimon.utils.WorldGuardUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import mp.kenimon.managers.*;
import mp.kenimon.commands.*;
import mp.kenimon.listeners.KillListener;
import mp.kenimon.placeholders.KenicompetitivoPlaceholders;

public class Kenicompetitivo extends JavaPlugin {

    private static Kenicompetitivo instance;
    private ConfigManager configManager;
    private ShopManager shopManager;
    private GemManager gemManager;
    private DatabaseManager databaseManager;
    private PanelManager panelManager;
    private CacheManager cacheManager;
    private RankingManager rankingManager;
    private AutoUpdateManager autoUpdateManager;
    private CosmeticManager cosmeticManager;
    private SignSelectionManager signSelectionManager;
    private TopStreakHeadManager topStreakHeadManager;
    private RewardManager rewardManager;
    private WorldGuardUtil worldGuardUtil;
    private VaultManager vaultManager;
    private PerformanceMonitor performanceMonitor;

    public static Kenicompetitivo getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Inicializamos la configuración y mensajes
        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        // Inicializar monitor de rendimiento
        performanceMonitor = new PerformanceMonitor(this);

        // Base de datos
        databaseManager = new DatabaseManager(this);

        // Inicializamos el sistema de caché
        cacheManager = new CacheManager(this);
        getServer().getPluginManager().registerEvents(cacheManager, this);

        // Economias
        gemManager = new GemManager(this);
        vaultManager = new VaultManager(this);

        // Core
        autoUpdateManager = new AutoUpdateManager(this);
        autoUpdateManager.startUpdateTasks();
        worldGuardUtil = new WorldGuardUtil(this);

        // Inicializamos el sistema de cosméticos
        cosmeticManager = new CosmeticManager(this);

        // Inicializar shop manager de forma asíncrona para evitar bloqueos de conexión
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                shopManager = new ShopManager(this);
                getLogger().info("ShopManager inicializado correctamente de forma asíncrona");
            } catch (Exception e) {
                getLogger().severe("Error al inicializar ShopManager: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // Inicializamos el sistema de rankings/leaderboards
        rankingManager = new RankingManager(this);

        // Inicializamos el sistema de selección de letreros
        signSelectionManager = new SignSelectionManager(this);

        // Inicializamos el sistema de recompensas
        rewardManager = new RewardManager(this);

        // Inicializamos el panel de "trofeos"
        panelManager = new PanelManager(this);

        // Inicializamos el sistema de cabeza para el jugador top
        topStreakHeadManager = new TopStreakHeadManager(this);

        // Registrar eventos
        getServer().getPluginManager().registerEvents(new PanelClickListener(this), this);
        getServer().getPluginManager().registerEvents(new KillListener(this), this);
        getServer().getPluginManager().registerEvents(new CosmeticMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);

        // Registrar comandos
        getCommand("region").setExecutor(new RegionCommands(this));
        getCommand("camino").setExecutor(new CaminoCommand(this));
        getCommand("kenicompetitivo").setExecutor(new KenicompetitivoCommand(this));
        getCommand("ranking").setExecutor(new RankingCommand(this));
        getCommand("cosmeticos").setExecutor(new CosmeticosCommand(this));
        getCommand("head").setExecutor(new HeadCommand(this));
        getCommand("tienda").setExecutor(new TiendaCommand(this));
        getCommand("tienda").setTabCompleter(new TiendaCommand(this));
        getCommand("reward").setExecutor(new RewardCommand(this));


        // Registrar placeholders si PlaceholderAPI está presente
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new KenicompetitivoPlaceholders(this).register();
            getLogger().info("PlaceholderAPI encontrado y placeholders registrados!");
        } else {
            getLogger().warning("PlaceholderAPI no encontrado. Los placeholders no funcionarán.");
        }

        // Actualizar rankings al inicio
        rankingManager.updateAllRankings();
        rankingManager.updateAllDisplays();

        // Actualizar cabeza del mejor jugador
        topStreakHeadManager.updateTopStreakHead();

        // Guardar datos al desactivar el plugin
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onServerStop(PluginDisableEvent event) {
                if (event.getPlugin().equals(Kenicompetitivo.this)) {
                }
            }
        }, this);

        if (getConfig().getBoolean("migrate_rewards", true)) {
            getRewardManager().migrateFromYamlToDatabase();
            getConfig().set("migrate_rewards", false);
            saveConfig();
        }

        getLogger().info("¡Plugin Kenicompetitivo activado con éxito!");
    }

    @Override
    public void onDisable() {
        // Guardar datos pendientes antes de desactivar
        if (cacheManager != null) {
            cacheManager.saveAllPendingChanges();
        }

        // Cerrar conexión con la base de datos
        if (databaseManager != null) {
            databaseManager.close();
        }

        if (autoUpdateManager != null) {
        }

        getLogger().info("¡Plugin Kenicompetitivo desactivado!");
    }

    // Getters para los managers
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PanelManager getPanelManager() {
        return panelManager;
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    public RankingManager getRankingManager() {
        return rankingManager;
    }

    public CosmeticManager getCosmeticManager() {
        return cosmeticManager;
    }

    public SignSelectionManager getSignSelectionManager() {
        return signSelectionManager;
    }

    public TopStreakHeadManager getTopStreakHeadManager() {
        return topStreakHeadManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public WorldGuardUtil getWorldGuardUtil() {
        return worldGuardUtil;
    }

    public ShopManager getShopManager() {
        // Verificar si el ShopManager está inicializado (puede tardar por carga asíncrona)
        if (shopManager == null) {
            getLogger().warning("ShopManager aún no está inicializado - inicializando ahora de forma síncrona como fallback");
            try {
                shopManager = new ShopManager(this);
            } catch (Exception e) {
                getLogger().severe("Error crítico: No se pudo inicializar ShopManager: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return shopManager;
    }

    public AutoUpdateManager getAutoUpdateManager() {
        return autoUpdateManager;
    }

    public VaultManager getVaultManager() {
        return vaultManager;
    }

    public GemManager getGemManager() {
        return gemManager;
    }

    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }
}
