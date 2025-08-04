package mp.kenimon.commands;

import mp.kenimon.Kenicompetitivo;
import mp.kenimon.cosmetics.CosmeticEffect;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class CosmeticosCommand implements CommandExecutor {

    private final Kenicompetitivo plugin;

    public CosmeticosCommand(Kenicompetitivo plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }

        Player player = (Player) sender;

        // Verificar si los cosméticos están habilitados
        if (!plugin.getConfigManager().getConfig().getBoolean("cosmetics.enabled", true)) {
            String disabledMessage = plugin.getConfigManager().getMessages().getString(
                    "cosmetics.disabled", "&cEl sistema de cosméticos está desactivado.");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', disabledMessage));
            return true;
        }

        // Verificar permisos
        if (!player.hasPermission("kenicompetitivo.cosmeticos")) {
            String noPermission = plugin.getConfigManager().getMessages().getString(
                    "cosmetics.no_permission", "&cNo tienes permiso para usar cosméticos.");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
            return true;
        }

        // Procesar los subcomandos
        if (args.length > 0) {
            // Permiso administrativo para subcomandos
            if (!player.hasPermission("kenicompetitivo.cosmeticos.admin")) {
                String noAdminPerm = plugin.getConfigManager().getMessages().getString(
                        "cosmetics.no_admin_permission", "&cNo tienes permiso para gestionar cosméticos.");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', noAdminPerm));
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "dar":
                    if (args.length < 3) {
                        player.sendMessage(ChatColor.RED + "Uso: /cosmeticos dar <jugador> <efecto_id>");
                        return true;
                    }
                    giveCosmetic(player, args[1], args[2]);
                    return true;

                case "listar":
                    listEffects(player);
                    return true;

                default:
                    player.sendMessage(ChatColor.RED + "Subcomando desconocido. Usa /cosmeticos, /cosmeticos dar o /cosmeticos listar");
                    return true;
            }
        }

        // Abrir menú de cosméticos
        plugin.getCosmeticManager().openCosmeticsMenu(player);

        return true;
    }

    private void giveCosmetic(Player admin, String targetName, String effectId) {
        Player target = plugin.getServer().getPlayer(targetName);

        if (target == null) {
            admin.sendMessage(ChatColor.RED + "Jugador no encontrado: " + targetName);
            return;
        }

        if (!plugin.getCosmeticManager().effectExists(effectId)) {
            admin.sendMessage(ChatColor.RED + "Efecto cosmético no encontrado: " + effectId);
            return;
        }

        plugin.getCosmeticManager().unlockEffect(target.getUniqueId(), effectId);
        admin.sendMessage(ChatColor.GREEN + "¡Efecto " + effectId + " otorgado a " + target.getName() + "!");
    }

    private void listEffects(Player player) {
        List<String> effects = plugin.getCosmeticManager().getAvailableEffects();

        player.sendMessage(ChatColor.GREEN + "=== Efectos Cosméticos Disponibles ===");
        for (String effect : effects) {
            CosmeticEffect cosmeticEffect = plugin.getCosmeticManager().getEffectById(effect);
            if (cosmeticEffect != null) {
                player.sendMessage(ChatColor.YELLOW + effect + ChatColor.WHITE + " - " + ChatColor.AQUA + cosmeticEffect.getName());
            }
        }
    }
}
