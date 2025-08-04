package mp.kenimon.managers;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class SignSelectionManager implements Listener {

    private Kenicompetitivo plugin;
    private Map<UUID, Consumer<Block>> pendingSelections;

    public SignSelectionManager(Kenicompetitivo plugin) {
        this.plugin = plugin;
        this.pendingSelections = new HashMap<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Marca a un jugador como pendiente para seleccionar un letrero
     */
    public void selectSign(Player player, Consumer<Block> callback) {
        pendingSelections.put(player.getUniqueId(), callback);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!pendingSelections.containsKey(player.getUniqueId())) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Si está en la lista de pendientes, procesar la selección
        Consumer<Block> callback = pendingSelections.get(player.getUniqueId());
        pendingSelections.remove(player.getUniqueId());

        event.setCancelled(true);
        callback.accept(block);
    }
}
