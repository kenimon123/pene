package mp.kenimon.managers;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class TopStreakHeadManager {
    private final Kenicompetitivo plugin;
    private final String HOLOGRAM_ID = "top_streak_head";

    private Location headLocation;
    private UUID currentTopPlayer;
    private int currentTopStreak;
    private BukkitTask updateTask;

    // Cache para el segundo mejor jugador
    private UUID secondTopPlayer;
    private int secondTopStreak;

    public TopStreakHeadManager(Kenicompetitivo plugin) {
        this.plugin = plugin;
        this.loadHeadLocation();
        this.startUpdateTask();
    }

    /**
     * Carga la ubicación de la cabeza desde la configuración
     */
    private void loadHeadLocation() {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        String worldName = config.getString("head_display.world");
        double x = config.getDouble("head_display.x");
        double y = config.getDouble("head_display.y");
        double z = config.getDouble("head_display.z");

        if (worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                this.headLocation = new Location(world, x, y, z);
                plugin.getLogger().info("Cabeza de mayor racha configurada en: " + worldName + ", " + x + ", " + y + ", " + z);
            } else {
                plugin.getLogger().warning("Mundo no encontrado para la cabeza de racha: " + worldName);
            }
        }
    }

    /**
     * Inicia la tarea de actualización periódica
     */
    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        // Cambiar a 30 segundos (600 ticks)
        int interval = 5 * 60 * 20; // 5 minutos en ticks

        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Solo actualizar si hay jugadores online
                if (!Bukkit.getOnlinePlayers().isEmpty()) {
                    updateTopStreakHead();
                }
            }
        }.runTaskTimer(plugin, 20L, interval);
    }

    /**
     * Forzar actualización inmediata de la cabeza
     */
    public void forceUpdate() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        // IMPORTANTE: Primero, actualizar internamente quién es el top player
        Map<UUID, Integer> topStreaks = plugin.getDatabaseManager().getAllKillStreaks();

        // Ordenar por valor de racha y actualizar los jugadores top
        List<Map.Entry<UUID, Integer>> sortedStreaks = new ArrayList<>(topStreaks.entrySet());
        sortedStreaks.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());

        UUID oldTopPlayer = currentTopPlayer;
        int oldTopStreak = currentTopStreak;

        // Actualizar el jugador top
        if (!sortedStreaks.isEmpty()) {
            Map.Entry<UUID, Integer> topEntry = sortedStreaks.get(0);
            currentTopPlayer = topEntry.getKey();
            currentTopStreak = topEntry.getValue();

            OfflinePlayer topPlayerObj = Bukkit.getOfflinePlayer(currentTopPlayer);
            String topPlayerName = topPlayerObj.getName() != null ? topPlayerObj.getName() : "Desconocido";
        } else {
            currentTopPlayer = null;
            currentTopStreak = 0;
        }

        // Actualizar el segundo mejor
        secondTopPlayer = null;
        secondTopStreak = 0;

        if (sortedStreaks.size() > 1) {
            Map.Entry<UUID, Integer> secondEntry = sortedStreaks.get(1);
            secondTopPlayer = secondEntry.getKey();
            secondTopStreak = secondEntry.getValue();

            OfflinePlayer secondPlayerObj = Bukkit.getOfflinePlayer(secondTopPlayer);
            String secondPlayerName = secondPlayerObj.getName() != null ? secondPlayerObj.getName() : "Desconocido";
        }

        // CRÍTICO: Siempre intenta actualizar la cabeza, incluso si el jugador top tiene racha 1
        if (currentTopPlayer != null) {
            // Ejecutar en el hilo principal para evitar errores
            Bukkit.getScheduler().runTask(plugin, () -> {
                updateTopStreakHead();
            });
        } else {
        }

        // Reiniciar la tarea programada
        startUpdateTask();

        // Forzar actualización de rankings
        plugin.getRankingManager().updateAllRankings();
        plugin.getRankingManager().updateAllDisplays();
    }

    private long lastPhysicalUpdate = 0;

    /**
     * Actualiza la cabeza del jugador con mayor racha
     */
    public void updateTopStreakHead() {
        if (headLocation == null) {
            plugin.getLogger().warning("No hay ubicación configurada para la cabeza de racha");
            return;
        }

        // ARREGLADO: Hacer la obtención de datos de BD asíncrona para no bloquear el hilo principal
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Guardar valores actuales antes de actualizar
            UUID oldTopPlayer = currentTopPlayer;
            int oldTopStreak = currentTopStreak;

            // Obtener datos de BD de forma asíncrona
            forceGetTopPlayer();

            // Volver al hilo principal para el resto del procesamiento
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Si no hay jugador top, no hacer nada
                if (currentTopPlayer == null) {
                    return;
                }

                // Verificar si realmente necesitamos actualizar físicamente la cabeza
                // Solo actualizar si: cambió el jugador, cambió la racha, o pasaron 5 minutos desde última actualización
                boolean needsUpdate = (oldTopPlayer == null || !currentTopPlayer.equals(oldTopPlayer) ||
                        currentTopStreak != oldTopStreak ||
                        System.currentTimeMillis() - lastPhysicalUpdate > 300000);

                if (!needsUpdate) {
                    return; // No es necesario actualizar físicamente
                }

                // Verificar que el mundo de la ubicación esté cargado
                if (headLocation.getWorld() == null) {
                    plugin.getLogger().warning("El mundo de la cabeza no está cargado");
                    return;
                }

                // Continuar con la actualización física en el hilo principal
                try {
                    // Actualizar físicamente la cabeza
                    updatePhysicalHead();
                    lastPhysicalUpdate = System.currentTimeMillis();
                } catch (Exception e) {
                    plugin.getLogger().severe("ERROR al actualizar la cabeza: " + e.getMessage());
                }
            });
        });
    }

    /**
     * Realiza la actualización física de la cabeza y el holograma
     */
    private void updatePhysicalHead() {
        // Verificar que el bloque es una cabeza o convertirlo
        Block block = headLocation.getBlock();
        boolean needsUpdate = block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD;

        if (needsUpdate) {
            block.setType(Material.PLAYER_HEAD);
        }

        // Establecer dirección de la cabeza según la configuración
        if (block.getBlockData() instanceof org.bukkit.block.data.Rotatable) {
            org.bukkit.block.data.Rotatable rotatable = (org.bukkit.block.data.Rotatable) block.getBlockData();

            // Obtener dirección configurada
            String directionStr = plugin.getConfigManager().getConfig()
                    .getString("head_display.direction", "SOUTH");
            BlockFace direction;

            try {
                direction = BlockFace.valueOf(directionStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                direction = BlockFace.SOUTH; // Valor por defecto
            }

            rotatable.setRotation(direction);
            block.setBlockData(rotatable);
        }

        // Establecer el dueño de la cabeza
        if (block.getState() instanceof Skull) {
            Skull skull = (Skull) block.getState();

            try {
                // Intentar obtener el jugador online o offline
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(currentTopPlayer);
                
                // Usar el método correcto de la API para evitar warnings de reflexión
                if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
                    skull.setOwningPlayer(offlinePlayer);
                } else {
                    // Si el jugador nunca ha jugado, buscar por nombre si está disponible
                    String playerName = offlinePlayer.getName();
                    if (playerName != null && !playerName.isEmpty()) {
                        // Usar método deprecado pero más seguro para jugadores que nunca se conectaron
                        skull.setOwner(playerName);
                    } else {
                        plugin.getLogger().warning("No se puede establecer dueño de skull: jugador no encontrado");
                        return;
                    }
                }

                // Actualizar el estado del bloque de forma segura
                boolean updated = skull.update(true, false);
                if (!updated) {
                    plugin.getLogger().warning("No se pudo actualizar el estado de la skull");
                }

                // Crear/actualizar holograma
                String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Desconocido";
                updateHologram(playerName, currentTopStreak);
                
            } catch (Exception e) {
                plugin.getLogger().warning("Error al establecer dueño de skull: " + e.getMessage());
                // Intentar método alternativo como fallback
                try {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(currentTopPlayer);
                    String playerName = offlinePlayer.getName();
                    if (playerName != null) {
                        skull.setOwner(playerName);
                        skull.update(true, false);
                        updateHologram(playerName, currentTopStreak);
                    }
                } catch (Exception fallbackEx) {
                    plugin.getLogger().severe("Error crítico al establecer skull: " + fallbackEx.getMessage());
                }
            }
        } else {
            plugin.getLogger().warning("El bloque en la ubicación configurada no es una calavera");
        }
    }

    /**
     * NUEVA FUNCIÓN: Fuerza la obtención del jugador top directo desde la base de datos
     * Versión optimizada que solo obtiene los mejores jugadores
     */
    private void forceGetTopPlayer() {
        // En lugar de obtener todos, obtener solo los top 5
        Map<UUID, Integer> topPlayers = plugin.getDatabaseManager().getTopKillStreaks(3);

        // Ordenar por valor de racha en orden descendente
        List<Map.Entry<UUID, Integer>> sortedPlayers = new ArrayList<>(topPlayers.entrySet());
        sortedPlayers.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());

        if (!sortedPlayers.isEmpty()) {
            Map.Entry<UUID, Integer> topEntry = sortedPlayers.get(0);
            currentTopPlayer = topEntry.getKey();
            currentTopStreak = topEntry.getValue();

            OfflinePlayer player = Bukkit.getOfflinePlayer(currentTopPlayer);
            String name = player.getName() != null ? player.getName() : "Desconocido";
        } else {
            currentTopPlayer = null;
            currentTopStreak = 0;
        }

        if (sortedPlayers.size() > 1) {
            Map.Entry<UUID, Integer> secondEntry = sortedPlayers.get(1);
            secondTopPlayer = secondEntry.getKey();
            secondTopStreak = secondEntry.getValue();
        } else {
            secondTopPlayer = null;
            secondTopStreak = 0;
        }
    }

    /**
     * Obtiene los dos jugadores con mayor racha
     */
    private Map<UUID, Integer> getTopTwoPlayers() {
        // CORRECCIÓN: Cambiar 'allStreaks' a 'topStreaks'
        Map<UUID, Integer> topStreaks = plugin.getDatabaseManager().getAllKillStreaks();

        // Ordenar por valor de racha en orden descendente (de mayor a menor)
        return topStreaks.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(2) // Obtener solo los 2 mejores
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1, // En caso de duplicados (no debería ocurrir)
                        LinkedHashMap::new // Mantener el orden
                ));
    }

    /**
     * Coloca físicamente la cabeza en la ubicación configurada
     */
    private void placeHead(UUID playerUUID, int streak) {
        // Verificar que la ubicación es válida
        if (headLocation == null || headLocation.getWorld() == null) {
            plugin.getLogger().warning("Ubicación de cabeza no válida. No se puede colocar la cabeza.");
            return;
        }

        // Hacer que el bloque sea una cabeza de jugador si no lo es ya
        Block block = headLocation.getBlock();
        if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) {
            block.setType(Material.PLAYER_HEAD);
            plugin.getLogger().info("Bloque convertido a PLAYER_HEAD");
        }

        // Establecer dirección de la cabeza según la configuración
        if (block.getBlockData() instanceof org.bukkit.block.data.Rotatable) {
            org.bukkit.block.data.Rotatable rotatable = (org.bukkit.block.data.Rotatable) block.getBlockData();

            // Obtener dirección configurada
            String directionStr = plugin.getConfigManager().getConfig()
                    .getString("head_display.direction", "SOUTH");
            BlockFace direction;

            try {
                direction = BlockFace.valueOf(directionStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                direction = BlockFace.SOUTH; // Valor por defecto
            }

            rotatable.setRotation(direction);
            block.setBlockData(rotatable);
            plugin.getLogger().info("Rotación de cabeza establecida a: " + direction);
        }

        // Establecer el dueño de la cabeza
        if (block.getState() instanceof Skull) {
            Skull skull = (Skull) block.getState();

            try {
                // Intentar obtener el jugador online o offline
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
                
                // Usar el método correcto de la API para evitar warnings de reflexión
                if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
                    skull.setOwningPlayer(offlinePlayer);
                } else {
                    // Si el jugador nunca ha jugado, buscar por nombre si está disponible
                    String playerName = offlinePlayer.getName();
                    if (playerName != null && !playerName.isEmpty()) {
                        // Usar método deprecado pero más seguro para jugadores que nunca se conectaron
                        skull.setOwner(playerName);
                    } else {
                        plugin.getLogger().warning("No se puede establecer dueño de skull: jugador no encontrado");
                        return;
                    }
                }

                // Actualizar el estado del bloque de forma segura
                boolean updated = skull.update(true, false);
                if (!updated) {
                    plugin.getLogger().warning("No se pudo actualizar el estado de la skull");
                }

                // Verificación de depuración para la skin
                plugin.getLogger().info("Cabeza actualizada para jugador: " +
                        (offlinePlayer.getName() != null ? offlinePlayer.getName() : playerUUID));

                // Anunciar la nueva cabeza en el servidor, solo si cambió de jugador
                String playerName = offlinePlayer.getName();
                if (playerName != null) {
                    String message = plugin.getConfigManager().getFormattedMessage(
                            "head_display.updated",
                            "&6¡{player} ahora tiene la racha más alta con {streak} kills consecutivas!");

                    message = message.replace("{player}", playerName)
                            .replace("{streak}", String.valueOf(streak));

                    Bukkit.broadcastMessage(message);
                }

                // Crear/actualizar holograma
                plugin.getLogger().info("Llamando a updateHologram con: " +
                        (playerName != null ? playerName : "Desconocido") + ", " + streak);
                updateHologram(playerName != null ? playerName : "Desconocido", streak);
                
            } catch (Exception e) {
                plugin.getLogger().warning("Error al establecer dueño de skull: " + e.getMessage());
                // Intentar método alternativo como fallback
                try {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
                    String playerName = offlinePlayer.getName();
                    if (playerName != null) {
                        skull.setOwner(playerName);
                        skull.update(true, false);
                        updateHologram(playerName, streak);
                    }
                } catch (Exception fallbackEx) {
                    plugin.getLogger().severe("Error crítico al establecer skull: " + fallbackEx.getMessage());
                }
            }
        } else {
            plugin.getLogger().warning("El bloque en la ubicación configurada no es una calavera");
        }
    }

    /**
     * Actualiza el holograma sobre la cabeza
     */
    private void updateHologram(String playerName, int streak) {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        if (!config.getBoolean("head_display.hologram.enabled", true)) {
            // Si está deshabilitado, eliminar el holograma existente
            if (plugin.getServer().getPluginManager().isPluginEnabled("DecentHolograms")) {
                try {
                    if (eu.decentsoftware.holograms.api.DHAPI.getHologram(HOLOGRAM_ID) != null) {
                        eu.decentsoftware.holograms.api.DHAPI.removeHologram(HOLOGRAM_ID);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error al eliminar holograma: " + e.getMessage());
                }
            }
            return;
        }

        if (!plugin.getServer().getPluginManager().isPluginEnabled("DecentHolograms")) {
            plugin.getLogger().warning("DecentHolograms no está instalado. No se pueden crear hologramas.");
            return;
        }

        try {
            // Calcular valor de la cabeza basado en la racha
            int headValue = calculateHeadValue(streak);

            // Crear holograma con offset configurable
            double offsetX = config.getDouble("head_display.hologram.offset_x", 0.5);
            double offsetY = config.getDouble("head_display.hologram.offset_y", 3.0);
            double offsetZ = config.getDouble("head_display.hologram.offset_z", 0.5);

            // Asegurarse de que la ubicación es válida
            if (headLocation == null || headLocation.getWorld() == null) {
                plugin.getLogger().warning("Ubicación de la cabeza no válida para crear holograma");
                return;
            }

            Location holoLoc = headLocation.clone().add(offsetX, offsetY, offsetZ);

            List<String> configLines = config.getStringList("head_display.hologram.lines");
            List<String> lines = new ArrayList<>();

            for (String line : configLines) {
                // Aplicar placeholders
                line = line.replace("{player}", playerName)
                        .replace("{streak}", String.valueOf(streak))
                        .replace("{value}", String.valueOf(headValue));

                lines.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            // Si está vacío, usar líneas por defecto
            if (lines.isEmpty()) {
                lines.add(ChatColor.translateAlternateColorCodes('&', "&c&l✮ &6&lTop Racha &c&l✮"));
                lines.add(ChatColor.translateAlternateColorCodes('&', "&e" + playerName));
                lines.add(ChatColor.translateAlternateColorCodes('&', "&b" + streak + " kills"));
                lines.add(ChatColor.translateAlternateColorCodes('&', "&6Valor: &a$" + headValue));
            }

            // Si existe, eliminar el anterior
            try {
                if (eu.decentsoftware.holograms.api.DHAPI.getHologram(HOLOGRAM_ID) != null) {
                    eu.decentsoftware.holograms.api.DHAPI.removeHologram(HOLOGRAM_ID);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error al eliminar holograma anterior: " + e.getMessage());
            }

            // Crear nuevo holograma
            try {

                eu.decentsoftware.holograms.api.DHAPI.createHologram(HOLOGRAM_ID, holoLoc, lines);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error general al crear holograma: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Calcula el valor de recompensa por la cabeza basado en la racha
     * MODIFICADO: Ahora calcula 1000 * racha
     */
    public int calculateHeadValue(int streak) {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        // Calcular el valor básico: 1000 * racha
        int baseValue = streak * 1000;

        // Si hay una configuración específica para esta racha, usar ese valor en su lugar
        String streakStr = String.valueOf(streak);
        if (config.contains("head_values." + streakStr)) {
            return config.getInt("head_values." + streakStr);
        }

        // Si no hay una configuración específica, devolver el valor calculado
        return baseValue;
    }

    /**
     * Verifica si un jugador tiene la mayor racha para otorgar recompensa al matarlo
     * NOTA: Este método está obsoleto y solo se mantiene por compatibilidad.
     * Ahora se usa processTopPlayerKill que es llamado directamente desde KillListener
     */
    public void checkHeadKill(Player victim, Player killer) {
        if (victim == null || killer == null) return;

        plugin.getLogger().info("DEPRECATED: El método checkHeadKill está obsoleto. Usar processTopPlayerKill");

        // Método obsoleto - ahora la lógica está en processTopPlayerKill
        // que es llamado directamente desde KillListener después de verificar
        // si la víctima es el jugador top
    }

    /**
     * Método para actualizar cuando un jugador supera la racha actual
     * Este método debe llamarse desde KillListener cuando se actualiza una racha
     */
    public void checkStreakUpdate(UUID playerUUID, int newStreak) {

        // IMPORTANTE: Incluir rachas de 1 kill
        if (newStreak > 0) {
            // Si no hay un jugador top actual o el nuevo supera al actual
            if (currentTopPlayer == null || newStreak > currentTopStreak) {

                // Si es un nuevo jugador que supera al actual
                if (!playerUUID.equals(currentTopPlayer)) {
                    // El anterior top pasa a ser segundo si existe
                    if (currentTopPlayer != null) {
                        secondTopPlayer = currentTopPlayer;
                        secondTopStreak = currentTopStreak;
                    }

                    // Actualizar el nuevo top
                    currentTopPlayer = playerUUID;
                    currentTopStreak = newStreak;
                } else {
                    // Es el mismo jugador mejorando su racha
                    currentTopStreak = newStreak;
                }

                // IMPORTANTE: Actualizar inmediatamente la cabeza y hologramas
                Bukkit.getScheduler().runTask(plugin, () -> {
                    updateTopStreakHead();
                });

            }
            // Si es el segundo y supera su propia racha
            else if (playerUUID.equals(secondTopPlayer) && newStreak > secondTopStreak) {
                secondTopStreak = newStreak;
            }
            // Si no es top 1 ni top 2, pero su racha supera al top 2
            else if (secondTopPlayer == null || newStreak > secondTopStreak) {
                secondTopPlayer = playerUUID;
                secondTopStreak = newStreak;
            }

            // CRÍTICO: Mover operaciones de DB a asíncrono para no bloquear el hilo principal
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getRankingManager().updateRanking("killstreak", "Racha", "kill_streak", "DESC");
                // updateHolograms ya se llama automáticamente desde updateRanking
            });
        }
    }

    public Location getHeadLocation() {
        return headLocation;
    }

    public void setHeadLocation(Location location) {
        this.headLocation = location;

        // Guardar en la configuración
        FileConfiguration config = plugin.getConfigManager().getConfig();
        config.set("head_display.world", location.getWorld().getName());
        config.set("head_display.x", location.getX());
        config.set("head_display.y", location.getY());
        config.set("head_display.z", location.getZ());

        plugin.getConfigManager().saveConfig();

        // Actualizar inmediatamente
        forceUpdate();
        // Actualizar todos los rankings
        plugin.getRankingManager().updateAllRankings();
        plugin.getRankingManager().updateAllDisplays();
    }

    /**
     * Establece la ubicación de la cabeza en la posición actual del jugador
     *
     * @param player El jugador cuya ubicación se usará
     */
    public void setHeadLocation(Player player) {
        // Usar la ubicación del bloque que el jugador está mirando
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock != null) {
            setHeadLocation(targetBlock.getLocation());
            player.sendMessage(ChatColor.GREEN + "Ubicación de la cabeza establecida en el bloque que estás mirando.");
        } else {
            // Si no está mirando un bloque, usar la ubicación del jugador
            setHeadLocation(player.getLocation().getBlock().getLocation());
            player.sendMessage(ChatColor.YELLOW + "Ubicación de la cabeza establecida en tu posición actual.");
        }
    }

    /**
     * Muestra información sobre la ubicación actual de la cabeza al jugador
     *
     * @param player El jugador a quien mostrar la información
     */
    public void showHeadLocation(Player player) {
        if (headLocation != null) {
            String world = headLocation.getWorld().getName();
            int x = headLocation.getBlockX();
            int y = headLocation.getBlockY();
            int z = headLocation.getBlockZ();

            player.sendMessage(ChatColor.GOLD + "=== Ubicación de la Cabeza Top Racha ===");
            player.sendMessage(ChatColor.YELLOW + "Mundo: " + ChatColor.WHITE + world);
            player.sendMessage(ChatColor.YELLOW + "X: " + ChatColor.WHITE + x);
            player.sendMessage(ChatColor.YELLOW + "Y: " + ChatColor.WHITE + y);
            player.sendMessage(ChatColor.YELLOW + "Z: " + ChatColor.WHITE + z);

            // Verificar si la cabeza está colocada en la ubicación
            Block block = headLocation.getBlock();
            if (block.getType() == Material.PLAYER_HEAD || block.getType() == Material.PLAYER_WALL_HEAD) {
                player.sendMessage(ChatColor.GREEN + "✓ La cabeza está correctamente colocada.");
            } else {
                player.sendMessage(ChatColor.RED + "✗ No hay una cabeza en esta ubicación (tipo: " + block.getType() + ")");
            }

            // Mostrar información del jugador actual con mayor racha
            if (currentTopPlayer != null) {
                OfflinePlayer topPlayer = Bukkit.getOfflinePlayer(currentTopPlayer);
                player.sendMessage(ChatColor.YELLOW + "Jugador actual: " +
                        (topPlayer.getName() != null ? ChatColor.WHITE + topPlayer.getName() : ChatColor.GRAY + "Desconocido"));
                player.sendMessage(ChatColor.YELLOW + "Racha: " + ChatColor.WHITE + currentTopStreak);
                player.sendMessage(ChatColor.YELLOW + "Valor de la recompensa: " + ChatColor.WHITE + calculateHeadValue(currentTopStreak));
            } else {
                player.sendMessage(ChatColor.RED + "No hay un jugador con racha máxima registrado.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "La ubicación de la cabeza no está configurada.");
            player.sendMessage(ChatColor.YELLOW + "Usa '/head set' para establecer la ubicación.");
        }
    }

    /**
     * Obtiene el UUID del jugador actual con la racha más alta
     * (método auxiliar para KillListener)
     */
    public UUID getCurrentTopPlayerUUID() {
        return this.currentTopPlayer;
    }

    /**
     * Procesa la muerte del jugador con la mejor racha y da recompensa al asesino
     * Esta función SOLO debe llamarse cuando la víctima es el jugador con mejor racha
     */
    public void processTopPlayerKill(Player victim, Player killer, int oldStreak) {
        if (victim == null || killer == null) return;

        plugin.getLogger().info("DEBUG: Procesando muerte del jugador top: " + victim.getName() +
                ", Racha: " + oldStreak + ", Asesino: " + killer.getName());

        // Verificación adicional para evitar recompensas incorrectas
        // Si la racha es 0, no dar recompensa (ya mató antes a este jugador)
        if (oldStreak <= 0) {
            plugin.getLogger().info("DEBUG: No se da recompensa porque la racha es 0 o negativa");
            return;
        }

        // Calcular valor de la recompensa basado en la racha anterior
        int headValue = calculateHeadValue(oldStreak);

        // Obtener mensajes personalizados
        String killerMessage = plugin.getConfigManager().getFormattedMessage(
                "head_display.reward",
                "{prefix}&a¡Has matado a {player} que tenía la racha más alta ({streak} kills)! Recompensa: &6${value}"
        );

        String broadcastMessage = plugin.getConfigManager().getFormattedMessage(
                "head_display.killed",
                "{prefix}&e¡{killer} ha eliminado a {victim} y reclamado una recompensa de &6${value}&e por su cabeza!"
        );

        // Reemplazar placeholders
        killerMessage = killerMessage
                .replace("{value}", String.valueOf(headValue))
                .replace("{player}", victim.getName())
                .replace("{streak}", String.valueOf(oldStreak));

        broadcastMessage = broadcastMessage
                .replace("{killer}", killer.getName())
                .replace("{victim}", victim.getName())
                .replace("{value}", String.valueOf(headValue))
                .replace("{streak}", String.valueOf(oldStreak));

        // Dar recompensa al asesino
        boolean rewardGiven = false;

        // Intentar con Vault primero
        if (plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
            try {
                plugin.getLogger().info("DEBUG: Intentando dar recompensa con Vault: $" + headValue);

                org.bukkit.plugin.RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp =
                        plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);

                if (rsp != null) {
                    net.milkbowl.vault.economy.Economy economy = rsp.getProvider();

                    if (economy != null) {
                        net.milkbowl.vault.economy.EconomyResponse response = economy.depositPlayer(killer, headValue);
                        rewardGiven = response.transactionSuccess();

                        plugin.getLogger().info("DEBUG: Resultado Vault: " + rewardGiven +
                                ", Mensaje: " + (response.errorMessage != null ? response.errorMessage : "OK"));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("ERROR con Vault: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Si Vault falló, usar comando alternativo
        if (!rewardGiven) {
            plugin.getLogger().info("DEBUG: Intentando dar recompensa con comando alternativo");
            String rewardCommand = plugin.getConfigManager().getConfig()
                    .getString("head_display.reward_command", "eco give {player} {value}");

            if (rewardCommand != null && !rewardCommand.isEmpty()) {
                rewardCommand = rewardCommand
                        .replace("{player}", killer.getName())
                        .replace("{value}", String.valueOf(headValue));

                try {
                    plugin.getLogger().info("DEBUG: Ejecutando comando: " + rewardCommand);
                    boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rewardCommand);
                    rewardGiven = success;

                    plugin.getLogger().info("DEBUG: Resultado comando: " + success);
                } catch (Exception e) {
                    plugin.getLogger().warning("ERROR al ejecutar comando: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        // Notificar al asesino y al servidor
        if (rewardGiven) {
            killer.sendMessage(killerMessage);
            Bukkit.broadcastMessage(broadcastMessage);

            // Efectos visuales y sonoros
            killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.6f);
            killer.playSound(killer.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.2f);
            killer.spawnParticle(Particle.TOTEM, killer.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
        } else {
            plugin.getLogger().severe("No se pudo otorgar la recompensa a " + killer.getName());
            killer.sendMessage(ChatColor.RED + "Error al otorgar la recompensa. Contacte a un administrador.");
        }

        // IMPORTANTE: Actualizar listas internas inmediatamente
        // Establecer que la víctima ya no es el top player (esto es crítico)
        if (currentTopPlayer != null && currentTopPlayer.equals(victim.getUniqueId())) {
            // Guardar referencia al antiguo top
            UUID oldTopPlayer = currentTopPlayer;

            // Asignar al segundo como primero si existe
            if (secondTopPlayer != null) {
                currentTopPlayer = secondTopPlayer;
                currentTopStreak = secondTopStreak;

                // El segundo ahora es desconocido hasta la próxima actualización
                secondTopPlayer = null;
                secondTopStreak = 0;
            } else {
                // No hay segundo, entonces no hay top player
                currentTopPlayer = null;
                currentTopStreak = 0;
            }

            // Forzar actualización completa para buscar el nuevo segundo
            forceUpdate();
        }
    }
}
