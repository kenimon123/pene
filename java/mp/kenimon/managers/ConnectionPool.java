package mp.kenimon.managers;

import mp.kenimon.Kenicompetitivo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Pool de conexiones optimizado para SQLite
 * Reduce el overhead de crear/destruir conexiones constantemente
 */
public class ConnectionPool {
    
    private final Kenicompetitivo plugin;
    private final String connectionUrl;
    private final BlockingQueue<Connection> pool;
    private final int maxConnections;
    private volatile boolean shutdown = false;
    
    // Configuración optimizada para SQLite con concurrencia mejorada
    private static final int DEFAULT_POOL_SIZE = 5; // Incrementado para manejar operaciones concurrentes
    private static final int CONNECTION_TIMEOUT = 5; // Reducido a 5 segundos para detectar problemas más rápido
    
    public ConnectionPool(Kenicompetitivo plugin, String connectionUrl) {
        this(plugin, connectionUrl, DEFAULT_POOL_SIZE);
    }
    
    public ConnectionPool(Kenicompetitivo plugin, String connectionUrl, int maxConnections) {
        this.plugin = plugin;
        this.connectionUrl = connectionUrl;
        this.maxConnections = maxConnections;
        this.pool = new ArrayBlockingQueue<>(maxConnections);
        
        // Inicializar el pool con conexiones
        initializePool();
        
        plugin.getLogger().info("Connection pool inicializado con " + maxConnections + " conexiones");
    }
    
