package mp.kenimon.commands;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CaminoCommand implements CommandExecutor {

    private final Kenicompetitivo plugin;

    public CaminoCommand(Kenicompetitivo plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Solo los jugadores pueden usar este comando.");
            return true;
        }

        Player player = (Player) sender;
        // Generamos el panel personalizado para el jugador (por ejemplo, página 0)
        player.openInventory(plugin.getPanelManager().buildPlayerPanel(player, 0));
        return true;
    }
}
