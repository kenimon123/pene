package mp.kenimon.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import mp.kenimon.Kenicompetitivo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.sql.DriverManager.getConnection;

public class CacheManager implements Listener {

    private Kenicompetitivo plugin;

    // Mapas para cachear datos frecuentemente accedidos
    private Map<UUID, Integer> trophiesCache = new ConcurrentHashMap<>();
    private Map<UUID, Integer> killStreakCache = new ConcurrentHashMap<>();
    private Map<UUID, Integer> maxKillStreakCache = new ConcurrentHashMap<>();

    // Registro de cambios pendientes para guardar en DB
    private Map<UUID, Integer> pendingTrophiesUpdates = new ConcurrentHashMap<>();
    private Map<UUID, Integer> pendingKillStreakUpdates = new ConcurrentHashMap<>();

    public CacheManager(Kenicompetitivo plugin) {
        this.plugin = plugin;

        // Programar tarea de guardado periódico (cada 5 minutos)
        new BukkitRunnable() {
            @Override
            public void run() {
                saveAllPendingChanges();
            }
        }.runTaskTimerAsynchronously(plugin, 12000L, 12000L); // 6000 ticks = 5 minutos
    }

    // Métodos para gestionar la caché

    public int getCachedTrophies(UUID uuid) {
        if (trophiesCache.containsKey(uuid)) {
            // Registrar cache hit
            if (plugin.getPerformanceMonitor() != null) {
                plugin.getPerformanceMonitor().recordCacheHit();
            }
            return trophiesCache.get(uuid);
        }
        // Registrar cache miss
        if (plugin.getPerformanceMonitor() != null) {
            plugin.getPerformanceMonitor().recordCacheMiss();
        }
        // Si no está en caché, obtener de DB y almacenar en caché
        int trophies = plugin.getDatabaseManager().getTrophies(uuid);
        trophiesCache.put(uuid, trophies);
        return trophies;
    }

    public int getCachedKillStreak(UUID uuid) {
        if (killStreakCache.containsKey(uuid)) {
            return killStreakCache.get(uuid);
        }
        // Si no está en caché, obtener de DB
        int streak = plugin.getDatabaseManager().getKillStreak(uuid);
        killStreakCache.put(uuid, streak);
        return streak;
    }

    public int getCachedMaxKillStreak(UUID uuid) {
        if (maxKillStreakCache.containsKey(uuid)) {
            return maxKillStreakCache.get(uuid);
        }
        // Si no está en caché, obtener de DB
        int maxStreak = plugin.getDatabaseManager().getMaxKillStreak(uuid);
        maxKillStreakCache.put(uuid, maxStreak);
        return maxStreak;
    }

    // Actualizar valores en caché y marcar para actualización

    /**
     * Actualiza trofeos usando batch operations (OPTIMIZADO)
     */
    public void updateTrophies(UUID uuid, int amount) {
        // Registrar métricas
        if (plugin.getPerformanceMonitor() != null) {
            plugin.getPerformanceMonitor().recordTrophyUpdate();
        }
        
        // Actualizar en caché
        int currentTrophies = getCachedTrophies(uuid);
        int newTrophies = currentTrophies + amount;

        // Limitar al máximo configurado
        int maxTrophies = plugin.getConfigManager().getConfig().getInt("max_total_trophies", 10000);
        newTrophies = Math.min(newTrophies, maxTrophies);

        // Actualizar caché inmediatamente
        trophiesCache.put(uuid, newTrophies);

        // Usar batch operation para DB (más eficiente)
        plugin.getDatabaseManager().updateTrophiesBatch(uuid, newTrophies);
    }

    /**
     * Actualiza la racha de kills de un jugador en la caché
     * OPTIMIZADO: Usa batch operations para reducir consultas SQL
     */
    public void updateKillStreak(UUID uuid, int streak) {
        // Actualizar caché inmediatamente
        killStreakCache.put(uuid, streak);
        
        // Si es una racha nueva récord para el jugador, actualizar también la máxima
        int currentMax = getCachedMaxKillStreak(uuid);
        if (streak > currentMax) {
            maxKillStreakCache.put(uuid, streak);
        }

        // CRÍTICO: Si la racha es 0 (murió), usar operación síncrona para actualización inmediata
        if (streak == 0) {
            try {
                // Para rachas que se resetean, actualizar inmediatamente en DB
                plugin.getDatabaseManager().setKillStreak(uuid, streak);
            } catch (Exception e) {
                plugin.getLogger().severe("ERROR al guardar racha 0: " + e.getMessage());
            }
        } else {
            // Para rachas > 0, usar batch operation (más eficiente)
            plugin.getDatabaseManager().updateKillStreakBatch(uuid, streak, 
                streak > currentMax ? streak : -1);
        }
    }

    // Métodos para guardar cambios en DB

    private void savePlayerTrophies(UUID uuid) {
        if (pendingTrophiesUpdates.containsKey(uuid)) {
            int trophies = pendingTrophiesUpdates.get(uuid);
            plugin.getDatabaseManager().setTrophies(uuid, trophies);
            pendingTrophiesUpdates.remove(uuid);
        }
    }

