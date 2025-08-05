package mp.kenimon.managers;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;

public class DatabaseManager {

    private final Kenicompetitivo plugin;
    private final String connectionUrl;
    private final ConnectionPool connectionPool;
    private final ExecutorService databaseExecutor;

    public DatabaseManager(Kenicompetitivo plugin) {
        this.plugin = plugin;

        // Asegurar que existe la carpeta de datos
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }

        // Configurar la URL de conexión para SQLite
        File dbFile = new File(plugin.getDataFolder(), "kenicompetitivo.db");
        this.connectionUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        // Crear pool de conexiones optimizado - usar 1 conexión por defecto para SQLite
        int poolSize = plugin.getConfig().getInt("database.pool_size", 1);
        this.connectionPool = new ConnectionPool(plugin, connectionUrl, poolSize);
        
        // Crear executor para operaciones de base de datos asíncronas
        this.databaseExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "Kenicompetitivo-DB-Worker");
            t.setDaemon(true);
            return t;
        });

        // Inicializar la base de datos
        setupDatabase();
        migratePlayersFromConfig();
        
        // Programar tarea periódica para procesar batch updates
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            processBatchUpdates();
        }, 100L, 100L); // Cada 5 segundos
        
        // Programar health check del pool cada 2 minutos
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            connectionPool.healthCheck();
        }, 2400L, 2400L); // Cada 2 minutos
    }

    /**
     * Obtiene una conexión a la base de datos del pool
     */
    public Connection getConnection() throws SQLException {
        long startTime = System.currentTimeMillis();
        Connection conn = null;
        int retryCount = 0;
        final int maxRetries = 3;
        
        while (retryCount < maxRetries) {
            try {
                conn = connectionPool.getConnection();
                if (conn == null) {
                    throw new SQLException("No se pudo obtener conexión del pool");
                }
                
                // Registrar métricas de rendimiento
                long queryTime = System.currentTimeMillis() - startTime;
                if (plugin.getPerformanceMonitor() != null) {
                    plugin.getPerformanceMonitor().recordDbQuery(queryTime);
                }
                
                return conn;
                
            } catch (SQLException e) {
                retryCount++;
                
                if (e.getMessage().contains("database is locked") || e.getMessage().contains("busy")) {
                    // Base de datos ocupada - reintentar
                    if (retryCount < maxRetries) {
                        try {
                            long waitTime = (long) Math.pow(2, retryCount) * 50; // 100ms, 200ms, 400ms
                            Thread.sleep(waitTime);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new SQLException("Interrupción mientras se esperaba reconexión", ie);
                        }
                        plugin.getLogger().warning("Base de datos ocupada, reintentando obtener conexión... (" + retryCount + "/" + maxRetries + ")");
                        continue;
                    }
                }
                
                // Si llegamos aquí, falló definitivamente
                throw e;
            }
        }
        
        throw new SQLException("No se pudo obtener conexión después de " + maxRetries + " intentos");
    }

    /**
     * Devuelve una conexión al pool
     */
    public void returnConnection(Connection conn) {
        connectionPool.returnConnection(conn);
    }
    
    /**
     * Obtiene el pool de conexiones para estadísticas
     */
    public ConnectionPool getConnectionPool() {
        return connectionPool;
    }
    
    /**
     * Obtiene estadísticas del pool para debugging
     */
    public String getPoolStats() {
        if (connectionPool != null) {
            return connectionPool.getStats().toString();
        }
        return "Pool no disponible";
    }

    /**
     * Configura las tablas necesarias en la base de datos
     */
    public void setupDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Configurar WAL mode una sola vez durante la inicialización
            stmt.execute("PRAGMA journal_mode = WAL");
            
            // Tabla principal de jugadores con índices optimizados
            String sql = "CREATE TABLE IF NOT EXISTS players (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "trophies INTEGER DEFAULT 0, " +
                    "kill_streak INTEGER DEFAULT 0, " +
                    "max_kill_streak INTEGER DEFAULT 0" +
                    ");";
            stmt.execute(sql);

            // Crear índices para consultas optimizadas
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_players_trophies ON players(trophies DESC);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_players_kill_streak ON players(kill_streak DESC);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_players_max_kill_streak ON players(max_kill_streak DESC);");

            // Tabla para cosméticos desbloqueados
            sql = "CREATE TABLE IF NOT EXISTS cosmetics_unlocked (" +
                    "uuid TEXT, " +
                    "effect_id TEXT, " +
                    "unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (uuid, effect_id), " +
                    "FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE" +
                    ");";
            stmt.execute(sql);

            // Tabla para cosméticos seleccionados
            sql = "CREATE TABLE IF NOT EXISTS cosmetics_selected (" +
                    "uuid TEXT, " +
                    "category TEXT, " +
                    "effect_id TEXT, " +
                    "selected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (uuid, category), " +
                    "FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE" +
                    ");";
            stmt.execute(sql);

            plugin.getLogger().info("Base de datos inicializada con índices optimizados");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error configurando la base de datos", e);
        }
    }

    /**
     * Cierra recursos de la base de datos
     */
    public void close() {
        // Cerrar el pool de conexiones
        if (connectionPool != null) {
            connectionPool.shutdown();
        }
        
        // Cerrar el executor
        if (databaseExecutor != null && !databaseExecutor.isShutdown()) {
            databaseExecutor.shutdown();
            try {
                if (!databaseExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    databaseExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                databaseExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        plugin.getLogger().info("Recursos de base de datos liberados.");
    }

    /**
     * Migra los datos de cosméticos desde config.yml a la base de datos
     */
    public void migratePlayersFromConfig() {
        try {
            if (!plugin.getConfigManager().getConfig().contains("cosmetics.players")) {
                return;
            }

            for (String uuidStr : plugin.getConfigManager().getConfig().getConfigurationSection("cosmetics.players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);

                    // Migrar cosméticos desbloqueados
                    List<String> unlockedEffects = plugin.getConfigManager().getConfig()
                            .getStringList("cosmetics.players." + uuidStr + ".unlocked");

                    for (String effectId : unlockedEffects) {
                        unlockCosmetic(uuid, effectId);
                    }

                    // Migrar cosméticos seleccionados
                    if (plugin.getConfigManager().getConfig()
                            .contains("cosmetics.players." + uuidStr + ".selected")) {
                        for (String category : plugin.getConfigManager().getConfig()
                                .getConfigurationSection("cosmetics.players." + uuidStr + ".selected").getKeys(false)) {
                            String effectId = plugin.getConfigManager().getConfig()
                                    .getString("cosmetics.players." + uuidStr + ".selected." + category);
                            selectCosmetic(uuid, category, effectId);
                        }
                    }

                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("UUID inválido en configuración: " + uuidStr);
                }
            }

            // Eliminar sección una vez migrada
            plugin.getConfigManager().getConfig().set("cosmetics.players", null);
            plugin.getConfigManager().saveConfig();
            plugin.getLogger().info("Migración de datos de cosméticos a la base de datos completada.");

        } catch (Exception e) {
            plugin.getLogger().warning("Error durante la migración de datos: " + e.getMessage());
        }
    }

    /**
     * Verifica si un jugador existe en la base de datos (ASÍNCRONO)
     */
    public CompletableFuture<Boolean> playerExistsAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM players WHERE uuid = ? LIMIT 1")) {
                
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                boolean exists = rs.next();
                rs.close();
                return exists;
            } catch (SQLException e) {
                plugin.getLogger().warning("Error al verificar jugador: " + e.getMessage());
                return false;
            }
        }, databaseExecutor);
    }

    /**
     * Verifica si un jugador existe en la base de datos (SÍNCRONO - solo para compatibilidad)
     */
    public boolean playerExists(UUID uuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM players WHERE uuid = ? LIMIT 1")) {
            
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            boolean exists = rs.next();
            rs.close();
            return exists;
        } catch (SQLException e) {
            plugin.getLogger().warning("Error al verificar jugador: " + e.getMessage());
            return false;
        }
    }

    /**
     * Registra un nuevo jugador (ASÍNCRONO)
     */
    public CompletableFuture<Void> registerPlayerAsync(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR IGNORE INTO players (uuid, trophies, kill_streak, max_kill_streak) VALUES (?, 0, 0, 0)")) {
                
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
                
            } catch (SQLException e) {
                plugin.getLogger().warning("Error al registrar jugador: " + e.getMessage());
            }
        }, databaseExecutor);
    }

    /**
     * Registra un nuevo jugador (SÍNCRONO - para compatibilidad)
     */
    public void registerPlayer(UUID uuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR IGNORE INTO players (uuid, trophies, kill_streak, max_kill_streak) VALUES (?, 0, 0, 0)")) {
            
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            
        } catch (SQLException e) {
            plugin.getLogger().warning("Error al registrar jugador: " + e.getMessage());
        }
    }

    /**
     * Establece la cantidad de trofeos para un jugador (ASÍNCRONO)
     */
    public CompletableFuture<Void> setTrophiesAsync(UUID uuid, int amount) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO players (uuid, trophies, kill_streak, max_kill_streak) " +
                     "VALUES (?, ?, COALESCE((SELECT kill_streak FROM players WHERE uuid = ?), 0), " +
                     "COALESCE((SELECT max_kill_streak FROM players WHERE uuid = ?), 0))")) {
                
                ps.setString(1, uuid.toString());
                ps.setInt(2, amount);
                ps.setString(3, uuid.toString());
                ps.setString(4, uuid.toString());
                ps.executeUpdate();
                
            } catch (SQLException e) {
                plugin.getLogger().warning("Error al establecer trofeos: " + e.getMessage());
            }
        }, databaseExecutor);
    }

    /**
     * Establece la cantidad de trofeos para un jugador (SÍNCRONO - para compatibilidad)
     */
    public void setTrophies(UUID uuid, int amount) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO players (uuid, trophies, kill_streak, max_kill_streak) " +
                 "VALUES (?, ?, COALESCE((SELECT kill_streak FROM players WHERE uuid = ?), 0), " +
                 "COALESCE((SELECT max_kill_streak FROM players WHERE uuid = ?), 0))")) {
            
            ps.setString(1, uuid.toString());
            ps.setInt(2, amount);
            ps.setString(3, uuid.toString());
            ps.setString(4, uuid.toString());
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al establecer trofeos: " + e.getMessage());
        }
    }

    // Variables para batch operations
    private final Map<UUID, BatchPlayerData> pendingBatchUpdates = new ConcurrentHashMap<>();
    private volatile long lastBatchProcess = System.currentTimeMillis();
    private static final long BATCH_PROCESS_INTERVAL = 5000; // 5 segundos
    private static final int BATCH_SIZE_THRESHOLD = 20; // Procesar cuando hay 20+ updates pendientes

    /**
     * Clase para datos de batch
     */
    private static class BatchPlayerData {
        Integer trophies;
        Integer killStreak;
        Integer maxKillStreak;
        long lastUpdate;
        
        BatchPlayerData() {
            this.lastUpdate = System.currentTimeMillis();
        }
    }

    /**
     * Actualiza trofeos usando batch (más eficiente para múltiples updates)
     */
    public void updateTrophiesBatch(UUID uuid, int trophies) {
        BatchPlayerData data = pendingBatchUpdates.computeIfAbsent(uuid, k -> new BatchPlayerData());
        data.trophies = trophies;
        data.lastUpdate = System.currentTimeMillis();
        
        // Procesar batch si hay muchos updates pendientes o ha pasado tiempo
        if (pendingBatchUpdates.size() >= BATCH_SIZE_THRESHOLD || 
            System.currentTimeMillis() - lastBatchProcess > BATCH_PROCESS_INTERVAL) {
            processBatchUpdates();
        }
    }

    /**
     * Actualiza kill streak usando batch
     */
    public void updateKillStreakBatch(UUID uuid, int killStreak, int maxKillStreak) {
        BatchPlayerData data = pendingBatchUpdates.computeIfAbsent(uuid, k -> new BatchPlayerData());
        data.killStreak = killStreak;
        if (maxKillStreak > 0) {
            data.maxKillStreak = maxKillStreak;
        }
        data.lastUpdate = System.currentTimeMillis();
        
        if (pendingBatchUpdates.size() >= BATCH_SIZE_THRESHOLD || 
            System.currentTimeMillis() - lastBatchProcess > BATCH_PROCESS_INTERVAL) {
            processBatchUpdates();
        }
    }

    /**
     * Procesa todas las actualizaciones batch pendientes
     */
    public void processBatchUpdates() {
        if (pendingBatchUpdates.isEmpty()) {
            return;
        }

        // Copiar datos y limpiar el map original
        Map<UUID, BatchPlayerData> toProcess = new HashMap<>(pendingBatchUpdates);
        pendingBatchUpdates.clear();
        lastBatchProcess = System.currentTimeMillis();

        // Procesar asíncronamente
        CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);

                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO players (uuid, trophies, kill_streak, max_kill_streak) " +
                    "VALUES (?, " +
                    "COALESCE(?, COALESCE((SELECT trophies FROM players WHERE uuid = ?), 0)), " +
                    "COALESCE(?, COALESCE((SELECT kill_streak FROM players WHERE uuid = ?), 0)), " +
                    "COALESCE(?, COALESCE((SELECT max_kill_streak FROM players WHERE uuid = ?), 0)))")) {

                    int batchCount = 0;
                    for (Map.Entry<UUID, BatchPlayerData> entry : toProcess.entrySet()) {
                        UUID uuid = entry.getKey();
                        BatchPlayerData data = entry.getValue();

                        ps.setString(1, uuid.toString());
                        ps.setObject(2, data.trophies); // null si no hay update
                        ps.setString(3, uuid.toString());
                        ps.setObject(4, data.killStreak);
                        ps.setString(5, uuid.toString());
                        ps.setObject(6, data.maxKillStreak);
                        ps.setString(7, uuid.toString());

                        ps.addBatch();
                        batchCount++;

                        // Procesar en lotes de 50 para evitar memory issues
                        if (batchCount % 50 == 0) {
                            ps.executeBatch();
                            ps.clearBatch();
                        }
                    }

                    // Procesar lote final
                    if (batchCount % 50 != 0) {
                        ps.executeBatch();
                    }

                    conn.commit();

                    // Registrar métricas de rendimiento
                    if (plugin.getPerformanceMonitor() != null) {
                        plugin.getPerformanceMonitor().recordBatchOperation(toProcess.size());
                    }

                    if (plugin.getConfig().getBoolean("database.debug", false)) {
                        plugin.getLogger().info("Procesadas " + toProcess.size() + " actualizaciones batch");
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error procesando batch updates", e);
            }
        }, databaseExecutor);
    }
    /**
     * Obtiene la cantidad de trofeos de un jugador (OPTIMIZADO)
     */
    public int getTrophies(UUID uuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT trophies FROM players WHERE uuid = ? LIMIT 1")) {
            
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int trophies = rs.getInt("trophies");
                rs.close();
                return trophies;
            } else {
                rs.close();
                // Registrar jugador asíncronamente para futuros accesos
                registerPlayerAsync(uuid);
                return 0;
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al obtener trofeos: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Establece la racha actual de kills para un jugador (OPTIMIZADO)
     * Usa UPSERT para evitar múltiples consultas
     */
    public void setKillStreak(UUID uuid, int streak) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO players (uuid, trophies, kill_streak, max_kill_streak) " +
                 "VALUES (?, " +
                 "COALESCE((SELECT trophies FROM players WHERE uuid = ?), 0), " +
                 "?, " +
                 "MAX(?, COALESCE((SELECT max_kill_streak FROM players WHERE uuid = ?), 0)))")) {
            
            ps.setString(1, uuid.toString());
            ps.setString(2, uuid.toString());
            ps.setInt(3, streak);
            ps.setInt(4, streak);
            ps.setString(5, uuid.toString());
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al establecer racha de kills: " + e.getMessage());
        }
    }

    /**
     * Obtiene la racha actual de kills de un jugador (OPTIMIZADO)
     */
    public int getKillStreak(UUID uuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT kill_streak FROM players WHERE uuid = ? LIMIT 1")) {
            
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int streak = rs.getInt("kill_streak");
                rs.close();
                return streak;
            } else {
                rs.close();
                // Registrar jugador asíncronamente
                registerPlayerAsync(uuid);
                return 0;
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al obtener racha de kills: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Obtiene la racha máxima de kills de un jugador
     */
    public int getMaxKillStreak(UUID uuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT max_kill_streak FROM players WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int maxStreak = rs.getInt("max_kill_streak");
                rs.close();
                return maxStreak;
            } else {
                rs.close();
                registerPlayer(uuid);
                return 0;
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al obtener racha máxima de kills: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Desbloquea un cosmético para un jugador
     */
    public void unlockCosmetic(UUID uuid, String effectId) {
        try (Connection conn = getConnection()) {
            registerPlayer(uuid);

            // Verificar si ya está desbloqueado
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM cosmetics_unlocked WHERE uuid = ? AND effect_id = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, effectId);
                ResultSet rs = ps.executeQuery();

                if (!rs.next()) {
                    // No existe, insertar
                    try (PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO cosmetics_unlocked (uuid, effect_id) VALUES (?, ?)")) {
                        insert.setString(1, uuid.toString());
                        insert.setString(2, effectId);
                        insert.executeUpdate();
                    }
                }

                rs.close();
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al desbloquear cosmético: " + e.getMessage());
        }
    }

    /**
     * Verifica si un jugador tiene desbloqueado un cosmético
     */
    public boolean hasUnlockedCosmetic(UUID uuid, String effectId) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM cosmetics_unlocked WHERE uuid = ? AND effect_id = ?")) {

            ps.setString(1, uuid.toString());
            ps.setString(2, effectId);
            ResultSet rs = ps.executeQuery();
            boolean unlocked = rs.next();
            rs.close();
            return unlocked;

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al verificar cosmético desbloqueado: " + e.getMessage());
            return false;
        }
    }

    /**
     * Obtiene todos los cosméticos desbloqueados de un jugador
     */
    public List<String> getUnlockedCosmetics(UUID uuid) {
        List<String> unlocked = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT effect_id FROM cosmetics_unlocked WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                unlocked.add(rs.getString("effect_id"));
            }

            rs.close();
            return unlocked;

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al obtener cosméticos desbloqueados: " + e.getMessage());
            return unlocked;
        }
    }

    /**
     * Selecciona un cosmético para un jugador en una categoría específica
     */
