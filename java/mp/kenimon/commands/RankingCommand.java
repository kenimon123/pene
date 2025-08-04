package mp.kenimon.commands;

import mp.kenimon.Kenicompetitivo;
import mp.kenimon.managers.RankingManager;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RankingCommand implements CommandExecutor, TabCompleter {

    private final Kenicompetitivo plugin;

    public RankingCommand(Kenicompetitivo plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Este comando solo puede ser utilizado por jugadores.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("kenicompetitivo.ranking")) {
            player.sendMessage(ChatColor.RED + "No tienes permiso para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "crear":
                if (!player.hasPermission("kenicompetitivo.ranking.admin")) {
                    player.sendMessage(ChatColor.RED + "No tienes permiso para crear rankings.");
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Uso: /ranking crear <tipo> [holograma]");
                    return true;
                }

                String type = args[1].toLowerCase();
                boolean isHologram = args.length > 2 && args[2].equalsIgnoreCase("holograma");

                if (isHologram) {
                    plugin.getRankingManager().addHologram(player, type);
                } else {
                    player.sendMessage(ChatColor.GOLD + "Haga clic en un letrero para configurarlo como ranking de " + type);
                    plugin.getSignSelectionManager().selectSign(player, (Block block) -> {
                        plugin.getRankingManager().registerSign(block, player, type, 1);
                    });
                }
                break;

            case "eliminar":
                if (!player.hasPermission("kenicompetitivo.ranking.admin")) {
                    player.sendMessage(ChatColor.RED + "No tienes permiso para eliminar rankings.");
                    return true;
                }

                if (args.length < 2 || (!args[1].equalsIgnoreCase("holograma") && !args[1].equalsIgnoreCase("letrero"))) {
                    player.sendMessage(ChatColor.RED + "Uso: /ranking eliminar <holograma|letrero>");
                    return true;
                }

                if (args[1].equalsIgnoreCase("holograma")) {
                    // Eliminar holograma más cercano
                    player.sendMessage(ChatColor.GOLD + "Buscando holograma cercano para eliminar...");
                    boolean found = plugin.getRankingManager().removeNearestHologram(player);

                    if (found) {
                        player.sendMessage(ChatColor.GREEN + "¡Holograma eliminado correctamente!");
                    } else {
                        player.sendMessage(ChatColor.RED + "No se encontraron hologramas cercanos.");
                    }
                } else {
                    // Eliminar letrero
                    player.sendMessage(ChatColor.GOLD + "Haz clic en el letrero que deseas eliminar");
                    plugin.getSignSelectionManager().selectSign(player, (Block block) -> {
                        boolean removed = plugin.getRankingManager().removeSign(block);
                        if (removed) {
                            player.sendMessage(ChatColor.GREEN + "¡Letrero de ranking eliminado correctamente!");
                        } else {
                            player.sendMessage(ChatColor.RED + "El bloque seleccionado no es un letrero de ranking registrado.");
                        }
                    });
                }
                break;

            case "actualizar":
                if (!player.hasPermission("kenicompetitivo.ranking.admin")) {
                    player.sendMessage(ChatColor.RED + "No tienes permiso para actualizar rankings.");
                    return true;
                }

                plugin.getRankingManager().updateAllRankings();
                plugin.getRankingManager().updateAllDisplays();
                player.sendMessage(ChatColor.GREEN + "Rankings actualizados correctamente.");
                break;

            case "top":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Uso: /ranking top <tipo>");
                    return true;
                }

                String rankingType = args[1].toLowerCase();
                showRanking(player, rankingType);
                break;

            case "listar":
                if (!player.hasPermission("kenicompetitivo.ranking.admin")) {
                    player.sendMessage(ChatColor.RED + "No tienes permiso para listar rankings.");
                    return true;
                }

                plugin.getRankingManager().listHolograms(player);
                break;

            case "mover":
                if (!player.hasPermission("kenicompetitivo.ranking.admin")) {
                    player.sendMessage(ChatColor.RED + "No tienes permiso para mover rankings.");
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Uso: /ranking mover <tipo>");
                    return true;
                }

                String typeToMove = args[1].toLowerCase();
                plugin.getRankingManager().moveHologram(player, typeToMove);
                break;

            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Comandos de Ranking ===");
        player.sendMessage(ChatColor.YELLOW + "/ranking top <tipo> " + ChatColor.WHITE + "- Muestra el ranking del tipo seleccionado");

        if (player.hasPermission("kenicompetitivo.ranking.admin")) {
            player.sendMessage(ChatColor.YELLOW + "/ranking crear <tipo> [holograma] " + ChatColor.WHITE + "- Crea un ranking (letrero u holograma)");
            player.sendMessage(ChatColor.YELLOW + "/ranking eliminar <holograma|letrero> " + ChatColor.WHITE + "- Elimina un holograma o letrero de ranking");
            player.sendMessage(ChatColor.YELLOW + "/ranking actualizar " + ChatColor.WHITE + "- Actualiza todos los rankings");
            player.sendMessage(ChatColor.YELLOW + "/ranking listar " + ChatColor.WHITE + "- Lista todos los hologramas de ranking");
        }
    }

    private void showRanking(Player player, String type) {
        List<RankingManager.RankingEntry> ranking = plugin.getRankingManager().getRanking(type);

        if (ranking.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No hay datos disponibles para este ranking.");
            return;
        }

        String displayName = plugin.getConfigManager().getConfig().getString("rankings.types." + type + ".display_name", type);
        player.sendMessage(ChatColor.GOLD + "=== Ranking: " + ChatColor.translateAlternateColorCodes('&', displayName) + ChatColor.GOLD + " ===");

        for (RankingManager.RankingEntry entry : ranking) {
            player.sendMessage(String.format(
                    ChatColor.YELLOW + "#%d " + ChatColor.WHITE + "%s " + ChatColor.GRAY + "- " + ChatColor.GOLD + "%d",
                    entry.getPosition(), entry.getPlayerName(), entry.getValue()
            ));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("top");
            if (sender.hasPermission("kenicompetitivo.ranking.admin")) {
                subcommands = Arrays.asList("crear", "actualizar", "top");
            }

            for (String subcommand : subcommands) {
                if (subcommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subcommand);
                }
            }
        } else if (args.length == 2) {
            List<String> types = Arrays.asList("trophies", "killstreak");
            for (String type : types) {
                if (type.startsWith(args[1].toLowerCase())) {
                    completions.add(type);
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("crear")) {
            if ("holograma".startsWith(args[2].toLowerCase())) {
                completions.add("holograma");
            }
        }

        return completions;
    }
}