    /**
     * Guarda la racha de kills de un jugador en la base de datos
     */
    private void savePlayerKillStreak(UUID uuid) {
        Integer streak = pendingKillStreakUpdates.remove(uuid);
        if (streak != null) {
            // Verificar si vale la pena actualizar
            Integer oldStreak = killStreakCache.get(uuid);
            if (oldStreak == null || !oldStreak.equals(streak)) {
                // Solo actualizar en la BD si hay cambios
                killStreakCache.put(uuid, streak);

                // Actualizar la base de datos asíncrona
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        // Si es una racha pequeña (1-3), actualizar con baja prioridad
                        if (streak <= 3) {
                            // Agregar a lote para procesar en bulk
                            plugin.getDatabaseManager().addKillStreakToBatch(uuid, streak);
                        } else {
                            // Para rachas importantes, actualizar inmediatamente
                            plugin.getDatabaseManager().setKillStreak(uuid, streak);
                        }

                        // Verificar si es una nueva racha máxima
                        Integer maxStreak = maxKillStreakCache.get(uuid);
                        if (maxStreak == null || streak > maxStreak) {
                            maxKillStreakCache.put(uuid, streak);

                            // Actualizar racha máxima en BD
                            plugin.getDatabaseManager().updateMaxKillStreak(uuid, streak, success -> {
                                // No hacer nada en el callback
                            });

                            // Verificar desbloqueos
                            Player player = Bukkit.getPlayer(uuid);
                            if (player != null && player.isOnline()) {
                                plugin.getCosmeticManager().checkStreakUnlocks(player, streak);
                            }
                        }

                        // Comprobar si es el jugador con mayor racha
                        plugin.getTopStreakHeadManager().checkStreakUpdate(uuid, streak);
                    } catch (Exception e) {
                        // Excepciones silenciadas para evitar spam en consola
                    }
                });
            }
        }
    }

    // Añadir estas variables al inicio de la clase CacheManager
    private Map<UUID, Map<String, String>> playerEffectsCache = new ConcurrentHashMap<>();
    private Map<UUID, Long> lastAccess = new ConcurrentHashMap<>();

    // Método saveAllPendingChanges corregido
    public void saveAllPendingChanges() {
        try {
            // Copia las claves para evitar ConcurrentModificationException
            for (UUID uuid : new HashMap<>(pendingTrophiesUpdates).keySet()) {
                savePlayerTrophies(uuid);
                // Actualizar tiempo de último acceso
                lastAccess.put(uuid, System.currentTimeMillis());
            }

            for (UUID uuid : new HashMap<>(pendingKillStreakUpdates).keySet()) {
                savePlayerKillStreak(uuid);
                // Actualizar tiempo de último acceso
                lastAccess.put(uuid, System.currentTimeMillis());
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error al guardar cambios pendientes: " + e.getMessage());
        }
    }

    // Método cleanupCache corregido
    public void cleanupCache() {
        // Eliminar de la caché jugadores offline por más de 10 minutos
        long cutoffTime = System.currentTimeMillis() - 600000; // 10 minutos

        List<UUID> toRemove = new ArrayList<>();
        for (UUID uuid : trophiesCache.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                // Verificar cuándo fue la última vez que se accedió
                if (!lastAccess.containsKey(uuid) || lastAccess.get(uuid) < cutoffTime) {
                    toRemove.add(uuid);
                }
            }
        }

        // Eliminar de todas las cachés
        for (UUID uuid : toRemove) {
            trophiesCache.remove(uuid);
            killStreakCache.remove(uuid);
            maxKillStreakCache.remove(uuid);
            if (playerEffectsCache.containsKey(uuid)) {
                playerEffectsCache.remove(uuid);
            }
            lastAccess.remove(uuid);
        }
    }

    // Método para saber si un jugador tiene efectos de partículas
    public boolean hasParticleEffectSelected(UUID uuid) {
        if (playerEffectsCache.containsKey(uuid)) {
            Map<String, String> effects = playerEffectsCache.get(uuid);
            return effects.containsKey("particle") && effects.get("particle") != null;
        }

        // Si no está en caché, consultar base de datos
        String effect = plugin.getDatabaseManager().getSelectedCosmetic(uuid, "particle");

        // Actualizar caché
        if (effect != null) {
            if (!playerEffectsCache.containsKey(uuid)) {
                playerEffectsCache.put(uuid, new HashMap<>());
            }
            playerEffectsCache.get(uuid).put("particle", effect);
            return true;
        }

        return false;
    }

    // Gestionar eventos de jugador

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Precargar datos del jugador
        plugin.getDatabaseManager().getKillStreakAsync(uuid, streak -> {
            killStreakCache.put(uuid, streak);
        });

        plugin.getDatabaseManager().getTrophiesAsync(uuid, trophies -> {
            trophiesCache.put(uuid, trophies);
        });

        plugin.getDatabaseManager().getMaxKillStreakAsync(uuid, maxStreak -> {
            maxKillStreakCache.put(uuid, maxStreak);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        // Guardar datos pendientes
        savePlayerTrophies(uuid);
        savePlayerKillStreak(uuid);

        // Limpiar caché
        trophiesCache.remove(uuid);
        killStreakCache.remove(uuid);
        maxKillStreakCache.remove(uuid);
    }

    // Método para limpiar toda la caché
    public void clearCache() {
        saveAllPendingChanges();
        trophiesCache.clear();
        killStreakCache.clear();
        maxKillStreakCache.clear();
    }
    /**
     * Establece directamente el valor de trofeos en caché para un jugador
     */
    public void setCachedTrophies(UUID playerUUID, int trophies) {
        trophiesCache.put(playerUUID, trophies);
    }

    /**
     * Establece directamente el valor de racha de kills en caché para un jugador
     */
    public void setCachedKillStreak(UUID playerUUID, int streak) {
        killStreakCache.put(playerUUID, streak);
    }

    /**
     * Establece directamente el valor de racha máxima en caché para un jugador
     */
    public void setCachedMaxKillStreak(UUID playerUUID, int maxStreak) {
        maxKillStreakCache.put(playerUUID, maxStreak);
    }
}
