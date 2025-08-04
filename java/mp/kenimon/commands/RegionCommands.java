package mp.kenimon.commands;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class RegionCommands implements CommandExecutor {

    private final Kenicompetitivo plugin;

    public RegionCommands(Kenicompetitivo plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Verificar si WorldGuard está disponible
        if (!plugin.getWorldGuardUtil().isWorldGuardEnabled()) {
            sender.sendMessage(ChatColor.RED + "WorldGuard no está disponible. Esta función requiere WorldGuard.");
            return true;
        }

        // Verificar permiso
        if (!sender.hasPermission("kenicompetitivo.admin.regions")) {
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
            case "list":
                listExcludedRegions(sender);
                break;
            case "add":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Uso: /region add <idRegion>");
                    return true;
                }
                addExcludedRegion(sender, args[1]);
                break;
            case "remove":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Uso: /region remove <idRegion>");
                    return true;
                }
                removeExcludedRegion(sender, args[1]);
                break;
            case "check":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
                    return true;
                }
                checkCurrentRegion((Player) sender);
                break;
            case "available":
                listAvailableRegions(sender);
                break;
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
        sender.sendMessage(ChatColor.GOLD + "=== Comandos de Regiones ===");
        sender.sendMessage(ChatColor.YELLOW + "/region list " + ChatColor.WHITE + "- Muestra las regiones excluidas");
        sender.sendMessage(ChatColor.YELLOW + "/region add <idRegion> " + ChatColor.WHITE + "- Añade una región a la lista de excluidas");
        sender.sendMessage(ChatColor.YELLOW + "/region remove <idRegion> " + ChatColor.WHITE + "- Elimina una región de la lista de excluidas");
        sender.sendMessage(ChatColor.YELLOW + "/region check " + ChatColor.WHITE + "- Comprueba la región en la que estás");
        sender.sendMessage(ChatColor.YELLOW + "/region available " + ChatColor.WHITE + "- Lista todas las regiones disponibles");
    }

    /**
     * Lista las regiones excluidas
     */
    private void listExcludedRegions(CommandSender sender) {
        List<String> regions = plugin.getWorldGuardUtil().getExcludedRegionIds();

        if (regions.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No hay regiones excluidas configuradas.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Regiones Excluidas ===");
        for (String region : regions) {
            sender.sendMessage(ChatColor.YELLOW + "- " + region);
        }
    }

    /**
     * Añade una región a la lista de excluidas
     */
    private void addExcludedRegion(CommandSender sender, String regionId) {
        // Verificar que la región existe
        if (!plugin.getWorldGuardUtil().regionExists(regionId)) {
            sender.sendMessage(ChatColor.RED + "La región '" + regionId + "' no existe.");
            return;
        }

        // Intentar añadir a la lista
        if (plugin.getWorldGuardUtil().addExcludedRegion(regionId)) {
            sender.sendMessage(ChatColor.GREEN + "Región '" + regionId + "' añadida a la lista de excluidas.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "La región '" + regionId + "' ya estaba en la lista de excluidas.");
        }
    }

    /**
     * Elimina una región de la lista de excluidas
     */
    private void removeExcludedRegion(CommandSender sender, String regionId) {
        // Intentar eliminar de la lista
        if (plugin.getWorldGuardUtil().removeExcludedRegion(regionId)) {
            sender.sendMessage(ChatColor.GREEN + "Región '" + regionId + "' eliminada de la lista de excluidas.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "La región '" + regionId + "' no estaba en la lista de excluidas.");
        }
    }

    /**
     * Comprueba la región en la que está el jugador
     */
    private void checkCurrentRegion(Player player) {
        List<String> regions = plugin.getWorldGuardUtil().getPlayerRegions(player);

        if (regions.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No estás en ninguna región.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "=== Regiones Actuales ===");
        for (String region : regions) {
            boolean excluded = plugin.getWorldGuardUtil().getExcludedRegionIds().contains(region.toLowerCase());
            ChatColor color = excluded ? ChatColor.RED : ChatColor.GREEN;
            String status = excluded ? " (Excluida)" : "";
            player.sendMessage(color + "- " + region + status);
        }

        // Verificar si el jugador está en una región excluida
        if (plugin.getWorldGuardUtil().isInExcludedRegion(player)) {
            player.sendMessage(ChatColor.RED + "Estás en una región donde no se acumulan rachas ni trofeos.");
        } else {
            player.sendMessage(ChatColor.GREEN + "Puedes acumular rachas y trofeos en esta ubicación.");
        }
    }

    /**
     * Lista todas las regiones disponibles en todos los mundos
     */
    private void listAvailableRegions(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Regiones WorldGuard Disponibles ===");

        // Obtener todas las regiones por mundo
        for (World world : plugin.getServer().getWorlds()) {
            Set<String> regionsInWorld = plugin.getWorldGuardUtil().getRegionsInWorld(world);

            if (!regionsInWorld.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + world.getName() + ": " +
                        ChatColor.WHITE + String.join(", ", regionsInWorld));
            }
        }

        // Mostrar regiones actualmente excluidas
        List<String> excludedRegions = plugin.getWorldGuardUtil().getExcludedRegionIds();
        if (!excludedRegions.isEmpty()) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.RED + "Regiones actualmente excluidas: " +
                    ChatColor.WHITE + String.join(", ", excludedRegions));
        }
    }
}
