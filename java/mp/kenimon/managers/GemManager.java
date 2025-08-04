package mp.kenimon.managers;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class GemManager {

    private final Kenicompetitivo plugin;
    private final Map<UUID, Integer> gemsCache = new HashMap<>();
    private boolean enabled = true;
    private boolean useDatabase = true;

    public GemManager(Kenicompetitivo plugin) {
        this.plugin = plugin;

        // Comprobar si está habilitado en config
        FileConfiguration config = plugin.getConfigManager().getConfig();
        this.enabled = config.getBoolean("economies.gems.enabled", true);
        this.useDatabase = config.getBoolean("economies.gems.use_database", true);

        if (enabled) {
            setupStorage();
        }
    }

    /**
     * Configura el almacenamiento para las gemas
     */
    private void setupStorage() {
        if (useDatabase) {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String sql = "CREATE TABLE IF NOT EXISTS player_gems ("
                        + "uuid VARCHAR(36) PRIMARY KEY, "
                        + "gems INTEGER DEFAULT 0, "
                        + "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
                conn.createStatement().execute(sql);
                plugin.getLogger().info("Tabla de gemas configurada correctamente");
            } catch (SQLException e) {
                plugin.getLogger().severe("Error al configurar tabla de gemas: " + e.getMessage());
                e.printStackTrace();
                // Si falla la BD, cambiar a archivo
                useDatabase = false;
            }
        }

        // Cargar datos iniciales para jugadores online
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            loadGemsForPlayer(player.getUniqueId());
        }
    }

    /**
     * Carga las gemas de un jugador desde el almacenamiento
     * @param uuid UUID del jugador
     */
    private void loadGemsForPlayer(UUID uuid) {
        if (!enabled) return;

        if (useDatabase) {
            loadGemsFromDatabase(uuid);
        } else {
            loadGemsFromFile(uuid);
        }
    }

    /**
     * Carga las gemas de un jugador desde la base de datos
     * @param uuid UUID del jugador
     */
    private void loadGemsFromDatabase(UUID uuid) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT gems FROM player_gems WHERE uuid = ?")) {

            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int gems = rs.getInt("gems");
                gemsCache.put(uuid, gems);
            } else {
                // Si no existe, crear registro con 0 gemas
                gemsCache.put(uuid, 0);
                try (PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO player_gems (uuid, gems) VALUES (?, 0)")) {
                    insertStmt.setString(1, uuid.toString());
                    insertStmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error al cargar gemas desde base de datos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Carga las gemas de un jugador desde un archivo
     * @param uuid UUID del jugador
     */
    private void loadGemsFromFile(UUID uuid) {
        File gemsFile = new File(plugin.getDataFolder(), "gems.yml");
        YamlConfiguration gemsConfig = YamlConfiguration.loadConfiguration(gemsFile);

        int gems = gemsConfig.getInt("gems." + uuid.toString(), 0);
        gemsCache.put(uuid, gems);
    }

    /**
     * Guarda las gemas de un jugador en el almacenamiento
     * @param uuid UUID del jugador
     */
    private void saveGemsForPlayer(UUID uuid) {
        if (!enabled) return;
        if (!gemsCache.containsKey(uuid)) return;

        if (useDatabase) {
            saveGemsToDatabase(uuid);
        } else {
            saveGemsToFile(uuid);
        }
    }

    /**
     * Guarda las gemas de un jugador en la base de datos
     * @param uuid UUID del jugador
     */
    private void saveGemsToDatabase(UUID uuid) {
        int gems = gemsCache.getOrDefault(uuid, 0);

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT OR REPLACE INTO player_gems (uuid, gems, last_updated) VALUES (?, ?, CURRENT_TIMESTAMP)")) {

            stmt.setString(1, uuid.toString());
            stmt.setInt(2, gems);
            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("Error al guardar gemas en base de datos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Guarda las gemas de un jugador en un archivo
     * @param uuid UUID del jugador
     */
    private void saveGemsToFile(UUID uuid) {
        File gemsFile = new File(plugin.getDataFolder(), "gems.yml");
        YamlConfiguration gemsConfig = YamlConfiguration.loadConfiguration(gemsFile);

        gemsConfig.set("gems." + uuid.toString(), gemsCache.getOrDefault(uuid, 0));

        try {
            gemsConfig.save(gemsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error al guardar gemas en archivo: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Obtiene las gemas de un jugador
     * @param uuid UUID del jugador
     * @return Cantidad de gemas
     */
    public int getGems(UUID uuid) {
        if (!enabled) return 0;

        // Si no está en caché, cargar
        if (!gemsCache.containsKey(uuid)) {
            loadGemsForPlayer(uuid);
        }

        return gemsCache.getOrDefault(uuid, 0);
    }

    /**
     * Establece una cantidad específica de gemas para un jugador
     * @param uuid UUID del jugador
     * @param amount Cantidad de gemas
     */
    public void setGems(UUID uuid, int amount) {
        if (!enabled) return;

        // Valor mínimo de gemas es 0
        amount = Math.max(0, amount);

        // Actualizar caché
        gemsCache.put(uuid, amount);

        // Guardar cambio
        saveGemsForPlayer(uuid);
    }

    /**
     * Añade gemas a un jugador
     * @param uuid UUID del jugador
     * @param amount Cantidad a añadir
     * @return Nueva cantidad de gemas
     */
    public int addGems(UUID uuid, int amount) {
        if (!enabled || amount <= 0) return getGems(uuid);

        int currentGems = getGems(uuid);
        int newAmount = currentGems + amount;

        setGems(uuid, newAmount);
        return newAmount;
    }

    /**
     * Quita gemas a un jugador
     * @param uuid UUID del jugador
     * @param amount Cantidad a quitar
     * @return true si se pudo quitar la cantidad completa
     */
    public boolean removeGems(UUID uuid, int amount) {
        if (!enabled || amount <= 0) return false;

        int currentGems = getGems(uuid);

        // Verificar si tiene suficientes gemas
        if (currentGems < amount) {
            return false;
        }

        // Actualizar cantidad
        setGems(uuid, currentGems - amount);
        return true;
    }

    /**
     * Verifica si un jugador tiene al menos cierta cantidad de gemas
     * @param uuid UUID del jugador
     * @param amount Cantidad mínima
     * @return true si tiene suficientes gemas
     */
    public boolean hasGems(UUID uuid, int amount) {
        if (!enabled) return false;
        return getGems(uuid) >= amount;
    }

    /**
     * Guarda todas las gemas en caché al servidor
     */
    public void saveAll() {
        if (!enabled) return;

        for (UUID uuid : gemsCache.keySet()) {
            saveGemsForPlayer(uuid);
        }
    }

    /**
     * Limpia la caché de gemas de jugadores offline
     */
    public void cleanupCache() {
        if (!enabled) return;

        // Copia para evitar ConcurrentModificationException
        Set<UUID> uuidsToCheck = new HashSet<>(gemsCache.keySet());

        for (UUID uuid : uuidsToCheck) {
            OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(uuid);
            if (!offlinePlayer.isOnline()) {
                saveGemsForPlayer(uuid);
                gemsCache.remove(uuid);
            }
        }
    }

    /**
     * Indica si el sistema de gemas está habilitado
     * @return true si está habilitado
     */
    public boolean isEnabled() {
        return enabled;
    }
}
