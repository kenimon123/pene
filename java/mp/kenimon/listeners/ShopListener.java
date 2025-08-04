package mp.kenimon.listeners;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ShopListener implements Listener {

    private final Kenicompetitivo plugin;

    public ShopListener(Kenicompetitivo plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();

        // Verificar si es la tienda
        if (!event.getView().getTitle().equals(plugin.getShopManager().getShopTitle())) return;

        event.setCancelled(true);

        // Verificar si se hizo clic en un ítem
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // Verificar si es el botón de cerrar
        if (event.getSlot() == plugin.getShopManager().getCloseButtonSlot()) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.6f, 1.0f);
            return;
        }

        // Procesar la compra/reclamación
        plugin.getShopManager().processItemPurchase(player, clickedItem);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();

        // Verificar si es la tienda
        if (!event.getView().getTitle().equals(plugin.getShopManager().getShopTitle())) return;

        // Reproducir sonido al cerrar
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.6f, 1.0f);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Si está configurado, notificar sobre la tienda diaria
        if (plugin.getConfigManager().getConfig().getBoolean("shop.notify_on_join", false)) {
            Player player = event.getPlayer();

            // Programar un mensaje retrasado para no saturar al jugador al entrar
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(plugin.getConfigManager().getFormattedMessage("shop.join_notification", "")
                        .replace("{time}", plugin.getShopManager().getNextUpdateTimeFormatted()));
            }, 40L); // 2 segundos después de entrar
        }
    }
}
