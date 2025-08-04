package mp.kenimon.commands;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HeadCommand implements CommandExecutor {

    private final Kenicompetitivo plugin;

    public HeadCommand(Kenicompetitivo plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("kenicompetitivo.head.admin")) {
            player.sendMessage(ChatColor.RED + "No tienes permiso para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set":
                plugin.getTopStreakHeadManager().setHeadLocation(player);
                break;

            case "info":
                plugin.getTopStreakHeadManager().showHeadLocation(player);
                break;

            case "update":
                plugin.getTopStreakHeadManager().updateTopStreakHead();
                player.sendMessage(ChatColor.GREEN + "Cabeza del mejor jugador actualizada.");
                break;

            default:
                showHelp(player);
                break;
        }

        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Comandos de Head ===");
        player.sendMessage(ChatColor.YELLOW + "/head set " + ChatColor.WHITE + "- Establece la ubicación de la cabeza en el bloque que estás mirando");
        player.sendMessage(ChatColor.YELLOW + "/head info " + ChatColor.WHITE + "- Muestra información sobre la ubicación actual de la cabeza");
        player.sendMessage(ChatColor.YELLOW + "/head update " + ChatColor.WHITE + "- Actualiza manualmente la cabeza del mejor jugador");
    }
}
