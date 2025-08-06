package mp.kenimon.commands;

import mp.kenimon.Kenicompetitivo;
import mp.kenimon.cosmetics.CosmeticEffect;
import mp.kenimon.managers.ConnectionPool;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class KenicompetitivoCommand implements CommandExecutor, TabCompleter {

    private final Kenicompetitivo plugin;

    public KenicompetitivoCommand(Kenicompetitivo plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Verificar permiso base
        if (!sender.hasPermission("kenicompetitivo.admin")) {
            sender.sendMessage(plugin.getConfigManager().getFormattedMessage(
                    "commands.no_permission", "&cNo tienes permiso para usar este comando."));
            return true;
        }

        // Si no hay argumentos o es ayuda, mostrar ayuda
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                handleReloadCommand(sender);
                break;
            case "trofeos":
                handleTrofeosCommand(sender, args);
                break;
            case "racha":
                handleRachaCommand(sender, args);
                break;
            case "cosmetico":
                handleCosmeticoCommand(sender, args);
                break;
            case "debug":
                handleDebugCommand(sender, args);
                break;
            case "stats":
                handleStatsCommand(sender);
                break;
            case "hologram":
                handleHologramCommand(sender, args);
                break;
            case "addgems":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Uso: /kenicompetitivo addgems <jugador> <cantidad>");
                    return true;
                }

                Player targetPlayer = Bukkit.getPlayer(args[1]);
                if (targetPlayer == null) {
                    sender.sendMessage(plugin.getConfigManager().getFormattedMessage("commands.player_not_found", ""));
                    return true;
                }

                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.getConfigManager().getFormattedMessage("commands.invalid_number", ""));
                    return true;
                }

                int oldGems = plugin.getGemManager().getGems(targetPlayer.getUniqueId());
                plugin.getGemManager().addGems(targetPlayer.getUniqueId(), amount);
                int newGems = plugin.getGemManager().getGems(targetPlayer.getUniqueId());

                String message = plugin.getConfigManager().getFormattedMessage("commands.gems_added", "")
                        .replace("{player}", targetPlayer.getName())
                        .replace("{old}", String.valueOf(oldGems))
                        .replace("{new}", String.valueOf(newGems));

                sender.sendMessage(message);
                targetPlayer.sendMessage(plugin.getConfigManager().getFormattedMessage("commands.gems_received", "")
                        .replace("{amount}", String.valueOf(amount)));

                return true;
            default:
                showHelp(sender);
                break;
        }

        return true;
    }

    /**
     * Muestra la ayuda del comando
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Kenicompetitivo - Ayuda ===");
        sender.sendMessage(ChatColor.YELLOW + "/kenicompetitivo reload " + ChatColor.WHITE + "- Recarga la configuración");
        sender.sendMessage(ChatColor.YELLOW + "/kenicompetitivo trofeos <set|add> <jugador> <cantidad> " + ChatColor.WHITE + "- Modifica trofeos");
        sender.sendMessage(ChatColor.YELLOW + "/kenicompetitivo racha <set|add> <jugador> <cantidad> " + ChatColor.WHITE + "- Modifica rachas");
        sender.sendMessage(ChatColor.YELLOW + "/kenicompetitivo cosmetico dar <jugador> <id> " + ChatColor.WHITE + "- Da un cosmético");
        sender.sendMessage(ChatColor.YELLOW + "/kenicompetitivo debug " + ChatColor.WHITE + "- Muestra información de depuración");
        sender.sendMessage(ChatColor.YELLOW + "/kenicompetitivo hologram update " + ChatColor.WHITE + "- Actualiza los hologramas");
    }

    /**
     * Maneja el comando reload
     */
    private void handleReloadCommand(CommandSender sender) {
        plugin.getConfigManager().reloadConfigs();
        plugin.getWorldGuardUtil().loadConfiguration();

        // Actualizar componentes que dependen de la configuración
        plugin.getRankingManager().updateAllRankings();
        plugin.getRankingManager().updateAllDisplays();
        plugin.getTopStreakHeadManager().forceUpdate();

        sender.sendMessage(plugin.getConfigManager().getFormattedMessage(
                "commands.reload_success", "&aConfiguración recargada correctamente."));
    }

    /**
     * Maneja el comando trofeos
     */
    private void handleTrofeosCommand(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.getConfigManager().getFormattedMessage(
                    "commands.invalid_arguments", "&cArgumentos inválidos. Uso: /kenicompetitivo trofeos <set|add> <jugador> <cantidad>"));
            return;
        }

        String action = args[1].toLowerCase();

        if (!action.equals("set") && !action.equals("add")) {
            sender.sendMessage(plugin.getConfigManager().getFormattedMessage(
                    "commands.invalid_arguments", "&cAcción inválida. Use 'set' o 'add'."));
            return;
        }

        // Obtener jugador objetivo
        String playerName = args[2];
        Player target = Bukkit.getPlayer(playerName);
        UUID targetUUID = null;

        // Buscar jugador online o offline
        if (target != null) {
            targetUUID = target.getUniqueId();
        } else {
            // Intenta buscar el jugador offline por nombre
            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(playerName)) {
                    targetUUID = offlinePlayer.getUniqueId();
                    break;
                }
            }
        }

        if (targetUUID == null) {
            sender.sendMessage(plugin.getConfigManager().getFormattedMessage(
                    "commands.player_not_found", "&cJugador no encontrado."));
            return;
        }

        // Validar y parsear el valor numérico
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getConfigManager().getFormattedMessage(
                    "commands.invalid_number", "&cLa cantidad debe ser un número válido."));
            return;
        }

        // Obtener trofeos actuales para mostrar mensaje informativo
        int oldTrophies = plugin.getCacheManager().getCachedTrophies(targetUUID);
        int newTrophies;

        if (action.equals("set")) {
            // Establecer trofeos directamente
            if (amount < 0) {
                sender.sendMessage(ChatColor.RED + "La cantidad debe ser un número positivo.");
                return;
            }

            // Actualizar en la base de datos
            plugin.getDatabaseManager().setTrophies(targetUUID, amount);
            // También actualizar en caché para consistencia inmediata
            plugin.getCacheManager().setCachedTrophies(targetUUID, amount);
            newTrophies = amount;
        } else { // action == "add"
            // Añadir a los trofeos actuales
            newTrophies = oldTrophies + amount;
            if (newTrophies < 0) {
                newTrophies = 0; // Evitar trofeos negativos
            }

            // Actualizar en la base de datos
            plugin.getDatabaseManager().setTrophies(targetUUID, newTrophies);
            // También actualizar en caché
            plugin.getCacheManager().setCachedTrophies(targetUUID, newTrophies);
        }

        // Mensaje de confirmación
        String playerNameToShow = target != null ? target.getName() : playerName;

        sender.sendMessage(plugin.getConfigManager().getFormattedMessage(
                        "commands.trophies_added", "&aTrofeos de {player} actualizados de {old} a {new}")
                .replace("{player}", playerNameToShow)
                .replace("{old}", String.valueOf(oldTrophies))
                .replace("{new}", String.valueOf(newTrophies)));

        // Notificar al jugador si está online
        if (target != null && target.isOnline()) {
            String message = plugin.getConfigManager().getFormattedMessage(
                            "commands.trophies_received", "&aHas recibido {amount} trofeos.")
                    .replace("{amount}", String.valueOf(amount));
            target.sendMessage(message);
        }
    }

    /**
     * Maneja el comando racha
     */
    private void handleRachaCommand(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.getConfigManager().getFormattedMessage(
                    "commands.invalid_arguments", "&cArgumentos inválidos. Uso: /kenicompetitivo racha <set|add> <jugador> <cantidad>"));
            return;
        }

        String action = args[1].toLowerCase();

        if (!action.equals("set") && !action.equals("add")) {
            sender.sendMessage(plugin.getConfigManager().getFormattedMessage(
                    "commands.invalid_arguments", "&cAcción inválida. Use 'set' o 'add'."));
            return;
        }

        // Obtener jugador objetivo
        String playerName = args[2];
        Player target = Bukkit.getPlayer(playerName);
        UUID targetUUID = null;

        // Buscar jugador online o offline
        if (target != null) {
            targetUUID = target.getUniqueId();
        } else {
            // Intenta buscar el jugador offline por nombre
            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(playerName)) {
                    targetUUID = offlinePlayer.getUniqueId();
                    break;
                }
            }
        }

        if (targetUUID == null) {
            sender.sendMessage(plugin.getConfigManager().getFormattedMessage(
                    "commands.player_not_found", "&cJugador no encontrado."));
            return;
        }

        // Validar y parsear el valor numérico
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getConfigManager().getFormattedMessage(
                    "commands.invalid_number", "&cLa cantidad debe ser un número válido."));
            return;
        }

        // Mover todas las operaciones de base de datos a hilo asíncrono para evitar bloqueo del servidor
        final UUID finalTargetUUID = targetUUID;
        final Player finalTarget = target;
        final String finalPlayerName = playerName;
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int currentStreak = plugin.getCacheManager().getCachedKillStreak(finalTargetUUID);
                final int newStreak;

                if (action.equals("set")) {
                    // Establecer racha directamente
                    if (amount < 0) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(ChatColor.RED + "La cantidad debe ser un número positivo.");
                        });
                        return;
                    }
                    newStreak = amount;

                    // Actualizar en la base de datos y en caché
                    plugin.getDatabaseManager().setKillStreak(finalTargetUUID, newStreak);
                    plugin.getCacheManager().setCachedKillStreak(finalTargetUUID, newStreak);

                    // Si es mayor que la racha máxima, actualizar también la racha máxima
                    final int maxStreak = plugin.getCacheManager().getCachedMaxKillStreak(finalTargetUUID);
                    if (newStreak > maxStreak) {
                        plugin.getDatabaseManager().updateMaxKillStreak(finalTargetUUID, newStreak, success -> {
                            if (success) {
                                plugin.getCacheManager().setCachedMaxKillStreak(finalTargetUUID, newStreak);
                            }
                        });
                    }

                } else { // action == "add"
                    // Añadir a la racha actual
                    int tempStreak = currentStreak + amount;
                    if (tempStreak < 0) {
                        // Permitir valores negativos pero no menores a 0
                        tempStreak = 0;
                    }
                    newStreak = tempStreak;

                    // Actualizar en la base de datos y en caché
                    plugin.getDatabaseManager().setKillStreak(finalTargetUUID, newStreak);
                    plugin.getCacheManager().setCachedKillStreak(finalTargetUUID, newStreak);

                    // Si es mayor que la racha máxima, actualizar también la racha máxima
                    final int maxStreak = plugin.getCacheManager().getCachedMaxKillStreak(finalTargetUUID);
                    if (newStreak > maxStreak) {
                        plugin.getDatabaseManager().updateMaxKillStreak(finalTargetUUID, newStreak, success -> {
                            if (success) {
                                plugin.getCacheManager().setCachedMaxKillStreak(finalTargetUUID, newStreak);
                            }
                        });
                    }
                }

                // Actualizar información de jugador top si corresponde
                if (finalTarget != null && finalTarget.isOnline()) {
                    plugin.getTopStreakHeadManager().checkStreakUpdate(finalTargetUUID, newStreak);
                    plugin.getCosmeticManager().checkStreakUnlocks(finalTarget, newStreak);
                }

                // Volver al hilo principal para enviar mensajes
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Mensaje de confirmación
                    String playerNameToShow = finalTarget != null ? finalTarget.getName() : finalPlayerName;
                    String actionWord = amount >= 0 ? "aumentada" : "reducida";

                    sender.sendMessage(plugin.getConfigManager().getFormattedMessage(
                                    "commands.streak_set", "&aRacha de kills de {player} establecida en {amount}")
                            .replace("{player}", playerNameToShow)
                            .replace("{amount}", String.valueOf(newStreak)));

                    // Notificar al jugador si está online
                    if (finalTarget != null && finalTarget.isOnline()) {
                        String message = plugin.getConfigManager().getFormattedMessage(
                                        "commands.streak_received", "&aTu racha de kills ha {action} en {amount}")
                                .replace("{action}", actionWord)
                                .replace("{amount}", String.valueOf(Math.abs(amount)));
                        finalTarget.sendMessage(message);
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().warning("Error procesando comando racha: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.RED + "Error procesando el comando. Inténtalo de nuevo.");
                });
            }
        });

        // CRÍTICO: Mover actualización de rankings a asíncrono para no bloquear el servidor
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getRankingManager().updateRanking("killstreak", "Racha", "kill_streak", "DESC");
            
            // Programar actualización de displays en el hilo principal
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getRankingManager().updateAllDisplays();
            });
        });
    }

    /**
     * Maneja el comando cosmetico
     */
    private void handleCosmeticoCommand(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.getConfigManager().getFormattedMessage(
                    "commands.invalid_arguments", "&cArgumentos inválidos. Uso: /kenicompetitivo cosmetico dar <jugador> <id>"));
            return;
        }

        String action = args[1].toLowerCase();

        if (!action.equals("dar")) {
            sender.sendMessage(plugin.getConfigManager().getFormattedMessage(
                    "commands.invalid_arguments", "&cAcción inválida. Use 'dar'."));
            return;
        }

        // Obtener jugador objetivo
        String playerName = args[2];
        Player target = Bukkit.getPlayer(playerName);
        UUID targetUUID = null;

        // Buscar jugador online o offline
        if (target != null) {
            targetUUID = target.getUniqueId();
        } else {
            // Intenta buscar el jugador offline por nombre
            for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(playerName)) {
                    targetUUID = offlinePlayer.getUniqueId();
                    break;
                }
            }
        }

        if (targetUUID == null) {
            sender.sendMessage(plugin.getConfigManager().getFormattedMessage(
                    "commands.player_not_found", "&cJugador no encontrado."));
            return;
        }

        String cosmeticId = args[3];

        // Verificar si el cosmético existe
        if (!plugin.getCosmeticManager().effectExists(cosmeticId)) {
            sender.sendMessage(plugin.getConfigManager().getFormattedMessage(
                    "commands.cosmetic_not_found", "&cEfecto no encontrado."));
            return;
        }

        // Dar el cosmético al jugador
        plugin.getCosmeticManager().unlockEffect(targetUUID, cosmeticId);

        // Mensaje de confirmación
        String playerNameToShow = target != null ? target.getName() : playerName;
        CosmeticEffect effect = plugin.getCosmeticManager().getEffectById(cosmeticId);
        String displayName = effect != null ? effect.getName() : cosmeticId;

        sender.sendMessage(plugin.getConfigManager().getFormattedMessage(
                        "commands.cosmetic_given", "&aHas dado el cosmético &6{id}&a a &6{player}")
                .replace("{id}", displayName)
                .replace("{player}", playerNameToShow));
    }

    /**
     * Maneja el comando de depuración
     */
    private void handleDebugCommand(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "=== Kenicompetitivo Debug ===");

        // NUEVO: Información del pool de conexiones
        try {
            String poolStats = plugin.getDatabaseManager().getPoolStats();
            sender.sendMessage(ChatColor.YELLOW + "Estado del pool DB: " + ChatColor.WHITE + poolStats);
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error obteniendo stats del pool: " + e.getMessage());
        }

        // Información de la base de datos
        try {
            int playerCount = 0;
            try {
                playerCount = Bukkit.getOnlinePlayers().size();
            } catch (Exception e) {
                // Ignorar error
            }
            sender.sendMessage(ChatColor.YELLOW + "Jugadores online: " + ChatColor.WHITE + playerCount);

            // Jugador con mayor racha (hacerlo asíncrono para no bloquear)
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    UUID topPlayerUUID = plugin.getTopStreakHeadManager().getCurrentTopPlayerUUID();
                    if (topPlayerUUID != null) {
                        OfflinePlayer topPlayer = Bukkit.getOfflinePlayer(topPlayerUUID);
                        int topStreak = plugin.getDatabaseManager().getKillStreak(topPlayerUUID);
                        
                        // Enviar resultado en el hilo principal
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(ChatColor.YELLOW + "Jugador con mayor racha: " +
                                    ChatColor.WHITE + (topPlayer.getName() != null ? topPlayer.getName() : "Desconocido") +
                                    ChatColor.GRAY + " (" + topStreak + " kills)");
                        });
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage(ChatColor.YELLOW + "Jugador con mayor racha: " +
                                    ChatColor.GRAY + "Ninguno");
                        });
                    }
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.RED + "Error obteniendo top player: " + e.getMessage());
                    });
                }
            });

            // Estado de WorldGuard
            sender.sendMessage(ChatColor.YELLOW + "WorldGuard integración: " +
                    (plugin.getWorldGuardUtil().isWorldGuardEnabled() ?
                            ChatColor.GREEN + "Activa" : ChatColor.RED + "Desactivada"));

            // Total de cosméticos disponibles
            List<String> effects = plugin.getCosmeticManager().getAvailableEffects();
            sender.sendMessage(ChatColor.YELLOW + "Cosméticos disponibles: " + ChatColor.WHITE + effects.size());

            // Mostrar regiones excluidas si WorldGuard está activo
            if (plugin.getWorldGuardUtil().isWorldGuardEnabled()) {
                sender.sendMessage(ChatColor.YELLOW + "Regiones excluidas: " +
                        ChatColor.WHITE + String.join(", ", plugin.getWorldGuardUtil().getExcludedRegionIds()));
            }

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error al obtener información de depuración: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Maneja el comando hologram
     */
    private void handleHologramCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /kenicompetitivo hologram <update|info>");
            return;
        }

        String action = args[1].toLowerCase();

        if (action.equals("update")) {
            // Forzar actualización de rankings y hologramas (asíncrono)
            sender.sendMessage(ChatColor.YELLOW + "Iniciando actualización asíncrona de hologramas...");
            
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    plugin.getRankingManager().updateAllRankings();
                    
                    // Actualizar displays en el hilo principal
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getRankingManager().updateAllDisplays();
                        sender.sendMessage(ChatColor.GREEN + "Hologramas actualizados correctamente.");
                    });
                    
                    // También actualizar cabeza del jugador top
                    plugin.getTopStreakHeadManager().forceUpdate();
                    
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(ChatColor.RED + "Error actualizando hologramas: " + e.getMessage());
                    });
                }
            });
            
        } else if (action.equals("info")) {
            // Mostrar información sobre hologramas configurados
            sender.sendMessage(ChatColor.YELLOW + "=== Información de Hologramas ===");
            sender.sendMessage(ChatColor.YELLOW + "Forzando actualización de hologramas...");

            // Intentar mostrar info sobre hologramas
            try {
                plugin.getRankingManager().listHolograms((Player)sender);
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Error al mostrar información: " + e.getMessage());
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Acción desconocida. Use 'update' o 'info'");
        }
    }

    /**
     * Maneja el comando de estadísticas de rendimiento
     */
    private void handleStatsCommand(CommandSender sender) {
        if (plugin.getPerformanceMonitor() == null) {
            sender.sendMessage(ChatColor.RED + "Monitor de rendimiento no inicializado.");
            return;
        }

        String stats = plugin.getPerformanceMonitor().getStatsForCommand();
        String[] lines = stats.split("\n");
        
        for (String line : lines) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }
        
        // Información adicional del connection pool
        try {
            ConnectionPool.PoolStats poolStats = plugin.getDatabaseManager().getConnectionPool().getStats();
            sender.sendMessage(ChatColor.YELLOW + "=== Connection Pool ===");
            sender.sendMessage(ChatColor.YELLOW + "Conexiones disponibles: " + ChatColor.WHITE + poolStats.getAvailableConnections());
            sender.sendMessage(ChatColor.YELLOW + "Conexiones activas: " + ChatColor.WHITE + poolStats.getActiveConnections());
            sender.sendMessage(ChatColor.YELLOW + "Total conexiones: " + ChatColor.WHITE + poolStats.getMaxConnections());
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error obteniendo stats del pool: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Subcomandos principales
            return StringUtil.copyPartialMatches(args[0],
                    Arrays.asList("reload", "trofeos", "racha", "cosmetico", "debug", "stats", "hologram"),
                    completions);
        } else if (args.length == 2) {
            // Opciones para subcomandos
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("trofeos") || subCommand.equals("racha")) {
                return StringUtil.copyPartialMatches(args[1],
                        Arrays.asList("set", "add"),
                        completions);
            } else if (subCommand.equals("cosmetico")) {
                return StringUtil.copyPartialMatches(args[1],
                        Arrays.asList("dar"),
                        completions);
            } else if (subCommand.equals("hologram")) {
                return StringUtil.copyPartialMatches(args[1],
                        Arrays.asList("update", "info"),
                        completions);
            }
        } else if (args.length == 3) {
            // Nombres de jugadores para comandos que los requieren
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("trofeos") || subCommand.equals("racha") || subCommand.equals("cosmetico")) {
                return getOnlinePlayerNames(args[2]);
            }
        } else if (args.length == 4) {
            // Completar IDs de cosméticos para el comando cosmetico
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("cosmetico") && args[1].equalsIgnoreCase("dar")) {
                return StringUtil.copyPartialMatches(args[3],
                        plugin.getCosmeticManager().getAvailableEffects(),
                        completions);
            }
        }

        return completions;
    }

    /**
     * Obtiene una lista de nombres de jugadores online que coincidan parcialmente con el input
     */
    private List<String> getOnlinePlayerNames(String input) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}