    private void initializePool() {
        try {
            // Crear conexiones secuencialmente para evitar conflictos de concurrencia
            for (int i = 0; i < maxConnections; i++) {
                synchronized (this) {
                    Connection conn = createConnection();
                    if (conn != null) {
                        pool.offer(conn);
                    }
                    // Pequeña pausa entre conexiones para SQLite
                    Thread.sleep(100);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error al inicializar el pool de conexiones", e);
        }
    }
    
    /**
     * Crea una nueva conexión optimizada para SQLite
     */
    private Connection createConnection() {
        Connection conn = null;
        Statement stmt = null;
        int retryCount = 0;
        final int maxRetries = 3;
        
        while (retryCount < maxRetries) {
            try {
                // Crear conexión con timeout
                conn = DriverManager.getConnection(connectionUrl);
                
                // Verificar que la conexión es válida
                if (!conn.isValid(5)) {
                    throw new SQLException("Conexión no válida después de crear");
                }
                
                stmt = conn.createStatement();
                
                // Optimizaciones específicas para SQLite - ejecutar una por una con validación
                try {
                    stmt.execute("PRAGMA synchronous = NORMAL");  // Balance entre seguridad y rendimiento
                    stmt.execute("PRAGMA cache_size = 20000");    // Cache de 20MB aproximadamente  
                    stmt.execute("PRAGMA temp_store = MEMORY");   // Tablas temporales en memoria
                    stmt.execute("PRAGMA busy_timeout = 30000");  // Timeout de 30 segundos
                    stmt.execute("PRAGMA journal_mode = WAL");    // Modo WAL para mejor concurrencia
                    stmt.execute("PRAGMA wal_autocheckpoint = 1000"); // Checkpoint cada 1000 páginas
                    stmt.execute("PRAGMA read_uncommitted = true"); // Permitir lecturas no comprometidas para mejor rendimiento
                } catch (SQLException pragmaEx) {
                    plugin.getLogger().warning("Error configurando PRAGMAs SQLite: " + pragmaEx.getMessage());
                    // Continuar aunque los PRAGMAs fallen - la conexión básica funciona
                }
                
                stmt.close();
                return conn;
                
            } catch (SQLException e) {
                retryCount++;
                
                // Cerrar recursos en caso de error
                if (stmt != null) {
                    try { stmt.close(); } catch (SQLException ignored) {}
                }
                if (conn != null) {
                    try { conn.close(); } catch (SQLException ignored) {}
                }
                
                if (e.getErrorCode() == 5 || e.getMessage().contains("database is locked") || 
                    e.getMessage().contains("busy")) {
                    // Error SQLITE_BUSY - reintentar con backoff exponencial
                    try {
                        long waitTime = (long) Math.pow(2, retryCount) * 100; // 200ms, 400ms, 800ms
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    
                    if (retryCount < maxRetries) {
                        plugin.getLogger().warning("Base de datos ocupada, reintentando conexión... (" + retryCount + "/" + maxRetries + ")");
                        continue;
                    }
                }
                
                plugin.getLogger().log(Level.WARNING, "Error al crear conexión a la base de datos (intento " + retryCount + "/" + maxRetries + "): " + e.getMessage());
                
                if (retryCount >= maxRetries) {
                    plugin.getLogger().log(Level.SEVERE, "No se pudo crear conexión después de " + maxRetries + " intentos", e);
                    return null;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Obtiene una conexión del pool con manejo mejorado de timeouts
     * @return Connection o null si no está disponible
     */
    public Connection getConnection() {
        if (shutdown) {
            plugin.getLogger().warning("Intentando obtener conexión de pool cerrado");
            return null;
        }
        
        try {
            // Intentar obtener conexión inmediatamente primero
            Connection conn = pool.poll();
            if (conn != null) {
                // Verificar si la conexión sigue siendo válida
                if (!conn.isClosed() && conn.isValid(1)) {
                    return conn;
                } else {
                    plugin.getLogger().info("Conexión inválida detectada, creando nueva");
                    try {
                        conn.close();
                    } catch (SQLException ignored) {}
                    conn = createConnection();
                    return conn;
                }
            }
            
            // Si no hay conexión disponible inmediatamente, esperar un tiempo reducido
            conn = pool.poll(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
            
            if (conn == null) {
                plugin.getLogger().warning("Timeout obteniendo conexión del pool después de " + CONNECTION_TIMEOUT + " segundos");
                // En lugar de crear una nueva conexión costosa, devolver null para manejo asíncrono
                return null;
            }
            
            // Verificar si la conexión sigue siendo válida
            if (conn.isClosed() || !conn.isValid(2)) {
                plugin.getLogger().info("Conexión inválida detectada, creando nueva");
                try {
                    conn.close();
                } catch (SQLException ignored) {}
                conn = createConnection(); // Crear nueva conexión si la anterior se cerró
            }
            
            return conn;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Interrupción mientras se esperaba conexión del pool");
            return null; // No crear conexión de fallback para evitar bloqueos
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error validando conexión: " + e.getMessage());
            return null; // No crear conexión de fallback para evitar bloqueos
        }
    }
    
    /**
     * Devuelve una conexión al pool
     * @param conn La conexión a devolver
     */
    public void returnConnection(Connection conn) {
        if (conn != null && !shutdown) {
            try {
                if (!conn.isClosed() && conn.isValid(1)) {
                    // Solo devolver al pool si la conexión está válida
                    if (!pool.offer(conn)) {
                        // Pool lleno, cerrar la conexión extra
                        conn.close();
                    }
                } else {
                    // Conexión inválida, cerrarla
                    conn.close();
                }
            } catch (SQLException e) {
                // Error al verificar la conexión, intentar cerrarla silenciosamente
                try {
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }
    
    /**
     * Cierra el pool y todas las conexiones
     */
    public void shutdown() {
        shutdown = true;
        
        // Cerrar todas las conexiones en el pool
        Connection conn;
        while ((conn = pool.poll()) != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error cerrando conexión del pool", e);
            }
        }
        
        plugin.getLogger().info("Connection pool cerrado");
    }
    
    /**
     * Obtiene estadísticas del pool
     */
    public PoolStats getStats() {
        return new PoolStats(maxConnections, pool.size(), maxConnections - pool.size());
    }
    
    /**
     * Clase para estadísticas del pool
     */
    public static class PoolStats {
        private final int maxConnections;
        private final int availableConnections;
        private final int activeConnections;
        
        public PoolStats(int maxConnections, int availableConnections, int activeConnections) {
            this.maxConnections = maxConnections;
            this.availableConnections = availableConnections;
            this.activeConnections = activeConnections;
        }
        
        public int getMaxConnections() { return maxConnections; }
        public int getAvailableConnections() { return availableConnections; }
        public int getActiveConnections() { return activeConnections; }
        
        @Override
        public String toString() {
            return String.format("Pool Stats - Max: %d, Available: %d, Active: %d", 
                maxConnections, availableConnections, activeConnections);
        }
    }
}