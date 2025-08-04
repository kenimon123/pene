package mp.kenimon.listeners;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.FileConfiguration;

public class CosmeticMenuListener implements Listener {

    private Kenicompetitivo plugin;

    public CosmeticMenuListener(Kenicompetitivo plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String title = event.getView().getTitle();

        // Obtener títulos de menús configurados
        String mainMenuTitle = ChatColor.translateAlternateColorCodes('&',
                config.getString("cosmetics.menu.title", "&b&lCosméticos"));

        String categoryTitleFormat = ChatColor.translateAlternateColorCodes('&',
                config.getString("cosmetics.menu.category_menu.title_format", "&b{category_name}"));

        // Verificar si es un menú de cosméticos (principal o categoría)
        boolean isMainMenu = title.equals(mainMenuTitle);
        boolean isCategoryMenu = false;

        // La verificación es más compleja para menús de categoría porque tienen nombre dinámico
        if (!isMainMenu) {
            // Intentar encontrar si es alguna categoría conocida
            for (String category : new String[]{"particle", "sound", "kill"}) {
                String categoryName = plugin.getConfigManager().getMessages().getString(
                        "cosmetics.menu.category_" + category,
                        config.getString("cosmetics.menu.categories." + category + ".name", category));

                String expectedTitle = categoryTitleFormat.replace("{category_name}",
                        ChatColor.translateAlternateColorCodes('&', categoryName));

                if (title.equals(expectedTitle)) {
                    isCategoryMenu = true;
                    break;
                }
            }
        }

        // Si es uno de nuestros menús, procesar el clic
        if (isMainMenu || isCategoryMenu) {
            event.setCancelled(true); // Prevenir que se muevan ítems

            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem != null) {
                plugin.getCosmeticManager().handleMenuClick(player, clickedItem);
            }
        }
    }
}
