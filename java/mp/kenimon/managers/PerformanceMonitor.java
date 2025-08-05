package mp.kenimon.managers;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.Bukkit;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Monitor de rendimiento para rastrear mejoras y diagnosticar problemas
 */
public class PerformanceMonitor {
    
    private final Kenicompetitivo plugin;
    
    // Contadores de operaciones
    private final AtomicLong totalDbQueries = new AtomicLong(0);
    private final AtomicLong batchOperationsProcessed = new AtomicLong(0);
    private final AtomicLong asyncOperations = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    
    // Timing metrics
    private final AtomicLong totalDbTime = new AtomicLong(0);
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    
    // Contadores de eventos del juego
    private final AtomicLong killsProcessed = new AtomicLong(0);
    private final AtomicLong trophiesUpdated = new AtomicLong(0);
    
    private long startTime;
    
    public PerformanceMonitor(Kenicompetitivo plugin) {
        this.plugin = plugin;
        this.startTime = System.currentTimeMillis();
        
        // Programar reporte periódico de estadísticas
        if (plugin.getConfig().getBoolean("performance.monitoring.enabled", false)) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::reportStats, 6000L, 6000L); // Cada 5 minutos
        }
    }
    
    // Métodos para registrar métricas
    
    public void recordDbQuery(long timeMs) {
        totalDbQueries.incrementAndGet();
        totalDbTime.addAndGet(timeMs);
    }
    
    public void recordBatchOperation(int batchSize) {
        batchOperationsProcessed.addAndGet(batchSize);
    }
    
    public void recordAsyncOperation() {
        asyncOperations.incrementAndGet();
    }
    
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }
    
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }
    
    public void recordKillProcessed() {
        killsProcessed.incrementAndGet();
    }
    
    public void recordTrophyUpdate() {
        trophiesUpdated.incrementAndGet();
    }
    
    public void setActiveConnections(int count) {
        activeConnections.set(count);
    }
    
    /**
     * Genera reporte de estadísticas de rendimiento
     */
    public void reportStats() {
        long uptime = System.currentTimeMillis() - startTime;
        long uptimeMinutes = uptime / 60000;
        
        if (uptimeMinutes == 0) return; // Evitar división por 0
        
        StringBuilder report = new StringBuilder();
        report.append("=== ESTADÍSTICAS DE RENDIMIENTO KENICOMPETITIVO ===\n");
        report.append("Tiempo activo: ").append(uptimeMinutes).append(" minutos\n");
        report.append("\n--- BASE DE DATOS ---\n");
        report.append("Consultas SQL ejecutadas: ").append(totalDbQueries.get()).append("\n");
        report.append("Promedio consultas/min: ").append(totalDbQueries.get() / uptimeMinutes).append("\n");
        report.append("Tiempo promedio por consulta: ").append(getAverageDbTime()).append("ms\n");
        report.append("Operaciones batch procesadas: ").append(batchOperationsProcessed.get()).append("\n");
        report.append("Operaciones asíncronas: ").append(asyncOperations.get()).append("\n");
        report.append("Conexiones activas: ").append(activeConnections.get()).append("\n");
        
        report.append("\n--- CACHE ---\n");
        long totalCacheOperations = cacheHits.get() + cacheMisses.get();
        double hitRate = totalCacheOperations > 0 ? (cacheHits.get() * 100.0 / totalCacheOperations) : 0;
        report.append("Cache hits: ").append(cacheHits.get()).append("\n");
        report.append("Cache misses: ").append(cacheMisses.get()).append("\n");
        report.append("Hit rate: ").append(String.format("%.1f%%", hitRate)).append("\n");
        
        report.append("\n--- EVENTOS DE JUEGO ---\n");
        report.append("Kills procesados: ").append(killsProcessed.get()).append("\n");
        report.append("Actualizaciones de trofeos: ").append(trophiesUpdated.get()).append("\n");
        report.append("Promedio kills/min: ").append(killsProcessed.get() / uptimeMinutes).append("\n");
        
        // Análisis de eficiencia
        report.append("\n--- ANÁLISIS DE EFICIENCIA ---\n");
        if (batchOperationsProcessed.get() > 0) {
            double reductionRatio = (double) batchOperationsProcessed.get() / totalDbQueries.get();
            report.append("Ratio de batch operations: ").append(String.format("%.2f", reductionRatio)).append("\n");
            report.append("Queries evitadas por batching: ~").append((long)(batchOperationsProcessed.get() * 0.8)).append("\n");
        }
        
        if (hitRate > 90) {
            report.append("✅ Cache funcionando óptimamente\n");
        } else if (hitRate > 70) {
            report.append("⚠️ Cache podría mejorarse\n");
        } else {
            report.append("❌ Cache necesita optimización\n");
        }
        
        if (getAverageDbTime() < 5) {
            report.append("✅ Tiempo de consultas SQL óptimo\n");
        } else if (getAverageDbTime() < 15) {
            report.append("⚠️ Tiempo de consultas SQL aceptable\n");
        } else {
            report.append("❌ Consultas SQL lentas - revisar índices\n");
        }
        
        // Log del reporte
        String[] lines = report.toString().split("\n");
        for (String line : lines) {
            plugin.getLogger().info(line);
        }
    }
    
    private double getAverageDbTime() {
        long queries = totalDbQueries.get();
        return queries > 0 ? (double) totalDbTime.get() / queries : 0;
    }
    
    /**
     * Obtiene estadísticas en formato para comando /kenicompetitivo stats
     */
    public String getStatsForCommand() {
        long uptime = System.currentTimeMillis() - startTime;
        long uptimeMinutes = uptime / 60000;
        
        StringBuilder stats = new StringBuilder();
        stats.append("&7=== &bEstadísticas de Rendimiento &7===\n");
        stats.append("&7Tiempo activo: &a").append(uptimeMinutes).append(" min\n");
        stats.append("&7Consultas SQL: &e").append(totalDbQueries.get()).append("\n");
        stats.append("&7Batch operations: &e").append(batchOperationsProcessed.get()).append("\n");
        stats.append("&7Cache hit rate: &a").append(String.format("%.1f%%", getCacheHitRate())).append("\n");
        stats.append("&7Kills procesados: &e").append(killsProcessed.get()).append("\n");
        stats.append("&7Conexiones activas: &e").append(activeConnections.get()).append("\n");
        
        return stats.toString();
    }
    
    private double getCacheHitRate() {
        long total = cacheHits.get() + cacheMisses.get();
        return total > 0 ? (cacheHits.get() * 100.0 / total) : 0;
    }
    
    /**
     * Resetea todas las estadísticas
     */
    public void resetStats() {
        totalDbQueries.set(0);
        batchOperationsProcessed.set(0);
        asyncOperations.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        totalDbTime.set(0);
        killsProcessed.set(0);
        trophiesUpdated.set(0);
        startTime = System.currentTimeMillis();
        
        plugin.getLogger().info("Estadísticas de rendimiento reseteadas");
    }
}