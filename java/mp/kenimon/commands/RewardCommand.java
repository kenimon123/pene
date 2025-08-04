package mp.kenimon.commands;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;

public class RewardCommand implements CommandExecutor, TabCompleter {

    private final Kenicompetitivo plugin;

    public RewardCommand(Kenicompetitivo plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Uso: /" + label + " <subcomando> [argumentos]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reset":
                if (!sender.hasPermission("kenicompetitivo.admin.rewards")) {
                    sender.sendMessage(plugin.getConfigManager().getFormattedMessage("commands.no_permission", ""));
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Uso: /" + label + " reset <jugador> <rewardId>");
                    return true;
                }

                handleResetReward(sender, args[1], args[2]);
                return true;

            case "clear":
                if (!sender.hasPermission("kenicompetitivo.admin.rewards")) {
                    sender.sendMessage(plugin.getConfigManager().getFormattedMessage("commands.no_permission", ""));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Uso: /" + label + " clear <daily|weekly|monthly>");
                    return true;
                }

                handleClearRewards(sender, args[1]);
                return true;

            case "list":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
                    return true;
                }

                if (!sender.hasPermission("kenicompetitivo.rewards.list")) {
                    sender.sendMessage(plugin.getConfigManager().getFormattedMessage("commands.no_permission", ""));
                    return true;
                }

                handleListRewards(sender);
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa /reward help para ver la ayuda.");
                return true;
        }
    }

    private void handleResetReward(CommandSender sender, String playerName, String rewardId) {
        Player target = plugin.getServer().getPlayer(playerName);
        UUID playerId;

        if (target != null) {
            playerId = target.getUniqueId();
        } else {
            // Si el jugador no está conectado, buscar en la caché o base de datos
            // Esta implementación es simplificada, podría requerir más lógica
            sender.sendMessage(ChatColor.RED + "El jugador no está conectado. No se puede resetear la recompensa.");
            return;
        }

        boolean success = plugin.getRewardManager().unclaimReward(playerId, rewardId);
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "Recompensa " + rewardId + " reseteada para " + playerName);
        } else {
            sender.sendMessage(ChatColor.RED + "Error al resetear la recompensa o el jugador no la había reclamado.");
        }
    }

    private void handleClearRewards(CommandSender sender, String period) {
        if (!Arrays.asList("daily", "weekly", "monthly").contains(period.toLowerCase())) {
            sender.sendMessage(ChatColor.RED + "Período no válido. Use daily, weekly o monthly.");
            return;
        }

        plugin.getRewardManager().clearRewardsByCycle(period.toLowerCase());
        sender.sendMessage(ChatColor.GREEN + "Se han limpiado las recompensas del período: " + period);
    }

    private void handleListRewards(CommandSender sender) {
        Player player = (Player) sender;
        UUID playerId = player.getUniqueId();

        Set<String> rewards = plugin.getRewardManager().getPlayerClaimedRewards(playerId);

        if (rewards.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No has reclamado ninguna recompensa todavía.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Recompensas reclamadas ===");
        for (String rewardId : rewards) {
            sender.sendMessage(ChatColor.YELLOW + "- " + rewardId);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            subcommands.add("list");

            if (sender.hasPermission("kenicompetitivo.admin.rewards")) {
                subcommands.add("reset");
                subcommands.add("clear");
            }

            StringUtil.copyPartialMatches(args[0], subcommands, completions);
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("clear") && sender.hasPermission("kenicompetitivo.admin.rewards")) {
                List<String> periods = Arrays.asList("daily", "weekly", "monthly");
                StringUtil.copyPartialMatches(args[1], periods, completions);
            } else if (args[0].equalsIgnoreCase("reset") && sender.hasPermission("kenicompetitivo.admin.rewards")) {
                // Autocompletar nombres de jugadores
                plugin.getServer().getOnlinePlayers().forEach(player -> completions.add(player.getName()));
            }
            return completions;
        }

        return completions;
    }
}
