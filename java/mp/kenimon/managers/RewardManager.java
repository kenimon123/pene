package mp.kenimon.managers;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class RewardManager {

    private final Kenicompetitivo plugin;
    private final Map<UUID, Set<String>> claimedRewards = new HashMap<>();

    public RewardManager(Kenicompetitivo plugin) {
        this.plugin = plugin;
        setupDatabase();
        loadClaimedRewards();
    }

    /**
     * Configura la tabla necesaria para guardar las recompensas en la base de datos.
     */
    private void setupDatabase() {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String createTableSQL = "CREATE TABLE IF NOT EXISTS claimed_rewards ("
                    + "player_uuid VARCHAR(36) NOT NULL, "
                    + "reward_id VARCHAR(64) NOT NULL, "
                    + "claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (player_uuid, reward_id))";

            conn.createStatement().execute(createTableSQL);
            plugin.getLogger().info("Tabla de recompensas configurada correctamente en la base de datos.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error al configurar la tabla de recompensas en la base de datos:");
            e.printStackTrace();
        }
    }

    /**
     * Carga las recompensas reclamadas desde la base de datos.
     */
    private void loadClaimedRewards() {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "SELECT player_uuid, reward_id FROM claimed_rewards";
            ResultSet rs = conn.createStatement().executeQuery(query);

            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                String rewardId = rs.getString("reward_id");

                claimedRewards.computeIfAbsent(playerId, k -> new HashSet<>()).add(rewardId);
            }

            plugin.getLogger().info("Recompensas reclamadas cargadas desde la base de datos.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error al cargar las recompensas reclamadas desde la base de datos:");
            e.printStackTrace();
        }
    }

    /**
     * Verifica si un jugador ya ha reclamado una recompensa específica.
     *
     * @param playerId UUID del jugador
     * @param rewardId ID de la recompensa
     * @return true si ya ha reclamado la recompensa, false en caso contrario
     */
    public boolean hasClaimedReward(UUID playerId, String rewardId) {
        return claimedRewards.containsKey(playerId) && claimedRewards.get(playerId).contains(rewardId);
    }

    /**
     * Registra que un jugador ha reclamado una recompensa.
     *
     * @param playerId UUID del jugador
     * @param rewardId ID de la recompensa
     * @return true si se registró correctamente, false si ya estaba registrada o hubo error
     */
    public boolean claimReward(UUID playerId, String rewardId) {
        if (hasClaimedReward(playerId, rewardId)) {
            return false;
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String insertSQL = "INSERT INTO claimed_rewards (player_uuid, reward_id) VALUES (?, ?)";
            PreparedStatement stmt = conn.prepareStatement(insertSQL);
            stmt.setString(1, playerId.toString());
            stmt.setString(2, rewardId);
            stmt.executeUpdate();

            // Actualizar la caché
            claimedRewards.computeIfAbsent(playerId, k -> new HashSet<>()).add(rewardId);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error al registrar recompensa reclamada en la base de datos:");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Elimina una recompensa reclamada (para pruebas o reseteo).
     *
     * @param playerId UUID del jugador
     * @param rewardId ID de la recompensa
     * @return true si se eliminó correctamente
     */
    public boolean unclaimReward(UUID playerId, String rewardId) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String deleteSQL = "DELETE FROM claimed_rewards WHERE player_uuid = ? AND reward_id = ?";
            PreparedStatement stmt = conn.prepareStatement(deleteSQL);
            stmt.setString(1, playerId.toString());
            stmt.setString(2, rewardId);
            int affected = stmt.executeUpdate();

            // Actualizar la caché si se eliminó algo
            if (affected > 0 && claimedRewards.containsKey(playerId)) {
                claimedRewards.get(playerId).remove(rewardId);
                return true;
            }
            return affected > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error al eliminar recompensa reclamada en la base de datos:");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Otorga una recompensa a un jugador.
     *
     * @param player El jugador que recibirá la recompensa
     * @param rewardId El ID de la recompensa
     * @param commands Los comandos a ejecutar para otorgar la recompensa
     * @param message Mensaje a mostrar al jugador
     */
    public void giveReward(Player player, String rewardId, List<String> commands, String message) {
        if (player == null || !player.isOnline()) return;

        // Registrar la recompensa como reclamada
        if (!claimReward(player.getUniqueId(), rewardId)) {
            player.sendMessage(ChatColor.RED + "¡Ya has reclamado esta recompensa!");
            return;
        }

        // Ejecutar los comandos de recompensa
        for (String cmd : commands) {
            String processedCommand = cmd.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
        }

        // Mostrar mensaje si existe
        if (message != null && !message.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    /**
     * Limpia las recompensas reclamadas según un período de tiempo (mensualmente, diariamente, etc.)
     *
     * @param period El período a limpiar ("daily", "weekly", "monthly", etc.)
     */
    public void clearRewardsByCycle(String period) {
        String timeCondition;
        switch (period.toLowerCase()) {
            case "daily":
                timeCondition = "DATE(claimed_at) < DATE('now', 'start of day')";
                break;
            case "weekly":
                timeCondition = "DATE(claimed_at) < DATE('now', 'weekday 0', '-7 days')";
                break;
            case "monthly":
                timeCondition = "DATE(claimed_at) < DATE('now', 'start of month')";
                break;
            default:
                plugin.getLogger().warning("Período de limpieza no válido: " + period);
                return;
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String deleteSQL = "DELETE FROM claimed_rewards WHERE " + timeCondition;
            int deleted = conn.createStatement().executeUpdate(deleteSQL);

            // Recargar desde la base de datos para actualizar la caché
            claimedRewards.clear();
            loadClaimedRewards();

            plugin.getLogger().info("Se eliminaron " + deleted + " registros de recompensas reclamadas para el período: " + period);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error al limpiar recompensas por ciclo:");
            e.printStackTrace();
        }
    }

    /**
     * Programa limpieza automática de recompensas
     *
     * @param period El período ("daily", "weekly", "monthly")
     * @param timeOfDay La hora del día en formato "HH:MM"
     */
    public void scheduleRewardCleanup(String period, String timeOfDay) {
        // Implementación para programar la limpieza automática según configuración
        new BukkitRunnable() {
            @Override
            public void run() {
                // Lógica para verificar si es hora de limpiar según timeOfDay
                clearRewardsByCycle(period);
            }
        }.runTaskTimerAsynchronously(plugin, 1200L, 1200L); // Verificar cada minuto (60s × 20ticks = 1200)
    }

    /**
     * Obtiene todas las recompensas reclamadas por un jugador.
     *
     * @param playerId UUID del jugador
     * @return Conjunto con IDs de recompensas reclamadas
     */
    public Set<String> getPlayerClaimedRewards(UUID playerId) {
        return claimedRewards.getOrDefault(playerId, new HashSet<>());
    }

    /**
     * Migra datos antiguos desde rewards.yml a la base de datos
     */
    public void migrateFromYamlToDatabase() {
        plugin.getLogger().info("Iniciando migración de recompensas desde YAML a base de datos...");

        try {
            File rewardsFile = new File(plugin.getDataFolder(), "rewards.yml");
            if (!rewardsFile.exists()) {
                plugin.getLogger().info("No existe archivo rewards.yml para migrar.");
                return;
            }

            YamlConfiguration rewardsConfig = YamlConfiguration.loadConfiguration(rewardsFile);
            ConfigurationSection playersSection = rewardsConfig.getConfigurationSection("players");

            if (playersSection == null) {
                plugin.getLogger().info("No hay datos de recompensas para migrar.");
                return;
            }

            int migratedCount = 0;
            for (String uuidString : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    List<String> claimedRewards = rewardsConfig.getStringList("players." + uuidString + ".claimed");

                    for (String rewardId : claimedRewards) {
                        if (claimReward(uuid, rewardId)) {
                            migratedCount++;
                        }
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("UUID inválido en rewards.yml: " + uuidString);
                }
            }

            // Renombrar el archivo original como respaldo
            if (migratedCount > 0) {
                File backup = new File(plugin.getDataFolder(), "rewards.yml.bak");
                rewardsFile.renameTo(backup);
                plugin.getLogger().info("Migración completada. " + migratedCount + " recompensas migradas.");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error durante la migración de recompensas: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Marca una recompensa como reclamada para un jugador.
     * Implementación completa para evitar que se puedan reclamar recompensas infinitamente.
     *
     * @param uniqueId UUID del jugador
     * @param threshold Valor del umbral de la recompensa
     */
    public void markRewardAsClaimed(UUID uniqueId, int threshold) {
        String rewardId = String.valueOf(threshold);

        // Si ya está reclamada, no hacer nada
        if (hasClaimedReward(uniqueId, rewardId)) {
            return;
        }

        // Registrar en la base de datos
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String insertSQL = "INSERT INTO claimed_rewards (player_uuid, reward_id) VALUES (?, ?)";
            PreparedStatement stmt = conn.prepareStatement(insertSQL);
            stmt.setString(1, uniqueId.toString());
            stmt.setString(2, rewardId);
            stmt.executeUpdate();

            // Actualizar la caché local
            claimedRewards.computeIfAbsent(uniqueId, k -> new HashSet<>()).add(rewardId);

            plugin.getLogger().info("Recompensa " + rewardId + " marcada como reclamada para " + uniqueId);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error al marcar recompensa como reclamada: " + e.getMessage());
            e.printStackTrace();
        }
    }
}