// Usar selectCosmetic con menos operaciones
    public void selectCosmetic(UUID uuid, String category, String effectId) {
        try (Connection conn = getConnection()) {
            // Si effectId es null, eliminar selección
            if (effectId == null) {
                try (PreparedStatement delete = conn.prepareStatement(
                        "DELETE FROM cosmetics_selected WHERE uuid = ? AND category = ?")) {
                    delete.setString(1, uuid.toString());
                    delete.setString(2, category);
                    delete.executeUpdate();
                }
            } else {
                // Usar INSERT OR REPLACE para SQLite (o MERGE para otros DBMS)
                try (PreparedStatement merge = conn.prepareStatement(
                        "INSERT OR REPLACE INTO cosmetics_selected (uuid, category, effect_id) VALUES (?, ?, ?)")) {
                    merge.setString(1, uuid.toString());
                    merge.setString(2, category);
                    merge.setString(3, effectId);
                    merge.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error al seleccionar cosmético: " + e.getMessage());
        }
    }

    /**
     * Obtiene el cosmético seleccionado por un jugador en una categoría
     */
    public String getSelectedCosmetic(UUID uuid, String category) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT effect_id FROM cosmetics_selected WHERE uuid = ? AND category = ?")) {

            ps.setString(1, uuid.toString());
            ps.setString(2, category);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String effectId = rs.getString("effect_id");
                rs.close();
                return effectId;
            } else {
                rs.close();
                return null;
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al obtener cosmético seleccionado: " + e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene todos los cosméticos seleccionados por un jugador
     */
    public Map<String, String> getSelectedCosmetics(UUID uuid) {
        Map<String, String> selected = new HashMap<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT category, effect_id FROM cosmetics_selected WHERE uuid = ?")) {

            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                selected.put(rs.getString("category"), rs.getString("effect_id"));
            }

            rs.close();
            return selected;

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al obtener cosméticos seleccionados: " + e.getMessage());
            return selected;
        }
    }

    /**
     * Obtiene el jugador con la racha de kills más alta
     */
    public UUID getTopStreakPlayer() {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT uuid FROM players ORDER BY max_kill_streak DESC LIMIT 1")) {

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String uuidStr = rs.getString("uuid");
                rs.close();
                return UUID.fromString(uuidStr);
            } else {
                rs.close();
                return null;
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al obtener jugador con mayor racha: " + e.getMessage());
            return null;
        }
    }

    /**
     * Añade rachas a un jugador (para comandos administrativos)
     */
    public void addKillStreak(UUID uuid, int amount) {
        try (Connection conn = getConnection()) {
            registerPlayer(uuid);

            int currentStreak = getKillStreak(uuid);
            int newStreak = currentStreak + amount;
            setKillStreak(uuid, newStreak);

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al añadir rachas: " + e.getMessage());
        }
    }

    /**
     * Obtiene el UUID del jugador con más trofeos
     */
    public UUID getTopTrophiesPlayer() {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT uuid FROM players ORDER BY trophies DESC LIMIT 1")) {

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String uuidStr = rs.getString("uuid");
                rs.close();
                return UUID.fromString(uuidStr);
            } else {
                rs.close();
                return null;
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al obtener jugador con mayor cantidad de trofeos: " + e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene la cantidad de trofeos del jugador con más trofeos
     */
    public int getTopTrophiesAmount() {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT trophies FROM players ORDER BY trophies DESC LIMIT 1")) {

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int trophies = rs.getInt("trophies");
                rs.close();
                return trophies;
            } else {
                rs.close();
                return 0;
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al obtener cantidad de trofeos máxima: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Actualiza la racha de kills de un jugador asincrónicamente
     */
    public void updateKillStreak(UUID uuid, int streak, Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection()) {
                registerPlayer(uuid);

                int maxStreak = getMaxKillStreak(uuid);
                if (streak > maxStreak) {
                    // Actualizar también la racha máxima
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE players SET kill_streak = ?, max_kill_streak = ? WHERE uuid = ?")) {
                        ps.setInt(1, streak);
                        ps.setInt(2, streak);
                        ps.setString(3, uuid.toString());
                        int updated = ps.executeUpdate();

                        // Ejecutar callback en el hilo principal
                        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(updated > 0));
                    }
                } else {
                    // Solo actualizar racha actual
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE players SET kill_streak = ? WHERE uuid = ?")) {
                        ps.setInt(1, streak);
                        ps.setString(2, uuid.toString());
                        int updated = ps.executeUpdate();

                        // Ejecutar callback en el hilo principal
                        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(updated > 0));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Error al establecer racha de kills: " + e.getMessage());

                // Ejecutar callback en el hilo principal con error
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(false));
            }
        });
    }

    /**
     * Obtiene la racha actual de kills de un jugador asincrónicamente
     */
    public void getKillStreakAsync(UUID uuid, Consumer<Integer> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int streak = 0;

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT kill_streak FROM players WHERE uuid = ?")) {

                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    streak = rs.getInt("kill_streak");
                } else {
                    // Si no existe, registramos al jugador
                    registerPlayer(uuid);
                }

                rs.close();

                // Resultado final para el callback
                final int result = streak;

                // Ejecutar callback en el hilo principal
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));

            } catch (SQLException e) {
                plugin.getLogger().warning("Error al obtener racha de kills: " + e.getMessage());

                // Ejecutar callback en el hilo principal con error
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(0));
            }
        });
    }

    /**
     * Obtiene la racha máxima de kills de un jugador asincrónicamente
     */
    public void getMaxKillStreakAsync(UUID uuid, Consumer<Integer> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int maxStreak = 0;

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT max_kill_streak FROM players WHERE uuid = ?")) {

                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    maxStreak = rs.getInt("max_kill_streak");
                } else {
                    // Si no existe, registramos al jugador
                    registerPlayer(uuid);
                }

                rs.close();

                // Resultado final para el callback
                final int result = maxStreak;

                // Ejecutar callback en el hilo principal
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));

            } catch (SQLException e) {
                plugin.getLogger().warning("Error al obtener racha máxima de kills: " + e.getMessage());

                // Ejecutar callback en el hilo principal con error
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(0));
            }
        });
    }

    /**
     * Obtiene los trofeos de un jugador asincrónicamente
     */
    public void getTrophiesAsync(UUID uuid, Consumer<Integer> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int trophies = 0;

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT trophies FROM players WHERE uuid = ?")) {

                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    trophies = rs.getInt("trophies");
                } else {
                    // Si no existe, registramos al jugador
                    registerPlayer(uuid);
                }

                rs.close();

                // Resultado final para el callback
                final int result = trophies;

                // Ejecutar callback en el hilo principal
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));

            } catch (SQLException e) {
                plugin.getLogger().warning("Error al obtener trofeos: " + e.getMessage());

                // Ejecutar callback en el hilo principal con error
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(0));
            }
        });
    }

    // Variables para la caché
    private Map<UUID, Integer> killStreakCache = new ConcurrentHashMap<>();
    private long lastKillStreakCacheUpdate = 0;
    private static final long CACHE_EXPIRY = 60000; // 1 minuto

    /**
     * Obtiene todas las rachas actuales de todos los jugadores
     * Versión optimizada con caché
     */
    public Map<UUID, Integer> getAllKillStreaks() {
        // Si la caché está fresca, usarla
        if (System.currentTimeMillis() - lastKillStreakCacheUpdate < CACHE_EXPIRY) {
            return new HashMap<>(killStreakCache);
        }

        // Si la caché expiró, actualizarla
        Map<UUID, Integer> streaks = new HashMap<>();

        // Umbral mínimo configurable de racha para considerar
        int minStreakThreshold = plugin.getConfigManager().getConfig().getInt("general.min_streak_threshold", 1);

        try (Connection conn = getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT uuid, kill_streak FROM players WHERE kill_streak >= ? ORDER BY kill_streak DESC")) {

                stmt.setInt(1, minStreakThreshold);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    try {
                        UUID playerUUID = UUID.fromString(rs.getString("uuid"));
                        int currentStreak = rs.getInt("kill_streak");

                        // Añadir al mapa
                        streaks.put(playerUUID, currentStreak);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("UUID inválido en base de datos");
                    }
                }
                rs.close();

                // Actualizar caché
                killStreakCache = new ConcurrentHashMap<>(streaks);
                lastKillStreakCacheUpdate = System.currentTimeMillis();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error al obtener rachas actuales de kills", e);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error de conexión al obtener rachas", e);
        }

        return streaks;
    }

    /**
     * Actualiza la racha máxima de kills de un jugador en la base de datos
     *
     * @param uuid      UUID del jugador
     * @param maxStreak Nuevo valor de racha máxima
     * @param callback  Callback que se ejecuta después de la operación
     */
    public void updateMaxKillStreak(UUID uuid, int maxStreak, Consumer<Boolean> callback) {
        // Ejecutar asincrónicamente para no bloquear el hilo principal
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = false;
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE players SET max_kill_streak = ? WHERE uuid = ?"
                 )) {
                stmt.setInt(1, maxStreak);
                stmt.setString(2, uuid.toString());
                int rowsAffected = stmt.executeUpdate();
                success = rowsAffected > 0;

                // Si no existe el jugador, crearlo
                if (!success) {
                    try (PreparedStatement insertStmt = conn.prepareStatement(
                            "INSERT INTO players (uuid, max_kill_streak) VALUES (?, ?) ON DUPLICATE KEY UPDATE max_kill_streak = ?"
                    )) {
                        insertStmt.setString(1, uuid.toString());
                        insertStmt.setInt(2, maxStreak);
                        insertStmt.setInt(3, maxStreak);
                        insertStmt.executeUpdate();
                        success = true;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error al actualizar la racha máxima de kills", e);
            }

            // Llamar al callback en el hilo principal
            boolean finalSuccess = success;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalSuccess));
        });
    }

    /**
     * Obtiene los N jugadores con mayor racha de kills
     *
     * @param limit Número máximo de jugadores a obtener
     * @return Mapa de UUIDs y sus rachas
     */
    public Map<UUID, Integer> getTopKillStreaks(int limit) {
        Map<UUID, Integer> streaks = new HashMap<>();

        // Umbral mínimo configurable de racha para considerar
        int minStreakThreshold = plugin.getConfigManager().getConfig().getInt("general.min_streak_threshold", 1);

        try (Connection conn = getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT uuid, kill_streak FROM players WHERE kill_streak >= ? ORDER BY kill_streak DESC LIMIT ?")) {

                stmt.setInt(1, minStreakThreshold);
                stmt.setInt(2, limit);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    try {
                        UUID playerUUID = UUID.fromString(rs.getString("uuid"));
                        int currentStreak = rs.getInt("kill_streak");

                        // Añadir al mapa
                        streaks.put(playerUUID, currentStreak);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("UUID inválido en base de datos");
                    }
                }
                rs.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al obtener mejores rachas de kills", e);
        }

        return streaks;
    }
    /**
     * Agrega una actualización de racha de kills al lote para procesar en bulk
     * @param uuid UUID del jugador
     * @param streak Valor de la racha
     */
    public void addKillStreakToBatch(UUID uuid, int streak) {
        // Usamos un Map para almacenar las actualizaciones pendientes
        if (pendingKillStreakBatch == null) {
            pendingKillStreakBatch = new HashMap<>();

            // Programar el procesamiento del lote si es el primer elemento
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                processPendingKillStreakBatch();
            }, 100L); // Procesar después de 5 segundos (100 ticks)
        }

        // Guardar en el lote
        pendingKillStreakBatch.put(uuid, streak);

        // Si el lote es muy grande, procesarlo inmediatamente
        if (pendingKillStreakBatch.size() >= 50) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::processPendingKillStreakBatch);
        }
    }

    /**
     * Variable para el lote de actualizaciones pendientes
     */
    private Map<UUID, Integer> pendingKillStreakBatch = null;

    /**
     * Procesa el lote de actualizaciones de rachas pendientes
     */
    private void processPendingKillStreakBatch() {
        // Verificar si hay algo que procesar
        if (pendingKillStreakBatch == null || pendingKillStreakBatch.isEmpty()) {
            pendingKillStreakBatch = null;
            return;
        }

        // Crear una copia y resetear el lote original
        Map<UUID, Integer> batchToProcess = new HashMap<>(pendingKillStreakBatch);
        pendingKillStreakBatch = null;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE players SET kill_streak = ? WHERE uuid = ?")) {

                // Agregar todas las actualizaciones al batch
                for (Map.Entry<UUID, Integer> entry : batchToProcess.entrySet()) {
                    ps.setInt(1, entry.getValue());
                    ps.setString(2, entry.getKey().toString());
                    ps.addBatch();
                }

                // Ejecutar el batch
                ps.executeBatch();
                conn.commit();

            } catch (SQLException e) {
                // Rollback en caso de error
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    // Error silencioso
                }

                // Si falla el batch, intentar uno por uno
                try {
                    conn.setAutoCommit(true);
                    for (Map.Entry<UUID, Integer> entry : batchToProcess.entrySet()) {
                        setKillStreak(entry.getKey(), entry.getValue());
                    }
                } catch (Exception retryEx) {
                    // Error silencioso
                }
            } finally {
                // Restaurar autocommit
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException finalEx) {
                    // Error silencioso
                }
            }

        } catch (SQLException connectionEx) {
            // Error de conexión, intentar actualizaciones individuales más tarde
            for (Map.Entry<UUID, Integer> entry : batchToProcess.entrySet()) {
                final UUID uuid = entry.getKey();
                final int streak = entry.getValue();

                // Programar un reintento más tarde
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                    setKillStreak(uuid, streak);
                }, 200L + (int)(Math.random() * 100)); // Reintento con delay aleatorio
            }
        }
    }
}
