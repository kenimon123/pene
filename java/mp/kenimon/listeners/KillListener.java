package mp.kenimon.listeners;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class KillListener implements Listener {

    private final Kenicompetitivo plugin;

    public KillListener(Kenicompetitivo plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Registrar kill procesado para métricas
        if (plugin.getPerformanceMonitor() != null) {
            plugin.getPerformanceMonitor().recordKillProcessed();
        }

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Si no hay killer o es el mismo jugador, reset de racha pero sin penalización
        if (killer == null || killer.equals(victim)) {
            resetStreak(victim, false);
            return;
        }

        // Verificar si están en región excluida
        if (plugin.getWorldGuardUtil() != null && plugin.getWorldGuardUtil().isWorldGuardEnabled()) {
            if (plugin.getWorldGuardUtil().isInExcludedRegion(victim) ||
                    plugin.getWorldGuardUtil().isInExcludedRegion(killer)) {
                String message = plugin.getConfigManager().getFormattedMessage("excluded_regions.message");
                killer.sendMessage(message);
                return;
            }
        }

        // Verificar si el killer es top player (para recompensa especial)
        UUID topPlayerUUID = plugin.getTopStreakHeadManager().getCurrentTopPlayerUUID();
        boolean isKillingTopPlayer = topPlayerUUID != null && topPlayerUUID.equals(victim.getUniqueId());

        // Guardar racha antigua para posible recompensa al matar al top
        int oldVictimStreak = plugin.getCacheManager().getCachedKillStreak(victim.getUniqueId());

        // Resetear la racha de la víctima y aplicar penalización
        resetStreak(victim, true);

        // Procesar racha del asesino
        int currentStreak = plugin.getCacheManager().getCachedKillStreak(killer.getUniqueId());
        int newStreak = currentStreak + 1;

        // Actualizar la racha en caché y base de datos usando batch operation
        plugin.getCacheManager().updateKillStreak(killer.getUniqueId(), newStreak);

        // Crear copias finales para usar en lambdas
        final Player finalKiller = killer;
        final int finalNewStreak = newStreak;

        // Usar callback asíncrono para UI updates - no bloquea el hilo principal
        CompletableFuture.runAsync(() -> {
            // Notificar al jugador sobre la racha
            if (finalNewStreak == 1) {
                String startMessage = plugin.getConfigManager().getFormattedMessage("killstreak.start");
                finalKiller.sendMessage(startMessage);
            } else {
                String updateMessage = plugin.getConfigManager().getFormattedMessage("killstreak.update");
                updateMessage = updateMessage.replace("{streak}", String.valueOf(finalNewStreak));
                finalKiller.sendMessage(updateMessage);
            }

            // Si alcanzó un milestone de racha (configurable)
            int milestoneInterval = plugin.getConfigManager().getConfig().getInt("milestone_interval", 5);
            if (milestoneInterval > 0 && finalNewStreak % milestoneInterval == 0) {
                String milestoneMessage = plugin.getConfigManager().getFormattedMessage("killstreak.milestone");
                final String finalMilestoneMessage = milestoneMessage
                        .replace("{player}", finalKiller.getName())
                        .replace("{streak}", String.valueOf(finalNewStreak));

                // Broadcast del milestone (ejecutar en main thread)
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.broadcastMessage(finalMilestoneMessage);
                });
            }

            // Otorgar trofeos por el asesinato
            grantTrophies(finalKiller, finalNewStreak);

            // Verificar desbloqueos de cosméticos
            plugin.getCosmeticManager().checkStreakUnlocks(finalKiller, finalNewStreak);

            // Actualizar lista de jugadores top streak si es necesario
            plugin.getTopStreakHeadManager().checkStreakUpdate(finalKiller.getUniqueId(), finalNewStreak);
        });

        // CORREGIDO: Ejecutar efectos de kill y sonidos inmediatamente (no asincrónicamente)
        // para garantizar que se muestren al momento de la muerte
        plugin.getCosmeticManager().handleKillEvent(killer, victim);

        // Si la víctima era el jugador con mejor racha, dar recompensa
        if (isKillingTopPlayer && oldVictimStreak > 0) {
            plugin.getTopStreakHeadManager().processTopPlayerKill(victim, killer, oldVictimStreak);
        }

        // NUEVO: Procesar recompensas por rachas
        processStreakRewards(killer, newStreak);
    }

    /**
     * Procesa las recompensas por rachas de kills
     * @param player El jugador a quien dar las recompensas
     * @param streak La racha actual del jugador
     */
    private void processStreakRewards(Player player, int streak) {
        ConfigurationSection rewardsSection = plugin.getConfigManager().getConfig().getConfigurationSection("streak_rewards.ranges");
        if (rewardsSection == null || !plugin.getConfigManager().getConfig().getBoolean("streak_rewards.enabled", true)) {
            return;
        }

        for (String rangeKey : rewardsSection.getKeys(false)) {
            String[] rangeParts = rangeKey.split("-");
            if (rangeParts.length != 2) continue;

            try {
                int minStreak = Integer.parseInt(rangeParts[0]);
                int maxStreak = Integer.parseInt(rangeParts[1]);

                // Verificar si la racha está en el rango
                if (streak >= minStreak && streak <= maxStreak) {
                    // Verificar si esta racha exacta ya fue recompensada
                    final String rewardId = "streak_" + streak;
                    if (!plugin.getRewardManager().hasClaimedReward(player.getUniqueId(), rewardId)) {
                        // Ejecutar comandos de recompensa
                        List<String> commands = rewardsSection.getStringList(rangeKey);
                        for (String cmd : commands) {
                            final String processedCmd = cmd.replace("{player}", player.getName());
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCmd);
                        }

                        // Marcar como reclamada
                        plugin.getRewardManager().claimReward(player.getUniqueId(), rewardId);

                        // Mostrar mensaje de recompensa
                        final String message = plugin.getConfigManager().getFormattedMessage(
                                        "streak_rewards.received",
                                        "&a¡Has recibido una recompensa por alcanzar una racha de &6{streak}&a kills!")
                                .replace("{streak}", String.valueOf(streak));

                        player.sendMessage(message);

                        // Efecto de sonido
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    }
                    break; // Una vez procesado un rango, salimos
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Formato de rango incorrecto en streak_rewards: " + rangeKey);
            }
        }
    }

    /**
     * Resetea la racha de kills de un jugador (OPTIMIZADO)
     * @param player El jugador
     * @param withPenalty Si debe aplicarse penalización de trofeos
     */
    private void resetStreak(Player player, boolean withPenalty) {
        int oldStreak = plugin.getCacheManager().getCachedKillStreak(player.getUniqueId());

        // Si el jugador no tenía racha, no hacer nada
        if (oldStreak <= 0) return;

        // Actualizar la racha a 0 usando cache manager optimizado
        plugin.getCacheManager().updateKillStreak(player.getUniqueId(), 0);

        // Crear una copia final del jugador para usar en lambdas
        final Player finalPlayer = player;
        final boolean finalWithPenalty = withPenalty;

        // Procesar penalización y notificaciones asíncronamente
        CompletableFuture.runAsync(() -> {
            // Aplicar penalización si corresponde
            if (finalWithPenalty) {
                applyStreakLossPenalty(finalPlayer);
            }

            // Notificar pérdida de racha (en main thread)
            Bukkit.getScheduler().runTask(plugin, () -> {
                String lostMessage = plugin.getConfigManager().getFormattedMessage("killstreak.lost");
                finalPlayer.sendMessage(lostMessage);
            });
        });
    }

    /**
     * Aplica penalización por perder racha de kills
     * @param player El jugador
     */
    private void applyStreakLossPenalty(Player player) {
        int penalty = plugin.getConfigManager().getConfig().getInt("streak_loss_penalty", 50);
        final String currency = plugin.getConfigManager().getConfig().getString("currency_name", "trofeos");

        // Si no hay penalización configurada, no hacer nada
        if (penalty <= 0) return;

        // Obtener trofeos actuales
        int currentTrophies = plugin.getCacheManager().getCachedTrophies(player.getUniqueId());

        // No aplicar penalización si no tiene suficientes trofeos
        if (currentTrophies < penalty) {
            penalty = currentTrophies;
        }

        // Si no hay trofeos para quitar, no hacer nada
        if (penalty <= 0) return;

        // Crear copia final de la penalidad para usar en posibles lambdas
        final int finalPenalty = penalty;

        // Aplicar la penalización
        int newTrophies = currentTrophies - finalPenalty;
        plugin.getCacheManager().setCachedTrophies(player.getUniqueId(), newTrophies);
        plugin.getDatabaseManager().setTrophies(player.getUniqueId(), newTrophies);

        // Notificar al jugador
        final String message = plugin.getConfigManager().getFormattedMessage("killstreak.max")
                .replace("%penalty%", String.valueOf(finalPenalty))
                .replace("%currency%", currency);

        player.sendMessage(message);
    }

    /**
     * Otorga trofeos por un asesinato (OPTIMIZADO)
     * @param player El jugador
     * @param streak La racha actual del jugador
     */
    private void grantTrophies(Player player, int streak) {
        // Trofeos base por kill
        int baseTrophies = plugin.getConfigManager().getConfig().getInt("base_trophies", 10);

        // Bonus por racha
        int bonus = 0;
        int maxBonus = plugin.getConfigManager().getConfig().getInt("max_trophy_bonus", 5);

        // Si hay bonus específico para esta racha en la config, usarlo
        final String streakStr = String.valueOf(streak);
        if (plugin.getConfigManager().getConfig().contains("trophy_increase_per_kill." + streakStr)) {
            bonus = plugin.getConfigManager().getConfig().getInt("trophy_increase_per_kill." + streakStr);
        } else {
            // Si no, calcular proporcional al nivel de racha, con límite
            bonus = Math.min(streak - 1, maxBonus);
        }

        // Total de trofeos a otorgar
        int totalTrophies = baseTrophies + bonus;

        // Usar cache manager que maneja batch operations automáticamente
        plugin.getCacheManager().updateTrophies(player.getUniqueId(), totalTrophies);
    }
}
