package mp.kenimon.managers;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

public class AutoUpdateManager {

    private final Kenicompetitivo plugin;
    private int updateTaskId = -1;

    public AutoUpdateManager(Kenicompetitivo plugin) {
        this.plugin = plugin;
    }

    /**
     * Inicia las tareas de actualización automática
     */
    public void startUpdateTasks() {
        stopUpdateTasks(); // Evita duplicados

        int updateInterval = plugin.getConfigManager().getConfig().getInt("auto_updates.interval", 12000); // 10 minutos por defecto

        updateTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllElements();
            }
        }.runTaskTimer(plugin, updateInterval, updateInterval).getTaskId();}

    /**
     * Detiene las tareas de actualización automática
     */
    public void stopUpdateTasks() {
        if (updateTaskId != -1) {
            Bukkit.getScheduler().cancelTask(updateTaskId);
            updateTaskId = -1;
        }
    }

    /**
     * Actualiza todos los elementos visuales y rankings
     */
    public void updateAllElements() {
        try {
            // Actualizar rankings
            plugin.getRankingManager().updateAllRankings();

            // Actualizar displays (hologramas, letreros)
            plugin.getRankingManager().updateAllDisplays();

            // Actualizar cabeza del mejor jugador
            plugin.getTopStreakHeadManager().forceUpdate();

            // Log y mensaje si está configurado
            if (plugin.getConfigManager().getConfig().getBoolean("auto_updates.broadcast", false)) {
                String message = plugin.getConfigManager().getFormattedMessage("holograms.auto_update",
                        "&7Actualización automática de hologramas completada.");
                Bukkit.broadcastMessage(message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
